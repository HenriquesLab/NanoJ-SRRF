package nanoj.srrf.java.gui;

import ij.ImagePlus;
import ij.gui.NonBlockingGenericDialog;
import nanoj.srrf.java.SRRF;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by sculley on 15/01/2018.
 */
public class SRRFParameterSweep_ extends _BaseSRRFDialog_ {

    public boolean doDriftCorrection, doRR, doTRM, doTRA, doTRPPM, doTRAC2, doTRAC4, doIW, doGW, doIntensityWeighting, radialityPositivityConstraint, doBatch, doGradSmooth, doGradWeight, doIntegrateLagTimes, doRadialitySquaring, renormalize, doTemporalSubtraction, doLinearise, doMinimizePatterning;
    boolean _doDriftCorrection, showAdvancedSettings, _showAdvancedSettings = false;
    public int radialityMagnification, SRRForder, frameStart, frameEnd, framesPerTimePoint, symmetryAxes, blockBorderNotConsideringDrift;
    int _frameStart, _frameEnd, _framesPerTimePoint, _blockFrames, _blockSize, _blockPerTimePoint, _nTimePoints, _blockBorderConsideringDrift, maxTemporalBlock, prefSpatialBlock;

    public float minRR, maxRR, ringRadius, psfWidth;

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

        gd = new NonBlockingGenericDialog("SRRF Parameter Sweep");

        gd.addCheckbox("Do_Drift-Correction (with pre-calculated drift-table)", getPrefs("doDriftCorrection", false));
        gd.addSlider("Radiality_Magnification (default: 2, fast -- slow)", 1, 10, getPrefs("radialityMagnification", 2));

        gd.addCheckbox("Sweep ring radius", getPrefs("doRR", true));
        gd.addNumericField("Ring radius minimum", getPrefs("minRadius", 0.1), 1);
        gd.addNumericField("Ring radius maximum", getPrefs("maxRadius", 1), 1);
        gd.addSlider("Constant ring radius (if not sweeping)", 0.1f, 3f, getPrefs("ringRadius", 0.5f));

        gd.addMessage("Select temporal analysis options to test");
        gd.addCheckbox("TRM", getPrefs("doTRM", true));
        gd.addCheckbox("TRA", getPrefs("doTRA", true));
        gd.addCheckbox("TRPPM", getPrefs("doTRPPM", true));
        gd.addCheckbox("TRAC2", getPrefs("doTRAC2", true));
        gd.addCheckbox("TRAC4", getPrefs("doTRAC4", true));

        gd.addMessage("Weighting options");
        gd.addCheckbox("Intensity weighting", getPrefs("doIW", true));
        gd.addCheckbox("Gradient weighting", getPrefs("doGW", true));
        gd.addSlider("PSF_FWHM (needed in Gradient Weighting)", 1.00f, 5.00f, getPrefs("PSF_Width", 1.35f) * 2.35);

        gd.addMessage("Running mode: "+(prefs.get("NJ.kernelMode", false)?"OpenCL Safe Mode (Java Thread Pool)":"Full OpenCL Acceleration"));
    }


    @Override
    public boolean loadSettings() {

        doDriftCorrection = gd.getNextBoolean();

        radialityMagnification = (int) max(gd.getNextNumber(), 1);

        doRR = gd.getNextBoolean();
        minRR = (float)  gd.getNextNumber();
        maxRR = (float)  gd.getNextNumber();
        ringRadius = (float)  min(max(gd.getNextNumber(), 0.1f), 3f);

        doTRM = gd.getNextBoolean();
        doTRA = gd.getNextBoolean();
        doTRPPM = gd.getNextBoolean();
        doTRAC2 = gd.getNextBoolean();
        doTRAC4 = gd.getNextBoolean();


        doIW = gd.getNextBoolean();
        doGW = gd.getNextBoolean();
        psfWidth = (float) min(max(gd.getNextNumber(),1.0f),5.0f) / 2.35f;

        setPrefs("doDriftCorrection", doDriftCorrection);
        setPrefs("radialityMagnification", radialityMagnification);
        setPrefs("doRR", doRR);
        setPrefs("minRR", minRR);
        setPrefs("maxRR", maxRR);
        setPrefs("ringRadius", ringRadius);
        setPrefs("doTRM", doTRM);
        setPrefs("doTRA", doTRA);
        setPrefs("doTRPPM", doTRPPM);
        setPrefs("doTRAC2", doTRAC2);
        setPrefs("doTRAC4", doTRAC4);
        setPrefs("doIW", doIW);
        setPrefs("doGW", doGW);
        setPrefs("psfWidth", psfWidth);

        prefs.savePreferences();

        return true;
    }
}
