package nanoj.srrf.java.gui;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.process.FloatProcessor;
import nanoj.core.java.threading.NanoJThreadExecutor;
import nanoj.srrf.java._BaseSRRFDialog_;

import java.awt.*;
import java.io.IOException;

import static nanoj.core.java.array.ArrayInitialization.initializeRandomIndexes;

/**
 * Created by paxcalpt on 28/03/2017.
 */
public class CalculateTemporalAutoCorrelations_ extends _BaseSRRFDialog_ {

    private boolean normalise;

    public boolean beforeSetupDialog(String arg) {
        useSettingsObserver = false;
        autoOpenImp = true;
        return true;
    }

    @Override
    public void setupDialog() {
        gd = new NonBlockingGenericDialog("Calculate temporal correlation...");
        gd.addCheckbox("Normalise", getPrefs("normalise", false));
        gd.addMessage("Note: make sure data is drift corrected first!");
        gd.addMessage("For @mrpaulreynolds, who always took the time to show us his awesome SRRF data =)...", new Font("Arial", Font.ITALIC, 8));
    }

    @Override
    public boolean loadSettings() {
        normalise = gd.getNextBoolean();

        setPrefs("normalise", normalise);
        return true;
    }

    @Override
    public void execute() throws InterruptedException, IOException {
        ImageStack ims = imp.getImageStack();
        int nSlices = ims.getSize();
        int nPixels = ims.getProcessor(1).getPixelCount();
        int w = ims.getWidth();
        int h = ims.getHeight();

        if (ims.getSize() < 2) {
            log.error("Image needs at least two frames");
            return;
        }

        int nTimeLags = ims.getSize()/2;

        log.status("Allocating memory...");
        float [][] data = new float[nPixels][nSlices];

        log.status("Converting data...");
        for (int s=1; s<=nSlices; s++) {
            log.progress(s, nSlices);
            FloatProcessor ip = ims.getProcessor(s).convertToFloatProcessor();
            float[] pixels = (float[]) ip.getPixels();

            for (int p=0; p<nPixels; p++) {
                data[p][s-1] = pixels[p];
            }
        }
        // now we have an array matrix with the temporal information for each pixels
        // lets calculate the temporal correlations...

        log.status("Calculating temporal correlations...");
        // note that I'm launching a thread per pixel, the thread will then calculate the temporal correlations for all
        // time-lags. This would be really unstable in OpenCL due to long looping

        float[][] temporalCorrelationResults = new float[nTimeLags][nPixels];
        ImageStack imsTemporalCorrelations = new ImageStack(w, h, nTimeLags);

        NanoJThreadExecutor NTE = new NanoJThreadExecutor(false);
        NTE.threadBufferSize *= 10;

        int[] indexes = initializeRandomIndexes(nPixels);
        int counter = 0;
        log.FPSString = "PPS";
        log.startTimer();
        for (int p: indexes) {
            counter++;
            //log.status("Calculating temporal correlation for pixel " + p + "/" + nPixels);
            if (counter % 1000 == 0) {
                log.progress(counter, nPixels);
                log.displayETF("Auto-correlation", counter, nPixels);
            }
            ThreadedTemporalCorrelations t = new ThreadedTemporalCorrelations(p, data[p], temporalCorrelationResults);
            NTE.execute(t);
        }
        log.progress(counter, nPixels);
        NTE.finish();

        // plotting correlations
        for (int timeLag=1; timeLag<=nTimeLags; timeLag++) {
            imsTemporalCorrelations.setProcessor(
                    new FloatProcessor(w, h, temporalCorrelationResults[timeLag-1]), timeLag);
            imsTemporalCorrelations.setSliceLabel("Time-Lag "+timeLag, timeLag);
        }
        ImagePlus impTemporalCorrelations =
                new ImagePlus(imp.getTitle()+" - Temporal Auto-Correlations", imsTemporalCorrelations);
        impTemporalCorrelations.show();
        imp.setDisplayRange(0, 0.5);
        IJ.run(impTemporalCorrelations, "Fire", "");
    }

    class ThreadedTemporalCorrelations extends Thread {
        private final int pixelId;
        public final float[] temporalData;
        public final float[][] temporalCorrelations;

        public ThreadedTemporalCorrelations(int pixelId, float[] temporalData, float[][] temporalCorrelations) {
            this.pixelId = pixelId;
            this.temporalData = temporalData;
            this.temporalCorrelations = temporalCorrelations;
        }

        @Override
        public void run() {

            int nSlices = temporalData.length;
            int nTimeLags = nSlices/2;

            // first, calculate mean, then mean subtract the data;
            double mean = 0;
            for (int t=0; t<nSlices; t++) mean += temporalData[t] / nSlices;
            for (int t=0; t<nSlices; t++) temporalData[t] -= mean;

            // now calculate the temporal correlations
            double [] counter = new double[nTimeLags];
            for (int t0 = 0; t0 < nTimeLags; t0++) {
                for (int t1 = t0 + 1; t1 < nSlices; t1++) {
                    int idTimeLag = (t1 - t0) - 1;
                    if (idTimeLag >= nTimeLags) continue;

                    float v0 = temporalData[t0];
                    float v1 = temporalData[t1];
                    double covariance = v0 * v1;

                    temporalCorrelations[idTimeLag][pixelId] +=
                            (covariance-temporalCorrelations[idTimeLag][pixelId])/(counter[idTimeLag]+1); // this is just a fancy rolling mean
                    counter[idTimeLag]++;
                }
            }
            if (normalise == true) {
                double variance = 0;
                for (int t = 0; t < nSlices; t++)
                    variance += temporalData[t] * temporalData[t] / nSlices;
                if (variance != 0) // avoid 0-division
                    for (int idTimeLag = 0; idTimeLag < nTimeLags; idTimeLag++)
                        temporalCorrelations[idTimeLag][pixelId] /= variance;
            }
        }
    }
}
