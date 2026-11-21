import os, subprocess, zipfile

asm_jar = r"C:\Program Files\Android\Android Studio\plugins\android\lib\asm-9.9.jar"
javac = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
bin_dir = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\bin"
agent_src = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\src\org\headless\runner\agent\RuntimeNeutralizerAgent.java"
out_agent_jar = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\lib\runner-agent.jar"

# 1. Compile agent
cmd = [javac, "-cp", asm_jar, "-d", bin_dir, agent_src]
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("Agent compile error:", res.stderr)
    exit(1)
print("Compiled RuntimeNeutralizerAgent successfully.")

# 2. Build fat jar
manifest_bytes = (
    b"Manifest-Version: 1.0\r\n"
    b"Premain-Class: org.headless.runner.agent.RuntimeNeutralizerAgent\r\n"
    b"Can-Redefine-Classes: true\r\n"
    b"Can-Retransform-Classes: true\r\n\r\n"
)

with zipfile.ZipFile(out_agent_jar, "w", zipfile.ZIP_DEFLATED) as zout:
    zout.writestr("META-INF/MANIFEST.MF", manifest_bytes)
    
    # Add agent classes from bin
    agent_dir = os.path.join(bin_dir, "org", "headless", "runner", "agent")
    for fname in os.listdir(agent_dir):
        if fname.endswith(".class"):
            fpath = os.path.join(agent_dir, fname)
            zout.write(fpath, f"org/headless/runner/agent/{fname}")
            
    # Add asm classes from asm_jar
    with zipfile.ZipFile(asm_jar, "r") as zin:
        for item in zin.infolist():
            if item.filename.startswith("org/objectweb/asm/") and item.filename.endswith(".class"):
                zout.writestr(item.filename, zin.read(item.filename))

print(f"Created fat runner-agent.jar at {out_agent_jar} successfully ({os.path.getsize(out_agent_jar)} bytes)!")
