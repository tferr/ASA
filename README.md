# [Sholl Analysis](http://fiji.sc/Sholl_Analysis)

A plugin for [ImageJ](http://imagej.nih.gov/ij/)/[Fiji](http://fiji.sc/), the standard in scientific image processing, that uses automated  Sholl to perform neuronal morphometry directly from bitmap images.

Sholl analysis [(Sholl, D.A., 1953)](http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1244622/) is a method used by neuroanatomists to describe neuronal arbors. The plugin takes the original technique beyond conventional approaches, offering major advantages over other implementations:

  * It does not require previous tracing of the arbor (although analysis can be applied to previously traced arbors)
  * It combines curve fitting with several methods to automatically retrieve quantitative descriptors from sampled data
  * It allows continuous and repeated sampling around user-defined foci

Why _ASA_? Throughout 2012 the plugin was [temporarily](SHA: 1fdf4992b748ef8678f57601f2739473e40718c9) called _Advanced Sholl Analysis_, hence the acronym


##Resources
 - [Documentation page](http://fiji.sc/Sholl_Analysis)
 - [Release Notes](./Notes.md)
 - Complementary routines provided by [IJ BAR](https://github.com/tferr/Scripts#ij-bar) and its [update site](http://fiji.sc/BAR#Installation):
   - Routines for [Bitmap Morphometry](https://github.com/tferr/Scripts#neuronal-morphometry)
   - Routines for [Data Analysis](https://github.com/tferr/Scripts#data-analysis)


##Installation
If you use Fiji, you already have _Sholl Analysis_ installed. If you are not using Fiji, download
the latest stable binary from [fiji.sc](http://fiji.sc/Sholl_Analysis) or from [jenkins.imagej.net](http://jenkins.imagej.net/job/Sholl-Analysis/lastStableBuild/) and drop it into the main ImageJ window (see the [documentation page](http://fiji.sc/Sholl_Analysis#Non-Fiji_users) for further details). For information on _development builds_ see [Release Notes](./Notes.md#).


##License
This program is free software: you can redistribute them and/or modify them under the terms of the
[GNU General Public License](http://www.gnu.org/licenses/gpl.txt) as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
