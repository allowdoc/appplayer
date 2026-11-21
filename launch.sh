#!/bin/bash
# ==============================================================================
# appplayer — Universal Android Runner launch script (Linux/macOS)
# Usage: ./launch.sh <path-to-apk> [--ui] [--timeout N]
# Boots the APK in-process (Zero VM / Zero QEMU), runs the REAL React Native
# bundle through the patched Hermes VM bridge and opens the optional viewer.
# app.jar is decompiled automatically when missing OR when the given APK
# differs from the one it was decompiled from (fingerprint marker).
# ==============================================================================
RUNNER_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$RUNNER_DIR"

if [ $# -lt 1 ]; then
    echo "Usage: ./launch.sh <path-to-apk> [--ui] [--timeout N]"
    exit 1
fi

TARGET_APK="$1"; shift
if [ ! -f "$TARGET_APK" ]; then
    echo "[-] APK not found: $TARGET_APK"
    exit 1
fi
TARGET_APK="$(cd "$(dirname "$TARGET_APK")" && pwd)/$(basename "$TARGET_APK")"

# ---- Universal input classification ------------------------------------------
# Android APKs take the full pipeline (enjarify, annotation recovery, overlay).
# ANY other input (plain Java jar, .class, class directory, .apk without a
# manifest) is a JAVA application: the launcher itself (UniversalEntryLoader)
# discovers its entry point and runs it - no decompilation, no per-app work.
IS_ANDROID_APK=1
if ! unzip -l "$TARGET_APK" 2>/dev/null | grep -q "AndroidManifest.xml"; then
    IS_ANDROID_APK=0
fi

# ---- Locate Java runtime ----
JAVA_BIN=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_BIN="java"
fi
if [ -z "$JAVA_BIN" ]; then
    echo "[-] No java found. Install JDK/JRE 17+ or set JAVA_HOME."
    exit 1
fi

# ---- Engine classes required ----
if [ ! -f "$RUNNER_DIR/bin/org/headless/runner/AppLauncher.class" ]; then
    echo "[-] Engine classes missing. Compile once first: ./build.sh /path/to/app.apk"
    exit 1
fi

BASE_JAR="$RUNNER_DIR/app.jar"
APP_JAR="$RUNNER_DIR/app-patched.jar"
APK_MARK="$RUNNER_DIR/data/.appjar-src.txt"

# ---- APK pipeline (Android APKs only) ----------------------------------------
# Android APKs take the full pipeline (enjarify, annotation recovery, overlay).
# ANY other input (plain Java jar, .class, class directory, .apk without a
# manifest) is a JAVA application: the launcher itself (UniversalEntryLoader)
# discovers its entry point and runs it - no decompilation, no per-app work.
OVERLAY_JAR=""
RUNTIME_JAR=""
if [ "$IS_ANDROID_APK" = "1" ]; then

# ---- Auto-rebuild: decompile when app.jar is missing OR from a DIFFERENT apk ----
TARGET_FPRINT="${TARGET_APK}|$(stat -c %s "$TARGET_APK")"
REBUILT=0
if [ -f "$BASE_JAR" ]; then
    if [ -f "$APK_MARK" ]; then
        MARK_DATA="$(cat "$APK_MARK")"
        if [ "$MARK_DATA" != "$TARGET_FPRINT" ]; then
            echo "[*] [AutoRebuild] APK changed since last decompile - rebuilding app.jar..."
            REBUILT=1
        fi
    else
        echo "[!] [AutoRebuild] app.jar provenance unknown (no marker) - delete app.jar to force a rebuild if this is a different APK."
    fi
else
    REBUILT=1
fi

if [ "${REBUILT:-0}" = "1" ]; then
    echo "[*] [AutoRebuild] Decompiling $TARGET_APK with enjarify..."
    if [ -f "$RUNNER_DIR/tools/enjarify/enjarify.sh" ]; then
        (cd "$RUNNER_DIR/tools/enjarify" && PYTHONPATH="$RUNNER_DIR/tools/enjarify" python3 -O -m enjarify.main "$TARGET_APK" -o "$BASE_JAR" -f) \
            || echo "[-] enjarify failed; provide app.jar manually."
    fi
    if [ ! -f "$BASE_JAR" ]; then
        echo "[-] enjarify failed. Install Python 3 or place app.jar next to launch.sh."
        exit 1
    fi
    mkdir -p "$RUNNER_DIR/data"
    echo "$TARGET_FPRINT" > "$APK_MARK"
    # Invalidate everything derived from the previous apk.
    rm -f "$APP_JAR" "$APP_JAR.frames.ok" \
          "$RUNNER_DIR/yoga-native-restored.jar" "$RUNNER_DIR/yoga-native-restored.jar.frames.ok"
    rm -rf "$RUNNER_DIR/bin-app"
    echo "[*] [AutoRebuild] Per-app overrides invalidated. Run ./build.sh \"$TARGET_APK\" to rebuild them (optional)."
fi

# ---- Annotation recovery + stack-map frame repair (separate JVM, before launch) ----
# enjarify emits Java 8 (v52) classes WITHOUT stack-map frames; JDK 13+ removed the
# old-verifier failover, so every class needs frames recomputed BEFORE the app JVM
# starts. Always run: it reuses app-patched.jar when its APK-identity marker is current.
echo "[*] [AutoRebuild] Verifying annotations + stack-map frames for the app jar..."
"$JAVA_BIN" -cp "bin:lib/runner-agent.jar:lib/android-all-10.jar:lib/dexlib2-2.5.2.jar:lib/guava-27.1-android.jar:lib/asm-9.6.jar:lib/asm-commons-9.6.jar:$BASE_JAR" \
    org.headless.runner.tool.annotation.AnnotationPipeline "$TARGET_APK" "$BASE_JAR" "$APP_JAR" "$RUNNER_DIR/data" \
    || echo "[-] Annotation pipeline failed; continuing with unpatched app.jar."

# ---- Yoga/graphics overlay jar (re-natived YogaNative + stubbed framework graphics) ----
OVERLAY_JAR="$RUNNER_DIR/yoga-native-restored.jar"
if [ ! -f "$OVERLAY_JAR" ] && [ -f "$APP_JAR" ]; then
    echo "[*] [AutoRebuild] Regenerating yoga-native-restored.jar..."
    "$JAVA_BIN" -cp "bin:lib/asm-9.6.jar:lib/asm-commons-9.6.jar:lib/android-all-10.jar:$APP_JAR" \
        org.headless.runner.tool.rn.RestoreNativeMethods "$APP_JAR" lib/android-all-10.jar "$OVERLAY_JAR" "$TARGET_APK" \
        || echo "[-] Overlay generation failed; RN layout will be degraded."
fi
# The overlay must carry the re-natived YogaNative or RegisterNatives fails
# and every RN app freezes after its first commit (blank screen).
if [ -f "$APP_JAR" ] && [ -f "$OVERLAY_JAR" ] \
   && ! unzip -l "$OVERLAY_JAR" 2>/dev/null | grep -q "com/facebook/yoga/YogaNative.class"; then
    echo "[*] [AutoRebuild] Overlay missing YogaNative - regenerating..."
    rm -f "$OVERLAY_JAR" "$OVERLAY_JAR.frames.ok"
    "$JAVA_BIN" -cp "bin:lib/asm-9.6.jar:lib/asm-commons-9.6.jar:lib/android-all-10.jar:$APP_JAR" \
        org.headless.runner.tool.rn.RestoreNativeMethods "$APP_JAR" lib/android-all-10.jar "$OVERLAY_JAR" "$TARGET_APK" \
        || echo "[-] Overlay regeneration failed; RN layout will be degraded."
fi
if [ -f "$OVERLAY_JAR" ]; then
    "$JAVA_BIN" -cp "bin:lib/asm-9.6.jar:lib/asm-commons-9.6.jar" \
        org.headless.runner.tool.BytecodeFrameNormalizer "$OVERLAY_JAR" || true
fi

# ---- Choose the jar for the app classpath (patched preferred) ----
RUNTIME_JAR="$BASE_JAR"
[ -f "$APP_JAR" ] && RUNTIME_JAR="$APP_JAR"
else
# ---- JAVA APPLICATION MODE: the launcher's UniversalEntryLoader handles
# ---- entry discovery + the app classloader itself; the runner classpath only
# ---- needs the environment (framework shims, tools, renderer).
echo "[*] [UniversalEntry] Java application input (no AndroidManifest.xml) - skipping APK pipeline."
fi

# ---- Classpath order: src shims, Yoga/graphics overlay (must shadow the
# ---- framework's native graphics methods), framework, libs, app jar ----
JAR_FILE="$RUNNER_DIR/lib/android-all-10.jar"
[ -f "$RUNNER_DIR/lib/android-all-10-framed.jar" ] && JAR_FILE="$RUNNER_DIR/lib/android-all-10-framed.jar"

CLASSPATH="bin:lib/runner-agent.jar"
[ -n "$OVERLAY_JAR" ] && [ -f "$OVERLAY_JAR" ] && CLASSPATH="$CLASSPATH:$OVERLAY_JAR"
CLASSPATH="$CLASSPATH:$JAR_FILE:lib/sqlite-jdbc.jar:lib/dexlib2-2.5.2.jar:lib/guava-27.1-android.jar:lib/asm-9.6.jar:lib/asm-commons-9.6.jar"
# The APK itself is on the classpath so apps can load their PACKAGED RESOURCES
# via classloader (joda-time tz data, .properties bundles, etc.) - exactly like
# Android's PathClassLoader, which exposes APK entries as classloader resources.
# classes.dex inside is ignored by the JVM (not a .class file).
[ -f "$TARGET_APK" ] && CLASSPATH="$CLASSPATH:$TARGET_APK"
# Official Kotlin runtime shadowing the app's dex-converted copies: the
# dex->jar kotlin-reflect loses primitive-typeOf support (KotlinReflectionInternalError
# "Unresolved class: float" in every Expo prop converter), so the environment
# provides a working kotlin-reflect/kotlin-stdlib matching the app (env-level,
# app-agnostic — like providing a newer libc).
for KJAR in lib/kotlin-stdlib-*.jar lib/kotlin-reflect-*.jar; do
    [ -f "$KJAR" ] && CLASSPATH="$CLASSPATH:$KJAR"
done
[ -d "$RUNNER_DIR/bin-app" ] && CLASSPATH="$CLASSPATH:$RUNNER_DIR/bin-app"
[ -n "$RUNTIME_JAR" ] && [ -f "$RUNTIME_JAR" ] && CLASSPATH="$CLASSPATH:$RUNTIME_JAR"
# Skia renderer (Google Skia via Skija). Optional: falls back to Java2D player.
[ -f "$RUNNER_DIR/lib/skija-shared-0.143.18.jar" ] && CLASSPATH="$CLASSPATH:$RUNNER_DIR/lib/skija-shared-0.143.18.jar:$RUNNER_DIR/lib/types-0.2.0.jar"
[ -f "$RUNNER_DIR/lib/skija-linux-x64-0.143.18.jar" ] && CLASSPATH="$CLASSPATH:$RUNNER_DIR/lib/skija-linux-x64-0.143.18.jar"
[ -f "$RUNNER_DIR/lib/skija-windows-x64-0.143.18.jar" ] && CLASSPATH="$CLASSPATH:$RUNNER_DIR/lib/skija-windows-x64-0.143.18.jar"

echo "=================================================================="
echo "  LAUNCHING NATIVE ANDROID RUNNER (ZERO VM / ZERO QEMU)"
echo "  Target: $TARGET_APK"
echo "=================================================================="
# JDK 25 ignores -noverify; the diagnostic flags below ARE honored and let
# the FrameNormalizer-produced app-patched.jar pass bytecode verification.
# libXtst/libEGL stubs (or real libs) must be findable for AWT headful windows
LD_LIBRARY_PATH="${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}$RUNNER_DIR/native/linux-x86_64"
export LD_LIBRARY_PATH

"$JAVA_BIN" \
    -XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationLocal -XX:-BytecodeVerificationRemote \
    -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss4m -XX:MaxMetaspaceSize=512m \
    -javaagent:lib/runner-agent.jar \
    -Djava.library.path="$RUNNER_DIR/native/linux-x86_64" \
    -cp "$CLASSPATH" org.headless.runner.AppLauncher "$TARGET_APK" "$@"
