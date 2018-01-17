package nanoj.srrf.java.gui;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.measure.ResultsTable;
import ij.process.FloatProcessor;
import nanoj.core.java.aparapi.CLDevicesInfo;
import nanoj.core.java.io.LoadNanoJTable;
import nanoj.core.java.io.ThreadedPartitionData;
import nanoj.srrf.java.SRRF;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.lang.Math.*;
import static nanoj.core.java.array.ArrayMath.getMaxValue;
import static nanoj.core.java.array.ArrayMath.getMinValue;
import static nanoj.core.java.imagej.ResultsTableTools.resultsTableToDataMap;
import static nanoj.core.java.tools.DontShowAgainDialog.dontShowAgainDialog;

/**
 * Created by sculley on 15/01/2018.
 */
public class SRRFParameterSweep_ extends _BaseSRRFDialog_ {

    public boolean doDriftCorrection, doRR, doTRM, doTRA, doTRPPM, doTRAC2, doTRAC4, doGW, doGS;
    boolean _doDriftCorrection;
    public int radialityMagnification, blockBorderNotConsideringDrift;
    int _frameStart, _frameEnd, _framesPerTimePoint, _blockFrames, _blockSize, _blockPerTimePoint, _nTimePoints, _blockBorderConsideringDrift, maxTemporalBlock, prefSpatialBlock;

    public float minRR, maxRR, incrementRR, ringRadius, psfWidth;

    public String driftTablePath, display;

    //sweep variables
    float[] valsRR;
    boolean[] SRRFtypes, GWs, GSs;
    int[] typeMap = new int[]{0, 1, -1, 2, 4};
    int nSRRFtypes;
    String[] SRRFStrings = new String[]{"TRM", "TRA", "TRPPM", "TRAC2", "TRAC4"};

    protected static SRRF srrf = new SRRF();
    private Map<String, double[]> driftTable = null;
    protected ImagePlus impReconstruction;

    @Override
    public boolean beforeSetupDialog(String arg) {
        autoOpenImp = false;
        useSettingsObserver = true;
        return true;
    }

    @Override
    public void setupDialog() {

        gd = new NonBlockingGenericDialog("SRRF Parameter Sweep");

        gd.addCheckbox("Do_Drift-Correction (with pre-calculated drift-table)", getPrefs("doDriftCorrection", false));
        gd.addSlider("Radiality_Magnification (default: 2, fast -- slow)", 1, 10, getPrefs("radialityMagnification", 2));

        gd.addCheckbox("Sweep ring radius", getPrefs("doRR", true));
        gd.addNumericField("Ring radius minimum", getPrefs("minRR", 0.1), 1);
        gd.addNumericField("Ring radius maximum", getPrefs("maxRR", 1), 1);
        gd.addNumericField("Ring radius increment", getPrefs("incrementRR", 0.1), 1);
        gd.addSlider("Constant ring radius (if not sweeping)", 0.1f, 3f, getPrefs("ringRadius", 0.5f));

        gd.addMessage("Select temporal analysis options to test");
        gd.addCheckbox("TRM", getPrefs("doTRM", true));
        gd.addCheckbox("TRA", getPrefs("doTRA", true));
        gd.addCheckbox("TRPPM", getPrefs("doTRPPM", true));
        gd.addCheckbox("TRAC2", getPrefs("doTRAC2", true));
        gd.addCheckbox("TRAC4", getPrefs("doTRAC4", true));

        gd.addMessage("Weighting options");
        gd.addCheckbox("Gradient weighting", getPrefs("doGW", true));
        gd.addSlider("PSF_FWHM (needed in Gradient Weighting)", 1.00f, 5.00f, getPrefs("PSF_Width", 1.35f) * 2.35);
        gd.addCheckbox("Gradient smoothing", getPrefs("doGS", false));

        gd.addMessage("Running mode: " + (prefs.get("NJ.kernelMode", false) ? "OpenCL Safe Mode (Java Thread Pool)" : "Full OpenCL Acceleration"));
    }


    @Override
    public boolean loadSettings() {

        doDriftCorrection = gd.getNextBoolean();
        radialityMagnification = (int) max(gd.getNextNumber(), 1);

        doRR = gd.getNextBoolean();
        minRR = (float) gd.getNextNumber();
        maxRR = (float) gd.getNextNumber();
        incrementRR = (float) gd.getNextNumber();
        ringRadius = (float) min(max(gd.getNextNumber(), 0.1f), 3f);

        doTRM = gd.getNextBoolean();
        doTRA = gd.getNextBoolean();
        doTRPPM = gd.getNextBoolean();
        doTRAC2 = gd.getNextBoolean();
        doTRAC4 = gd.getNextBoolean();

        doGW = gd.getNextBoolean();
        psfWidth = (float) min(max(gd.getNextNumber(), 1.0f), 5.0f) / 2.35f;
        doGS = gd.getNextBoolean();

        setPrefs("doDriftCorrection", doDriftCorrection);
        setPrefs("radialityMagnification", radialityMagnification);
        setPrefs("doRR", doRR);
        setPrefs("minRR", minRR);
        setPrefs("maxRR", maxRR);
        setPrefs("incrementRR", incrementRR);
        setPrefs("ringRadius", ringRadius);
        setPrefs("doTRM", doTRM);
        setPrefs("doTRA", doTRA);
        setPrefs("doTRPPM", doTRPPM);
        setPrefs("doTRAC2", doTRAC2);
        setPrefs("doTRAC4", doTRAC4);
        setPrefs("doGW", doGW);
        setPrefs("psfWidth", psfWidth);

        prefs.savePreferences();

        return true;
    }

    public boolean loadSettingsFromPrefs() {

        // Configure SRRF
        blockBorderNotConsideringDrift = (int) ringRadius + 3;
        if (doGW && psfWidth > ringRadius) {
            blockBorderNotConsideringDrift = (int) (floor(psfWidth) + 4);
        }
        return true;
    }

    @Override
    public void execute() throws InterruptedException {
        if (imp == null) {
            setupImp();
            if (imp == null) return;
        }
        dealWithAutoSettings();
        log.msg("settings for image \"" + imp.getTitle() + "\":\n" + getPrefsText());
        if (!prefs.continueNanoJCommand()) {
            log.abort();
            return;
        }
        runAnalysis(imp.getImageStack());
    }

    public void runAnalysis(ImageStack ims) {

        setUpSweep();

        int nSRRFRuns = nSRRFtypes * valsRR.length * GWs.length * GSs.length;
        log.msg("Imma gonna run SRRF "+nSRRFRuns+" times ;-)");
        _nTimePoints = 1;

        // Image stack to contain final reconstructions
        ImageStack imsReconstructions = new ImageStack(imp.getWidth()*radialityMagnification, imp.getHeight()*radialityMagnification);

        // Start analysis
        ImageStack imsBlock;
        FloatProcessor ipBlockRC;

        int thisSRRFIteration = 1;

        for(int nSRRF=0; nSRRF<5; nSRRF++){

            // loop through temporal methods

            if(!SRRFtypes[nSRRF]) continue;
            int _SRRForder = typeMap[nSRRF];
            String SRRFstring = SRRFStrings[nSRRF];

            for(int rr=0; rr<valsRR.length; rr++){

                // loop through ring radius

                float _ringRadius = valsRR[rr];

                String RRstring = "RR="+_ringRadius;

                for(int gw=0; gw<GWs.length; gw++){

                    // loop through gradient weightings

                    String GWstring = "";

                    boolean _doGW = GWs[gw];

                    if(_doGW){
                        GWstring = "GW";
                    }

                    for(int gs=0; gs<GSs.length; gs++){

                        // loop through gradient smoothings

                        String GSstring = "";

                        boolean _doGS = GSs[gs];
                        if(_doGS){
                            GSstring = "GS";
                        }

                        srrf.setupSRRF(radialityMagnification, _SRRForder, 8,
                                _ringRadius, psfWidth, _blockBorderConsideringDrift,
                                false, false, false, false,
                                _doGW, true, _doGS,
                                "Radiality");

                        // set up blocks (block sizes etc. populated in the DealWithAutoSettings method)
                        ThreadedPartitionData tpd = new ThreadedPartitionData(ims);
                        tpd.setupBlockSize(_blockSize, _blockSize, _blockFrames, _blockBorderConsideringDrift, _blockBorderConsideringDrift);

                        int m = radialityMagnification;
                        int wm = imp.getWidth() * m;
                        int hm = imp.getHeight() * m;

                        float averageCalculationTime = 0;

                        // grab from data partitioner
                        int nIterations = tpd.tTotalBlocks * tpd.yTotalBlocks * tpd.xTotalBlocks;
                        int i = 1;

                        log.showTimeInMessages(true);

                        for (int tp = 0; tp < _nTimePoints; tp++) {
                            // only one timepoint for parameter sweep, hardwired to _nTimePoints=1

                            FloatProcessor ipRC = new FloatProcessor(wm, hm);

                            for (int tb = 0; tb < _blockPerTimePoint; tb++) {

                                outerloop:

                                for (int yb = 0; yb < tpd.yTotalBlocks; yb++) {

                                    int yLMargin = (yb == 0) ? 0 : _blockBorderConsideringDrift * m;
                                    int yRMargin = (yb == tpd.yTotalBlocks - 1) ? 0 : _blockBorderConsideringDrift * m;

                                    for (int xb = 0; xb < tpd.xTotalBlocks; xb++) {

                                        if (!prefs.continueNanoJCommand()) {
                                            log.abort();
                                            return;
                                        }
                                        if (IJ.escapePressed()) {
                                            IJ.resetEscape();
                                            log.abort();
                                            return;
                                        }

                                        log.progress(i, nIterations);
                                        i++;

                                        // load block data
                                        String oldStatus = log.currentStatus;
                                        log.status("loading data...");
                                        try {
                                            imsBlock = tpd.getNextBlock();
                                        } catch (NullPointerException e) {
                                            break outerloop;
                                        }
                                        log.status(oldStatus);

                                        float startTime = log.getTimerValueSeconds();

                                        // load drift data
                                        float[][] shift = getShiftArrays(_frameStart + (tp * _blockPerTimePoint + tb) * _blockFrames, imsBlock.getSize());

                                        // do the analysis
                                        ipBlockRC = srrf.calculate(imsBlock, shift[0], shift[1]);

                                        // copy pixels
                                        int xLMargin = (xb == 0) ? 0 : _blockBorderConsideringDrift * m;
                                        int xRMargin = (xb == tpd.xTotalBlocks - 1) ? 0 : _blockBorderConsideringDrift * m;
                                        int interiorWidth = ipBlockRC.getWidth() - xLMargin - xRMargin;
                                        int interiorHeight = ipBlockRC.getHeight() - yLMargin - yRMargin;
                                        int xRC0 = xb * _blockSize * m;
                                        int yRC0 = yb * _blockSize * m;


                                        for (int yBI = 0; yBI < interiorHeight; yBI++) {
                                            for (int xBI = 0; xBI < interiorWidth; xBI++) {
                                                int xB = xBI + xLMargin;
                                                int yB = yBI + yLMargin;
                                                int xRC = xRC0 + xBI;
                                                int yRC = yRC0 + yBI;
                                                float pixelV = (ipBlockRC.getf(xB, yB) / _blockPerTimePoint) + ipRC.getf(xRC, yRC);
                                                ipRC.setf(xRC, yRC, pixelV);
                                            }
                                        }

                                        float calculationTime = log.getTimerValueSeconds() - startTime;
                                        averageCalculationTime += (calculationTime - averageCalculationTime) / (i - 1);
                                        float fps = _blockFrames / (averageCalculationTime * tpd.xTotalBlocks * tpd.yTotalBlocks);
                                        float ETF = (nIterations - i) * averageCalculationTime;
                                        String fpsString;
                                        if (fps > 1e3) fpsString = round(fps / 1000f) + "kFPS";
                                        else fpsString = round(fps) + "FPS";

                                        int _h = (int) (ETF / 3600);
                                        int _m = (int) (((ETF % 86400) % 3600) / 60);
                                        int _s = (int) (((ETF % 86400) % 3600) % 60);
                                        log.status("SRRF running at " + fpsString + " ETF " + String.format("%02d:%02d:%02d", _h, _m, _s));

                                    }
                                }
                            }

                            if(thisSRRFIteration==1){
                                imsReconstructions.addSlice(ipRC);
                                imsReconstructions.setSliceLabel(SRRFstring+"_"+GWstring+"_"+GSstring+"_"+RRstring, thisSRRFIteration);
                                impReconstruction = new ImagePlus(imp.getTitle() + " - SRRF", imsReconstructions);
                                impReconstruction.show();
                            }
                            else{
                                imsReconstructions.addSlice(ipRC);
                                imsReconstructions.setSliceLabel(SRRFstring+"_"+GWstring+"_"+GSstring+"_"+RRstring, thisSRRFIteration);
                                impReconstruction.setStack(imsReconstructions);
                                impReconstruction.setSlice(thisSRRFIteration);
                            }

                            thisSRRFIteration++;
                        }

                        log.msg(2, String.format("SRRF: full analysis took %gs", log.getTimerValueSeconds()));
                        log.status("Done...");
                        log.progress(1);

                    }

                }
            }

        }

        dealWithFinalDataset(impReconstruction);
    }


    private void setUpSweep(){

        // list of ring radius
        if(doRR){
            float diffRR = maxRR - minRR;
            int nRR = (int) (diffRR/incrementRR)+1;
            valsRR = new float[nRR];

            for(int n=0; n<nRR; n++){
                valsRR[n] = minRR + (n*incrementRR);
            }
        }
        else{
            valsRR = new float[1];
            valsRR[0] = ringRadius;
        }

        // list of SRRF orders
        SRRFtypes = new boolean[]{doTRM, doTRA, doTRPPM, doTRAC2, doTRAC4};
        nSRRFtypes = 0;
        for(int i=0; i<5; i++){
            if(SRRFtypes[i]) nSRRFtypes++;
        }

        // list of gradient weightings
        if(doGW) GWs = new boolean[]{false, true};
        else GWs = new boolean[]{false};

        // list of gradient smoothings
        if(doGS) GSs = new boolean[]{false, true};
        else GSs = new boolean[]{false};

    }


    protected void dealWithFinalDataset(ImagePlus impReconstruction) {
        ij.IJ.run(impReconstruction, "Enhance Contrast", "saturated=0.35");
    }

    private void dealWithAutoSettings() {
        _frameStart = 1;
        _frameEnd = imp.getStack().getSize();
        _framesPerTimePoint = _frameEnd - _frameStart + 1;
        maxTemporalBlock = 100;

        _doDriftCorrection = getDriftTableIfNeeded(false);

        _blockBorderConsideringDrift = (int) (blockBorderNotConsideringDrift + floor(getMaxShift()) + 1);


        float memAvailable = (float) (CLDevicesInfo.getGlobalMemSizeChosenDevice() * 0.75);
        if (memAvailable == 0) {
            String msg = "GPU error - Check CL device is set up correctly, running on Safe Mode for now\n" +
                    "You can later try to enable OpenCL on Plugins>NanoJ>Tools>Debug Preferences";
            log.msg(msg);
            dontShowAgainDialog("clDeviceProblem", "GPU error", msg);
            memAvailable = 750000000;
            prefs.set("NJ.kernelMode", true);
        }
        //System.out.println(memAvailable);
        boolean geometryOkay = false;
        int minBlockSize = 1 + 2 * _blockBorderConsideringDrift;
        int maxBlockSize = min(imp.getWidth(), imp.getHeight());

        if (!geometryOkay) {
            int divider = 1;
            while (_framesPerTimePoint / divider > maxTemporalBlock) divider++;
            for (int d = divider; _framesPerTimePoint / d > 0; d++) {
                int bF = _framesPerTimePoint / d;
                for (int bS = maxBlockSize; bS >= minBlockSize; bS--) {
                    if (estimateMemoryUsedInSRRF(bS, bF) < memAvailable && lastBlockHasEnoughPixels(bS, minBlockSize)) {
                        _blockSize = bS;
                        _blockFrames = bF;
                        _blockPerTimePoint = (int) floor(((float) _framesPerTimePoint) / _blockFrames);
                        geometryOkay = true;
                        break;
                    }
                }
                if (geometryOkay == true) break;
            }
        }

        if (!geometryOkay) {
            prefs.stopNanoJCommand();
            if (imp.getWidth() != imp.getHeight())
                log.msg("Data Blocking Error - suggest cropping data to a square");
            else
                log.msg("Data Blocking Error - SRRF does not seem to be compatible with the dataset size");
        }
        log.msg(2, "Time points split into " + _blockPerTimePoint + " blocks to conserve GPU memory");
        log.msg("Blocksize set to " + _blockSize + "x" + _blockSize + "x" + _blockFrames);
    }

    private long estimateMemoryUsedInSRRF(int blockSize, int blockFrames) {
        long radialityMagnificationSquared = radialityMagnification * radialityMagnification;
        long rawPixelsPerBlockFrame = blockSize + 2 * _blockBorderConsideringDrift;
        long radialityPixelsPerBlockFrame = rawPixelsPerBlockFrame * rawPixelsPerBlockFrame * radialityMagnificationSquared;

        long rawPixelsPerBlock = rawPixelsPerBlockFrame * rawPixelsPerBlockFrame * blockFrames;
        long GXYPixelsPerBlock = rawPixelsPerBlock * 2;
        long radialityPixelsPerBlock = radialityPixelsPerBlockFrame * blockFrames;
        long SRRFPixelsPerBlock = radialityPixelsPerBlockFrame;
        long shiftValues = blockFrames * 2;
        long otherVariables = SRRFPixelsPerBlock * 50 + rawPixelsPerBlockFrame * 50;

        long memEstimate = rawPixelsPerBlock +
                GXYPixelsPerBlock +
                radialityPixelsPerBlock +
                SRRFPixelsPerBlock +
                shiftValues +
                otherVariables;
        memEstimate = 32 * memEstimate;
        return memEstimate;
    }

    private boolean lastBlockHasEnoughPixels(int blockSize, int minPixels) {
        int remainder;
        remainder = imp.getWidth() % blockSize;
        if (remainder != 0 && remainder < minPixels) return false;
        remainder = imp.getHeight() % blockSize;
        if (remainder != 0 && remainder < minPixels) return false;
        return true;
    }

    private boolean getDriftTableIfNeeded(boolean useDirPathChoiceDialog) {
        int NOT_FOUND = 0;
        int FOUND_NJT = 1;
        int FOUND_XLS = 2;
        int found = NOT_FOUND;

        if (doDriftCorrection) {
            if (impPath != null && impPath.endsWith("-000.nji")) {
                File fPathNJT = new File(impPath.replace("-000.nji", "-DriftTable.njt"));
                File fPathXLS = new File(impPath.replace("-000.nji", "-DriftTable.xls"));
                if (fPathNJT.exists()) {
                    driftTablePath = fPathNJT.getPath();
                    log.msg("Drift table found in: " + driftTablePath);
                    found = FOUND_NJT;
                } else if (fPathXLS.exists()) {
                    driftTablePath = fPathXLS.getPath();
                    log.msg("Drift table found in: " + driftTablePath);
                    found = FOUND_XLS;
                }
            }
            if (driftTablePath == null) {
                if (useDirPathChoiceDialog) {
                    driftTablePath = IJ.getFilePath("Choose Drift-Table to load...");
                    if (driftTablePath == null) return false;
                    else if (driftTablePath.endsWith(".njt")) found = FOUND_NJT;
                    else if (driftTablePath.endsWith(".xls")) found = FOUND_XLS;
                    else {
                        IJ.error("Not .njt or .xls file...");
                        return false;
                    }
                } else return false;
            }
            try {
                if (found == FOUND_NJT)
                    driftTable = new LoadNanoJTable(driftTablePath).getData();
                else
                    driftTable = resultsTableToDataMap(ResultsTable.open(driftTablePath));
            } catch (IOException e) {
                IJ.error("Could not open Drift-Table...");
                e.printStackTrace();
                return false;
            }
            if (driftTable.get("X-Drift (pixels)").length != imp.getImageStack().getSize()) {
                IJ.error("Number of frames in drift-table different from number of frames in image...");
                return false;
            } else return true;
        }
        return false;
    }

    protected float getMaxShift() {
        if (!_doDriftCorrection || driftTable == null) return 0;
        double[] shiftX = driftTable.get("X-Drift (pixels)");
        double[] shiftY = driftTable.get("Y-Drift (pixels)");

        double maxShift = getMaxValue(shiftX)[1];
        maxShift = max(maxShift, getMaxValue(shiftY)[1]);
        maxShift = max(maxShift, abs(getMinValue(shiftX)[1]));
        maxShift = max(maxShift, abs(getMinValue(shiftY)[1]));
        return (float) maxShift;
    }

    protected float[][] getShiftArrays(int sStart, int nSlices) {
        float[] shiftX = new float[nSlices];
        float[] shiftY = new float[nSlices];

        if (!_doDriftCorrection)
            return new float[][]{shiftX, shiftY};

        double[] driftX = driftTable.get("X-Drift (pixels)");
        double[] driftY = driftTable.get("Y-Drift (pixels)");
        sStart--;
        for (int s = 0; s < nSlices; s++) {
            shiftX[s] = (float) -driftX[sStart + s];
            shiftY[s] = (float) -driftY[sStart + s];
        }
        return new float[][]{shiftX, shiftY};
    }

}

