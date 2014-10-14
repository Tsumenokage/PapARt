/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.inria.papart.procam;

import org.bytedeco.javacpp.freenect;
import fr.inria.papart.drawingapp.Button;
import fr.inria.papart.depthcam.Kinect;
import fr.inria.papart.multitouch.TouchInput;
import fr.inria.papart.procam.camera.CameraFactory;
import fr.inria.papart.procam.camera.CameraOpenKinect;
import java.lang.reflect.Constructor;
import java.util.Set;
import org.reflections.Reflections;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PVector;

/**
 *
 * @author jiii
 */
public class Papart {

    public final static String folder = fr.inria.papart.procam.Utils.getPapartFolder();
    public static String proCamCalib = folder + "/data/calibration/camera-projector.yaml";
    public static String camCalibARtoolkit = folder + "/data/calibration/camera-projector.cal";
    public static String kinectIRCalib = folder + "/data/calibration/calibration-kinect-IR.yaml";
    public static String kinectRGBCalib = folder + "/data/calibration/calibration-kinect-RGB.yaml";
    public static String kinectScreenCalib = folder + "/data/calibration/KinectScreenCalibration.txt";
    public static String defaultFont = folder + "/data/Font/" + "GentiumBookBasic-48.vlw";
    public int defaultFontSize = 12;

    protected static Papart singleton = null;

    protected float zNear = 10;
    protected float zFar = 6000;

    private final PApplet applet;
    private final Class appletClass;

    private boolean displayInitialized;
    private boolean cameraInitialized;
    private boolean touchInitialized;
    private ARDisplay display;
    private Projector projector;
    private Camera cameraTracking;
    private Kinect kinect;
    private TouchInput touchInput;
    private PVector frameSize;
    private CameraOpenKinect cameraOpenKinect;

    // TODO: find what to do with these...
    private final int depthFormat = freenect.FREENECT_DEPTH_10BIT;
    private final int kinectFormat = Kinect.KINECT_10BIT;
//    private final int depthFormat = freenect.FREENECT_DEPTH_MM;
//    private final int kinectFormat = Kinect.KINECT_MM;

    public Papart(Object applet) {
        this.displayInitialized = false;
        this.cameraInitialized = false;
        this.touchInitialized = false;
        this.applet = (PApplet) applet;

        this.appletClass = applet.getClass();
        PFont font = this.applet.loadFont(defaultFont);
        Button.setFont(font);
        Button.setFontSize(defaultFontSize);
        // TODO: singleton -> Better implementation. 
        if (Papart.singleton == null) {
            Papart.singleton = this;
        }
    }

    public static Papart getPapart() {
        return Papart.singleton;
    }

    public void initProjectorCamera(String cameraNo, Camera.Type cameraType) {
        initProjectorCamera(cameraNo, cameraType, 1);
    }

    /**
     * Load a projector & camera couple. Default configuration files are used.
     *
     * @param quality
     * @param cameraNo
     * @param cameraType
     */
    public void initProjectorCamera(String cameraNo, Camera.Type cameraType, float quality) {
        assert (!cameraInitialized);

        initProjectorDisplay(quality);

        cameraTracking = CameraFactory.createCamera(cameraType, cameraNo);
        cameraTracking.setParent(applet);
        cameraTracking.setCalibration(proCamCalib);
        cameraTracking.start();
        loadTracking(proCamCalib);
        cameraTracking.setThread();

        checkInitialization();
    }

    public void initKinectCamera(float quality) {
        assert (!cameraInitialized);

        cameraTracking = CameraFactory.createCamera(Camera.Type.OPEN_KINECT, 0);
        cameraTracking.setParent(applet);
        cameraTracking.setCalibration(kinectRGBCalib);
        cameraTracking.start();

        loadTracking(kinectRGBCalib);
        cameraTracking.setThread();

        initARDisplay(quality);

        checkInitialization();
    }

    /**
     * Initialize a camera for object tracking.
     *
     * @see initCamera(String, int, float)
     */
    public void initCamera(String cameraNo, Camera.Type cameraType) {
        initCamera(cameraNo, cameraType, 1);
    }

    public void initCamera(String cameraNo, Camera.Type cameraType, float quality) {
        assert (!cameraInitialized);

        cameraTracking = CameraFactory.createCamera(cameraType, cameraNo);
        cameraTracking.setParent(applet);
        cameraTracking.setCalibration(proCamCalib);
        cameraTracking.start();
        loadTracking(proCamCalib);
        cameraTracking.setThread();

        initARDisplay(quality);
        checkInitialization();
    }

    private void initProjectorDisplay(float quality) {
        // TODO: check if file exists !
        projector = new Projector(this.applet, proCamCalib, zNear, zFar, quality);
        display = projector;
        displayInitialized = true;
        frameSize = new PVector(projector.getWidth(), projector.getHeight());
    }

    private void initARDisplay(float quality) {
        assert (this.cameraTracking != null && this.applet != null);

        display = new ARDisplay(this.applet, cameraTracking,
                zNear, zFar, quality);
        frameSize = new PVector(display.getWidth(), display.getHeight());
        displayInitialized = true;
    }

    private void checkInitialization() {
        assert (cameraTracking != null);
        this.applet.registerMethod("dispose", this);
        this.applet.registerMethod("stop", this);
    }

    private void loadTracking(String calibrationPath) {
        // TODO: check if file exists !
        Camera.convertARParams(this.applet, calibrationPath, camCalibARtoolkit);
        cameraTracking.initMarkerDetection(camCalibARtoolkit);

        // The camera view is handled in another thread;
        cameraInitialized = true;
    }

    /**
     * Touch input when the camera tracking the markers is a Kinect.
     *
     * @param touch2DPrecision
     * @param touch3DPrecision
     */
    public void loadTouchInputKinectOnly(int touch2DPrecision,
            int touch3DPrecision) {

        if (this.cameraTracking == null) {
            cameraTracking = CameraFactory.createCamera(Camera.Type.OPEN_KINECT, 0);
            cameraTracking.setParent(applet);
            cameraTracking.setCalibration(kinectRGBCalib);
            cameraTracking.start();
            cameraTracking.setThread();
            cameraInitialized = true;
            checkInitialization();
        }

        kinect = new Kinect(this.applet,
                kinectIRCalib,
                kinectRGBCalib,
                kinectFormat);

        touchInput = new TouchInput(this.applet,
                (CameraOpenKinect) cameraTracking,
                kinect, kinectScreenCalib);
        touchInput.useRawDepth((CameraOpenKinect) cameraTracking);

        touchInput.setPrecision(touch2DPrecision, touch3DPrecision);
        touchInitialized = true;
    }

    /**
     * *
     * Touch input with a Kinect calibrated with the display area.
     *
     * @param touch2DPrecision
     * @param touch3DPrecision
     */
    public void loadTouchInput(int touch2DPrecision, int touch3DPrecision) {

        cameraOpenKinect = (CameraOpenKinect) CameraFactory.createCamera(Camera.Type.OPEN_KINECT, 0);
        cameraOpenKinect.setParent(this.applet);
        cameraOpenKinect.setCalibration(Papart.kinectRGBCalib);
        cameraOpenKinect.setDepthFormat(depthFormat);
        cameraOpenKinect.start();
        cameraOpenKinect.setThread();

        kinect = new Kinect(this.applet,
                kinectIRCalib,
                kinectRGBCalib,
                kinectFormat);

        touchInput = new TouchInput(this.applet,
                cameraOpenKinect,
                kinect, kinectScreenCalib);

        // TODO: use Raw depth for Touch also here
        // Conversion Kinect -> Projector
//        touchInput.useRawDepth(cameraTracking);

        touchInput.setPrecision(touch2DPrecision, touch3DPrecision);

        touchInitialized = true;
    }

    public void loadSketches() {

        // Sketches are not within a package.
        Reflections reflections = new Reflections("");

        Set<Class<? extends PaperTouchScreen>> paperTouchScreenClasses = reflections.getSubTypesOf(PaperTouchScreen.class);
        for (Class<? extends PaperTouchScreen> klass : paperTouchScreenClasses) {
            try {
                Class[] ctorArgs2 = new Class[1];
                ctorArgs2[0] = this.appletClass;
                Constructor<? extends PaperTouchScreen> constructor = klass.getDeclaredConstructor(ctorArgs2);
                System.out.println("Starting a PaperTouchScreen. " + klass.getName());
                constructor.newInstance(this.appletClass.cast(this.applet));
            } catch (Exception ex) {
                System.out.println("Error loading PapartTouchApp : " + klass.getName() + ex);
                ex.printStackTrace();
            }
        }

        Set<Class<? extends PaperScreen>> paperScreenClasses = reflections.getSubTypesOf(PaperScreen.class);

        // Add them once.
        paperScreenClasses.removeAll(paperTouchScreenClasses);
        for (Class<? extends PaperScreen> klass : paperScreenClasses) {
            try {
                Class[] ctorArgs2 = new Class[1];
                ctorArgs2[0] = this.appletClass;
                Constructor<? extends PaperScreen> constructor = klass.getDeclaredConstructor(ctorArgs2);
                System.out.println("Starting a PaperScreen. " + klass.getName());
                constructor.newInstance(this.appletClass.cast(this.applet));
            } catch (Exception ex) {
                System.out.println("Error loading PapartApp : " + klass.getName());
            }
        }

    }

    public void startTracking() {
        this.cameraTracking.trackSheets(true);
    }

    public void stop() {
        this.dispose();
    }

    public void dispose() {
        if (touchInitialized && cameraOpenKinect != null) {
            cameraOpenKinect.close();
        }
        if (cameraInitialized && cameraTracking != null) {
            try {
                cameraTracking.close();
            } catch (Exception e) {
                System.err.println("Error closing the tracking camera" + e);
            }
        }
//        System.out.println("Cameras closed.");
    }

    public ARDisplay getDisplay() {
        assert (displayInitialized);
        return this.display;
    }

    public Camera getCameraTracking() {
        assert (cameraInitialized);
        return this.cameraTracking;
    }

    public TouchInput getTouchInput() {
        assert (touchInitialized);
        return this.touchInput;
    }

    public PVector getFrameSize() {
        assert (this.frameSize != null);
        return this.frameSize.get();
    }

    public PApplet getApplet() {
        return applet;
    }

}