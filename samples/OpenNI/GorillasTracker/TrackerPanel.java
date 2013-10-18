// TrackerPanel.java
// Andrew Davison, September 2011, ad@fivedots.psu.ac.th

/*
    Based on the Java OpenNI UserTracker sample

    Displays a depth map where each user is coloured differently. A 2D skeleton is
    drawn over each user's depth image, which shows how the user's
    joints move over time.

    The skeletons are maintained, updated, and drawn by the Skeletons class.
*/

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.nio.ShortBuffer;
import java.text.DecimalFormat;

import javax.swing.*;

import org.OpenNI.Context;
import org.OpenNI.DepthGenerator;
import org.OpenNI.DepthMetaData;
import org.OpenNI.GeneralException;
import org.OpenNI.License;
import org.OpenNI.MapOutputMode;
import org.OpenNI.SceneMetaData;
import org.OpenNI.StatusException;
import org.OpenNI.UserGenerator;

public class TrackerPanel extends JPanel implements Runnable {
    private static final int MAX_DEPTH_SIZE = 10000;

    private Color USER_COLORS[] = { Color.RED, Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.PINK, Color.YELLOW, Color.WHITE };
          /* colors used to draw each user's depth image, except the last
             (white) which is for the background */

    private byte[] imgbytes;
    private int imWidth, imHeight;
    private float histogram[];        // for the depth values
    private int maxDepth = 0;         // largest depth value

    private volatile boolean isRunning;

    // used for the average ms processing information
    private int imageCount = 0;
    private long totalTime = 0;
    private DecimalFormat df;
    private Font msgFont;

    // OpenNI
    private Context context;
    private DepthMetaData depthMD;
    private Skeletons skels;
    private DepthGenerator depthGen;
    private SceneMetaData sceneMD;

    /*
        used to create a labeled depth map, where each pixel holds a user ID
        (1, 2, etc.), or 0 to mean it is part of the background
    */

    public TrackerPanel() {
        setBackground(Color.WHITE);

        df = new DecimalFormat("0.#");  // 1 dp
        msgFont = new Font("SansSerif", Font.BOLD, 18);

        configOpenNI();

        histogram = new float[MAX_DEPTH_SIZE];

        imWidth = depthMD.getFullXRes();
        imHeight = depthMD.getFullYRes();
        System.out.println("Image dimensions (" + imWidth + ", " +
                                   imHeight + ")");

        imgbytes = new byte[imWidth * imHeight * 3];

        new Thread(this).start();
    }

    /*
        create context, depth generator, depth metadata,
        user generator, scene metadata, and skeletons
    */
    private void configOpenNI() {
        try {
            context = new Context();

            License license = new License("PrimeSense", "0KOIk2JeIBYClPWVnMoRKn5cdY4=");
            context.addLicense(license);

            depthGen = DepthGenerator.create(context);
            MapOutputMode mapMode = new MapOutputMode(640, 480, 30);   // xRes, yRes, FPS
            depthGen.setMapOutputMode(mapMode);

            context.setGlobalMirror(true);         // set mirror mode

            depthMD = depthGen.getMetaData();
            // use depth metadata to access depth info (avoids bug with DepthGenerator)

            UserGenerator userGen = UserGenerator.create(context);
            sceneMD = userGen.getUserPixels(0);
            // used to return a map containing user IDs (or 0) at each depth location

            skels = new Skeletons(userGen, depthGen);

            context.startGeneratingAll();
            System.out.println("Started context generating...");
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(imWidth, imHeight);
    }

    public void closeDown() {
        isRunning = false;
    }

    public void run() {
        isRunning = true;
        while (isRunning) {
            try {
                context.waitAnyUpdateAll();
            } catch (StatusException e) {
                System.out.println(e);
                System.exit(1);
            }
            long startTime = System.currentTimeMillis();
            try {
                updateUserDepths();
            } catch (GeneralException e) {
                throw new IllegalStateException(e);
            }
            skels.update();
            imageCount++;
            totalTime += (System.currentTimeMillis() - startTime);
            repaint();
        }
        try {
            context.stopGeneratingAll();
        } catch (StatusException e) {
        }
        context.release();
        System.exit(0);
    }  // end of run()

    private void updateUserDepths() throws GeneralException {
        /*
            build a histogram of 8-bit depth values, and convert it to
            depth image bytes where each user is coloured differently
        */
        ShortBuffer depthBuf = depthGen.getDepthMap().createShortBuffer();
        calcHistogram(depthBuf);
        depthBuf.rewind();

        // use user IDs to colour the depth map
        ShortBuffer usersBuf = sceneMD.getData().createShortBuffer();
        /*
            usersBuf is a labeled depth map, where each pixel holds an
            user ID (e.g. 1, 2, 3), or 0 to denote that the pixel is
            part of the background.
         */
        while (depthBuf.remaining() > 0) {
            int pos = depthBuf.position();
            short depthVal = depthBuf.get();
            short userID = usersBuf.get();

            imgbytes[3 * pos] = 0;     // default colour is black when there's no depth data
            imgbytes[3 * pos + 1] = 0;
            imgbytes[3 * pos + 2] = 0;

            if (depthVal != 0) {
                // convert userID to index into USER_COLORS[]
                int colorIdx = userID % (USER_COLORS.length - 1);   // skip last color

                if (userID == 0) {
                    colorIdx = USER_COLORS.length - 1;
                }
                // use last index: the position of white in USER_COLORS[]

                // convert histogram value (0.0-1.0f) to a RGB color
                float histValue = histogram[depthVal];
                imgbytes[3 * pos] = (byte) (histValue * USER_COLORS[colorIdx].getRed());
                imgbytes[3 * pos + 1] = (byte) (histValue * USER_COLORS[colorIdx].getGreen());
                imgbytes[3 * pos + 2] = (byte) (histValue * USER_COLORS[colorIdx].getBlue());
            }
        }
    }

    private void calcHistogram(ShortBuffer depthBuf) {
        // reset histogram
        for (int i = 0; i <= maxDepth; i++) {
            histogram[i] = 0;
        }

        // record number of different depths in histogram[]
        int numPoints = 0;
        maxDepth = 0;
        while (depthBuf.remaining() > 0) {
            short depthVal = depthBuf.get();
            if (depthVal > maxDepth) {
                maxDepth = depthVal;
            }
            if ((depthVal != 0) && (depthVal < MAX_DEPTH_SIZE)) {      // skip histogram[0]
                histogram[depthVal]++;
                numPoints++;
            }
        }

        // convert into a cumulative depth count (skipping histogram[0])
        for (int i = 1; i <= maxDepth; i++) {
            histogram[i] += histogram[i - 1];
        }

        /*
            convert cumulative depth into the range 0.0 - 1.0f
            which will later be used to modify a color from USER_COLORS[]
        */
        if (numPoints > 0) {
            for (int i = 1; i <= maxDepth; i++) {
                histogram[i] = 1.0f - (histogram[i] / (float) numPoints);
            }
        }
    }

    // Draw the depth image with coloured users, skeletons, and statistics info
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        drawUserDepths(g2d);
        g2d.setFont(msgFont);
        skels.draw(g2d);
        writeStats(g2d);
    }

    private void drawUserDepths(Graphics2D g2d) {
        /*
            Create BufferedImage using the depth image bytes
            and a color model, then draw it
        */
        // define an 8-bit RGB channel color model
        ColorModel colorModel =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                        new int[] { 8, 8, 8 }, false, false, ComponentColorModel.OPAQUE,
                                        DataBuffer.TYPE_BYTE);

        // fill the raster with the depth image bytes
        DataBufferByte dataBuffer = new DataBufferByte(imgbytes, imWidth * imHeight * 3);

        WritableRaster raster = Raster.createInterleavedRaster(dataBuffer, imWidth, imHeight, imWidth * 3, 3, new int[] { 0, 1, 2 }, null);

        // combine color model and raster to create a BufferedImage
        BufferedImage image = new BufferedImage(colorModel, raster, false, null);

        g2d.drawImage(image, 0, 0, null);
    }

    private void writeStats(Graphics2D g2d) {
        /*
            write statistics in bottom-left corner, or
            "Loading" at start time
        */
        g2d.setColor(Color.BLUE);
        int panelHeight = getHeight();
        if (imageCount > 0) {
            double avgGrabTime = (double) totalTime / imageCount;
            g2d.drawString("Pic " + imageCount + "  " +
                                   df.format(avgGrabTime) + " ms", 5, panelHeight - 10);  // bottom left
        } else {
            g2d.drawString("Loading...", 5, panelHeight - 10);
        }
    }
}
