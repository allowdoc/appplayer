import subprocess
import re
import os

aapt2 = r"C:\Users\Noval\AppData\Local\Android\Sdk\build-tools\35.0.0\aapt2.exe"
apk = r"C:\Users\Noval\.gemini\antigravity\scratch\headless-android\base.apk"
out_path = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\data\strings.tsv"

print("[*] Running aapt2 dump resources...")
proc = subprocess.run([aapt2, "dump", "resources", apk], capture_output=True, text=True, encoding="utf-8", errors="ignore")

strings = {}
current_id = None
current_name = None

for line in proc.stdout.splitlines():
    line_s = line.strip()
    m = re.match(r"resource (0x[0-9a-fA-F]+) string/(\w+)", line_s)
    if m:
        current_id = int(m.group(1), 16)
        current_name = m.group(2)
        continue
    if current_id is not None and line_s.startswith("() \""):
        val = line_s[4:-1]
        strings[current_id] = (current_name, val)
        current_id = None

os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w", encoding="utf-8") as f:
    for sid, (name, val) in strings.items():
        # Escape newlines and tabs
        val_escaped = val.replace("\r", "").replace("\n", "\\n").replace("\t", " ")
        f.write(f"{sid}\t{name}\t{val_escaped}\n")

print(f"[+] Successfully extracted {len(strings)} strings to {out_path}")
