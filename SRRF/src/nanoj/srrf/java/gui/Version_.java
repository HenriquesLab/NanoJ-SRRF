package nanoj.srrf.java.gui;

import ij.IJ;
import ij.plugin.PlugIn;
import nanoj.srrf.java.Version;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 17/01/15
 * Time: 13:47
 */
public class Version_ implements PlugIn {

    @Override
    public void run(String s) {
        IJ.showMessage(Version.getVersion());
    }
}

