
// ViewerPanel.java
// Andrew Davison, August 2011, ad@fivedots.psu.ac.th
// Version 6; copy to parent directory to use with OpenNIViewer.java

/* Based on OpenNI's SimpleViewer example
     Initialize OpenNI *without* using an XML file;
     Display a colourized version of the 8-bit depth image *combined with* the Kinect webcam image
    The mapping from 8-bits to colour is done
    using the ColorUtils library methods (http://code.google.com/p/colorutils/)

    The depth colouring code is from Version 5 of
    this class. The webcam code is from Version 3.
   
    The colouring is a little more complicated since
    it includes an alpha so that the depth image
    becomes a little transparent, so the webcam 
    image can be seen.
*/

import java.awt.*;

import javax.swing.*;

import java.awt.image.*;
import java.text.DecimalFormat;
import java.io.*;

import javax.imageio.*;

import java.util.*;

import org.OpenNI.*;

import java.nio.*;

import edu.scripps.fl.color.*;

public class ViewerPanel extends JPanel implements Runnable {
    private static final int MAX_DEPTH_SIZE = 10000;
    private static final int NUM_COLORS = 256;

    // image vars
    private byte[] imgbytes;
    private BufferedImage cameraImage = null;
    private int imWidth, imHeight;
    private float histogram[];         // for the depth values
    private int maxDepth = 0;         // largest depth value
    private IndexColorModel colModel;     // used for colorizing

    private volatile boolean isRunning;

    // used for the average ms processing information
    private int imageCount = 0;
    private long totalTime = 0;
    private DecimalFormat df;
    private Font msgFont;

    // OpenNI
    private Context context;
    private DepthMetaData depthMD;
    private ImageGenerator imageGen;
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
    // (including an alpha channel)
    {
        byte[] r = new byte[NUM_COLORS];    // reds
        byte[] g = new byte[NUM_COLORS];    // greens
        byte[] b = new byte[NUM_COLORS];    // blues
        byte[] a = new byte[NUM_COLORS];    // alphas

        Color[] cs = ColorUtils.getSpectrum(NUM_COLORS, 0, 1);
        // colours will range from red to blue

        for (int i = 0; i < NUM_COLORS; i++) {
            r[i] = (byte) cs[i].getRed();
            g[i] = (byte) cs[i].getGreen();
            b[i] = (byte) cs[i].getBlue();
            a[i] = (byte) 0x88;     // transparency
        }
        return new IndexColorModel(8, NUM_COLORS, r, g, b, a);       // 8 == no of bits in pixel
    }  // end of genColorModel()

    private void configOpenNI()
    // create context, depth generator, image generator
    {
        try {
            context = new Context();

            // add the NITE Licence
            License licence = new License("PrimeSense", "0KOIk2JeIBYClPWVnMoRKn5cdY4=");   // vendor, key
            context.addLicense(licence);

            depthGen = DepthGenerator.create(context);
            imageGen = ImageGenerator.create(context);

            // if possible, set the viewpoint of the DepthGenerator to match the ImageGenerator
            boolean hasAltView = depthGen.isCapabilitySupported("AlternativeViewPoint");
            System.out.println("Alternative ViewPoint supported: " + hasAltView);

            MapOutputMode mapMode = new MapOutputMode(640, 480, 30);   // xRes, yRes, FPS
            depthGen.setMapOutputMode(mapMode);
            imageGen.setMapOutputMode(mapMode);
            imageGen.setPixelFormat(PixelFormat.RGB24);

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
  /* update and display the depth and webcam images 
     whenever the context is updated.
  */ {
        isRunning = true;
        while (isRunning) {
            try {
                // context.waitAnyUpdateAll();
                context.waitAndUpdateAll();  // wait for all nodes to have new data, then updates them
            } catch (StatusException e) {
                System.out.println(e);
                System.exit(1);
            }
            long startTime = System.currentTimeMillis();
            updateImage();
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

    private void updateImage()
    // get image data as bytes; convert to an image
    {
        try {
            ByteBuffer imageBB = imageGen.getImageMap().createByteBuffer();
            cameraImage = bufToImage(imageBB);
        } catch (GeneralException e) {
            System.out.println(e);
        }
    }  // end of updateImage()

    private BufferedImage bufToImage(ByteBuffer pixelsRGB)
  /* Transform the ByteBuffer of pixel data into a BufferedImage
     Converts RGB bytes to ARGB ints with no transparency. 
     (Same as in Version 3.)
  */ {
        int[] pixelInts = new int[imWidth * imHeight];

        int rowStart = 0;
        // rowStart will index the first byte (red) in each row;
        // starts with first row, and moves down

        int bbIdx;               // index into ByteBuffer
        int i = 0;               // index into pixels int[]
        int rowLen = imWidth * 3;    // number of bytes in each row
        for (int row = 0; row < imHeight; row++) {
            bbIdx = rowStart;
            // System.out.println("bbIdx: " + bbIdx);
            for (int col = 0; col < imWidth; col++) {
                int pixR = pixelsRGB.get(bbIdx++);
                int pixG = pixelsRGB.get(bbIdx++);
                int pixB = pixelsRGB.get(bbIdx++);
                pixelInts[i++] =
                        0xFF000000 | ((pixR & 0xFF) << 16) |
                                ((pixG & 0xFF) << 8) | (pixB & 0xFF);
            }
            rowStart += rowLen;   // move to next row
        }

        // create a BufferedImage from the pixel data
        BufferedImage im =
                new BufferedImage(imWidth, imHeight, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, imWidth, imHeight, pixelInts, 0, imWidth);
        return im;
    }  // end of bufToImage()

    private void updateDepthImage() {
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
        maxDepth = depthBuf.get();
        int minDepth = maxDepth;    // also record minimum depth
        depthBuf.rewind();

        // count number of different depths
        int numPoints = 0;
        while (depthBuf.remaining() > 0) {
            short depthVal = depthBuf.get();
            if (depthVal > maxDepth) {
                maxDepth = depthVal;
            }
            if (depthVal < minDepth) {
                minDepth = depthVal;
            }
            if ((depthVal != 0) && (depthVal < MAX_DEPTH_SIZE)) {     // skip histogram[0]
                histogram[depthVal]++;
                numPoints++;
            }
        }

        // System.out.println("No. of numPoints: " + numPoints);
        // System.out.println("Minimum-Maximum depth range: " + minDepth + " - " + maxDepth);

        // convert into a cummulative depth count (skipping histogram[0])
        for (int i = 1; i <= maxDepth; i++)
            histogram[i] += histogram[i - 1];

    /* convert cummulative depth into 8-bit range (0-255), 
       which will later become colors using an indexed colour model
    */
        if (numPoints > 0) {
            for (int i = 1; i <= maxDepth; i++)   // skipping histogram[0]
                histogram[i] = (int) (256 * (1.0f - (histogram[i] / (float) numPoints)));
        }
    }  // end of calcHistogram()

    public void paintComponent(Graphics g)
  /* Draw the webcam image first, then the translucent depth image 
     on top of it, and finally the statistics info  */ {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (cameraImage != null) {
            g2.drawImage(cameraImage, 0, 0, this);
        }

        BufferedImage depthImage = createColorImage(imgbytes, colModel);
        if (depthImage != null) {
            g2.drawImage(depthImage, 0, 0, this);
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

