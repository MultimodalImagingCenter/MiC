package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.measure.ResultsTable;
import ij.process.Blitter;

import java.awt.*;

public class AnalysisResultDisplay {
    private final ImagePlus truthMaskIP;
    private final ImagePlus testMaskIP;
    private ImagePlus plotHyperStack;

    private Metrics[] accumulatedMetrics;
    private double[] thresholds;

    public AnalysisResultDisplay(ImagePlus truthMaskIP, ImagePlus testMaskIP){
        this.truthMaskIP = truthMaskIP;
        this.testMaskIP = testMaskIP;
    }

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

    public void showPlots(){
        if(plotHyperStack == null) return;
        plotHyperStack.show();
        plotHyperStack.getCanvas().setMagnification(1);
    }

    public boolean hasPlots(){
        return plotHyperStack != null;
    }

    private void createPlotHyperStack(int width, int height){
        plotHyperStack = IJ.createHyperStack("Plots " + truthMaskIP.getTitle() + "__VS__" + testMaskIP.getTitle(), width, height, truthMaskIP.getNChannels(), truthMaskIP.getNSlices(), truthMaskIP.getNFrames(), 24);
    }

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

    private void computeStats(double[] tps, double[] fps, double[] fns, double[] precision, double[] sensitivity, double[] jaccardIndex, double[] fmeasure) {
        for (int index = 0; index < tps.length; index++) {
//        STATISTICS
            precision[index] = tps[index] / (tps[index] + fps[index]);
            sensitivity[index] = tps[index] / (tps[index] + fns[index]);
            jaccardIndex[index] = tps[index] / (tps[index] + fns[index] + fps[index]);
            fmeasure[index] = 2 * precision[index] * sensitivity[index] / (precision[index] + sensitivity[index]);
        }

    }

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
}
