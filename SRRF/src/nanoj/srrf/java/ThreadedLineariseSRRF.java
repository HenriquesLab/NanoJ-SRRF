package nanoj.srrf.java;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.CurveFitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import nanoj.core.java.threading.NanoJThreadExecutor;
import nanoj.core.java.tools.Log;

import java.util.ArrayList;
import java.util.Random;

import static nanoj.core.java.array.ArrayCasting.toArray;
import static nanoj.core.java.array.ArrayInitialization.initializeRandomIndexes;

/**
 * Created by Henriques-lab on 13/04/2016.
 */
public class ThreadedLineariseSRRF {

    public final static int MAX_SAMPLING = 5000;

    NanoJThreadExecutor NTE = new NanoJThreadExecutor(false);
    private Random rand = new Random();
    private ArrayList<Double> vSRRF = new ArrayList<Double>();
    private ArrayList<Double> vRAW = new ArrayList<Double>();;
    private Log log = new Log();

    public void addBlock(ImageStack rawBlock, FloatProcessor SRRFProjection, float[][] shift, int blockBorder) {
        // calculate RAW-SRRF values correspondence
        ThreadedAnalyseBlock t = new ThreadedAnalyseBlock();
        t.setup(rawBlock, SRRFProjection, shift, blockBorder);
        NTE.execute(t);
    }

    private synchronized void addValuePair(Float vSRRF, Float vRAW) {
        float samplingSize = this.vSRRF.size();
        if (samplingSize > MAX_SAMPLING && rand.nextFloat() <= MAX_SAMPLING / samplingSize) return; // sample a bit less when we reach limit
        this.vSRRF.add(Double.valueOf(vSRRF));
        this.vRAW.add(Double.valueOf(vRAW));
    }

    public void calculateFactorsAndCorrectSRRF(ImagePlus impReconstruction) {
        NTE.finish();
        String equation = "y = a * pow(x, b) + c";

        double[] xData, yData;
        if (vSRRF.size() < MAX_SAMPLING) {
            xData = toArray(vSRRF, 0d);
            yData = toArray(vRAW, 0d);
        }
        else {
            xData = new double[MAX_SAMPLING];
            yData = new double[MAX_SAMPLING];
            int[] rIdx = initializeRandomIndexes(vSRRF.size());

            for (int i=0; i<MAX_SAMPLING; i++) {
                int r = rIdx[i];
                xData[i] = vSRRF.get(r);
                yData[i] = vRAW.get(r);
            }
        }

        CurveFitter cf = new CurveFitter(xData, yData);
        cf.setStatusAndEsc("Estimating how to balance SRRF: Iteration ", true);
        cf.doCustomFit(equation, new double[]{100, 1, 100}, false);
        double[] params = cf.getParams();
        double a = params[0];
        double b = params[1];
        double c = params[2];
        //if (false) {}
        if (b >= 1) log.msg("No SRRF linearisation needed");
        else if (b < 0.01) log.msg("SRRF linearisation factors too low... not applying");
        else {
            //log.msg("Linearising SRRF with y = " + a + "*x^" + b);
            log.msg("Linearising SRRF...");
            ImageStack ims = impReconstruction.getImageStack();
            int nSlices = ims.getSize();
            for (int s = 1; s <= nSlices; s++) {
                float[] pixels = (float[]) ims.getPixels(s);
                for (int p = 0; p < pixels.length; p++) {
                    pixels[p] = (float) (a * Math.pow(pixels[p], b));
                }
                log.progress(s, nSlices);
            }
            IJ.run(impReconstruction, "Enhance Contrast", "saturated=0.35");
            //log.msg(cf.getResultString());
            //plot(cf);
        }
    }

    private class ThreadedAnalyseBlock extends Thread {
        private ImageStack rawBlock;
        private FloatProcessor SRRFProjection;
        private float[][] shift;
        private int blockBorder;

        public void setup(ImageStack rawBlock, FloatProcessor SRRFProjection, float[][] shift, int blockBorder) {
            this.rawBlock = rawBlock;
            this.SRRFProjection = SRRFProjection;
            this.shift = shift;
            this.blockBorder = blockBorder;
        }

        public void run() {
            int wm = SRRFProjection.getWidth();
            int hm = SRRFProjection.getHeight();
            int w = rawBlock.getWidth();
            int h = rawBlock.getHeight();
            double m = wm/w;
            int nSlices = rawBlock.getSize();
            float[] shiftX = shift[0];
            float[] shiftY = shift[1];

            FloatProcessor rawProjection = new FloatProcessor(w, h);
            boolean[][] saturationMap = new boolean[w][h];
            float[] rawProjectionsPixels = (float[]) rawProjection.getPixels();
            rawProjection.setInterpolationMethod(ImageProcessor.BICUBIC);

            // calculate rawProjection
            for (int s = 1; s <= nSlices; s++) {
                ImageProcessor rawFrame = rawBlock.getProcessor(s);
                float dx = -shiftX[s-1];
                float dy = -shiftY[s-1];
                if (dx != 0 && dy != 0) {
                    rawFrame.setInterpolationMethod(ImageProcessor.BICUBIC);
                    rawFrame.translate(dx, dy);
                }
                for (int p=0; p<rawProjectionsPixels.length; p++) {
                    float v = rawFrame.getf(p);
                    rawProjectionsPixels[p] += rawFrame.getf(p) / nSlices;
                    if (v>=16000) saturationMap[p % w][p / w] = true;
                }
            }

            // match SRRF peaks with projection values
            for (int ym=blockBorder; ym < hm-blockBorder; ym++) {
                for (int xm=blockBorder; xm < wm-blockBorder; xm++) {
                    if (saturationMap[(int) (xm/m)][(int) (ym/m)] == true) continue;
                    if (!isLocalSRRFMax(xm, ym)) continue;
                    // we got a local max ... calculate the average projection
                    float vSRRF = SRRFProjection.getf(xm, ym);
                    float vRAW = (float) rawProjection.getInterpolatedPixel(xm/m, ym/m);
                    addValuePair(vSRRF, vRAW);
                }
            }
        }

        private boolean isLocalSRRFMax(int xm, int ym) {
            float v = SRRFProjection.getf(xm, ym);
            if (SRRFProjection.getf(xm-1, ym-1) >= v) return false;
            if (SRRFProjection.getf(xm-1, ym) >= v) return false;
            if (SRRFProjection.getf(xm-1, ym+1) >= v) return false;
            if (SRRFProjection.getf(xm+1, ym-1) >= v) return false;
            if (SRRFProjection.getf(xm+1, ym) >= v) return false;
            if (SRRFProjection.getf(xm+1, ym+1) >= v) return false;
            if (SRRFProjection.getf(xm, ym+1) >= v) return false;
            if (SRRFProjection.getf(xm, ym-1) >= v) return false;
            return true;
        }
    }
}
