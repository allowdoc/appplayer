import subprocess

script = """
import java.io.File;
import org.headless.runner.manifest.ApkInfo;
import org.headless.runner.manifest.ManifestExtractor;

public class TestProviders {
    public static void main(String[] args) throws Exception {
        File apk = new File("C:\\\\Users\\\\Noval\\\\.gemini\\\\antigravity\\\\scratch\\\\headless-android\\\\base.apk");
        ApkInfo info = ManifestExtractor.extract(apk);
        System.out.println("Total Providers: " + info.getProviders().size());
        for (ApkInfo.ProviderEntry pe : info.getProviders()) {
            System.out.println("  Provider: " + pe.className + " -- auth: " + pe.authority);
        }
        System.out.println("Total Services: " + info.getServices().size());
        for (ApkInfo.ServiceEntry se : info.getServices()) {
            System.out.println("  Service: " + se.className + " -- perm: " + se.permission);
        }
    }
}
"""
with open('src/TestProviders.java', 'w') as f:
    f.write(script)

javac = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
java = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
subprocess.run([javac, "-cp", "bin;lib\\android-all-10.jar", "-d", "bin", "src/TestProviders.java"])
res = subprocess.run([java, "-cp", "bin;lib\\android-all-10.jar;app.jar", "TestProviders"], capture_output=True, text=True)
print(res.stdout)
