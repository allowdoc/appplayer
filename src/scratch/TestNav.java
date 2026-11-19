package scratch;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import org.headless.runner.core.ActivityStack;

public class TestNav {
    public static void test(Activity activity) {
        System.out.println("[*] [TestNav] Running navigation test from: " + activity);

        // 1. Seed LanguagePreference so LanguageListActivity gets genuine onboarding data
        try {
            com.myairtelapp.data.dto.newHome.LanguagePreference pref = new com.myairtelapp.data.dto.newHome.LanguagePreference();
            String[][] langDefs = {
                {"en", "English", "English"},
                {"hi", "Hindi", "\u0939\u093F\u0928\u094D\u0926\u0940"},
                {"pa", "Punjabi", "\u0A2A\u0A70\u0A1C\u0A3E\u0A2C\u0A40"},
                {"te", "Telugu", "\u0C24\u0C46\u0C32\u0C41\u0C17\u0C41"},
                {"ta", "Tamil", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD"},
                {"bn", "Bengali", "\u09AC\u09BE\u0982\u09B2\u09BE"},
                {"mr", "Marathi", "\u092E\u0930\u093E\u0920\u0940"},
                {"gu", "Gujarati", "\u0A97\u0AC1\u0A9C\u0AB0\u0ABE\u0AA4\u0AC0"}
            };
            com.myairtelapp.data.dto.newHome.LanguageDetails[] details = new com.myairtelapp.data.dto.newHome.LanguageDetails[langDefs.length];
            for (int i = 0; i < langDefs.length; i++) {
                details[i] = new com.myairtelapp.data.dto.newHome.LanguageDetails();
                details[i].setCode(langDefs[i][0]);
                details[i].setName(langDefs[i][1]);
                details[i].setTranslation(langDefs[i][2]);
            }
            pref.setDetails(details);
            pref.setPreferredLanguage("en");
            pref.setLanguageSaved(false);
            com.bumptech.glide.d.b = pref;
            System.out.println("[+] [TestNav] Seeded 8 languages into LanguagePreference!");
        } catch (Throwable t) {
            System.err.println("[-] [TestNav] Error seeding LanguagePreference: " + t);
        }

        // 2. Launch LanguageListActivity
        Activity langAct = null;
        try {
            System.out.println("[*] [TestNav] Launching LanguageListActivity...");
            Intent intent = new Intent();
            intent.setClassName("com.myairtelapp", "com.myairtelapp.activity.LanguageListActivity");
            langAct = ActivityStack.getInstance().launchActivity(intent);
            if (langAct != null) {
                System.out.println("[+] [TestNav] LanguageListActivity live on top of stack!");
            }
        } catch (Throwable t) {
            System.err.println("[-] [TestNav] Error launching LanguageListActivity: " + t);
            t.printStackTrace();
        }

        if (langAct != null) {
            // 3. Trigger decor measure & layout pass
            try {
                View decor = langAct.getWindow().getDecorView();
                int wSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
                decor.measure(wSpec, hSpec);
                decor.layout(0, 0, 1080, 1920);
                System.out.println("[+] [TestNav] decor.measure() & decor.layout(0,0,1080,1920) executed!");
            } catch (Throwable t) {
                System.err.println("[-] [TestNav] Decor layout warning: " + t);
            }

            // 4. Verify adapter items
            try {
                java.lang.reflect.Field i0Field = langAct.getClass().getField("i0");
                Object adapter = i0Field.get(langAct);
                if (adapter != null) {
                    java.lang.reflect.Field dField = adapter.getClass().getField("d");
                    java.util.ArrayList<?> list = (java.util.ArrayList<?>) dField.get(adapter);
                    System.out.println("[+] [TestNav] Language Adapter is active with " + (list != null ? list.size() : 0) + " languages!");
                }
            } catch (Throwable t) {
                System.err.println("[-] [TestNav] Check adapter warning: " + t);
            }

            // Capture LanguageListActivity onboarding screen before proceeding
            try {
                java.io.File onboardingFile = new java.io.File(langAct.getFilesDir().getParentFile(), "screen_onboarding.png");
                org.headless.runner.ui.HeadlessScreenCapture.capture(langAct, onboardingFile);
            } catch (Throwable ignored) {}

            // 5. Test Interactive Click Simulation: Click the Confirm button (automated test mode)
            boolean isUi = Boolean.getBoolean("headless.runner.ui");
            if (!isUi) {
                try {
                    java.lang.reflect.Field s0Field = langAct.getClass().getField("s0");
                    Object binding = s0Field.get(langAct);
                    if (binding != null) {
                        java.lang.reflect.Field cField = binding.getClass().getField("c");
                        View confirmBtn = (View) cField.get(binding);
                        if (confirmBtn != null) {
                            if (confirmBtn instanceof android.widget.TextView) {
                                System.out.println("[*] [TestNav] confirmBtn text = '" + ((android.widget.TextView) confirmBtn).getText() + "'");
                            }
                            System.out.println("[*] [TestNav] Simulating automated click on 'Confirm Language' button...");
                            confirmBtn.performClick();
                            System.out.println("[+] [TestNav] 'Confirm Language' button click dispatched successfully!");
                            try {
                                Thread.sleep(600);
                                android.os.Looper.getMainLooper().getQueue().drainPendingMessages();
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[-] [TestNav] Button click warning: " + t);
                }
            } else {
                System.out.println("[+] [TestNav] UI interactive mode active. Ready for live user interaction!");
            }
        }
    }
}
