print("=== Hermes Globals ===");
print(Object.getOwnPropertyNames(globalThis).sort().join(", "));
if (typeof HermesInternal !== 'undefined') {
  print("=== HermesInternal ===");
  print(Object.getOwnPropertyNames(HermesInternal).sort().join(", "));
}
