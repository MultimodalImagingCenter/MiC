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
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class ConvertStack2DMasksTo3DMask implements PlugInFilter {

    ImagePlus myimp;
    double iou=0.5;

    @Override
    public int setup(String arg, ImagePlus imp) {
        this.myimp=imp;
        iou=IJ.getNumber("IoU threshold:",iou);
        return DOES_ALL+STACK_REQUIRED;
    }

    @Override
    public void run(ImageProcessor ip) {
        MicUtils.correctObjectNumbering(myimp);
        ImageStack is=myimp.getImageStack();
        for(int z=2;z<= is.getSize();z++){
            correctNumber3D(is.getProcessor(z-1),is.getProcessor(z),iou);
        }

    }

    public void correctNumber3D(ImageProcessor ip1, ImageProcessor ip2, double thresholdIoU){
        int max1 = (int)Math.round(ip1.getStats().max+1);
        int max2 = (int)Math.round(ip2.getStats().max+1);
        //IJ.log("max1="+max1);
        //IJ.log("max2="+max2);
        ImageProcessor histo = new ShortProcessor(max1 , max2);
        MicUtils.histo2D(ip1,ip2,histo);
        //new ImagePlus("histo2D",histo).show();

        int[] histo1D1 = new int[max1];
        int[] histo1D2 = new int[max2];
        MicUtils.histo1D(ip1,histo1D1);
        MicUtils.histo1D(ip2,histo1D2);

        ImageProcessor ious = MicUtils.computesIoUs(histo,histo1D1,histo1D2);
        //new ImagePlus("ious",ious).show();

        int[] convertedIndex = new int[max2];
        int currentMax=max1;
        for(int i2=1; i2<max2; i2++){
            int newi=0;
            for(int i1=1;i1<max1;i1++){
                if(ious.getf(i1,i2)>=thresholdIoU){
                    newi=i1;
                }
            }
            if(newi==0) {
                newi=currentMax;
                currentMax++;
            }
            convertedIndex[i2]=newi;
        }
        for (int y=0;y<ip2.getHeight();y++){
            for(int x=0;x<ip2.getWidth();x++){
                ip2.setf(x,y,convertedIndex[Math.round(ip2.getf(x,y))]);
            }
        }
    }
}
