package nanoj.srrf.java;

import ij.ImageStack;
import ij.process.FloatProcessor;
import nanoj.core.java.aparapi.NJKernel;
import nanoj.core.java.tools.Log;

import static nanoj.core.java.array.ArrayMath.getSumValue;
import static nanoj.core.java.array.ImageStackToFromArray.ImageStackToFloatArray;

/**
 * Created with IntelliJ IDEA.
 * User: Nils Gustafsson
 * Date: 07/07/2015
 * Time: 18:00
 */
public class SRRF {

    public static Kernel_SRRF kernel = new Kernel_SRRF();

    private static Log log = new Log();

    public String getExecutionMode() {
        return kernel.getExecutionMode().toString();
    }

    ///////////////////
    // Setup methods //
    ///////////////////

    public void setupSRRF(int magnification, int order, int symmetryAxis,
                          float spatialRadius, float psfWidth, int border,
                          boolean doRadialitySquaring, boolean renormalize, boolean doIntegrateLagTimes,
                          boolean radialityPositivityConstraint,
                          boolean doGradWeight, boolean doIntensityWeighting, boolean doGradSmoothing,
                          String display){

        kernel.magnification = magnification;
        kernel.SRRForder = order;
        kernel.nRingCoordinates = symmetryAxis * 2;
        kernel.spatialRadius = (spatialRadius * kernel.magnification);
        kernel.gradRadius = psfWidth * kernel.magnification;

        kernel.border = border;


        kernel.doRadialitySquaring = doRadialitySquaring? 1: 0;
        kernel.renormalize = renormalize? 1: 0;
        kernel.doIntegrateLagTimes = doIntegrateLagTimes? 1: 0;
        kernel.radialityPositivityConstraint = radialityPositivityConstraint? 1: 0;
        kernel.doGradWeight = doGradWeight? 1: 0;
        kernel.doIntensityWeighting = doIntensityWeighting? 1: 0;

        if(doGradSmoothing){
            kernel.ParallelGRadius = 2;
            kernel.PerpendicularGRadius = 1;
            kernel.doGradSmooth = 1;
        }else{
            kernel.ParallelGRadius = 1;
            kernel.PerpendicularGRadius = 0;
            kernel.doGradSmooth = 0;
        }

        if (display.equals("Gradient")){
            kernel.display = 1;
        } else if (display.equals("Gradient Ring Sum")) {
            kernel.display = 2;
        } else if (display.equals("Intensity Interp")) {
            kernel.display = 3;
        } else {
            kernel.display = 0;
        }

        kernel.setupComplete = true;
    }

    ///////////////////////
    // Calculate methods //
    //////////////////////
    public float[] calculate(float[] pixels, int width, int height,
                             float[] shiftX, float[] shiftY) {
        return kernel.calculate(pixels, width, height, shiftX, shiftY);
    }

    public FloatProcessor calculate(ImageStack ims) {
        int nSlices = ims.getSize();
        return calculate(ims, new float[nSlices], new float[nSlices]);
    }

    public FloatProcessor calculate(ImageStack ims, float[] shiftX, float[] shiftY) {

        float[] pixels = ImageStackToFloatArray(ims);
        int width = ims.getWidth();
        int height = ims.getHeight();
        float[] rcArray = calculate(pixels, width, height, shiftX, shiftY);
        int magnification = kernel.magnification;

        return new FloatProcessor(width * magnification, height * magnification, rcArray);
    }
}

class Kernel_SRRF extends NJKernel {

    private Log log = new Log();

    public static final int STEP_CALCULATE_GXGY = 0;
    public static final int STEP_CALCULATE_RADIALITY = 1;
    public static final int STEP_CALCULATE_SRRF = 2;

    protected float[] pixels, GxArray, GyArray, shiftX, shiftY;
    private float[] radArray, SRRFArray;
    protected float spatialRadius;
    protected int magnification;
    protected float gradRadius;
    protected int SRRForder;

    private float[] xRingCoordinates0, yRingCoordinates0;
    private float[] xRingCoordinates1, yRingCoordinates1;
    private float PI = (float) Math.PI;
    protected int ParallelGRadius = 1;
    protected int PerpendicularGRadius = 0;
    protected int nRingCoordinates;

    private int width, height, widthHeight, widthHeightTime;
    private int widthM, heightM, widthHeightM, widthHeightMTime;
    private int widthMBorderless, heightMBorderless, widthHeightMBorderless, widthHeightMTimeBorderless;
    private int nTimePoints;
    protected int border = 0, borderM = 0;

    private int stepFlag;
    protected int doIntensityWeighting = 0;
    protected int doIntegrateLagTimes = 1;
    protected int doRadialitySquaring = 1;
    protected int doGradWeight = 0;
    protected int doGradSmooth = 0;
    protected int doTemporalSubtraction = 0;
    protected int display = 0;
    protected int renormalize = 0;
    protected int radialityPositivityConstraint = 0;

    protected boolean setupComplete = false;

    public float[] calculate(float[] pixels, int width, int height,
                             float[] shiftX, float[] shiftY) {

        //setExecutionMode(EXECUTION_MODE.JTP);

        assert (setupComplete);

        // Input image properties
        this.width = width;
        this.height = height;
        this.widthHeight = width * height;
        this.nTimePoints = pixels.length / widthHeight;
        this.widthHeightTime = widthHeight * nTimePoints;

        // Radiality Image Properties
        this.widthM = width * magnification;
        this.heightM = height * magnification;
        this.widthHeightM = widthM * heightM;
        this.widthHeightMTime = widthHeightM * nTimePoints;
        this.borderM = border * magnification;
        this.widthMBorderless = widthM - borderM * 2;
        this.heightMBorderless = heightM - borderM * 2;
        this.widthHeightMBorderless = widthMBorderless * heightMBorderless;
        this.widthHeightMTimeBorderless = widthHeightMBorderless * nTimePoints;

        // Initialise Arrays
        this.pixels = pixels;
        this.GxArray = new float[widthHeightTime];
        this.GyArray = new float[widthHeightTime];
        this.radArray = new float[widthHeightMTime];
        this.SRRFArray = new float[widthHeightM];
        this.shiftX = shiftX;
        this.shiftY = shiftY;

        // Upload arrays
        setExplicit(true);
        autoChooseDeviceForNanoJ();

        //System.out.println("About to upload at magnification "+magnification);
        //System.out.println(Arrays.toString(shiftX));
        //System.out.println(Arrays.toString(shiftY));

        buildRing();
        put(this.xRingCoordinates0);
        put(this.yRingCoordinates0);
        put(this.xRingCoordinates1);
        put(this.yRingCoordinates1);
        put(this.pixels);
        put(this.GxArray);
        put(this.GyArray);
        put(this.radArray);
        put(this.SRRFArray);
        put(this.shiftX);
        put(this.shiftY);

        //System.out.println("upload Success");

        log.startTimer();

        log.msg(3, "Kernel_SRRF: calculating SRRF");

        // Step 1 - calculate Gx and Gy
        stepFlag = STEP_CALCULATE_GXGY;
        execute(widthHeightTime);

        //System.out.println("Gradient Calc success");

        // Step 2 - calculate Radiality
        stepFlag = STEP_CALCULATE_RADIALITY;
        execute(widthHeightMBorderless);

        //System.out.println("Radiality Calc success");

        // Step 3 - calculate temporal correlations
        stepFlag = STEP_CALCULATE_SRRF;
        execute(widthHeightMBorderless);

        //System.out.println("SRRF Calc success");

        get(SRRFArray);

        //System.out.println("Get Array success");
        //this bit solves a really strange bug where sometimes aparapi returns an empty array, we try to see if we have
        //a zero-filled array and if so we force a re-run on JTP
        if (getExecutionMode()!=EXECUTION_MODE.JTP) {
            if(getSumValue(SRRFArray)==0) {
                runOnceAsJTP();
                return calculate(pixels, width, height, shiftX, shiftY);
            }
        }

        log.msg(3, "Kernel_SRRF: done");
        return SRRFArray;
    }

    ////////////////////
    // NON-CL METHODS //
    ////////////////////

    public void buildRing() {
        xRingCoordinates0 = new float[nRingCoordinates];
        yRingCoordinates0 = new float[nRingCoordinates];
        xRingCoordinates1 = new float[nRingCoordinates];
        yRingCoordinates1 = new float[nRingCoordinates];
        float angleStep = (PI * 2f) / (float) nRingCoordinates;
        for(int angleIter = 0; angleIter < nRingCoordinates; angleIter++){
            xRingCoordinates0[angleIter] = spatialRadius * cos(angleStep * angleIter);
            yRingCoordinates0[angleIter] = spatialRadius * sin(angleStep * angleIter);
            xRingCoordinates1[angleIter] = gradRadius * cos(angleStep * angleIter);
            yRingCoordinates1[angleIter] = gradRadius * sin(angleStep * angleIter);
        }
    }

    ////////////////
    // CL METHODS //
    ////////////////

    @Override
    public void run() {
        if (stepFlag == STEP_CALCULATE_GXGY) calculateGxGy();
        else if (stepFlag == STEP_CALCULATE_RADIALITY) calculateRadiality();
        else if (stepFlag == STEP_CALCULATE_SRRF) calculateSRRF();
    }

    private void calculateGxGy() {
        int pixelIdx = getGlobalId();
        int x = pixelIdx % width;
        int y = (pixelIdx / width) % height;
        int t = pixelIdx / widthHeight;

        // Calculate Gx GY
        _calculateGxGy(x, y, t);
    }

    private void calculateRadiality() {

        // Position variables in Radiality magnified space
        int idx = getGlobalId();
        int X = idx % widthMBorderless + borderM;
        int Y = (idx / widthMBorderless) % heightMBorderless + borderM;
        //int t = idx / widthHeightMBorderless;

        float x0, x1, y0, y1, Gx, Gx1, Gy, Gy1;

        for(int t = 0; t < nTimePoints; t++) {
            float Xc = X + 0.5f + shiftX[t] * magnification;
            float Yc = Y + 0.5f + shiftY[t] * magnification;

            // Gradient Variables
            float GMag, GMag1, GMagSum1 = 0;
            float GdotR = 0, GdotR1 = 0;

            // Radiality Variables
            float Dk, DivDFactor = 0;

            // Calculate Gradient at center
            float GMagCenter = 0;
            float IWCenter = 0;
            if (doGradWeight == 1 || display != 0){
                Gx1 = _interpolateGxGy(Xc, Yc, t, true);
                Gy1 = _interpolateGxGy(Xc, Yc, t, false);
                GMagCenter = sqrt(Gx1 * Gx1 + Gy1 * Gy1);
                IWCenter = _interpolateIntensity(Xc, Yc, t);
            }

            // Output
            float CGH = 0;

            for (int sampleIter = 0; sampleIter < nRingCoordinates; sampleIter++) {

                int isDiverging = -1;
                int isDiverging1 = -1;

                // Sample (x, y) position
                x0 = Xc + xRingCoordinates0[sampleIter];
                y0 = Yc + yRingCoordinates0[sampleIter];

                Gx = _interpolateGxGy(x0, y0, t, true);
                Gy = _interpolateGxGy(x0, y0, t, false);
                GMag = sqrt(Gx * Gx + Gy * Gy);
                GdotR = (Gx * xRingCoordinates0[sampleIter] + Gy * yRingCoordinates0[sampleIter]) / (GMag * spatialRadius);

                if (GdotR > 0 || GdotR != GdotR) isDiverging = 1;

                if(doGradWeight == 1 || display == 2) {
                    x1 = Xc + xRingCoordinates1[sampleIter];
                    y1 = Yc + yRingCoordinates1[sampleIter];
                    Gx1 = _interpolateGxGy(x1, y1, t, true);
                    Gy1 = _interpolateGxGy(x1, y1, t, false);
                    GMag1 = sqrt(Gx1 * Gx1 + Gy1 * Gy1);
                    GdotR1 = (Gx * xRingCoordinates1[sampleIter] + Gy * yRingCoordinates1[sampleIter]) / (GMag1 * gradRadius);
                    if (GdotR1 > 0 || GdotR1 != GdotR1) isDiverging1 = 1;
                    if (isDiverging1 == 1) {
                        GMagSum1 -= GMag1 - GMagCenter;
                    } else {
                        GMagSum1 += GMag1 - GMagCenter;
                    }
                    //GMagSum1 += _calculateGradientMagnitude(x1, y1, t) - GMagCenter;
                }

                // Perpendicular distance from (Xc,Yc) to gradient line through (x,y)
                Dk = 1f - _calculateDk(x0, y0, Xc, Yc, Gx, Gy, GMag) / spatialRadius;

//                if (doRadialitySquaring == 1) {
//                    Dk = Dk * Dk;
//                }
                Dk = Dk * Dk;

                // Accumulate Variables
                if (isDiverging == 1) {
                    DivDFactor -= Dk;
                } else {
                    DivDFactor += Dk;
                }

            }
            DivDFactor = DivDFactor / nRingCoordinates;

            if(renormalize == 1){
                DivDFactor = 0.5f + (DivDFactor / 2);
            }

            if (radialityPositivityConstraint == 1){
                CGH = max(DivDFactor, 0);
            }else{
                CGH = DivDFactor;
            }

            if (doGradWeight == 1 || display == 2) {
                if (radialityPositivityConstraint == 1) GMagSum1 = max(GMagSum1, 0);
                GMagSum1 = GMagSum1 / nRingCoordinates;
                GMagSum1 = GMagSum1 / max(IWCenter,1);
                CGH = CGH * GMagSum1;
            }

            radArray[getIdxRM3(X, Y, t)] = CGH;
            if (display == 1) {
                radArray[getIdxRM3(X, Y, t)] = GMagCenter;
            }else if (display == 2) {
                radArray[getIdxRM3(X, Y, t)] = GMagSum1;
            }else if(display == 3){
                radArray[getIdxRM3(X, Y, t)] = IWCenter;
            }

        }
    }

    private void calculateSRRF() {
        if (SRRForder == -1) {
            _calculatePairwiseProductSum();
        }
        else if(SRRForder == 0) {
            _calculateMIP();
        } else if(SRRForder == 1) {
            _calculateAVE();
        } else {
            _calculateACRF();
        }
    }

    private void _calculateMIP() {
        int idx = getGlobalId();
        int x = idx % widthMBorderless + borderM;
        int y = (idx / widthMBorderless) % heightMBorderless + borderM;

        float _x;
        float _y;

        float MIP = 0;

        if (doIntensityWeighting == 1) {
            for (int t=0; t<nTimePoints; t++) {
                _x = (float) x + 0.5f + shiftX[t] * magnification;
                _y = (float) y + 0.5f + shiftY[t] * magnification;
                MIP = max(radArray[getIdxRM3(x, y, t)] * _interpolateIntensity(_x, _y, t),MIP);
            }
        } else {
            for (int t = 0; t < nTimePoints; t++) {
                MIP = max(radArray[getIdxRM3(x, y, t)], MIP);
            }
        }

        SRRFArray[y * widthM + x] = MIP;
    }

    private void _calculatePairwiseProductSum() {
        int idx = getGlobalId();
        int x = idx % widthMBorderless + borderM;
        int y = (idx / widthMBorderless) % heightMBorderless + borderM;

        float _x;
        float _y;

        float pps = 0, r0 = 0, r1 = 0;
        float IW = 0;
        int counter = 0;


        for (int t0 = 0; t0 < nTimePoints; t0++) {
            r0 = max(radArray[getIdxRM3(x, y, t0)], 0);
            if (r0 > 0) {
                for (int t1 = t0; t1 < nTimePoints; t1++) {
                    r1 = max(radArray[getIdxRM3(x, y, t1)], 0);
                    pps += r0 * r1;
                    counter++;
                }
            } else counter += nTimePoints - t0;
        }
        pps /= max(counter, 1);


        if (doIntensityWeighting == 1) {
            for (int t = 0; t < nTimePoints; t++) {
                _x = (float) x + 0.5f + shiftX[t] * magnification;
                _y = (float) y + 0.5f + shiftY[t] * magnification;
                IW += _interpolateIntensity(_x, _y, t) / nTimePoints;
            }
            pps *= IW;
        }

        SRRFArray[y * widthM + x] = pps;
    }

    private void _calculateAVE() {
        int idx = getGlobalId();
        int x = idx % widthMBorderless + borderM;
        int y = (idx / widthMBorderless) % heightMBorderless + borderM;

        float _x;
        float _y;

        float mean = 0;

        if (doIntensityWeighting == 1) {
            for (int t=0; t<nTimePoints; t++) {
                _x = (float) x + 0.5f + shiftX[t] * magnification;
                _y = (float) y + 0.5f + shiftY[t] * magnification;
                mean += radArray[getIdxRM3(x, y, t)] * _interpolateIntensity(_x, _y, t);
            }
        } else {
            for (int t = 0; t < nTimePoints; t++) {
                mean += radArray[getIdxRM3(x, y, t)];
            }
        }
        mean = mean / nTimePoints;

        SRRFArray[y * widthM + x] = mean;
    }

    private void _calculateACRF(){
        int idx = getGlobalId();
        int x = idx % widthMBorderless + borderM;
        int y = (idx / widthMBorderless) % heightMBorderless + borderM;

        float _x;
        float _y;

        float mean = 0;
        float IW = 0;
        int t = 0;
        int tBin = 0;
        int nBinnedTimePoints = nTimePoints;
        float ABCD = 0;
        float ABC = 0;
        float AB = 0;
        float CD = 0;
        float AC = 0;
        float BD = 0;
        float AD = 0;
        float BC = 0;
        float A = 0;
        float B = 0;
        float C = 0;
        float D = 0;

        for (int ti=0; ti<nTimePoints; ti++) {
            _x = (float) x + 0.5f + shiftX[ti] * magnification;
            _y = (float) y + 0.5f + shiftY[ti] * magnification;
            IW += radArray[getIdxRM3(x, y, ti)] * _interpolateIntensity(_x, _y, ti);
            mean += radArray[getIdxRM3(x, y, ti)];
        }
        mean = mean / nTimePoints;
        IW = IW / nTimePoints;


        if(doIntegrateLagTimes != 1) {
            t = 0;
            ABCD = 0;
            ABC = 0;
            AB = 0;
            CD = 0;
            AC = 0;
            BD = 0;
            AD = 0;
            BC = 0;
            while (t < (nTimePoints - SRRForder)) {
                AB += ((radArray[getIdxRM3(x, y, t)] - mean) * (radArray[getIdxRM3(x, y, t + 1)] - mean));
                if (SRRForder == 3) {
                    ABC += ((radArray[getIdxRM3(x, y, t)] - mean) * (radArray[getIdxRM3(x, y, t + 1)] - mean) * (radArray[getIdxRM3(x, y, t + 2)] - mean));
                }
                if (SRRForder == 4) {
                    A = radArray[getIdxRM3(x, y, t)] - mean;
                    B = radArray[getIdxRM3(x, y, t + 1)] - mean;
                    C = radArray[getIdxRM3(x, y, t + 2)] - mean;
                    D = radArray[getIdxRM3(x, y, t + 3)] - mean;
                    ABCD += A * B * C * D;
                    CD += C * D;
                    AC += A * C;
                    BD += B * D;
                    AD += A * D;
                    BC += B * C;
                }
                t++;
            }
            if (SRRForder == 3) {
                SRRFArray[y * widthM + x] = abs(ABC) / (float) nTimePoints;
            } else if (SRRForder == 4) {
                SRRFArray[y * widthM + x] = abs(ABCD - AB * CD - AC * BD - AD * BC) / (float) nTimePoints;
            } else {
                SRRFArray[y * widthM + x] = abs(AB) / (float) nTimePoints;
            }
        }else{

            while (nBinnedTimePoints > SRRForder) {
                t = 0;
                AB = 0;

                while (t < (nBinnedTimePoints - SRRForder)) {
                    tBin = t * SRRForder;
                    AB += ((radArray[getIdxRM3(x, y, t)] - mean) * (radArray[getIdxRM3(x, y, t + 1)] - mean));
                    if (SRRForder == 3) {
                        ABC += ((radArray[getIdxRM3(x, y, t)] - mean) * (radArray[getIdxRM3(x, y, t + 1)] - mean) * (radArray[getIdxRM3(x, y, t + 2)] - mean));
                    }
                    if (SRRForder == 4) {
                        A = radArray[getIdxRM3(x, y, t)] - mean;
                        B = radArray[getIdxRM3(x, y, t + 1)] - mean;
                        C = radArray[getIdxRM3(x, y, t + 2)] - mean;
                        D = radArray[getIdxRM3(x, y, t + 3)] - mean;
                        ABCD += A * B * C * D;
                        CD += C * D;
                        AC += A * C;
                        BD += B * D;
                        AD += A * D;
                        BC += B * C;
                    }
                    radArray[getIdxRM3(x, y, t)] = 0;
                    if (tBin < nBinnedTimePoints){
                        for(int _t = 0; _t < SRRForder; _t++) {
                            radArray[getIdxRM3(x, y, t)] += radArray[getIdxRM3(x,y,tBin+_t)] / (float) SRRForder;
                        }
                    }
                    t++;
                }
                if (SRRForder == 3) {
                    SRRFArray[y * widthM + x] += abs(ABC) / (float) nBinnedTimePoints;
                } else if (SRRForder == 4) {
                    SRRFArray[y * widthM + x] += abs((ABCD - AB * CD - AC * BD - AD * BC)) / (float) nBinnedTimePoints;
                } else {
                    SRRFArray[y * widthM + x] += abs(AB) / (float) nBinnedTimePoints;
                }
                nBinnedTimePoints = nBinnedTimePoints / SRRForder;
            }
        }
        if (doIntensityWeighting == 1) {
            SRRFArray[y * widthM + x] = IW * SRRFArray[y * widthM + x];
        }
    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    private float _calculateDk(float x, float y, float Xc, float Yc, float Gx, float Gy, float Gx2Gy2) {
        float Dk = abs(Gy * (Xc - x) - Gx * (Yc - y)) / Gx2Gy2;
        if (Dk == Dk) return Dk;
        else return 1f * spatialRadius;
    }

    private void _calculateGxGy(int x, int y, int t) {

        //boundary checked x - y pixel co-ords
        int x_, y_;
        float v;

        if (doGradSmooth == 0) {
            int x0 = max(x-1, 0);
            int x1 = min(x+1, width-1);
            int y0 = max(y-1, 0);
            int y1 = min(y+1, height-1);
            GxArray[getIdx3(x, y, t)] = -pixels[getIdx3(x0, y, t)]+pixels[getIdx3(x1, y, t)];
            GyArray[getIdx3(x, y, t)] = -pixels[getIdx3(x, y0, t)]+pixels[getIdx3(x, y1, t)];
            return;
        }

        // Calculate Gx or Gy
        v = 0;
        for (int i = -ParallelGRadius; i <= ParallelGRadius; i++) {
            for (int j = -PerpendicularGRadius; j <= PerpendicularGRadius; j++) {
                x_ = min(max((x + i), 0), width - 1);
                y_ = min(max((y + j), 0), height - 1);
                if (i < 0) {
                    v -= pixels[getIdx3(x_, y_, t)];
                } else if (i > 0) {
                    v += pixels[getIdx3(x_, y_, t)];
                }
            }
        }
        GxArray[getIdx3(x, y, t)] = v;

        v = 0;
        for (int i = -PerpendicularGRadius; i <= PerpendicularGRadius; i++) {
            for (int j = -ParallelGRadius; j <= ParallelGRadius; j++) {
                x_ = min(max((x + i), 0), width - 1);
                y_ = min(max((y + j), 0), height - 1);
                if (j < 0) {
                    v -= pixels[getIdx3(x_, y_, t)];
                } else if (j > 0) {
                    v += pixels[getIdx3(x_, y_, t)];
                }
            }
        }
        GyArray[getIdx3(x, y, t)] = v;

    }

    private float _interpolateGxGy(float x, float y, int t, boolean isGx){

        x = x / magnification;
        y = y / magnification;

        if (x<1.5f || x>width-1.5f || y<1.5f || y>height-1.5f)
            return 0;

        if(isGx) {
            int u0 = (int) floor(x - 0.5f);
            int v0 = (int) floor(y - 0.5f);
            float q = 0.0f;
            for (int j = 0; j <= 3; j++) {
                int v = v0 - 1 + j;
                float p = 0.0f;
                for (int i = 0; i <= 3; i++) {
                    int u = u0 - 1 + i;
                    p = p + GxArray[getIdx3(u, v, t)] * cubic(x - (u + 0.5f));
                }
                q = q + p * cubic(y - (v + 0.5f));
            }
            return q;
        }else{
            int u0 = (int) floor(x - 0.5f);
            int v0 = (int) floor(y - 0.5f);
            float q = 0.0f;
            for (int j = 0; j <= 3; j++) {
                int v = v0 - 1 + j;
                float p = 0.0f;
                for (int i = 0; i <= 3; i++) {
                    int u = u0 - 1 + i;
                    p = p + GyArray[getIdx3(u, v, t)] * cubic(x - (u + 0.5f));
                }
                q = q + p * cubic(y - (v + 0.5f));
            }
            return q;
        }
    }

    private float _interpolateIntensity(float x, float y, int t){

        x = x / magnification;
        y = y / magnification;

        if (x<1.5f || x>width-1.5f || y<1.5f || y>height-1.5f)
            return 0;
        int u0 = (int) floor(x - 0.5f);
        int v0 = (int) floor(y - 0.5f);
        float q = 0.0f;
        for (int j = 0; j <= 3; j++) {
            int v = v0 - 1 + j;
            float p = 0.0f;
            for (int i = 0; i <= 3; i++) {
                int u = u0 - 1 + i;
                p = p + pixels[getIdx3(u, v, t)] * cubic(x - (u + 0.5f));
            }
            q = q + p * cubic(y - (v + 0.5f));
        }
        return max(q, 0.0f);
    }

    private float cubic(float x) {
        float a = 0.5f; // Catmull-Rom interpolation
        if (x < 0.0f) x = -x;
        float z = 0.0f;
        if (x < 1.0f)
            z = x * x * (x * (-a + 2.0f) + (a - 3.0f)) + 1.0f;
        else if (x < 2.0f)
            z = -a * x * x * x + 5.0f * a * x * x - 8.0f * a * x + 4.0f * a;
        return z;
    }

    ///////////////////////
    // Get Array Indexes //
    ///////////////////////

    private int getIdx3(int x, int y, int t) {
        int pt = t * widthHeight;
        int pf = y * width + x; // position within a frame
        return pt + pf;
    }

    private int getIdxRM3(int x, int y, int t) {
        int ptRM = t * widthHeightM;
        int pfRM = y * widthM + x;
        return ptRM + pfRM;
    }
}
