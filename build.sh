#!/bin/bash
# ==============================================================================
# appplayer — Universal Android Runner ("Android Wine") build script (Linux/macOS)
# Two-pass build (app-agnostic):
#   pass 1  CORE engine + framework shims -> bin/     (compiled WITHOUT any
#           app jar, against compile-stubs/ — never fails because of an app)
#   pass 2  per-APP override classes     -> bin-app/  (compiled against the
#           current app.jar; skipped with a warning when app-incompatible)
# Everything is relative to this script's directory — no absolute paths.
# ==============================================================================
set -e
RUNNER_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$RUNNER_DIR"

APK_ARG="$1"
# Absolutize early: enjarify runs from tools/enjarify, so relative APK paths
# (e.g. "app-release.apk") would point at the wrong directory.
if [ -n "$APK_ARG" ] && [ -f "$APK_ARG" ]; then
    APK_ARG="$(cd "$(dirname "$APK_ARG")" && pwd)/$(basename "$APK_ARG")"
fi

echo "=================================================================="
echo "  BUILDING UNIVERSAL ANDROID RUNNER (Linux/macOS)"
echo "  Runner dir: $RUNNER_DIR"
echo "=================================================================="

# ---- Locate a JDK (javac required) ----
JAVAC_BIN=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC_BIN="$JAVA_HOME/bin/javac"
elif command -v javac >/dev/null 2>&1; then
    JAVAC_BIN="javac"
else
    for d in /usr/lib/jvm/*/; do
        if [ -x "$d/bin/javac" ]; then JAVAC_BIN="$d/bin/javac"; break; fi
    done
fi
if [ -z "$JAVAC_BIN" ]; then
    echo "[-] No JDK found (javac). Install JDK 17+ or set JAVA_HOME."
    exit 1
fi
JAVA_BIN="${JAVAC_BIN%javac}java"
echo "[+] Using javac: $JAVAC_BIN"

# ---- App jar (optional for pass 1, needed for pass 2) ----
BASE_JAR="$RUNNER_DIR/app.jar"
if [ ! -f "$BASE_JAR" ] && [ -n "$APK_ARG" ] && [ -f "$APK_ARG" ]; then
    echo "[*] No app jar yet — decompiling $APK_ARG with enjarify..."
    if [ -f "$RUNNER_DIR/tools/enjarify/enjarify.sh" ]; then
        (cd "$RUNNER_DIR/tools/enjarify" && python3 -O -m enjarify.main "$APK_ARG" -o "$BASE_JAR" -f) \
            || echo "[-] enjarify failed for $APK_ARG (python3 required, path must exist)."
    else
        echo "[-] tools/enjarify missing — cannot decompile."
    fi
fi

# ---- Record which APK app.jar came from (fingerprint marker) ----
if [ -f "$BASE_JAR" ] && [ -n "$APK_ARG" ] && [ -f "$APK_ARG" ]; then
    APK_ABS="$(cd "$(dirname "$APK_ARG")" && pwd)/$(basename "$APK_ARG")"
    mkdir -p "$RUNNER_DIR/data"
    echo "${APK_ABS}|$(stat -c %s "$APK_ABS")" > "$RUNNER_DIR/data/.appjar-src.txt"
fi
APP_JAR="$BASE_JAR"

# ---- Pass 1: CORE engine + framework shims (compiled WITHOUT app jar) ----
mkdir -p bin
# Drop stale per-app override classes from previous builds/APKs.
rm -rf bin/com/bugsnag bin/com/facebook/react bin/com/facebook/hermes bin/com/google/gson bin/okio
find src/org src/android src/androidx src/com/android src/com/facebook/jni \
     src/com/facebook/soloader src/com/facebook/react/turbomodule \
     src/com/google/android src/dalvik src/libcore \
     -name "*.java" > sources.txt
SKIA_CP=""
[ -f lib/skija-shared-0.143.18.jar ] && SKIA_CP=":lib/skija-shared-0.143.18.jar:lib/types-0.2.0.jar"
"$JAVAC_BIN" -encoding UTF-8 -nowarn --release 17 -sourcepath compile-stubs -implicit:none \
    -cp "lib/sqlite-jdbc.jar:lib/runner-agent.jar:lib/android-all-10.jar:lib/dexlib2-2.5.2.jar:lib/guava-27.1-android.jar:lib/asm-9.6.jar:lib/asm-commons-9.6.jar$SKIA_CP" \
    -d bin @sources.txt
echo "[+] Core engine compiled (app-independent)."

# ---- Pass 2: per-app overrides -> bin-app (optional, warns on failure) ----
# Each override group compiles INDEPENDENTLY: one app-incompatible group is
# skipped without taking away the others (e.g. a bridge class this app needs).
rm -rf bin-app
if [ -f "$APP_JAR" ]; then
    OVERRIDE_CP="bin:lib/sqlite-jdbc.jar:lib/runner-agent.jar:lib/android-all-10.jar:lib/dexlib2-2.5.2.jar:lib/guava-27.1-android.jar:lib/asm-9.6.jar:lib/asm-commons-9.6.jar:$APP_JAR"
    rm -f app-overrides-errors.txt
    mkdir -p bin-app
    for D in com/bugsnag com/facebook/hermes com/facebook/react com/google/gson okio; do
        if [ -d "src/$D" ]; then
            find "src/$D" -name "*.java" > app-src.tmp
            rm -rf /tmp/binapp-tmp && mkdir -p /tmp/binapp-tmp
            if "$JAVAC_BIN" -encoding UTF-8 -nowarn --release 17 \
                -cp "$OVERRIDE_CP:bin-app" -d /tmp/binapp-tmp @app-src.tmp 2>>app-overrides-errors.txt; then
                cp -r /tmp/binapp-tmp/. bin-app/
                echo "[+] Override group '$D' compiled into bin-app."
            else
                echo "[!] Override group '$D' does not fit THIS apk — skipping it."
            fi
        fi
    done
    if [ -d bin-app ] && [ -n "$(ls bin-app 2>/dev/null)" ]; then
        echo "[+] App-specific overrides ready in bin-app."
    else
        rm -rf bin-app
        echo "[!] No app-specific overrides compiled (universal mode). Details: app-overrides-errors.txt"
    fi
else
    echo "[!] No app.jar yet — skipping per-app overrides. Run ./build.sh <apk> to build them."
fi

# ---- Re-native Yoga JNI bridge classes + verify stack-map frames ----
# IMPORTANT: use app-patched.jar when present. Its YogaNative jni_* methods
# were DE-NATIVIZED by the annotation pipeline; RestoreNativeMethods restores
# ACC_NATIVE so the desktop libyoga can RegisterNatives against them. The raw
# app.jar still has native flags (enjarify preserves them), so running the
# tool against it finds nothing to change and silently emits an overlay
# WITHOUT YogaNative - which kills RN layout (RegisterNatives fails, every
# RN app freezes after its first commit).
OVERLAY_SRC_JAR="$RUNNER_DIR/app-patched.jar"
[ -f "$OVERLAY_SRC_JAR" ] || OVERLAY_SRC_JAR="$APP_JAR"
# Only RN apps ship Yoga. A non-RN APK (plain Android/Java app) has no
# com/facebook/yoga at all - skipping the overlay is CORRECT there, not an
# error: aborting used to make every non-RN app unbuildable.
IS_RN_APP=0
if [ -f "$OVERLAY_SRC_JAR" ] && unzip -l "$OVERLAY_SRC_JAR" 2>/dev/null | grep -q "com/facebook/yoga/YogaNative.class"; then
    IS_RN_APP=1
fi
if [ "$IS_RN_APP" = "1" ] && [ -f "$OVERLAY_SRC_JAR" ]; then
    "$JAVA_BIN" -cp "bin:bin-app:lib/asm-9.6.jar:lib/asm-commons-9.6.jar:lib/android-all-10.jar:$OVERLAY_SRC_JAR" \
        org.headless.runner.tool.rn.RestoreNativeMethods "$OVERLAY_SRC_JAR" lib/android-all-10.jar yoga-native-restored.jar \
        || { echo "[-] RestoreNativeMethods FAILED - RN layout would be dead. Aborting."; exit 1; }
    # The overlay MUST carry the re-natived YogaNative (all yoga jni_* methods
    # become ACC_NATIVE and bind to the desktop libyoga). Without it
    # RegisterNatives fails and every RN app freezes after its first commit.
    if ! unzip -l yoga-native-restored.jar 2>/dev/null | grep -q "com/facebook/yoga/YogaNative.class"; then
        echo "[-] Overlay jar is missing YogaNative - RN layout would be dead. Aborting."
        exit 1
    fi
    echo "[+] Yoga native-method overlay ready."
else
    rm -f yoga-native-restored.jar yoga-native-restored.jar.frames.ok
    echo "[*] Non-RN app (no com/facebook/yoga in APK) - skipping yoga overlay (correct)."
fi
if [ -f "$RUNNER_DIR/yoga-native-restored.jar" ]; then
    "$JAVA_BIN" -cp "bin:lib/asm-9.6.jar:lib/asm-commons-9.6.jar" \
        org.headless.runner.tool.BytecodeFrameNormalizer "$RUNNER_DIR/yoga-native-restored.jar" || true
fi

# ---- Compile the universal RN driver to Hermes bytecode (v96) ----
HERMESC="$RUNNER_DIR/tools/hermes-linux/hermesc"
if [ -x "$HERMESC" ]; then
    "$HERMESC" -emit-binary -out "$RUNNER_DIR/tools/rn/driver.hbc" "$RUNNER_DIR/tools/rn/driver.js"
    echo "[+] RN driver compiled to Hermes bytecode."
else
    echo "[!] tools/hermes-linux/hermesc missing — driver.hbc must exist (shipped prebuilt)."
    [ -f "$RUNNER_DIR/tools/rn/driver.hbc" ] || { echo "[-] driver.hbc missing too. Aborting."; exit 1; }
fi

echo "=================================================================="
echo "  BUILD OK — ./launch.sh re-decompiles automatically when you"
echo "  pass a NEW apk:  ./launch.sh /path/to/any.apk [--ui]"
echo "=================================================================="
