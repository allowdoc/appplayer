# appplayer — Universal Android Runtime Engine ("Android Wine")

In-process Android runtime that boots **unmodified production APKs** directly on
OpenJDK (Windows + Linux) — Zero VM, Zero QEMU, Zero emulators. Includes a
**universal React Native runtime** that executes the app's real Hermes JS
bundle and renders the app's genuine React views.

## What was fixed in this version

### Round 2 - universal engine fixes (environment-level, zero per-app code)

| # | Problem | Root cause | Fix |
|---|---------|-----------|-----|
| 1 | **Blank/black screen on every app** - every programmatically-created View silently received an opaque-black `backgroundTint` + a placeholder background | The `TypedArray` shim reported ALL styleable indexes as resolved (`present || node == null`), so the View constructor read every `android.R.styleable.View` attribute for every view; the getters returned non-null fallbacks (black ColorStateList, 48x48 placeholder drawable) | AOSP-correct `TypedArray`: `getIndexCount()` only counts resolved attributes; `getDrawable()`/`getColorStateList()` return null for unset attrs and resolve real references when set |
| 2 | Every RN view died at creation: `LayerDrawable.mutate()` -> "Invalid drawable added to LayerDrawable!" | The fake black tint above forced `View.applyBackgroundTint` to mutate RN's `CompositeBackgroundDrawable` on every view | Same root cause - gone with fix #1 |
| 3 | Expo prop converters failed with `KotlinReflectionInternalError: Unresolved class: float` | dex->jar conversion breaks kotlin-reflect's primitive `typeOf()` | The environment ships official `kotlin-reflect`/`kotlin-stdlib` jars on the classpath ahead of the app (like a newer libc); `lib/kotlin-*.jar` auto-detected by launch scripts |
| 4 | Expo suspend functions rejected with "Cannot call a X method in test context" | expo SDK 50+ `ModuleHolder.call()` only handles plain async functions and unconditionally throws for SuspendFunctionComponent (on device they run through JSI) | `ExpoKotlinDispatcher` reproduces the device JSI protocol with the app's own classes: `AnyFunction.convertArgs$default` + `body.invoke(GlobalScope, args, continuation)` + result->promise settle, with R8 bridge-method awareness and a 10s settle timeout (capability-absent semantics - resolve null, never hang) |
| 5 | Expo map args failed `LinkedHashMap cannot be cast to ReadableMap` | JS bridge delivers JSON containers; expo converters assert `instanceof ReadableMap` | `toReactTypes()` converts JSON maps/lists into the app's own `WritableNativeMap`/`WritableNativeArray` at the dispatch boundary |
| 6 | **Plain Java apps could not run at all** (`launch.bat app.jar` died in the APK manifest parser) | The launcher had no Java entry path | **`UniversalEntryLoader`**: classifies input (Android APK vs jar/.class/directory), discovers the entry (`--main`/`--java` arg, manifest Main-Class, ASM bytecode scan for `public static void main(String[])`), builds a child-first app classloader (input + sibling lib/libs) over the runner framework, runs the app on the host JVM. Launch scripts auto-detect and skip the APK pipeline for Java inputs |
| 7 | App JS could freeze with promise continuations parked behind microtasks | the driver only drains jobs after the NEXT bridge message | Continuous stall watchdog: every 5s of VM silence it pumps Hermes jobs + React microtasks + flushes the MessageQueue over the bridge |
| 8 | JDK 25 `--sourcepath` compile issues in tool rebuilds; driver.js `nativeCallSyncHook` verified | hygiene | verified/cleaned; `java-launch.args` contains no deprecated `-noverify` |

### Round 1

| # | Problem (before) | Fix (now) |
|---|------------------|-----------|
| 1 | `ReactViewPopulator` fabricated a fake Airtel login UI (`LinearLayout` + `EditText` + "GET OTP") inside any empty `ReactRootView` | **Deleted permanently.** Real UI comes from the app's own ViewManagers |
| 2 | Fake session keys (`synthetic_fcm_token_pixel5_...` written into app prefs, prefilled Airtel session data committed to `data/`) | **Removed.** Fresh-install state guaranteed; Google/Firebase satisfied at framework layer only |
| 3 | SoLoader returned `true` without loading anything ("Simulated native library load") | Real desktop native loading: bundled `native/linux-x86_64/libyoga.so` + `native/win-x86_64/yoga.dll` |
| 4 | `CatalystInstanceImpl` was a no-op printer — the JS bundle never executed | **Universal Hermes bridge**: the patched Hermes VM (bytecode v96) executes the app's genuine `index.android.bundle`; JS↔Java calls flow over a stdio NDJSON protocol |
| 5 | No app-agnostic native-module config | `RnModuleConfigGenerator` reflects the app's real `NativeModuleRegistry` (module names, `@ReactMethod` tables, promise/sync IDs, constants) — same contract the C++ bridge builds on device |
| 6 | Yoga was fake ("Simulated: libyoga.so -> SUCCESS"); zero layout | Desktop Yoga 3.x built from source; `RestoreNativeMethods` re-natives the 118 stripped `YogaNative` methods so **real flexbox layout runs** |
| 7 | Framework graphics natives crashed (`ColorSpace$Rgb.nativeCreate` etc.) | 755 framework graphics natives converted to safe stub bodies by the same bytecode tool |
| 8 | `aapt2` hardcoded to `C:\Users\Noval\...` | Bundled `tools/aapt2/{linux-x86_64,win-x86_64}/aapt2` + SDK/PATH fallbacks (`RunnerPaths`) |
| 9 | Google Play checks failed (`error 9`, Dynamite "Invalid GmsCore APK") | **Universal Google Services fixer**: `GooglePlayServicesUtil(Light)`, `GoogleApiAvailability`, `DynamiteModule` shims answer `ConnectionResult.SUCCESS` / local-version, in-process |
| 10 | `META-INF/services` lost by enjarify → kotlinx Main dispatcher missing → navigation dead | Service entries recovered from the APK into the overlay jar |
| 11 | App anti-tamper `System.exit` killed the JVM silently | `RuntimeNeutralizerAgent` (`-javaagent`) rewrites/blocks app-called exits |
| 12 | Per-APK `strings.tsv`/`ids.tsv` committed to the repo | Generated per-APK on boot from `aapt2 dump resources` |
| 13 | `build.bat`/`launch.bat` hardcoded `C:\Users\Noval\...` | Fully dynamic scripts: `build.sh`/`launch.sh` (Linux/macOS) + `build.bat`/`launch.bat` (Windows) |

## Repository layout

```
appplayer/
├── build.sh / launch.sh          Linux/macOS build + run (dynamic paths)
├── build.bat / launch.bat        Windows build + run (dynamic paths)
├── src/                          Runner engine (Java)
│   ├── org/headless/runner/
│   │   ├── AppLauncher.java      main(): boot any APK in-process
│   │   ├── rn/                   ★ Universal React Native bridge
│   │   │   ├── UniversalReactBridge.java   Hermes VM process + stdio protocol
│   │   │   ├── RnModuleConfigGenerator.java reflection-based module config
│   │   │   ├── JsonCodec.java             NDJSON codec
│   │   │   └── JsCallbackBridge.java      Callback/Promise routing
│   │   └── tool/rn/
│   │       ├── RestoreNativeMethods.java  yoga re-native + graphics de-native overlay
│   │       └── ResourceTableGenerator.java per-APK resource tables
│   ├── com/facebook/react/bridge/  bridge shims (classpath-shadowed)
│   ├── com/facebook/soloader/      desktop native loader
│   ├── com/google/android/gms/     universal GMS fixer shims
│   └── android/...                 framework shims
├── tools/
│   ├── hermes-linux/hermes|hermesc  patched Hermes VM v96 (Linux, prebuilt)
│   ├── hermes.exe|hermesc.exe       Hermes VM v96 (Windows, prebuilt)
│   ├── rn/driver.js|driver.hbc      universal RN driver (bridge loop)
│   └── aapt2/{linux-x86_64,win-x86_64}/
├── native/
│   ├── linux-x86_64/libyoga.so      real Yoga engine (desktop)
│   └── win-x86_64/yoga.dll          real Yoga engine (desktop)
├── lib/                             framework + tool jars (android-all-10 from Releases)
├── app.jar / app-patched.jar        decompiled target app (regenerate per APK)
└── data/                            runtime app data (created fresh at boot)
```

## Build & run

### Linux / macOS
```bash
./build.sh                      # compiles engine + overlay + driver.hbc
./launch.sh /path/to/base.apk   # headless boot
./launch.sh /path/to/base.apk --ui          # interactive viewer window
./launch.sh /path/to/base.apk --timeout 30  # bounded run
```

### Windows — first test
```bat
cd /d C:\Users\Noval\Downloads\appplayer-universal-runner\appplayer
build.bat C:\path\to\your-app.apk
launch.bat C:\path\to\your-app.apk --ui
```
The APK is the FIRST argument of both scripts. `build.bat` decompiles the APK
(via enjarify — Python 3 on PATH required) and compiles the engine + RN driver.
`launch.bat` boots it in-process; `--ui` opens the live viewer window.
Any React Native APK works — the runner is app-agnostic. If the log shows
`[-] [RNDriver] This Hermes VM lacks the Universal RN Bridge host functions`,
JS execution needs the universal Hermes VM — see "Hermes VM" below.

### Workflow — one build, any APK
`build.bat`/`build.sh` compile the ENGINE (app-independent — it does not need
your jar to compile) plus per-APP override classes into `bin-app/`. You do NOT
need to delete anything or re-run `build.bat` to switch apps:

```bat
launch.bat C:\path\to\other-app.apk --ui
```

`launch.bat` fingerprints the APK (path + size, stored in
`data\.appjar-src.txt`): when the APK differs from the one `app.jar` was
decompiled from, it automatically re-decompiles via enjarify, regenerates
`app-patched.jar` (annotation recovery + stack-map frame repair), rebuilds the
Yoga/graphics overlay and wipes the stale `bin-app` overrides. Re-running
`build.bat <new-apk>` afterwards is optional — it only recompiles the per-app
override groups (some groups may be skipped for an app whose obfuscated names
do not match; that is normal and logged, not fatal).

### Why stack-map frame repair exists
enjarify emits class files at version 52 (Java 8) **without** StackMapTable
frames. JDK 13+ removed the old-verifier failover, and JDK 21+/25+ silently
ignore `-Xverify:none`, so unpatched jars fail loading with
`java.lang.VerifyError: Expecting a stackmap frame at branch target …`.
The launch pipeline therefore recomputes frames for every class
(`BytecodeFrameNormalizer`) before the app JVM starts — do not remove it.

Notes:
* `lib/android-all-10.jar` (125 MB) is not in git — download from the
  [Releases "lib" tag](https://github.com/allowdoc/appplayer/releases/tag/lib).
  It is already bundled inside the distributed `appplayer-universal-runner.zip`.
* Data dirs are created fresh per package — no session state ships with the repo.

## Hermes VM

The **Linux** patched VM (`tools/hermes-linux/hermes`, bytecode v96 + bridge
host functions) ships ready-to-use. The bundled Windows `tools/hermes.exe`
(v5.3 MB, statically linked CRT) is the **universal build** — it already
contains the six bridge host functions and binds `icuuc64.dll`/`icuin64.dll`
(shipped next to it; also built into Windows 10 1703+). **No local build is
needed on Windows anymore.**

If you ever want to rebuild it yourself (requires CMake 3.13+ and
Visual Studio 2019/2022 C++ workload or Build Tools):

```bat
git clone https://github.com/facebook/hermes.git %USERPROFILE%\hermes-src
cd /d %USERPROFILE%\hermes-src
git checkout hermes-2024-02-20-RNv0.74.0-999cfd9979b5f57b1269119679ab8cdf60897de9
git apply %USERPROFILE%\Downloads\appplayer-universal-runner\appplayer\tools\hermes-patch\hermes-universal-rnbridge.diff
%USERPROFILE%\Downloads\appplayer-universal-runner\appplayer\tools\hermes-patch\build_hermes_windows.bat %USERPROFILE%\hermes-src
```

This rebuilds `tools/hermes.exe` with the six bridge host functions
(`__rnExecuteHBC`, `__rnReadInbox`, `__rnWriteOutbox`, `__rnSyncCall`,
`__rnReadFile`, `__rnGetEnv`). Without it, Windows boots the app and clearly
logs that the universal VM is missing (the driver refuses cryptic failures).

## Universal React Native bridge (how it works)

1. `AppLauncher` boots the real Application/Activities (instrumentation-level,
   in-process, no VM).
2. When an activity creates a `ReactRootView`, the app's own
   `ReactInstanceManager` (real classes from the APK) builds the real
   `NativeModuleRegistry` (103+ modules) through the shadowed bridge classes.
3. `CatalystInstanceImpl.runJSBundle()` starts `UniversalReactBridge`:
   * reflects the registry into `remoteModuleConfig` (the exact JS contract),
   * spawns the patched Hermes VM with `RN_BRIDGE_BUNDLE`/`RN_BRIDGE_CONFIG`,
   * the driver HBC installs `nativeFlushQueueImmediate` / `nativeCallSyncHook`,
     executes the app's **real** `index.android.bundle` (Hermes v96) in the
     same VM, and enters the bridge loop.
4. JS→Native batches (`@@Q [[moduleIds],[methodIds],[params],callId]`) are
   dispatched via reflection into the app's real native modules; callbacks and
   promises route back into JS by encoded callback ID.
5. `UIManager.createView/setChildren/manageChildren` create the app's **real**
   React views; Yoga computes layout through the desktop engine; timers are
   owned by the bridge (`JSTimers.callTimers`) so RN scheduling works.
6. `HeadlessViewerWindow` (`--ui`) renders the live view tree and dispatches
   mouse clicks into the app.

## Google Services fixer

`SyntheticGmsBroker` answers every GMS service binding in-process with
`ConnectionResult.SUCCESS`; the `GooglePlayServicesUtil` / `GoogleApiAvailability`
/ `DynamiteModule` classpath shims report GMS as available and Dynamite as
local, so any APK's Google/Firebase clients initialize without timeouts,
hangs, or — importantly — **without any fake tokens written into app data**.
