package com.hanul.aipacs.ai.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class ImageUtils {
    private ImageUtils() {
    }

    public static byte[] resizeGrayscale(byte[] grayscale, int width, int height, int targetWidth, int targetHeight) {
        BufferedImage source = grayscaleImage(grayscale, width, height);
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return ((DataBufferByte) resized.getRaster().getDataBuffer()).getData();
    }

    public static byte[] toPng(byte[] grayscale, int width, int height) {
        return encode(grayscaleImage(grayscale, width, height));
    }

    public static String toBase64Png(byte[] image, int width, int height) {
        return Base64.getEncoder().encodeToString(toPng(image, width, height));
    }

    public static String toBase64Png(byte[] image, int width, int height, boolean rgb) {
        byte[] png = rgb ? toPngRgb(image, width, height) : toPng(image, width, height);
        return Base64.getEncoder().encodeToString(png);
    }

    public static byte[] toPngRgb(byte[] rgb, int width, int height) {
        return encode(rgbImage(rgb, width, height));
    }

    public static byte[] fromBufferedImage(BufferedImage image) {
        byte[] bytes = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        byte[] rgb = new byte[image.getWidth() * image.getHeight() * 3];
        for (int i = 0; i < image.getWidth() * image.getHeight(); i++) {
            int src = i * 3;
            int dst = i * 3;
            if (src + 2 >= bytes.length) {
                break;
            }
            rgb[dst] = bytes[src + 2];
            rgb[dst + 1] = bytes[src + 1];
            rgb[dst + 2] = bytes[src];
        }
        return rgb;
    }

    private static BufferedImage grayscaleImage(byte[] grayscale, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(grayscale, 0, target, 0, Math.min(grayscale.length, target.length));
        return image;
    }

    private static BufferedImage rgbImage(byte[] rgb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < width * height; i++) {
            int src = i * 3;
            int dst = i * 3;
            if (src + 2 >= rgb.length || dst + 2 >= target.length) {
                break;
            }
            target[dst] = rgb[src + 2];
            target[dst + 1] = rgb[src + 1];
            target[dst + 2] = rgb[src];
        }
        return image;
    }

    private static byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode PNG", e);
        }
    }
}
