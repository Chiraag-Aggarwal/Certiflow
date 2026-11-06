import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/** Rebuilds the small, locally drawn sparkle animation used in the welcome panel. */
public class CreateAcademicAssets {
    public static void main(String[] args) throws Exception {
        Path target = Path.of(args[0]);
        Files.createDirectories(target.getParent());
        byte[] r = {0, (byte)216, (byte)250, (byte)137, (byte)103};
        byte[] g = {0, (byte)168, (byte)230, (byte)101, (byte)139};
        byte[] b = {0, (byte)77, (byte)177, (byte)45, (byte)109};
        IndexColorModel colours = new IndexColorModel(8, 5, r, g, b, 0);
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < 24; frame++) {
                BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_BYTE_INDEXED, colours);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double phase = frame * Math.PI * 2 / 24;
                graphics.setColor(new Color(216,168,77));
                star(graphics, 45, 48 + Math.sin(phase) * 2, 21 + Math.sin(phase) * 1.5, 8);
                graphics.setColor(new Color(103,139,109));
                star(graphics, 75, 24, 7 + Math.cos(phase) * 1.5, 3);
                graphics.setColor(new Color(137,101,45));
                graphics.fillOval(19, 71, 5, 5);
                graphics.dispose();
                var metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode table = node(root, "LocalColorTable");
                table.setAttribute("sizeOfLocalColorTable", "8");
                table.setAttribute("sortFlag", "FALSE");
                while (table.hasChildNodes()) table.removeChild(table.getFirstChild());
                for (int colour = 0; colour < 8; colour++) {
                    IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", Integer.toString(colour));
                    entry.setAttribute("red", Integer.toString(colour < r.length ? r[colour] & 255 : 0));
                    entry.setAttribute("green", Integer.toString(colour < g.length ? g[colour] & 255 : 0));
                    entry.setAttribute("blue", Integer.toString(colour < b.length ? b[colour] & 255 : 0));
                    table.appendChild(entry);
                }
                IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "restoreToBackgroundColor");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "TRUE");
                control.setAttribute("delayTime", "10");
                control.setAttribute("transparentColorIndex", "0");
                if (frame == 0) {
                    IIOMetadataNode application = new IIOMetadataNode("ApplicationExtension");
                    application.setAttribute("applicationID", "NETSCAPE");
                    application.setAttribute("authenticationCode", "2.0");
                    application.setUserObject(new byte[] {1, 0, 0});
                    node(root, "ApplicationExtensions").appendChild(application);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        BufferedImage gif = ImageIO.read(target.toFile());
        if (gif.getRGB(45, 48) != new Color(216, 168, 77).getRGB()) throw new IllegalStateException("GIF palette did not survive encoding.");
        BufferedImage books = ImageIO.read(target.resolveSibling("study-books.png").toFile());
        System.out.println("Animation saved. Illustration alpha: " + books.getColorModel().hasAlpha()
                + "; corner alpha: " + (books.getRGB(0, 0) >>> 24));
    }
    private static void star(Graphics2D g, double x, double y, double outer, double inner) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 4;
            double radius = i % 2 == 0 ? outer : inner;
            double px = x + Math.cos(angle) * radius, py = y + Math.sin(angle) * radius;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.closePath();
        g.fill(path);
    }
    private static IIOMetadataNode node(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++)
            if (parent.item(i).getNodeName().equals(name)) return (IIOMetadataNode) parent.item(i);
        IIOMetadataNode child = new IIOMetadataNode(name);
        parent.appendChild(child);
        return child;
    }
}
