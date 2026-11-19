package test;

public class TestInstantiate {
    public static void main(String[] args) {
        System.setProperty("java.vm.version", "2.1.0");
        android.os.Looper.prepareMainLooper();
        org.headless.runner.core.SyntheticServiceManager.initializeGlobalServices();
        try {
            Class<?> actClass = Class.forName("androidx.appcompat.widget.AppCompatImageView");
            System.out.println("Loaded class: " + actClass);
            java.lang.reflect.Constructor<?> ctor = actClass.getConstructor(android.content.Context.class);
            System.out.println("Found constructor: " + ctor);
            android.content.Context ctx = new org.headless.runner.core.SyntheticContext(
                new org.headless.runner.manifest.ApkInfo("com.test", null, null, "1.0", 1),
                new java.io.File("."),
                new java.io.File(".")
            );
            Object obj = ctor.newInstance(ctx);
            System.out.println("Instantiated: " + obj);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
