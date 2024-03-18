package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;

public class MicUtils {

    public static int[] histo1D(ImagePlus imp, int max){
        IJ.log("histo1D max "+max);
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

    public static int[] histo1D(ImageProcessor ip, int[] histo){
        for(int y=0;y<ip.getHeight();y++){
            for(int x=0;x<ip.getWidth();x++){
                histo[(int)ip.getf(x,y)]++;
            }
        }
        return histo;
    }

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
    public static ImageProcessor histo2D(ImageProcessor ip1, ImageProcessor ip2, ImageProcessor histo){
        for(int y=0;y<ip1.getHeight();y++){
            for(int x=0;x<ip1.getWidth();x++){
                int val = histo.get((int)ip1.getf(x,y),(int)ip2.getf(x,y));
                histo.set((int)ip1.getf(x,y),(int)ip2.getf(x,y),val+1);
            }
        }
        return histo;
    }

    public static ImageProcessor computesIoUs(ImageProcessor histo2D, int[] histoTruth, int[] histoTest){
        FloatProcessor fp=new FloatProcessor(histo2D.getWidth(), histo2D.getHeight());
        for(int y=0;y<histo2D.getHeight();y++){
            for(int x=0;x<histo2D.getWidth();x++){
                double val = histo2D.get(x,y);
                val/=(histoTruth[x]+histoTest[y]-val);
                fp.setf(x,y,(float)val);
            }
        }
        return fp;
    }

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
}
