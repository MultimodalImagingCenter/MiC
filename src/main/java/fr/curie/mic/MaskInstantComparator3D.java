/*-
 * #%L
 * MiC is an ImageJ plugin to compare segmentation masks
 * %%
 * Copyright (C) 2023 - 2024 Multimodal-Imaging-Center
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.*;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.*;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;

public class MaskInstantComparator3D implements PlugIn {
    //    Binary images to compare
    private ImagePlus truthMaskIP;
    private ImagePlus testMaskIP;

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


    //composite image created in results
    private ImagePlus compositeImage;

    //    Methods of comparison
    private double minSize;
    private double maxSize;
    private boolean pixelMethod;
    private boolean objectMethod;
    private boolean pixelObjectMethod;
    private double overlapMin;
    private double overlapMax;
    private double overlapInc;
    private double minDist;

    //  ROIS (if object type methods)
    private Roi[] truthRois;
    private Roi[] testRois;
    private ArrayList<Roi[]> allTruthRoi;

    //    Result Table
    private ResultsTable resultsTable;
    private ResultsTable pixelObjectResultsTable;
    // graphs
    private boolean showGraphs;

    private boolean showComposite;
    private boolean showCorrespondances;
    private PlotVirtualStack plotStack;
    private LUT lutcomposite;
    @Override
    public void run(String arg) {
        boolean useOpenImages=WindowManager.getImageCount() > 0;
        GenericDialog gd = getGenericDialog(useOpenImages);
        gd.showDialog();
        if(gd.wasCanceled()) return;

        getChoicesFromGD(gd,useOpenImages);

        ResultsTable rt = ResultsTable.getResultsTable("Mask comparison results");
        ResultsTable rt2 = ResultsTable.getResultsTable("Mask comparison Object with IoU thresholds");
        if (rt == null) {
            resultsTable = new ResultsTable();
        } else {
            resultsTable = rt;
            resultsTable.incrementCounter();
        }
        if(rt2!=null){
            pixelObjectResultsTable=rt2;
            pixelObjectResultsTable.incrementCounter();
        }else{
            pixelObjectResultsTable = new ResultsTable();
        }

        if(showComposite) createLUT();

        analysis();

        resultsTable.show("Mask comparison results");
        if(pixelObjectMethod)pixelObjectResultsTable.show("Mask comparison Object with IoU thresholds");


    }

    public boolean analysis(){
        int maxTruth = MicUtils.correctObjectNumbering(truthMaskIP);
        int maxTest = MicUtils.correctObjectNumbering(testMaskIP);

        IJ.log("Truth has "+maxTruth+" objects");
        IJ.log("Test has "+maxTest+" objects");

        //compute histogram 2D to get correspondance between two images
        ImageProcessor histo2D = MicUtils.histo2D(truthMaskIP,maxTruth,testMaskIP,maxTest);
        //pixel analysis
        if(pixelMethod) pixelAnalysis(histo2D);

        //object based analysis
        if(objectMethod || pixelObjectMethod) {
            //compute histograms 1D for each images
            int[] histoTruth=MicUtils.histo1D(truthMaskIP,maxTruth);
            int[] histoTest=MicUtils.histo1D(testMaskIP,maxTest);
            //compute IoUs for each objects
            ImageProcessor iou = MicUtils.computesIoUs(histo2D, histoTruth, histoTest);
            checkPositionAndSize(iou,histoTruth,histoTest,minSize,minDist);
            if(showCorrespondances) new ImagePlus("objects_correspondance_X_Truth_Y_Test",iou).show();
            if(objectMethod) {
                double[] metrics = objectAnalysis(iou, 0.5);
                addToResultTable(resultsTable, "Object",metrics[0],metrics[1],metrics[2],metrics[3],metrics[4],metrics[5],metrics[6],0.5);
                if(showComposite) {
                    ImageProcessor colorcode = convertIoU2ColorCode(iou,0.5);
                    ImagePlus imp = displayCombination(truthMaskIP, testMaskIP, colorcode);
                    imp.setTitle("IoU"+0.5+"_"+imp.getTitle());
                    imp.show();
                }
            }
            if(pixelObjectMethod){
                int nbcomp=(int)Math.round((overlapMax-overlapMin)/overlapInc+1);
                double[] ious=new double[nbcomp];
                double[] precisions=new double[nbcomp];
                double[] sensitivities=new double[nbcomp];
                double[] jaccards=new double[nbcomp];
                double[] dscs=new double[nbcomp];
                int index=0;
                for(double th=overlapMin; th<=overlapMax+EPSILON; th+=overlapInc){
                    if(pixelObjectResultsTable.getCounter()>0) pixelObjectResultsTable.incrementCounter();
                    double[] metrics = objectAnalysis(iou, th);
                    ious[index]=th;
                    precisions[index]=metrics[3];
                    sensitivities[index]=metrics[4];
                    jaccards[index]=metrics[5];
                    dscs[index]=metrics[6];
                    //IJ.log("index:"+index+" , iou:"+ious[index]+" , jaccard:"+jaccards[index]);
                    index++;
                    addToResultTable(pixelObjectResultsTable, "Object",metrics[0],metrics[1],metrics[2],metrics[3],metrics[4],metrics[5],metrics[6],th);
                }
                if(showGraphs) createGraphs(truthMaskIP.getShortTitle()+"_VS_"+testMaskIP.getShortTitle()+"_IoU_graph",ious,precisions,sensitivities,jaccards,dscs);

            }
        }
        return true;
    }

    protected void createLUT(){
        byte[] r=new byte[256];
        byte[] g=new byte[256];
        byte[] b=new byte[256];
        //TP
        r[1]=(byte)255;
        g[1]=(byte)255;
        b[1]=(byte)0;
        //TP OVER
        r[2]=(byte)255;
        g[2]=(byte)0;
        b[2]=(byte)0;
        //TP under
        r[3]=(byte)0;
        g[3]=(byte)255;
        b[3]=(byte)0;
        //fused
        r[4]=(byte)255;
        g[4]=(byte)128;
        b[4]=(byte)0;
        //split
        r[5]=(byte)0;
        g[5]=(byte)255;
        b[5]=(byte)255;
        //IoU under
        r[6]=(byte)0;
        g[6]=(byte)0;
        b[6]=(byte)255;
        //IoU under_ext
        r[7]=(byte)128;
        g[7]=(byte)0;
        b[7]=(byte)255;
        //FP
        r[8]=(byte)128;
        g[8]=(byte)0;
        b[8]=(byte)0;
        //FN
        r[9]=(byte)0;
        g[9]=(byte)128;
        b[9]=(byte)0;
        //Not Analyzed
        r[10]=(byte)128;
        g[10]=(byte)128;
        b[10]=(byte)128;

        lutcomposite = new LUT(r,g,b);
    }

    /**
     * Calculate the stats and add it all to the result table
     *
     * @param method : Object, Pixel or Object-Pixel
     * @param tp     : True Positives
     * @param fp     : False Positives
     * @param fn     : False Negatives
     */
    private void addToResultTable(ResultsTable resultsTable, String method, double tp, double fp, double fn,double precision, double sensitivity,double jaccardIndex, double fmeasure, double threshold) {

//        ADD TO RESULT TABLE
        if(threshold>=0) resultsTable.addValue(method + " IoU threshold", threshold);
        resultsTable.addValue(method + " TP", tp);
        resultsTable.addValue(method + " FP", fp);
        resultsTable.addValue(method + " FN", fn);
        resultsTable.addValue(method + " Precision", precision);/*(TP/Positives)*/
        resultsTable.addValue(method + " Recall/Sensitivity", sensitivity);/*(TP/Truth)*/
        resultsTable.addValue(method + " Jaccard Index", jaccardIndex);/*(TP/(TP+FN+FP))*/
        resultsTable.addValue(method + " F-measure", fmeasure);/*(2*Precision/(precision+recall))*/

    }


    /**
     * Creates generate dialog with all the information to obtain
     *
     * @param useOpenImages : if user want to use open images
     * @return GenericDialog
     */
    private GenericDialog getGenericDialog(boolean useOpenImages) {
        GenericDialog gd = new GenericDialog("Mask instant Comparator");
        //Font f = gd.getFont();
        //IJ.log("default font is "+f.getFontName());
        //gd.setFont(new Font("Monospaced", Font.PLAIN, f.getSize()));
        gd.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/logo_MiC_50.png")));
        String[] imageList = WindowManager.getImageTitles();
        try {
            gd.setInsets(10,250,0);
            gd.addImage(IJ.openImage(getClass().getResource("/logo_MiC_50.png").toURI().toString()));
            //gd.addToSameRow();
            gd.addMessage("Developped by Cédric Messaoudi from the Multimodal Imaging Center - Institut Curie (France)");
            //gd.addToSameRow();
            //gd.addImage(IJ.openImage(getClass().getResource("/MIC_IC_logo.png").toURI().toString()));

        }catch (Exception e){
            e.printStackTrace();
        }
        gd.addMessage("--------------------------------------------------        data        --------------------------------------------------");
//      True segmentation
        if (useOpenImages) gd.addChoice("Truth_mask_image", imageList, imageList[1]);
        else gd.addFileField("Truth_mask_path", "");
        //gd.addFileField("Truth_particles_ROI_zip_path(if exists)", "");

//      Segmentation to test
        if (useOpenImages) gd.addChoice("Test_mask_image", imageList, imageList[0]);
        else gd.addFileField("Test_mask_path", "");
        //gd.addFileField("Test_particles_ROI_zip_path(if exists)", "");

        if (!useOpenImages) {
            //gd.addToSameRow();
            gd.addCheckbox("Show_images", true);
        }

        gd.addMessage("--------------------------------------------------     parameters     --------------------------------------------------");

//        Methods
        gd.addCheckboxGroup(1, 3, new String[]{"Pixel", "Object_(IoU=0.5)", "Object_(varying_IoU)"}, new boolean[]{true, true, true});
        gd.addNumericField("Minimum_IoU_threshold (0-1)", 0.5,2);
        gd.addNumericField("Maximum_IoU_threshold (0-1)", 1.0,2);
        gd.addNumericField("Increment_of_IoU_threshold (0-1)", 0.05,2);

//        Additional choices
        gd.addMessage("-------------------------------------------------- displayed results  --------------------------------------------------");
        gd.addCheckboxGroup(1, 4, new String[]{"Show_composite_images", "Show_graphs", "Show_GT_objects_correspondence_table"}, new boolean[]{true, true, true});

        gd.addMessage("-------------------------------------------------- filters on objects --------------------------------------------------");
        gd.addNumericField("Minimum_size_for_objects (pixels)",0);
        gd.addNumericField("Minimum_distance_to_border (pixels)",0);

        gd.addMessage("distance to border value explanation:");
        //gd.addToSameRow();
        gd.addMessage("set -1 to remove nothing, 0 to remove objects touching borders, higher values to remove objects with pixels in defined distance from horizontal and vertical borders of image");

        Vector chV=gd.getCheckboxes();
        Vector numV=gd.getNumericFields();
        int offset=useOpenImages?0:1;
        Checkbox objCB=(Checkbox)chV.get(1+offset);
        Checkbox varIoUCB=(Checkbox)chV.get(2+offset);
        varIoUCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean varChecked=varIoUCB.getState();
                boolean objChecked=objCB.getState();
                //IJ.log("varying IoU activated: "+checked);
                ((TextField)numV.get(0)).setEnabled(varChecked);
                ((TextField)numV.get(1)).setEnabled(varChecked);
                ((TextField)numV.get(2)).setEnabled(varChecked);

                ((Checkbox)chV.get(4+offset)).setEnabled(varChecked);
                ((Checkbox)chV.get(5+offset)).setEnabled(varChecked);
                ((Checkbox)chV.get(6+offset)).setEnabled(varChecked||objChecked);

                ((TextField)numV.get(3)).setEnabled(varChecked||objChecked);
                ((TextField)numV.get(4)).setEnabled(varChecked||objChecked);

                gd.repaint();
            }
        });
        objCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean varChecked=varIoUCB.getState();
                boolean objChecked=objCB.getState();
                //IJ.log("varying IoU activated: "+checked);
                ((TextField)numV.get(0)).setEnabled(varChecked);
                ((TextField)numV.get(1)).setEnabled(varChecked);
                ((TextField)numV.get(2)).setEnabled(varChecked);

                ((Checkbox)chV.get(4+offset)).setEnabled(varChecked);
                ((Checkbox)chV.get(5+offset)).setEnabled(varChecked);
                ((Checkbox)chV.get(6+offset)).setEnabled(varChecked||objChecked);

                ((TextField)numV.get(3)).setEnabled(varChecked||objChecked);
                ((TextField)numV.get(4)).setEnabled(varChecked||objChecked);

                gd.repaint();
            }
        });
        return gd;
    }


    private void getChoicesFromGD(GenericDialog gd, boolean useOpenImages) {
//        Get truth and test images' and ROIs' paths
        String truthMaskPathOrTitle;
        String testMaskPathOrTitle;

        if (useOpenImages) truthMaskPathOrTitle = gd.getNextChoice();
        else truthMaskPathOrTitle = gd.getNextString();

        //String truthRoiTemp = gd.getNextString();

        if (useOpenImages) testMaskPathOrTitle = gd.getNextChoice();
        else testMaskPathOrTitle = gd.getNextString();

        boolean showImage;
        if (!useOpenImages) showImage = gd.getNextBoolean();
        else showImage = false;
        String truthRoiTemp = "";
        String testRoiTemp = "";
        if(!useOpenImages){
            truthRoiTemp = gd.getNextString();
            testRoiTemp = gd.getNextString();
            if(!truthRoiTemp.equals("")) IJ.log("will load truth ROI: "+truthRoiTemp);
            if(!testRoiTemp.equals("")) IJ.log("will load test ROI: "+testRoiTemp);
        }

        //String testRoiTemp = gd.getNextString();

//        Get methods to use
        pixelMethod = gd.getNextBoolean();
        objectMethod = gd.getNextBoolean();
        pixelObjectMethod = gd.getNextBoolean();

        IJ.log("pixel method:"+pixelMethod+"\nobject:"+objectMethod+"\npixel/obejct:"+pixelObjectMethod);

//        Get tolerance of error for pixelobject method and choice for showing images
        overlapMin = gd.getNextNumber();
        overlapMax = gd.getNextNumber();
        overlapInc = gd.getNextNumber();

        showComposite = gd.getNextBoolean();
        showGraphs = gd.getNextBoolean();
        showCorrespondances = gd.getNextBoolean();

//        Set images
        if (useOpenImages) truthMaskIP = WindowManager.getImage(truthMaskPathOrTitle);
        else truthMaskIP = getImage(truthMaskPathOrTitle, showImage);
        if (useOpenImages) testMaskIP = WindowManager.getImage(testMaskPathOrTitle);
        else testMaskIP = getImage(testMaskPathOrTitle, showImage);

        truthMaskIP.resetRoi();
        testMaskIP.resetRoi();

        minSize = gd.getNextNumber();
        maxSize = Double.POSITIVE_INFINITY;
        minDist = gd.getNextNumber();
        IJ.log("minimum size of objects: "+minSize);
        IJ.log("minimum distance to border: "+minDist);
//        Set ROIs


    }


    /**
     * @param imagePath : path of image
     * @param showImage : display the image or not
     *                  If displayed, modify the display range to be able to see the segmentation results
     * @return ImagePlus without invertedLut and without calibration
     */
    private static ImagePlus getImage(String imagePath, boolean showImage) {
//        OPEN IMAGE

        File imageFile = new File(imagePath);
        if (imageFile.exists() && imageFile.isFile()) {
            ImagePlus image = IJ.openImage(imagePath);
            //        REMOVE POTENTIAL DISTANCE SCALE
            image.setCalibration(null);
//        If wanted, show image (with display range adapted to range of intensity values present)
            if (showImage) {
                image.show();
                image.setDisplayRange(0, image.getRawStatistics().max + 10);
                image.updateAndDraw(); /*update the display*/
            }
            return image;
        } else {
            if (!imageFile.exists()) IJ.error("The path " + imagePath + " does not exist.");
            else IJ.error("The path " + imagePath + " corresponds to a directory, not a file.");
            return null;
        }
    }





    private void checkPositionAndSize(ImageProcessor iou, int[] histoTruth, int[] histoTest, double minSize, double minDist){
        //check size
        for(int y=0;y<iou.getHeight();y++){
            for(int x=0;x<iou.getWidth();x++){
                if(histoTruth[x]<minSize||histoTest[y]<minSize) iou.setf(x,y,-1);
            }
        }

        //check position
        if(minDist<0) return;

        ArrayList<Integer> borderTruth=new ArrayList<>();
        if(truthMaskIP.getNSlices()==1) borderTruth.addAll(objectBorder(truthMaskIP.getProcessor(),minDist));
        else{
            ImageStack is=truthMaskIP.getImageStack();
            for(int z=1;z<=is.getSize();z++){
                borderTruth.addAll(objectBorder(is.getProcessor(z),minDist));
            }
        }
        Set<Integer> set = new HashSet<Integer>(borderTruth);
        borderTruth = new ArrayList<Integer>(set);

        ArrayList<Integer> borderTest=new ArrayList<>();
        if(testMaskIP.getNSlices()==1) borderTest.addAll(objectBorder(testMaskIP.getProcessor(),minDist));
        else{
            ImageStack is=testMaskIP.getImageStack();
            for(int z=1;z<=is.getSize();z++){
                borderTest.addAll(objectBorder(is.getProcessor(z),minDist));
            }
        }
        set = new HashSet<Integer>(borderTest);
        borderTest = new ArrayList<Integer>(set);
        removefromIoU(iou,borderTruth,borderTest);
    }

    private ArrayList<Integer> objectBorder(ImageProcessor ip, double minDist){
        ArrayList<Integer> border=new ArrayList<>();
        for(int x=0;x<ip.getWidth();x++){
            for(int y=0;y<=minDist;y++) {
                if (ip.getf(x, y) > 0) border.add(new Integer((int) ip.getf(x, y)));
                if (ip.getf(x, ip.getHeight() - 1-y) > 0) border.add(new Integer((int) ip.getf(x, ip.getHeight() - 1 -y)));
            }
        }
        for(int y=0;y<ip.getHeight();y++){
            for(int x=0;x<=minDist;x++){
                if(ip.getf(x,y)>0) border.add(new Integer((int)ip.getf(x,y)));
                if(ip.getf(ip.getWidth()-1-x,y)>0) border.add(new Integer((int)ip.getf(ip.getWidth()-1-x,y)));
            }
        }
        Set<Integer> set = new HashSet<Integer>(border);
        return new ArrayList<Integer>(set);
    }
    private void removefromIoU(ImageProcessor iou, ArrayList<Integer> borderTruth,ArrayList<Integer> borderTest){
        for(Integer bt:borderTruth){
            for(int y=0;y<iou.getHeight();y++){
                iou.setf(bt,y,-1);
            }
        }
        for(Integer bt:borderTest){
            for(int x=0;x<iou.getWidth();x++){
                iou.setf(x,bt,-1);
            }
        }
    }

    private ImageProcessor convertIoU2ColorCode(ImageProcessor iou, double threshold){
        ByteProcessor bp=new ByteProcessor(iou.getWidth(),iou.getHeight());
        float[] row=new float[iou.getWidth()];
        float[] col = new float[iou.getHeight()];
        for(int y=1;y<iou.getHeight();y++){
            for(int x=1;x<iou.getWidth();x++){
                double val = iou.getf(x,y);
                if(val==-1){
                    bp.set(x,y,NOT_ANALYZED_COLOR_INDEX);
                }else if(val>=threshold){
                    bp.set(x,y,TP_COLOR_INDEX);
                }else{
                    Arrays.fill(row,0);
                    iou.getRow(1,y,row,iou.getWidth()-1);
                    Arrays.sort(row);
                    Arrays.fill(col,0);
                    iou.getColumn(x,1,col,iou.getHeight()-1);
                    Arrays.sort(col);
                    if(col[col.length-1]>threshold){
                        bp.set(x,y,SPLIT_COLOR_INDEX);
                    } else if(row[row.length-1]>threshold){
                        bp.set(x,y,FUSED_COLOR_INDEX);
                    } else bp.set(x,y,UNDER_IOU_COLOR_INDEX);
                }
            }
        }
        for(int y=1;y<iou.getHeight();y++){
            if(iou.getf(0,y)<0) bp.set(0,y,NOT_ANALYZED_COLOR_INDEX);
            else {
                iou.getRow(0, y, row, iou.getWidth());
                int foundTP = 0;
                int foundIoU = 0;
                for (int x = 1; x < iou.getWidth(); x++) {
                    if (row[x] > threshold) foundTP++;
                    else if (row[x] > 0 && row[x] < threshold) foundIoU++;
                }
                if (foundTP > 0) bp.set(0, y, TP_OVER_COLOR_INDEX);
                else if (foundIoU > 0) bp.set(0, y, UNDER_IOU_EXT_COLOR_INDEX);
                else bp.set(0, y, FP_COLOR_INDEX);
            }
        }
        row=null;
        for(int x=1;x<iou.getWidth();x++){
            if(iou.getf(x,0)<0) bp.set(x,0,NOT_ANALYZED_COLOR_INDEX);
            else {
                iou.getColumn(x, 0, col, iou.getHeight());
                int foundTP = 0;
                int foundIoU = 0;
                for (int y = 1; y < iou.getHeight(); y++) {
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
            if(lutcomposite!=null) tmp.setLut(lutcomposite);
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
        if(lutcomposite!=null) tmp.setLut(lutcomposite);
        return tmp;
    }
    protected ImageProcessor displayCombinationProcessor(ImageProcessor truth, ImageProcessor test, ImageProcessor colorcode){
        ByteProcessor result=new ByteProcessor(truth.getWidth(),truth.getHeight());
        for(int y=0;y<truth.getHeight();y++){
            for(int x=0;x<truth.getWidth();x++){
                int valx = (int)truth.getf(x,y);
                int valy = (int)test.getf(x,y);
                result.set(x,y,colorcode.get(valx,valy));
            }
        }
        return result;
    }


    private void createGraphs(String title,double[] thresholds, double[] precision, double[] sensitivity, double[] jaccardIndex, double[] fmeasure){
        IJ.log("add graphs");
        Plot plot=new Plot(title,"overlap threshold","score");
        //add precision
        plot.setColor(Color.RED);
        plot.add("line",thresholds,precision);
        String labels="precision (tp/(tp+fp))";
        //add sensitivity
        plot.setColor(Color.GREEN);
        plot.add("line",thresholds,sensitivity);
        labels+="\tsensitivity/recall (tp/(tp+fn))";
        //add jaccard index
        plot.setColor(Color.BLACK);
        plot.add("line",thresholds,jaccardIndex);
        labels+="\tjaccard index (tp/(tp+fp+fn))";
        //add fmeasure
        plot.setColor(Color.BLUE);
        plot.add("line",thresholds,fmeasure);
        labels+="\tfmeasure ((2*precision*sensitivity)/(precision+sensitivity))";
        //add legend
        plot.addLegend(labels);
        plot.setLimits(thresholds[0], thresholds[thresholds.length-1],0, 1.1);
        PlotWindow pw = plot.show();

    }

    protected void pixelAnalysis(ImageProcessor histo2D){
        int tp=0;
        int fp=0;
        int fn=0;
        int tn=histo2D.get(0,0);
        int total=0;
        for(int y=0;y<histo2D.getHeight();y++){
            for(int x=0;x<histo2D.getWidth();x++){
                double val = histo2D.get(x,y);
                total+=val;
                if(x>0&&y>0) tp+=val;
                if(x==0&&y>0) fp+=val;
                if(y==0&&x>0) fn+=val;
            }
        }

        double precision = ((double)tp) / (double)(tp+fp);
        double sensitivity = ((double)tp) / (double)(tp+fn);
        double jaccard = ((double)tp) / (double)(tp+fp+fn);
        double dsc = ((double)2*precision*sensitivity) / (double)(precision+sensitivity);
        addToResultTable(resultsTable, "Pixel",tp,fp,fn,precision,sensitivity,jaccard,dsc,-1);
        //IJ.log("Pixel analysis");
        //IJ.log("total voxels="+total+" ("+(tp+tn+fp+fn)+")");

    }

    /**
     * computes the metrics with corresponding threshold
     * @param iou
     * @param threshold
     * @return array with {tp,fp,fn, precision,sensitivity,jaccard,dsc}
     */
    protected double[] objectAnalysis(ImageProcessor iou, double threshold){
        int tp=0;
        int fp=0;
        int fn=0;
        float[] row=new float[iou.getWidth()+1];
        float[] col = new float[iou.getHeight()+1];
        for(int y=1;y<iou.getHeight();y++){
            iou.getRow(0,y,row,iou.getWidth());
            int found=0;
            for(int x=1;x<iou.getWidth();x++){
                if(row[x]>threshold) found++;
            }
            switch (found){
                case 0: if(row[0]>0)fp++; break;
                case 1: tp++; break;
                default: tp++; IJ.log("object "+y + "in test as "+ found +" correspondances");
            }
        }
        row=null;
        for(int x=1;x<iou.getWidth();x++){
            iou.getColumn(x,0,col,iou.getHeight());
            int found=0;
            for(int y=1;y<iou.getHeight();y++){
                if(col[y]>threshold) found++;
            }
            if(found==0 && col[0]>0) fn++;
        }
        double precision = ((double)tp) / (double)(tp+fp);
        double sensitivity = ((double)tp) / (double)(tp+fn);
        double jaccard = ((double)tp) / (double)(tp+fp+fn);
        double dsc = (2*precision*sensitivity) / (precision+sensitivity);
        return new double[] {tp,fp,fn, precision,sensitivity,jaccard,dsc};
    }


}
