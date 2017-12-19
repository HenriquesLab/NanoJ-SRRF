package nanoj.srrf.java.gui;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.measure.ResultsTable;
import ij.process.FloatProcessor;
import nanoj.core.java.aparapi.CLDevicesInfo;
import nanoj.core.java.gui.tools.io.SaveStackAsNJI_;
import nanoj.core.java.io.LoadNanoJTable;
import nanoj.core.java.io.ThreadedPartitionData;
import nanoj.srrf.java.SRRF;
import nanoj.srrf.java.ThreadedLineariseSRRF;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.lang.Math.*;
import static nanoj.core.java.array.ArrayMath.getMaxValue;
import static nanoj.core.java.array.ArrayMath.getMinValue;
import static nanoj.core.java.imagej.ResultsTableTools.resultsTableToDataMap;
import static nanoj.core.java.io.OpenNanoJDataset.openNanoJDataset;
import static nanoj.core.java.tools.DontShowAgainDialog.dontShowAgainDialog;
import static nanoj.core.java.tools.NJ_LUT.applyLUT_NanoJ_Orange;
import static nanoj.srrf.java.MinimizeSRRFPatterning.minimizeSRRFPatterning;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 24/01/15
 * Time: 11:29
 */
public class SRRFAnalysis_ extends _BaseSRRFDialog_ {

    public boolean doDriftCorrection, doIntensityWeighting, radialityPositivityConstraint, doBatch, doGradSmooth, doGradWeight, doIntegrateLagTimes, doRadialitySquaring, renormalize, doTemporalSubtraction, doLinearise, doMinimizePatterning;
    boolean _doDriftCorrection, showAdvancedSettings, _showAdvancedSettings = false;
    public int radialityMagnification, SRRForder, frameStart, frameEnd, framesPerTimePoint, symmetryAxes, blockBorderNotConsideringDrift;
    int _frameStart, _frameEnd, _framesPerTimePoint, _blockFrames, _blockSize, _blockPerTimePoint, _nTimePoints, _blockBorderConsideringDrift, maxTemporalBlock, prefSpatialBlock;

    public float ringRadius, psfWidth;

    public String driftTablePath, batchFolderPath, saveFolderPath, display;

    protected static SRRF srrf = new SRRF();
    protected SRRFAnalysis_ExtraSettings_ SRRFExtraSettings = new SRRFAnalysis_ExtraSettings_();
    protected SRRFAnalysis_ExtraSettings_ _SRRFExtraSettings;
    private Map<String, double[]> driftTable = null;
    protected ImagePlus impReconstruction;
    private String batchOutput;

    @Override
    public boolean beforeSetupDialog(String arg) {
        autoOpenImp = false;
        useSettingsObserver = true;
        return true;
    }

    @Override
    public void setupDialog() {

        gd = new NonBlockingGenericDialog("Super-Resolution Radial Fluctuations...");

        //gd.addMessage("~~ Parameters ~~");
        gd.addSlider("Ring Radius (default: 0.5)", 0.1f, 3f, getPrefs("ringRadius", 0.5f));
        gd.addSlider("Radiality_Magnification (default: 5, fast -- slow)", 1, 10, getPrefs("radialityMagnification", 5));
        gd.addSlider("Axes in Ring (default: 6, fast -- slow)", 2, 8, getPrefs("symmetryAxes", 6));

        gd.addCheckbox("Do_Drift-Correction (with pre-calculated drift-table)", getPrefs("doDriftCorrection", false));
        gd.addCheckbox("Do_Batch-Analysis (.nji files in selected folder)", getPrefs("doBatch", false));

        gd.addMessage("-=-= Time-Lapse =-=-\n", headerFont);
        gd.addNumericField("Frames_per_time-point (0 - auto)", getPrefs("FPT", 0), 0);

        gd.addMessage("-=-= Crop Data =-=-\n", headerFont);
        gd.addNumericField("Start analysis on frame (0 - auto)", 0, 0);
        gd.addNumericField("End analysis on frame (0 - auto)", 0, 0);

        gd.addMessage("-=-= Advanced Settings =-=-\n", headerFont);
        gd.addCheckbox("Show_Advanced_Settings", false);
        gd.addNumericField("Max temporal analysis block size (default: 100)", getPrefs("maxTemporalBlock", 100), 0);
        gd.addNumericField("Preferred spatial analysis block size (0 - auto)", getPrefs("prefSpatialBlock", 0), 0);

        gd.addMessage("-=-= Preview =-=-\n", headerFont);
        if (log.useDebugChoices()) {
            gd.addRadioButtonGroup("Display_Mode", new String[]{"Gradient", "Gradient Ring Sum", "Radiality", "Intensity Interp"}, 4, 1, "Radiality");
        }
        gd.addCheckbox("Show_Preview", false);

        gd.addMessage("Running mode: "+(prefs.get("NJ.kernelMode", false)?"OpenCL Safe Mode (Java Thread Pool)":"Full OpenCL Acceleration"));

        //gd.addHelp("file:///Users/nils/Documents/Work/Website/my-website/index.html");
    }

    @Override
    public boolean loadSettings() {
        // ~~ Parameters ~~
        ringRadius = (float)  min(max(gd.getNextNumber(), 0.1f), 3f);
        radialityMagnification = (int) max(gd.getNextNumber(), 1);
        symmetryAxes = (int) min(max(gd.getNextNumber(),2),16);

        doDriftCorrection = gd.getNextBoolean();
        doBatch = gd.getNextBoolean();

        // ~~ Time-Lapse Settings
        framesPerTimePoint = (int) gd.getNextNumber();

        // ~~ Performance ~~
        frameStart = (int) gd.getNextNumber();
        frameEnd = (int) gd.getNextNumber();

        showAdvancedSettings = gd.getNextBoolean();
        if (!_showAdvancedSettings && showAdvancedSettings && _SRRFExtraSettings == null) {
            _SRRFExtraSettings = new SRRFAnalysis_ExtraSettings_();
            _SRRFExtraSettings.start();
            _showAdvancedSettings = true;
        }
        else if (_SRRFExtraSettings != null && _SRRFExtraSettings.gd != null) {
            _SRRFExtraSettings.gd.dispose();
            _SRRFExtraSettings = null;
        }
        if (showAdvancedSettings) _showAdvancedSettings = true;
        else _showAdvancedSettings = false;
        maxTemporalBlock = (int) gd.getNextNumber();
        prefSpatialBlock = (int) gd.getNextNumber();

        // ~~ Preview ~~
        if (log.useDebugChoices()) {
            display = gd.getNextRadioButton();
        }
        else display = "Radiality";
        showPreview = gd.getNextBoolean();

        // Check values or load values
        if (frameStart!=0 && frameEnd!=0 && frameEnd < frameStart) return false;

        setPrefs("ringRadius", ringRadius);
        setPrefs("radialityMagnification", radialityMagnification);
        setPrefs("doDriftCorrection", doDriftCorrection);
        setPrefs("doBatch", doBatch);
        setPrefs("FPT", framesPerTimePoint);
        setPrefs("symmetryAxes", symmetryAxes);
        //setPrefs("doGradWeight", doGradWeight);
        setPrefs("maxTemporalBlock", maxTemporalBlock);
        setPrefs("prefSpatialBlock", prefSpatialBlock);

        prefs.savePreferences();

        return true;
    }

    public boolean loadSettingsFromPrefs() {
        doTemporalSubtraction = SRRFExtraSettings.getPrefs("doTemporalSubtraction", false);
        SRRForder = SRRFExtraSettings.getPrefs("SRRForder", 2);
        doIntegrateLagTimes = SRRFExtraSettings.getPrefs("doIntegrateLagTimes", true);

        radialityPositivityConstraint = !SRRFExtraSettings.getPrefs("removeRadialityPositivityConstraint", false);
        doRadialitySquaring = true;//SRRFExtraSettings.getPrefs("doRadialitySquaring", true);
        doGradSmooth = SRRFExtraSettings.getPrefs("doGradSmooth", false);
        renormalize = SRRFExtraSettings.getPrefs("renormalize", false);

        doIntensityWeighting = SRRFExtraSettings.getPrefs("doIntensityWeighting", true);
        doGradWeight = SRRFExtraSettings.getPrefs("doGradWeight", false);
        psfWidth = SRRFExtraSettings.getPrefs("PSF_Width", 1.35f);

        if (radialityMagnification > 1 && SRRFExtraSettings.getPrefs("doLinearise", false)) doLinearise = true;
        else doLinearise = false;
        doMinimizePatterning = SRRFExtraSettings.getPrefs("doMinimizePatterning", true);

        batchOutput = SRRFExtraSettings.getPrefs("batchOutput", ".nji");

        // Configure SRRF
        blockBorderNotConsideringDrift = (int) ringRadius + 3;
        if (doGradWeight && psfWidth > ringRadius){
            blockBorderNotConsideringDrift = (int) (floor(psfWidth) + 4);
        }

        //TODO: ReThink this message maybe?
//        if (SRRForder > 1 && radialityPositivityConstraint != 0) {
//            log.warning("Temporal Correlations Order > 1 disadvised with Minimum Radiality > 0");
//            showStatus("Warning: Order > 1 disadvised with Minimum Radiality > 0");
//        }
        return true;
    }

    @Override
    public void execute() throws InterruptedException {
        if (doBatch) {
            if (imp != null) imp.close();
            batchFolderPath = IJ.getDir("Folder with .nji datasets...");
            saveFolderPath = IJ.getDir("Folder to save reconstructions...");
            _SaveReconstruction saveReconstruction = new _SaveReconstruction();

            for (File f: new File(batchFolderPath).listFiles()) {
                if (!prefs.continueNanoJCommand()) {
                    log.abort();
                    return;
                }

                if (f.getName().endsWith("000.nji") && !f.getName().contains("-SRRF")) {
                    // do analysis
                    impPath = f.getPath();
                    imp = openNanoJDataset(impPath);
                    imp.show();
                    dealWithAutoSettings(false);
                    log.msg("Starting analysis of: "+f.getPath());
                    runAnalysis(imp.getImageStack());

                    // save data
                    String fSave = saveFolderPath + f.getName();
                    savePrefsText(fSave.replace("-000.nji", "-SRRFSettings.txt"));
                    log.msg("Waiting for previous to finish saving...");
                    saveReconstruction.join();
                    log.msg("Saving dataset: "+fSave);
                    saveReconstruction = new _SaveReconstruction();
                    saveReconstruction.setup(impReconstruction.getImageStack(), fSave.replace("-000.nji", "-SRRF"));
                    saveReconstruction.start();

                    // clear data
                    imp.close();
                    imp = null;
                    impReconstruction.close();
                    impReconstruction = null;
                }
            }
            saveReconstruction.join();
        }

        else {
            if (imp == null){
                setupImp();
                if(imp == null) return;
            }
            dealWithAutoSettings(false);
            log.msg("settings for image \""+imp.getTitle()+"\":\n" + getPrefsText());
            if (!prefs.continueNanoJCommand()) {
                log.abort();
                return;
            }
            runAnalysis(imp.getImageStack());
        }
    }

    public void doPreview() {

        if (imp == null) {
            setupImp();
            if (imp == null) return;
        }

        log.status("calculating preview...");

        ImageStack ims = imp.getImageStack();
        float[] shiftX, shiftY;

        ImageStack imsBlock = new ImageStack(imp.getWidth(), imp.getHeight());
        imsBlock.addSlice(imp.getProcessor());

        shiftX = new float[]{0};
        shiftY = new float[]{0};

        dealWithAutoSettings(true);

        srrf.setupSRRF(radialityMagnification, 1, symmetryAxes,
                ringRadius, psfWidth, blockBorderNotConsideringDrift,
                doRadialitySquaring, renormalize, doIntegrateLagTimes,
                radialityPositivityConstraint,
                doGradWeight, doIntensityWeighting, doGradSmooth,
                display);

        if(SRRForder != 1) log.msg("Preview disables Temporal-Correlations by default...");

        FloatProcessor ipRendering = srrf.calculate(imsBlock, shiftX, shiftY);

        if (impPreview != null) impPreview.setProcessor(ipRendering);
        else impPreview = new ImagePlus("Preview...", ipRendering);
        impPreview.show();

    }

    public void runAnalysis(ImageStack ims) {

        // Start analysis
        ImageStack imsBlock;
        FloatProcessor ipBlockRC;

        // Shave frames at start and end
        int nFrames = ims.getSize();
        for (int n = 0; n < (nFrames - _frameEnd); n++) ims.deleteSlice(ims.getSize());
        if (ims.getSize() > _frameStart) for (int n = 0; n < (_frameStart - 1); n++) ims.deleteSlice(1);
        //nFrames = ims.getSize();
        //log.msg("nFrames="+nFrames);

        srrf.setupSRRF(radialityMagnification, SRRForder, symmetryAxes,
                ringRadius, psfWidth, _blockBorderConsideringDrift,
                doRadialitySquaring, renormalize, doIntegrateLagTimes,
                radialityPositivityConstraint,
                doGradWeight, doIntensityWeighting, doGradSmooth,
                display);

        ThreadedPartitionData tpd = new ThreadedPartitionData(ims);
        ThreadedLineariseSRRF lineariseSRRF = new ThreadedLineariseSRRF();
        tpd.setupBlockSize(_blockSize, _blockSize, _blockFrames, _blockBorderConsideringDrift, _blockBorderConsideringDrift);

        int m = radialityMagnification;
        int wm = imp.getWidth() * m;
        int hm = imp.getHeight() * m;

        float averageCalculationTime = 0;
        int nIterations = tpd.tTotalBlocks * tpd.yTotalBlocks * tpd.xTotalBlocks;
        int i = 1;

        log.showTimeInMessages(true);

        for (int tp = 0; tp < _nTimePoints; tp++) {

            FloatProcessor ipRC = new FloatProcessor(wm, hm);

            for (int tb = 0; tb < _blockPerTimePoint; tb++) {

                log.msg(8, "analysing time-block=" + (tp * tb + tb + 1) + "/" + tpd.tTotalBlocks);
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
                        }
                        catch (NullPointerException e) {
                            break outerloop;
                        }
                        log.status(oldStatus);

                        float startTime = log.getTimerValueSeconds();

                        // load drift data
                        float[][] shift = getShiftArrays(_frameStart + (tp * _blockPerTimePoint + tb) * _blockFrames, imsBlock.getSize());
                        //log.msg("tp "+(_frameStart + (tp * _blockPerTimePoint + tb) * _blockFrames)+" "+imsBlock.getSize());

                        // do the analysis
                        ipBlockRC = srrf.calculate(imsBlock, shift[0], shift[1]);

                        // add pixels intensities to balance register
                        if (doLinearise) lineariseSRRF.addBlock(imsBlock, ipBlockRC, shift, _blockBorderConsideringDrift);

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
                        averageCalculationTime += (calculationTime - averageCalculationTime) / (i-1);
                        float fps = _blockFrames / (averageCalculationTime * tpd.xTotalBlocks * tpd.yTotalBlocks);
                        float ETF = (nIterations - i) * averageCalculationTime;
                        String fpsString;
                        if (fps > 1e3) fpsString = round(fps / 1000f) + "kFPS";
                        else fpsString = round(fps) + "FPS";

                        int _h = (int) (ETF / 3600);
                        int _m = (int) (((ETF % 86400) % 3600) / 60);
                        int _s = (int) (((ETF % 86400) % 3600) % 60);
                        log.status("SRRF running at " + fpsString + " ETF " + String.format("%02d:%02d:%02d", _h, _m, _s));
                        log.msg(8, "SRRF running at " + fpsString + " ETF " + String.format("%02d:%02d:%02d", _h, _m, _s));
                    }
                }
            }
            dealWithTimePointFrame(tp, ipRC);
        }

        if (doLinearise) lineariseSRRF.calculateFactorsAndCorrectSRRF(impReconstruction);
        if (doMinimizePatterning) {
            //ImageStack imsEvolution = new ImageStack(impReconstruction.getWidth(), impReconstruction.getHeight());
            //imsEvolution.addSlice(impReconstruction.getProcessor().duplicate());
            double oldMean = Double.MAX_VALUE;
            for (int iter=0; iter<10; iter++) {
                log.status("Minimizing patterning... interation="+iter+"/10");

                double newMean = minimizeSRRFPatterning(impReconstruction, radialityMagnification, doDriftCorrection);
                if (newMean > oldMean || newMean < 1E-6) break;
                oldMean = newMean;
                //imsEvolution.addSlice(impReconstruction.getProcessor().duplicate());
            }
            //new ImagePlus("Pattern evolution", imsEvolution).show();
        }

        dealWithFinalDataset(impReconstruction);

        log.msg(2, String.format("SRRF: full analysis took %gs", log.getTimerValueSeconds()));
        log.status("Done...");
        log.progress(1);
    }

    protected void dealWithTimePointFrame(int timePoint, FloatProcessor ip) {
        ImageStack imsReconstruction;

        if (timePoint == 0) {
            imsReconstruction = new ImageStack(ip.getWidth(), ip.getHeight());
            imsReconstruction.addSlice(ip);
            impReconstruction = new ImagePlus(imp.getTitle() + " - SRRF", imsReconstruction);
            impReconstruction.show();
            ij.IJ.run(impReconstruction, "Enhance Contrast", "saturated=0.35");
            applyLUT_NanoJ_Orange(impReconstruction);
        }
        else {
            imsReconstruction = impReconstruction.getImageStack();
            imsReconstruction.addSlice(ip);
            impReconstruction.setStack(imsReconstruction);
            if (impReconstruction.getSlice() >= impReconstruction.getNSlices()-1)
                impReconstruction.setSlice(impReconstruction.getNSlices());
        }
    }

    protected void dealWithFinalDataset(ImagePlus impReconstruction) {
        ij.IJ.run(impReconstruction, "Enhance Contrast", "saturated=0.35");
    }

    private void dealWithAutoSettings(boolean doingPreview) {
        if (frameStart == 0) _frameStart = 1;
        else _frameStart = frameStart;

        if (frameEnd == 0) _frameEnd = imp.getStack().getSize();
        else _frameEnd = frameEnd;

        if (framesPerTimePoint == 0 || framesPerTimePoint > imp.getImageStack().getSize())
            _framesPerTimePoint = _frameEnd - _frameStart + 1;
        else _framesPerTimePoint = framesPerTimePoint;

        if (framesPerTimePoint == 1) SRRForder = 1;

        if (SRRForder == -1) radialityPositivityConstraint = true;

        _nTimePoints = (_frameEnd - _frameStart + 1) / _framesPerTimePoint;

        if (!doingPreview)_doDriftCorrection = getDriftTableIfNeeded(!doBatch);
        _blockBorderConsideringDrift = (int) (blockBorderNotConsideringDrift + floor(getMaxShift()) + 1);

//        if (maxShift != 0)
//
//        else
//            _blockBorderConsideringDrift = blockBorderNotConsideringDrift;
        //_blockBorderConsideringDrift += 1;
        //log.msg("maxShift="+getMaxShift());

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

        if (prefSpatialBlock != 0) {
            int bF = maxTemporalBlock;
            int bS = min(prefSpatialBlock, maxBlockSize);
            if (!(estimateMemoryUsedInSRRF(bS, bF) < memAvailable)) {
                log.msg("Could not use preferred spatial block size - block too big for CPU/GPU memory");
            }
            else if (!lastBlockHasEnoughPixels(bS, minBlockSize)) {
                log.msg("Could not use preferred spatial block size - not enough pixels in last block");
            }
            else {
                _blockSize = bS;
                _blockFrames = bF;
                _blockPerTimePoint = (int) floor(((float) _framesPerTimePoint) / _blockFrames);
                geometryOkay = true;
            }
        }

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
            if (imp.getWidth()!=imp.getHeight())
                log.msg("Data Blocking Error - suggest cropping data to a square");
            else
                log.msg("Data Blocking Error - SRRF does not seem to be compatible with the dataset size");
        }
        log.msg(2,"Time points split into "+_blockPerTimePoint+" blocks to conserve GPU memory");
        log.msg("Blocksize set to "+_blockSize+"x"+_blockSize+"x"+_blockFrames);
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

        long memEstimate =  rawPixelsPerBlock +
                GXYPixelsPerBlock +
                radialityPixelsPerBlock +
                SRRFPixelsPerBlock +
                shiftValues +
                otherVariables;
        memEstimate = 32 * memEstimate;
        //System.out.println(memEstimate);
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
                    log.msg("Drift table found in: "+driftTablePath);
                    found = FOUND_NJT;
                }
                else if (fPathXLS.exists()){
                    driftTablePath = fPathXLS.getPath();
                    log.msg("Drift table found in: "+driftTablePath);
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
                }
                else return false;
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
            }
            else return true;
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

    class _SaveReconstruction extends Thread {
        private ImageStack imsRendering;
        private String savePath;

        public void setup(ImageStack imsRendering, String savePath) {
            this.imsRendering = imsRendering.duplicate();
            this.savePath = savePath;
        }

        public void run() {
            if (batchOutput == ".nji") {
                SaveStackAsNJI_ sNJI = new SaveStackAsNJI_();
                sNJI.filePath = savePath+".nji";
                sNJI.imp = new ImagePlus("", imsRendering);
                sNJI.run();
            }
            else {
                IJ.saveAsTiff(new ImagePlus("", imsRendering), savePath+".tif");
            }
        }
    }
}
