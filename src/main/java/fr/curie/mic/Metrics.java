package fr.curie.mic;

import ij.IJ;
import ij.process.ImageProcessor;

import java.util.*;

public class Metrics {
    private final double IoUthreshold;
    private final ImageProcessor IoUs;

    private int tp;
    private int fn;
    private int fp;

    private final double[] confidences;
    private int[] labels;

    public Metrics(ImageProcessor ioUs, double ioUthreshold, double[] confidences) {
        IoUs = ioUs;
        IoUthreshold = ioUthreshold;
        this.confidences = confidences;
        prepare();
    }
    public Metrics(int tp, int fp, int fn){
        this.IoUs = null;
        this.IoUthreshold = Double.NaN;
        this.confidences = null;

        this.tp = tp;
        this.fp = fp;
        this.fn = fn;
    }
    public void add(Metrics other){
        this.tp += other.tp;
        this.fp += other.fp;
        this.fn += other.fn;
    }

    public void prepare(){
        if(IoUthreshold<0) pixelAnalysis();
        else objectAnalysis();
    }

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
     * computes the metrics with corresponding threshold
     * @return array with {tp,fp,fn, precision,sensitivity,jaccard,dsc}
     */
    protected void objectAnalysis(){
        tp=0;
        fp=0;
        fn=0;
        float[] row=new float[IoUs.getWidth()+1];
        float[] col = new float[IoUs.getHeight()+1];
        labels = (confidences!=null)? new int[confidences.length]:null;
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
                default: tp++; //IJ.log("object "+y + "in test as "+ found +" correspondances");
                        break;
            }
        }
        row=null;
        for(int x=1;x<IoUs.getWidth();x++){
            IoUs.getColumn(x,0,col,IoUs.getHeight());
            int found=0;
            for(int y=1;y<IoUs.getHeight();y++){
                if(col[y]>IoUthreshold) found++;
            }
            if(found==0 && col[0]>0) fn++;
        }

    }
    public int getTP() { return tp;  }
    public int getFP() { return fp; }
    public int getFN() { return fn; }
    public double getIoUthreshold() { return IoUthreshold; }
    public int[] getLabels() { return labels; }
    double getPrecision(){return (tp + fp > 0) ? ((double)tp) / (double)(tp + fp) : Double.NaN; }
    double getSensitivity(){return (tp + fn > 0) ? ((double)tp) / (double)(tp + fn) : Double.NaN;}
    double getJaccardIndex(){return (tp + fp + fn > 0) ? ((double)tp) / (double)(tp + fp+ fn) : Double.NaN;}
    double getF1measure(){ return (tp + fp + fn > 0) ? (2.0 * tp) / (double)(2 * tp + fp + fn) : Double.NaN;}
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
    public double getAP(){
        return getPrecision() * getSensitivity();
    }
    public double getRecall(){
        return getSensitivity();
    }


    public List<Map<String, Double>> calculatePrecisionRecallCurve() {
        if(confidences==null) return Collections.emptyList();
        if(labels==null) return Collections.emptyList();
        if(confidences.length!=labels.length) throw new IllegalStateException("confidences and labels must have the same length");
        // Associer chaque score à son label et trier par ordre décroissant de score
        List<ScoreLabel> scoreLabelList = new ArrayList<>();
        for (int i = 0; i < confidences.length; i++) {
            scoreLabelList.add(new ScoreLabel(confidences[i], labels[i]));
        }
        scoreLabelList.sort((a, b) -> Double.compare(b.score, a.score));

        int tp = 0, fp = 0;
        int numPositives = (int) Arrays.stream(labels).filter(l -> l == 1).count();

        List<Map<String, Double>> prCurve = new ArrayList<>();

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

    static class ScoreLabel {
        double score;
        int label;

        ScoreLabel(double score, int label) {
            this.score = score;
            this.label = label;
        }
    }

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
