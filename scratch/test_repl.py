import subprocess

p = subprocess.Popen(['C:/Users/Noval/.gemini/antigravity/scratch/android-runner/tools/hermes.exe'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
stdout, stderr = p.communicate(input='let a = 10; print("RESULT:" + (a * 2));\n')
print('stdout:', stdout)
print('stderr:', stderr)
