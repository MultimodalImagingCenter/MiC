package fr.curie.mic;

import ij.gui.Plot;

import java.awt.Color;

/**
 * Aggregates analysis results across multiple IoU thresholds, planes, and time points.
 * Stores both pixel-level and object-level metrics, generates performance curves and plots.
 * 
 * Supports multiple mAP definitions following Hirling et al, Nature Methods 2022:
 * - mAP1: Average AP across classes (fixed IoU, requires confidence scores)
 * - mAP2: Average Jaccard Index across thresholds
 * - mAP3: Average Precision across thresholds
 * - mAP4: Average (Precision × Recall) across thresholds
 * - mAP5: Average Jaccard Index across slices
 */
public class AnalysisResult {
    private Metrics pixelMetrics;
    private Metrics objectMetrics;
    private double[] thresholds;
    private Metrics[] curveMetrics;

    private int channel;
    private int frame;
    private int slice;

    /**
     * Returns pixel-level metrics (TP, FP, FN at pixel granularity).
     * 
     * @return Metrics computed from pixel overlaps
     */
    public Metrics getPixelMetrics() {
        return pixelMetrics;
    }

    /**
     * Sets pixel-level metrics.
     * 
     * @param pixelMetrics Metrics from pixel analysis
     */
    public void setPixelMetrics(Metrics pixelMetrics) {
        this.pixelMetrics = pixelMetrics;
    }

    /**
     * Returns object-level metrics at fixed IoU threshold (typically 0.5).
     * 
     * @return Metrics computed from object-level matching
     */
    public Metrics getObjectMetrics() {
        return objectMetrics;
    }

    /**
     * Sets object-level metrics.
     * 
     * @param objectMetrics Metrics from object analysis
     */
    public void setObjectMetrics(Metrics objectMetrics) {
        this.objectMetrics = objectMetrics;
    }

    /**
     * Returns metrics computed at each IoU threshold.
     * 
     * @return array of Metrics objects, one per threshold
     */
    public Metrics[] getCurveMetrics() {
        return curveMetrics;
    }

    /**
     * Sets metrics for multiple thresholds.
     * 
     * @param curveMetrics array of Metrics
     */
    public void setCurveMetrics(Metrics[] curveMetrics) {
        this.curveMetrics = curveMetrics;
    }

    /**
     * Returns metrics for a specific threshold index.
     * 
     * @param index position in the threshold range
     * @return Metrics at that threshold
     */
    public Metrics getCurveMetric(int index){
        return curveMetrics[index];
    }

    /**
     * Returns the IoU thresholds used for curve metrics.
     * 
     * @return array of threshold values
     */
    public double[] getThresholds() {
        return thresholds;
    }

    /**
     * Sets the IoU thresholds.
     * 
     * @param thresholds array of IoU values
     */
    public void setThresholds(double[] thresholds) {
        this.thresholds = thresholds;
    }
    
    /**
     * Returns the channel index (0-based for multichannel images).
     * 
     * @return channel number
     */
    public int getChannel() { return channel; }
    
    /**
     * Sets the channel index.
     * 
     * @param channel channel number
     */
    public void setChannel(int channel) { this.channel = channel; }

    /**
     * Returns the time frame index (0-based for time series).
     * 
     * @return frame number
     */
    public int getFrame() { return frame; }
    
    /**
     * Sets the time frame index.
     * 
     * @param frame frame number
     */
    public void setFrame(int frame) { this.frame = frame; }

    /**
     * Returns the Z-slice index (0-based for 3D stacks).
     * 
     * @return slice number
     */
    public int getSlice() { return slice; }
    
    /**
     * Sets the Z-slice index.
     * 
     * @param slice slice number
     */
    public void setSlice(int slice) { this.slice = slice; }

    /**
     * Computes mean precision across all thresholds in the curve.
     * Useful for overall performance assessment.
     * 
     * @return average precision value, or NaN if no curve metrics
     */
    public double getMeanPrecision() {
        if(curveMetrics == null || curveMetrics.length == 0) return Double.NaN;
        double sum = 0;
        for(Metrics m : curveMetrics){
            sum += m.getPrecision();
        }
        return sum / curveMetrics.length;
    }
    
    /**
     * Computes mean Jaccard Index across all thresholds.
     * 
     * @return average Jaccard value, or NaN if no curve metrics
     */
    public double getMeanJaccard(){
        if(curveMetrics == null || curveMetrics.length == 0) return Double.NaN;
        double sum = 0;
        for(Metrics m : curveMetrics){
            sum += m.getJaccardIndex();
        }
        return sum / curveMetrics.length;
    }
    
    /**
     * Computes mAP (mean Average Precision) from curve metrics.
     * Averages AP computed at each threshold (mAP3 definition).
     * 
     * @return mAP value across thresholds, or NaN if no valid AP values
     */
    public double getMeanAveragePrecision(){
        if(curveMetrics == null || curveMetrics.length == 0) return Double.NaN;
        double sum = 0;
        int count = 0;
        for(Metrics m : curveMetrics){
            double ap = m.getAveragePrecision();
            if(!Double.isNaN(ap)){
                sum += ap;
                count++;
            }
        }

        return (count > 0) ? sum / count : Double.NaN;
    }

    /**
     * Extracts precision values across all thresholds.
     * 
     * @return array of precision values
     */
    public double[] getPrecisionCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getPrecision();
        }

        return values;
    }

    /**
     * Extracts recall (sensitivity) values across all thresholds.
     * 
     * @return array of recall values
     */
    public double[] getRecallCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getSensitivity();
        }

        return values;
    }
    
    /**
     * Extracts Jaccard Index values across all thresholds.
     * 
     * @return array of Jaccard values
     */
    public double[] getJaccardCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getJaccardIndex();
        }

        return values;
    }
    
    /**
     * Extracts F1-measure values across all thresholds.
     * 
     * @return array of F1-measure values
     */
    public double[] getF1Curve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getF1measure();
        }

        return values;
    }
    
    /**
     * Creates an ImageJ Plot with all metric curves.
     * X-axis: IoU threshold, Y-axis: metric value
     * 
     * Lines:
     * - Red: Precision (TP/(TP+FP))
     * - Green: Sensitivity/Recall (TP/(TP+FN))
     * - Black: Jaccard Index (TP/(TP+FP+FN))
     * - Blue: F1-measure (2*TP/(2*TP+FP+FN))
     * 
     * @param title plot title
     * @return ImageJ Plot object
     */
    public Plot createPlot(String title){
        Plot plot = new Plot(title, "overlap threshold", "score");

        plot.setColor(Color.RED);
        plot.add("line", thresholds, getPrecisionCurve());

        plot.setColor(Color.GREEN);
        plot.add("line", thresholds, getRecallCurve());

        plot.setColor(Color.BLACK);
        plot.add("line", thresholds, getJaccardCurve());

        plot.setColor(Color.BLUE);
        plot.add("line", thresholds, getF1Curve());

        String labels =
                "precision (tp/(tp+fp))" +
                        "\tsensitivity/recall (tp/(tp+fn))" +
                        "\tjaccard index (tp/(tp+fp+fn))" +
                        "\tfmeasure ((2*precision*sensitivity)/(precision+sensitivity))";

        plot.addLegend(labels);

        plot.setLimits(
                thresholds[0],
                thresholds[thresholds.length - 1],
                0,
                1.1
        );

        return plot;
    }
    
    /**
     * Creates a plot with auto-generated title including image coordinates.
     * 
     * @return ImageJ Plot object
     */
    public Plot createPlot(){
        return createPlot(
                "Metrics" +
                        " (C=" + channel +
                        ", T=" + frame +
                        ", Z=" + slice + ")"
        );
    }
    
    /**
     * Generates a plot title with image coordinates appended.
     * 
     * @param baseTitle base title text
     * @return formatted title with [C-T-Z] coordinates
     */
    public String getPlotTitle(String baseTitle){
        return baseTitle + " [C" + channel + "-T" + frame + "-Z" + slice + "]";
    }

    /**
     * Factory method to create an AnalysisResult from curve metrics and coordinates.
     * 
     * @param curveMetrics metrics at each threshold
     * @param thresholds IoU thresholds
     * @param channel channel index
     * @param frame frame index
     * @param slice slice index
     * @return new AnalysisResult instance
     */
    public static AnalysisResult fromCurveMetrics(Metrics[] curveMetrics, double[] thresholds, int channel, int frame, int slice){
        AnalysisResult result = new AnalysisResult();
        result.setCurveMetrics(curveMetrics);
        result.setThresholds(thresholds);
        result.setChannel(channel);
        result.setFrame(frame);
        result.setSlice(slice);
        return result;
    }
}
