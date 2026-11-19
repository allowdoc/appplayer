import re

with open('assets/index.android.bundle', 'rb') as f:
    data = f.read()

# Search for string literals in Hermes bytecode
# Hermes strings are stored in a string table or ASCII blocks
strings = set()
for match in re.finditer(b'[\x20-\x7e]{4,}', data):
    s = match.group(0).decode('ascii', errors='ignore')
    if any(k in s.lower() for k in ['helloscreen', 'splashscreen', 'mobile', 'login', 'otp', 'appregistry']):
        strings.add(s)

print(f"Total interesting strings found: {len(strings)}")
for s in sorted(strings)[:30]:
    print("  ", s)
