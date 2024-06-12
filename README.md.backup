[![Build Status](https://github.com/MultimodalImagingCenter/MiC/actions/workflows/build.yml/badge.svg)](https://github.com/MultimodalImagingCenter/MiC/actions/workflows/build.yml)

# Mask instant Comparator (MiC) <img src="ressources/logoMiC.png" width="100" >

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
	+ **F1-measure** (or Sorensen Dice Coefficient - DSC) defined as $\frac{2TP}{2TP + FP + FN}$
- It displays the **superposition of the two masks** with the ground truth in green and the mask to evaluate in red for pixel level, for Object level the truth is in green, the mask to evaluate in red, TP are thus yellow, FP blue, FN dark green. 
- It displays the **plots** corresponding to metrics as function of IoU
- All computed values are stored in **result tables** that can be exported in excel or csv format
- possibility to work on **stacks**. It works only in **2D** for now, each slice will be compared to corresponding slice. If varying IoU, an additional plot is displayed with metrics corresponding to the sum of TPs, FNs and FPs on all images.
- the plugin is **macro recordable**


## Install

download the jar file and copy into plugin Folder of ImageJ
or use the updater in Fiji:

menu >Help>Update...

click the button "Manage update sites"

select MiC mask comparator

if it is not available directly you can add it (button add update site) with the folowing URL https://sites.imagej.net/MiC-mask-comparator/

## Usage

run >Plugin>MiC>Mask instant Comparator

or 

run >Plugin>MiC>Mask instant Comparator3D

![MiC3D_dialog](https://github.com/MultimodalImagingCenter/MiC/assets/93767992/9c7b9bdf-4dd4-46e4-8c43-7e5ece47725c)

![MiC3D_output](https://github.com/MultimodalImagingCenter/MiC/assets/93767992/58fd6855-89de-4611-b5ec-d35527222105)

please look at the [wiki](https://github.com/MultimodalImagingCenter/MiC/wiki) for more information on each plugin.



## Licensing

 MiC plugin is licensed under the MIT License
