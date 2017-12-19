package nanoj.srrf.java;

import ij.ImagePlus;
import ij.ImageStack;
import nanoj.core.java.tools.Log;

import static java.lang.Math.min;
import static java.lang.Math.sqrt;
import static nanoj.core.java.array.ArrayMath.getAverageValue;

/**
 * Created by Henriques-lab on 13/04/2016.
 */
public class MinimizeSRRFPatterning {

    private static Log log = new Log();

    public static double minimizeSRRFPatterning(ImagePlus imp, int magnification, boolean doDriftCorrection) {

        int w = imp.getWidth();
        int h = imp.getHeight();
        ImageStack ims = imp.getImageStack();
        int nSlices = ims.getSize();

        // create mean and variance matrix
        double[] pixelsMean = new double[magnification * magnification];
        double[] pixelsVariance = new double[magnification * magnification];
        long[] pixelsCounter = new long[magnification * magnification];

        for (int s = 1; s <= nSlices; s++) {
            float[] pixels = (float[]) ims.getPixels(s);

            for (int p = 0; p < pixels.length; p++) {
                if (pixels[p] == 0) continue;

                int x = p % w;
                int y = p / w;
                int bx = x % magnification;
                int by = y % magnification;
                int bp = by * magnification + bx;

                pixelsCounter[bp]++;

                double oldMean = pixelsMean[bp];
                double newMean = oldMean + (pixels[p] - oldMean) / pixelsCounter[bp];

                double delta = (pixels[p] - newMean) * (pixels[p] - oldMean);
                pixelsVariance[bp] += (delta - pixelsVariance[bp]) / pixelsCounter[bp];
                pixelsMean[bp] = newMean;
            }

            log.progress(s, nSlices);
        }

        // average extremes
        if (!doDriftCorrection) {
            int magMinus1 = magnification - 1;
            for (int j = 0; j < magnification / 2; j++) {
                for (int i = 0; i < magnification / 2; i++) {
                    double m;

                    int p0 = j * magnification + i;
                    int p1 = j * magnification + (magMinus1 - i);
                    int p2 = (magMinus1 - j) * magnification + i;
                    int p3 = (magMinus1 - j) * magnification + (magMinus1 - i);

                    m = (pixelsMean[p0] + pixelsMean[p1] + pixelsMean[p2] + pixelsMean[p3]) / 4;
                    pixelsMean[p0] = m;
                    pixelsMean[p1] = m;
                    pixelsMean[p2] = m;
                    pixelsMean[p3] = m;

                    m = (pixelsVariance[p0] + pixelsVariance[p1] + pixelsVariance[p2] + pixelsVariance[p3]) / 4;
                    pixelsVariance[p0] = m;
                    pixelsVariance[p1] = m;
                    pixelsVariance[p2] = m;
                    pixelsVariance[p3] = m;
                }
            }
        }

        // calculate Gain and Offset
        double pixelsGain[] = new double[magnification * magnification];
        double pixelsOffset[] = new double[magnification * magnification];

        // calculate Gain matrix
        double gainMin = Double.MAX_VALUE;
        for (int p = 0; p < pixelsVariance.length; p++) {
            pixelsGain[p] = 2 * sqrt(pixelsVariance[p]);
            gainMin = min(gainMin, pixelsGain[p]);
        }
        for (int p = 0; p < pixelsVariance.length; p++) pixelsGain[p] = 1 / (pixelsGain[p] / gainMin);

        // calculate Offset matrix
        double offsetMin = Double.MAX_VALUE;
        for (int p = 0; p < pixelsMean.length; p++) offsetMin = min(offsetMin, pixelsMean[p]);
        for (int p = 0; p < pixelsMean.length; p++) pixelsOffset[p] = -(pixelsMean[p] - offsetMin);

        // apply correction
        for (int s = 1; s <= nSlices; s++) {
            float[] pixels = (float[]) ims.getPixels(s);

            for (int p = 0; p < pixels.length; p++) {
                if (pixels[p] == 0) continue;

                int x = p % w;
                int y = p / w;
                int bx = x % magnification;
                int by = y % magnification;
                int bp = by * magnification + bx;

                if (pixels[p]!=0)
                    pixels[p] = (float) ((pixels[p]+pixelsOffset[bp])*pixelsGain[bp]);
                    //pixels[p] = (float) (pixels[p]*pixelsGain[bp]+pixelsOffset[bp]);
            }
            log.progress(s, nSlices);
        }

        return -getAverageValue(pixelsOffset);
    }
}
