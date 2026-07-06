package fr.curie.mic;

import ij.IJ;
import ij.process.ImageProcessor;

import java.util.*;

public class Metrics {
    double IoUthreshold;
    int tp;
    int fn;
    int fp;

    ImageProcessor IoUs;
    double[] confidences;
    int[] labels;

    public Metrics(ImageProcessor ioUs, double ioUthreshold, double[] confidences) {
        IoUs = ioUs;
        IoUthreshold = ioUthreshold;
        this.confidences = confidences;
        prepare();
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
        int[] labels = (confidences!=null)? new int[confidences.length]:null;
        for(int y=1;y<IoUs.getHeight();y++){
            IoUs.getRow(0,y,row,IoUs.getWidth());
            int found=0;
            for(int x=1;x<IoUs.getWidth();x++){
                if(row[x]>IoUthreshold) found++;
            }
            switch (found){
                case 0: if(row[0]>0)fp++;
                        if(labels!=null) labels[y-1]=0;
                        break;
                case 1: tp++;
                        if(labels!=null) labels[y-1]=1;
                        break;
                default: tp++; IJ.log("object "+y + "in test as "+ found +" correspondances");
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

    double getPrecision(){return ((double)tp) / (double)(tp+fp); }
    double getSensitivity(){return ((double)tp) / (double)(tp+fn);}
    double getJaccardIndex(){return ((double)tp) / (double)(tp+fp+fn);}
    double getF1measure(){ return (2.0*tp) / (double)(2*tp+fp+fn);}
    double getAveragePrecision(){
        List<Map<String, Double>> precisionRecallCurve = calculatePrecisionRecallCurve();
        double ap=0;
        for (Map<String, Double> point : precisionRecallCurve) {
                    ap+=point.get("precision");
        }
        ap/=precisionRecallCurve.size();
        return ap;
    }


    public List<Map<String, Double>> calculatePrecisionRecallCurve() {
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
            double recall = (double) tp / numPositives;

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
}
