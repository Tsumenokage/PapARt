package fr.inria.papart.apps;

import fr.inria.papart.procam.*;
import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_core;
import org.reflections.*;
import TUIO.*;
import fr.inria.papart.apps.MyApp2D;
import fr.inria.papart.calibration.CalibrationPopup;
import fr.inria.papart.procam.camera.Camera;
import fr.inria.papart.tracking.DetectedMarker;
import fr.inria.papart.tracking.MarkerDetector;
import fr.inria.papart.tracking.MarkerSvg;
import java.util.ArrayList;
import org.bytedeco.javacpp.opencv_core.IplImage;
import processing.awt.PShapeJava2D;
import processing.core.PApplet;
import processing.core.PMatrix;
import processing.core.PMatrix2D;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.core.PShapeSVG;
import processing.core.PVector;
import processing.data.XML;
import toxi.geom.*;

import processing.video.*;

public class PaperApp2D extends PApplet {

    boolean useProjector = false;
    Papart papart;

    @Override
    public void settings() {
        size(900, 600, P3D);
//        fullScreen(P3D, 2);
    }

    public static void main(String args[]) {
        PApplet.main(new String[]{"--present", "fr.inria.papart.apps.PaperApp2D"});
    }

    @Override
    public void setup() {

        papart = Papart.seeThrough(this);
//        papart.loadTouchInput();

        PaperScreen app = new MyApp2D();
        papart.startTracking();
        
//        papart.getARDisplay().manualMode();

    }

    public void draw() {
        
        Camera camera = papart.getCameraTracking();
        
        image(camera.getImage(), 0, 0, camera.width(), camera.height());
        
        DetectedMarker[] detectedMarkers = camera.getDetectedMarkers();
        if(detectedMarkers != null){
            
            fill(255);
            stroke(0);
            strokeWeight(1);
            
            for(DetectedMarker marker : detectedMarkers){
                marker.drawSelf(g, 4);
            }
        }
    }

//    public void keyPressed(){
//        if(key == 'c'){
//           papart.calibration();
//        }
//    }
}
