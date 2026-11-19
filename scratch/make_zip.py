import zipfile
import json

zip_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\scratch\test_bundle.zip'
load_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\scratch\test_load.hbc'
bundle_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\assets\index.android.bundle'

metadata = {
    "segments": {
        "0": ["test_load.hbc"],
        "1": ["bundle.hbc"]
    }
}

with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('metadata.json', json.dumps(metadata))
    z.write(load_path, 'test_load.hbc')
    z.write(bundle_path, 'bundle.hbc')

print("[+] Created segmented test_bundle.zip!")
