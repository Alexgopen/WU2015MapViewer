package alexgopen.wurmunlimited.clientmods.wu2015map;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.imageio.*;
import java.io.*;

public class ImageViewer extends JPanel implements KeyListener, MouseWheelListener, ComponentListener, Runnable {
    private static final long serialVersionUID = 1L;
    BufferedImage image, backBuffer;
    double oldZoom = 0.0, zoom = 1.0;
    int offsetX = 0, offsetY = 0;
    boolean up, down, left, right;
    boolean running = true;
    JFrame frame;
    int mouseX = 0, mouseY = 0;
    
    // Repo edit url:
    // https://github.com/Alexgopen/latestMapUrl/blob/master/latestMapUrl.txt
    
    private static String latestMapUrlFile = "https://raw.githubusercontent.com/Alexgopen/WU2015MapViewer/refs/heads/master/latestMapUrl.txt";
    @SuppressWarnings("unused")
	private static String testImageUrl = "https://u.cubeupload.com/alexgopen/mapdumpJune222025.png";
    
    private static final boolean allowChoosing = false;

    public static void main(String[] args) {
        new ImageViewer().start();
    }

    void start() {
        String imageUrl = getImageUrl();
        // This is for testing
        // imageUrl = testImageUrl;
        if (imageUrl != null && !imageUrl.isEmpty())
        {
        	image = loadImageFromURL(imageUrl);
        }
        
        
        if (image == null) {
        	if (allowChoosing)
        	{
	            JOptionPane.showMessageDialog(null, "Could not load image from URL. Please select a file manually.");
	            image = loadImageFromChooser();
        	}
        	else
        	{
	            JOptionPane.showMessageDialog(null, "Failed to retrieve map image.");
        	}
            if (image == null) System.exit(0);
        }

        frame = new JFrame("WU 2015 Map Viewer");
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                running = false;
            }
        });
        frame.setSize(1200, 800);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(this);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);  // Center the window
        frame.addComponentListener(this);

        addKeyListener(this);
        addMouseWheelListener(this);
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });
        setFocusable(true);
        requestFocusInWindow();

        resizeBackBuffer();
        
        
        // Set initial zoom to minimum (zoomed out)
        zoom = 0.5;

        // Center the image
        int w = (int)(image.getWidth() * zoom);
        int h = (int)(image.getHeight() * zoom);
        int viewW = backBuffer.getWidth();
        int viewH = backBuffer.getHeight();

        // Center the image within the viewport
        offsetX = (viewW - w) / 2;
        offsetY = (viewH - h) / 2;

        clampPan();
        
        new Thread(this).start();
    }

    /**
     * Retrieves the url to reference the latest map dump
     * @return the realMapUrl if successful, otherwise return null
     */
    private String getImageUrl() {
        try {
            java.net.URL url = new java.net.URL(latestMapUrlFile);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);  // Important: follow redirects automatically
            
            int responseCode = conn.getResponseCode();
            System.out.println("GET Response Code: " + responseCode);
            if (responseCode != 200) {
                System.out.println("Failed to fetch latest map URL file. HTTP code: " + responseCode);
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String imageUrl = reader.readLine();
                if (imageUrl == null || imageUrl.trim().isEmpty()) {
                    System.out.println("Latest map URL file is empty.");
                    return null;
                }
                imageUrl = imageUrl.trim();
                System.out.println("Read image URL: " + imageUrl);
                return imageUrl;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    BufferedImage loadImageFromURL(String urlString) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            System.out.println("HTTP response code: " + responseCode);
            String contentType = conn.getContentType();
            System.out.println("Content-Type: " + contentType);

            if (responseCode != 200) {
                System.out.println("HTTP GET failed with code: " + responseCode);
                return null;
            }

            if (!contentType.startsWith("image")) {
                System.out.println("URL did not return an image. Content-Type: " + contentType);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    for (int i = 0; i < 10; i++) {
                        System.out.println(br.readLine());
                    }
                }
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    BufferedImage loadImageFromChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Image to View");
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                return ImageIO.read(chooser.getSelectedFile());
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    public void run() {
        long last = System.nanoTime();
        double nsPerUpdate = 1e9 / 60.0;
        while (running) {
            long now = System.nanoTime();
            if (now - last >= nsPerUpdate) {
                update();
                render();
                last = now;
            }
            try { Thread.sleep(1); } catch (Exception e) {}
        }
    }

    void update() {
        int move = (zoom == 2.0) ? 12 : (zoom == 4.0) ? 16 : (zoom == 1.0) ? 10 : 8;
        if (up) offsetY += move;
        if (down) offsetY -= move;
        if (left) offsetX += move;
        if (right) offsetX -= move;
        clampPan();
    }

    void render() {
        Graphics2D g = backBuffer.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, backBuffer.getWidth(), backBuffer.getHeight());

        int w = (int)(image.getWidth() * zoom);
        int h = (int)(image.getHeight() * zoom);

        int viewW = backBuffer.getWidth();
        int viewH = backBuffer.getHeight();

        int drawX = (w >= viewW) ? offsetX : (viewW - w) / 2;
        int drawY = (h >= viewH) ? offsetY : (viewH - h) / 2;

        // Draw map image
        g.drawImage(image, drawX, drawY, w, h, null);

        // Draw grid overlay
        g.setColor(new Color(0, 0, 0, 180)); // Semi-opaque black lines
        g.setStroke(new BasicStroke(1.0f));

        int cols = 26;
        int rows = 26;

        double cellW = (double)w / cols;
        double cellH = (double)h / rows;

        // Draw vertical lines
        for (int i = 0; i <= cols; i++) {
            int x = (int)(drawX + i * cellW);
            g.drawLine(x, drawY, x, drawY + h);
        }

        // Draw horizontal lines
        for (int i = 0; i <= rows; i++) {
            int y = (int)(drawY + i * cellH);
            g.drawLine(drawX, y, drawX + w, y);
        }

        // Draw labels
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.setColor(Color.WHITE);

        // Column numbers (1-26)
        for (int i = 0; i < cols; i++) {
            String label = String.valueOf(i + 1);
            int x = (int)(drawX + i * cellW + cellW / 2);
            int y = drawY - 4;
            g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, Math.max(y, 12));
        }

        // Row letters (A-Z) — always along left edge of screen
        for (int i = 0; i < rows; i++) {
            String label = String.valueOf((char)('A' + i));
            int y = (int)(drawY + i * cellH + cellH / 2 + g.getFontMetrics().getAscent() / 2);

            // Only draw if this row is at least partially visible on screen
            if (y >= -cellH && y <= viewH + cellH) {
                int labelX = 4;  // fixed 4px from left screen edge
                g.drawString(label, labelX, y);
            }
        }
        
        if (image != null) {
            g.setFont(new Font("Consolas", Font.PLAIN, 10));

            double relX = (mouseX - drawX) / cellW;
            double relY = (mouseY - drawY) / cellH;

            int col = (int)Math.floor(relX);
            int row = (int)Math.floor(relY);

            String coord;
            if (col >= 0 && col < cols && row >= 0 && row < rows) {
                coord = String.format("%c%02d", 'A' + row, col + 1);
            } else {
                coord = "";
            }

            if (!coord.isEmpty()) {
                // Measure text size
                FontMetrics fm = g.getFontMetrics();
                int textW = fm.stringWidth(coord);
                int textH = fm.getHeight();

                int padding = 3;
                int boxW = textW + padding * 2;
                int boxH = textH + padding;

                int tooltipX = mouseX;
                int tooltipY = mouseY;

                // Adjust if tooltip would go off screen
                if (tooltipX + boxW > viewW) tooltipX = viewW - boxW - 4;
                if (tooltipY + boxH > viewH) tooltipY = viewH - boxH - 4;

                // Draw tooltip background (more transparent)
                g.setColor(new Color(0, 0, 80, 100));
                tooltipX -= boxW;
                tooltipY -= boxH;
                g.fillRoundRect(tooltipX, tooltipY, boxW, boxH, 8, 8);

                // Draw text centered in box
                int textX = tooltipX + (boxW - textW) / 2;
                int textY = tooltipY + (boxH + fm.getAscent()) / 2 - 2;
                g.setColor(Color.WHITE);
                g.drawString(coord, textX, textY);
            }
        }


        g.dispose();
        repaint();
    }

    protected void paintComponent(Graphics g) {
        g.drawImage(backBuffer, 0, 0, null);
    }

    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_W) up = true;
        if (e.getKeyCode() == KeyEvent.VK_S) down = true;
        if (e.getKeyCode() == KeyEvent.VK_A) left = true;
        if (e.getKeyCode() == KeyEvent.VK_D) right = true;
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_W) up = false;
        if (e.getKeyCode() == KeyEvent.VK_S) down = false;
        if (e.getKeyCode() == KeyEvent.VK_A) left = false;
        if (e.getKeyCode() == KeyEvent.VK_D) right = false;
    }

    public void keyTyped(KeyEvent e) {}

    public void mouseWheelMoved(MouseWheelEvent e) {
        double[] levels = { 0.5, 1.0, 2.0, 4.0 };
        int idx = 1;
        for (int i = 0; i < levels.length; i++) if (zoom == levels[i]) idx = i;
        int oldIdx = idx;

        if (e.getWheelRotation() < 0 && idx < levels.length - 1) idx++;
        if (e.getWheelRotation() > 0 && idx > 0) idx--;

        if (idx == oldIdx) return;

        double mapX = (mouseX - offsetX) / zoom;
        double mapY = (mouseY - offsetY) / zoom;

        zoom = levels[idx];
        offsetX = (int)(mouseX - mapX * zoom);
        offsetY = (int)(mouseY - mapY * zoom);

        clampPan();
    }

    void clampPan() {
        int w = (int)(image.getWidth() * zoom);
        int h = (int)(image.getHeight() * zoom);
        int viewW = backBuffer.getWidth();
        int viewH = backBuffer.getHeight();

        if (w <= viewW) offsetX = 0;
        else {
            if (offsetX > 0) offsetX = 0;
            if (offsetX < viewW - w) offsetX = viewW - w;
        }

        if (h <= viewH) offsetY = 0;
        else {
            if (offsetY > 0) offsetY = 0;
            if (offsetY < viewH - h) offsetY = viewH - h;
        }
    }

    public void componentResized(ComponentEvent e) {
        resizeBackBuffer();
        clampPan();
    }

    public void componentMoved(ComponentEvent e) {}
    public void componentShown(ComponentEvent e) {}
    public void componentHidden(ComponentEvent e) {}

    void resizeBackBuffer() {
        backBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    }
}
