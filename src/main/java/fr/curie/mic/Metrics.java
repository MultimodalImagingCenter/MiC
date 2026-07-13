package fr.curie.mic;

import ij.IJ;
import ij.process.ImageProcessor;

import java.util.*;

/**
 * Computes and stores segmentation metrics (TP, FP, FN) at object or pixel level.
 * Supports precision, recall, Jaccard Index, F1-measure, and precision-recall curves with confidence scores.
 */
public class Metrics {
    private final double IoUthreshold;
    private final ImageProcessor IoUs;

    private int tp;
    private int fn;
    private int fp;

    private final double[] confidences;
    private int[] labels;

    /**
     * Constructs Metrics from IoU matrix and optional confidence scores.
     * Uses either pixel-level or object-level analysis based on threshold sign.
     * 
     * @param ioUs ImageProcessor containing IoU values or pixel co-occurrence counts
     * @param ioUthreshold IoU threshold for object-level analysis (negative value triggers pixel-level analysis)
     * @param confidences optional detection confidence scores for precision-recall curve (can be null)
     */
    public Metrics(ImageProcessor ioUs, double ioUthreshold, double[] confidences) {
        IoUs = ioUs;
        IoUthreshold = ioUthreshold;
        this.confidences = confidences;
        prepare();
    }

    /**
     * Constructs Metrics directly from TP, FP, FN counts.
     * Used when metrics are precomputed.
     * 
     * @param tp true positive count
     * @param fp false positive count
     * @param fn false negative count
     */
    public Metrics(int tp, int fp, int fn){
        this.IoUs = null;
        this.IoUthreshold = Double.NaN;
        this.confidences = null;

        this.tp = tp;
        this.fp = fp;
        this.fn = fn;
    }
    /**
     * Adds another Metrics' counts to this instance.
     * Aggregates TP, FP, FN across multiple analyses.
     * 
     * @param other Metrics to accumulate
     */
    public void add(Metrics other){
        this.tp += other.tp;
        this.fp += other.fp;
        this.fn += other.fn;
    }

    /**
     * Initializes metrics computation based on IoU threshold and data type.
     * Routes to pixelAnalysis() or objectAnalysis() depending on threshold sign.
     */
    public void prepare(){
        if(IoUthreshold<0) pixelAnalysis();
        else objectAnalysis();
    }

    /**
     * Computes pixel-level metrics from the 2D histogram.
     * 
     * Logic:
     * - Interior (x>0, y>0): True Positive pixels (overlap between truth and test)
     * - Row 0 (y=0, x>0): False Negative pixels (truth-only pixels)
     * - Column 0 (x=0, y>0): False Positive pixels (test-only pixels)
     */
    protected void pixelAnalysis(){
        tp=0;
        fp=0;
        fn=0;
        for(int y=0;y<IoUs.getHeight();y++){
            for(int x=0;x<IoUs.getWidth();x++){
                double val = IoUs.get(x,y);
                if(x>0&&y>0) tp+=val;
                if(x==0&&y>0) fp+=val;
                if(y==0&&x>0) fn+=val;
            }
        }
    }

    /**
     * Computes object-level metrics from the IoU matrix.
     * 
     * Algorithm:
     * 1. For each test object (row y): count matches with IoU >= threshold
     *    - 0 matches: FP (false positive)
     *    - 1 match: TP (true positive)
     *    - 2+ matches: TP (over-segmentation: multiple truth objects detected as one)
     * 2. For each truth object (column x): count matches with IoU >= threshold
     *    - 0 matches: FN (false negative)
     * 3. Store object labels if confidence scores provided
     * 
     * @return array with {tp, fp, fn, precision, sensitivity, jaccard, dsc}
     */
    protected void objectAnalysis(){
        tp=0;
        fp=0;
        fn=0;
        float[] row=new float[IoUs.getWidth()+1];
        float[] col = new float[IoUs.getHeight()+1];
        labels = (confidences!=null)? new int[confidences.length]:null;
        
        // Process each test object (row in IoU matrix)
        for(int y=1;y<IoUs.getHeight();y++){
            IoUs.getRow(0,y,row,IoUs.getWidth());
            int found=0;
            for(int x=1;x<IoUs.getWidth();x++){
                if(row[x]>=IoUthreshold) found++;
            }
            switch (found){
                case 0: if(row[0]>0)fp++;
                        if(labels!=null) labels[y-1]=0;
                        break;
                case 1: tp++;
                        if(labels!=null) labels[y-1]=1;
                        break;
                default: tp++;
                        break;
            }
        }
        row=null;
        
        // Process each truth object (column in IoU matrix)
        for(int x=1;x<IoUs.getWidth();x++){
            IoUs.getColumn(x,0,col,IoUs.getHeight());
            int found=0;
            for(int y=1;y<IoUs.getHeight();y++){
                if(col[y]>IoUthreshold) found++;
            }
            if(found==0 && col[0]>0) fn++;
        }

    }
    /**
     * Returns the true positive count.
     * 
     * @return number of correctly detected objects
     */
    public int getTP() { return tp;  }
    
    /**
     * Returns the false positive count.
     * 
     * @return number of incorrectly detected objects
     */
    public int getFP() { return fp; }
    
    /**
     * Returns the false negative count.
     * 
     * @return number of undetected objects
     */
    public int getFN() { return fn; }
    
    /**
     * Returns the IoU threshold used for object-level analysis.
     * 
     * @return IoU threshold value
     */
    public double getIoUthreshold() { return IoUthreshold; }
    
    /**
     * Returns the object-level labels computed from confidence scores.
     * Only populated if confidences array was provided.
     * 
     * @return array where labels[i] = 1 if confident object, 0 if unmatched
     */
    public int[] getLabels() { return labels; }
    
    /**
     * Computes precision: TP / (TP + FP).
     * Returns NaN if denominator is zero.
     * 
     * @return precision value [0, 1]
     */
    double getPrecision(){return (tp + fp > 0) ? ((double)tp) / (double)(tp + fp) : Double.NaN; }
    
    /**
     * Computes sensitivity (recall): TP / (TP + FN).
     * Returns NaN if denominator is zero.
     * 
     * @return sensitivity/recall value [0, 1]
     */
    double getSensitivity(){return (tp + fn > 0) ? ((double)tp) / (double)(tp + fn) : Double.NaN;}
    
    /**
     * Computes Jaccard Index: TP / (TP + FP + FN).
     * Intersection over Union for object level.
     * Returns NaN if denominator is zero.
     * 
     * @return Jaccard Index value [0, 1]
     */
    double getJaccardIndex(){return (tp + fp + fn > 0) ? ((double)tp) / (double)(tp + fp+ fn) : Double.NaN;}
    
    /**
     * Computes F1-measure: (2 * TP) / (2 * TP + FP + FN).
     * Harmonic mean of precision and sensitivity.
     * Returns NaN if denominator is zero.
     * 
     * @return F1-measure value [0, 1]
     */
    double getF1measure(){ return (tp + fp + fn > 0) ? (2.0 * tp) / (double)(2 * tp + fp + fn) : Double.NaN;}
    
    /**
     * Computes Average Precision from precision-recall curve.
     * Only works if confidence scores were provided.
     * 
     * @return AP value [0, 1], or NaN if no confidence scores
     */
    double getAveragePrecision(){
        List<Map<String, Double>> precisionRecallCurve = calculatePrecisionRecallCurve();
        double ap=0;
        for (Map<String, Double> point : precisionRecallCurve) {
                    ap+=point.get("precision");
        }
        if(precisionRecallCurve.isEmpty()) return Double.NaN;
        ap /= precisionRecallCurve.size();
        return ap;
    }
    
    /**
     * Computes AP as product of precision and sensitivity.
     * Alternative AP calculation.
     * 
     * @return precision × sensitivity
     */
    public double getAP(){
        return getPrecision() * getSensitivity();
    }
    
    /**
     * Shorthand for sensitivity (recall).
     * 
     * @return sensitivity value
     */
    public double getRecall(){
        return getSensitivity();
    }


    /**
     * Calculates precision-recall curve from confidence scores and labels.
     * Sorts objects by confidence (descending) and computes precision/recall at each threshold.
     * 
     * Algorithm:
     * 1. Pair each confidence score with its label (1=TP, 0=FP)
     * 2. Sort by confidence descending
     * 3. Iterate through sorted list, accumulating TP and FP
     * 4. Compute precision = TP/(TP+FP) and recall = TP/numPositives
     * 
     * @return list of maps with keys: "threshold" (confidence), "precision", "recall"
     */
    public List<Map<String, Double>> calculatePrecisionRecallCurve() {
        if(confidences==null) return Collections.emptyList();
        if(labels==null) return Collections.emptyList();
        if(confidences.length!=labels.length) throw new IllegalStateException("confidences and labels must have the same length");
        
        // Pair each score with its label and sort by score descending
        List<ScoreLabel> scoreLabelList = new ArrayList<>();
        for (int i = 0; i < confidences.length; i++) {
            scoreLabelList.add(new ScoreLabel(confidences[i], labels[i]));
        }
        scoreLabelList.sort((a, b) -> Double.compare(b.score, a.score));

        int tp = 0, fp = 0;
        int numPositives = (int) Arrays.stream(labels).filter(l -> l == 1).count();

        List<Map<String, Double>> prCurve = new ArrayList<>();

        // Compute precision and recall at each threshold
        for (ScoreLabel sl : scoreLabelList) {
            if (sl.label == 1) {
                tp++;
            } else {
                fp++;
            }

            double precision = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
            double recall = (numPositives > 0) ? (double) tp / numPositives : 0.0;

            Map<String, Double> point = new HashMap<>();
            point.put("threshold", sl.score);
            point.put("precision", precision);
            point.put("recall", recall);

            prCurve.add(point);
        }

        return prCurve;
    }

    /**
     * Container for a confidence score with its corresponding label.
     */
    static class ScoreLabel {
        double score;
        int label;

        ScoreLabel(double score, int label) {
            this.score = score;
            this.label = label;
        }
    }

    /**
     * Returns all metrics as an array.
     * 
     * @return array with {TP, FP, FN, Precision, Sensitivity, Jaccard, F1-measure}
     */
    public Object[] getMetricsArray() {

        return new Object[]{
                tp,
                fp,
                fn,
                getPrecision(),
                getSensitivity(),
                getJaccardIndex(),
                getF1measure()
        };
    }
}
