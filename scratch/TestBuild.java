package test;

public class TestBuild {
    public static void main(String[] args) {
        System.out.println("SUPPORTED_ABIS: " + java.util.Arrays.toString(android.os.Build.SUPPORTED_ABIS));
        System.out.println("CPU_ABI: " + android.os.Build.CPU_ABI);
        System.out.println("FINGERPRINT: " + android.os.Build.FINGERPRINT);
    }
}
