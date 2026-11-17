package C9;

public final class m implements Runnable {
    public final int a;

    public m(int a) {
        this.a = a;
    }

    public m(com.myairtelapp.fragment.settings.securepay.SecurePaySettingActivity activity) {
        this.a = 4;
    }

    public m(boolean b) {
        this.a = 7;
    }

    private final void a() {}
    private final void c() {}

    @Override
    public void run() {
        if (this.a == 6 || this.a > 27 || this.a < 0) {
            System.out.println("[AntiTamper] Disarmed security exit attempt in C9.m (case " + this.a + ")");
            return;
        }
    }
}
