import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;

import javax.swing.*;
import java.awt.*;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
/**
 * QRCodeScanner is a class that extends JFrame and implements Runnable and ThreadFactory.
 * It is used to scan QR codes using a webcam and display the results in a GUI.
 */
public class QRCodeScanner extends JFrame implements Runnable, ThreadFactory {
    // Logger for logging information and errors
    private static final Logger LOGGER = Logger.getLogger(QRCodeScanner.class.getName());
    // Serial version UID for serialization
    private static final long serialVersionUID = 1L;
    // Result instance to represent no QR code found
    private static final Result NO_QR_CODE_FOUND = new Result("No QR code found", null, null, null);
    // Queue to store decoded results
    private BlockingQueue<String> decodedResultsQueue = new LinkedBlockingQueue<>();
    // Executor to run tasks in a separate thread
    private Executor executor = Executors.newSingleThreadExecutor(this);
    // Webcam to capture images
    private Webcam webcam = null;
    // Panel to display webcam feed
    private WebcamPanel panel = null;
    // Text area to display decoded results
    private JTextArea textarea = null;
    // Scroll pane to make text area scrollable
    private JScrollPane scrollPane = null;
    // Start time of the application
    private long startTime;

    
    // Static block to setup logger
    static {
        try {
            String desktopPath = System.getProperty("user.home") + "/Desktop";
            FileHandler fileHandler = new FileHandler(desktopPath + "/logs.txt", true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error occurred while setting up log file.", e);
        }
    }

    
    /**
     * Constructor for QRCodeScanner.
     * Initializes the GUI and starts the executor.
     */
    public QRCodeScanner() {
        // omitted for brevity
    	super();

        setLayout(new BorderLayout());
        setTitle("QR Code Scanner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        webcam = Webcam.getWebcams().get(1); // Initialize webcam before using it

        Dimension[] resolutions = webcam.getViewSizes();
        Dimension maxResolution = resolutions[resolutions.length - 1];
        LOGGER.info("Max resolution of the webcam: " + maxResolution);

        webcam.setViewSize(maxResolution);

        panel = new WebcamPanel(webcam);
        panel.setPreferredSize(maxResolution);

        textarea = new JTextArea();
        textarea.setEditable(false);
        scrollPane = new JScrollPane(textarea);
        scrollPane.setPreferredSize(maxResolution);

        add(panel, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);

        pack();
        setVisible(true);

        startTime = System.currentTimeMillis();  // Record the start time

        executor.execute(this);
    }

    /**
     * Processes the given image and decodes the QR code in it.
     * @param image the image to process
     * @return the decoded Result, or NO_QR_CODE_FOUND if no QR code is found
     */
    private Result processImageAndDecodeQRCode(BufferedImage image) {
        if (image == null) {
            LOGGER.warning("Input image is null.");
            return null;
        }

        BufferedImage grayscaleImage = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayscaleImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        LuminanceSource source = new BufferedImageLuminanceSource(grayscaleImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result = null;
        try {
            DataMatrixReader dataMatrixReader = new DataMatrixReader();
            result = dataMatrixReader.decode(bitmap);
        } catch (NotFoundException | ChecksumException | FormatException e) {
            LOGGER.warning("No Data Matrix code found.");
            LOGGER.warning("Exception message: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Exception stack trace: ", e);
            Throwable cause = e.getCause();
            if (cause != null) {
                LOGGER.warning("Cause of the exception: " + cause.toString());
            } else {
                LOGGER.warning("No specific cause for the exception.");
            }
            return NO_QR_CODE_FOUND;  // Return the special Result instance instead of throwing an exception
        }

        if (result != null) {
            Map<ResultMetadataType, Object> metadata = result.getResultMetadata();
            if (metadata != null) {
                Integer orientation = (Integer) metadata.get(ResultMetadataType.ORIENTATION);
                if (orientation != null) {
                    LOGGER.info("Barcode orientation: " + orientation + " degrees");
                } else {
                    LOGGER.info("Barcode orientation not available");
                }
            }
        }

        return result;
    }

    /**
     * The run method for the Runnable interface.
     * Continuously captures images from the webcam and processes them.
     */
       
    @Override
    public void run() {
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                LOGGER.severe("Thread interrupted: " + e.getMessage());
            }

            BufferedImage image = null;

            if (webcam.isOpen()) {
                LOGGER.info("Webcam is open.");
                if ((image = webcam.getImage()) == null) {
                    LOGGER.warning("Webcam image is null.");
                    continue;
                }
            } else {
                LOGGER.warning("Webcam is not open.");
            }

            Result result = processImageAndDecodeQRCode(image);

            if (result == NO_QR_CODE_FOUND) {  // Check if the Result is the special instance
                continue;  // Skip the rest of the loop iteration
            }

            if (result != null) {
                if (result.getBarcodeFormat() == BarcodeFormat.UPC_E) {
                    LOGGER.info("Detected UPC_E format, rescanning...");
                    continue;
                }
                result.getBarcodeFormat();
                LOGGER.info("QR code format: " + result.getBarcodeFormat());
                result.getNumBits();
                LOGGER.info("QR code num bits: " + result.getNumBits());
                result.getResultMetadata();
                LOGGER.info("QR code result metadata: " + result.getResultMetadata());
                result.getRawBytes();
                LOGGER.info("QR code raw bytes: " + result.getRawBytes());
                String text = result.getText();
                LOGGER.info("QR code text: " + text);
                decodedResultsQueue.add(text);
            }
        } while (true);
    }

    /**
     * Starts a new thread to update the UI with decoded results.
     */    
    public void startUIUpdateThread() {
        new Thread(() -> {
            while (true) {
                try {
                    // Take the next decoded result from the queue and update the UI
                    String text = decodedResultsQueue.take();
                    textarea.append(text + "\n");

                    // Calculate elapsed time
                    long endTime = System.currentTimeMillis();
                    long elapsedTime = endTime - startTime;
                    textarea.append("Elapsed time: " + elapsedTime + " milliseconds\n");

                    // Log elapsed time
                    LOGGER.info("Elapsed time: " + elapsedTime + " milliseconds");

                    // Stop the application
                    System.exit(0);

                } catch (InterruptedException e) {
                    LOGGER.severe("UI update thread interrupted: " + e.getMessage());
                }
            }
        }).start();
    }
    /**
     * The newThread method for the ThreadFactory interface.
     * Creates a new daemon thread with the given Runnable.
     * @param r the Runnable to run in the new thread
     * @return the new Thread
     */
    
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "example-runner");
        t.setDaemon(true);
        return t;
    }
    /**
     * The main method.
     * Creates a new QRCodeScanner and starts the UI update thread.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        QRCodeScanner scanner = new QRCodeScanner();
        scanner.startUIUpdateThread();  // Start the UI update thread
    }
}
