# Super-Resolution Radial Fluctuations (SRRF) - ImageJ Plugin #

*Check out the **[SRRF paper](http://www.nature.com/articles/ncomms12471)** in Nature Communications or our SMLMS2016 short talk about [**NanoJ and SRRF**](https://www.youtube.com/watch?v=HjrcM8NfWJE).* 

*We have now developed [**SRRF-Stream**](http://www.andor.com/srrf-stream?gclid=CjwKCAjwtdbLBRALEiwAm8pA5ZtXUvog5Uq-ENVIHr0dVeZYDYQt8M_eJQYfjSyhyJ96Cb2Jw2cotBoCIaAQAvD_BwE) in partnership with Andor Technology. SRRF-Stream allows real-time analysis of data being acquired in Andor cameras. Check out [**our webinar**](http://www.andor.com/learning-academy/nanoj-srrf-and-srrf-stream-fast-live-cell-conventional-fluorophore-super-resolution-for-most-modern-microscopes-july-2017) on NanoJ-SRRF and SRRF-Stream.*

**SRRF** (pronounced as *surf*) is as a novel open-source and high-performance analytical approach for **Live-cell Super-Resolution Microscopy**, provided as a fast **GPU-enabled ImageJ plugin**. **SRRF** is capable of extracting high-fidelity super-resolution information in modern microscopes (**TIRF**, **widefield** and **confocals**) using conventional fluorophores such as **GFP**. Compared to other methods, **SRRF** is capable of live-cell imaging over timescales ranging from minutes to hours, using sample illumination orders of magnitude lower than methods such as **PALM**, **STORM** or **STED**.

[![](https://bitbucket.org/repo/MxA9bg/images/3159217949-test.jpg)](https://youtu.be/1o7TW32pUKE)
`Actin labelled with LifeAct-GFP and imaging performed on a standard TIRF microscope. Each SRRF frame was produced by running SRRF analysis on 100 frames of the raw TIRF acquisition; for comparison the average of these 100 TIRF frames is displayed on the left.`


## Tutorials on how to get started ##

**To get set up with SRRF** please click [**here**](https://bitbucket.org/rhenriqueslab/nanoj-srrf/wiki/Getting%20set%20up%20with%20SRRF) to find **tutorials on how to install, run and compile NanoJ-SRRF**.

## Hall of Fame ##

Here is a [**list of researchers**](https://bitbucket.org/rhenriqueslab/nanoj-srrf/wiki/Hall%20of%20Fame) that have either posted data analysed with SRRF or given us feedback that helped improve the algorithm.

## About SRRF ##

**SRRF** is part of the [**NanoJ project**](https://github.com/HenriquesLab/NanoJ-Core) - a collection of analytical methods dedicated to super-resolution and advanced imaging compatible with ImageJ. Both **SRRF** and **NanoJ** are developed by the [**Henriques laboratory**](http://www.ucl.ac.uk/lmcb/users/ricardo-henriques) in the [MRC Laboratory for Molecular Cell Biology](http://www.ucl.ac.uk/lmcb/) at [University College London](http://www.ucl.ac.uk/).

[![](https://bitbucket.org/repo/MxA9bg/images/1852944901-youtube%20screenshot.jpg)](https://youtu.be/wb78HsPwQ38)
`Microtubules labelled with tubulin-GFP and imaging performed on a spinning disk confocal microscope. Z sections were taken at 300nm intervals, with each SRRF image produced from SRRF analysis of 100 raw frames acquired at each interval; for comparison the average of these 100 spinning disk frames is displayed on the left. The right hand panel shows the cumulative projections of the SRRF and spinning disk images colour-coded by z position.`

## Features ##

* **Super-resolution with standard microscopes**: **SRRF** is capable of super-resolving cellular structures imaged with **widefield**, **TIRF** or **confocal** modern microscopes without the need for specialized optics. Additionally, **SRRF** has sample illumination intensity requirements orders of magnitude lower than other super-resolution methods such as **PALM**, **STORM** or **STED**.
* **Super-resolution with conventional fluorophores such as GFP**: we have shown that **SRRF** is able to produce super-resolution images from samples labelled with a wide range of conventional fluorophores, such as **GFP**.
* **Live-cell super-resolution with minimal phototoxicity**: as **SRRF** is able to extract high-fidelity super-resolution information from low signal-to-noise ratio samples, it requires lower sample illumination than most other super-resolution methods. For this reason, **SRRF** enables live-cell imaging over timescales ranging from minutes to hours. Imaged cells generally remain capable of undergoing mitosis, mitochondrial motility and cytoskeletal reorganisation as expected in normal healthy conditions.
* **Speed**: **SRRF** is very fast!! It has been fully optimized to take advantage of **GPU** high-performance computing in modern graphics cards. However, its analytical framework has been developed to work in almost any computer, independently of its architecture. **SRRF**, generally will process images and generate super-resolution data in real-time.
* **Drift correction**: drift is a major challenge in super-resolution microscopy and in most cases the limiting factor for resolution. Since the acquisition can take from several minutes to hours, drifts of even a few tens of nanometers can drastically deteriorate resolution, or worse, create anomalies in reconstructed images (e.g. artifactual doubling of filamentary structures). **SRRF** provides an easy drift correction method based on dynamics sample tracking (cross-correlation).
* **Availability, ease of use and open-source**: both the software and its source code are freely available as a **Fiji** or **ImageJ** plugin. This allows maximal dissemination of the software in the biological research community, optimal usability, and will offer users the ability to modify and improve the software at will.

## SRRF and QuickPALM ##

**SRRF** follows the ideology of our previous algorithm [**QuickPALM**](https://code.google.com/archive/p/quickpalm/) published in [Nature Methods](http://www.nature.com/nmeth/journal/v7/n5/full/nmeth0510-339.html), **QuickPALM** remains one of the [most cited analytical packages for PALM and STORM](https://scholar.google.com/scholar_lookup?title=QuickPALM%3A+3D+real-time+photoactivation+nanoscopy+image+processing+in+ImageJ&author=Henriques&publication_year=2010) super-resolution since 2010, provided freely to the community. However **SRRF** goes far beyond the first generation of analytical approaches, of which **QuickPALM** is part of, allowing for Super-Resolution information to be extracted from almost any modern microscope, including those non-specialised for super-resolution.

[![](https://bitbucket.org/repo/MxA9bg/images/1948380863-MTs%20placeholder.jpg)](https://youtu.be/vR176IW0bPw)
`Microtubules labelled with GFP and imaging is performed on a standard TIRF microscope. Each SRRF frame was produced by running SRRF analysis on 100 frames of the raw TIRF acquisition; for comparison the average of these 100 TIRF frames is displayed above.`
