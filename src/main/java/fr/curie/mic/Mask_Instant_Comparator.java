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

import ij.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import ij.plugin.LutLoader;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import ij.process.*;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;


//TODO centre géométrique
// Class to compare two mask and measure the differences between them
public class Mask_Instant_Comparator implements PlugIn {
    //    Binary images to compare
    private ImagePlus truthMaskIP;
    private ImagePlus testMaskIP;
    //composite image created in results
    private ImagePlus[] compositeImage;

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

    private enum CalculationMode {
        ROI_BASED,
        LABEL_MASK_BASED
    }

    private CalculationMode calculationMode = CalculationMode.ROI_BASED;


    //  ROIS (if object type methods)
    private Roi[] truthRois;
    private Roi[] testRois;
    private ArrayList<Roi[]> allTruthRoi;


    // graphs
    private boolean showGraphs;
    private boolean showSummary;
    private boolean showCorrespondances;
    private AnalysisResultDisplay resultDisplay;

    private ResultsTable resultsTable;
    private ResultsTable pixelObjectResultsTable;
    private ResultsTable objectCorrespondanceTable;


    //    CONSTRUCTOR

    /**
     * GUI constructor
     */
    public Mask_Instant_Comparator() {

    }

    /**
     *
     * @param truthMaskIP:      Image corresponding to manually verified particles
     * @param testMaskIP:       Image corresponding to particles segmentation to compare
     * @param pixelMethod       : use Pixel method to compare both masks
     * @param objectMethod      : use Object method to compare both mask
     * @param pixelObjectMethod : use mixe method (Object Pixel) to compare both masks
     * @param overlapMin        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     * @param overlapMax        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     * @param overlapInc        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     *
     */
    public Mask_Instant_Comparator(ImagePlus truthMaskIP, ImagePlus testMaskIP, boolean pixelMethod, boolean objectMethod, boolean pixelObjectMethod, double overlapMin, double overlapMax, double overlapInc, double minSize, double maxSize) {
        this.truthMaskIP = truthMaskIP;
        truthMaskIP.setRoi((Roi) null);
        this.testMaskIP = testMaskIP;
        testMaskIP.setRoi((Roi) null);
        this.pixelMethod = pixelMethod;
        this.objectMethod = objectMethod;
        this.pixelObjectMethod = pixelObjectMethod;
        this.overlapMin = overlapMin;
        this.overlapMax = overlapMax;
        this.overlapInc = overlapInc;
        this.minSize = minSize;
        this.maxSize = maxSize;

        this.testRois = null;
        this.truthRois = null;
    }

    /**
     * Constructor to use outside of GUI
     *
     * @param truthMaskPath     : Path of the mask manually segmented.
     * @param testMaskPath      : Path of the mask automatically segmented.
     * @param pixelMethod       : use Pixel method to compare both masks
     * @param objectMethod      : use Object method to compare both mask
     * @param pixelObjectMethod : use Mixe method (Object Pixel) to compare both masks
     * @param overlapMin        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     * @param overlapMax        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     * @param overlapInc        : proportion of overlapping pixel for Mixe method to consider an object as true positive
     */
    public Mask_Instant_Comparator(String truthMaskPath, String testMaskPath, boolean pixelMethod, boolean objectMethod, boolean pixelObjectMethod, double overlapMin, double overlapMax, double overlapInc) {
        this(getImage(truthMaskPath, true), getImage(testMaskPath, true), pixelMethod, objectMethod, pixelObjectMethod, overlapMin, overlapMax, overlapInc, 0, Double.POSITIVE_INFINITY);
    }

//    SETTER

    /**
     * If the user has the Rois file, they can be used directly
     *
     * @param roiPath : path of the roi file
     * @param truth   : if true, path of the truth ROIs file, else it is the test ROIs path
     */
    public void setRois(String roiPath, boolean truth) {
        /*Opens the ROIs file (normally a zip file) through the RoiManager*/
        RoiManager roiManager = RoiManager.getRoiManager();
        if (roiManager.getCount() > 0) {
//            roiManager.reset(); /*I do not use it, because it throws exception. It seems to be a problem with the GUI not updating fast enough*/
//            Even without reset, creates exception. Still in search of correction
            roiManager.runCommand("Select all");
            roiManager.runCommand("Delete");
        }
//        Test if good file path (exists and a file)
        File roiFile = new File(roiPath);
        if (roiFile.exists() && roiFile.isFile()) {
            roiManager.open(roiPath);
            if (truth) {
                truthRois = roiManager.getRoisAsArray();
                IJ.log("load truth ROIs : " + truthRois.length + " ROIs");
            } else {
                testRois = roiManager.getRoisAsArray();
                IJ.log("load test ROIs : " + testRois.length + " ROIs");
            }
        } else {
            String type = truth ? "truth" : "test";
            if (!roiFile.exists()) IJ.error("The path given for " + type + " ROIs does not exist.");
            else IJ.error("The path given for " + type + " ROIs corresponds to a directory.");
        }
    }


// METHODS/FUNCTIONS

    /**
     *
     * @param imageWidth  : width of image
     * @param imageHeight : height of image
     * @param rois        : array of ROIs to print on image
     * @return image with one intensity per ROI
     */
    public static ImageProcessor labeledImage(int imageWidth, int imageHeight, Roi[] rois) {
        ImageProcessor ip = new ShortProcessor(imageWidth, imageHeight);/*NewImage.createShortImage("labeledImage",imageWidth,imageHeight,1,NewImage.FILL_BLACK);*/
        for (int i = 0; i < rois.length; i++) {
            ip.setColor(i + 1);
            ip.fill(rois[i]);
        }
        return ip;
    }

    /**
     * Reproduce the concept of particle analyzer but considers objects with different values of intensities as different
     *
     * @param ip : image with the different objects differentiated by intensity value
     * @return array of the objects Rois
     */
    public static Roi[] oneIntensityParticleAnalyzer(ImageProcessor ip, double minSize, double maxSize) {
        ip.snapshot(); /* makes copy of image's pixel data that can be later restored*/
        int particleCount = 0;
        double tolerance = 1e-2;

//        Get dimension of images
        int width = ip.getWidth();
        int height = ip.getHeight();

        int roiType = Roi.FREEROI; /*to have freehand forms, not polygons*/

        Wand wand = new Wand(ip); /* wand selects pixel of equal or similar value*/

//        Set RoiManager
        RoiManager roiManager = RoiManager.getRoiManager();
        if (roiManager.getCount() > 0) {
//            roiManager.reset(); /*I do not use it, because it throws exception. It seems to be a problem with the GUI not updating fast enough*/
            roiManager.runCommand("Select all");
            roiManager.runCommand("Delete");
            IJ.log("reset all rois in roimanager");
        }

        double intensityValue;
        ip.setColor(0);

        /*for each line and each pixel in the line, look if it is different from 0 (i.e. the background)
         * if it is, the edge is traced with the wand to mark the object.
         * The object is added to the RoiManager and is filled with 0, so that the other pixels of the object are not remeasured*/
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                intensityValue = ip.getPixelValue(x, y);
                if (intensityValue != 0) { /*is part of an object*/
                    particleCount++;
                    wand.autoOutline(x, y, intensityValue - tolerance, intensityValue + tolerance, Wand.EIGHT_CONNECTED); /*traces boundary of area of same intensity*/
                    Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, roiType); /*create Roi from the wand outlined object*/

                    ip.fill(roi); /*fill the object to be "background"*/
                    ip.setRoi(roi); /*show the object identified on the image*/
                    ImageStatistics stats = ImageStatistics.getStatistics(ip);
                    //IJ.log("#"+intensityValue+" count:"+stats.pixelCount+" minSize:"+minSize+" maxSize"+maxSize);
                    if (stats.pixelCount >= minSize && stats.pixelCount <= maxSize) {
                        roiManager.add(roi, particleCount);
                    }
                }
            }
        }
        ip.reset(); /*return image to initial state without the objects filled*/

        return roiManager.getRoisAsArray();
    }

    /**
     * Creates composite image to compare visually segmentation
     *
     * @param truthMaskIP : truth segmentation image
     * @param testMaskIP  : segmentation to test image
     * @return composite image to compare both segmentation visually
     */
    public static ImagePlus combineImages(ImagePlus truthMaskIP, ImagePlus testMaskIP) {
        ImageStack testStack = new ImageStack();
        ImageStack truthStack = new ImageStack();
        for (int slice = 1; slice <= truthMaskIP.getNSlices(); slice++) {
            truthMaskIP.setSlice(slice);
            testMaskIP.setSlice(slice);
            ByteProcessor truthMaskBinary = getMaskBinary(truthMaskIP);
            ByteProcessor testMaskBinary = getMaskBinary(testMaskIP);
            truthStack.addSlice(truthMaskBinary);
            testStack.addSlice(testMaskBinary);
        }

//        --> Create ImagePlus from the ByteProcessor
        ImagePlus truthMaskBinaryIP = new ImagePlus(truthMaskIP.getShortTitle() + "_binary", truthStack);
        ImagePlus testMaskBinaryIP = new ImagePlus(testMaskIP.getShortTitle() + "_binary", testStack);

//        Create composite image from both binary mask ImagePlus
        ImagePlus composite = RGBStackMerge.mergeChannels(new ImagePlus[]{testMaskBinaryIP, truthMaskBinaryIP}, true);
        composite.setTitle(testMaskIP.getShortTitle() + "_composite");
        IJ.log("In the composite image : the truth mask corresponds to the green and test mask to the red");
        return composite;
    }

    private static ByteProcessor getMaskBinary(ImagePlus maskIP) {
        //        Create binary image (essentially in case of image with one intensity per particle)
//        --> threshold image
        maskIP.resetDisplayRange();
        maskIP.getProcessor().setThreshold(1, maskIP.getProcessor().getMax());

//        --> create ByteProcessor
        return maskIP.getProcessor().createMask();
    }

    /**
     * Verify both images have the same size and combine all the results of the comparison methods
     */
    public boolean analysis() {
        IJ.log("Truth image:" + truthMaskIP.getTitle());
        IJ.log("Test image:" + testMaskIP.getTitle());
        boolean isStack;
//        VERIFICATIONS
//        -->if exists
        if (testMaskIP == null || truthMaskIP == null) return false;
        if (truthMaskIP.getType() == ImagePlus.COLOR_RGB || truthMaskIP.getType() == ImagePlus.COLOR_256) {
            IJ.error("the truth mask should be in gray levels (one value per object for instance segmentation)!");
            return false;
        }
        if (testMaskIP.getType() == ImagePlus.COLOR_RGB || testMaskIP.getType() == ImagePlus.COLOR_256) {
            IJ.error("the test mask should be in gray levels (one value per object for instance segmentation)!");
            return false;
        }
        IJ.log("truth is Stack " + truthMaskIP.isStack());
        IJ.log("test is Stack " + testMaskIP.isStack());
//        --> if same size
        if ((testMaskIP.getProcessor().getHeight() != truthMaskIP.getProcessor().getHeight())
                || (testMaskIP.getProcessor().getWidth() != truthMaskIP.getProcessor().getWidth())) {
            IJ.error("The images : " + truthMaskIP.getTitle() + " and " + testMaskIP.getTitle() + " do not have the same size");
            return false;
        }
        if(testMaskIP.getNChannels() != truthMaskIP.getNChannels()){
            IJ.error("Both images have different number of channels.");
            return false;
        }
        if(testMaskIP.getNFrames() != truthMaskIP.getNFrames()){
            IJ.error("Both images have different number of frames.");
            return false;
        }
        if(testMaskIP.getNSlices() != truthMaskIP.getNSlices()){
            IJ.error("Both images have different number of slices.");
            return false;
        }
//        --> if stack
        if (testMaskIP.isStack() && truthMaskIP.isStack()) {
            isStack = true;/*both stacks*/

        } else if (!testMaskIP.isStack() && !truthMaskIP.isStack()) { /*none is a stack*/
            isStack = false;
        } else {/*only one ImagePlus is a stack*/
            IJ.error("Only one image is a stack.");
            return false;
        }
        setLUT(truthMaskIP);
        setLUT(testMaskIP);

//        STACK
        //if (isStack){
        int[] dimensions = truthMaskIP.getDimensions(); //[width,height,channels, slices, frames]
        for (int c = 0; c < dimensions[2]; c++) {
            for (int t = 0; t < dimensions[4]; t++) {
//            Iterate on slices
                for (int nrSlice = 1; nrSlice <= dimensions[3]; nrSlice++) {
                    if (nrSlice > 1) {
                        resultsTable.incrementCounter();
                        pixelObjectResultsTable.incrementCounter();
                    }
//                --> If ROI given by user, filter the ROIs according to slice
                    Roi[] truthRoiStackTemp = null;
                    Roi[] testRoiStackTemp = null;
                    if (truthRois != null) {
                        if (truthMaskIP.getNSlices() > 1) truthRoiStackTemp = setSliceRoi(truthRois, c, t, nrSlice);
                        else truthRoiStackTemp = setSliceRoi(truthRois, 0, 0, 0);
                    }
                    if (testRois != null) {
                        if (testMaskIP.getNSlices() > 1) testRoiStackTemp = setSliceRoi(testRois, c, t, nrSlice);
                        else testRoiStackTemp = setSliceRoi(testRois, 0, 0, 0);
                    }
//                --> Set colum with images names*/
                    resultsTable.addValue("Truth image", truthMaskIP.getTitle());
                    resultsTable.addValue("Test image", testMaskIP.getTitle());
                    resultsTable.addValue("Channel", c);
                    resultsTable.addValue("Frame", t);
                    resultsTable.addValue("Slice number", nrSlice);
                    resultsTable.addValue("minimum distance to border", minDist);
                    resultsTable.addValue("minimum size of objects", minSize);

//                --> Do the comparisons by changing the slice displayed (and so the main image processor)
                    if (truthMaskIP.isStack()) {
                        truthMaskIP.setPosition(c+1,nrSlice,t+1);
                        testMaskIP.setPosition(c+1,nrSlice,t+1);
                    }
                    pairComparisonChoice(c, t, nrSlice, truthMaskIP.getProcessor(), testMaskIP.getProcessor(), truthRoiStackTemp, testRoiStackTemp);
                    resultsTable.incrementCounter();
                    if (pixelObjectResultsTable != null) pixelObjectResultsTable.incrementCounter();
                }
            }
        }
        //remove last line of resultsTable
        resultsTable.deleteRow(resultsTable.getCounter() - 1);
        if (pixelObjectResultsTable != null)
            pixelObjectResultsTable.deleteRow(pixelObjectResultsTable.getCounter() - 1);
        if (objectCorrespondanceTable != null)
            objectCorrespondanceTable.deleteRow(objectCorrespondanceTable.getCounter() - 1);


        setLUT(truthMaskIP);
        setLUT(testMaskIP);
        return true;
    }

    /**
     * Filter Rois from array to only keep the one attached to a specific slice
     *
     * @param roisAll : array containing all the Rois
     * @param nrSlice : slice of the Rois we want to keep
     */
    private Roi[] setSliceRoi(Roi[] roisAll, int channel, int frame, int nrSlice) {
        ArrayList<Roi> roiArrayList = new ArrayList<>();
        for (Roi roi : roisAll) {
            //IJ.log("roi : "+roi.getZPosition());
            boolean matchC = roi.getCPosition() == 0 || roi.getCPosition() == channel;
            boolean matchZ = roi.getZPosition() == 0 || roi.getZPosition() == nrSlice;
            boolean matchT = roi.getTPosition() == 0 || roi.getTPosition() == frame;
            if(matchC && matchZ && matchT) roiArrayList.add(roi);
        }
        Roi[] roiArray = new Roi[roiArrayList.size()];
        for (int roi = 0; roi < roiArrayList.size(); roi++) {
            roiArray[roi] = roiArrayList.get(roi);
        }
        return roiArray;
    }

    private void setRoisInRoiManager(ArrayList<Roi[]> rois) {
        RoiManager rm = RoiManager.getInstance();
        if (rm.getCount() > 0) {
            rm.runCommand("Select all");
            rm.runCommand("Delete");
        }
        if (truthMaskIP.isStack()) {
            IJ.log("set rois in roimanager with stack position");
            for (int z = 0; z < truthMaskIP.getNSlices(); z++) {
                truthMaskIP.setSlice(z + 1);
                Roi[] roisArray = rois.get(z);
                IJ.log("adding " + roisArray.length + " rois to image " + (z + 1));
                for (Roi r : roisArray) {
                    r.setPosition(0, z + 1, 0);
                    rm.addRoi(r);
                }
            }
        } else {
            IJ.log("set rois in roi manager (single image)");
            Roi[] roisArray = rois.get(0);
            for (Roi r : roisArray) {
                rm.addRoi(r);
            }
        }
    }

    private void setLUT(ImagePlus imp) {
        IndexColorModel cm = LutLoader.getLut("3-3-2 RGB");
        imp.getProcessor().resetMinAndMax();
        if (imp.getNSlices() > 1) {
            imp.getImageStack().setColorModel(cm);
            imp.getProcessor().setColorModel(cm);
        } else {
            imp.getProcessor().setColorModel(cm);
        }
        imp.updateAndRepaintWindow();
    }


    /**
     * Analysis of an image processor according to choice of method
     * The results are displayed in a {@link ResultsTable}
     *
     * @param truthMaskProc : processor of truth image to compare
     * @param testMaskProc  : processor of test image to compare
     * @param truthRois     : array of ROIs delimiting truth particles
     * @param testRois      : array of ROIs delimiting test particles
     */
    private void pairComparisonChoice(int channel, int time, int nrSlice, ImageProcessor truthMaskProc, ImageProcessor testMaskProc, Roi[] truthRois, Roi[] testRois) {
        if(useLabelMaskCalculation()){
            pairComparisonChoiceLabel(channel, time, nrSlice, truthMaskProc, testMaskProc, truthRois, testRois);
        }else{
            pairComparisonChoiceROI(channel, time, nrSlice, truthMaskProc, testMaskProc, truthRois, testRois);
        }

    }

    private void pairComparisonChoiceLabel(int channel, int time, int nrSlice, ImageProcessor truthMaskProc, ImageProcessor testMaskProc, Roi[] truthRois, Roi[] testRois){
        ImagePlus truth = new ImagePlus("truth", truthMaskProc.duplicate());
        ImagePlus test = new ImagePlus("test", testMaskProc.duplicate());

        IoUAnalysis analysis = IoUAnalysis.create(truth, test, minSize, minDist);
        AnalysisResult result = analysis.computeAnalysisResult(overlapMin,overlapMax,overlapInc);
        result.setChannel(channel);
        result.setFrame(time);
        result.setSlice(nrSlice);

        resultsTable.addValue("Truth objects", analysis.getMaxTruth());
        resultsTable.addValue("Test objects", analysis.getMaxTest());

        if(pixelMethod){
            //Metrics metrics = analysis.getPixelMetrics();
            Metrics metrics = result.getPixelMetrics();
            resultDisplay.addMetric("Pixel", metrics);
        }
        if(objectMethod){
            //Metrics metrics = analysis.getMetrics(0.5);
            Metrics metrics = result.getObjectMetrics();
            addToResultTable(resultsTable,"Object (IoU=0.5)",metrics.getTP(),metrics.getFP(),metrics.getFN(),
                    metrics.getPrecision(),metrics.getSensitivity(),metrics.getJaccardIndex(),metrics.getF1measure(),-1);
        }
        if(pixelObjectMethod) {
            Metrics[] curveMetrics = result.getCurveMetrics();
            double[] thresholds = result.getThresholds();

            resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)+FN(IoU)))", result.getMeanJaccard());
            resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)))", result.getMeanPrecision());
            resultDisplay.accumulate(result);

            if(showGraphs) resultDisplay.addPlot(result, truthMaskIP.getShortTitle() + "/" + testMaskIP.getShortTitle());

            for(int i = 0; i < thresholds.length; i++){
                if(i != 0) pixelObjectResultsTable.incrementCounter();

                pixelObjectResultsTable.addValue("Truth image", truthMaskIP.getTitle());
                pixelObjectResultsTable.addValue("Test image", testMaskIP.getTitle());
                pixelObjectResultsTable.addValue("channel", channel);
                pixelObjectResultsTable.addValue("frame", time);
                pixelObjectResultsTable.addValue("Slice number", nrSlice);
                pixelObjectResultsTable.addValue("Truth objects", analysis.getMaxTruth());
                pixelObjectResultsTable.addValue("Test objects", analysis.getMaxTest());

                Metrics m = curveMetrics[i];

                addToResultTable(pixelObjectResultsTable,"Object", m.getTP(), m.getFP(), m.getFN(),
                        m.getPrecision(), m.getSensitivity(), m.getJaccardIndex(), m.getF1measure(), thresholds[i]);
                pixelObjectResultsTable.addValue("AP = precision*sensitivity", m.getAP());
            }
        }
    }

    private void pairComparisonChoiceROI(int channel, int time, int nrSlice, ImageProcessor truthMaskProc, ImageProcessor testMaskProc, Roi[] truthRois, Roi[] testRois) {
//            Set ROIs  and if object type comparison add a colum with the number of found objects
        if (truthRois == null) {
            IJ.log("convert Truth mask to Rois");
            truthRois = oneIntensityParticleAnalyzer(truthMaskProc, minSize, maxSize);
            IJ.log(truthRois.length + " Rois in Truth mask");
        } else {
            IJ.log("comparison truth ROIs: " + truthRois.length + " no need to create from image");
        }
        if (testRois == null) {
            IJ.log("convert Test mask to Rois");
            testRois = oneIntensityParticleAnalyzer(testMaskProc, minSize, maxSize);
            IJ.log(testRois.length + " Rois in Test mask");
        } else {
            IJ.log("comparison test ROIs: " + testRois.length + " no need to create from image");
        }
        if (objectMethod) {
            resultsTable.addValue("Truth objects", truthRois.length);
            resultsTable.addValue("Test objects", testRois.length);
        }
        truthMaskProc = labeledImage(truthMaskProc.getWidth(), truthMaskProc.getHeight(), truthRois);
        testMaskProc = labeledImage(testMaskProc.getWidth(), testMaskProc.getHeight(), testRois);

        allTruthRoi.add(truthRois);
//            LAUNCH METHODS
        int indexComposite = 1;
        if (pixelMethod) {
            pixelComparison(truthMaskProc, testMaskProc);
            if (compositeImage != null)
                addCompositePixels(compositeImage[channel], indexComposite, channel, time, nrSlice);
        }
        if (objectMethod || pixelObjectMethod) {
            int[] objectAssignation = new int[truthRois.length];
            double[] overlapPercents = new double[truthRois.length];
            objectAssignation(truthRois, testRois, objectAssignation, overlapPercents);
            boolean[] validTruth = new boolean[truthRois.length];
            boolean[] validTest = new boolean[testRois.length];
            validateRoisToBorder(truthRois, validTruth, minDist, truthMaskProc.getWidth(), truthMaskProc.getHeight());
            validateRoisToBorder(testRois, validTest, minDist, truthMaskProc.getWidth(), truthMaskProc.getHeight());
            if (showCorrespondances) {
                addCorrespondenceToTable(truthMaskIP.getTitle(), channel, time, nrSlice, truthRois, objectAssignation, overlapPercents, validTruth, objectCorrespondanceTable);
                objectCorrespondanceTable.incrementCounter();
            }
            if (objectMethod) {
                Metrics objectMetrics = computeROIMetricsAtThreshold(truthRois, testRois, objectAssignation, validTruth, validTest, overlapPercents, 0.5);
                indexComposite++;
                if(compositeImage != null) addCompositeObjects(compositeImage[channel], indexComposite, channel, time, nrSlice, truthRois, testRois, objectAssignation, validTruth, validTest, overlapPercents, 0.5);
                addToResultTable(resultsTable, "Object (IoU=0.5)", objectMetrics.getTP(), objectMetrics.getFP(), objectMetrics.getFN(), objectMetrics.getPrecision(), objectMetrics.getSensitivity(), objectMetrics.getJaccardIndex(), objectMetrics.getF1measure(), -1);

            }
            if (pixelObjectMethod) {

                double[] thresholds = buildThresholds(overlapMin, overlapMax, overlapInc);
                Metrics[] curveMetrics = computeROIMetricsCurve(truthRois, testRois, objectAssignation, validTruth, validTest, overlapPercents, thresholds);

                AnalysisResult result = new AnalysisResult();
                result.setCurveMetrics(curveMetrics);
                result.setThresholds(thresholds);
                result.setChannel(channel);
                result.setFrame(time);
                result.setSlice(nrSlice);
                //computes mAP folowing definitions in Hirling et al, Nature methods 2022
                //AP1 cannot be measured without confidence fixed IoU
                //mAP1 cannot be computed without confidence average of AP1 for all classes (fixed IoU)
                //AP2 correspond to jaccard index
                //mAP2 corresponds to average of jaccard index for all IoU
//                double mAP2 = 0;
//                for (double AP_2 : jaccardIndex) mAP2 += AP_2;
//                mAP2 /= jaccardIndex.length;
                resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)+FN(IoU)))", result.getMeanJaccard());
                //AP3 corresponds to the average of precision for all IoU
//                double mAP3 = 0;
//                for (double AP_3 : precision) mAP3 += AP_3;
//                mAP3 /= precision.length;
                resultsTable.addValue("mAP = 1/NIoU * sum(TP(IoU)/(TP(IoU)+FP(IoU)))", result.getMeanPrecision());
                //mAP4 precision x recall
                //mAP5 corresponds to the average of jaccard index for all slices
                // AP4 COCO metric cannot compute without confidence average of AP1 for all IoU
                //mAP6 = AP5 = average of AP4 for all classes cannot compute without confidence
                //store for global measure
                /*if (tps == null) tps = new double[nbIndexes];
                if (fns == null) fns = new double[nbIndexes];
                if (fps == null) fps = new double[nbIndexes];
                if (this.thresholds == null) this.thresholds = thresholds;
                for(int pos = 0; pos < nbIndexes; pos++){
                    Metrics m = result.getCurveMetric(pos);
                    tps[pos] += m.getTP();
                    fps[pos] += m.getFP();
                    fns[pos] += m.getFN();
                }*/
                resultDisplay.accumulate(result);
                //create object Images
                if (compositeImage != null) {
                    for (double range : thresholds) {
                        indexComposite++;
                        addCompositeObjects(compositeImage[channel], indexComposite, channel, time, nrSlice, truthRois, testRois, objectAssignation, validTruth, validTest, overlapPercents, range);
                    }
                }
                //create graphs
                if(showGraphs) resultDisplay.addPlot(result, truthMaskIP.getShortTitle() + "/" + testMaskIP.getShortTitle());
                //add to result table
                for (int i = 0; i < thresholds.length; i++) {
                    if (i != 0) pixelObjectResultsTable.incrementCounter();
                    pixelObjectResultsTable.addValue("Truth image", truthMaskIP.getTitle());
                    pixelObjectResultsTable.addValue("Test image", testMaskIP.getTitle());
                    pixelObjectResultsTable.addValue("channel", channel);
                    pixelObjectResultsTable.addValue("frame", time);
                    pixelObjectResultsTable.addValue("Slice number", nrSlice);
                    pixelObjectResultsTable.addValue("Truth objects", truthRois.length);
                    pixelObjectResultsTable.addValue("Test objects", testRois.length);
                    //addToResultTable(pixelObjectResultsTable, "Object", tp[i], fp[i], fn[i], precision[i], sensitivity[i], jaccardIndex[i], fmeasure[i], thresholds[i]);
                    Metrics m = result.getCurveMetric(i);
                    addToResultTable(pixelObjectResultsTable, "Object", m.getTP(), m.getFP(), m.getFN(),
                            m.getPrecision(), m.getSensitivity(), m.getJaccardIndex(), m.getF1measure(), thresholds[i]);
                    //pixelObjectResultsTable.addValue("AP = precision*sensitivity", precision[i] * sensitivity[i]);
                    pixelObjectResultsTable.addValue("AP = precision*sensitivity", m.getAP());
                }
            }
        }
    }

    private void objectAssignation(Roi[] truthRois, Roi[] testRois, int[] correspondance, double[] overlapPercent) {
        Arrays.fill(correspondance, -1);
        Arrays.fill(overlapPercent, -1);

        for (int truthIndex = 0; truthIndex < truthRois.length; truthIndex++) {
            Roi truthRoi = truthRois[truthIndex];
            for (int testIndex = 0; testIndex < testRois.length; testIndex++) {
                Roi testRoi = testRois[testIndex];
                double overlap = compare2Rois(truthRoi, testRoi);
                //IJ.log("truth: "+truthIndex+"\t test: "+testIndex+"\t overlap: "+overlap);
                if (overlap > 0 && overlapPercent[truthIndex] < overlap) {
                    int checkAlreadyExistingIndex = findIndexOf(correspondance, testIndex);
                    if (checkAlreadyExistingIndex == -1) {
                        correspondance[truthIndex] = testIndex;
                        overlapPercent[truthIndex] = overlap;
                    } else {
                        //IJ.log("exist already: "+checkAlreadyExistingIndex+" with overlap:"+overlapPercent[checkAlreadyExistingIndex]);
                        if (overlap > overlapPercent[checkAlreadyExistingIndex]) {
                            correspondance[truthIndex] = testIndex;
                            overlapPercent[truthIndex] = overlap;
                            correspondance[checkAlreadyExistingIndex] = -1;
                            overlapPercent[checkAlreadyExistingIndex] = -1;
                        }
                    }
                }
            }
        }

    }

    private void addCorrespondenceToTable(String name, int channel, int time, int slice, Roi[] rois, int[] correspondance, double[] overlapPercent, boolean[] valid, ResultsTable rt) {
        for (int i = 0; i < correspondance.length; i++) {
            if (i != 0) rt.incrementCounter();
            rt.addValue("image", name);
            rt.addValue("channel", channel);
            rt.addValue("frame", time);
            rt.addValue("slice", slice);
            rt.addValue("roi truth", i);
            rt.addValue("centerX", rois[i].getBounds().getCenterX());
            rt.addValue("centerY", rois[i].getBounds().getCenterY());
            Rectangle roi = rois[i].getBounds();
            double dist = Math.min(roi.getCenterX(), truthMaskIP.getWidth() - roi.getCenterX());
            dist = Math.min(dist, Math.min(roi.getCenterY(), truthMaskIP.getHeight() - roi.getCenterY()));
            rt.addValue("center distance to border", dist);
            rt.addValue("distance validated (1 for true)", (valid[i]) ? 1 : 0);
            rt.addValue("corresponding roi test", correspondance[i]);
            rt.addValue("IoU", overlapPercent[i]);
        }
    }

    public double compare2Rois(Roi truthRoi, Roi testRoi) {
        //test bounding box
        Rectangle truthRect = truthRoi.getBounds(); /*Boundings of truth Roi*/
        Rectangle testRect = testRoi.getBounds(); /*Boundings of test Roi*/
        if (truthRect.intersects(testRect)) {
            Point[] truthPoints = truthRoi.getContainedPoints();
            Point[] testPoints = testRoi.getContainedPoints();
            double totalTruth = truthPoints.length;
            double totalTest = testPoints.length;
            double countCommon = 0;
            for (Point p : testPoints) {
                if (truthRoi.containsPoint(p.getX(), p.getY())) {
                    countCommon++;
                }
            }
            if (countCommon == 0) return -1;
            return countCommon / (totalTruth + totalTest - countCommon);// percentage of overlap
            //the denominator corresponds to the total pixels in the 2 Rois minus the overlap that is counted two times
        } else {
            return -1;
        }
    }

    private void validateRoisToBorder(Roi[] rois, boolean[] valid, double distanceThreshold, int imgWidth, int imgHeight) {
        for (int i = 0; i < rois.length; i++) {
            valid[i] = isDistanceToBorderOK(rois[i], distanceThreshold, imgWidth, imgHeight);
        }
    }

    private boolean isDistanceToBorderOK(Roi roi, double distanceThreshold, int imgWidth, int imgHeight) {
        Rectangle box = roi.getBounds();
        if (box.getCenterX() < distanceThreshold || box.getCenterX() >= imgWidth - distanceThreshold) return false;
        if (box.getCenterY() < distanceThreshold || box.getCenterY() >= imgHeight - distanceThreshold) return false;
        if (distanceThreshold >= 0 && (box.getX() == 0 || box.getX() + box.getWidth() >= imgWidth)) return false;
        return !(distanceThreshold >= 0) || (box.getY() != 0 && !(box.getY() + box.getHeight() >= imgHeight));
    }

    private void createCompositeStack() {
        IJ.log("create composite");
        int nbIndexes = ((pixelObjectMethod) ? (int) Math.round((overlapMax - overlapMin) / overlapInc + 1) : 0);
        IJ.log("nbIndexes:" + nbIndexes);
        nbIndexes += ((pixelMethod) ? 1 : 0);
        IJ.log("nbIndexes:" + nbIndexes);
        nbIndexes += ((objectMethod) ? 1 : 0);
        IJ.log("nbIndexes:" + nbIndexes);
        IJ.log("nb channels : " + truthMaskIP.getNChannels());
        IJ.log("nb frames : " + truthMaskIP.getNFrames());
        IJ.log("nb z: " + truthMaskIP.getNSlices());
        IJ.log("create " + truthMaskIP.getNChannels() + " composite stack (one per channel)");
        compositeImage = new ImagePlus[truthMaskIP.getNChannels()];
        for (int channel = 1; channel <= truthMaskIP.getNChannels(); channel++) {
            compositeImage[channel - 1] = IJ.createImage("C" + channel + "_display of masks " + truthMaskIP.getTitle() + "__VS__" + testMaskIP.getTitle(), "composite", truthMaskIP.getWidth(), truthMaskIP.getHeight(), nbIndexes, truthMaskIP.getNSlices(), truthMaskIP.getNFrames());
        }

    }






    private double[] buildThresholds(double rangeMin, double rangeMax, double rangeIncrement){
        int nbIndexes = (int)Math.round((rangeMax - rangeMin) / rangeIncrement + 1);
        double[] thresholds = new double[nbIndexes];
        int index = 0;
        for(double range = rangeMin; range <= rangeMax + 0.000001; range += rangeIncrement){
            thresholds[index] = Math.round(range * 10000.0) / 10000.0;
            index++;
        }
        return thresholds;
    }

    private Metrics computeROIMetricsAtThreshold(Roi[] truthRois, Roi[] testRois, int[] correspondence, boolean[] validTruth, boolean[] validTest, double[] overlapPercent, double threshold){
        int tp = 0;
        int validTru = 0;
        int validTe = 0;
        for(int i = 0; i < correspondence.length; i++){
            if(validTruth[i]){
                validTru++;
                if(correspondence[i] >= 0){
                    if(overlapPercent[i] >= threshold) tp++;
                    validTe++;
                }
            }
        }
        for(int i = 0; i < testRois.length; i++){
            if(validTest[i]){
                int j = findIndexOf(correspondence, i);
                if(j < 0) validTe++;
            }
        }
        int fp = validTe - tp;
        int fn = validTru - tp;
        return new Metrics(tp, fp, fn);
    }
    private Metrics[] computeROIMetricsCurve(Roi[] truthRois, Roi[] testRois, int[] correspondence, boolean[] validTruth, boolean[] validTest, double[] overlapPercent, double[] thresholds){
        Metrics[] metrics = new Metrics[thresholds.length];
        for(int i = 0; i < thresholds.length; i++){
            metrics[i] = computeROIMetricsAtThreshold(truthRois, testRois, correspondence, validTruth, validTest, overlapPercent, thresholds[i]);
        }
        return metrics;
    }


    private void addCompositePixels(ImagePlus imp, int index, int channel, int time, int slice) {
        IJ.log("add composite from pixels");
        truthMaskIP.setPosition(channel + 1, slice, time + 1);
        testMaskIP.setPosition(channel + 1, slice, time + 1);
        ByteProcessor truthMaskBinary = getMaskBinary(truthMaskIP);
        ByteProcessor testMaskBinary = getMaskBinary(testMaskIP);
        ColorProcessor col = new ColorProcessor(truthMaskIP.getWidth(), truthMaskIP.getHeight());
        col.setChannel(1, testMaskBinary);
        col.setChannel(2, truthMaskBinary);
        ImagePlus rgb = new ImagePlus("rgb", col);
        ImageConverter converter = new ImageConverter(rgb);
        converter.convertRGBtoIndexedColor(256);
        imp.getImageStack().getProcessor(imp.getStackIndex(index, slice, time +1)).copyBits(rgb.getProcessor(), 0, 0, Blitter.COPY);

        CompositeImage ci = (CompositeImage) imp;
        ci.setChannelLut(rgb.getProcessor().getLut(), index);
        imp.resetDisplayRange();
    }

    private void addCompositeObjects(ImagePlus imp, int index, int channel, int time, int slice, Roi[] truthRois, Roi[] testRois, int[] correspondence, boolean[] validTruth, boolean[] validTest, double[] overlapPercent, double threshold) {
        IJ.log("add composite from objects with threshold " + threshold);
        ColorProcessor col = new ColorProcessor(truthMaskIP.getWidth(), truthMaskIP.getHeight());
        ByteProcessor truthMaskBinary = new ByteProcessor(truthMaskIP.getWidth(), truthMaskIP.getHeight());
        ByteProcessor testOKMaskBinary = new ByteProcessor(truthMaskIP.getWidth(), truthMaskIP.getHeight());
        ByteProcessor testKOMaskBinary = new ByteProcessor(truthMaskIP.getWidth(), truthMaskIP.getHeight());

        for (int i = 0; i < correspondence.length; i++) {
            if (validTruth[i]) {
                if (correspondence[i] >= 0 && overlapPercent[i] >= threshold) {
                    testOKMaskBinary.setValue(255);
                    testOKMaskBinary.fill(testRois[correspondence[i]]);
                    truthMaskBinary.setValue(255);
                    truthMaskBinary.fill(truthRois[i]);
                } else if (correspondence[i] >= 0) {
                    testKOMaskBinary.setValue(255);
                    testKOMaskBinary.fill(testRois[correspondence[i]]);
                    truthMaskBinary.setValue(255);
                    truthMaskBinary.fill(truthRois[i]);
                } else {
                    truthMaskBinary.setValue(150);
                    truthMaskBinary.fill(truthRois[i]);
                }
            } else {
                truthMaskBinary.setValue(128);
                truthMaskBinary.fill(truthRois[i]);

                if (correspondence[i] >= 0) {
                    testOKMaskBinary.setValue(128);
                    testOKMaskBinary.fill(testRois[correspondence[i]]);
                    testKOMaskBinary.setValue(128);
                    testKOMaskBinary.fill(testRois[correspondence[i]]);
                }
            }
        }
        for (int i = 0; i < testRois.length; i++) {
            int j = findIndexOf(correspondence, i);
            if (j < 0) {
                if (validTest[i]) {
                    testKOMaskBinary.setValue(255);
                    testKOMaskBinary.fill(testRois[i]);
                } else {
                    //invalidObjects.fill(testRois[i]);
                }
            }
        }
        col.setChannel(1, testOKMaskBinary);
        col.setChannel(2, truthMaskBinary);
        col.setChannel(3, testKOMaskBinary);
        ImagePlus rgb = new ImagePlus("rgb", col);
        ImageConverter converter = new ImageConverter(rgb);
        converter.convertRGBtoIndexedColor(256);
        //rgb.show();
        imp.getImageStack().getProcessor(imp.getStackIndex(index, slice, time+1)).copyBits(rgb.getProcessor(), 0, 0, Blitter.COPY);
        CompositeImage ci = (CompositeImage) imp;
        ci.setChannelLut(rgb.getProcessor().getLut(), index);
        //imp.getImageStack().getProcessor(imp.getStackIndex(index,slice,time)).copyBits(col,0,0,Blitter.COPY);
    }

    private int findIndexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        return -1;
    }


    /**
     * compares each pixel of the test image to the truth one, to measure the difference between them
     */
    private void pixelComparison(ImageProcessor truthMaskProc, ImageProcessor testMaskProc) {
        double truePositive = 0;
        double falsePositive = 0;
        double falseNegative = 0;
        int[][] truthPixelsValues = truthMaskProc.getIntArray();
        int[][] testPixelsValues = testMaskProc.getIntArray();

        /*for each pixel compare value in truth and test values*/
        for (int x = 0; x < truthMaskProc.getWidth(); x++) {
            for (int y = 0; y < truthMaskProc.getHeight(); y++) {
                int truthValue = truthPixelsValues[x][y];
                int testValue = testPixelsValues[x][y];
                if (truthValue != 0) {
                    if (testValue != 0) { /*both images' pixel corresponds to an object*/
                        truePositive++;
                    } else { /*object of truth not found in test*/
                        falseNegative++;
                    }
                } else {
                    if (testValue != 0) { /*object in test absent in truth*/
                        falsePositive++;
                    }
                }
            }
        }
        addToResultTable("Pixel", truePositive, falsePositive, falseNegative);
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
                image.setDisplayRange(0, findMaxValue(image.getProcessor().getHistogram()) + 10);
                image.updateAndDraw(); /*update the display*/
            }
            return image;
        } else {
            if (!imageFile.exists()) IJ.error("The path " + imagePath + " does not exist.");
            else IJ.error("The path " + imagePath + " corresponds to a directory, not a file.");
            return null;
        }
    }



    /**
     * find maximum value of int array (used to find the max value for display of image)
     *
     * @param histogram : histogram values of an image
     * @return the max value
     */
    public static int findMaxValue(int[] histogram) {
        int maxValue = 0;
        for (int pixelValue = 0; pixelValue < histogram.length; pixelValue++)
            maxValue = (histogram[pixelValue] > 0) ? pixelValue : maxValue;
        if (maxValue == 0) IJ.error("The image is only background");
        return maxValue;
    }

//    GENERIC DIALOG FUNCTIONS

    /**
     * Get choices from user
     *
     * @param useOpenImages : if the user want to use the opened images
     * @param gd            : generic dialog
     */
    private void getChoicesFromGD(GenericDialog gd, boolean useOpenImages) {
//        Get truth and test images' and ROIs' paths
        String truthMaskPathOrTitle;
        String testMaskPathOrTitle;

        if (useOpenImages) truthMaskPathOrTitle = gd.getNextChoice();
        else truthMaskPathOrTitle = gd.getNextString();

        if (useOpenImages) testMaskPathOrTitle = gd.getNextChoice();
        else testMaskPathOrTitle = gd.getNextString();

        boolean showImage;
        if (!useOpenImages) showImage = gd.getNextBoolean();
        else showImage = false;
        String truthRoiTemp = "";
        String testRoiTemp = "";
        if (!useOpenImages) {
            truthRoiTemp = gd.getNextString();
            testRoiTemp = gd.getNextString();
            if (!truthRoiTemp.equals("")) IJ.log("will load truth ROI: " + truthRoiTemp);
            if (!testRoiTemp.equals("")) IJ.log("will load test ROI: " + testRoiTemp);
        }

//        Get methods to use
        pixelMethod = gd.getNextBoolean();
        objectMethod = gd.getNextBoolean();
        pixelObjectMethod = gd.getNextBoolean();

        IJ.log("pixel method:" + pixelMethod + "\nobject:" + objectMethod + "\npixel/obejct:" + pixelObjectMethod);

//        Get tolerance of error for pixelobject method and choice for showing images
        overlapMin = gd.getNextNumber();
        overlapMax = gd.getNextNumber();
        overlapInc = gd.getNextNumber();

        boolean showComposite = gd.getNextBoolean();
        showGraphs = gd.getNextBoolean();
        showSummary = gd.getNextBoolean();
        showCorrespondances = gd.getNextBoolean();

//        Set images
        if (useOpenImages) truthMaskIP = WindowManager.getImage(truthMaskPathOrTitle);
        else truthMaskIP = getImage(truthMaskPathOrTitle, showImage);
        if (useOpenImages) testMaskIP = WindowManager.getImage(testMaskPathOrTitle);
        else testMaskIP = getImage(testMaskPathOrTitle, showImage);

        minSize = gd.getNextNumber();
        maxSize = Double.POSITIVE_INFINITY;
        minDist = gd.getNextNumber();
        IJ.log("minimum size of objects: " + minSize);
        IJ.log("minimum distance to border: " + minDist);
        String mode = gd.getNextChoice();
        calculationMode = mode.equals("Label_mask_based") ? CalculationMode.LABEL_MASK_BASED : CalculationMode.ROI_BASED;
        IJ.log("Object calculation mode : " + calculationMode);

//        Set ROIs
        if (!truthRoiTemp.equals("")) setRois(truthRoiTemp, true);
        if (!testRoiTemp.equals("")) setRois(testRoiTemp, false);
        if (showComposite && truthMaskIP.getNSlices() == testMaskIP.getNSlices()) createCompositeStack();

        if (!truthMaskIP.isStack() || !testMaskIP.isStack()) showSummary = false;
    }

    public void saveResults(String path) {
        resultsTable.save(path);
        resultsTable.reset();
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
            gd.setInsets(10, 250, 0);
            gd.addImage(IJ.openImage(getClass().getResource("/logo_MiC_50.png").toURI().toString()));
            //gd.addToSameRow();
            gd.addMessage("Developped by Cédric Messaoudi from the Multimodal Imaging Center - Institut Curie (France)");
            //gd.addToSameRow();
            //gd.addImage(IJ.openImage(getClass().getResource("/MIC_IC_logo.png").toURI().toString()));

        } catch (Exception e) {
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
        if (!useOpenImages) {
            gd.addMessage("----------------------------------------------- load ROIs (optionnal) -----------------------------------------------");
            gd.addFileField("Truth_ROI_zip_path (if exists)", "");
            gd.addFileField("Test_ROI_zip_path (if exists)", "");
        }
        gd.addMessage("--------------------------------------------------     parameters     --------------------------------------------------");

//        Methods
        gd.addCheckboxGroup(1, 3, new String[]{"Pixel", "Object_(IoU=0.5)", "Object_(varying_IoU)"}, new boolean[]{true, true, true});
        gd.addNumericField("Minimum_IoU_threshold (0-1)", 0.5, 2);
        gd.addNumericField("Maximum_IoU_threshold (0-1)", 1.0, 2);
        gd.addNumericField("Increment_of_IoU_threshold (0-1)", 0.05, 2);

//        Additional choices
        gd.addMessage("-------------------------------------------------- displayed results  --------------------------------------------------");
        gd.addCheckboxGroup(1, 4, new String[]{"Show_composite_images", "Show_graphs", "Show_summary_graph (Stacks)", "Show_GT_objects_correspondence_table"}, new boolean[]{true, true, true, true});

        gd.addMessage("-------------------------------------------------- filters on objects --------------------------------------------------");
        gd.addNumericField("Minimum_size_for_objects (pixels)", 0);
        gd.addNumericField("Minimum_distance_to_border (pixels)", 0);
        gd.addChoice("Object_calculation_mode", new String[]{"ROI_based", "Label_mask_based"},
                "ROI_based"
        );

        gd.addMessage("distance to border value explanation:");
        //gd.addToSameRow();
        gd.addMessage("set -1 to remove nothing, 0 to remove objects touching borders, higher values uses the distance of truth object's center to border");

        Vector chV = gd.getCheckboxes();
        Vector numV = gd.getNumericFields();
        int offset = useOpenImages ? 0 : 1;
        Checkbox objCB = (Checkbox) chV.get(1 + offset);
        Checkbox varIoUCB = (Checkbox) chV.get(2 + offset);
        varIoUCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean varChecked = varIoUCB.getState();
                boolean objChecked = objCB.getState();
                //IJ.log("varying IoU activated: "+checked);
                ((TextField) numV.get(0)).setEnabled(varChecked);
                ((TextField) numV.get(1)).setEnabled(varChecked);
                ((TextField) numV.get(2)).setEnabled(varChecked);

                ((Checkbox) chV.get(4 + offset)).setEnabled(varChecked);
                ((Checkbox) chV.get(5 + offset)).setEnabled(varChecked);
                ((Checkbox) chV.get(6 + offset)).setEnabled(varChecked || objChecked);

                ((TextField) numV.get(3)).setEnabled(varChecked || objChecked);
                ((TextField) numV.get(4)).setEnabled(varChecked || objChecked);

                gd.repaint();
            }
        });
        objCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                boolean varChecked = varIoUCB.getState();
                boolean objChecked = objCB.getState();
                //IJ.log("varying IoU activated: "+checked);
                ((TextField) numV.get(0)).setEnabled(varChecked);
                ((TextField) numV.get(1)).setEnabled(varChecked);
                ((TextField) numV.get(2)).setEnabled(varChecked);

                ((Checkbox) chV.get(4 + offset)).setEnabled(varChecked);
                ((Checkbox) chV.get(5 + offset)).setEnabled(varChecked);
                ((Checkbox) chV.get(6 + offset)).setEnabled(varChecked || objChecked);

                ((TextField) numV.get(3)).setEnabled(varChecked || objChecked);
                ((TextField) numV.get(4)).setEnabled(varChecked || objChecked);

                gd.repaint();
            }
        });
        return gd;
    }

    private boolean useLabelMaskCalculation() {
        return calculationMode == CalculationMode.LABEL_MASK_BASED;
    }

    private boolean useROICalculation() {
        return calculationMode == CalculationMode.ROI_BASED;
    }

    /**
     * Function for running in imageJ (obligatory with PlugIn implementation)
     *
     * @param s : argument, not currently used
     */
    @Override
    public void run(String s) {
        boolean openImages;
        GenericDialog gd;
        //            SET RESULTS TABLE

        allTruthRoi = new ArrayList<>();
//        There are opened images : dialog to choose between local images or the opened ones
        boolean isOpenImage = (WindowManager.getImageCount() > 0);
        gd = getGenericDialog(isOpenImage);
        gd.showDialog();
//          --> according to choices, launch the comparison
        if (!gd.wasCanceled()) {
            getChoicesFromGD(gd, isOpenImage);
            resultDisplay = new AnalysisResultDisplay(truthMaskIP, testMaskIP);
            resultsTable = resultDisplay.getResultsTable();
            pixelObjectResultsTable = resultDisplay.getThresholdResultsTable();
            objectCorrespondanceTable = resultDisplay.getCorrespondenceTable();

            if (!analysis()) return;


            resultsTable.show("Mask comparison results");
            if (pixelObjectMethod) pixelObjectResultsTable.show("Mask comparison Object with IoU thresholds");
            if ((pixelObjectMethod || objectMethod) && showCorrespondances)
                objectCorrespondanceTable.show("Objects correspondences");

            if (showSummary) {
                resultDisplay.showSummaryGraph();
            }

            if (compositeImage != null) {
                for (ImagePlus ip : compositeImage) {
                    ip.show();
                }
            }
            if(resultDisplay != null) resultDisplay.showPlots();
            if(useROICalculation())setRoisInRoiManager(allTruthRoi);
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
    private void addToResultTable(String method, double tp, double fp, double fn) {
//        STATISTICS
        double precision = tp / (tp + fp);
        double recall = tp / (tp + fn);
        double jaccardIndex = tp / (tp + fn + fp);
        double fMeasure = 2 * precision * recall / (precision + recall);

//        ADD TO RESULT TABLE
        addToResultTable(resultsTable, method, tp, fp, fn, precision, recall, jaccardIndex, fMeasure, -1);

    }

    /**
     * Calculate the stats and add it all to the result table
     *
     * @param method : Object, Pixel or Object-Pixel
     * @param tp     : True Positives
     * @param fp     : False Positives
     * @param fn     : False Negatives
     */
    private void addToResultTable(ResultsTable resultsTable, String method, double tp, double fp, double fn, double precision, double sensitivity, double jaccardIndex, double fmeasure, double threshold) {

//        ADD TO RESULT TABLE
        if (threshold >= 0) resultsTable.addValue(method + " IoU threshold", threshold);
        resultsTable.addValue(method + " TP", tp);
        resultsTable.addValue(method + " FP", fp);
        resultsTable.addValue(method + " FN", fn);
        resultsTable.addValue(method + " Precision", precision);/*(TP/Positives)*/
        resultsTable.addValue(method + " Recall/Sensitivity", sensitivity);/*(TP/Truth)*/
        resultsTable.addValue(method + " Jaccard Index", jaccardIndex);/*(TP/(TP+FN+FP))*/
        resultsTable.addValue(method + " F-measure", fmeasure);/*(2*Precision/(precision+recall))*/

    }

}

