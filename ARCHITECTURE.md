# In-Process Universal Android Runtime Engine ("Android Wine")
**Status & Architecture Reference for Autonomous Coding Agents**

---

## 1. Project Vision & Fundamental Invariants
This project builds a **lightweight, universal in-process Android runtime engine** that executes unmodified production Android APKs directly on standard **OpenJDK 25 (Java VM)** on both **Windows and Linux**.

### 🚫 Non-Negotiable Invariants (DO NOT VIOLATE):
1. **ZERO VM, ZERO QEMU, ZERO WSL, ZERO DAEMONS:**
   - Under **NO** circumstances should you suggest, install, or wrap WSL, Docker, QEMU, Anbox, Waydroid, or an Android Virtual Device (AVD).
   - The entire engine must run directly as a single host OS process (`java.exe` / `java`).
2. **Performance Budget:**
   - **Process RAM:** Must stay strictly **under 60 MB** (currently running at **~41 MB**).
   - **Cold Boot Time:** Under **5.0 seconds** to interactive state.
3. **Headless Execution First, Lightweight UI Second:**
   - The primary goal is **headless app execution**: booting the Android framework, running background handlers, direct networking, and lifecycle state machines.
   - The UI is strictly an **inspection and interaction surface** to visually verify the screen and dispatch manual user/automation actions (clicks, key events). It does not need a heavy GPU emulator.
4. **No Fake Facades or App-Specific Hacks:**
   - Do **NOT** hardcode app-specific class names, package names, or mock DTOs into engine classes.
   - The engine must provide a **genuine, universal Android framework environment** (Looper, Binder, Services, Layouts, ContentProviders, SQLite) that lets ANY APK run its real bytecode and logic.
5. **Universal Cross-Platform:**
   - Works identically on Windows and native Linux without OS-specific emulator dependencies.

---

## 2. System Architecture & Component Map

The codebase is located at: `C:\Users\Noval\.gemini\antigravity\scratch\android-runner`

### A. Core Lifecycle & Event Loop
* **`src/org/headless/runner/AppLauncher.java`**:
  - The entry point (`main`).
  - Initializes Android Main Looper (`Looper.prepareMainLooper()`).
  - Runs the background Looper message pump thread (`MainLooperPump`) draining ready tasks continuously.
  - Delegates `ArchTaskExecutor` to route background/main-thread Android architecture components.
  - Instantiates `Application` via reflection, attaches `SyntheticContext`, and invokes `Application.onCreate()`.
  - Attaches the primary Activity and pushes it to `ActivityStack`.
  - Generic launch flow for any APK (no app-specific navigation testing in mainline execution).
* **`src/org/headless/runner/core/ActivityStack.java`**:
  - Maintains the in-process Activity task stack (`mStack`).
  - Manages Android Activity lifecycle: `performCreate()` $\to$ `performStart()` $\to$ `performResume()`.
  - Maps activity tokens (`IBinder`) to `Activity` instances in `mTokenMap` for token-accurate `finishActivity()` routing.
  - Automatically notifies stack changes and captures screen frames via `notifyStackChanged()` on lifecycle transitions.
* **`src/org/headless/runner/core/SyntheticInstrumentation.java`**:
  - Subclasses `android.app.Instrumentation`.
  - Intercepts `execStartActivity()` to seamlessly route activity transitions inside the same process.
  - Automatically grants runtime permissions when intercepted.

### B. In-Process Service Registry (Zero IPC / Synthetic Binder)
* **`src/org/headless/runner/core/SyntheticServiceManager.java`**:
  - Populates Android's `ServiceManager.sCache` using dynamic reflection proxies (`createSyntheticProxy`).
  - Completely eliminates IPC / Binder driver requirements by fulfilling Android service AIDL interfaces in-process:
    - `activity` / `activity_task`: `IActivityManager` and `IActivityTaskManager` (handles `finishActivity`, task descriptions).
    - `package`: `IPackageManager` (handles component lookups, signatures, installers).
    - `window`: `IWindowManager` (returns screen density 420 dpi, rotation 0).
    - `uimode`: `IUiModeManager` (returns `MODE_NIGHT_NO`).
    - `connectivity`: `IConnectivityManager` (reports active connected Wi-Fi/LTE network).
    - `alarm`: `IAlarmManager` (timer management).
    - `alarm`: `IAlarmManager` (real timer scheduling via `ScheduledExecutorService`).
    - `jobscheduler`: `IJobScheduler` (synthetic job execution via `SyntheticJobScheduler`).
    - `display`: `DisplayManagerGlobal` (in-process display instance).
    - `telephony.registry` / `phone`: `ITelephony` and `ITelephonyRegistry`.

### C. Headless Task Runner & Background Service Pipeline
* **`src/org/headless/runner/core/HeadlessTaskDispatcher.java`**:
  - In-process background service and task lifecycle manager.
  - **Service Lifecycle:** Implements `startService()`, `startForegroundService()`, `bindService()`, `stopService()`, `unbindService()` using real `android.app.Service.attach()` and `onCreate()` / `onStartCommand()` invocations on daemon worker threads.
  - **JobService Execution:** Dispatches `JobService.onStartJob()` with synthetic `JobParameters` and tracks completion callbacks (`jobFinished`).
  - **Broadcast Dispatch:** In-memory registry of dynamic `BroadcastReceiver`s; dispatches `sendBroadcast()` to matching `IntentFilter`s on the main thread/Looper.
* **`src/org/headless/runner/core/SyntheticJobScheduler.java`**:
  - Implements `android.app.job.IJobScheduler`.
  - Registered into `SyntheticServiceManager` under `jobscheduler`.
  - Automatically captures `schedule()`, `enqueue()`, `cancel()` and routes tasks directly to `HeadlessTaskDispatcher`.
* **Universal Manifest Service Discovery & Push Token Provisioning:**
  - Automated extraction of manifest `<service>` declarations (`ManifestExtractor`).
  - Automated provisioning of synthetic FCM / push notification token in `com.google.android.gms.appid` so backend network sync and device registration authenticate without stalling.

### D. Resource & Layout Inflation Subsystem
* **`src/org/headless/runner/core/AxmlLayoutTreeParser.java`**:
  - Uses `aapt2 dump xmltree` and `aapt2 dump resources` to extract compiled 32-bit binary XML resource IDs and trees directly from the APK.
* **`src/org/headless/runner/core/RealXmlResourceParser.java`**:
  - Implements `XmlResourceParser` / `AttributeSet`.
  - Maintains `Node` tree with populated attribute maps (`attrResIds` and `attrValues`).
* **`src/android/content/res/TypedArray.java`**:
  - Two-way attribute resolution resolving resource IDs (e.g. `android:id` `0x010100d0`, `layout_width` `0x010100f4`, `visibility` `0x010100dc`) directly from the XML node.
* **`src/org/headless/runner/core/SyntheticLayoutInflater.java`**:
  - Instantiates real Android view classes (`ViewGroup`, `TextView`, `Button`, `ImageView`, `RecyclerView`, `DrawerLayout`) in memory from the parsed XML.

### D. Universal 2D Spatial View Renderer & Viewer
* **`src/org/headless/runner/ui/HeadlessScreenCapture.java`**:
  - Universal 2D Spatial View Renderer. Walks genuine `DecorView` $\to$ `ViewGroup` $\to$ `View` hierarchies, computing exact screen pixel coordinates:
    $$\text{screenX} = \text{parentX} + \text{view.getLeft()}, \quad \text{screenY} = \text{parentY} + \text{view.getTop()}$$
  - Renders genuine backgrounds, typography, gravity alignments, and vector glyphs (`←`, `✓`, dividers, rounded buttons).
  - Generic splash and icon rendering derived from package identity without hardcoded app strings.
* **`src/org/headless/runner/ui/HeadlessViewerWindow.java`**:
  - Interactive desktop window (`--ui`) providing live view inspection and mouse-click dispatch.
  - Automatically observes `ActivityStack.getTopActivity()` and refreshes in real-time.
  - Dispatches clicks via `SpatialHitTarget` directly into Android's `performClick()`.

### E. Persistent KeyStore & Real Database Engine
* **`src/org/headless/runner/core/SyntheticKeyStoreProvider.java`**:
  - Pure Java in-process JCA security provider for `"AndroidKeyStore"`.
  - Binary disk serialization at `data/.keystore.dat`.
  - Supports AES-GCM, HMAC, RSA keys, and synthetic X.509 certificate chains.
  - Eliminates `AEADBadTagException: Tag mismatch` during `EncryptedSharedPreferences.create()`.
* **`lib/sqlite-jdbc.jar` & `src/android/database/`**:
  - Self-contained SQLite JDBC driver (`org.xerial:sqlite-jdbc:3.36.0.3`).
  - Pure Java in-memory `CursorWindow.java` storing typed cell arrays (`Object[]`).
  - Pure Java `SQLiteConnection.java` executing genuine SQLite queries, DDL, and PRAGMAs.
  - Completely eliminates the infinite `[D/SQLiteCursor] received count(*) from native_fill_window: 0` polling loop.

### F. Google Play Services & Universal ContentProvider Shims
* **`src/org/headless/runner/core/SyntheticGmsBroker.java`**:
  - Pure Java `IGmsServiceBroker` Binder implementation.
  - Intercepts GMS client service bindings (`bindService`).
  - Replies with `onPostInitComplete(0, internalService, bundle)` (`ConnectionResult.SUCCESS = 0`) to all GMS client queries (Location, Auth, FCM, AppMeasurement).
  - Guarantees instant connection state for any app using Google Play Services with zero timeouts or hangs.
* **`src/org/headless/runner/core/SyntheticContentResolver.java`**:
  - Dynamically registers and routes manifest `ContentProvider`s (`FileProvider`, `MoEngageFileProvider`, `BugsnagContentProvider`, etc.) directly to `cp.getIContentProvider()`.
  - Provides synthetic system settings proxy for `android_id`, `font_scale`, and `screen_off_timeout`.
* **`src/org/headless/runner/core/SyntheticPackageManager.java`**:
  - Dynamic discovery of services, providers, and receivers.
  - Injects metadata for `androidx.startup.InitializationProvider` (automatically initializing AndroidX WorkManager, EmojiCompat, and ProcessLifecycle).

### G. Native C++ / JNI React Native Bridge
* **`src/com/facebook/react/bridge/CatalystInstanceImpl.java`**:
  - Pure Java `CatalystInstance` and `Builder` implementation.
  - Completely bypasses `libreactnativejni.so` and JNI bridge initialization.
  - Initializes `NativeModuleRegistry` and dispatches `AppRegistry` calls (`runApplication`, `startHeadlessTask`).

---

## 3. Key Milestones Completed

1. **Persistent AndroidKeyStore Implemented:**
   - Disk persistence to `data/.keystore.dat` resolves crypto tag mismatches permanently across process restarts.
2. **Real SQLite JDBC & CursorWindow Integration:**
   - Standalone `sqlite-jdbc` integration completely resolves the 0-row SQLite query loop, allowing Room migrations and databases to initialize.
3. **Universal GMS Broker Shim & ContentProvider Routing:**
   - Intercepts all GMS service connections with `SyntheticGmsBroker` providing instant `ConnectionResult.SUCCESS`.
   - Routes ContentProvider queries dynamically to real in-process providers.
4. **Android Main Looper Keep-Alive Loop:**
   - Main thread continuously runs `Looper.loop()` with automatic exception recovery.
   - Dedicated daemon thread `HeadlessKeepAliveMonitor` logs real-time memory and task metrics.
5. **Pure Java React Native Bridge:**
   - `CatalystInstanceImpl` eliminates `libreactnativejni.so` crashes, supporting both UI view registration and background HeadlessJsTasks.
6. **Verified Performance:**
   - **RAM Footprint:** ~44 MB (strictly below 60 MB limit).
   - **Boot Time:** 6.1 seconds.
   - **Status:** Single-process OpenJDK 25, zero VM, zero QEMU, zero daemons.


---

## 4. Version 2 Additions — Universal React Native Runtime (this revision)

### H. Universal Hermes stdio Bridge (replaces the pure-Java fake bridge)
* **`src/org/headless/runner/rn/UniversalReactBridge.java`**:
  Spawns the patched Hermes VM (`tools/hermes-linux/hermes` on Linux,
  `tools/hermes.exe` on Windows — bytecode version 96, RN 0.72–0.76 era) with:
  - `RN_BRIDGE_BUNDLE` — the app's real `assets/index.android.bundle`
    (extracted from the APK automatically, universal),
  - `RN_BRIDGE_CONFIG` — `remoteModuleConfig` reflected from the app's own
    `NativeModuleRegistry` (`RnModuleConfigGenerator`).
  Protocol (NDJSON over stdio):
  - JS→Native batches: `@@Q [[moduleIds],[methodIds],[params],callId]`,
  - Native→JS: `{type:'call'|'callback'|'eval'|'die'}` inbox lines,
  - Synchronous hooks: `@@SYNC <req>` → one reply line.
  `ArgumentExtractor` semantics are replicated exactly (regular args from the
  front, Callback/Promise ids from the end). Timers are owned by the bridge
  (`Timing.createTimer` → scheduled executor → `JSTimers.callTimers`) so RN
  scheduling works without Choreographer.

### I. Patched Hermes VM + driver
* Hermes (tag `hermes-2024-02-20-RNv0.74.0`) patched in
  `lib/ConsoleHost/ConsoleHost.cpp` with six host functions:
  `__rnReadInbox`, `__rnWriteOutbox`, `__rnSyncCall`, `__rnReadFile`,
  `__rnExecuteHBC` (executes the app HBC **inside the same VM instance**),
  `__rnGetEnv`.
* `tools/rn/driver.js` (compiled to `driver.hbc` v96) installs RN globals,
  loads the app bundle, flushes the initial queue and pumps the bridge loop.

### J. `RestoreNativeMethods` bytecode normalizer
* Overlay jar (above `app-patched.jar` on the classpath) that:
  1. **Re-natives** the 118 `com/facebook/yoga/YogaNative` methods the
     de-nativization pipeline had stripped (with stub bodies removed via ASM),
     enabling genuine `RegisterNatives` binding against the desktop Yoga,
  2. **De-natives** 755 `android/graphics/**` framework natives into safe
     default-returning bodies (ColorSpace, VectorDrawable, ...),
  3. **Recovers `META-INF/services/`** from the APK (enjarify drops them),
     restoring ServiceLoader flows (kotlinx Main dispatcher etc.).

### K. Desktop Yoga engine
* `native/linux-x86_64/libyoga.so` + `native/win-x86_64/yoga.dll`
  (yoga main-branch JNI, cross-compiled for Windows). `SoLoader.loadLibrary`
  loads these genuinely (Desktop Bridge Mode) — real flexbox layout for RN.

### L. Universal Google Services fixer (framework layer only)
* `GooglePlayServicesUtil(Light)`, `GoogleApiAvailability`, `DynamiteModule`
  classpath shims answer SUCCESS / local-version for ANY APK.
* **No synthetic tokens, no fake session keys, no prefilled app state** —
  `data/` ships empty; apps boot in pristine fresh-install state. The
  previous synthetic FCM token provisioning in `AppLauncher` was removed.

### M. Universal paths & per-APK resource tables
* `RunnerPaths` — single source of runner-relative paths; aapt2 resolved from
  `tools/aapt2/<os>/` (bundled), SDK, or PATH.
* `ResourceTableGenerator` builds `data/{ids,strings}.tsv` per APK on boot.
* Engine hardcoding removed: scratch Airtel tests deleted, DeviceProfile
  carrier-neutral, SQLiteConnection paths derived from package name.

---

## 5. Version 3 Additions — Real Resource Runtime & Runner-Compat Patches

### N. Compiled resource table runtime (`ArscParser`)
* `src/org/headless/runner/core/ArscParser.java` parses `resources.arsc`
  directly from ANY APK (global value pool, per-package type/key pools,
  TYPE chunks with configs, simple values AND bags/parents):
  * resid → (type, name, value/bag), name → resid, style parent chains,
  * theme resolution: `resolveTheme(styleResId)` walks the parent chain and
    returns attr-id → resolved value (child overrides parent).
* Wired into `android.content.res.Resources` / `Resources.Theme` /
  `TypedArray`:
  * `Theme.applyStyle` + `resolveAttribute` + the three
    `obtainStyledAttributes` overloads now return REAL theme values,
  * view inflation cascades: XML node attrs → defStyleAttr → style → theme,
  * `getString/getColor/getDimension/getBoolean/getInteger/getValue` resolve
    from the compiled table (TSV tables remain as fallback only).
* `ManifestExtractor` now parses `android:theme` (0x01010000) for
  application + activities; `AppLauncher` applies the activity theme BEFORE
  `onCreate` so AppCompat/PhoneWindow see genuine window attributes.
* `AxmlLayoutTreeParser` maps layout resIds to their REAL res paths
  (obfuscated names like `res/-8.xml` supported) via the arsc table —
  `Resources.getLayout` inflates genuine appcompat/app XML layouts
  (`abc_screen_simple` → FitWindowsLinearLayout → ContentFrameLayout →
  ReactRootView reached in testing).

### O. Runner-compat bytecode patches (in `InjectAnnotations`, applied per APK)
* `ModuleHolder.getCanOverrideExistingModule` / `BaseJavaModule.canOverrideExistingModule`
  → `true`: duplicate RN native module registration (manual package + autolink)
  degrades to replace-instead-of-throw.
* `FabricUIManagerProviderImpl.createUIManager`: the C++-only
  RuntimeExecutor/RuntimeScheduler null-check throw is bypassed (pattern
  matched, 4 `ifnull`); `FabricUIManagerBinding.register(...)` → no-op;
  `FabricUIManager.startSurface` → no-op. Bridge-mode React context creation
  completes on the JVM.
* EVERY app class now gets `stubNativeMethods` (expo modules, vendor SDKs —
  no more `UnsatisfiedLinkError` killing the React context thread).
* `AnnotationPipeline` ALWAYS produces `app-patched.jar` (even with zero
  Retrofit annotations) so patches apply uniformly; the marker fingerprint
  includes a compat version so jars rebuild after runner updates.
* **Module name fix:** `RnModuleConfigGenerator` derives the JS module name
  from `@ReactModule(name=...)` (canonical: `PlatformConstants`,
  `NativeModulesProxy`, ...). Expo aliases exported: `NativeModulesProxy`
  also as `EXNativeModulesProxy`; `ExpoBridge` also as `ExpoModulesCore`.

### P. Universal Google Services fixer (framework layer)
* `AppLauncher` pre-initializes `FirebaseApp` with synthetic options when the
  APK ships no `google-services.json` (FirebaseInitProvider now reports
  `FirebaseApp initialization successful`). Real values are left untouched.
* Synthetic `google_api_key` / `google_crash_reporting_api_key` /
  `google_storage_bucket` strings registered alongside the existing ones.

### Q. Build hardening (Windows)
* `build.bat` pass 2 now uses a UNIQUE temp dir per override group (a locked
  leftover `%TEMP%\binapp-tmp` made EVERY group fail with "does not fit THIS
  apk") and prints the first javac errors for diagnosable skips.
* `launch.bat` passes `-Dheadless.runner.dir` / `-Dheadless.runner.apk`
  (the Catalyst shim needs the real APK path to extract the JS bundle).
* Render path fixed: `HeadlessScreenCapture.renderToImage` no longer mutates
  the view tree (the Live Player painted at ~16 fps and spammed fragment
  transactions off the main thread); `ViewPagerPopulator` logs a single
  warning instead of one per frame.

### R. Verified end-to-end on an Expo SDK 52 / RN new-arch APK (test.com.test)
* Boot: theme resolved → AppCompat subdecor inflated → ReactRootView attached
  → React context created (47 modules) → **real Hermes VM executed the app's
  own `index.android.bundle`** → module registry live (48 specs) →
  JS↔Native batches flowing (`ExceptionsManager.reportException` reached the
  Java dispatcher).
* Remaining known gap (next milestone): expo-modules-core's JS event system
  (`NativeModulesProxy.EventEmitter` / `requireNativeModule('ExpoModulesCore')`)
  initializes lazily; until TurboModule-style event surfaces are bridged,
  Expo apps stop after bundle top-level. Bridge/non-Expo RN apps proceed to
  `AppRegistry.runApplication` and UIManager view creation.

### S. Gap list — what still separates this from a full emulator
1. **JS-driven UI completion for Expo/new-arch apps** (above) — the last
   functional gap before arbitrary RN apps render their real screens.
2. **Drawing:** framework `Canvas`/`RenderNode` natives are stubbed; the
   player paints the view tree (backgrounds, text, layout) directly. Rich
   app drawing (custom Views, shaders) needs a desktop Skia bridge.
3. **Assets/9-patches/drawables:** `resources.arsc` values are real, but
   `res/` file contents (bitmaps, raw XML drawables) still need real decoding
   (VectorDrawable/NinePatch) instead of `SyntheticDrawable`.
4. **System services breadth:** InputMethod, Clipboard (sync), Notification
   manager surface, alarms exact semantics — partially stubbed.
5. **Hidden framework APIs / dex2oat-equivalent speed:** enjarify is a
   one-time cost, but cold-boot still pays annotation recovery + frame
   normalization per APK (cached afterwards).
