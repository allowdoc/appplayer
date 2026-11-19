import java.awt.*;
import java.awt.image.BufferedImage;

public class TestFont {
    public static void main(String[] args) {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        Font f = new Font("Nirmala UI", Font.BOLD, 24);
        g2.setFont(f);
        String s = "\u0A2A\u0A70\u0A1C\u0A3E\u0A2C\u0A40";
        int canDisplayCount = 0;
        for (char c : s.toCharArray()) {
            if (f.canDisplay(c)) canDisplayCount++;
            else System.out.println("Cannot display char: 0x" + Integer.toHexString(c));
        }
        System.out.println("Can display " + canDisplayCount + " / " + s.length() + " chars of Punjabi");
    }
}
