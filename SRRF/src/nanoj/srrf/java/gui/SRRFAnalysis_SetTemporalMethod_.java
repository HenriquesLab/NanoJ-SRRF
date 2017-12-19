package nanoj.srrf.java.gui;

import ij.gui.NonBlockingGenericDialog;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 14/12/15
 * Time: 18:14
 */
public class SRRFAnalysis_SetTemporalMethod_  extends _BaseSRRFDialog_ {

    protected SRRFAnalysis_ExtraSettings_ SRRFExtraSettings = new SRRFAnalysis_ExtraSettings_();

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
        gd.addMessage("Temporal Analysis Methods are:\n" +
                "0 - TRM\n" +
                "1 - TRA\n" +
                "2 - TRPPM\n" +
                "3 - TRAC");
        gd.addNumericField("Temporal Analysis", 0, 0);

    }

    @Override
    public boolean loadSettings() {
        int SRRFType = (int) gd.getNextNumber();

        int SRRForder = 0;
        if (SRRFType == 0) SRRForder = 0;
        else if (SRRFType == 1) SRRForder = 1;
        else if (SRRFType == 2) SRRForder = -1;

        SRRFExtraSettings.setPrefs("SRRFType", SRRFType);
        SRRFExtraSettings.setPrefs("SRRForder", SRRForder);

        prefs.changed();
        prefs.savePreferences();
        return true;
    }

    @Override
    public void execute() throws InterruptedException, IOException {

    }
}
