import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Draws the delivery notes in kit/delivery-notes. No starter kit was supplied, so these stand in for it.
 * Run from the repository root: java kit/tools/GenerateNotes.java
 * The random seed is fixed, so the output is repeatable on a machine with the same fonts.
 */
public class GenerateNotes {
    static final int W = 1000;
    static final int H = 1300;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args.length > 0 ? args[0] : "kit/delivery-notes");
        dir.mkdirs();
        ImageIO.write(printed("Fresh Farm Produce Ltd", "DN-30392", "PO-4501", new String[][] {
                {"Gala Apples", "200", "KG"},
                {"Oranges, Navel", "150", "KG"},
                {"Lettuce, Romaine", "60", "CASE"}}), "png", new File(dir, "note-1-clean.png"));
        ImageIO.write(printed("Blue Ocean Seafood Co.", "DN-30417", "PO-4502", new String[][] {
                {"Salmon fillet", "72", "KG"},
                {"Scallops", "10", "KG"}}), "png", new File(dir, "note-2-short.png"));
        ImageIO.write(messy(), "png", new File(dir, "note-3-messy.png"));
        ImageIO.write(printed("Fresh Farm Produce Ltd", "DN-30501", "PO-4501", new String[][] {
                {"Gala Apples", "220", "KGS"},
                {"Oranges, Navel", "150", "kg"},
                {"Lettuce, Romaine", "60", "CASES"}}), "png", new File(dir, "note-4-over.png"));
        ImageIO.write(printed("Green Valley Eggs", "DN-30502", "PO-4513", new String[][] {
                {"Eggs, large, tray", "120", "TRAYS"}}), "png", new File(dir, "note-5-eggs.png"));
        // A clearly printed PO number that belongs to a different supplier.
        ImageIO.write(printed("Green Valley Eggs", "DN-30503", "PO-4503", new String[][] {
                {"Eggs, large, tray", "120", "TRAY"}}), "png", new File(dir, "note-6-wrong-supplier.png"));
        ImageIO.write(printed("Blue Ocean Seafood Co.", "DN-30504", null, new String[][] {
                {"Salmon fillet", "80", "KG"},
                {"Tiger Prawns", "40", "KG"}}), "png", new File(dir, "note-7-no-po.png"));
        ImageIO.write(crossedOut(), "png", new File(dir, "note-8-crossed-out.png"));
        ImageIO.write(cafeReceipt(), "png", new File(dir, "note-9-not-a-note.png"));
    }

    static Graphics2D setup(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static BufferedImage printed(String supplier, String noteNo, String po, String[][] lines) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 44));
        g.drawString(supplier, 70, 120);
        g.setFont(new Font("Arial", Font.BOLD, 34));
        g.drawString("DELIVERY NOTE", 70, 190);
        g.setFont(new Font("Arial", Font.PLAIN, 30));
        g.drawString("Delivery note no: " + noteNo, 70, 260);
        if (po != null) {
            g.drawString("Purchase order: " + po, 70, 305);
        }
        g.drawString("Deliver to: Harbor Foods Cold Store", 70, 350);
        g.setStroke(new BasicStroke(3));
        g.drawLine(70, 420, W - 70, 420);
        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.drawString("Item", 80, 465);
        g.drawString("Qty", 640, 465);
        g.drawString("Unit", 790, 465);
        g.drawLine(70, 490, W - 70, 490);
        g.setFont(new Font("Arial", Font.PLAIN, 32));
        int y = 550;
        for (String[] line : lines) {
            g.drawString(line[0], 80, y);
            g.drawString(line[1], 640, y);
            g.drawString(line[2], 790, y);
            y += 70;
        }
        g.drawLine(70, y - 25, W - 70, y - 25);
        g.setFont(new Font("Arial", Font.PLAIN, 26));
        g.drawString("Received by: ____________________", 70, H - 130);
        g.dispose();
        return img;
    }

    static BufferedImage messy() {
        Random random = new Random(4503);
        BufferedImage paper = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(paper);
        g.setColor(new Color(240, 234, 216));
        g.fillRect(0, 0, W, H);
        Color ink = new Color(25, 35, 95);
        g.setColor(new Color(170, 190, 215));
        for (int y = 150; y < H; y += 72) {
            g.drawLine(0, y, W, y);
        }
        g.setColor(ink);
        Font hand = new Font("Segoe Print", Font.PLAIN, 42);
        hand(g, hand, random, "Dairy Direct", 70, 125);
        Font small = hand.deriveFont(34f);
        hand(g, small, random, "delivery note  DN-30455", 70, 215);
        hand(g, small, random, "PO-45", 70, 290);
        int blotX = 70 + g.getFontMetrics(small).stringWidth("PO-45") + 4;
        hand(g, small, random, "3", blotX + 34, 290);
        // A blot sits where the third digit would be, so the number reads only as PO-45?3.
        g.setColor(new Color(50, 55, 105, 235));
        for (int i = 0; i < 90; i++) {
            int dx = random.nextInt(24) - 4;
            int dy = random.nextInt(44) - 40;
            g.fillOval(blotX + dx, 290 + dy, 9 + random.nextInt(8), 9 + random.nextInt(8));
        }
        g.setColor(ink);
        hand(g, small, random, "Milk 1L", 90, 440);
        hand(g, small, random, "252 ea", 620, 440);
        hand(g, small, random, "Butter 500g", 90, 584);
        hand(g, small, random, "100 cases", 620, 584);
        hand(g, small, random, "Cheddar", 90, 728);
        hand(g, small, random, "38 kg", 620, 728);
        hand(g, small.deriveFont(30f), random, "left at bay 3", 90, 944);
        g.dispose();
        return asPhoto(paper, random, -4.5);
    }

    /** Handwritten, with one quantity struck through and corrected. The right reading is 78. */
    static BufferedImage crossedOut() {
        Random random = new Random(4502);
        BufferedImage paper = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(paper);
        g.setColor(new Color(240, 234, 216));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(170, 190, 215));
        for (int y = 150; y < H; y += 72) {
            g.drawLine(0, y, W, y);
        }
        Color ink = new Color(25, 35, 95);
        g.setColor(ink);
        Font hand = new Font("Segoe Print", Font.PLAIN, 42);
        Font small = hand.deriveFont(34f);
        hand(g, hand, random, "Blue Ocean Seafood Co.", 70, 125);
        hand(g, small, random, "delivery note  DN-30505", 70, 215);
        hand(g, small, random, "PO-4502", 70, 290);
        hand(g, small, random, "Salmon fillet", 90, 440);
        hand(g, small, random, "80", 560, 440);
        g.setStroke(new BasicStroke(5));
        g.drawLine(548, 432, 628, 412);
        hand(g, small, random, "78 kg", 660, 440);
        hand(g, small, random, "Tiger Prawns", 90, 584);
        hand(g, small, random, "40 kg", 660, 584);
        g.dispose();
        return asPhoto(paper, random, 3.0);
    }

    /** Not a delivery note at all, the kind of thing that gets photographed by mistake. */
    static BufferedImage cafeReceipt() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = setup(img);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Consolas", Font.BOLD, 46));
        g.drawString("CORNER CAFE", 330, 140);
        g.setFont(new Font("Consolas", Font.PLAIN, 34));
        g.drawString("Table 4        Thank you!", 200, 220);
        g.drawString("2 x Flat white          7.00", 200, 340);
        g.drawString("1 x Almond croissant    3.80", 200, 400);
        g.drawString("TOTAL                  10.80", 200, 520);
        g.drawString("Paid by card", 200, 600);
        g.dispose();
        return img;
    }

    static BufferedImage asPhoto(BufferedImage paper, Random random, double degrees) {
        BufferedImage photo = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D p = setup(photo);
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        p.setColor(new Color(92, 88, 82));
        p.fillRect(0, 0, W, H);
        AffineTransform tilt = new AffineTransform();
        tilt.translate(W / 2.0, H / 2.0);
        tilt.rotate(Math.toRadians(degrees));
        tilt.scale(0.93, 0.93);
        tilt.translate(-W / 2.0, -H / 2.0);
        p.drawImage(paper, tilt, null);
        p.dispose();

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int noise = (int) (random.nextGaussian() * 13);
                int rgb = photo.getRGB(x, y);
                int r = clamp(((rgb >> 16) & 255) + noise);
                int gr = clamp(((rgb >> 8) & 255) + noise);
                int b = clamp((rgb & 255) + noise);
                photo.setRGB(x, y, (r << 16) | (gr << 8) | b);
            }
        }
        return photo;
    }

    static void hand(Graphics2D g, Font font, Random random, String text, int x, int y) {
        AffineTransform saved = g.getTransform();
        g.rotate(Math.toRadians(random.nextDouble() * 2 - 1), x, y);
        g.setFont(font);
        g.drawString(text, x, y + random.nextInt(5) - 2);
        g.setTransform(saved);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
