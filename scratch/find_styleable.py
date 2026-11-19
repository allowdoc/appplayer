import zipfile

jar_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\app.jar'
with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if 'R$styleable.class' in name:
            data = z.read(name)
            if b'AppCompatTheme_windowNoTitle' in data:
                print("Found in:", name)
                # Let's inspect fields in this class
                import subprocess
                javap = r'C:\Program Files\Android\Android Studio\jbr\bin\javap.exe'
                cls_name = name.replace('.class', '')
                out = subprocess.check_output([javap, '-cp', jar_path, cls_name], text=True)
                for line in out.splitlines():
                    if 'AppCompatTheme_window' in line:
                        print(" ", line)
