package test;

public class TestGgJ {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.vm.version", "2.1.0");
        android.os.Looper.prepareMainLooper();
        org.headless.runner.core.SyntheticServiceManager.initializeGlobalServices();
        java.io.File apkFile = new java.io.File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\headless-android\\base.apk");
        org.headless.runner.manifest.ApkInfo info = org.headless.runner.manifest.ManifestExtractor.extract(apkFile);
        java.io.File storageDir = new java.io.File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data");
        org.headless.runner.core.SyntheticContext context = new org.headless.runner.core.SyntheticContext(info, apkFile, storageDir);
        Class<?> appClass = Class.forName("com.myairtelapp.global.App");
        Object app = appClass.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method m = appClass.getDeclaredMethod("attachBaseContext", android.content.Context.class);
        m.setAccessible(true);
        m.invoke(app, context);

        java.lang.reflect.Method appOnCreate = appClass.getMethod("onCreate");
        appOnCreate.invoke(app);
        
        Class<?> delegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate");
        java.lang.reflect.Method setNight = delegateClass.getMethod("setDefaultNightMode", int.class);
        setNight.invoke(null, 1);

        String mainActivityName = "com.myairtelapp.home.views.activities.SplashScreenActivity";
        Class<?> actClass = Class.forName(mainActivityName);
        android.app.Activity act = (android.app.Activity) actClass.getDeclaredConstructor().newInstance();
        
        java.lang.reflect.Constructor<android.app.ActivityThread> atCtor = android.app.ActivityThread.class.getDeclaredConstructor();
        atCtor.setAccessible(true);
        android.app.ActivityThread at = atCtor.newInstance();

        android.content.Intent launchIntent = new android.content.Intent();
        launchIntent.setClassName(info.getPackageName(), mainActivityName);

        android.content.pm.ActivityInfo actInfo = new android.content.pm.ActivityInfo();
        actInfo.packageName = info.getPackageName();
        actInfo.name = mainActivityName;
        actInfo.applicationInfo = context.getApplicationInfo();

        android.content.res.Configuration config = new android.content.res.Configuration();
        config.uiMode = android.content.res.Configuration.UI_MODE_TYPE_NORMAL | android.content.res.Configuration.UI_MODE_NIGHT_NO;

        org.headless.runner.core.SyntheticInstrumentation instrumentation = new org.headless.runner.core.SyntheticInstrumentation();
        org.headless.runner.core.ActivityStack.getInstance().initialize(
            context, at, (android.app.Application) app, instrumentation
        );
        
        java.lang.reflect.Method attach = android.app.Activity.class.getDeclaredMethod("attach",
            android.content.Context.class, android.app.ActivityThread.class, android.app.Instrumentation.class,
            android.os.IBinder.class, int.class, android.app.Application.class, android.content.Intent.class,
            android.content.pm.ActivityInfo.class, CharSequence.class, android.app.Activity.class, String.class,
            Class.forName("android.app.Activity$NonConfigurationInstances"),
            android.content.res.Configuration.class, String.class,
            Class.forName("com.android.internal.app.IVoiceInteractor"),
            android.view.Window.class,
            Class.forName("android.view.ViewRootImpl$ActivityConfigCallback"),
            android.os.IBinder.class
        );
        attach.setAccessible(true);
        attach.invoke(act, context, at, instrumentation,
            new android.os.Binder(), 0, (android.app.Application) app, launchIntent, actInfo,
            mainActivityName, null, "id", null, config, "ref", null, null, null, new android.os.Binder()
        );
        org.headless.runner.core.ActivityStack.getInstance().pushInitialActivity(act);

        java.lang.reflect.Method onCreate = actClass.getMethod("onCreate", android.os.Bundle.class);
        onCreate.invoke(act, (android.os.Bundle) null);

        System.out.println("[*] Testing Gg.J Runnable directly...");
        try {
            Class<?> jClass = Class.forName("Gg.J");
            java.lang.reflect.Constructor<?> ctor = jClass.getConstructor(actClass, int.class);
            Runnable r0 = (Runnable) ctor.newInstance(act, 0);
            System.out.println("[*] Running Gg.J(act, 0)...");
            r0.run();
            System.out.println("[+] Gg.J(act, 0) SUCCESS!");

            Runnable r1 = (Runnable) ctor.newInstance(act, 1);
            System.out.println("[*] Running Gg.J(act, 1)...");
            r1.run();
            System.out.println("[+] Gg.J(act, 1) SUCCESS!");
        } catch (Throwable t) {
            System.out.println("[-] Gg.J execution threw:");
            t.printStackTrace(System.out);
        }
    }
}
