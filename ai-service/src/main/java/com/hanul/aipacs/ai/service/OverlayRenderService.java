package com.hanul.aipacs.ai.service;

import com.hanul.aipacs.ai.domain.BoundingBox;
import com.hanul.aipacs.ai.util.ImageUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OverlayRenderService {
    public byte[] makeOverlay(byte[] image, byte[] heatmap, int width, int height, List<BoundingBox> boxes) {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y * width) + x;
                int gray = Byte.toUnsignedInt(image[index]);
                int rgbIndex = index * 3;
                int r = clamp((int) Math.round(gray * 0.66 + Byte.toUnsignedInt(heatmap[rgbIndex]) * 0.34));
                int g = clamp((int) Math.round(gray * 0.66 + Byte.toUnsignedInt(heatmap[rgbIndex + 1]) * 0.34));
                int b = clamp((int) Math.round(gray * 0.66 + Byte.toUnsignedInt(heatmap[rgbIndex + 2]) * 0.34));
                bufferedImage.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.setColor(new Color(82, 255, 214));
        graphics.setStroke(new BasicStroke(3f));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (BoundingBox box : boxes) {
            graphics.drawRect(box.x(), box.y(), box.width(), box.height());
            graphics.drawString(box.label(), box.x() + 4, Math.max(14, box.y() - 4));
        }
        graphics.dispose();
        return ImageUtils.fromBufferedImage(bufferedImage);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
