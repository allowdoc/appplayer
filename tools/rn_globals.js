var global = this;
global.__fbBatchedBridgeConfig = {
  remoteModuleConfig: [
    ["PlatformConstants", {
      isTesting: false,
      reactNativeVersion: { major: 0, minor: 71, patch: 1 },
      Version: 33,
      Release: "13",
      Serial: "pixel5",
      Fingerprint: "google/redfin/redfin:13/TQ3A.230901.001/10750766:user/release-keys",
      uiMode: "normal"
    }, [], []],
    ["UIManager", {
      customBubblingEventTypes: {},
      customDirectEventTypes: {},
      Dimensions: {
        window: { width: 1080, height: 1920, scale: 2.625, fontScale: 1.0 },
        screen: { width: 1080, height: 1920, scale: 2.625, fontScale: 1.0 }
      }
    }, [
      "createView", "setChildren", "manageChildren", "updateView", "removeSubviewsFromContainerWithID",
      "measure", "measureInWindow", "dispatchViewManagerCommand"
    ], []],
    ["DeviceInfo", {
      Dimensions: {
        window: { width: 1080, height: 1920, scale: 2.625, fontScale: 1.0 },
        screen: { width: 1080, height: 1920, scale: 2.625, fontScale: 1.0 }
      }
    }, ["getConstants"], []],
    ["Timing", {}, ["createTimer", "deleteTimer", "setSendIdleEvents"], []],
    ["ExceptionsManager", {}, ["reportFatalException", "reportSoftException"], []],
    ["SourceCode", { scriptURL: "assets://index.android.bundle" }, [], []],
    ["AsyncLocalStorage", {}, ["multiGet", "multiSet", "multiRemove", "clear", "getAllKeys"], []]
  ]
};
global.nativeFlushQueueImmediate = function(calls) {
  print("[Hermes->Native] nativeFlushQueueImmediate called with " + (calls ? calls.length : 0) + " calls");
};
global.nativeCallSyncHook = function(moduleId, methodId, args) {
  return null;
};
print("[+] Hermes React Native Globals initialized successfully!");
