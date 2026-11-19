import zipfile, struct

with zipfile.ZipFile(r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\app.jar', 'r') as z:
    for name in z.namelist():
        if name == 'androidx/appcompat/R$styleable.class':
            data = z.read(name)
            import subprocess
            javap = r'C:\Program Files\Android\Android Studio\jbr\bin\javap.exe'
            out = subprocess.check_output([javap, '-cp', r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\app.jar', '-v', '-p', 'androidx.appcompat.R$styleable'], text=True)
            lines = out.splitlines()
            for i, line in enumerate(lines):
                if 'AppCompatTheme_windowNoTitle' in line or 'AppCompatTheme_windowActionBar' in line:
                    print('\n'.join(lines[i:i+6]))
