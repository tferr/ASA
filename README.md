# [Sholl Analysis](http://imagej.net/Sholl)
[![Maintenance](https://img.shields.io/badge/Legacy-Replaced%20by%20SNT-orange)](https://github.com/morphonets/SNT)
[![DOI](https://zenodo.org/badge/4622/tferr/ASA.svg)](https://zenodo.org/badge/latestdoi/4622/tferr/ASA)
[![Travis](https://travis-ci.org/tferr/ASA.svg?branch-master)](https://travis-ci.org/tferr/ASA)
[![GPL License](http://img.shields.io/badge/license-GPL-blue.svg?style=flat-square)](http://opensource.org/licenses/GPL-3.0)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/e032f437a8ba44a6b4ff7cc9bdf8d978)](https://www.codacy.com/app/tferr/ASA?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=tferr/ASA&amp;utm_campaign=Badge_Grade)



:warning: :warning:**This project is now part of [SNT](https://github.com/morphonets/SNT). The most up-to-date code for this plugin can now be found at [morphonets/SNT](https://github.com/morphonets/SNT)** :warning: :warning:

A plugin for [ImageJ](http://imagej.net/), the _de facto_ standard in scientific image processing, that uses automated  Sholl to perform neuronal morphometry directly from bitmap images. It is part of [Fiji](http://fiji.sc/).

Sholl analysis [(Sholl, D.A., 1953)](http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1244622/) is a method used by neuroanatomists to describe neuronal arbors. The plugin takes the original technique beyond conventional approaches, offering major advantages over other implementations:

* It can perform the analysis in either 2D or 3D from three distinct sources:
  1. Segmented images, allowing continuous or repeated sampling. In this mode analysis
     does not require previous tracing of the arbor
  1. Traced data (SWC, eSWC or [SNT](https://imagej.net/SNT) traces files)
  1. Tabular data (CSV and related files)
* It combines curve fitting with several methods to automatically retrieve quantitative
  descriptors from sampled data (currently ~20 metrics)
* Allows analysis of sub-compartments centered on user-defined foci
* It is scriptable and capable of batch processing

Why _ASA_? Throughout 2012 the plugin was temporarily called _Advanced Sholl Analysis_,
hence the acronym


## Publication
This program is described in [Nature methods](http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html).
Please cite it if you use it in your own research:

- Ferreira T, Blackman A, Oyrer J, Jayabal A, Chung A, Watt A, Sjöström J, van Meyel D.
  [Neuronal morphometry directly from bitmap images](http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html),
  Nature Methods 11(10): 982–984, 2014

The manuscript uses _Sholl Analysis_ to describe and classify morphologically challenging cells and is accompanied by a [Supplementary Note](http://www.nature.com/nmeth/journal/v11/n10/extref/nmeth.3125-S1.pdf) that presents the software in greater detail.

## Installation
In  [Fiji](https://imagej.net/Fiji), subscribe to the *NeuroAnatomy* [update site](https://imagej.net/Update_Sites):

1.  Run the Fiji Updater (*Help › Update...*, the penultimate entry in the  *Help ›*  menu)
2.  Click *Manage update sites*
3.  Select the *Neuroanatomy* checkbox
4.  Click *Apply changes* and Restart Fiji. Sholl-related commands will be registered under _Plugins>Neuroanatomy>_ in the main menu and SNT scripts under _Templates>Neuroanatomy>_ in Fiji's Script Editor. The plugin can also be accessed from [SNT](https://github.com/morphonets/SNT) itself.

The [documentation page](http://imagej.net/Sholl_Analysis) contains usage details.


## Help?
 * Want to contribute?
    * Thanks! Please, please do! Head over to [morphonets/SNT](https://github.com/morphonets/SNT)
    * Documentation updates are also welcome, so go ahead and improve the _Sholl Analysis_
      [documentation page](http://imagej.net/Sholl)
 * Having problems? Found a bug? Need to ask a question?
    * See the plugin's [FAQs](http://imagej.net/Sholl_Analysis#FAQ), ImageJ's [FAQs](http://imagej.net/Frequently_Asked_Questions) and [Bug reporting best practices](http://imagej.net/Bug_reporting_best_practices). Then, you can either:
      * [Open an issue](https://github.com/morphonets/SNT/issues)
      * Report it on the [ImageJ forum](http://forum.imagej.net)

## License

This program is free software: you can redistribute them and/or modify them under the terms of the
[GNU General Public License](http://www.gnu.org/licenses/gpl.txt) as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
