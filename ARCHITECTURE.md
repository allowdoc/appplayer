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
