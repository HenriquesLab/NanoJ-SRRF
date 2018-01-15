package nanoj.srrf.java.gui;

import ij.ImagePlus;
import ij.gui.NonBlockingGenericDialog;
import nanoj.srrf.java.SRRF;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.util.Map;

/**
 * Created by sculley on 15/01/2018.
 */
public class SRRFParameterSweep_ extends _BaseSRRFDialog_ {

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

        gd = new NonBlockingGenericDialog("SRRF Parameter Sweep");

        gd.addCheckbox("Do_Drift-Correction (with pre-calculated drift-table)", getPrefs("doDriftCorrection", false));
        gd.addSlider("Radiality_Magnification (default: 2, fast -- slow)", 1, 10, getPrefs("radialityMagnification", 2));

        gd.addCheckbox("Sweep ring radius" , true);
        gd.addNumericField("Ring radius minimum", getPrefs("minRadius", 0.1), 1);
        gd.addNumericField("Ring radius maximum", getPrefs("maxRadius", 1), 1);

        

        //gd.addMessage("~~ Parameters ~~");
        gd.addSlider("Ring Radius (default: 0.5)", 0.1f, 3f, getPrefs("ringRadius", 0.5f));

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
}
