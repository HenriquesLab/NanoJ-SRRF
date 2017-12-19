// This is a helper macro for NanoJ-SRRF that can be easily be modified to sweep through parameters
// for an example dataset use: http://bigwww.epfl.ch/smlm/datasets/index.html?p=real-hd

// Defining some of the helper functions
temporal_method_id = newArray(
	"TRM", //0
	"TRA", //1
	"TRPPM", //2
	"TRAC2", //3
	"TRAC3", //4
	"TRAC4"); //5

function setMethod(method) {
	order = 0;
    if (method == 0) order = 0;
    else if (method == 1) order = 1;
    else if (method == 2) order = -1;
    else if (method == 3) order = 2;
    else if (method == 4) order = 3;
    else if (method == 5) order = 4;
    call("ij.Prefs.set", "nanoj.java.gui.SRRFAnalysis_ExtraSettings_.SRRFType", method);
    call("ij.Prefs.set", "nanoj.srrf.java.gui.SRRFAnalysis_ExtraSettings_.SRRForder", order);
}
////////////////////////////////////////

MAGNIFICATION = 2;
SWEEP_STACK_TITLE = "Parameter Sweep";
title = getTitle();

run("Z Project...", "projection=[Average Intensity]");
run("Size...", "width="+(getWidth()*MAGNIFICATION)+" height="+getHeight()*MAGNIFICATION+" constrain average interpolation=Bicubic");
rename(SWEEP_STACK_TITLE);

for (n=0; n<lengthOf(temporal_method_id); n++) {
	selectImage(title);

	run("SRRF - Configure Advanced Settings", "integrate_temporal_correlations do_intensity_weighting psf_fwhm=3.17 minimize_srrf_patterning save=.tif");
	setMethod(n); // note, always run setMethod after "SRRF - Configure Advanced Settings"

	// now actually run the analysis
	run("SRRF Analysis", "ring=0.50 radiality_magnification=2 axes=6 frames_per_time-point=0 start=0 end=0 max=100");

	tempTitle = getTitle();
	run("Copy");

	selectImage(SWEEP_STACK_TITLE);
	run("Add Slice");
	run("Paste");
	run("Set Label...", "label="+temporal_method_id[n]);

	selectImage(tempTitle);
	close();
}
run("Enhance Contrast...", "saturated=0.3 process_all");
