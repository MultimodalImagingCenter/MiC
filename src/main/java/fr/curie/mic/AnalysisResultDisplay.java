package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.measure.ResultsTable;
import ij.process.Blitter;

import java.awt.*;
import ij.gui.Roi;
import java.awt.Rectangle;

/**
 * Manages display and output of mask analysis results.
 * <p>
 * This class handles the accumulation and visualization of metrics from segmentation mask comparisons,
 * including precision-recall curves, F-measure plots, and IoU threshold analysis.
 * It also manages correspondence tables between truth and test objects.
 * </p>
 */
public class AnalysisResultDisplay {
    private final ImagePlus truthMaskIP;
    private final ImagePlus testMaskIP;
    private ImagePlus plotHyperStack;

    private Metrics[] accumulatedMetrics;
    private double[] thresholds;
    private ResultsTable resultsTable;
    private ResultsTable thresholdResultsTable;
    private ResultsTable correspondenceTable;


    /**
     * Constructs an AnalysisResultDisplay for the given mask images.
     *
     * @param truthMaskIP the ground truth segmentation mask
     * @param testMaskIP the test segmentation mask to compare
     */
    public AnalysisResultDisplay(ImagePlus truthMaskIP, ImagePlus testMaskIP){
        this.truthMaskIP = truthMaskIP;
        this.testMaskIP = testMaskIP;

        createTables();
    }

    /**
     * Returns the main results table containing pixel and object metrics.
     *
     * @return the main results table
     */
    public ResultsTable getResultsTable(){
        return resultsTable;
    }

    /**
     * Returns the threshold-based results table for IoU analysis.
     *
     * @return the threshold results table
     */
    public ResultsTable getThresholdResultsTable(){
        return thresholdResultsTable;
    }

    /**
     * Returns the correspondence table mapping truth and test objects.
     *
     * @return the correspondence table
     */
    public ResultsTable getCorrespondenceTable(){
        return correspondenceTable;
    }

    /**
     * Adds a plot from an analysis result to the plot hyperstack.
     *
     * @param result the analysis result containing the plot
     * @param baseTitle the base title for the plot
     */
    public void addPlot(AnalysisResult result, String baseTitle){
        Plot plot = result.createPlot(result.getPlotTitle(baseTitle));
        if(plotHyperStack == null) createPlotHyperStack(plot.getProcessor().getWidth(), plot.getProcessor().getHeight());
        int c = result.getChannel() + 1;
        int z = result.getSlice();
        int t = result.getFrame() + 1;
        int index = plotHyperStack.getStackIndex(c, z, t);
        plotHyperStack.getStack().getProcessor(index).copyBits(plot.getProcessor(), 0, 0, Blitter.COPY);
        plotHyperStack.getStack().setSliceLabel(result.getPlotTitle(baseTitle), index);
    }

    /**
     * Displays the plot hyperstack if it exists.
     */
    public void showPlots(){
        if(plotHyperStack == null) return;
        plotHyperStack.show();
        plotHyperStack.getCanvas().setMagnification(1);
    }

    /**
     * Checks if there are plots available for display.
     *
     * @return true if the plot hyperstack has been created, false otherwise
     */
    public boolean hasPlots(){
        return plotHyperStack != null;
    }

    /**
     * Creates a hyperstack for storing plots organized by channel, slice, and frame.
     *
     * @param width the width of each plot image
     * @param height the height of each plot image
     */
    private void createPlotHyperStack(int width, int height){
        plotHyperStack = IJ.createHyperStack("Plots " + truthMaskIP.getTitle() + "__VS__" + testMaskIP.getTitle(), width, height, truthMaskIP.getNChannels(), truthMaskIP.getNSlices(), truthMaskIP.getNFrames(), 24);
    }

    /**
     * Accumulates metrics from an analysis result into the accumulated metrics array.
     * Initializes the accumulator on first call.
     *
     * @param result the analysis result to accumulate
     */
    public void accumulate(AnalysisResult result){
        Metrics[] curveMetrics = result.getCurveMetrics();

        if(curveMetrics == null) return;

        if(accumulatedMetrics == null){
            thresholds = result.getThresholds();
            accumulatedMetrics = new Metrics[curveMetrics.length];

            for(int i = 0; i < accumulatedMetrics.length; i++){
                accumulatedMetrics[i] = new Metrics(0, 0, 0);
            }
        }

        for(int i = 0; i < curveMetrics.length; i++){
            accumulatedMetrics[i].add(curveMetrics[i]);
        }
    }

    /**
     * Computes statistical metrics (precision, sensitivity, Jaccard index, F-measure) from TP/FP/FN counts.
     *
     * @param tps the true positives for each threshold
     * @param fps the false positives for each threshold
     * @param fns the false negatives for each threshold
     * @param precision output array for precision scores
     * @param sensitivity output array for sensitivity (recall) scores
     * @param jaccardIndex output array for Jaccard index scores
     * @param fmeasure output array for F-measure scores
     */
    private void computeStats(double[] tps, double[] fps, double[] fns, double[] precision, double[] sensitivity, double[] jaccardIndex, double[] fmeasure) {
        for (int index = 0; index < tps.length; index++) {
//        STATISTICS
            precision[index] = tps[index] / (tps[index] + fps[index]);
            sensitivity[index] = tps[index] / (tps[index] + fns[index]);
            jaccardIndex[index] = tps[index] / (tps[index] + fns[index] + fps[index]);
            fmeasure[index] = 2 * precision[index] * sensitivity[index] / (precision[index] + sensitivity[index]);
        }

    }

    /**
     * Generates and displays a summary graph showing precision, sensitivity, Jaccard index, and F-measure
     * across all IoU thresholds using accumulated metrics.
     */
    public void showSummaryGraph() {

        if(accumulatedMetrics == null) return;
        double[] precision = new double[thresholds.length];
        double[] sensitivity = new double[thresholds.length];
        double[] jaccard = new double[thresholds.length];
        double[] fmeasure = new double[thresholds.length];
        for(int i = 0; i < accumulatedMetrics.length; i++){
            Metrics m = accumulatedMetrics[i];
            precision[i] = m.getPrecision();
            sensitivity[i] = m.getSensitivity();
            jaccard[i] = m.getJaccardIndex();
            fmeasure[i] = m.getF1measure();
        }
        IJ.log("add graphs");
        Plot plot = new Plot("Global IoU Summary", "overlap threshold", "score");
        //add precision
        plot.setColor(Color.RED);
        plot.add("line", thresholds, precision);
        String labels = "precision (tp/(tp+fp))";
        //add sensitivity
        plot.setColor(Color.GREEN);
        plot.add("line", thresholds, sensitivity);
        labels += "\tsensitivity/recall (tp/(tp+fn))";
        //add jaccard index
        plot.setColor(Color.BLACK);
        plot.add("line", thresholds, jaccard);
        labels += "\tjaccard index (tp/(tp+fp+fn))";
        //add fmeasure
        plot.setColor(Color.BLUE);
        plot.add("line", thresholds, fmeasure);
        labels += "\tfmeasure ((2*precision*sensitivity)/(precision+sensitivity))";
        //add legend
        plot.addLegend(labels);
        plot.setLimits(thresholds[0], thresholds[thresholds.length - 1], 0, 1.1);
        PlotWindow pw = plot.show();

        ResultsTable rt = new ResultsTable();
        for (int i = 0; i < thresholds.length; i++) {
            if (i != 0) rt.incrementCounter();
            addMetricToTable(rt, accumulatedMetrics[i], thresholds[i]);
        }
        rt.show(testMaskIP.getTitle() + " sum of all objects");

    }

    /**
     * Adds a metric entry to the given results table.
     *
     * @param rt the results table to add to
     * @param metric the metrics object containing TP, FP, FN, and derived statistics
     * @param threshold the IoU threshold (or -1 if not threshold-based)
     */
    private void addMetricToTable(ResultsTable rt, Metrics metric, double threshold){
        if(threshold >= 0)
            rt.addValue("Object IoU threshold", threshold);

        rt.addValue("Object TP", metric.getTP());
        rt.addValue("Object FP", metric.getFP());
        rt.addValue("Object FN", metric.getFN());
        rt.addValue("Object Precision", metric.getPrecision());
        rt.addValue("Object Recall/Sensitivity", metric.getSensitivity());
        rt.addValue("Object Jaccard Index", metric.getJaccardIndex());
        rt.addValue("Object F-measure", metric.getF1measure());
    }

    /**
     * Initializes or retrieves the three main results tables from ImageJ.
     * Creates new tables if they don't exist, or retrieves existing ones.
     */
    private void createTables(){
        ResultsTable rt = ResultsTable.getResultsTable("Mask comparison results");

        if(rt == null){
            resultsTable = new ResultsTable();
        }else{
            resultsTable = rt;
            resultsTable.incrementCounter();
        }

        ResultsTable rt2 = ResultsTable.getResultsTable("Mask comparison Object with IoU thresholds");

        if(rt2 == null){
            thresholdResultsTable = new ResultsTable();
        }else{
            thresholdResultsTable = rt2;
            thresholdResultsTable.incrementCounter();
        }

        ResultsTable rt3 = ResultsTable.getResultsTable("Objects correspondences");

        if(rt3 == null){
            correspondenceTable = new ResultsTable();
        }else{
            correspondenceTable = rt3;
            correspondenceTable.incrementCounter();
        }
    }

    /**
     * Calculates statistics and adds a single row to the main results table.
     * Computes precision, recall, Jaccard index, and F-measure from TP/FP/FN counts.
     *
     * @param method comparison method name ("Pixel", "Object", etc.)
     * @param tp true positives
     * @param fp false positives
     * @param fn false negatives
     */
    private void addToResultTable(String method, double tp, double fp, double fn) {
        //STATISTICS
        double precision = tp / (tp + fp);
        double recall = tp / (tp + fn);
        double jaccardIndex = tp / (tp + fn + fp);
        double fMeasure = 2 * precision * recall / (precision + recall);

        //ADD TO RESULT TABLE
        addToResultTable(resultsTable, method, tp, fp, fn, precision, recall, jaccardIndex, fMeasure, -1);

    }

    /**
     * Internal method to add metric data to a results table.
     *
     * @param resultsTable the table to update
     * @param method the comparison method name ("Pixel", "Object", etc.)
     * @param tp true positives count
     * @param fp false positives count
     * @param fn false negatives count
     * @param precision calculated precision value
     * @param sensitivity calculated sensitivity value
     * @param jaccardIndex calculated Jaccard index
     * @param fmeasure calculated F-measure
     * @param threshold the IoU threshold (or -1 if not applicable)
     */
    private void addToResultTable(ResultsTable resultsTable, String method, double tp, double fp, double fn, double precision, double sensitivity, double jaccardIndex, double fmeasure, double threshold) {

        //ADD TO RESULT TABLE
        if (threshold >= 0) resultsTable.addValue(method + " IoU threshold", threshold);
        resultsTable.addValue(method + " TP", tp);
        resultsTable.addValue(method + " FP", fp);
        resultsTable.addValue(method + " FN", fn);
        resultsTable.addValue(method + " Precision", precision);/*(TP/Positives)*/
        resultsTable.addValue(method + " Recall/Sensitivity", sensitivity);/*(TP/Truth)*/
        resultsTable.addValue(method + " Jaccard Index", jaccardIndex);/*(TP/(TP+FN+FP))*/
        resultsTable.addValue(method + " F-measure", fmeasure);/*(2*Precision/(precision+recall))*/

    }
    /*public void addMetric(String method, Metrics metrics){
        addToResultTable(
                resultsTable,
                method,
                metrics.getTP(),
                metrics.getFP(),
                metrics.getFN(),
                metrics.getPrecision(),
                metrics.getSensitivity(),
                metrics.getJaccardIndex(),
                metrics.getF1measure(),
                -1
        );
    }*/

    /**
    * Adds metrics to the main results table for the given method.
    *
    * @param method the comparison method name
    * @param metrics the metrics object with TP, FP, FN, and derived statistics
    */
    public void addMetric(String method, Metrics metrics){
       addMetric(resultsTable, method, metrics, -1);
    }

    /**
    * Adds metrics to the threshold results table with a specific IoU threshold.
    *
    * @param method the comparison method name
    * @param metrics the metrics object with TP, FP, FN, and derived statistics
    * @param threshold the IoU threshold used
    */
    public void addMetric(String method, Metrics metrics, double threshold){
       addMetric(thresholdResultsTable, method, metrics, threshold);
    }

    /**
    * Internal method to add metrics to the specified results table.
    *
    * @param rt the results table to update
    * @param method the comparison method name
    * @param metrics the metrics object
    * @param threshold the IoU threshold (or -1 if not applicable)
    */
    private void addMetric(ResultsTable rt, String method, Metrics metrics, double threshold){
        if(threshold >= 0) rt.addValue(method + " IoU threshold", threshold);

        rt.addValue(method + " TP", metrics.getTP());
        rt.addValue(method + " FP", metrics.getFP());
        rt.addValue(method + " FN", metrics.getFN());
        rt.addValue(method + " Precision", metrics.getPrecision());
        rt.addValue(method + " Recall/Sensitivity", metrics.getSensitivity());
        rt.addValue(method + " Jaccard Index", metrics.getJaccardIndex());
        rt.addValue(method + " F-measure", metrics.getF1measure());
    }

    /**
     * Records the main analysis context (truth/test images, channel, frame, slice, parameters).
     *
     * @param channel the channel index
     * @param frame the frame index
     * @param slice the slice/depth index
     * @param minDist minimum distance to border threshold
     * @param minSize minimum object size threshold
     */
    public void addMainContext(int channel, int frame, int slice, double minDist, double minSize){
        resultsTable.addValue("Truth image", truthMaskIP.getTitle());
        resultsTable.addValue("Test image", testMaskIP.getTitle());
        resultsTable.addValue("Channel", channel);
        resultsTable.addValue("Frame", frame);
        resultsTable.addValue("Slice number", slice);
        resultsTable.addValue("minimum distance to border", minDist);
        resultsTable.addValue("minimum size of objects", minSize);
    }

    /**
     * Records the object counts for truth and test images.
     *
     * @param truthObjects number of objects in truth mask
     * @param testObjects number of objects in test mask
     */
    public void addMainObjectCounts(int truthObjects, int testObjects){
        resultsTable.addValue("Truth objects", truthObjects);
        resultsTable.addValue("Test objects", testObjects);
    }

    /**
     * Records mean average precision scores from analysis result.
     *
     * @param result the analysis result containing mean score calculations
     */
    public void addMeanScores(AnalysisResult result){
        resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)+FN(IoU)))", result.getMeanJaccard());
        resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)))", result.getMeanPrecision());
    }

    /**
     * Records context information for threshold-based analysis.
     *
     * @param channel the channel index
     * @param frame the frame index
     * @param slice the slice/depth index
     * @param truthObjects number of truth objects
     * @param testObjects number of test objects
     */
    public void addThresholdContext(int channel, int frame, int slice, int truthObjects, int testObjects){
        thresholdResultsTable.addValue("Truth image", truthMaskIP.getTitle());
        thresholdResultsTable.addValue("Test image", testMaskIP.getTitle());
        thresholdResultsTable.addValue("channel", channel);
        thresholdResultsTable.addValue("frame", frame);
        thresholdResultsTable.addValue("Slice number", slice);
        thresholdResultsTable.addValue("Truth objects", truthObjects);
        thresholdResultsTable.addValue("Test objects", testObjects);
    }

    /**
     * Adds average precision metric to threshold results table.
     *
     * @param metric the metric object containing AP calculation
     */
    public void addThresholdAP(Metrics metric){
        thresholdResultsTable.addValue("AP = precision*sensitivity", metric.getAP());
    }

    /**
     * Advances the main results table counter to a new row.
     */
    public void incrementMainTable(){
        resultsTable.incrementCounter();
    }

    /**
     * Advances the threshold results table counter to a new row.
     */
    public void incrementThresholdTable(){
        thresholdResultsTable.incrementCounter();
    }

    /**
     * Advances the correspondence table counter to a new row.
     */
    public void incrementCorrespondenceTable(){
        correspondenceTable.incrementCounter();
    }

    /**
     * Deletes the last row from all results tables.
     */
    public void deleteLastRows(){
        if(resultsTable.getCounter() > 0) resultsTable.deleteRow(resultsTable.getCounter() - 1);
        if(thresholdResultsTable.getCounter() > 0) thresholdResultsTable.deleteRow(thresholdResultsTable.getCounter() - 1);
        if(correspondenceTable.getCounter() > 0) correspondenceTable.deleteRow(correspondenceTable.getCounter() - 1);
    }

    /**
     * Displays all results tables in ImageJ windows.
     *
     * @param pixelObjectMethod whether to show the threshold results table
     * @param objectMethod whether object method was used
     * @param showCorrespondances whether to show the correspondence table
     */
    public void showTables(boolean pixelObjectMethod, boolean objectMethod, boolean showCorrespondances){
        resultsTable.show("Mask comparison results");
        if(pixelObjectMethod) thresholdResultsTable.show("Mask comparison Object with IoU thresholds");
        if((pixelObjectMethod || objectMethod) && showCorrespondances) correspondenceTable.show("Objects correspondences");
    }

    /**
     * Records object correspondences between truth and test masks, including spatial location and overlap metrics.
     *
     * @param name the image name
     * @param channel the channel index
     * @param time the frame index
     * @param slice the slice index
     * @param rois the truth object ROIs
     * @param correspondance array mapping test object indices to truth object indices
     * @param overlapPercent array of IoU overlap percentages
     * @param valid array indicating whether each object is within validation distance
     * @param imageWidth the image width for distance-to-border calculations
     * @param imageHeight the image height for distance-to-border calculations
     */
    public void addCorrespondence(String name, int channel, int time, int slice, Roi[] rois, int[] correspondance, double[] overlapPercent, boolean[] valid, int imageWidth, int imageHeight){
        for(int i = 0; i < correspondance.length; i++){
            if(i != 0) correspondenceTable.incrementCounter();

            correspondenceTable.addValue("image", name);
            correspondenceTable.addValue("channel", channel);
            correspondenceTable.addValue("frame", time);
            correspondenceTable.addValue("slice", slice);
            correspondenceTable.addValue("roi truth", i);
            correspondenceTable.addValue("centerX", rois[i].getBounds().getCenterX());
            correspondenceTable.addValue("centerY", rois[i].getBounds().getCenterY());

            Rectangle roi = rois[i].getBounds();

            double dist = Math.min(
                    roi.getCenterX(),
                    imageWidth - roi.getCenterX()
            );

            dist = Math.min(
                    dist,
                    Math.min(
                            roi.getCenterY(),
                            imageHeight - roi.getCenterY()
                    )
            );

            correspondenceTable.addValue("center distance to border", dist);
            correspondenceTable.addValue("distance validated (1 for true)", valid[i] ? 1 : 0);
            correspondenceTable.addValue("corresponding roi test", correspondance[i]);
            correspondenceTable.addValue("IoU", overlapPercent[i]);
        }
        if(correspondance.length > 0) correspondenceTable.incrementCounter();
    }


}
