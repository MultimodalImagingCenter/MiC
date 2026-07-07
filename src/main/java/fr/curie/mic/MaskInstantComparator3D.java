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
import ij.CompositeImage;

public class MaskInstantComparator3D implements PlugIn {
    //    Binary images to compare
    private ImagePlus truthMaskIP;
    private ImagePlus testMaskIP;
    private int nChannels;
    private int nFrames;


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
    private ImagePlus graphHyperStack;
    private ArrayList<ImageProcessor> correspondanceImages;
    private ImagePlus correspondanceHyperStack;


    private LUT lutcomposite;
    private double EPSILON = 1E-7;

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
            //resultsTable.incrementCounter();
        }
        if(rt2!=null){
            pixelObjectResultsTable=rt2;
            //pixelObjectResultsTable.incrementCounter();
        }else{
            pixelObjectResultsTable = new ResultsTable();
        }

        if(showComposite) lutcomposite = IoUAnalysis.getMiCLUT();
        analysis();

        resultsTable.show("Mask comparison results");
        if(pixelObjectMethod)pixelObjectResultsTable.show("Mask comparison Object with IoU thresholds");
        if(correspondanceHyperStack != null){
            correspondanceHyperStack.show();
        }
        if(graphHyperStack != null){
            graphHyperStack.setOpenAsHyperStack(true);
            graphHyperStack.show();
        }

    }
    public boolean analysis() {
        if (truthMaskIP == null || testMaskIP == null) {
            IJ.error("Truth or test image is null.");
            return false;
        }
        if (!checkHyperstackCompatibility(truthMaskIP, testMaskIP)) {
            return false;
        }

        ImagePlus originalTruth = truthMaskIP;
        ImagePlus originalTest = testMaskIP;

        int nChannels = originalTruth.getNChannels();
        int nFrames = originalTruth.getNFrames();

        if(showCorrespondances){
            correspondanceImages = new ArrayList<>();
        }

        boolean ok = true;

        for (int t = 1; t <= nFrames; t++) {
            for (int c = 1; c <= nChannels; c++) {
                IJ.log("Analyzing channel " + c + ", frame " + t);
                ImagePlus truthSubVolume = extractCZVolume(originalTruth, c, t);
                ImagePlus testSubVolume = extractCZVolume(originalTest, c, t);
                truthMaskIP = truthSubVolume;
                testMaskIP = testSubVolume;
                resultsTable.incrementCounter();
                boolean currentOk = analysis3D(c, t);
                ok = ok && currentOk;
            }
        }
        truthMaskIP = originalTruth;
        testMaskIP = originalTest;
        if(showCorrespondances){
            buildCorrespondanceHyperStack(nChannels, nFrames);
        }
        if (showComposite && (objectMethod || pixelObjectMethod)) {
            buildAndShowCompositeHyperstacks(originalTruth, originalTest);
        }
        return ok;
    }

    public boolean analysis3D(int channel, int frame){
        IoUAnalysis analysis = IoUAnalysis.create(truthMaskIP, testMaskIP,minSize, minDist);
        int maxTruth = analysis.getMaxTruth();
        int maxTest = analysis.getMaxTest();

        IJ.log("Truth has "+maxTruth+" objects");
        IJ.log("Test has "+maxTest+" objects");


        resultsTable.addValue("Truth image", truthMaskIP.getTitle());
        resultsTable.addValue("Test image", testMaskIP.getTitle());
        resultsTable.addValue("Channel", channel);
        resultsTable.addValue("Frame", frame);
        resultsTable.addValue("Truth objects", maxTruth);
        resultsTable.addValue("Test objects", maxTest);

        //compute histogram 2D to get correspondance between two images
        //ImageProcessor histo2D = MicUtils.histo2D(truthMaskIP,maxTruth,testMaskIP,maxTest);

        //pixel analysis
        if(pixelMethod) {
            //pixelAnalysis(histo2D);
            //Metrics metrics = new Metrics(histo2D, -1, null);
            Metrics metrics = analysis.getPixelMetrics();
            addToResultTable(resultsTable, "Pixel", metrics.getTP(), metrics.getFP(), metrics.getFN(),
                    metrics.getPrecision(), metrics.getSensitivity(), metrics.getJaccardIndex(), metrics.getF1measure(), -1);
        }

        //object based analysis
        if(objectMethod || pixelObjectMethod) {
            //compute histograms 1D for each images
            //IJ.log("truth histo");
            //int[] histoTruth=MicUtils.histo1D(truthMaskIP,maxTruth);
            //IJ.log("test histo");
            //int[] histoTest=MicUtils.histo1D(testMaskIP,maxTest);
            //compute IoUs for each objects
            //IJ.log("compute ious");
            //ImageProcessor iou = MicUtils.computesIoUs(histo2D, histoTruth, histoTest);
            //IoUAnalysis analysis = computeIoUForVolumes(truthMaskIP, testMaskIP);
            //IoUAnalysis analysis = IoUAnalysis.create(truthMaskIP,testMaskIP,minSize,minDist);
            //checkPositionAndSize(iou,histoTruth,histoTest,minSize,minDist,truthMaskIP,testMaskIP);
            if(showCorrespondances) correspondanceImages.add(analysis.getIoU().duplicate());
            if(objectMethod) {
                //double[] metrics = objectAnalysis(iou, 0.5);
                Metrics metrics = analysis.getMetrics(0.5);
                addToResultTable(resultsTable, "Object",metrics.getTP(),metrics.getFP(),metrics.getFN(),
                        metrics.getPrecision(),metrics.getSensitivity(),metrics.getJaccardIndex(),metrics.getF1measure(),0.5);
                /*if(showComposite) {
                    ImageProcessor colorcode = convertIoU2ColorCode(iou,0.5);
                    ImagePlus imp = displayCombination(truthMaskIP, testMaskIP, colorcode);
                    imp.setTitle("IoU"+0.5+"_"+imp.getTitle());
                    imp.show();
                }*/
            }
            if(pixelObjectMethod){
                int nbcomp=(int)Math.round((overlapMax-overlapMin)/overlapInc+1);
                double[] ious=new double[nbcomp];
                double[] precisions=new double[nbcomp];
                double[] sensitivities=new double[nbcomp];
                double[] jaccards=new double[nbcomp];
                double[] dscs=new double[nbcomp];
                int index=0;
                for(int th=(int)(overlapMin*10000); th<=(int)((overlapMax+EPSILON)*10000); th+=(int)(overlapInc*10000)){
                    if(pixelObjectResultsTable.getCounter()>0) pixelObjectResultsTable.incrementCounter();
                    double qth=th/10000.0;
                    //double[] metrics = objectAnalysis(iou, qth);
                    Metrics metrics = analysis.getMetrics(qth);
                    ious[index]=qth;
                    precisions[index]=metrics.getPrecision();
                    sensitivities[index]=metrics.getSensitivity();
                    jaccards[index]=metrics.getJaccardIndex();
                    dscs[index]=metrics.getF1measure();
                    //IJ.log("index:"+index+" , iou:"+ious[index]+" , jaccard:"+jaccards[index]);
                    index++;
                    addToResultTable(pixelObjectResultsTable, "Object",metrics.getTP(),metrics.getFP(),metrics.getFN(),
                            metrics.getPrecision(),metrics.getSensitivity(),metrics.getJaccardIndex(),metrics.getF1measure(),qth);
                }
                if(showGraphs) createGraphs(channel, frame, truthMaskIP.getShortTitle()+"_VS_"+testMaskIP.getShortTitle()+"_IoU_graph",ious,precisions,sensitivities,jaccards,dscs);

            }
        }
        return true;
    }
    private boolean checkHyperstackCompatibility(ImagePlus truth, ImagePlus test) {

        if (truth.getWidth() != test.getWidth() ||
                truth.getHeight() != test.getHeight()) {

            IJ.error(
                    "Truth and test images must have the same width and height."
            );
            return false;
        }

        if (truth.getNChannels() != test.getNChannels()) {

            IJ.error(
                    "Truth and test images must have the same number of channels.\n" +
                            "Truth channels: " + truth.getNChannels() + "\n" +
                            "Test channels: " + test.getNChannels()
            );
            return false;
        }

        if (truth.getNSlices() != test.getNSlices()) {

            IJ.error(
                    "Truth and test images must have the same number of Z slices.\n" +
                            "Truth slices: " + truth.getNSlices() + "\n" +
                            "Test slices: " + test.getNSlices()
            );
            return false;
        }

        if (truth.getNFrames() != test.getNFrames()) {

            IJ.error(
                    "Truth and test images must have the same number of frames.\n" +
                            "Truth frames: " + truth.getNFrames() + "\n" +
                            "Test frames: " + test.getNFrames()
            );
            return false;
        }

        return true;
    }
    private ImagePlus extractCZVolume(ImagePlus source, int channel, int frame) {

        int width = source.getWidth();
        int height = source.getHeight();
        int nSlices = source.getNSlices();

        ImageStack sourceStack = source.getStack();
        ImageStack subStack = new ImageStack(width, height);

        for (int z = 1; z <= nSlices; z++) {
            int stackIndex = source.getStackIndex(channel, z, frame);
            ImageProcessor ip = (sourceStack!=null && sourceStack.size()>0) ? sourceStack.getProcessor(stackIndex).duplicate() : source.getProcessor().duplicate();

            String label = (sourceStack!=null && sourceStack.size()>0) ? sourceStack.getSliceLabel(stackIndex) : source.getShortTitle();
            subStack.addSlice(label, ip);
        }

        String title = source.getShortTitle() + "_C" + channel + "_T" + frame;
        ImagePlus result = new ImagePlus(title, subStack);
        result.setDimensions(1, nSlices, 1);

        if (source.getCalibration() != null) {
            result.setCalibration(source.getCalibration().copy());
        }

        return result;
    }


    private double[] getCompositeIoUThresholds() {

        LinkedHashMap<Integer, Double> thresholds = new LinkedHashMap<>();

        if (objectMethod) {
            thresholds.put(5000, 0.5);
        }

        if (pixelObjectMethod) {
            int start = (int) Math.round(overlapMin * 10000.0);
            int end = (int) Math.round(overlapMax * 10000.0);
            int inc = (int) Math.round(overlapInc * 10000.0);

            if (inc <= 0) {
                IJ.error("IoU increment must be > 0.");
                return new double[0];
            }

            for (int th = start; th <= end + 1; th += inc) {
                double value = th / 10000.0;
                thresholds.put(th, value);
            }
        }

        double[] result = new double[thresholds.size()];

        int i = 0;
        for (Double value : thresholds.values()) {
            result[i++] = value;
        }

        return result;
    }



    private void buildAndShowCompositeHyperstacks(ImagePlus originalTruth, ImagePlus originalTest) {
        double[] thresholds = getCompositeIoUThresholds();
        if (thresholds.length == 0) {
            IJ.log("No IoU threshold available for composite hyperstack.");
            return;
        }

        int width = originalTruth.getWidth();
        int height = originalTruth.getHeight();

        int nOriginalChannels = originalTruth.getNChannels();
        int nSlices = originalTruth.getNSlices();
        int nFrames = originalTruth.getNFrames();

        if (lutcomposite == null) {
            lutcomposite = IoUAnalysis.getMiCLUT();
        }

        for (int originalChannel = 1; originalChannel <= nOriginalChannels; originalChannel++) {
            IJ.log("Building composite hyperstack for original channel " + originalChannel);
            //long timestart= System.currentTimeMillis();
            ImageStack resultStack = new ImageStack(width, height);
            for (int t = 1; t <= nFrames; t++) {
                ImagePlus truthVolume = extractCZVolume(originalTruth, originalChannel, t);
                ImagePlus testVolume = extractCZVolume(originalTest, originalChannel, t);

                //ImageProcessor iou = computeIoUForVolumes(truthVolume, testVolume);
                //IoUAnalysis analysis = computeIoUForVolumes(truthVolume, testVolume);
                IoUAnalysis analysis = IoUAnalysis.create(truthVolume,testVolume,minSize,minDist);

                ImageStack truthStack = truthVolume.getImageStack();
                ImageStack testStack = testVolume.getImageStack();

                for (int z = 1; z <= nSlices; z++) {

                    ImageProcessor truthProcessor = truthStack.getProcessor(z);
                    ImageProcessor testProcessor = testStack.getProcessor(z);

                    for (int thresholdIndex = 0; thresholdIndex < thresholds.length; thresholdIndex++) {
                        //long start = System.currentTimeMillis();
                        ImageProcessor compositePlane = analysis.createCompositePlane(truthProcessor,testProcessor,
                                thresholds[thresholdIndex]
                        );
                        //IJ.log(" : createCompositePlane : "+(System.currentTimeMillis()-start)+" ms");
                        String label ="IoU=" + IJ.d2s(thresholds[thresholdIndex], 4) +
                                " C=" + originalChannel + " Z=" + z + " T=" + t;
                        resultStack.addSlice(label, compositePlane);
                    }
                }
            }

            String title ="c"+originalChannel+"_" + originalTruth.getShortTitle() +
                            "_VS_" + originalTest.getShortTitle() + "_sourceC" + originalChannel;

            ImagePlus composite = new ImagePlus(title, resultStack);
            composite.setDimensions(thresholds.length,nSlices,nFrames);
            composite.setOpenAsHyperStack(true);
            if (originalTruth.getCalibration() != null) {
                composite.setCalibration(originalTruth.getCalibration().copy());
            }

            CompositeImage compositeImage = new CompositeImage(composite,CompositeImage.COLOR);
            for (int thresholdIndex = 0; thresholdIndex < thresholds.length; thresholdIndex++) {
                compositeImage.setChannelLut(lutcomposite, thresholdIndex + 1);
            }
            compositeImage.setTitle(title);
            compositeImage.show();
            //long timestop= System.currentTimeMillis();
            //IJ.log("Composite hyperstack built in "+(timestop-timestart)+" ms");
        }
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
               // ((Checkbox)chV.get(6+offset)).setEnabled(varChecked||objChecked);

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

        MicUtils.checkImagePlus(truthMaskIP);
        MicUtils.checkImagePlus(testMaskIP);
        nChannels=truthMaskIP.getNChannels();
        nFrames=truthMaskIP.getNFrames();

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

    private void createGraphs(int channel,int frame, String title,double[] thresholds, double[] precision, double[] sensitivity, double[] jaccardIndex, double[] fmeasure){
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
        //plot.setSize(graphHyperStack.getWidth(),graphHyperStack.getHeight());
        //plot.setFrameSize(graphHyperStack.getWidth(),graphHyperStack.getHeight());
        plot.setLimits(thresholds[0], thresholds[thresholds.length-1],0, 1.1);

        ImageProcessor plotProcessor = plot.getImagePlus().getProcessor();
        if(graphHyperStack==null){
            graphHyperStack = IJ.createHyperStack("IoU Graphs",plotProcessor.getWidth(), plotProcessor.getHeight(),nChannels,1,nFrames,24);
        }
        IJ.log("plotprocessor size = "+plotProcessor.getWidth()+" x "+plotProcessor.getHeight());
        IJ.log("plot size = "+plot.getSize().getWidth()+" x "+plot.getSize().getHeight());
        int stackIndex = graphHyperStack.getStackIndex(channel, 1, frame);
        graphHyperStack.getStack().getProcessor(stackIndex).copyBits(plotProcessor, 0, 0, Blitter.COPY);
        graphHyperStack.getStack().setSliceLabel("C"+channel+"_T"+frame, stackIndex);
    }

    private void buildCorrespondanceHyperStack(int nChannels, int nFrames){
        int maxWidth = 1;
        int maxHeight = 1;
        for(ImageProcessor ip : correspondanceImages){
            maxWidth = Math.max(maxWidth, ip.getWidth());
            maxHeight = Math.max(maxHeight, ip.getHeight());
        }
        ImageStack stack = new ImageStack(maxWidth, maxHeight);

        for(ImageProcessor ip : correspondanceImages){
            FloatProcessor fp = new FloatProcessor(maxWidth, maxHeight);
            fp.copyBits(ip,0,0, Blitter.COPY);
            stack.addSlice(fp);
        }

        correspondanceHyperStack =new ImagePlus("IoU Correspondances", stack);
        correspondanceHyperStack.setDimensions(nChannels, 1, nFrames);
        correspondanceHyperStack.setOpenAsHyperStack(true);
    }

}
