boolean isCalibrated = false;

PMatrix3D  cameraPaperCalibration = null;
PMatrix3D  projectorPaperCalibration = null;
PMatrix3D  kinectPaperCalibration = null;

boolean isCamPaperSet = false;
boolean isProjPaperSet = false;
boolean isKinectPaperSet = false;

boolean useDefautExtrinsics = false;

ArrayList<PMatrix3D> proCamCalibrations = new ArrayList<PMatrix3D>();
int calibrationNumber = 0;


public void useExtrinsicsFromProjector(){
    useDefautExtrinsics = true;
    controlFrame.showCalibrateProCam();
}


public void saveCameraPaper(){

    assert(Mode.is("CamMarker") || Mode.is("CamManual"));

    if(Mode.is("CamMarker")){
        cameraPaperCalibration = currentCamBoard().get();
    }

    if(Mode.is("CamManual")){
        cameraPaperCalibration = objectProjectorTransfo.get();
    }

    controlFrame.setCameraPaperLabel("Camera - Paper OK");
    isCamPaperSet = true;

    checkIfCalibrationPossible();
    noMode();
}

public void saveProjectorPaper(){
    assert(Mode.is("ProjMarker") || Mode.is("ProjManual"));

    if(Mode.is("ProjMarker")){
        projectorPaperCalibration = currentProjBoard().get();
    }

    if(Mode.is("ProjManual")){
        projectorPaperCalibration = objectProjectorTransfo.get();
    }

    isProjPaperSet = true;
    controlFrame.setProjectorPaperLabel("Projection - Paper OK");
    checkIfCalibrationPossible();

    noMode();
}

public void saveKinectPaper(){
    assert(Mode.is("KinectMarker") || Mode.is("KinectManual"));

    if(Mode.is("KinectMarker")){
        kinectPaperCalibration = currentKinect360Board().get();
    }

    if(Mode.is("KinectManual")){
        kinectPaperCalibration = objectProjectorTransfo.get();
    }

    isKinectPaperSet = true;
    controlFrame.setKinectPaperLabel("Kinect - Paper OK");
    checkIfCalibrationPossible();

    noMode();
}

void checkIfCalibrationPossible(){
    if(isProjPaperSet && isCamPaperSet){
        controlFrame.showAddProCamCalibration();

        checkIfKinectCalibrationPossible();
    }
}

void checkIfKinectCalibrationPossible(){
    if(isKinectPaperSet || isKinectOne){
        controlFrame.showCalibrateKinectCam();
    }

}



PMatrix3D camBoard(){
    if(isCamPaperSet){
        return cameraPaperCalibration;
    }
    return currentCamBoard();
}


PMatrix3D kinect360Board(){
    assert(isKinect360Activated);
    if(isKinectPaperSet){
        return kinectPaperCalibration;
    }
    return currentKinect360Board();
}

PMatrix3D projBoard(){
    if(isProjPaperSet){
        return projectorPaperCalibration;
    }
    return currentProjBoard();
}


public void calibrateKinectCam(){
    if(isKinectOne){
        calibrateKinectOne();
    }
    if(isKinect360){
        calibrateKinect360();
    }

    controlFrame.hideCalibrateKinectCam();
}



public void addProCamCalibrationData(){

    PMatrix3D camPaper = camBoard();
    PMatrix3D projPaper = projBoard().get();
    projPaper.invert();
    projPaper.preApply(camPaper);
    projPaper.invert();
    proCamCalibrations.add (projPaper);

    controlFrame.showCalibrateProCam();
    controlFrame.hideAddProCamCalibration();
    calibrationNumber = calibrationNumber + 1;
    isProjPaperSet = false;
    isCamPaperSet = false;
    controlFrame.setProjectorPaperLabel("Please set the calibration.");
    controlFrame.setCameraPaperLabel("Please set the calibration.");
}

public void calibrateProCam(){

    if(useDefautExtrinsics){
        calibrateWithDefaultFile();
    } else {
        computeManualCalibrations();
    }

    // something need to change, to recalibrate.
    controlFrame.hideCalibrateProCam();

    // Set to projection mode to test.
    noMode();
    projMode(2);
}

private void calibrateWithDefaultFile(){
    try{
        ProjectiveDeviceP projectiveDeviceP = ProjectiveDeviceP.loadProjectorDevice(
            this, Papart.projectorCalib);

        if(!projectiveDeviceP.hasExtrinsics()){
            println("The projector calibration does not have extrinsic calibration: "  + Papart.projectorCalib);
            return;
        }
        println("Calibrating with " + Papart.projectorCalib);
        PMatrix3D projPaper = projectiveDeviceP.getExtrinsics();

        projPaper.print();

        papart.saveCalibration(Papart.cameraProjExtrinsics, projPaper);

    } catch(Exception e){
        println("Could not use the projector calibration: "  + Papart.projectorCalib);
    }

}

private void computeManualCalibrations(){
    PMatrix3D sum = new PMatrix3D(0, 0, 0, 0,
                                  0, 0, 0, 0,
                                  0, 0, 0, 0,
                                  0, 0, 0, 0);

    for(PMatrix3D calib : proCamCalibrations){
        addMatrices(sum, calib);
    }
    multMatrix(sum, 1f / (float) proCamCalibrations.size());
    papart.saveCalibration(Papart.cameraProjExtrinsics, sum);
    projector.setExtrinsics(sum);
}

public void clearCalibrations(){
    proCamCalibrations.clear();
    calibrationNumber = 0;
}

private void calibrateKinectOne(){
    PMatrix3D kinectExtr = kinectDevice.getStereoCalibration().get();
    kinectExtr.invert();

    PMatrix3D boardViewFromDepth = camBoard();

    // camBoard().print();
    // boardViewFromDepth.print();

    planeCalibCam = PlaneCalibration.CreatePlaneCalibrationFrom(boardViewFromDepth,
                                                                new PVector(297, 210));
    planeCalibCam.flipNormal();

    kinectCameraExtrinsics.set(kinectExtr);
    // kinectCameraExtrinsics.reset();

    boolean inter = computeScreenPaperIntersection(planeCalibCam);

    if(!inter){
        println("No intersection");
        return;
    }

    // move the plane up a little.
    planeCalibCam.moveAlongNormal(-7f);

    saveKinectCalibration(planeCalibCam);
}


private void calibrateKinect360(){
    PVector paperSize = new PVector(297, 210);

    PlaneCalibration planeCalibKinect =
        PlaneCalibration.CreatePlaneCalibrationFrom(kinect360Board().get(), paperSize);

    planeCalibCam = PlaneCalibration.CreatePlaneCalibrationFrom(camBoard().get(), paperSize);
    planeCalibCam.flipNormal();

    PMatrix3D kinectExtr = kinectDevice.getStereoCalibration().get();
    kinectExtr.invert();

    kinectCameraExtrinsics = camBoard().get();  // cam -> board
    kinectCameraExtrinsics.invert();  // board -> cam
    kinectCameraExtrinsics.preApply(kinect360Board().get()); // kinect -> board -> board -> cam

    kinectCameraExtrinsics.preApply(kinectExtr);

    // println("Kinect - Camera extrinsics : ");
    // kinectCameraExtrinsics.print();

    boolean inter = computeScreenPaperIntersection(planeCalibCam);
    if(!inter){
        println("No intersection");
        kinect360Board().print();
        return;
    }

    // move the plane up a little.
    planeCalibKinect.flipNormal();
    planeCalibKinect.moveAlongNormal(-17f);

    saveKinectCalibration(planeCalibKinect);
}


void saveKinectCalibration(PlaneCalibration planeCalib){
    planeProjCalib.setPlane(planeCalib);
    planeProjCalib.setHomography(homographyCalibration);

    planeProjCalib.saveTo(this, Papart.planeAndProjectionCalib);
    HomographyCalibration.saveMatTo(this,
                                    kinectCameraExtrinsics,
                                    Papart.kinectTrackingCalib);

    papart.setTableLocation(camBoard());
    println("Calibration OK");
    isCalibrated = true;
}
