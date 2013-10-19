// Skeletons.java
// Andrew Davison, September 2011, ad@fivedots.psu.ac.th

/* Skeletons sets up four 'observers' (listeners) so that 
   when a new user is detected in the scene, a standard pose for that 
   user is detected, the user skeleton is calibrated in the pose, and then the
   skeleton is tracked. The start of tracking adds a skeleton entry to userSkeletonJoints.

   Each call to update() updates the joint positions for each user's
   skeleton.
  
   Each call to draw() draws each user's skeleton, with a rotated HEAD_FNM
   image for their head, and status text at the body's center-of-mass.
*/

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.OpenNI.CalibrationProgressEventArgs;
import org.OpenNI.CalibrationProgressStatus;
import org.OpenNI.DepthGenerator;
import org.OpenNI.IObservable;
import org.OpenNI.IObserver;
import org.OpenNI.Point3D;
import org.OpenNI.PoseDetectionCapability;
import org.OpenNI.PoseDetectionEventArgs;
import org.OpenNI.SkeletonCapability;
import org.OpenNI.SkeletonJoint;
import org.OpenNI.SkeletonJointPosition;
import org.OpenNI.SkeletonProfile;
import org.OpenNI.StatusException;
import org.OpenNI.UserEventArgs;
import org.OpenNI.UserGenerator;

public class HeadHandsSkeletonTracker implements SkeletonTracker {
    private static final String HEAD_FNM = "gorilla.png";

    // used to colour a user's limbs so they're different from the user's body color
    private Color USER_COLORS[] = { Color.RED, Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.PINK, Color.YELLOW, Color.WHITE };

    private BufferedImage headImage;

    private UserGenerator userGenerator;
    private DepthGenerator depthGenerator;

    private SkeletonCapability skeletonCapability;
    private PoseDetectionCapability poseDetectionCapability;

    private String calibrationPoseName = null;

    private HashMap<Integer, HashMap<SkeletonJoint, SkeletonJointPosition>> userSkeletonJoints;

    public HeadHandsSkeletonTracker(UserGenerator userGenerator, DepthGenerator depthGenerator) {
        this.userGenerator = userGenerator;
        this.depthGenerator = depthGenerator;
        headImage = loadImage(HEAD_FNM);
        configure();
        userSkeletonJoints = new HashMap<Integer, HashMap<SkeletonJoint, SkeletonJointPosition>>();
    }

    private BufferedImage loadImage(String fnm) {
        BufferedImage im = null;
        try {
            im = ImageIO.read(new File(fnm));
            System.out.println("Loaded image from " + fnm);
        } catch (Exception e) {
            System.out.println("Unable to load image from " + fnm);
        }

        return im;
    }

    private void configure() {
        /*
            create pose and skeleton detection capabilities for the user generator,
            and set up observers (listeners)
         */
        try {
            // setup UserGenerator pose and skeleton detection capabilities;
            // should really check these using ProductionNode.isCapabilitySupported()
            poseDetectionCapability = userGenerator.getPoseDetectionCapability();

            skeletonCapability = userGenerator.getSkeletonCapability();
            calibrationPoseName = skeletonCapability.getSkeletonCalibrationPose();  // the 'psi' pose
            skeletonCapability.setSkeletonProfile(SkeletonProfile.ALL);
            // other possible values: UPPER_BODY, LOWER_BODY, HEAD_HANDS

            // set up four observers
            userGenerator.getNewUserEvent().addObserver(new NewUserObserver());
            userGenerator.getLostUserEvent().addObserver(new LostUserObserver());
            poseDetectionCapability.getPoseDetectedEvent().addObserver(new PoseDetectedObserver());
            skeletonCapability.getCalibrationCompleteEvent().addObserver(new CalibrationCompleteObserver());
        } catch (Exception e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    // update skeleton of each user
    public void update() {
        try {
            int[] userIDs = userGenerator.getUsers();
            for (int i = 0; i < userIDs.length; ++ i) {
                int userID = userIDs[i];
                if (skeletonCapability.isSkeletonCalibrating(userID)) {
                    continue;
                }
                if (skeletonCapability.isSkeletonTracking(userID)) {
                    updateJoints(userID);
                }
            }
        } catch (StatusException e) {
            System.out.println(e);
        }
    }

    // update all the joints for this userID in userSkeletonJoints
    private void updateJoints(int userID) {
        HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJointPositionMap = userSkeletonJoints.get(userID);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.HEAD);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.NECK);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_SHOULDER);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_ELBOW);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_HAND);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_SHOULDER);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_ELBOW);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_HAND);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.TORSO);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_HIP);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_KNEE);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.LEFT_FOOT);

        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_HIP);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_KNEE);
        updateJoint(skeletonJointPositionMap, userID, SkeletonJoint.RIGHT_FOOT);
    }

    private void updateJoint(HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJointPositionMap,
                             int userID, SkeletonJoint joint) {
        /*
            update the position of the specified user's joint by
            looking at the skeleton capability
        */
        try {
            // report unavailable joints (should not happen)
            if (!skeletonCapability.isJointAvailable(joint) || !skeletonCapability.isJointActive(joint)) {
                System.out.println(joint + " not available for updates");
                return;
            }

            SkeletonJointPosition jointPosition = skeletonCapability.getSkeletonJointPosition(userID, joint);
            if (jointPosition == null) {
                System.out.println("No update for " + joint);
                return;
            }
      
            SkeletonJointPosition skeletonJointPosition = null;
            if (jointPosition.getPosition().getZ() != 0) {
                skeletonJointPosition = new SkeletonJointPosition(depthGenerator
                                .convertRealWorldToProjective(jointPosition.getPosition()), jointPosition.getConfidence());
            } else {
                skeletonJointPosition = new SkeletonJointPosition(new Point3D(), 0);
            }
            skeletonJointPositionMap.put(joint, skeletonJointPosition);
        } catch (StatusException e) {
            System.out.println(e);
        }
    }

    // draw skeleton of each user, with a head image, and user status
    public void draw(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(8));

        try {
            int[] userIDs = userGenerator.getUsers();
            for (int i = 0; i < userIDs.length; ++ i) {
                setLimbColor(g2d, userIDs[i]);
                if (skeletonCapability.isSkeletonCalibrating(userIDs[i])) {
                }
                else if (skeletonCapability.isSkeletonTracking(userIDs[i])) {
                    HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJoints = userSkeletonJoints.get(userIDs[i]);
                    drawSkeleton(g2d, skeletonJoints);
                    drawHead(g2d, skeletonJoints);
                }
                drawUserStatus(g2d, userIDs[i]);
            }
        } catch (StatusException e) {
            System.out.println(e);
        }
    }

    private void setLimbColor(Graphics2D g2d, int userID) {
        /*
            use the 'opposite' of the user ID color for the limbs, so they
            stand out against the colored body
        */
        Color color = USER_COLORS[userID % USER_COLORS.length];
        Color oppColor = new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue());
        g2d.setColor(oppColor);
    }

    // draw skeleton as lines (limbs) between its joints;
    // hardwired to avoid non-implemented joints
    private void drawSkeleton(Graphics2D g2d, HashMap<SkeletonJoint, SkeletonJointPosition> skel) {
        drawLine(g2d, skel, SkeletonJoint.HEAD, SkeletonJoint.NECK);

        drawLine(g2d, skel, SkeletonJoint.LEFT_SHOULDER, SkeletonJoint.TORSO);
        drawLine(g2d, skel, SkeletonJoint.RIGHT_SHOULDER, SkeletonJoint.TORSO);

        drawLine(g2d, skel, SkeletonJoint.NECK, SkeletonJoint.LEFT_SHOULDER);
        drawLine(g2d, skel, SkeletonJoint.LEFT_SHOULDER, SkeletonJoint.LEFT_ELBOW);
        drawLine(g2d, skel, SkeletonJoint.LEFT_ELBOW, SkeletonJoint.LEFT_HAND);

        drawLine(g2d, skel, SkeletonJoint.NECK, SkeletonJoint.RIGHT_SHOULDER);
        drawLine(g2d, skel, SkeletonJoint.RIGHT_SHOULDER, SkeletonJoint.RIGHT_ELBOW);
        drawLine(g2d, skel, SkeletonJoint.RIGHT_ELBOW, SkeletonJoint.RIGHT_HAND);

        drawLine(g2d, skel, SkeletonJoint.LEFT_HIP, SkeletonJoint.TORSO);
        drawLine(g2d, skel, SkeletonJoint.RIGHT_HIP, SkeletonJoint.TORSO);
        drawLine(g2d, skel, SkeletonJoint.LEFT_HIP, SkeletonJoint.RIGHT_HIP);

        drawLine(g2d, skel, SkeletonJoint.LEFT_HIP, SkeletonJoint.LEFT_KNEE);
        drawLine(g2d, skel, SkeletonJoint.LEFT_KNEE, SkeletonJoint.LEFT_FOOT);

        drawLine(g2d, skel, SkeletonJoint.RIGHT_HIP, SkeletonJoint.RIGHT_KNEE);
        drawLine(g2d, skel, SkeletonJoint.RIGHT_KNEE, SkeletonJoint.RIGHT_FOOT);
    }

    // draw a line (limb) between the two joints (if they have positions)
    private void drawLine(Graphics2D g2d, HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJointPositionMap,
                          SkeletonJoint firstJoint, SkeletonJoint secondJoint) {
        Point3D pointStart = getJointPos(skeletonJointPositionMap, firstJoint);
        Point3D pointEnd = getJointPos(skeletonJointPositionMap, secondJoint);
        if (pointStart != null && pointEnd != null) {
            drawLine(g2d, pointStart, pointEnd);
        }
    }

    private void drawLine(Graphics2D g2d, Point3D pointStart, Point3D pointEnd) {
        g2d.drawLine((int) pointStart.getX(), (int) pointStart.getY(), (int) pointEnd.getX(), (int) pointEnd.getY());
    }

    // get the (x, y, z) coordinate for the joint (or return null)
    private Point3D getJointPos(HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJoints, SkeletonJoint joint) {
        SkeletonJointPosition pos = skeletonJoints.get(joint);
        if (pos == null) {
            return null;
        }

        // don't draw a line to a joint with a zero-confidence pos
        if (pos.getConfidence() == 0) {
            return null;
        }

        return pos.getPosition();
    }

    // draw a head image rotated around the z-axis to follow the neck-->head line
    private void drawHead(Graphics2D g2d, HashMap<SkeletonJoint, SkeletonJointPosition> skeletonJoints) {
        if (headImage == null) {
            return;
        }

        Point3D headPt = getJointPos(skeletonJoints, SkeletonJoint.HEAD);
        Point3D neckPt = getJointPos(skeletonJoints, SkeletonJoint.NECK);
        if ((headPt == null) || (neckPt == null)) {
            return;
        } else {
            int angle = 90 - ((int) Math.round(Math.toDegrees(Math.atan2(neckPt.getY() - headPt.getY(), headPt.getX() - neckPt.getX()))));
            drawRotatedHead(g2d, headPt, headImage, angle);
        }
    }

    private void drawRotatedHead(Graphics2D g2d, Point3D headPt, BufferedImage headImage, int angle) {
        AffineTransform originalTransform = g2d.getTransform();
        AffineTransform headTransform = (AffineTransform) (originalTransform.clone());

        // center of rotation is the head joint
        headTransform.rotate(Math.toRadians(angle), (int) headPt.getX(), (int) headPt.getY());
        g2d.setTransform(headTransform);

        // draw image centered at head joint
        int x = (int) headPt.getX() - (headImage.getWidth() / 2);
        int y = (int) headPt.getY() - (headImage.getHeight() / 2);
        g2d.drawImage(headImage, x, y, null);

        g2d.setTransform(originalTransform);    // reset original orientation
    }

    // draw user ID and status on the skeleton at its center of mass (CoM)
    private void drawUserStatus(Graphics2D g2d, int userID) throws StatusException {
        Point3D massCenter = depthGenerator.convertRealWorldToProjective(userGenerator.getUserCoM(userID));
        String label;
        if (skeletonCapability.isSkeletonTracking(userID)) {
            label = "Tracking user " + userID;
        } else if (skeletonCapability.isSkeletonCalibrating(userID)) {
            label = "Calibrating user " + userID;
        } else {
            label = "Looking for " + calibrationPoseName + " pose for user " + userID;
        }

        g2d.drawString(label, (int) massCenter.getX(), (int) massCenter.getY());
    }

    // --------------------- 4 observers -----------------------
    /*
        user detection --> pose detection --> skeleton calibration -->
        skeleton tracking (and creation of userSkeletonJoints entry)
        + may also lose a user (and so delete its userSkeletonJoints entry)
    */

    class NewUserObserver implements IObserver<UserEventArgs> {
        public void update(IObservable<UserEventArgs> observable, UserEventArgs args) {
            int userID = args.getId();
            System.out.println("Detected new user " + userID);
            try {
                if (skeletonCapability.needPoseForCalibration()) {
                    System.out.println("Starting pose detection");
                    poseDetectionCapability.startPoseDetection(calibrationPoseName, userID);
                } else {
                    System.out.println("Starting skeleton calibration");
                    skeletonCapability.requestSkeletonCalibration(userID, true);
                }
            } catch (StatusException e) {
                e.printStackTrace();
            }
        }
    }

    class LostUserObserver implements IObserver<UserEventArgs> {
        public void update(IObservable<UserEventArgs> observable, UserEventArgs args) {
            userSkeletonJoints.remove(args.getId());
        }
    }

    class PoseDetectedObserver implements IObserver<PoseDetectionEventArgs> {
        public void update(IObservable<PoseDetectionEventArgs> observable, PoseDetectionEventArgs args) {
            int userID = args.getUser();
            try {
                System.out.println("Stopping pose detection");
                poseDetectionCapability.stopPoseDetection(userID);
                System.out.println("Starting skeleton calibration");
                skeletonCapability.requestSkeletonCalibration(userID, true);
            } catch (StatusException e) {
                e.printStackTrace();
            }
        }
    }

    class CalibrationCompleteObserver implements IObserver<CalibrationProgressEventArgs> {
        public void update(IObservable<CalibrationProgressEventArgs> observable, CalibrationProgressEventArgs args) {
            int userID = args.getUser();
            System.out.println("Calibration status: " + args.getStatus() + " for user " + userID);
            try {
                if (args.getStatus() == CalibrationProgressStatus.OK) {
                    skeletonCapability.startTracking(userID);
                    userSkeletonJoints.put(userID, new HashMap<SkeletonJoint, SkeletonJointPosition>());
                } else  {
                    System.out.println("Starting pose detection");
                    poseDetectionCapability.startPoseDetection(calibrationPoseName, userID);
                }
            } catch (StatusException e) {
                e.printStackTrace();
            }
        }
    }
}
