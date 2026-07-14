/*
 * SPDX-FileCopyrightText: 2026 Espressif Systems (Shanghai) CO LTD
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class NavigationAssetGenerator {
    private static final int ICON_SIZE = 128;
    private static final Color WHITE = new Color(0xF5, 0xF8, 0xF6);
    private static final Color GREEN = new Color(0x35, 0xD0, 0x7F);
    private static final Color DARK = new Color(0x10, 0x14, 0x12);

    private NavigationAssetGenerator() {}

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        List<Icon> icons = createIcons();
        Path svgDir = root.resolve("assets/navigation/svg");
        Path pngDir = root.resolve("assets/navigation/png/128");
        Path drawableDir = root.resolve("android-app/app/src/main/res/drawable");
        Files.createDirectories(svgDir);
        Files.createDirectories(pngDir);
        Files.createDirectories(drawableDir);

        List<BufferedImage> rendered = new ArrayList<>();
        for (Icon icon : icons) {
            Files.writeString(svgDir.resolve(icon.name + ".svg"), toSvg(icon));
            Files.writeString(drawableDir.resolve("ic_nav_" + icon.name + ".xml"), toVectorDrawable(icon));
            BufferedImage image = render(icon);
            ImageIO.write(image, "png", pngDir.resolve(icon.name + ".png").toFile());
            rendered.add(image);
        }
        writeSprite(root.resolve("assets/navigation/navigation-sprite-4x4.png"), rendered);
        Files.writeString(root.resolve("assets/navigation/manifest.json"), toManifest(icons));
        writePreview(root.resolve("assets/navigation/esp-preview-320x240.png"), rendered.get(2));
        verifyOutputs(root, icons);
        System.out.printf(Locale.ROOT, "Generated and verified %d navigation assets.%n", icons.size());
    }

    private static List<Icon> createIcons() {
        List<Icon> icons = new ArrayList<>();
        icons.add(icon("straight", stroke(WHITE, 12, 64, 106, 64, 30), arrow(64, 16, 46, 40, 82, 40)));
        icons.add(icon("left", stroke(WHITE, 12, 86, 108, 86, 61, 38, 61), arrow(24, 61, 48, 43, 48, 79)));
        icons.add(icon("right", stroke(WHITE, 12, 42, 108, 42, 61, 90, 61), arrow(104, 61, 80, 43, 80, 79)));
        icons.add(icon("slight_left", stroke(WHITE, 12, 82, 108, 82, 72, 48, 38), arrow(37, 27, 69, 36, 46, 59)));
        icons.add(icon("slight_right", stroke(WHITE, 12, 46, 108, 46, 72, 80, 38), arrow(91, 27, 59, 36, 82, 59)));
        icons.add(icon("sharp_left", stroke(WHITE, 12, 88, 108, 88, 68, 42, 68, 42, 35), arrow(42, 22, 24, 44, 60, 44)));
        icons.add(icon("sharp_right", stroke(WHITE, 12, 40, 108, 40, 68, 86, 68, 86, 35), arrow(86, 22, 68, 44, 104, 44)));
        icons.add(icon("u_turn", stroke(WHITE, 12, 88, 108, 88, 53, 82, 36, 68, 28, 52, 31, 42, 43, 42, 76),
            arrow(42, 91, 24, 67, 60, 67)));
        icons.add(icon("roundabout", stroke(WHITE, 12, 64, 112, 64, 88), circle(WHITE, 10, 64, 58, 29),
            stroke(GREEN, 8, 82, 35, 94, 39), arrow(99, 42, 83, 25, 80, 48)));
        icons.add(icon("arrive", stroke(WHITE, 9, 38, 108, 38, 25), polygon(GREEN, 38, 27, 94, 42, 38, 62),
            stroke(WHITE, 5, 55, 32, 55, 57, 72, 37, 72, 52, 88, 41)));
        icons.add(icon("merge", stroke(WHITE, 12, 76, 108, 76, 31), arrow(76, 17, 58, 40, 94, 40),
            stroke(GREEN, 9, 30, 88, 42, 88, 76, 60)));
        icons.add(icon("exit", stroke(WHITE, 12, 50, 108, 50, 25), arrow(50, 13, 32, 36, 68, 36),
            stroke(GREEN, 9, 50, 72, 75, 55, 94, 55), arrow(105, 55, 84, 39, 84, 71)));
        icons.add(icon("unknown", stroke(WHITE, 10, 40, 108, 40, 83, 56, 70),
            stroke(GREEN, 9, 56, 53, 58, 40, 68, 32, 80, 34, 87, 43, 85, 54, 74, 62, 68, 71),
            circle(GREEN, 0, 68, 91, 6)));
        icons.add(icon("route", stroke(GREEN, 10, 31, 104, 31, 83, 58, 64, 58, 44, 91, 25),
            circle(WHITE, 0, 31, 104, 7), circle(WHITE, 0, 91, 25, 7)));
        return icons;
    }

    private static Icon icon(String name, ShapeCommand... commands) {
        return new Icon(name, Arrays.asList(commands));
    }

    private static ShapeCommand stroke(Color color, int width, int... coordinates) {
        return new Polyline(color, width, false, coordinates);
    }

    private static ShapeCommand arrow(int... coordinates) {
        return new Polyline(GREEN, 0, true, coordinates);
    }

    private static ShapeCommand polygon(Color color, int... coordinates) {
        return new Polyline(color, 0, true, coordinates);
    }

    private static ShapeCommand circle(Color color, int width, int centerX, int centerY, int radius) {
        return new Circle(color, width, centerX, centerY, radius);
    }

    private static BufferedImage render(Icon icon) {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configureGraphics(graphics);
        for (ShapeCommand command : icon.commands) command.draw(graphics);
        graphics.dispose();
        return image;
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static String toSvg(Icon icon) {
        StringBuilder body = new StringBuilder();
        for (ShapeCommand command : icon.commands) body.append("  ").append(command.svg()).append('\n');
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"128\" height=\"128\" viewBox=\"0 0 128 128\">\n"
            + body + "</svg>\n";
    }

    private static String toVectorDrawable(Icon icon) {
        StringBuilder body = new StringBuilder();
        for (ShapeCommand command : icon.commands) body.append("    ").append(command.vector()).append('\n');
        return "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    android:width=\"128dp\" android:height=\"128dp\"\n"
            + "    android:viewportWidth=\"128\" android:viewportHeight=\"128\">\n"
            + body + "</vector>\n";
    }

    private static void writeSprite(Path output, List<BufferedImage> images) throws IOException {
        BufferedImage sprite = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sprite.createGraphics();
        configureGraphics(graphics);
        for (int index = 0; index < images.size(); index++) {
            graphics.drawImage(images.get(index), index % 4 * ICON_SIZE, index / 4 * ICON_SIZE, null);
        }
        graphics.dispose();
        ImageIO.write(sprite, "png", output.toFile());
    }

    private static String toManifest(List<Icon> icons) {
        StringBuilder json = new StringBuilder("{\n  \"cellSize\": 128,\n  \"columns\": 4,\n  \"icons\": [\n");
        for (int index = 0; index < icons.size(); index++) {
            Icon icon = icons.get(index);
            json.append(String.format(Locale.ROOT,
                "    {\"name\":\"%s\",\"row\":%d,\"column\":%d}%s%n",
                icon.name, index / 4, index % 4, index + 1 == icons.size() ? "" : ","));
        }
        return json.append("  ]\n}\n").toString();
    }

    private static void writePreview(Path output, BufferedImage rightIcon) throws IOException {
        BufferedImage preview = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        configureGraphics(graphics);
        graphics.setColor(DARK);
        graphics.fillRect(0, 0, 320, 240);
        graphics.setColor(new Color(0x1D, 0x25, 0x21));
        graphics.fillRoundRect(8, 7, 304, 31, 12, 12);
        graphics.setColor(WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        graphics.drawString("\u4eba\u6c11\u8def", 128, 29);
        graphics.setColor(GREEN);
        graphics.fillOval(20, 18, 9, 9);
        graphics.drawImage(rightIcon, 26, 42, 112, 112, null);
        graphics.setColor(WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        graphics.drawString("300 m", 146, 105);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        graphics.drawString("\u4e0b\u4e00\u8def\u53e3", 149, 127);
        drawSpeedBadge(graphics, 268, 65, "60", true);
        drawSpeedBadge(graphics, 268, 119, "36", false);
        graphics.setColor(new Color(0x1D, 0x25, 0x21));
        graphics.fillRoundRect(8, 194, 304, 38, 12, 12);
        graphics.setColor(WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("\u5269\u4f59 8.6 km", 20, 217);
        graphics.drawString("18 min", 128, 217);
        graphics.drawString("ETA 14:30", 218, 217);
        graphics.dispose();
        ImageIO.write(preview, "png", output.toFile());
    }

    private static void drawSpeedBadge(Graphics2D graphics, int centerX, int centerY, String text, boolean limit) {
        graphics.setColor(limit ? new Color(0xF2, 0xF4, 0xF3) : new Color(0x1D, 0x25, 0x21));
        graphics.fillOval(centerX - 21, centerY - 21, 42, 42);
        graphics.setStroke(new BasicStroke(limit ? 5 : 2));
        graphics.setColor(limit ? new Color(0xE3, 0x45, 0x45) : GREEN);
        graphics.drawOval(centerX - 21, centerY - 21, 42, 42);
        graphics.setColor(limit ? DARK : WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        int width = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - width / 2, centerY + 6);
    }

    private static void verifyOutputs(Path root, List<Icon> icons) throws IOException {
        Path assets = root.resolve("assets/navigation");
        for (Icon icon : icons) {
            BufferedImage image = ImageIO.read(assets.resolve("png/128/" + icon.name + ".png").toFile());
            require(image != null && image.getWidth() == 128 && image.getHeight() == 128, icon.name + " PNG size");
            require(image.getColorModel().hasAlpha(), icon.name + " alpha channel");
            require(image.getRGB(0, 0) >>> 24 == 0, icon.name + " transparent corner");
            String svg = Files.readString(assets.resolve("svg/" + icon.name + ".svg"));
            require(svg.contains("viewBox=\"0 0 128 128\""), icon.name + " SVG viewBox");
        }
        BufferedImage sprite = ImageIO.read(assets.resolve("navigation-sprite-4x4.png").toFile());
        require(sprite != null && sprite.getWidth() == 512 && sprite.getHeight() == 512, "sprite size");
        String manifest = Files.readString(assets.resolve("manifest.json"));
        long entries = manifest.lines().filter(line -> line.contains("{\"name\":" )).count();
        require(entries == icons.size(), "manifest entries");
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("Asset verification failed: " + label);
    }

    private interface ShapeCommand {
        void draw(Graphics2D graphics);
        String svg();
        String vector();
    }

    private record Icon(String name, List<ShapeCommand> commands) {}

    private record Polyline(Color color, int width, boolean filled, int[] coordinates) implements ShapeCommand {
        @Override
        public void draw(Graphics2D graphics) {
            Path2D path = path();
            graphics.setColor(color);
            if (filled) {
                graphics.fill(path);
            } else {
                graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(path);
            }
        }

        @Override
        public String svg() {
            String attributes = filled
                ? "fill=\"" + hex(color) + "\""
                : "fill=\"none\" stroke=\"" + hex(color) + "\" stroke-width=\"" + width
                    + "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"";
            return "<path d=\"" + pathData() + "\" " + attributes + "/>";
        }

        @Override
        public String vector() {
            String attributes = filled
                ? "android:fillColor=\"" + hex(color) + "\""
                : "android:fillColor=\"#00000000\" android:strokeColor=\"" + hex(color)
                    + "\" android:strokeWidth=\"" + width
                    + "\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\"";
            return "<path android:pathData=\"" + pathData() + "\" " + attributes + " />";
        }

        private Path2D path() {
            Path2D path = new Path2D.Double();
            path.moveTo(coordinates[0], coordinates[1]);
            for (int index = 2; index < coordinates.length; index += 2) {
                path.lineTo(coordinates[index], coordinates[index + 1]);
            }
            if (filled) path.closePath();
            return path;
        }

        private String pathData() {
            StringBuilder data = new StringBuilder("M ").append(coordinates[0]).append(',').append(coordinates[1]);
            for (int index = 2; index < coordinates.length; index += 2) {
                data.append(" L ").append(coordinates[index]).append(',').append(coordinates[index + 1]);
            }
            if (filled) data.append(" Z");
            return data.toString();
        }
    }

    private record Circle(Color color, int width, int centerX, int centerY, int radius) implements ShapeCommand {
        @Override
        public void draw(Graphics2D graphics) {
            Ellipse2D circle = new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2.0, radius * 2.0);
            graphics.setColor(color);
            if (width == 0) graphics.fill(circle);
            else {
                graphics.setStroke(new BasicStroke(width));
                graphics.draw(circle);
            }
        }

        @Override
        public String svg() {
            String attributes = width == 0 ? "fill=\"" + hex(color) + "\""
                : "fill=\"none\" stroke=\"" + hex(color) + "\" stroke-width=\"" + width + "\"";
            return "<circle cx=\"" + centerX + "\" cy=\"" + centerY + "\" r=\"" + radius + "\" "
                + attributes + "/>";
        }

        @Override
        public String vector() {
            String path = "M " + (centerX - radius) + "," + centerY + " A " + radius + "," + radius
                + " 0 1,0 " + (centerX + radius) + "," + centerY + " A " + radius + "," + radius
                + " 0 1,0 " + (centerX - radius) + "," + centerY;
            String attributes = width == 0 ? "android:fillColor=\"" + hex(color) + "\""
                : "android:fillColor=\"#00000000\" android:strokeColor=\"" + hex(color)
                    + "\" android:strokeWidth=\"" + width + "\"";
            return "<path android:pathData=\"" + path + "\" " + attributes + " />";
        }
    }

    private static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}
