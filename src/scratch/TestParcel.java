package scratch;

public class TestParcel {
    public static void main(String[] args) {
        try {
            boolean reg = com.myairtelapp.utils.j.o();
            System.out.println("[+] com.myairtelapp.utils.j.o() returns: " + reg);
            String num = com.myairtelapp.utils.j.l();
            System.out.println("[+] com.myairtelapp.utils.j.l() returns: '" + num + "'");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
