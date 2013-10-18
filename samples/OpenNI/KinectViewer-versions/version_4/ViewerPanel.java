
// ViewerPanel.java
// Andrew Davison, August 2011, ad@fivedots.psu.ac.th
// Version 4; copy to parent directory to use with OpenNIViewer.java

/* Based on OpenNI's SimpleViewer example
     Initialize OpenNI *without* using an XML file;
     Display a 8-bit grayscale representing the Kinect IR image.

   The image sometimes flickers until a webcam image had been shown, e.g. by
   calling OpenNI's NiViewer example, or version 3 of ViewerPanel ???
*/

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.ShortBuffer;
import java.text.DecimalFormat;

import javax.swing.*;

import org.OpenNI.Context;
import org.OpenNI.GeneralException;
import org.OpenNI.IRGenerator;
import org.OpenNI.IRMetaData;
import org.OpenNI.License;
import org.OpenNI.MapOutputMode;
import org.OpenNI.StatusException;

public class ViewerPanel extends JPanel implements Runnable {
    private static final int MIN_8_BIT = 0;
    private static final int MAX_8_BIT = 255;    // For 8 bits per sample (255)
    // for mapping the IR values into a 8-bit range

    // image vars
    private BufferedImage image = null;
    private int imWidth, imHeight;

    private volatile boolean isRunning;

    // used for the average ms processing information
    private int imageCount = 0;
    private long totalTime = 0;
    private DecimalFormat df;
    private Font msgFont;

    // OpenNI
    private Context context;
    // private IRMetaData irMD;
    private IRGenerator irGen;

    public ViewerPanel() {
        setBackground(Color.WHITE);

        df = new DecimalFormat("0.#");  // 1 dp
        msgFont = new Font("SansSerif", Font.BOLD, 18);

        configOpenNI();

        System.out.println("Image dimensions (" + imWidth + ", " +
                                   imHeight + ")");

        new Thread(this).start();   // start updating the panel's image
    } // end of ViewerPanel()

    private void configOpenNI()
    // create context, IR generator, and IR metadata
    // use IR metadata to avoid mirror flickering
    {
        try {
            context = new Context();

            // add the NITE Licence
            License licence = new License("PrimeSense", "0KOIk2JeIBYClPWVnMoRKn5cdY4=");   // vendor, key
            context.addLicense(licence);

            irGen = IRGenerator.create(context);

            MapOutputMode mapMode = new MapOutputMode(640, 480, 30);   // xRes, yRes, FPS
            irGen.setMapOutputMode(mapMode);

            // set Mirror mode for all
            context.setGlobalMirror(true);

            context.startGeneratingAll();
            System.out.println("Started context generating...");

            IRMetaData irMD = irGen.getMetaData();
            imWidth = irMD.getFullXRes();
            imHeight = irMD.getFullYRes();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }  // end of configOpenNI()

    public Dimension getPreferredSize() {
        return new Dimension(imWidth, imHeight);
    }

    public void run()
  /* update and display the webcam image whenever the context
     is updated.
  */ {
        isRunning = true;
        while (isRunning) {
            try {
                // context.waitAnyUpdateAll();
                context.waitOneUpdateAll(irGen);
                // wait for 'one' ('any' is safer)
            } catch (StatusException e) {
                System.out.println(e);
                System.exit(1);
            }

            long startTime = System.currentTimeMillis();
            updateIRImage();
            imageCount++;
            totalTime += (System.currentTimeMillis() - startTime);
            repaint();
        }

        // close down
        try {
            context.stopGeneratingAll();
        } catch (StatusException e) {
        }
        context.release();
        System.exit(1);
    }  // end of run()

    public void closeDown() {
        isRunning = false;
    }

    private void updateIRImage()
  /* get the IR data and find its minumum and maximum values before converting
     it to a grayscale image */ {
        try {
            ShortBuffer irSB = irGen.getIRMap().createShortBuffer();

            // search the IR data, storing the min and max values
            int numPoints = 0;
            int minIR = irSB.get();
            int maxIR = minIR;

            while (irSB.remaining() > 0) {
                int irVal = irSB.get();
                if (irVal > maxIR) {
                    maxIR = irVal;
                }
                if (irVal < minIR) {
                    minIR = irVal;
                }
                numPoints++;
            }
            irSB.rewind();

            // System.out.println("Minimum - Maximum IR: " + minIR + " - " + maxIR);
            // usual range seems to be 0 - 1020/1022

            // convert the IR values into 8-bit grayscales
            image = createGrayIm(irSB, minIR, maxIR);
        } catch (GeneralException e) {
            System.out.println(e);
        }
    }  // end of updateIRImage()

    private BufferedImage createGrayIm(ShortBuffer irSB, int minIR, int maxIR)
  /* map the IR values into a 8-bit range, so they can
     be converted to a grayscale image */ {
        BufferedImage image = new BufferedImage(imWidth, imHeight, BufferedImage.TYPE_BYTE_GRAY);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        float displayRatio = (float) (MAX_8_BIT - MIN_8_BIT) / (maxIR - minIR);
        int i = 0;
        while (irSB.remaining() > 0) {
            int irVal = irSB.get();
            int out;
            if (irVal <= minIR) {
                out = MIN_8_BIT;
            } else if (irVal >= maxIR) {
                out = MAX_8_BIT;
            } else {
                out = (int) ((irVal - minIR) * displayRatio);
            }
            data[i++] = (byte) out;
        }

        return image;
    }  // end of createGrayIm()

    public void paintComponent(Graphics g)
    // Draw the IR image and statistics info
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (image != null) {
            g2.drawImage(image, 0, 0, this);
        }

        writeStats(g2);
    } // end of paintComponent()

    private void writeStats(Graphics2D g2)
  /* write statistics in bottom-left corner, or
     "Loading" at start time */ {
        g2.setColor(Color.BLUE);
        g2.setFont(msgFont);
        int panelHeight = getHeight();
        if (imageCount > 0) {
            double avgGrabTime = (double) totalTime / imageCount;
            g2.drawString("Pic " + imageCount + "  " +
                                  df.format(avgGrabTime) + " ms",
                          5, panelHeight - 10);  // bottom left
        } else  // no image yet
        {
            g2.drawString("Loading...", 5, panelHeight - 10);
        }
    }  // end of writeStats()
} // end of ViewerPanel class

