package fr.curie.mic;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.process.Blitter;

public class AnalysisResultDisplay {
    private final ImagePlus truthMaskIP;
    private final ImagePlus testMaskIP;
    private ImagePlus plotHyperStack;

    public AnalysisResultDisplay(ImagePlus truthMaskIP, ImagePlus testMaskIP){
        this.truthMaskIP = truthMaskIP;
        this.testMaskIP = testMaskIP;
    }

    public void addPlot(AnalysisResult result, String baseTitle){
        Plot plot = result.createPlot(result.getPlotTitle(baseTitle));
        if(plotHyperStack == null) createPlotHyperStack(plot.getProcessor().getWidth(), plot.getProcessor().getHeight());
        int c = result.getChannel() + 1;
        int z = result.getSlice();
        int t = result.getFrame() + 1;
        int index = plotHyperStack.getStackIndex(c, z, t);
        plotHyperStack.getStack().getProcessor(index).copyBits(plot.getProcessor(), 0, 0, Blitter.COPY);
        plotHyperStack.getStack().setSliceLabel(result.getPlotTitle(baseTitle), index);
    }

    public void showPlots(){
        if(plotHyperStack == null) return;
        plotHyperStack.show();
        plotHyperStack.getCanvas().setMagnification(1);
    }

    public boolean hasPlots(){
        return plotHyperStack != null;
    }

    private void createPlotHyperStack(int width, int height){
        plotHyperStack = IJ.createHyperStack("Plots " + truthMaskIP.getTitle() + "__VS__" + testMaskIP.getTitle(), width, height, truthMaskIP.getNChannels(), truthMaskIP.getNSlices(), truthMaskIP.getNFrames(), 24);
    }
}
