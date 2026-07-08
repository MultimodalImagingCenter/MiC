package fr.curie.mic;

import ij.gui.Plot;
import java.awt.Color;

public class AnalysisResult {
    private Metrics pixelMetrics;
    private Metrics objectMetrics;
    private double[] thresholds;
    private Metrics[] curveMetrics;

    private int channel;
    private int frame;
    private int slice;

    public Metrics getPixelMetrics() {
        return pixelMetrics;
    }

    public void setPixelMetrics(Metrics pixelMetrics) {
        this.pixelMetrics = pixelMetrics;
    }

    public Metrics getObjectMetrics() {
        return objectMetrics;
    }

    public void setObjectMetrics(Metrics objectMetrics) {
        this.objectMetrics = objectMetrics;
    }

    public Metrics[] getCurveMetrics() {
        return curveMetrics;
    }

    public void setCurveMetrics(Metrics[] curveMetrics) {
        this.curveMetrics = curveMetrics;
    }

    public double[] getThresholds() {
        return thresholds;
    }

    public void setThresholds(double[] thresholds) {
        this.thresholds = thresholds;
    }
    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }

    public int getFrame() { return frame; }
    public void setFrame(int frame) { this.frame = frame; }

    public int getSlice() { return slice; }
    public void setSlice(int slice) { this.slice = slice; }

    public double getMeanPrecision() {
        if(curveMetrics == null || curveMetrics.length == 0) return Double.NaN;
        double sum = 0;
        for(Metrics m : curveMetrics){
            sum += m.getPrecision();
        }
        return sum / curveMetrics.length;
    }
    public double getMeanJaccard(){
        if(curveMetrics == null || curveMetrics.length == 0) return Double.NaN;
        double sum = 0;
        for(Metrics m : curveMetrics){
            sum += m.getJaccardIndex();
        }
        return sum / curveMetrics.length;
    }
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

    public double[] getPrecisionCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getPrecision();
        }

        return values;
    }

    public double[] getRecallCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getSensitivity();
        }

        return values;
    }
    public double[] getJaccardCurve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getJaccardIndex();
        }

        return values;
    }
    public double[] getF1Curve(){
        double[] values = new double[curveMetrics.length];

        for(int i = 0; i < curveMetrics.length; i++){
            values[i] = curveMetrics[i].getF1measure();
        }

        return values;
    }
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
}
