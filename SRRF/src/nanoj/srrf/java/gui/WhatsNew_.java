package nanoj.srrf.java.gui;

import ij.Prefs;
import ij.gui.NonBlockingGenericDialog;
import ij.plugin.PlugIn;
import nanoj.srrf.java.Version;

/**
 * Created by Nils Gustafsson on 17/03/15.
 */
public class WhatsNew_ implements PlugIn {

    public Prefs prefs = new Prefs();

    public void run(String arg){

        NonBlockingGenericDialog gd_whatsNew = new NonBlockingGenericDialog("What's new...");
        gd_whatsNew.addMessage(Version.WHATS_NEW);
        gd_whatsNew.addCheckbox("Don't show again...", false);
        gd_whatsNew.showDialog();
        if (gd_whatsNew.wasCanceled()) {
            return;
        }
        boolean agreed = gd_whatsNew.getNextBoolean();
        if (agreed) setShowWhatsNew(false);
    }

    public void setShowWhatsNew(boolean show) {
        prefs.set("NJ.showWhatsNew", show);
        prefs.savePreferences();
    }
}
