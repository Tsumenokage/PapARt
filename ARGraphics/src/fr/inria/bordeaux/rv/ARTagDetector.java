/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.inria.bordeaux.rv;

import com.googlecode.javacpp.BytePointer;
import com.googlecode.javacv.FrameGrabber.ImageMode;
import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.ARToolKitPlus;
import com.googlecode.javacv.cpp.ARToolKitPlus.ArtLogFunction;
import com.googlecode.javacv.cpp.ARToolKitPlus.MultiTracker;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import java.beans.IntrospectionException;
import java.beans.PropertyVetoException;
import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.logging.Logger;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;

import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_core.*;
import com.googlecode.javacv.cpp.opencv_core.*;

/**
 *
 * @author jeremy
 */
public class ARTagDetector {

    private FrameGrabber grabber;
    private IplImage iimg;
    private PImage pimg;
    private CameraDevice cam;
    private IplImage img2 = null;
    private IplImage bayerImg = null;
    private boolean lastUndistorted;
    private boolean isCopy = false;
    private HashMap<MarkerBoard, PVector> lastPosMap;
//    static private boolean useSafeMode = false;
    private boolean hasBayer;

    public ARTagDetector(int device, int w, int h, int framerate, String yamlCameraProj, String cameraFile, MarkerBoard[] paperSheets) {
        this(device, null, w, h, framerate, yamlCameraProj, cameraFile, paperSheets, false);
    }

    public ARTagDetector(int device, int w, int h, int framerate, String yamlCameraProj, String cameraFile, MarkerBoard[] paperSheets, boolean hasBayer) {
        this(device, null, w, h, framerate, yamlCameraProj, cameraFile, paperSheets, hasBayer);
    }

    public ARTagDetector(String fileName, int w, int h, int framerate, String yamlCameraProj, String cameraFile, MarkerBoard[] paperSheets) {
        this(-1, fileName, w, h, framerate, yamlCameraProj, cameraFile, paperSheets, false);
    }

    // TODO: ARTagDetector avec CameraDevice en parametre...
    // TODO: Gestion du Grabber dans la classe Camera...
    protected ARTagDetector(int device, String videoFile, int w, int h, int framerate, String yamlCameraProj,
            String cameraFile, MarkerBoard[] paperSheets, boolean hasBayer) {

        // check the files
        File f1 = new File(cameraFile);
        assert (f1.exists());
        for (MarkerBoard p : paperSheets) {
            String name = p.getFileName();
            File f2 = new File(name);
            assert (f2.exists());
        }

        this.hasBayer = hasBayer;

        if (hasBayer) {
            CvSize outSize = new CvSize();
            outSize.width(w);
            outSize.height(h);
            bayerImg = cvCreateImage(outSize, 8, 3);
        }

        // Init the camera parameters
        try {
            CameraDevice[] c = CameraDevice.read(yamlCameraProj);
            if (c.length > 0) {
                cam = c[0];
            }

            boolean useConfig = w == -1 && h == -1;


            // TODO: load camera id ... or camera name ...
            if (device == -1) {
                grabber = new OpenCVFrameGrabber(videoFile);
            } else {
                if (videoFile == null) {
                    grabber = new OpenCVFrameGrabber(device);

                }
            }

            if (!useConfig) {
                grabber.setImageWidth(w);
                grabber.setImageHeight(h);
                grabber.setImageMode(ImageMode.RAW);
                grabber.setFrameRate(framerate);
                grabber.setDeinterlace(true);
//                grabber.setTriggerMode(false);
//                grabber.setNumBuffers(6);
//                grabber.setTriggerMode(true);
//                grabber.flush();
            }

            pimg = new PImage(w, h, PApplet.RGB);

            ArtLogFunction f = new ArtLogFunction() {

                @Override
                public void call(String nStr) {
                    Logger.getLogger(MarkerDetector.class.getName()).warning(nStr);
                }
            };
            ARToolKitPlus.Logger log = new ARToolKitPlus.Logger(null);

            // ARToolkitPlus tracker 
//            if (useSafeMode) {
            lastPosMap = new HashMap<MarkerBoard, PVector>();
//            }

            for (MarkerBoard sheet : paperSheets) {

                MultiTracker tracker = new MultiTracker(w, h);

                //            int pixfmt = ARToolKitPlus.PIXEL_FORMAT_LUM;
                int pixfmt = ARToolKitPlus.PIXEL_FORMAT_BGR;

                tracker.setPixelFormat(pixfmt);
                tracker.setBorderWidth(0.125f);
                tracker.activateAutoThreshold(true);
                tracker.setUndistortionMode(ARToolKitPlus.UNDIST_NONE);
                tracker.setPoseEstimator(ARToolKitPlus.POSE_ESTIMATOR_RPP);
                tracker.setMarkerMode(ARToolKitPlus.MARKER_ID_BCH);
                tracker.setImageProcessingMode(ARToolKitPlus.IMAGE_FULL_RES);
                tracker.setUseDetectLite(false);

                if (!tracker.init(cameraFile, sheet.getFileName(), 1.0f, 1000.f, log)) {
                    throw new Exception("Init ARTOOLKIT Error" + sheet.getFileName() + " " + sheet.getName());
                }

                float[] transfo = new float[16];
                for (int i = 0; i < 3; i++) {
                    transfo[12 + i] = 0;
                }
                transfo[15] = 0;

//                if (useSafeMode) {
                lastPosMap.put(sheet, new PVector());
//                }

                sheet.setTracker(tracker, transfo);

            }
            grabber.start();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void grab() {
        grab(false, isCopy);
    }

    public void grab(boolean undistort) {
        grab(undistort, isCopy);
    }
    public int nbImagesCopied = 0;

    public void grab(boolean undistort, boolean copy) {
        try {

            this.lastUndistorted = undistort;
//            grabber.trigger();
            iimg = grabber.grab();

            if (hasBayer) {
//
//                if (bayerImg == null) {
//                    bayerImg = iimg.clone();
//                }

                cvCvtColor(iimg, bayerImg, CV_BayerBG2BGR);
                iimg = bayerImg;
                // Deal with undistort ? 

            }

            if (undistort) {
                if (img2 == null) {
                    img2 = iimg.clone();
                }
                cam.undistort(iimg, img2);
            }

            // Image drawing
            if (copy) {
                ByteBuffer buff1 = iimg.getByteBuffer();
                pimg.loadPixels();
                for (int i = 0; i
                        < iimg.width() * iimg.height(); i++) {
                    int offset = i * 3;
                    pimg.pixels[i] = (buff1.get(offset + 2) & 0xFF) << 16
                            | (buff1.get(offset + 1) & 0xFF) << 8
                            | (buff1.get(offset) & 0xFF);
                }

                pimg.updatePixels();

                // TODO: HACK
                if (nbImagesCopied++ == 60) {
                    System.gc();
                    nbImagesCopied = 0;
                }

            }
        } catch (Exception e) {
            System.out.println("Exception in findMarkers " + e);
        }
    }

    public float[] findMarkers(MarkerBoard sheet) {
        sheet.updatePosition(lastUndistorted ? img2 : iimg);
        return sheet.getTransfo();
    }

    public boolean isReady(boolean undistort) {
        if (undistort) {
            return img2 != null;
        }
        return iimg != null;
    }

    public PImage getImage() {
        return pimg;
    }

    public IplImage getImageIpl() {
        if (img2 == null) {
            return iimg;
        }
        return img2;
    }

    public void setCopyToPimage(boolean isCopy) {
        this.isCopy = isCopy;
    }

    public void close() {
        try {
            grabber.stop();
        } catch (Exception e) {
        }
    }
    // findMarkers avec une autre bibliothèque d'entrée vidéo que JavaCV
    int imgSizeByte = 640 * 480 * 4;
    BytePointer bp = null;
    ByteBuffer bb = null;
    IntBuffer ib = null;
// ------------------------ Working. ----------------
    // Old functions to delete... 
//    public float[] findMarkers(PImage img) {
//
//        if (bp == null) {
//            bp = new BytePointer(imgSizeByte);
////            bb = bp.asByteBuffer();
////            bb = bp.asByteBuffer(imgSizeByte);
//            ib = bb.asIntBuffer();
//        }
//
//        try {
//
//            int k;
//
////            BufferedImage bufferedImage = (BufferedImage) img.getImage();
////            int[] imgData = new int[648 * 480];
////            imgData = bufferedImage.getRGB(0, 0, 640, 480, imgData, 0, 1);
////            System.out.println(imgData);
//
////            Raster data = bufferedImage.getData();
////
////            DataBuffer dataBuffer = data.getDataBuffer();
////            DataBufferInt dbi = (DataBufferInt) dataBuffer;
////            int[] imgData = dbi.getData();
//
////            ib.put(img.pixels);
////
////            for (int i = 0; i < 8; i++) {
////                System.out.print(bb.get(i) + " ");
////            }
////            System.out.print("\n");
////
////            ib.rewind();
//
//            int[] px = img.pixels;
//            k = 0;
//            for (int i = 0; i < 640 * 480; i++) {
////                bb.put(k++, (byte) (px[i]>> 24));
//                bb.put(k++, (byte) (px[i] >> 16));
//                bb.put(k++, (byte) (px[i] >> 8));
//                bb.put(k++, (byte) (px[i]));
//            }
//
////            for (int i = 0; i < 8; i++) {
////                       System.out.print(bb.get(i) + " ");
////            }
////            System.out.print("\n");
//
//
//            tracker.calc(bp);
//
//            numDetect = tracker.getNumDetectedMarkers();
////            System.out.println(numDetect + " detected ");
//
//            if (numDetect < 0) {
//                return null;
//            }
//
//            tracker.getDetectedMarker(0);
//
//            ARMultiMarkerInfoT multiMarkerConfig = tracker.getMultiMarkerConfig();
////            DoubleBuffer buff = multiMarkerConfig.trans().asBuffer(12);
//            DoubleBuffer buff = multiMarkerConfig.trans().asBuffer();
//            k = 0;
//            for (int i = 0; i < 12; i++) {
//                transfo[i] = (float) buff.get(k++);
//            }
//
//            return transfo;
//
//        } catch (Exception e) {
//            System.out.println("Exception : " + e);
//        }
//        return null;
//    }
//
//    public float[] findMarkers(boolean undistort) {
//        return findMarkers(undistort, false);
//    }
//
//    public float[] findMarkers(boolean undistort, boolean copy) {
//        try {
//            iimg = grabber.grab();
//
//            if (undistort) {
//                if (img2 == null) {
//                    img2 = iimg.clone();
//                }
//                cam.undistort(iimg, img2);
//                tracker.calc(img2.imageData());
//            } else {
//                tracker.calc(iimg.imageData());
//            }
//
//            numDetect = tracker.getNumDetectedMarkers();
////            System.out.println(numDetect + " detected ");
//
//            if (numDetect < 0) {
//                return transfo;
//            }
//
//            tracker.getDetectedMarker(0);
//
//            ARMultiMarkerInfoT multiMarkerConfig = tracker.getMultiMarkerConfig();
////            DoubleBuffer buff = multiMarkerConfig.trans().asBuffer(12);
//            DoubleBuffer buff = multiMarkerConfig.trans().asBuffer();
//            int k = 0;
////            for (int i = 0; i < 12; i++) {
////                transfo[i] = (float) buff.get(k++);
////            }
//
//            for (int i = 0; i < 12; i++) {
//                transfo[i] = (float) multiMarkerConfig.trans().get(k++);
//            }
//
//            // Image drawing
//            if (copy) {
//                ByteBuffer buff1 = iimg.getByteBuffer();
//                pimg.loadPixels();
//                for (int i = 0; i
//                        < iimg.width() * iimg.height(); i++) {
//                    int offset = i * 3;
////            ret.pixels[i] = applet.color(buff.get(offset + 0) & 0xff, buff.get(offset + 1) & 0xFF, buff.get(offset + 2) & 0xff);
//                    pimg.pixels[i] = (buff1.get(offset + 2) & 0xFF) << 16
//                            | (buff1.get(offset + 1) & 0xFF) << 8
//                            | (buff1.get(offset) & 0xFF);
//                }
//                pimg.updatePixels();
//            }
//            return transfo;
//
//        } catch (Exception e) {
//            System.out.println("Exception in findMarkers " + e);
//        }
//
//        return null;
//    }
//
//    public float[][] findMultiMarkers(boolean undistort, boolean copy) {
//        try {
//            iimg = grabber.grab();
//
//            if (undistort) {
//                if (img2 == null) {
//                    img2 = iimg.clone();
//                }
//                cam.undistort(iimg, img2);
//                for (MultiTracker tracker : trackers) {
//                    tracker.calc(img2.imageData());
//                }
//            } else {
//                for (MultiTracker tracker : trackers) {
//                    tracker.calc(img2.imageData());
//                }
//            }
//
//            int trackId = 0;
//            for (MultiTracker tracker : trackers) {
//                if (tracker.getNumDetectedMarkers() < 0) {
//                    continue;
//                }
//
//                ARMultiMarkerInfoT multiMarkerConfig = tracker.getMultiMarkerConfig();
////                DoubleBuffer buff = multiMarkerConfig.trans().asBuffer(12);
//                DoubleBuffer buff = multiMarkerConfig.trans().asBuffer();
//                int k = 0;
//
////                for (int i = 0; i < 12; i++) {
////                    transfos[trackId][i] = (float) buff.get(k++);
////                }
//                for (int i = 0; i < 12; i++) {
//                    transfos[trackId][i] = (float) multiMarkerConfig.trans().get(k++);
//                }
//
////
//                trackId++;
//            }
//
//            // Image drawing
//            if (copy) {
//                ByteBuffer buff1 = iimg.getByteBuffer();
//                pimg.loadPixels();
//                for (int i = 0; i
//                        < iimg.width() * iimg.height(); i++) {
//                    int offset = i * 3;
//                    pimg.pixels[i] = (buff1.get(offset + 2) & 0xFF) << 16
//                            | (buff1.get(offset + 1) & 0xFF) << 8
//                            | (buff1.get(offset) & 0xFF);
//                }
//                pimg.updatePixels();
//            }
//            return transfos;
//
//        } catch (Exception e) {
//            System.out.println("Exception in findMarkers " + e);
//        }
//
//        return null;
//    }
}
