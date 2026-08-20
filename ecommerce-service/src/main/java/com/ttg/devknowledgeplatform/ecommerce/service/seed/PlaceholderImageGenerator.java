package com.ttg.devknowledgeplatform.ecommerce.service.seed;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Renders a simple solid-color-plus-label JPEG in memory, for {@code ProductImageSeeder} — no
 * real product photography exists for this sample catalog, and checking in a handful of static
 * binary placeholder files felt like the wrong tradeoff versus generating them on the fly from
 * plain Java code (no new binary assets in the repo, trivially variable per product).
 *
 * <p>Deliberately not a general-purpose image utility — the label wrapping/centering logic is
 * tuned for a short product-name string on a square gallery thumbnail, not arbitrary text.
 *
 * @author ttg
 */
public final class PlaceholderImageGenerator {

    private static final int SIZE = 600;
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 40);

    static {
        // AWT's font/graphics rendering can throw HeadlessException on a server JVM without a
        // display, even for pure in-memory BufferedImage rendering that never opens a window —
        // this class only ever runs inside a Spring Boot app (never has a real display anyway),
        // so headless mode is always the correct setting, not an environment-specific guess.
        System.setProperty("java.awt.headless", "true");
    }

    private PlaceholderImageGenerator() {
    }

    /**
     * @param label      text drawn centered on the image (word-wrapped across a few lines)
     * @param background the fill color; text color is chosen for contrast against it
     * @return JPEG-encoded bytes of a {@value #SIZE}x{@value #SIZE} square image
     */
    public static byte[] generate(String label, Color background) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, SIZE, SIZE);
            g.setFont(LABEL_FONT);
            g.setColor(contrastingTextColor(background));
            drawWrappedCenteredText(g, label);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render placeholder image for '" + label + "'", e);
        }
    }

    private static Color contrastingTextColor(Color background) {
        // Standard relative-luminance threshold for picking black-vs-white text on a given
        // background — the same formula browsers/design tools use for WCAG contrast checks.
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        return luminance > 0.6 ? Color.BLACK : Color.WHITE;
    }

    private static void drawWrappedCenteredText(Graphics2D g, String label) {
        FontMetrics metrics = g.getFontMetrics();
        int maxLineWidth = (int) (SIZE * 0.8);
        List<String> lines = wrap(label, metrics, maxLineWidth);

        int lineHeight = metrics.getHeight();
        int totalHeight = lineHeight * lines.size();
        int y = (SIZE - totalHeight) / 2 + metrics.getAscent();
        for (String line : lines) {
            int x = (SIZE - metrics.stringWidth(line)) / 2;
            g.drawString(line, x, y);
            y += lineHeight;
        }
    }

    private static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
