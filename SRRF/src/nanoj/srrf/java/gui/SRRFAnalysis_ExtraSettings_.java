package nanoj.srrf.java.gui;

import ij.gui.NonBlockingGenericDialog;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.io.IOException;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by paxcalpt on 19/07/15.
 */
public class SRRFAnalysis_ExtraSettings_ extends _BaseSRRFDialog_ {

    public static final String[] SRRFTypes = new String[]{
            "Temporal Radiality Maximum (TRM - activate in high-magnification)",
            "Temporal Radiality Average (TRA - default)",
            "Temporal Radiality Pairwise Product Mean (TRPPM)",
            "Temporal Radiality Auto-Correlations (TRAC)"};

    @Override
    public boolean beforeSetupDialog(String arg) {
        autoOpenImp = false;
        useSettingsObserver = false;
        return true;
    }

    @Override
    public void setupDialog() {
        gd = new NonBlockingGenericDialog("SRRF Advanced Settings...");
        gd.hideCancelButton();

        gd.addMessage("-=-= Temporal Analysis =-=-\n", headerFont);
        gd.addRadioButtonGroup("", SRRFTypes, 4, 1, getPrefs("SRRFType", SRRFTypes[1]));
        gd.addMessage("TRAC options (used when TRAC selected):");
        gd.addCheckbox("Integrate_Temporal_Correlations (default: active)", getPrefs("doIntegrateLagTimes", true));
        gd.addSlider("TRAC_Order (default: 2)", 2, 4, getPrefs("SRRForder", 2));

        gd.addMessage("-=-= Radiality =-=-\n", headerFont);
        gd.addCheckbox("Remove_Positivity_Constraint (default: disabled)", getPrefs("removeRadialityPositivityConstraint", false));
        gd.addCheckbox("Renormalize (default: disabled, activate for 2D structures)", getPrefs("renormalize", false));
        gd.addCheckbox("Do_Gradient_Smoothing (default: disabled, activate in low-density)", getPrefs("doGradSmooth", false));

        gd.addMessage("-=-= Weighting =-=-\n", headerFont);
        gd.addCheckbox("Do_Intensity_Weighting (default: active)", getPrefs("doIntensityWeighting", true));
        gd.addCheckbox("Do_Gradient_Weighting (default: disabled, activate in low-SNR, unstable)", getPrefs("doGradWeight", false));
        gd.addSlider("PSF_FWHM (needed in Gradient Weighting)", 1.00f, 5.00f, getPrefs("PSF_Width", 1.35f) * 2.35);

        gd.addMessage("-=-= Corrections =-=-\n", headerFont);
        gd.addCheckbox("Minimize_SRRF_patterning (default: active, experimental)", getPrefs("doMinimizePatterning", true));
        gd.addCheckbox("Fast_linearise_SRRF (default: disabled, experimental)", getPrefs("doLinearise", false));

        gd.addMessage("-=-= Batch-Analysis =-=-\n", headerFont);
        gd.addChoice("Save batch-analysis results as:", new String[]{".nji", ".tif"}, getPrefs("batchOutput", ".nji"));
    }

    @Override
    public boolean loadSettings() {

        String SRRFType = SRRFTypes[1];
        try {
            SRRFType = gd.getNextRadioButton();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            log.warning("Found a Fiji bug (independent of NanoJ), using "+SRRFType+" as default.");
            log.warning("Use ImageJ instead or wait for a Fiji update to fix this.");
        }
        boolean doIntegrateLagTimes = gd.getNextBoolean();
        int SRRForder = (int) min(max(gd.getNextNumber(), 2), 4);

        // change order depending on SRRF-type
        if (SRRFType == SRRFTypes[0]) SRRForder = 0;
        else if (SRRFType == SRRFTypes[1]) SRRForder = 1;
        else if (SRRFType == SRRFTypes[2]) SRRForder = -1;

        boolean removeRadialityPositivityConstraint = gd.getNextBoolean();
        boolean renormalize = gd.getNextBoolean();
        boolean doGradSmooth = gd.getNextBoolean();

        boolean doIntensityWeighting = gd.getNextBoolean();
        boolean doGradWeight = gd.getNextBoolean();
        float psfWidth = (float) min(max(gd.getNextNumber(),1.0f),5.0f) / 2.35f;

        boolean doMinimizePatterning = gd.getNextBoolean();
        boolean doLinearise = gd.getNextBoolean();

        String batchOutput = gd.getNextChoice();

        setPrefs("SRRFType", SRRFType);
        setPrefs("SRRForder", SRRForder);
        setPrefs("doIntegrateLagTimes", doIntegrateLagTimes);

        setPrefs("removeRadialityPositivityConstraint", removeRadialityPositivityConstraint);
        setPrefs("doGradSmooth", doGradSmooth);
        setPrefs("renormalize", renormalize);

        setPrefs("PSF_Width", psfWidth);
        setPrefs("doIntensityWeighting", doIntensityWeighting);
        setPrefs("doGradWeight", doGradWeight);

        setPrefs("doMinimizePatterning", doMinimizePatterning);
        setPrefs("doLinearise", doLinearise);

        setPrefs("batchOutput", batchOutput);

        prefs.changed();
        prefs.savePreferences();

        return true;
    }

    @Override
    public void execute() throws InterruptedException, IOException {

    }
}
