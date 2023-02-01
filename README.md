# Mask instant Comparator (MiC)

## About

MiC is an ImageJ plugin to compare segmentation masks.  
- It computes the number of **true positive (TP)**, **false positives (FP)** and **false negatives (FN)**
	+ at pixel level 
	+ at object level with an overlap (or intersection over union  - IoU) of 0.5
		+ possibility of varying IoU 
- It computes metrics
	+ **Precision** defined as $\frac{TP}{TP + FP}$
	+ **Recall** (or sensitivity) defined as $\frac{TP}{TP + FN}$
	+ **Jaccard index** (or global perecision) defined as $\frac{TP}{TP + FP + FN}$
	+ **F-measure** (or Sorensen Dice Coefficient - DSC) defined as $\frac{2TP}{2TP + FP + FN}$
- It displays the **superposition of the two masks** with the ground truth in green and the mask to evaluate in red for pixel level, for Object level the truth is in green, the mask to evaluate in red, TP are thus yellow, FP blue, FN dark green. 
- It displays the **plots** corresponding to metrics as function of IoU
- All computed values are stored in **result tables** that can be exported in excel or csv format
- possibility to work on **stacks**. It works only in **2D** for now, each slice will be compared to corresponding slice. If varying IoU, an additional plot is displayed with metrics corresponding to the sum of TPs, FNs and FPs on all images.
- the plugin is **macro recordable**


## Install

download the jar file and copy into plugin Folder of ImageJ

## Usage

run >Plugin>MiC>Mask instant Comparator

![MiC, dialog window](MiC_Dialog.png)

### parameters

Selection of images to work with 
+ Truth mask image
+ Test mask image

Selection of level for metrics computations
+ Pixel
+ Object (IoU=0.5)
+ Object (Varying IoU)

Selection of parameter for varying threshold for the object level with varying IoU
+ Minimum IoU threshold (0-1)
+ Maximum IoU threshold (0-1)
+ Increment of IoU threshold (0-1)

Selection of outputs
+ Show composite images
+ Show graphs (varying IoU)
+ Show summary graph (varying IoU)
+ Show GT objects correspondence table

Selection of filters on objects to remove objects touching border of image or small objects that might be due to noise
+ Minimum distance to border (pixels)
	* -1 to remove nothing
	* 0 to remove object touching borders
	* higher values to define a minimal distance of object's center to border
+ Minimum size for particles (pixels)


### outputs description

Once the program ends the computation, several ouputs are displayed.

![MiC, outputs](MiC_results_screenshot.png)

Depending on the choice of parameters, the outputs consists of 
+ one stack of superimposed masks
+ one plot if the varying IoU is selected. It displays the 4 metrics score on ordinate and IoU on abscissa
	+ if working on stack this plot is replaced by
		+ a stack of plot with one plot for each slice, corresponding to metrics for corresponding slice
		+ a plot with metrics computed by summing objects from all slices
+ several result tables
	- one containg counts of TP, FP, FN, metrics at pixel / object level
	- one containing counts with varying IoU
	- one containing information about correspondence found with notably the IoU for each object

## Interpretation of results

### image

![MiC, output image](MiC_output_image.png)
The mask to test is superimposed to the ground truth with a color code
+ for pixel level
	* Green for GT (FN)
	* Red for mask (FP)
	* Yellow where the two masks overlaps (TP)
+ for object level (the IoU threshold is displayed in the slice label)
	* Yellow for corresponding objects (TP)
	* Green for underestimation of TP
	* Red for overestimation of TP
	* Dark green for GT objects without correspondence (FN)
	* Dark blue for Mask's objects without correspondence (FP)
	* Cyan for objects with IoU lower than threshold 
	* Orange for fused objects
	* Gray for objects eliminated with respect of distance to border parameter 

### result window 1

### result window 2

### result window 3


## Licensing

 MiC plugin is licensed under the MIT License
