package nanoj.srrf.java.gui;

import ij.plugin.PlugIn;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 21/09/15
 * Time: 15:24
 */
public class Help_ implements PlugIn {

    @Override
    public void run(String s) {
        try {
            //Set your page url in this string. For eg, I m using URL for Google Search engine
            String url = "https://bitbucket.org/rhenriqueslab/nanoj-srrf/wiki/Home";
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        }
        catch (java.io.IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
