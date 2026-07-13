package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.*;

import java.awt.*;
import java.util.*;

/**
 * Performs IoU (Intersection over Union) analysis between truth and test labeled images.
 * Generates color-coded visualizations based on object matching categories (TP, FP, FN, splits, fusions, etc.).
 * Supports metrics computation and image overlay visualization.
 */
public class IoUAnalysis {

    // Color indices for visualization categories
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
    protected static final int SECONDARY_OVERLAP_COLOR_INDEX = 11;
    
    private final ImagePlus truth;
    private final ImagePlus test;
    private ImageProcessor iou;
    private final int maxTruth;
    private final int maxTest;
    private final HashMap<Double, ImageProcessor> colorCodeCache = new HashMap<>();
    private final ImageProcessor histo2D;


    /**
     * Constructs an IoUAnalysis instance with precomputed IoU values and histograms.
     * 
     * @param truth labeled truth image
     * @param test labeled test image
     * @param histo2D 2D histogram of object overlaps (truth labels × test labels)
     * @param iou IoU matrix where iou[i][j] = IoU between truth object i and test object j
     * @param maxTruth maximum label value in truth image
     * @param maxTest maximum label value in test image
     */
    public IoUAnalysis(ImagePlus truth, ImagePlus test, ImageProcessor histo2D, ImageProcessor iou, int maxTruth, int maxTest) {
        this.truth = truth;
        this.test = test;
        this.histo2D = histo2D;
        this.iou = iou;
        this.maxTruth = maxTruth;
        this.maxTest = maxTest;
    }

    /**
     * Factory method to create an IoUAnalysis from labeled images with filtering options.
     * Normalizes object labels, computes IoU matrix, and filters objects by size and distance to border.
     * 
     * @param truth labeled truth image (3D stack)
     * @param test labeled test image (3D stack)
     * @param minSize minimum object size in pixels; objects below this threshold are marked as not analyzed
     * @param minDist minimum distance from image border in pixels; objects closer are marked as not analyzed
     * @return IoUAnalysis instance ready for metrics computation and visualization
     */
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

    /**
     * Factory method to create an IoUAnalysis from Regions of Interest (ROIs).
     * Converts ROIs to labeled images and computes IoU analysis with border filtering.
     * 
     * @param truthRois array of ground truth ROIs
     * @param testRois array of test ROIs
     * @param width image width
     * @param height image height
     * @param minDist minimum distance from image border in pixels
     * @return IoUAnalysis instance based on ROI comparison
     */
    public static IoUAnalysis create(Roi[] truthRois, Roi[] testRois, int width, int height, double minDist){
        ImageProcessor truthLabels = labeledImageFromRois(width, height, truthRois);
        ImageProcessor testLabels = labeledImageFromRois(width, height, testRois);
        ImagePlus truth = new ImagePlus("truth_rois_labels", truthLabels);
        ImagePlus test = new ImagePlus("test_rois_labels", testLabels);
        int maxTruth = MicUtils.correctObjectNumbering(truth);
        int maxTest = MicUtils.correctObjectNumbering(test);
        ImageProcessor histo2D = MicUtils.histo2D(truth, maxTruth, test, maxTest);
        int[] histoTruth = MicUtils.histo1D(truth, maxTruth);
        int[] histoTest = MicUtils.histo1D(test, maxTest);
        ImageProcessor iou = buildIoUImageFromRois(truthRois, testRois);
        IoUAnalysis result = new IoUAnalysis(truth, test, histo2D, iou, maxTruth, maxTest);
        result.checkPositionAndSize(iou, histoTruth, histoTest, 0, minDist, truth, test);
        return result;
    }

    /**
     * Combines truth and test labeled images into a single color-coded visualization.
     * Each pixel is assigned a color based on the IoU relationship between the truth and test objects at that location.
     * 
     * @param truth labeled truth image
     * @param test labeled test image
     * @param colorcode IoU color code matrix where colorcode[truthLabel][testLabel] = color index
     * @return ByteProcessor with color indices ready for LUT visualization
     */
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

    /**
     * Builds the MiC-specific Look-Up Table (LUT) for visualization.
     * Maps color indices to RGB values for different object relationship categories.
     * 
     * Color scheme:
     * - TP (index 1): Yellow - correct detection
     * - TP_OVER (index 2): Red - test object oversegments truth
     * - TP_UNDER (index 3): Green - test object undersegments truth
     * - FUSED (index 4): Orange - one test object matches multiple truth objects (fusion)
     * - SPLIT (index 5): Cyan - one truth object matches multiple test objects (split)
     * - UNDER_IOU (index 6): Blue - partial overlap below threshold
     * - UNDER_IOU_EXT (index 7): Purple - extended partial overlap region
     * - FP (index 8): Dark red - false positive (test object without truth match)
     * - FN (index 9): Dark green - false negative (truth object without test match)
     * - NOT_ANALYZED (index 10): Grey - object filtered out (too small or near border)
     * - SECONDARY_OVERLAP (index 11): White - partial overlap between two already matched objects
     * 
     * @return ImageJ LUT object for color mapping
     */
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
        // Secondary overlap between already accepted objects (white)
        r[11] = (byte) 255;
        g[11] = (byte) 255;
        b[11] = (byte) 255;



        return new LUT(r, g, b);
    }

    /**
     * Returns the IoU matrix.
     * 
     * @return ImageProcessor containing IoU values; iou[i][j] = IoU between truth object i and test object j
     */
    public ImageProcessor getIoU() {
        return iou;
    }


    /**
     * Returns the 2D histogram of object overlaps.
     * 
     * @return ImageProcessor containing pixel-level co-occurrence counts between truth and test objects
     */
    public ImageProcessor getHisto2D() {
        return histo2D;
    }

    /**
     * Computes metrics (TP, FP, FN) using greedy best-match assignment.
     * Each truth object is matched to at most one test object and vice versa.
     * Matches are assigned greedily in order of descending IoU values above the threshold.
     * 
     * @param threshold minimum IoU value for a match to be considered (typically 0.5)
     * @return Metrics object containing TP, FP, and FN counts
     */
    public Metrics getMetrics(double threshold){
        return getMatchingMetrics(threshold);
    }

    /**
     * Computes object-level metrics using greedy matching strategy.
     * 
     * Algorithm:
     * 1. Collect all IoU pairs above threshold
     * 2. Sort pairs by descending IoU value
     * 3. Greedily assign matches: each valid pair that hasn't used either object
     * 4. Count TP (matched objects), FP (unmatched test), FN (unmatched truth)
     * 
     * @param threshold minimum IoU threshold for valid matches
     * @return Metrics with TP, FP, FN counts
     */
    private Metrics getMatchingMetrics(double threshold){
        int nTruth = maxTruth;
        int nTest = maxTest;
        boolean[] validTruth = new boolean[nTruth];
        boolean[] validTest = new boolean[nTest];
        // Mark objects as valid if they weren't filtered out (IoU value >= 0)
        for(int i = 0; i < nTruth; i++) validTruth[i] = iou.getf(i + 1, 0) >= 0;
        for(int j = 0; j < nTest; j++) validTest[j] = iou.getf(0, j + 1) >= 0;
        
        ArrayList<ObjectMatch> matches = new ArrayList<>();
        for(int i = 0; i < nTruth; i++){
            if(!validTruth[i]) continue;
            for(int j = 0; j < nTest; j++){
                if(!validTest[j]) continue;
                double value = iou.getf(i + 1, j + 1);
                if(value >= threshold) matches.add(new ObjectMatch(i, j, value));
            }
        }
        
        // Sort by IoU descending for greedy assignment
        matches.sort((a, b) -> Double.compare(b.iou, a.iou));
        
        // Greedy matching: assign highest IoU pairs first, prevent one-to-many
        boolean[] acceptedTruth = new boolean[nTruth];
        boolean[] acceptedTest = new boolean[nTest];
        int tp = 0;
        for(ObjectMatch match : matches){
            if(acceptedTruth[match.truth] || acceptedTest[match.test]) continue;
            acceptedTruth[match.truth] = true;
            acceptedTest[match.test] = true;
            tp++;
        }
        
        // Count unmatched objects
        int fp = 0;
        for(int j = 0; j < nTest; j++){
            if(validTest[j] && !acceptedTest[j]) fp++;
        }
        int fn = 0;
        for(int i = 0; i < nTruth; i++){
            if(validTruth[i] && !acceptedTruth[i]) fn++;
        }
        return new Metrics(tp, fp, fn);
    }

    /**
     * Computes pixel-level metrics from the 2D histogram.
     * 
     * @return Metrics object with pixel-level TP, FP, FN counts
     */
    public Metrics getPixelMetrics() {
        return new Metrics(
                histo2D,
                -1,
                null
        );
    }

    /**
     * Returns the truth labeled image.
     * 
     * @return ImagePlus containing ground truth object labels
     */
    public ImagePlus getTruth() {
        return truth;
    }

    /**
     * Returns the test labeled image.
     * 
     * @return ImagePlus containing test segmentation object labels
     */
    public ImagePlus getTest() {
        return test;
    }

    /**
     * Returns the maximum label value in the truth image.
     * 
     * @return maximum truth object label (number of truth objects)
     */
    public int getMaxTruth() {
        return maxTruth;
    }

    /**
     * Returns the maximum label value in the test image.
     * 
     * @return maximum test object label (number of test objects)
     */
    public int getMaxTest() {
        return maxTest;
    }

    /**
     * Retrieves color code matrix from cache or computes it if not cached.
     * 
     * @param threshold IoU threshold for color classification
     * @return ByteProcessor with color indices for each (truth, test) object pair
     */
    public ImageProcessor getColorCode(double threshold) {
        ImageProcessor cached = colorCodeCache.get(threshold);
        if (cached != null) return cached;
        ImageProcessor generated = computeColorCode(threshold);
        colorCodeCache.put(threshold, generated);
        return generated;
    }

    /**
     * Computes color-coded IoU matrix for visualization.
     * 
     * Algorithm:
     * 1. Pre-compute row and column maxima to identify objects with accepted TPs
     * 2. Classify each (truth, test) pair:
     *    - TP: IoU >= threshold
     *    - SECONDARY_OVERLAP: IoU > 0 AND both objects have accepted TPs
     *    - SPLIT: IoU > 0 AND only truth has accepted TP
     *    - FUSED: IoU > 0 AND only test has accepted TP
     *    - UNDER_IOU: IoU > 0 AND neither has accepted TP
     *    - NOT_ANALYZED: IoU = -1 (filtered out)
     * 3. Classify edge cases (objects with no matches or only poor matches)
     * 
     * @param threshold minimum IoU for TP classification
     * @return ByteProcessor with color indices ready for LUT visualization
     */
    public ImageProcessor computeColorCode(double threshold) {
        ByteProcessor bp = new ByteProcessor(this.iou.getWidth(), this.iou.getHeight());
        
        // Pre-compute maximum IoU for each test object (row) and truth object (column)
        // These identify which objects have at least one accepted TP match
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
        
        // Classify interior cells: (truth, test) object pairs
        for (int y = 1; y < this.iou.getHeight(); y++) {
            for (int x = 1; x < this.iou.getWidth(); x++) {
                double val = this.iou.getf(x, y);
                if(val == -1){
                    bp.set(x, y, NOT_ANALYZED_COLOR_INDEX);
                }else if(val >= threshold){
                    bp.set(x, y, TP_COLOR_INDEX);
                }else if(val > 0){
                    // Partial overlap: determine type based on whether each object has accepted TP
                    boolean truthHasAcceptedTP = colMax[x] >= threshold;
                    boolean testHasAcceptedTP = rowMax[y] >= threshold;
                    if(truthHasAcceptedTP && testHasAcceptedTP){
                        bp.set(x, y, SECONDARY_OVERLAP_COLOR_INDEX);
                    }else if(truthHasAcceptedTP){
                        bp.set(x, y, SPLIT_COLOR_INDEX);
                    }else if(testHasAcceptedTP){
                        bp.set(x, y, FUSED_COLOR_INDEX);
                    }else{
                        bp.set(x, y, UNDER_IOU_COLOR_INDEX);
                    }
                }
            }
        }
        
        // Classify test objects (row 0): analyze each test object's matches
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
                // TP_OVER: test object has accepted TP match(es)
                if (foundTP > 0) bp.set(0, y, TP_OVER_COLOR_INDEX);
                // UNDER_IOU_EXT: test object only has partial overlaps
                else if (foundIoU > 0) bp.set(0, y, UNDER_IOU_EXT_COLOR_INDEX);
                // FP: test object has no matches
                else bp.set(0, y, FP_COLOR_INDEX);
            }
        }
        row = null;
        
        // Classify truth objects (column 0): analyze each truth object's matches
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
                // TP_UNDER: truth object has accepted TP match(es)
                if (foundTP > 0) bp.set(x, 0, TP_UNDER_COLOR_INDEX);
                // UNDER_IOU_EXT: truth object only has partial overlaps
                else if (foundIoU > 0) bp.set(x, 0, UNDER_IOU_EXT_COLOR_INDEX);
                // FN: truth object has no matches
                else bp.set(x, 0, FN_COLOR_INDEX);
            }
        }

        return bp;
    }

    /**
     * Creates a composite overlay image combining truth and test segmentations.
     * Each pixel is colored according to the IoU relationship at that location.
     * 
     * @param truthPlane truth labeled image (single plane)
     * @param testPlane test labeled image (single plane)
     * @param threshold IoU threshold for visualization
     * @return ByteProcessor with color-coded overlay
     */
    public ImageProcessor createCompositePlane(ImageProcessor truthPlane, ImageProcessor testPlane, double threshold) {
        ImageProcessor colorcode = getColorCode(threshold);

        return displayCombinationProcessor(truthPlane, testPlane, colorcode);
    }

    /**
     * Filters objects by size and distance to image border.
     * Objects that are too small or too close to borders are marked as not analyzed (IoU = -1).
     * 
     * @param iou IoU matrix to filter
     * @param histoTruth pixel count histogram for truth objects
     * @param histoTest pixel count histogram for test objects
     * @param minSize minimum object size in pixels
     * @param minDist minimum distance from border in pixels
     * @param truth ground truth image stack
     * @param test test segmentation image stack
     */
    private void checkPositionAndSize(
            ImageProcessor iou,
            int[] histoTruth,
            int[] histoTest,
            double minSize,
            double minDist,
            ImagePlus truth,
            ImagePlus test
    ) {
        // Mark undersized objects
        for (int y = 0; y < iou.getHeight(); y++) {
            for (int x = 0; x < iou.getWidth(); x++) {
                boolean truthTooSmall = x >= 0 && x < histoTruth.length && histoTruth[x] < minSize;
                boolean testTooSmall = y >= 0 && y < histoTest.length && histoTest[y] < minSize;

                if (truthTooSmall || testTooSmall) {
                    iou.setf(x, y, -1);
                }
            }
        }

        // Mark objects touching or near image borders
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

    /**
     * Identifies all object labels that touch or are close to image borders.
     * Scans a border region of width/height minDist around image edges.
     * 
     * @param ip labeled image processor (single plane)
     * @param minDist border distance threshold
     * @return list of object labels within minDist pixels of any edge
     */
    private ArrayList<Integer> objectBorder(ImageProcessor ip, double minDist) {

        ArrayList<Integer> border = new ArrayList<>();

        int dist = (int) Math.round(minDist);

        dist = Math.max(0, dist);
        dist = Math.min(dist, Math.min(ip.getWidth(), ip.getHeight()) - 1);

        // Check top and bottom edges
        for (int x = 0; x < ip.getWidth(); x++) {
            for (int y = 0; y <= dist; y++) {

                float v1 = ip.getf(x, y);
                float v2 = ip.getf(x, ip.getHeight() - 1 - y);

                if (v1 > 0) border.add((int) v1);
                if (v2 > 0) border.add((int) v2);
            }
        }

        // Check left and right edges
        for (int y = 0; y < ip.getHeight(); y++) {
            for (int x = 0; x <= dist; x++) {

                float v1 = ip.getf(x, y);
                float v2 = ip.getf(ip.getWidth() - 1 - x, y);

                if (v1 > 0) border.add((int) v1);
                if (v2 > 0) border.add((int) v2);
            }
        }

        // Remove duplicates and return unique border object labels
        Set<Integer> set = new HashSet<>(border);
        return new ArrayList<>(set);
    }

    /**
     * Marks all IoU entries for border objects as not analyzed (-1).
     * 
     * @param iou IoU matrix to modify
     * @param borderTruth list of truth object labels to remove
     * @param borderTest list of test object labels to remove
     */
    private void removefromIoU(ImageProcessor iou, ArrayList<Integer> borderTruth, ArrayList<Integer> borderTest) {
        // Mark entire rows (test objects) as not analyzed
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

        // Mark entire columns (truth objects) as not analyzed
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

    /**
     * Computes analysis metrics across a range of IoU thresholds.
     * Useful for generating precision-recall or F1 curves.
     * 
     * @param overlapMin minimum IoU threshold
     * @param overlapMax maximum IoU threshold
     * @param overlapInc threshold step size
     * @return AnalysisResult with metrics computed at each threshold
     */
    public AnalysisResult computeAnalysisResult(double overlapMin, double overlapMax, double overlapInc){
        AnalysisResult result = new AnalysisResult();

        result.setPixelMetrics(getPixelMetrics());
        result.setObjectMetrics(getMetrics(0.5));

        int nbIndexes = (int)Math.round((overlapMax - overlapMin) / overlapInc + 1);

        Metrics[] curveMetrics = new Metrics[nbIndexes];
        double[] thresholds = new double[nbIndexes];

        int index = 0;

        for(double threshold = overlapMin; threshold <= overlapMax + 1e-6; threshold += overlapInc){
            thresholds[index] = Math.round(threshold * 10000.0) / 10000.0;
            curveMetrics[index] = getMetrics(thresholds[index]);
            index++;
        }

        result.setThresholds(thresholds);
        result.setCurveMetrics(curveMetrics);

        return result;
    }

    /**
     * Creates a labeled image from an array of ROIs.
     * Each ROI is filled with its index + 1 as the label.
     * 
     * @param width image width
     * @param height image height
     * @param rois array of ROIs to label
     * @return ShortProcessor labeled image
     */
    private static ImageProcessor labeledImageFromRois(int width, int height, Roi[] rois){
        ImageProcessor ip = new ShortProcessor(width, height);
        for(int i = 0; i < rois.length; i++){
            ip.setColor(i + 1);
            ip.fill(rois[i]);
        }
        return ip;
    }

    /**
     * Computes IoU matrix directly from ROI objects.
     * 
     * @param truthRois array of ground truth ROIs
     * @param testRois array of test ROIs
     * @return FloatProcessor with IoU values; iou[i+1][j+1] = IoU between truthRois[i] and testRois[j]
     */
    private static FloatProcessor buildIoUImageFromRois(Roi[] truthRois, Roi[] testRois){
        FloatProcessor iou = new FloatProcessor(truthRois.length + 1, testRois.length + 1);
        for(int truthIndex = 0; truthIndex < truthRois.length; truthIndex++){
            for(int testIndex = 0; testIndex < testRois.length; testIndex++){
                double value = compareRois(truthRois[truthIndex], testRois[testIndex]);
                if(value > 0) iou.setf(truthIndex + 1, testIndex + 1, (float)value);
            }
        }
        return iou;
    }

    /**
     * Computes IoU between two ROI objects using the formula: intersection / (truth + test - intersection).
     * 
     * @param truthRoi ground truth ROI
     * @param testRoi test ROI
     * @return IoU value [0, 1], or 0 if no intersection
     */
    private static double compareRois(Roi truthRoi, Roi testRoi){
        Rectangle truthRect = truthRoi.getBounds();
        Rectangle testRect = testRoi.getBounds();
        if(!truthRect.intersects(testRect)) return 0;
        Point[] truthPoints = truthRoi.getContainedPoints();
        Point[] testPoints = testRoi.getContainedPoints();
        double totalTruth = truthPoints.length;
        double totalTest = testPoints.length;
        double common = 0;
        for(Point p : testPoints){
            if(truthRoi.containsPoint(p.getX(), p.getY())) common++;
        }
        if(common == 0) return 0;
        return common / (totalTruth + totalTest - common);
    }

    /**
     * Filters IoU matrix by removing secondary overlaps (partial overlaps between matched objects).
     * Used internally for metrics computation.
     * 
     * @param threshold IoU threshold to identify accepted matches
     * @return duplicate of IoU matrix with secondary overlaps zeroed out
     */
    private ImageProcessor getIoUForMetrics(double threshold){
        ImageProcessor filtered = iou.duplicate();
        float[] rowMax = new float[iou.getHeight()];
        float[] colMax = new float[iou.getWidth()];
        for(int y = 1; y < iou.getHeight(); y++){
            float max = Float.NEGATIVE_INFINITY;
            for(int x = 1; x < iou.getWidth(); x++) max = Math.max(max, iou.getf(x, y));
            rowMax[y] = max;
        }
        for(int x = 1; x < iou.getWidth(); x++){
            float max = Float.NEGATIVE_INFINITY;
            for(int y = 1; y < iou.getHeight(); y++) max = Math.max(max, iou.getf(x, y));
            colMax[x] = max;
        }
        for(int y = 1; y < iou.getHeight(); y++){
            for(int x = 1; x < iou.getWidth(); x++){
                float val = iou.getf(x, y);
                boolean secondaryOverlap = val > 0 && val < threshold && colMax[x] >= threshold && rowMax[y] >= threshold;
                if(secondaryOverlap) filtered.setf(x, y, 0);
            }
        }
        return filtered;
    }

    /**
     * Represents a potential match between a truth and test object with associated IoU value.
     */
    private static class ObjectMatch {
        int truth;
        int test;
        double iou;
        ObjectMatch(int truth, int test, double iou){
            this.truth = truth;
            this.test = test;
            this.iou = iou;
        }
    }

}