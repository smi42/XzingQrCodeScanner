package zxingscanner;



import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.swing.*;
import com.github.sarxos.webcam.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixReader;

public class zxingscanner extends JFrame implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(zxingscanner.class.getName());
    private static final long serialVersionUID = 1L;
    private static final int FRAME_SKIP = 5;
    private static final int ROI_SIZE = 300;
    private static final int SCAN_INTERVAL_MIN = 50;
    private static final int SCAN_INTERVAL_MAX = 500;

    private BlockingQueue<String> decodedResultsQueue = new LinkedBlockingQueue<>();
    private ExecutorService executor = Executors.newFixedThreadPool(2);
    private Webcam webcam = null;
    private WebcamPanel panel = null;
    private JTextArea textarea = null;
    private long startTime;
    private Set<String> recentlyScanedCodes = new HashSet<>();
    private int frameCounter = 0;
    private int scanInterval = 100;
    private int consecutiveFailures = 0;

    public zxingscanner() {
        super();
        initializeGUI();
        startTime = System.currentTimeMillis();
        executor.execute(this);
        startUIUpdateThread();
    }

    private void initializeGUI() {
        setLayout(new BorderLayout());
        setTitle("QR Code Scanner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        webcam = Webcam.getWebcams().get(1);
        java.awt.Dimension[] resolutions = webcam.getViewSizes();
        java.awt.Dimension optimalResolution = findOptimalResolution(resolutions);
        LOGGER.info("Optimal resolution: " + optimalResolution);
        webcam.setViewSize(optimalResolution);

        panel = new WebcamPanel(webcam);
        panel.setPreferredSize(optimalResolution);

        textarea = new JTextArea();
        textarea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textarea);
        scrollPane.setPreferredSize(optimalResolution);

        add(panel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);

        pack();
        setVisible(true);
    }


private java.awt.Dimension findOptimalResolution(java.awt.Dimension[] resolutions) {
    // Choose a resolution that balances quality and performance
    // This is a simple implementation; you might want to adjust based on your specific needs
    if (resolutions.length > 1) {
        return resolutions[resolutions.length - 2]; // Second highest resolution
    }
    return resolutions[0];
}


    private Result processImageAndDecodeQRCode(BufferedImage image) {
        if (image == null) {
            LOGGER.warning("Input image is null.");
            return null;
        }

        // Apply ROI
        int width = image.getWidth();
        int height = image.getHeight();
        int x = (width - ROI_SIZE) / 2;
        int y = (height - ROI_SIZE) / 2;
        BufferedImage roi = image.getSubimage(x, y, ROI_SIZE, ROI_SIZE);

        BufferedImage grayscaleImage = new BufferedImage(ROI_SIZE, ROI_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayscaleImage.getGraphics();
        g.drawImage(roi, 0, 0, null);
        g.dispose();

        LuminanceSource source = new BufferedImageLuminanceSource(grayscaleImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            DataMatrixReader dataMatrixReader = new DataMatrixReader();
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            return dataMatrixReader.decode(bitmap, hints);
        } catch (NotFoundException | ChecksumException | FormatException e) {
            // Don't log every failure, just return null
            return null;
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                Thread.sleep(scanInterval);

                if (!webcam.isOpen()) {
                    LOGGER.warning("Webcam is not open.");
                    continue;
                }

                BufferedImage image = webcam.getImage();
                if (image == null) {
                    LOGGER.warning("Webcam image is null.");
                    continue;
                }

                frameCounter++;
                if (frameCounter % FRAME_SKIP != 0) {
                    continue;
                }

                Result result = processImageAndDecodeQRCode(image);
                if (result == null) {
                    handleScanFailure();
                    continue;
                }

                handleScanSuccess(result);

            } catch (InterruptedException e) {
                LOGGER.severe("Thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleScanFailure() {
        consecutiveFailures++;
        if (consecutiveFailures > 5) {
            scanInterval = Math.min(SCAN_INTERVAL_MAX, scanInterval + 50);
        }
    }

    private void handleScanSuccess(Result result) {
        String text = result.getText();
        if (!recentlyScanedCodes.contains(text)) {
            recentlyScanedCodes.add(text);
            decodedResultsQueue.add(text);
            LOGGER.info("QR code scanned: " + text);

            consecutiveFailures = 0;
            scanInterval = Math.max(SCAN_INTERVAL_MIN, scanInterval - 10);
        }
    }

    private void startUIUpdateThread() {
        executor.execute(() -> {
            while (!Thread.interrupted()) {
                try {
                    String text = decodedResultsQueue.take();
                    SwingUtilities.invokeLater(() -> {
                        textarea.append(text + "\n");
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        textarea.append("Elapsed time: " + elapsedTime + " milliseconds\n");
                        LOGGER.info("Elapsed time: " + elapsedTime + " milliseconds");
                    });
                } catch (InterruptedException e) {
                    LOGGER.severe("UI update thread interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(zxingscanner::new);
    }
}



POMXML file :

    
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">  <modelVersion>4.0.0</modelVersion>
  <groupId>com.antelope</groupId>
  <artifactId>qr-code-scanner</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <dependencies>
        <dependency>
            <groupId>com.github.sarxos</groupId>
            <artifactId>webcam-capture</artifactId>
            <version>0.3.12</version>
        </dependency>
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>core</artifactId>
            <version>3.4.1</version>
        </dependency>
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>javase</artifactId>
            <version>3.4.1</version>
        </dependency>
    </dependencies>
    <build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <version>3.2.0</version>
            <configuration>
                <archive>
                    <manifest>
                        <addClasspath>true</addClasspath>
                        <classpathPrefix>lib/</classpathPrefix>
                        <mainClass>QRCodeScanner</mainClass>
                    </manifest>
                </archive>
            </configuration>
        </plugin>
        <plugin>
            <artifactId>maven-assembly-plugin</artifactId>
            <configuration>
                <archive>
                    <manifest>
                        <mainClass>QRCodeScanner</mainClass>
                    </manifest>
                </archive>
                <descriptorRefs>
                    <descriptorRef>jar-with-dependencies</descriptorRef>
                </descriptorRefs>
            </configuration>
            <executions>
                <execution>
                    <id>make-assembly</id>
                    <phase>package</phase>
                    <goals>
                        <goal>single</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
</project>
