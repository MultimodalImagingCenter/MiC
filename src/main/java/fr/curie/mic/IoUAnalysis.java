package fr.curie.mic;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.util.Arrays;

public class IoUAnalysis {

    private final ImagePlus truth;
    private final ImagePlus test;

    private final ImageProcessor iou;


    private double EPSILON = 1E-7;

    protected static final int TP_COLOR_INDEX=1;
    protected static final int TP_OVER_COLOR_INDEX=2;
    protected static final int TP_UNDER_COLOR_INDEX=3;
    protected static final int FUSED_COLOR_INDEX=4;
    protected static final int SPLIT_COLOR_INDEX=5;
    protected static final int UNDER_IOU_COLOR_INDEX=6;
    protected static final int UNDER_IOU_EXT_COLOR_INDEX=7;
    protected static final int FP_COLOR_INDEX=8;
    protected static final int FN_COLOR_INDEX=9;
    protected static final int NOT_ANALYZED_COLOR_INDEX=10;



    public IoUAnalysis(
            ImagePlus truth,
            ImagePlus test,
            ImageProcessor iou
    ) {
        this.truth = truth;
        this.test = test;
        this.iou = iou;
    }

    public ImageProcessor getIoU() {
        return iou;
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
                iou,
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

    public ImageProcessor getColorCode( double threshold){
        ByteProcessor bp=new ByteProcessor(this.iou.getWidth(),this.iou.getHeight());
        float[] row=new float[this.iou.getWidth()];
        float[] col = new float[this.iou.getHeight()];
        for(int y=1;y<this.iou.getHeight();y++){
            for(int x=1;x<this.iou.getWidth();x++){
                double val = this.iou.getf(x,y);
                if(val==-1){
                    bp.set(x,y,NOT_ANALYZED_COLOR_INDEX);
                }else if(val>=threshold){
                    bp.set(x,y,TP_COLOR_INDEX);
                }else{
                    Arrays.fill(row,0);
                    this.iou.getRow(1,y,row,this.iou.getWidth()-1);
                    Arrays.sort(row);
                    Arrays.fill(col,0);
                    this.iou.getColumn(x,1,col,this.iou.getHeight()-1);
                    Arrays.sort(col);
                    if(col[col.length-1]>threshold){
                        bp.set(x,y,SPLIT_COLOR_INDEX);
                    } else if(row[row.length-1]>threshold){
                        bp.set(x,y,FUSED_COLOR_INDEX);
                    } else bp.set(x,y,UNDER_IOU_COLOR_INDEX);
                }
            }
        }
        for(int y=1;y<this.iou.getHeight();y++){
            if(this.iou.getf(0,y)<0) bp.set(0,y,NOT_ANALYZED_COLOR_INDEX);
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
        row=null;
        for(int x=1;x<this.iou.getWidth();x++){
            if(this.iou.getf(x,0)<0) bp.set(x,0,NOT_ANALYZED_COLOR_INDEX);
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

    protected ImagePlus displayCombination(ImagePlus truth, ImagePlus test, ImageProcessor colorcode){
        if(truth.getNSlices()==1){
            ImagePlus tmp = new ImagePlus("composite_"+truth.getTitle()+"_VS_"+test.getTitle(),
                    displayCombinationProcessor(truth.getProcessor(),test.getProcessor(),colorcode));
            //if(lutcomposite!=null) tmp.setLut(lutcomposite);
            return tmp;
        }
        ImageStack is1=truth.getImageStack();
        ImageStack is2=test.getImageStack();
        ImageStack result=new ImageStack(is1.getWidth(),is1.getHeight());
        for(int z=0;z<truth.getNSlices();z++){
            result.addSlice(displayCombinationProcessor(is1.getProcessor(z+1),
                    is2.getProcessor(z+1),colorcode));
        }
        ImagePlus tmp = new ImagePlus("composite_"+truth.getTitle()+"_VS_"+test.getTitle(),result);
        //if(lutcomposite!=null) tmp.setLut(lutcomposite);
        return tmp;
    }

    public static ImageProcessor displayCombinationProcessor(ImageProcessor truth, ImageProcessor test, ImageProcessor colorcode ) {
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

                if (
                        truthLabel < 0 ||
                                testLabel < 0 ||
                                truthLabel > maxTruthLabel ||
                                testLabel > maxTestLabel
                ) {
                    result.set(x, y, 0);
                } else {
                    result.set(
                            x,
                            y,
                            colorcode.get(truthLabel, testLabel)
                    );
                }
            }
        }

        return result;
    }

}