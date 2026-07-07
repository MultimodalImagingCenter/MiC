package fr.curie.mic;

public class AnalysisResult {
    private Metrics pixelMetrics;
    private Metrics objectMetrics;
    private double[] thresholds;
    private Metrics[] curveMetrics;

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
        for(Metrics m : curveMetrics){
            sum += m.getAveragePrecision();
        }
        return sum / curveMetrics.length;
    }
}
