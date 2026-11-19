import subprocess

script = """
import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

public class TestNet {
    public static void main(String[] args) {
        try {
            System.out.println("Testing NetworkManager.getInstance()...");
            // Set App.s
            File fakeCache = new File("build/cache");
            fakeCache.mkdirs();
            android.content.Context fakeContext = new org.headless.runner.core.MockContext() {
                @Override
                public File getCacheDir() {
                    return fakeCache;
                }
            };
            try {
                Class<?> appCls = Class.forName("com.myairtelapp.global.App");
                Field f = appCls.getField("s");
                f.set(null, fakeContext);
                System.out.println("App.s set successfully!");
            } catch (Throwable t) {
                System.out.println("Failed to set App.s: " + t);
            }

            com.network.NetworkManager nm = com.network.NetworkManager.getInstance();
            System.out.println("NetworkManager instance: " + nm);

            Field tmplField = com.network.NetworkManager.class.getDeclaredField("retrofitBuilderTemplates");
            tmplField.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) tmplField.get(null);
            System.out.println("retrofitBuilderTemplates size: " + (map != null ? map.size() : "null"));
            if (map != null) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
"""
with open('src/TestNet.java', 'w') as f:
    f.write(script)

javac = r"C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
java = r"C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
subprocess.run([javac, "-cp", "bin;lib\\android-all-10.jar;app.jar", "-d", "bin", "src/TestNet.java"])
res = subprocess.run([java, "-Xverify:none", "-cp", "bin;lib\\android-all-10.jar;app.jar", "TestNet"], capture_output=True, text=True)
print("STDOUT:", res.stdout)
print("STDERR:", res.stderr)
