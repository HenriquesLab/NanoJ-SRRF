package nanoj.srrf.java.gui;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.SaveDialog;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.array.ArrayInitialization;
import nanoj.core.java.featureExtraction.ExtractRois;
import nanoj.core.java.image.transform.NormalizedCrossCorrelationMap;
import nanoj.core.java.image.transform.TranslateOrRotateImage;
import nanoj.core.java.projections.Projections2D;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static nanoj.core.java.array.ArrayCasting.floatToDouble;
import static nanoj.core.java.array.ArrayMath.multiply;
import static nanoj.core.java.array.ArrayMath.normalize;
import static nanoj.core.java.featureExtraction.Peaks.getPeaks;
import static nanoj.core.java.featureExtraction.Peaks.populateRoiManagerWithPeaks;
import static nanoj.core.java.image.drift.EstimateShiftAndTilt.CENTER_OF_MASS;
import static nanoj.core.java.image.drift.EstimateShiftAndTilt.getShiftFromCrossCorrelationPeak;
import static nanoj.core.java.imagej.ResultsTableTools.dataMapToResultsTable;
import static nanoj.core.java.io.SaveNanoJTable.saveNanoJTable;
import static nanoj.core.java.projections.Projections2D.do2DProjection;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 02/04/15
 * Time: 16:02
 */
public class OldDriftEstimation_ extends _BaseSRRFDialog_ {

    int radius, nROIs, timeAveraging;
    boolean showDriftPlot, showDriftTable, showCrossCorrelationMap, apply;
    private String filePath;

    RoiManager rm = null;
    private static final NormalizedCrossCorrelationMap normalizedCrossCorrelationMap = new NormalizedCrossCorrelationMap();
    private static final TranslateOrRotateImage TRO = new TranslateOrRotateImage();

    @Override
    public boolean beforeSetupDialog(String arg) {
        useSettingsObserver = false;
        autoOpenImp = true;
        return true;
    }

    @Override
    public void setupDialog() {
        gd = new NonBlockingGenericDialog("Estimate Drift...");
        gd.addNumericField("Max drift (pixels)", getPrefs("maxDrift", 30), 0);
        gd.addNumericField("Number of ROIs to use", getPrefs("nROIs", 100), 0);

        gd.addNumericField("Time averaging", getPrefs("timeAveraging", 10), 0);

        gd.addCheckbox("Show Cross-Correlation Map", getPrefs("showCrossCorrelationMap", true));
        gd.addCheckbox("Show drift plot", getPrefs("showDriftPlot", true));
        gd.addCheckbox("Show drift table", getPrefs("showDriftTable", true));

        gd.addMessage("note: you can fine tune ROI Manager");

        gd.addCheckbox("Apply to current dataset", false);
        gd.addMessage("Note: always better to apply correction during SRRF analysis instead.");

        //gd.addMessage("Running mode: "+(prefs.get("NJ.kernelMode", false)?"OpenCL Safe Mode (Java Thread Pool)":"Full OpenCL Acceleration"));
    }

    @Override
    public boolean loadSettings() {

        // Grab data from dialog
        radius = (int) gd.getNextNumber();
        nROIs = (int) gd.getNextNumber();
        timeAveraging = (int) gd.getNextNumber();
        showCrossCorrelationMap = gd.getNextBoolean();
        showDriftPlot = gd.getNextBoolean();
        showDriftTable = gd.getNextBoolean();
        apply = gd.getNextBoolean();

        if (radius <3 || nROIs<1) return false;

        setPrefs("maxDrift", radius);
        setPrefs("nROIs", nROIs);
        setPrefs("timeAveraging", timeAveraging);
        setPrefs("showCrossCorrelationMap", showCrossCorrelationMap);
        setPrefs("showDriftPlot", showDriftPlot);
        setPrefs("showDriftTable", showDriftTable);

        log.msg(3, "BlockedDriftCorrection: detecting peaks");
        FloatProcessor ip = imp.getProcessor().convertToFloatProcessor();
        float[][] peaks = getPeaks(ip, nROIs, radius, 0);
        rm = ExtractRois.getRoiManager();
        populateRoiManagerWithPeaks(peaks, radius, rm);
        rm.runCommand("Associate", "false");

        if (filePath == null) {
            SaveDialog sd = new SaveDialog(
                    "Choose where to save Drift-Table...",
                    prefs.get("NJ.defaultSavePath", ""),
                    imp.getTitle(), ".njt");
            if (sd.getFileName() == null) {
                return false;
            }
            String dirPath = sd.getDirectory();
            filePath = sd.getDirectory()+sd.getFileName();
            prefs.set("NJ.defaultSavePath", dirPath);
            prefs.set("NJ.filePath", filePath);
        }

        prefs.savePreferences();

        return true;
    }

    public void execute() throws InterruptedException, IOException {

        int[] xStart, yStart, rWidth, rHeight;

        int nROIs = rm.getCount();

        if (nROIs>0) {
            xStart = new int[nROIs];
            yStart = new int[nROIs];
            rWidth = new int[nROIs];
            rHeight = new int[nROIs];

            for (int n=0; n<nROIs; n++) {
                Rectangle r = rm.getRoi(n).getBounds();
                xStart[n] = r.x;
                yStart[n] = r.y;
                rWidth[n] = r.width;
                rHeight[n] = r.height;
            }
        }

        else {
            nROIs = 1;
            xStart = new int[nROIs];
            yStart = new int[nROIs];
            rWidth = new int[nROIs];
            rHeight = new int[nROIs];

            xStart[0] = 0;
            yStart[0] = 0;
            rWidth[0] = imp.getWidth();
            rHeight[0] = imp.getHeight();
        }

        log.status("extracting ROIs to analyse drift...");
        ImageStack ims = imp.getImageStack();
        ImageStack[] imsRois = ExtractRois.extractRois(ims, xStart, yStart, rWidth, rHeight);
        ImageStack[] imsCCMap = new ImageStack[imsRois.length];

        log.status("calculating time averaging and cross-correlation map...");
        ImageStack imsAverageCCM = null;
        if (timeAveraging > 1) {
            for (int n=0; n<imsRois.length; n++) {
                log.progress(n + 1, imsRois.length);
                log.status("calculating time averaging for ROI " + (n + 1) + "/" + imsRois.length);
                imsRois[n] = do2DProjection(imsRois[n], timeAveraging, true, Projections2D.AVERAGE);
            }
        }
        for (int n=0; n<imsRois.length; n++) {
            log.progress(n+1, imsRois.length);
            log.status("cross-correlation for ROI "+(n+1)+"/"+imsRois.length);
            imsCCMap[n] = normalizedCrossCorrelationMap.calculate(
                    imsRois[n].getProcessor(1).convertToFloatProcessor(), imsRois[n], radius, radius);

            if (n==0) imsAverageCCM = imsCCMap[0].duplicate();
            else {
                for (int s=1; s<=imsAverageCCM.getSize(); s++) {
                    FloatProcessor fp = (FloatProcessor) imsCCMap[n].getProcessor(s);
                    imsAverageCCM.getProcessor(s).copyBits(fp, 0, 0, Blitter.ADD);
                }
            }
        }
        for (int s=1; s<=imsAverageCCM.getSize(); s++)
            normalize((float[]) imsAverageCCM.getProcessor(s).getPixels(), 1);

        log.status("calculating cross-correlation peaks...");
        float[][] drift = getShiftFromCrossCorrelationPeak(imsAverageCCM, CENTER_OF_MASS);
        drift[1] = multiply(drift[1], -1);
        drift[2] = multiply(drift[2], -1);
        int nPoints = imsAverageCCM.getSize();

        // Create drift table
        log.status("populating drift table...");
        Map<String, double[]> data = new LinkedHashMap<String, double[]>();
        data.put("XY-Drift (pixels)", floatToDouble(drift[0]));
        data.put("X-Drift (pixels)", floatToDouble(drift[1]));
        data.put("Y-Drift (pixels)", floatToDouble(drift[2]));

        if (showDriftTable) {
            ResultsTable rt = dataMapToResultsTable(data);
            rt.show("Drift-Table");
        }

        String NJTPath = filePath.replace(".njt", "-DriftTable.njt");
        saveNanoJTable(NJTPath, getPrefs(), data);

        // Create drift plot
        if (showDriftPlot) {
            log.status("generating plots...");

            float[] timePoints = ArrayInitialization.initializeFloatAndGrowthFill(nPoints, 1, 1);
            Plot plotDrift = new Plot("Drift", "time-points", "drift (px)", timePoints, drift[0]);
            Plot plotDriftX = new Plot("Drift-X", "time-points", "x-drift (px)", timePoints, drift[1]);
            Plot plotDriftY = new Plot("Drift-Y", "time-points", "y-drift (px)", timePoints, drift[2]);
            plotDrift.show();
            plotDriftX.show();
            plotDriftY.show();
        }

        // Show Cross-Correlation Map
        if (showCrossCorrelationMap) {
            rm = ExtractRois.getRoiManager();
            float radiusX = imsAverageCCM.getWidth()/2f;
            float radiusY = imsAverageCCM.getHeight()/2f;
            ImagePlus imp = new ImagePlus("Average CCM", imsAverageCCM);
            imp.show();

            Roi r;
            for (int s=1; s<=nPoints; s++) {
                r = new PointRoi((double) (drift[1][s-1]+radiusX), (double) (drift[2][s-1]+radiusY));
                imp.setSlice(s);
                imp.setRoi(r);
                rm.add(imp, r, s);
            }
            rm.runCommand("Associate", "true");
            rm.runCommand("Show None");
            rm.runCommand("Show All without labels");
        }

        if (apply) {
            String title = imp.getTitle();
            if (imp.getImageStack().isVirtual()) {
                log.status("duplicating stack");
                imp = imp.duplicate();
            }
            ims = imp.getImageStack();
            //ims = TRO.translate(ims, multiply(drift[1], -1), multiply(drift[2], -1));
            for (int n=1; n<=ims.getSize(); n++) {
                log.status("Translating frame "+n+"/"+ims.getSize());
                log.progress(n, ims.getSize());
                ImageProcessor ip = ims.getProcessor(n);
                ip.translate(-drift[1][n - 1], -drift[2][n - 1]);
                ims.setProcessor(ip, n);
            }
            imp = new ImagePlus(title+" - drift corrected", ims);
            imp.show();
        }
    }
}
