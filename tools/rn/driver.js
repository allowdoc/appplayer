// Universal React Native Driver (appplayer / Android Wine)
// Compiled to HBC with patched hermesc, executed by patched hermes VM.
// Protocol: NDJSON lines prefixed with @@ on stdout; inbox request lines on stdin.
//
// Resilience: if the VM is a STOCK hermes build (no __rn* host functions),
// the driver reports it clearly and exits instead of crashing cryptically.

function rnLog(s) { print('[RNDriver] ' + s); }

if (typeof __rnGetEnv !== 'function') {
  print('[-] [RNDriver] This Hermes VM lacks the Universal RN Bridge host');
  print('[-] [RNDriver] functions (__rnGetEnv etc). A patched hermes VM is');
  print('[-] [RNDriver] required: tools/hermes-linux/hermes (Linux, bundled)');
  print('[-] [RNDriver] or build one for Windows via build_hermes_windows.sh.');
  print('[-] [RNDriver] See README.md section "Hermes VM".');
  quit();
}

var CONFIG_PATH = __rnGetEnv('RN_BRIDGE_CONFIG');
var BUNDLE_PATH = __rnGetEnv('RN_BRIDGE_BUNDLE');

if (!BUNDLE_PATH) {
  print('[-] [RNDriver] RN_BRIDGE_BUNDLE env var not set — nothing to execute.');
  quit();
}

// ---- 1. Load native module config produced by the Java runner ----
var config = null;
try {
  if (CONFIG_PATH) {
    var raw = __rnReadFile(CONFIG_PATH);
    if (raw) config = JSON.parse(raw);
  }
} catch (e) {
  rnLog('config parse failed: ' + e);
}
if (!config) config = { remoteModuleConfig: [] };
globalThis.__fbBatchedBridgeConfig = config;

// ---- 2. Core host-function polyfills expected by RN ----
// nativePerformanceNow is installed by RN's native side on a device; React's
// Scheduler uses it for every scheduleCallback deadline. Without it, the
// FIRST render runs but every setState afterwards silently dies and the app
// freezes on its root view forever.
globalThis.nativePerformanceNow = function () {
  return Date.now();
};
// Release Hermes swallows unhandled promise rejections silently (RN only
// installs rejection reporting in dev). Route them to the log so any async
// failure in the app is visible.
try {
  HermesInternal.setPromiseRejectionTrackingHook(function (promise, reason) {
    rnLog('UNHANDLED REJECTION: ' + (reason && reason.stack ? reason.stack : String(reason)));
  });
  HermesInternal.enablePromiseRejectionTracker();
  rnLog('promise rejection tracking installed');
} catch (eRej) {
  rnLog('rejection tracking unavailable: ' + eRej);
}
globalThis.nativeFlushQueueImmediate = function (queue) {
  try {
    if (queue && queue[0] && queue[0].length) {
      rnLog('flush ' + queue[0].length + ' call(s): mods=' + JSON.stringify(queue[0]).slice(0, 200));
    }
  } catch (e) {}
  __rnWriteOutbox('@@Q ' + JSON.stringify(queue));
};
globalThis.nativeCallSyncHook = function (moduleID, methodID, args) {
  var res = __rnSyncCall(JSON.stringify([moduleID, methodID, args]));
  try { rnLog('sync ' + moduleID + ':' + methodID + ' -> ' + String(res).slice(0, 120)); } catch (e) {}
  return res === null || res === '' ? null : JSON.parse(res);
};
globalThis.nativeRequireModuleConfig = null; // full config provided upfront

// ---- 2a. TurboModuleRegistry bridge (RN 0.68+) -------------------------------
// On a device the native TurboModuleManager installs globalThis
// .__turboModuleProxy; TurboModuleRegistry.getEnforcing('PlatformConstants')
// and friends resolve core modules through it. The runner exposes the same
// hook over the stdio bridge, dispatching by module NAME (the runner keeps
// its spec map by name) - identical semantics, no per-app knowledge.
globalThis.__rnNativeCallByName = function (module, method, params) {
  var b = __fbBatchedBridge;
  return new Promise(function (resolve, reject) {
    try {
      // moduleId=-1/methodId=-1: name-based dispatch. Trailing callback ids
      // ride the same params array exactly like the id-based path.
      b.enqueueNativeCall(-1, -1, [module, method, params || []],
        function (failArgs) {
          var info = failArgs && failArgs.length ? failArgs[0] : null;
          var msg = info && typeof info === 'object'
            ? ((info.message || info.code || 'native error') + '') : String(info);
          reject(new Error(msg));
        },
        function (succArgs) {
          resolve(succArgs && succArgs.length ? succArgs[0] : null);
        });
      var q = b.flushedQueue();
      if (q && q[0] && q[0].length) __rnWriteOutbox('@@Q ' + JSON.stringify(q));
    } catch (eQ) { reject(eQ); }
  });
};
globalThis.__rnNativeCallByNameSync = function (module, method, params) {
  var res = __rnSyncCall(JSON.stringify([-1, -1, [module, method].concat(params || [])]));
  return res ? JSON.parse(res) : null;
};
globalThis.__turboModuleProxy = function (name) {
  var obj = { __rnTurboModuleName: name };
  obj.getConstants = function () {
    try {
      var c = __rnNativeCallByNameSync(name, 'getConstants', []);
      return c || {};
    } catch (e) { return {}; }
  };
  // Turbo module methods: try the SYNC path first (Cxx modules like
  // DeviceInfo/FeatureFlags resolve synchronously on a device). Promise
  // methods answer with a {__rnAsync:true} marker and re-route through the
  // async name dispatch, whose callbacks the runner resolves over stdio.
  return new Proxy(obj, {
    get: function (target, prop) {
      if (prop in target) return target[prop];
      if (typeof prop !== 'string' || prop === 'then') return undefined;
      return function () {
        var args = Array.prototype.slice.call(arguments);
        try {
          var r = __rnNativeCallByNameSync(name, String(prop), args);
          if (r && typeof r === 'object' && r.__rnAsync) {
            return __rnNativeCallByName(name, String(prop), args);
          }
          return r;
        } catch (e) {
          return __rnNativeCallByName(name, String(prop), args);
        }
      };
    },
  });
};

// ---- 2b. Universal expo native-globals shim (globalThis.expo) ----------------
// expo-modules-core installs globalThis.expo = { EventEmitter, NativeModule,
// SharedObject, modules.core, uuidv4, uuidv5, ... } from its C++ JSI bindings
// on a real device. Apps then read `globalThis.expo.EventEmitter` at module
// init (see expo-modules-core/src/EventEmitter.ts) and abort with
// "TypeError: Cannot read property 'EventEmitter' of undefined" when the
// native bindings are absent — which killed the whole module init chain
// before AppRegistry.runApplication could mount the UI.
// The runner provides the same environment in pure JS (universal runner
// infrastructure, not per-app code): a self-contained EventEmitter, the
// base-class placeholders expo expects, and the install markers
// ensureNativeModulesAreInstalled() looks for.
if (!globalThis.expo) {
  function ExpoEventEmitterShim(nativeModule) {
    this._nativeModule = nativeModule || null;
    this._listeners = {};
  }
  ExpoEventEmitterShim.prototype.addListener = function (eventName, listener) {
    var list = (this._listeners[eventName] = this._listeners[eventName] || []);
    list.push(listener);
    var self = this;
    return {
      remove: function () {
        var arr = self._listeners[eventName] || [];
        var i = arr.indexOf(listener);
        if (i >= 0) arr.splice(i, 1);
      },
    };
  };
  ExpoEventEmitterShim.prototype.removeAllListeners = function (eventName) {
    if (eventName === undefined || eventName === null) {
      this._listeners = {};
    } else {
      delete this._listeners[eventName];
    }
  };
  ExpoEventEmitterShim.prototype.emit = function (eventName) {
    var args = Array.prototype.slice.call(arguments, 1);
    var arr = (this._listeners[eventName] || []).slice();
    for (var i = 0; i < arr.length; i++) {
      try { arr[i].apply(null, args); } catch (e) { rnLog('expo shim listener error: ' + e); }
    }
  };

  function uuidReplace(char) {
    var r = Math.floor(Math.random() * 16);
    var v = char === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  }
  globalThis.expo = {
    EventEmitter: ExpoEventEmitterShim,
    NativeModule: function ExpoNativeModuleBase() {},
    SharedObject: function ExpoSharedObjectBase() {},
    // Marker consumed by expo-modules-core ensureNativeModulesAreInstalled()
    modules: {
      core: { installModules: function () {}, modulesInstalled: true },
      expoModulesCore: { modulesInstalled: true },
    },
    uuidv4: function () {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, uuidReplace);
    },
    uuidv5: function (name, namespace) {
      // Deterministic pseudo-v5 (desktop runner placeholder; same input
      // always maps to the same id within this process).
      var h = 5381;
      var s = String(namespace) + '/' + String(name);
      for (var i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) & 0xffffffff;
      var hex = ('00000000' + (h >>> 0).toString(16)).slice(-8);
      return hex + '-8' + hex.slice(1, 4) + '-5' + hex.slice(1, 4) + '-' + hex.slice(0, 4) + '-' + hex + hex.slice(0, 4);
    },
    getViewConfig: function (viewModuleName) {
      // Real expo installs this via native JSI. Rebuild the view config from
      // the viewManagersMetadata the adapter module exports in its constants.
      try {
        var vmm = null;
        var rmc2 = (config && config.remoteModuleConfig) || [];
        for (var vi = 0; vi < rmc2.length; vi++) {
          if (rmc2[vi] && rmc2[vi][0] === 'NativeUnimoduleProxy' && rmc2[vi][1]
              && rmc2[vi][1].viewManagersMetadata) {
            vmm = rmc2[vi][1].viewManagersMetadata;
            break;
          }
          if (rmc2[vi] && rmc2[vi][0] === 'NativeModulesProxy' && rmc2[vi][1]
              && rmc2[vi][1].viewManagersMetadata) {
            vmm = rmc2[vi][1].viewManagersMetadata;
          }
        }
        if (!vmm) return null;
        var meta = vmm[viewModuleName] || vmm['ViewManagerAdapter_' + viewModuleName] || null;
        if (!meta) return null;
        var validAttributes = {};
        var props = meta.propsNames || [];
        for (var pi = 0; pi < props.length; pi++) validAttributes[props[pi]] = true;
        return {
          uiViewClassName: viewModuleName,
          bubblingEventTypes: {},
          directEventTypes: {},
          validAttributes: validAttributes,
        };
      } catch (eVC) {
        return null;
      }
    },
  };
  rnLog('globalThis.expo shim installed (EventEmitter, NativeModule, SharedObject, modules.core)');
}

// ---- 2c. Expo Kotlin module proxies (requireNativeModule support) ----------
// Expo SDK 50+ resolves native modules via globalThis.expo.modules[<name>]
// (JSI host objects on a real device). The Java runner exports the app's own
// expo module metadata ("expoModules" config section) and dispatches calls
// through the ExpoKotlinDispatcher pseudo module, so the same JS contract
// works here without any C++ JSI runtime.
try {
  var expoMeta = (config && config.expoModules) || {};
  var dispatcherId = -1;
  var dispatcherSyncMethodId = -1;
  var rmc = (config && config.remoteModuleConfig) || [];
  for (var ri = 0; ri < rmc.length; ri++) {
    if (rmc[ri] && rmc[ri][0] === 'ExpoKotlinDispatcher') {
      dispatcherId = ri;
      var syncIds = rmc[ri][4];
      if (syncIds && syncIds.length) dispatcherSyncMethodId = syncIds[0];
      break;
    }
  }
  if (dispatcherId >= 0 && Object.keys(expoMeta).length) {
    globalThis.__expoRunnerCallbacks = {};
    var __expoRunnerCbSeq = 0;
    // Dispatcher method ids in the numeric bridge config: callMethod (async
    // promise calls) and callSync are resolved from the same config Java used
    // to build remoteModuleConfig, so ids always match on both sides.
    var dispatcherCallMethodId = -1;
    var legacyModuleId = -1;
    var legacyCallMethodId = -1;
    for (var di = 0; di < rmc.length; di++) {
      if (rmc[di] && rmc[di][0] === 'ExpoKotlinDispatcher') {
        dispatcherCallMethodId = (rmc[di][2] || []).indexOf('callMethod');
      }
      if (rmc[di] && rmc[di][0] === 'NativeModulesProxy') {
        legacyModuleId = di;
        legacyCallMethodId = (rmc[di][2] || []).indexOf('callMethod');
      }
    }
    // Universal native-bridge promise call: enqueue a REAL numeric native
    // call exactly like RN's NativeModules.genMethod does
    // (enqueueNativeCall appends the RN-0.81 callback ids callID<<1 /
    // callID<<1|1 itself), then flush the queue over the outbox so Java
    // dispatches it. This is the same traffic path every RN native module
    // uses - no JS-callable-module hacks that could never reach native.
    var __rnNativeCall = function (moduleId, methodId, params) {
      var b = __fbBatchedBridge;
      return new Promise(function (resolve, reject) {
        try {
          b.enqueueNativeCall(moduleId, methodId, params,
            function (failArgs) {
              var info = failArgs && failArgs.length ? failArgs[0] : null;
              var msg = info && typeof info === 'object'
                ? ((info.message || info.code || 'expo error') + '') : String(info);
              reject(new Error(msg));
            },
            function (succArgs) {
              resolve(succArgs && succArgs.length ? succArgs[0] : null);
            });
          var q = b.flushedQueue();
          if (q && q[0] && q[0].length) {
            __rnWriteOutbox('@@Q ' + JSON.stringify(q));
          }
        } catch (eQ) { reject(eQ); }
      });
    };
    Object.keys(expoMeta).forEach(function (moduleName) {
      var meta = expoMeta[moduleName] || {};
      var obj = {};
      var consts = meta.constants || {};
      Object.keys(consts).forEach(function (k) { obj[k] = consts[k]; });

      if (moduleName === 'NativeModulesProxy') {
        // The legacy expo proxy must expose the adapter module's REAL metadata
        // (viewManagersMetadata / modulesConstants / exportedMethods) plus a
        // queue-based dispatcher per legacy module - exactly what the native
        // adapter provides on a device.
        for (var qi = 0; qi < rmc.length; qi++) {
          if (!(rmc[qi] && rmc[qi][0] === 'NativeModulesProxy')) continue;
          var qConsts = rmc[qi][1] || {};
          Object.keys(qConsts).forEach(function (qk) { obj[qk] = qConsts[qk]; });
          obj.__moduleId = qi;
          obj.__callMethodId = (rmc[qi][2] || []).indexOf('callMethod');
          obj.__callSyncId = (rmc[qi][4] || []).indexOf('callSync');
          break;
        }
        var legacyQueueCall = function (legacyModule, meth, args) {
          if (legacyModuleId < 0 || legacyCallMethodId < 0) {
            return Promise.reject(new Error('NativeModulesProxy bridge ids unavailable'));
          }
          return __rnNativeCall(legacyModuleId, legacyCallMethodId,
            [legacyModule, meth, args]);
        };
        var exportedMethods = obj.exportedMethods || {};
        Object.keys(exportedMethods).forEach(function (legacyModule) {
          var methodsMeta = exportedMethods[legacyModule] || {};
          var methodNames = methodsMeta.methodNames || methodsMeta.keys || [];
          var stub = { moduleName: legacyModule };
          for (var mi = 0; mi < methodNames.length; mi++) {
            (function (methName) {
              stub[methName] = function () {
                return legacyQueueCall(legacyModule, methName,
                  Array.prototype.slice.call(arguments));
              };
            })(methodNames[mi]);
          }
          stub.getConstants = function () { return methodsMeta.constants || {}; };
          obj[legacyModule] = stub;
        });
        obj.callMethod = function (legacyModule, meth, args) {
          return legacyQueueCall(legacyModule, meth, args);
        };
        globalThis.expo.modules[moduleName] = obj;
        return;
      }
      (meta.async || []).forEach(function (m) {
        obj[m] = function () {
          if (dispatcherCallMethodId < 0) {
            return Promise.reject(new Error('ExpoKotlinDispatcher bridge ids unavailable'));
          }
          return __rnNativeCall(dispatcherId, dispatcherCallMethodId,
            [moduleName, m, Array.prototype.slice.call(arguments)]);
        };
      });
      (meta.sync || []).forEach(function (m) {
        obj[m] = function () {
          var args = Array.prototype.slice.call(arguments);
          if (dispatcherSyncMethodId < 0) return null;
          var res = nativeCallSyncHook(dispatcherId, dispatcherSyncMethodId, [moduleName, m, args]);
          return res === null || res === undefined ? null : res;
        };
      });
      globalThis.expo.modules[moduleName] = obj;
    });

    // ---- Runner-provided expo environment values --------------------------
    // expo-constants / expo-file-system read these from native JSI module
    // objects on a device. The runner synthesizes them from the app's own
    // assets/app.config (read by the Java side) - universal, not per-app.
    var expoConfig = (config && config.expoConfig) || null;
    var expoManifest = (config && config.expoManifest) || null;
    var pkgName = expoConfig && expoConfig.android && expoConfig.android.package
      ? expoConfig.android.package : 'com.placeholder.app';
    var expConst = globalThis.expo.modules.ExponentConstants;
    if (expConst) {
      expConst.manifest = expoManifest;
      expConst.expoConfig = expoConfig;
      expConst.sessionId = globalThis.expo.uuidv4();
      expConst.installationId = globalThis.expo.uuidv5(pkgName, 'appplayer-runner-install');
      expConst.appOwnership = 'expo';
      expConst.isDevice = true;
      expConst.deviceName = 'AppPlayer Desktop Runner';
      expConst.systemName = 'android';
      expConst.executionEnvironment = 'bare';
      expConst.nativeApplicationVersion = expoConfig ? expoConfig.version : null;
      expConst.nativeBuildVersion = '1';
      expConst.statusBarHeight = 24;
    }
    var fsMod = globalThis.expo.modules.FileSystem;
    if (fsMod) {
      if (!fsMod.EncodingTypes) fsMod.EncodingTypes = { UTF8: 'utf-8', Base64: 'base64' };
      if (!fsMod.documentDirectory) {
        fsMod.documentDirectory = 'file:///data/user/0/' + pkgName + '/files/';
        fsMod.cacheDirectory = 'file:///data/user/0/' + pkgName + '/cache/';
      }
    }
    if (expConst || fsMod) {
      rnLog('expo environment values pre-filled (manifest, sessionId, FileSystem dirs)');
    }
  }
} catch (eExpoProxy) {
  rnLog('expo.modules proxy build failed: ' + eExpoProxy);
}

// ---- 2d. Metro module-failure tracer --------------------------------------
// The bundle's runtime assigns global.__d when it executes; intercept that
// assignment and wrap every module factory so a throwing factory reports its
// metro module id before the error propagates.
var __dCurrent = undefined;
try {
  Object.defineProperty(globalThis, '__d', {
    configurable: true,
    get: function () { return __dCurrent; },
    set: function (fn) {
      var orig = fn;
      __dCurrent = function (factory, moduleId, deps) {
        globalThis.__metroFactories = globalThis.__metroFactories || {};
        globalThis.__metroFactories[moduleId] = factory;
        return orig(function () {
          try {
            return factory.apply(this, arguments);
          } catch (e) {
            rnLog('MODULE ' + moduleId + ' THREW: ' + e + ' | lastExpoReads: '
              + (globalThis.__expoReads || []).slice(-8).join(','));
            throw e;
          }
        }, moduleId, deps);
      };
    },
  });
} catch (eTracer) {
  rnLog('module tracer unavailable: ' + eTracer);
}

// Read-logging proxies: the LAST property read before a module failure names
// the missing environment surface.
try {
  globalThis.__expoReads = [];
  var origExpo = globalThis.expo;
  var origModules = origExpo.modules;
  var readLog = globalThis.__expoReads;
  origExpo.modules = new Proxy(origModules, {
    get: function (t, k) { readLog.push('modules.' + String(k)); return t[k]; },
  });
  globalThis.expo = new Proxy(origExpo, {
    get: function (t, k) { readLog.push('expo.' + String(k)); return t[k]; },
    has: function (t, k) { readLog.push('expo-has:' + String(k)); return k in t; },
  });
} catch (eProxy) { rnLog('read tracer unavailable: ' + eProxy); }

// Drain promise continuations before blocking on the next inbox message:
// the app's `await` continuations (after native callbacks resolve) only run
// when the host drains the JS job queue. Without this the app would freeze
// after its first async callback.
try {
  if (typeof __rnDrainJobs === 'function') {
    var drainResult = __rnDrainJobs();
    if (drainResult === 'EXCEPTION') rnLog('job drain threw (continued)');
  }
} catch (eDrain) {
  rnLog('drain failed: ' + eDrain);
}

// ---- 2e. React DevTools hook (universal runtime diagnostic) ------------
// Installed BEFORE the bundle executes so React's renderer registers itself.
// The runner's stall watchdog walks the captured fiber roots to report the
// real component tree state. This mirrors the standard react-devtools hook.
try {
  var __hookRenderers = new Map();
  var __hookRoots = new Set();
  globalThis.__REACT_DEVTOOLS_GLOBAL_HOOK__ = {
    supportsFiber: true,
    rendererInterfaces: {},
    renderers: __hookRenderers,
    _injectCount: 0,
    inject: function (internals) {
      var id = ++this._injectCount;
      __hookRenderers.set(id, internals);
      rnLog('devtools hook: renderer injected id=' + id + ' pkg='
        + (internals && internals.rendererPackageName ? internals.rendererPackageName : '?'));
      return id;
    },
    onCommitFiberRoot: function (rendererID, root) {
      if (root) __hookRoots.add(root);
    },
    onCommitFiberUnmount: function () {},
    getFiberRoots: function () { return __hookRoots; },
    on: function () {},
    off: function () {},
    sub: function () { return function () {}; },
    unsub: function () {},
    checkDCE: null,
  };
  rnLog('devtools hook installed (renderer registration active)');
} catch (eHook) {
  rnLog('devtools hook install failed: ' + eHook);
}

// ---- 3. Execute the real app bundle in this same VM ----
rnLog('executing bundle: ' + BUNDLE_PATH);
var bundleOK = false;
try {
  __rnExecuteHBC(BUNDLE_PATH);
  bundleOK = true;
  rnLog('bundle top-level completed OK');
  try {
    rnLog('post-bundle probe: hook=' + typeof globalThis.__REACT_DEVTOOLS_GLOBAL_HOOK__
      + ' renderers=' + (__hookRenderers ? __hookRenderers.size : 'n/a')
      + ' roots=' + (__hookRoots ? __hookRoots.size : 'n/a')
      + ' metroFactories=' + typeof globalThis.__metroFactories
      + ' AppRegistry=' + typeof globalThis.AppRegistry
      + ' batchedBridge=' + typeof globalThis.__fbBatchedBridge);
  } catch (ePb) { rnLog('post-bundle probe threw: ' + ePb); }
} catch (e) {
  rnLog('bundle threw: ' + e);
}

// ---- 4. Initial flush ----
// React Native legacy microtask pump (setImmediate drain).
// RN 0.76 legacy bridge: setImmediate -> JSTimers.queueReactNativeMicrotask;
// on a device the NATIVE side drains these by calling the JS callable module
// JSTimers.callReactNativeMicrotasks after every processed batch. Without
// that pump every React state update (scheduled via setImmediate by the
// React scheduler) sits in the JS queue forever and the UI freezes after the
// first commit - the universal "blank screen after SafeAreaProvider" bug.
function drainReactNativeMicrotasks() {
  try {
    var bb = __fbBatchedBridge;
    if (!bb || !bb.callFunctionReturnFlushedQueue) return;
    for (var mt = 0; mt < 50; mt++) {
      var q = bb.callFunctionReturnFlushedQueue(
        'JSTimers', 'callReactNativeMicrotasks', []);
      var hadCalls = q && q[0] && q[0].length;
      if (hadCalls) __rnWriteOutbox('@@Q ' + JSON.stringify(q));
      if (typeof __rnDrainJobs === 'function') __rnDrainJobs();
      var extra = __fbBatchedBridge.flushedQueue();
      var hadExtra = extra && extra[0] && extra[0].length;
      if (hadExtra) __rnWriteOutbox('@@Q ' + JSON.stringify(extra));
      if (!hadCalls && !hadExtra) break;
    }
  } catch (eMT) {
    rnLog('microtask drain failed: ' + eMT);
  }
}

if (bundleOK) {
  // Drain promise continuations queued during bundle execution (expo-router
  // boot, AsyncStorage hydration ...) BEFORE the first flush - their calls
  // sit in the MessageQueue and would otherwise be stuck until the first
  // inbox message.
  try {
    if (typeof __rnDrainJobs === 'function') {
      for (var preDrains = 0; preDrains < 100; preDrains++) {
        __rnDrainJobs();
        var q0 = __fbBatchedBridge.flushedQueue();
        if (q0 && q0[0] && q0[0].length) {
          rnLog('post-bundle drain ' + preDrains + ' flushed ' + q0[0].length + ' call(s)');
          __rnWriteOutbox('@@Q ' + JSON.stringify(q0));
        } else {
          break;
        }
      }
    }
  } catch (ePre) {
    rnLog('post-bundle drain failed: ' + ePre);
  }
  try {
    var q = __fbBatchedBridge.flushedQueue();
    if (q && ((q[0] && q[0].length) || (q[1] && q[1].length))) {
      __rnWriteOutbox('@@Q ' + JSON.stringify(q));
    }
  } catch (e2) {
    rnLog('initial flush failed: ' + e2);
  }
  // Pump the RN microtask queue so any state updates scheduled during
  // bundle execution (React scheduler immediates) commit before idle.
  drainReactNativeMicrotasks();
}

// ---- 5. Bridge loop: native -> JS calls until EOF ----
while (true) {
  var msg = __rnReadInbox();
  if (msg === null) break; // Java closed stdin => shutdown
  var reply = null;
  try {
    var req = JSON.parse(msg);
    if (!req || req.type === undefined) {
      rnLog('inbox RAW (no type): ' + String(msg).slice(0, 200));
    }
    rnLog('inbox: type=' + req.type + (req.module ? ' ' + req.module + '.' + req.method : (req.id !== undefined ? ' id=' + req.id : '')));
    if (req.type === 'call') {
      reply = __fbBatchedBridge.callFunctionReturnFlushedQueue(
        req.module, req.method, req.args || []);
      try {
        rnLog('call ' + req.module + '.' + req.method + ' -> flushed calls='
          + ((reply && reply[0] && reply[0].length) || 0));
      } catch (eR) {}
    } else if (req.type === 'callback') {
      reply = __fbBatchedBridge.invokeCallbackAndReturnFlushedQueue(
        req.id, req.args || []);
    } else if (req.type === 'eval') {
      var evaled = (1, eval)(req.code);
      reply = { ok: true, result: evaled === undefined ? null : evaled };
    } else if (req.type === 'die') {
      break;
    }
  } catch (e3) {
    reply = { ok: false, error: String(e3) };
    rnLog('REQ ERR type=' + (req && req.type) + (req && req.id !== undefined ? ' id=' + req.id : '')
      + ': ' + e3 + (e3 && e3.stack ? ' | stack: ' + e3.stack : ''));
  }
  if (reply !== null && reply !== undefined) {
    try {
      __rnWriteOutbox('@@Q ' + JSON.stringify(reply));
    } catch (e4) {
      rnLog('reply stringify failed: ' + e4);
    }
  }
  // Drain promise continuations + flush the native call queue until stable.
  // The await continuations queued during the callback only run when the host
  // drains jobs; calls they enqueue sit in the MessageQueue (5ms flush
  // throttle) and MUST be manually flushed or they would never reach native.
  try {
    if (typeof __rnDrainJobs === 'function') {
      for (var drains = 0; drains < 100; drains++) {
        __rnDrainJobs();
        var q = __fbBatchedBridge.flushedQueue();
        if (q && q[0] && q[0].length) {
          rnLog('drain cycle ' + drains + ' flushed ' + q[0].length + ' call(s)');
          __rnWriteOutbox('@@Q ' + JSON.stringify(q));
        } else {
          break; // queue empty and no pending jobs - stable
        }
      }
    }
  } catch (e5) {
    rnLog('drain after message failed: ' + e5);
  }
  // RN microtask pump: state updates scheduled during this message
  // (promise resolutions -> setState -> React scheduler immediates)
  // only commit when JSTimers.callReactNativeMicrotasks runs. On a
  // device the native bridge pumps this after every batch.
  drainReactNativeMicrotasks();
}
rnLog('bridge loop ended');
