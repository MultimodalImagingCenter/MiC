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
import ij.io.FileInfo;
import ij.plugin.Converter;
import ij.process.*;

/**
 * Utility methods for image processing operations used by MiC.
 * Provides histogram computation, IoU calculation, and object label normalization.
 */
public class MicUtils {

    /**
     * Computes 1D histogram of object labels across all slices in an image.
     * Accumulates pixel counts for each label value.
     * 
     * @param imp ImagePlus to analyze (can be single plane or 3D stack)
     * @param max maximum expected label value (determines histogram size)
     * @return histogram array where histogram[label] = pixel count for that label
     */
    public static int[] histo1D(ImagePlus imp, int max){
        int[] histo = new int[max+1];
        if(imp.getNSlices()==1){
            return histo1D(imp.getProcessor(),histo);
        }
        ImageStack is=imp.getImageStack();
        for(int z=0;z<imp.getNSlices();z++){
            histo1D(is.getProcessor(z+1),histo);
        }
        return histo;
    }

    /**
     * Accumulates 1D histogram from a single image plane.
     * 
     * @param ip ImageProcessor to analyze
     * @param histo histogram array to accumulate into (modified in-place)
     * @return updated histogram array
     */
    public static int[] histo1D(ImageProcessor ip, int[] histo){
        int count=0;
        for(int y=0;y<ip.getHeight();y++){
            for(int x=0;x<ip.getWidth();x++){
                int val=(int)ip.getf(x,y);
                if(val<histo.length) {
                    histo[val]++;
                }else{
                    count++;
                }
            }
        }
        if(count>0) IJ.log(count+" pixels were incorrects!");
        return histo;
    }

    /**
     * Computes 2D histogram of object label co-occurrences across all slices.
     * Counts how many pixels have truth label i and test label j at the same location.
     * 
     * @param imp1 first ImagePlus (truth labels)
     * @param max1 maximum expected label in imp1
     * @param imp2 second ImagePlus (test labels)
     * @param max2 maximum expected label in imp2
     * @return ShortProcessor histogram where histo[i][j] = co-occurrence count
     */
    public static ImageProcessor histo2D(ImagePlus imp1, int max1, ImagePlus imp2, int max2){
        ImageProcessor histo= new ShortProcessor(max1+1, max2+1);
        if(imp1.getNSlices()==1){
            return histo2D(imp1.getProcessor(), imp2.getProcessor(),histo);
        }
        ImageStack is1=imp1.getImageStack();
        ImageStack is2=imp2.getImageStack();
        for(int z=0;z<imp1.getNSlices();z++){
            histo2D(is1.getProcessor(z+1),is2.getProcessor(z+1),histo);
        }
        return histo;
    }
    
    /**
     * Accumulates 2D histogram from two single-plane images.
     * 
     * @param ip1 first image processor
     * @param ip2 second image processor
     * @param histo 2D histogram array to accumulate into (modified in-place)
     * @return updated histogram
     */
    public static ImageProcessor histo2D(ImageProcessor ip1, ImageProcessor ip2, ImageProcessor histo){
        for(int y=0;y<ip1.getHeight();y++){
            for(int x=0;x<ip1.getWidth();x++){
                int val = histo.get((int)ip1.getf(x,y),(int)ip2.getf(x,y));
                histo.set((int)ip1.getf(x,y),(int)ip2.getf(x,y),val+1);
            }
        }
        return histo;
    }

    /**
     * Computes IoU (Intersection over Union) matrix from 2D histogram and 1D histograms.
     * 
     * Formula: IoU[i][j] = cooccurrence[i][j] / (sum_i[i][j] + sum_j[i][j] - cooccurrence[i][j])
     * 
     * @param histo2D 2D co-occurrence histogram
     * @param histoTruth 1D histogram of truth object sizes (pixel counts)
     * @param histoTest 1D histogram of test object sizes (pixel counts)
     * @return FloatProcessor with IoU values [0, 1]
     */
    public static ImageProcessor computesIoUs(ImageProcessor histo2D, int[] histoTruth, int[] histoTest){
        FloatProcessor fp=new FloatProcessor(histo2D.getWidth(), histo2D.getHeight());
        for(int y=0;y<histo2D.getHeight();y++){
            for(int x=0;x<histo2D.getWidth();x++){
                double val = histo2D.get(x,y);
                // Apply IoU formula: intersection / union
                val/=(histoTruth[x]+histoTest[y]-val);
                if(val>1) val=1;
                fp.setf(x,y,(float)val);
            }
        }
        return fp;
    }

    /**
     * Renumbers object labels to be contiguous starting from 1.
     * Removes gaps caused by filtered or invalid objects.
     * Operates on entire image stack (all slices).
     * 
     * Example: labels [0, 1, 0, 3, 0, 5] → [0, 1, 0, 2, 0, 3]
     * 
     * @param imp ImagePlus to renumber (modified in-place)
     * @return maximum label value after renumbering
     */
    public static int correctObjectNumbering(ImagePlus imp){
        StackStatistics sstats=new StackStatistics(imp);
        int[] histo= MicUtils.histo1D(imp, (int)Math.round(sstats.max));
        int max=-1;

        int[] convert = conversionIndexes(histo);
        if(imp.getNSlices()==1){
            return correctObjectNumbering(imp.getProcessor(),convert);
        }
        ImageStack is=imp.getImageStack();
        for(int z=0;z<imp.getNSlices();z++){
            int tmp=correctObjectNumbering(is.getProcessor(z+1),convert);
            max=Math.max(max,tmp);
        }
        return max;
    }

    /**
     * Applies label renumbering mapping to a single plane.
     * 
     * @param ip ImageProcessor to renumber (modified in-place)
     * @param convert conversion map: convert[oldLabel] = newLabel
     * @return maximum label value in the processed plane
     */
    public static int correctObjectNumbering(ImageProcessor ip, int[] convert){
        int max=-1;
        for(int y=0;y<ip.getHeight();y++){
            for(int x=0;x<ip.getWidth();x++) {
                int val=(int)ip.getf(x,y);
                int val2=convert[val];
                max=Math.max(max,val2);
                ip.setf(x,y,val2);
            }
        }
        return max;
    }

    /**
     * Builds a mapping from old labels to new contiguous labels.
     * Only maps labels that have non-zero pixel counts.
     * 
     * @param histo 1D histogram where histo[label] = pixel count
     * @return conversion array where convert[oldLabel] = newLabel
     */
    public static int[] conversionIndexes(int[] histo){
        int[] convert=new int[histo.length];
        int value=0;
        for(int index=1;index<histo.length;index++){
            if(histo[index]>0){
                value++;
                convert[index]=value;
            }
        }
        return convert;
    }

    /**
     * Corrects 16-bit signed image format if necessary.
     * Converts and back-converts to ensure proper interpretation.
     * 
     * @param imp ImagePlus to check and potentially convert
     */
    public static void checkImagePlus(ImagePlus imp){
    	FileInfo currentFileInfo = imp.getOriginalFileInfo();
    	if (currentFileInfo != null) {
    		if(currentFileInfo.fileType == FileInfo.GRAY16_SIGNED){
    			ImageConverter converter=new ImageConverter(imp);
    			converter.convertToGray32();
    			converter.convertToGray16();
    		}			
		}
    }
}
