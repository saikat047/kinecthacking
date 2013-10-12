
// ViewerPanel.java
// Andrew Davison, August 2011, ad@fivedots.psu.ac.th
// Version 5; copy to parent directory to use with OpenNIViewer.java

/* Based on OpenNI's SimpleViewer example
     Initialize OpenNI *without* using an XML file;
     Display a colourized version of the 8-bit depth image
    The mapping from 8-bits to colour is done
    using the ColorUtils library methods (http://code.google.com/p/colorutils/)
*/

import java.awt.*;

import javax.swing.*;

import java.awt.image.*;
import java.text.DecimalFormat;
import java.io.*;

import javax.imageio.*;

import java.util.*;

import org.OpenNI.*;

import java.nio.ShortBuffer;

import edu.scripps.fl.color.*;

public class ViewerPanel extends JPanel implements Runnable {
    private static final int MAX_DEPTH_SIZE = 10000;
    private static final int NUM_COLORS = 256;

    // image vars
    private byte[] imgbytes;
    private int imWidth, imHeight;
    private float histogram[];         // for the depth values
    private int maxDepth = 0;          // largest depth value
    private IndexColorModel colModel;    // used for colorizing

    private volatile boolean isRunning;

    // used for the average ms processing information
    private int imageCount = 0;
    private long totalTime = 0;
    private DecimalFormat df;
    private Font msgFont;

    // OpenNI
    private Context context;
    private DepthMetaData depthMD;
    private DepthGenerator depthGen;

    public ViewerPanel() {
        setBackground(Color.WHITE);

        df = new DecimalFormat("0.#");  // 1 dp
        msgFont = new Font("SansSerif", Font.BOLD, 18);

        colModel = genColorModel();
        configOpenNI();

        histogram = new float[MAX_DEPTH_SIZE];

        imWidth = depthMD.getFullXRes();
        imHeight = depthMD.getFullYRes();
        System.out.println("Image dimensions (" + imWidth + ", " +
                                   imHeight + ")");

        // create empty pixel's array of correct size and type
        imgbytes = new byte[imWidth * imHeight];

        new Thread(this).start();   // start updating the panel's image
    } // end of ViewerPanel()

    private IndexColorModel genColorModel()
    // generate a index colour model for the BufferedImage
    {
        byte[] r = new byte[NUM_COLORS];    // reds
        byte[] g = new byte[NUM_COLORS];    // greens
        byte[] b = new byte[NUM_COLORS];    // blues

        // ColorUtils comes with several predefined colour models
        // Color[] cs = ColorUtils.getRedtoBlueSpectrum(NUM_COLORS);
        // Color[] cs = ColorUtils.getRedtoYellowSpectrum(NUM_COLORS);
        // Color[] cs = ColorUtils.getColorsBetween(Color.RED, Color.GREEN, NUM_COLORS);
        Color[] cs = ColorUtils.getSpectrum(NUM_COLORS, 0, 1);
        // colours will range from red to violet

        for (int i = 0; i < NUM_COLORS; i++) {
            r[i] = (byte) cs[i].getRed();
            g[i] = (byte) cs[i].getGreen();
            b[i] = (byte) cs[i].getBlue();
        }
        return new IndexColorModel(8, NUM_COLORS, r, g, b);     // 8 == no of bits in pixel
    }  // end of genColorModel()

    private void configOpenNI()
    // create context and depth generator
    {
        try {
            context = new Context();

            // add the NITE Licence
            License licence = new License("PrimeSense", "0KOIk2JeIBYClPWVnMoRKn5cdY4=");   // vendor, key
            context.addLicense(licence);

            depthGen = DepthGenerator.create(context);
            MapOutputMode mapMode = new MapOutputMode(640, 480, 30);   // xRes, yRes, FPS
            depthGen.setMapOutputMode(mapMode);

            // set Mirror mode for all
            context.setGlobalMirror(true);

            context.startGeneratingAll();
            System.out.println("Started context generating...");

            depthMD = depthGen.getMetaData();
            // use depth metadata to access depth info (avoids bug with DepthGenerator)
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }  // end of configOpenNI()

    public Dimension getPreferredSize() {
        return new Dimension(imWidth, imHeight);
    }

    public void run()
  /* update and display the depth image whenever the context
     is updated.
  */ {
        isRunning = true;
        while (isRunning) {
            try {
                context.waitAnyUpdateAll();
            } catch (StatusException e) {
                System.out.println(e);
                System.exit(1);
            }
            long startTime = System.currentTimeMillis();
            updateDepthImage();
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
        System.exit(0);
    }  // end of run()

    public void closeDown() {
        isRunning = false;
    }

    private void updateDepthImage()
  /* build a new histogram of 8-bit depth values, and convert it to
     image pixels (stored as bytes) */ {
        // ShortBuffer depthBuf = depthMD.getData().createShortBuffer();
        ShortBuffer depthBuf = null;
        try {
            depthBuf = depthGen.getDepthMap().createShortBuffer();
        } catch (GeneralException e) {
            e.printStackTrace();
        }
        calcHistogram(depthBuf);
        depthBuf.rewind();

        while (depthBuf.remaining() > 0) {
            int pos = depthBuf.position();
            short depth = depthBuf.get();
            imgbytes[pos] = (byte) histogram[depth];
        }
    }  // end of updateDepthImage()

    private void calcHistogram(ShortBuffer depthBuf) {
        // reset histogram
        for (int i = 0; i <= maxDepth; i++)
            histogram[i] = 0;

        // record number of different depths in histogram[]
        int numPoints = 0;
        maxDepth = 0;
        while (depthBuf.remaining() > 0) {
            short depthVal = depthBuf.get();
            if (depthVal > maxDepth) {
                maxDepth = depthVal;
            }
            if ((depthVal != 0) && (depthVal < MAX_DEPTH_SIZE)) {   // skip histogram[0]
                histogram[depthVal]++;
                numPoints++;
            }
        }

        for (int i = 1; i <= maxDepth; i++)
            histogram[i] += histogram[i - 1];

        if (numPoints > 0) {
            for (int i = 1; i <= maxDepth; i++)  // skipping histogram[0]
                histogram[i] = (int) (256 * (1.0f - (histogram[i] / (float) numPoints)));
        }
    }  // end of calcHistogram()

    public void paintComponent(Graphics g)
    // Draw the depth image and statistics info
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        BufferedImage image = createColorImage(imgbytes, colModel);
        if (image != null) {
            g2.drawImage(image, 0, 0, this);
        }

        writeStats(g2);
    } // end of paintComponent()

    private BufferedImage createColorImage(byte[] imgbytes, IndexColorModel colModel)
  /* Create BufferedImage using the image pixel bytes
     and indexed color model */ {
        // create suitable raster that matches the color model
        WritableRaster wr = colModel.createCompatibleWritableRaster(imWidth, imHeight);

        // copy image data into data buffer of raster
        DataBufferByte dataBuffer = (DataBufferByte) wr.getDataBuffer();
        byte[] destPixels = dataBuffer.getData();
        System.arraycopy(imgbytes, 0, destPixels, 0, destPixels.length);

        // create BufferedImage from color model and raster
        return new BufferedImage(colModel, wr, false, null);
    }  // end of createColorImage()

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

