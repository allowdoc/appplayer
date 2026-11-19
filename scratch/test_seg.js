
print('[+] Prelude: Starting setup...');
globalThis.global = globalThis;

let _batchedBridge = null;
Object.defineProperty(global, '__fbBatchedBridge', {
  get() { return _batchedBridge; },
  set(val) {
    _batchedBridge = val;
    print('[+] React Native BatchedBridge registered successfully! Type: ' + typeof val);
  },
  configurable: true
});

global.__fbBatchedBridgeConfig = {
  remoteModuleConfig: [
    ['PlatformConstants', { PlatformConstants: { Version: 30, ServerHost: '', isTesting: false, reactNativeVersion: { major: 0, minor: 78, patch: 2 } } }, [], [], []],
    ['UIManager', {}, ['createView', 'setChildren', 'manageChildren', 'updateView', 'removeRootView', 'measure', 'dispatchViewManagerCommand'], [], []],
    ['AsyncLocalStorage', {}, ['multiGet', 'multiSet', 'multiRemove', 'clear', 'getAllKeys'], [0, 1, 2, 3, 4], []],
    ['DeviceInfo', { Dimensions: { window: { width: 1080, height: 1920, scale: 2.75, fontScale: 1 }, screen: { width: 1080, height: 1920, scale: 2.75, fontScale: 1 } } }, [], [], []],
    ['Timing', {}, ['createTimer', 'deleteTimer', 'setSendIdleEvents'], [], []],
    ['AppState', { initialAppState: 'active' }, ['getCurrentAppState', 'addListener', 'removeListeners'], [], []],
    ['Networking', {}, ['sendRequest', 'abortRequest', 'clearCookies'], [], []]
  ]
};

print('[+] Prelude: Loading Segment 1 (index.android.bundle)...');
try {
    loadSegment(require.context, 1);
    print('[+] Segment 1 loaded successfully!');
} catch (e) {
    print('[-] Segment load error:', e);
}
