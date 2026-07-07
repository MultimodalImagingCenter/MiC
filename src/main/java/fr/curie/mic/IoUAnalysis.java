package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.util.*;

public class IoUAnalysis {

    protected static final int TP_COLOR_INDEX = 1;
    protected static final int TP_OVER_COLOR_INDEX = 2;
    protected static final int TP_UNDER_COLOR_INDEX = 3;
    protected static final int FUSED_COLOR_INDEX = 4;
    protected static final int SPLIT_COLOR_INDEX = 5;
    protected static final int UNDER_IOU_COLOR_INDEX = 6;
    protected static final int UNDER_IOU_EXT_COLOR_INDEX = 7;
    protected static final int FP_COLOR_INDEX = 8;
    protected static final int FN_COLOR_INDEX = 9;
    protected static final int NOT_ANALYZED_COLOR_INDEX = 10;
    private final ImagePlus truth;
    private final ImagePlus test;
    private final ImageProcessor iou;
    private final int maxTruth;
    private final int maxTest;
    private final HashMap<Double, ImageProcessor> colorCodeCache = new HashMap<>();
    private final ImageProcessor histo2D;


    public IoUAnalysis(ImagePlus truth, ImagePlus test, ImageProcessor histo2D, ImageProcessor iou, int maxTruth, int maxTest) {
        this.truth = truth;
        this.test = test;
        this.histo2D = histo2D;
        this.iou = iou;
        this.maxTruth = maxTruth;
        this.maxTest = maxTest;
    }

    public static IoUAnalysis create(ImagePlus truth, ImagePlus test, double minSize, double minDist) {
        //long start = System.currentTimeMillis();
        int maxTruth = MicUtils.correctObjectNumbering(truth);
        int maxTest = MicUtils.correctObjectNumbering(test);

        ImageProcessor histo2D =MicUtils.histo2D(truth, maxTruth, test, maxTest);
        int[] histoTruth = MicUtils.histo1D(truth, maxTruth);
        int[] histoTest = MicUtils.histo1D(test, maxTest);

        ImageProcessor iou = MicUtils.computesIoUs(histo2D, histoTruth, histoTest);
        IoUAnalysis result = new IoUAnalysis(truth, test, histo2D, iou, maxTruth, maxTest);
        result.checkPositionAndSize(iou, histoTruth, histoTest, minSize, minDist, truth, test);
        //IJ.log("IoUAnalysis.create : "+(System.currentTimeMillis()-start)+" ms");
        return result;
    }

    public static ImageProcessor displayCombinationProcessor(ImageProcessor truth, ImageProcessor test, ImageProcessor colorcode) {
        ByteProcessor result = new ByteProcessor(
                truth.getWidth(),
                truth.getHeight()
        );

        int maxTruthLabel = colorcode.getWidth() - 1;
        int maxTestLabel = colorcode.getHeight() - 1;

        for (int y = 0; y < truth.getHeight(); y++) {
            for (int x = 0; x < truth.getWidth(); x++) {

                int truthLabel = (int) truth.getf(x, y);
                int testLabel = (int) test.getf(x, y);

                if (truthLabel < 0 || testLabel < 0 || truthLabel > maxTruthLabel || testLabel  > maxTestLabel) {
                    result.set(x, y, 0);
                } else {
                    result.set(x, y, colorcode.get(truthLabel, testLabel));
                }
            }
        }

        return result;
    }

    public static LUT getMiCLUT() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        //TP (yellow)
        r[1] = (byte) 255;
        g[1] = (byte) 255;
        b[1] = (byte) 0;
        //TP OVER (red)
        r[2] = (byte) 255;
        g[2] = (byte) 0;
        b[2] = (byte) 0;
        //TP under (green)
        r[3] = (byte) 0;
        g[3] = (byte) 255;
        b[3] = (byte) 0;
        //fused (orange)
        r[4] = (byte) 255;
        g[4] = (byte) 128;
        b[4] = (byte) 0;
        //split (cyan)
        r[5] = (byte) 0;
        g[5] = (byte) 255;
        b[5] = (byte) 255;
        //IoU under (blue)
        r[6] = (byte) 0;
        g[6] = (byte) 0;
        b[6] = (byte) 255;
        //IoU under_ext (purple)
        r[7] = (byte) 128;
        g[7] = (byte) 0;
        b[7] = (byte) 255;
        //FP (dark red)
        r[8] = (byte) 128;
        g[8] = (byte) 0;
        b[8] = (byte) 0;
        //FN (dark green)
        r[9] = (byte) 0;
        g[9] = (byte) 128;
        b[9] = (byte) 0;
        //Not Analyzed (grey)
        r[10] = (byte) 128;
        g[10] = (byte) 128;
        b[10] = (byte) 128;

        return new LUT(r, g, b);
    }

    public ImageProcessor getIoU() {
        return iou;
    }

    public ImageProcessor getHisto2D() {
        return histo2D;
    }

    public Metrics getMetrics(double threshold) {
        return new Metrics(
                iou,
                threshold,
                null
        );
    }

    public Metrics getPixelMetrics() {
        return new Metrics(
                histo2D,
                -1,
                null
        );
    }

    public ImagePlus getTruth() {
        return truth;
    }

    public ImagePlus getTest() {
        return test;
    }

    public int getMaxTruth() {
        return maxTruth;
    }

    public int getMaxTest() {
        return maxTest;
    }

    public ImageProcessor getColorCode(double threshold) {
        ImageProcessor cached = colorCodeCache.get(threshold);
        if (cached != null) return cached;
        ImageProcessor generated = computeColorCode(threshold);
        colorCodeCache.put(threshold, generated);
        return generated;
    }

    public ImageProcessor computeColorCode(double threshold) {
        ByteProcessor bp = new ByteProcessor(this.iou.getWidth(), this.iou.getHeight());
        float[] rowMax = new float[this.iou.getHeight()];
        float[] colMax = new float[this.iou.getWidth()];
        for(int y=1;y<this.iou.getHeight();y++){
            float max = Float.NEGATIVE_INFINITY;
            for(int x=1;x<this.iou.getWidth();x++){
                max = Math.max(max,this.iou.getf(x,y));
            }
            rowMax[y] = max;
        }
        for(int x=1;x<this.iou.getWidth();x++){
            float max = Float.NEGATIVE_INFINITY;
            for(int y=1;y<this.iou.getHeight();y++){
                max = Math.max(max,this.iou.getf(x,y));
            }
            colMax[x] = max;
        }

        float[] row = new float[this.iou.getWidth()];
        float[] col = new float[this.iou.getHeight()];
        for (int y = 1; y < this.iou.getHeight(); y++) {
            for (int x = 1; x < this.iou.getWidth(); x++) {
                double val = this.iou.getf(x, y);
                if (val == -1) {
                    bp.set(x, y, NOT_ANALYZED_COLOR_INDEX);
                } else if (val >= threshold) {
                    bp.set(x, y, TP_COLOR_INDEX);
                } else {
                    if (colMax[x] > threshold) {
                        bp.set(x, y, SPLIT_COLOR_INDEX);
                    } else if (rowMax[y] > threshold) {
                        bp.set(x, y, FUSED_COLOR_INDEX);
                    } else bp.set(x, y, UNDER_IOU_COLOR_INDEX);
                }
            }
        }
        for (int y = 1; y < this.iou.getHeight(); y++) {
            if (this.iou.getf(0, y) < 0) bp.set(0, y, NOT_ANALYZED_COLOR_INDEX);
            else {
                this.iou.getRow(0, y, row, this.iou.getWidth());
                int foundTP = 0;
                int foundIoU = 0;
                for (int x = 1; x < this.iou.getWidth(); x++) {
                    if (row[x] > threshold) foundTP++;
                    else if (row[x] > 0 && row[x] < threshold) foundIoU++;
                }
                if (foundTP > 0) bp.set(0, y, TP_OVER_COLOR_INDEX);
                else if (foundIoU > 0) bp.set(0, y, UNDER_IOU_EXT_COLOR_INDEX);
                else bp.set(0, y, FP_COLOR_INDEX);
            }
        }
        row = null;
        for (int x = 1; x < this.iou.getWidth(); x++) {
            if (this.iou.getf(x, 0) < 0) bp.set(x, 0, NOT_ANALYZED_COLOR_INDEX);
            else {
                this.iou.getColumn(x, 0, col, this.iou.getHeight());
                int foundTP = 0;
                int foundIoU = 0;
                for (int y = 1; y < this.iou.getHeight(); y++) {
                    if (col[y] > threshold) foundTP++;
                    else if (col[y] > 0 && col[y] < threshold) foundIoU++;
                }
                if (foundTP > 0) bp.set(x, 0, TP_UNDER_COLOR_INDEX);
                else if (foundIoU > 0) bp.set(x, 0, UNDER_IOU_EXT_COLOR_INDEX);
                else bp.set(x, 0, FN_COLOR_INDEX);
            }
        }

        return bp;
    }

    public ImageProcessor createCompositePlane(ImageProcessor truthPlane, ImageProcessor testPlane, double threshold) {
        ImageProcessor colorcode = getColorCode(threshold);

        return displayCombinationProcessor(truthPlane, testPlane, colorcode);
    }

    private void checkPositionAndSize(
            ImageProcessor iou,
            int[] histoTruth,
            int[] histoTest,
            double minSize,
            double minDist,
            ImagePlus truth,
            ImagePlus test
    ) {
        // Check object size
        for (int y = 0; y < iou.getHeight(); y++) {
            for (int x = 0; x < iou.getWidth(); x++) {
                boolean truthTooSmall = x >= 0 && x < histoTruth.length && histoTruth[x] < minSize;
                boolean testTooSmall = y >= 0 && y < histoTest.length && histoTest[y] < minSize;

                if (truthTooSmall || testTooSmall) {
                    iou.setf(x, y, -1);
                }
            }
        }

        // Check object distance to border
        if (minDist < 0) return;

        ArrayList<Integer> borderTruth = new ArrayList<>();
        ImageStack truthStack = truth.getImageStack();

        for (int z = 1; z <= truthStack.getSize(); z++) {
            borderTruth.addAll( objectBorder(truthStack.getProcessor(z), minDist)   );
        }

        Set<Integer> truthSet = new HashSet<>(borderTruth);
        borderTruth = new ArrayList<>(truthSet);
        ArrayList<Integer> borderTest = new ArrayList<>();
        ImageStack testStack = test.getImageStack();

        for (int z = 1; z <= testStack.getSize(); z++) {
            borderTest.addAll(objectBorder( testStack.getProcessor(z), minDist)  );
        }

        Set<Integer> testSet = new HashSet<>(borderTest);
        borderTest = new ArrayList<>(testSet);

        removefromIoU(iou, borderTruth, borderTest);
    }

    private ArrayList<Integer> objectBorder(ImageProcessor ip, double minDist) {

        ArrayList<Integer> border = new ArrayList<>();

        int dist = (int) Math.round(minDist);

        dist = Math.max(0, dist);
        dist = Math.min(dist, Math.min(ip.getWidth(), ip.getHeight()) - 1);

        for (int x = 0; x < ip.getWidth(); x++) {
            for (int y = 0; y <= dist; y++) {

                float v1 = ip.getf(x, y);
                float v2 = ip.getf(x, ip.getHeight() - 1 - y);

                if (v1 > 0) border.add((int) v1);
                if (v2 > 0) border.add((int) v2);
            }
        }

        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x <= dist; x++) {

                float v1 = ip.getf(x, y);
                float v2 = ip.getf(ip.getWidth() - 1 - x, y);

                if (v1 > 0) border.add((int) v1);
                if (v2 > 0) border.add((int) v2);
            }
        }

        Set<Integer> set = new HashSet<>(border);
        return new ArrayList<>(set);
    }

    private void removefromIoU(ImageProcessor iou, ArrayList<Integer> borderTruth, ArrayList<Integer> borderTest) {
        for (Integer bt : borderTruth) {

            if (bt == null) continue;

            int truthLabel = bt;

            if (truthLabel <= 0 || truthLabel >= iou.getWidth()) {
                IJ.log(
                        "Warning: truth border label " +
                                truthLabel +
                                " ignored because IoU width is " +
                                iou.getWidth()
                );
                continue;
            }

            for (int y = 0; y < iou.getHeight(); y++) {
                iou.setf(truthLabel, y, -1);
            }
        }

        for (Integer bt : borderTest) {

            if (bt == null) continue;

            int testLabel = bt;

            if (testLabel <= 0 || testLabel >= iou.getHeight()) {
                IJ.log(
                        "Warning: test border label " +
                                testLabel +
                                " ignored because IoU height is " +
                                iou.getHeight()
                );
                continue;
            }

            for (int x = 0; x < iou.getWidth(); x++) {
                iou.setf(x, testLabel, -1);
            }
        }
    }

}