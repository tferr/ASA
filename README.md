# [Sholl Analysis](http://imagej.net/Sholl)
[![DOI](https://zenodo.org/badge/4622/tferr/ASA.svg)](https://zenodo.org/badge/latestdoi/4622/tferr/ASA)
[![Jenkins](http://img.shields.io/jenkins/s/http/jenkins.imagej.net/Sholl-Analysis.svg?style=flat-square)](http://jenkins.imagej.net/job/Sholl-Analysis/)
[![Latest Release](https://img.shields.io/github/release/tferr/ASA.svg?style=flat-square)](https://github.com/tferr/ASA/releases)
[![Issues](https://img.shields.io/github/issues/tferr/ASA.svg?style=flat-square)](https://github.com/tferr/ASA/issues)
[![GPL License](http://img.shields.io/badge/license-GPL-blue.svg?style=flat-square)](http://opensource.org/licenses/GPL-3.0)

A plugin for [ImageJ](http://imagej.net/)/[Fiji](http://fiji.sc/), the _de facto_ standard in
scientific image processing, that uses automated  Sholl to perform neuronal morphometry directly from
bitmap images.

Sholl analysis [(Sholl, D.A., 1953)](http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1244622/) is a
method used by neuroanatomists to describe neuronal arbors. The plugin takes the original technique
beyond conventional approaches, offering major advantages over other implementations:

 * It does not require previous tracing of the arbor (although analysis can be applied to previously
   traced arbors)
 * It combines curve fitting with several methods to automatically retrieve quantitative descriptors
   from sampled data
 * It allows continuous and repeated sampling around user-defined foci

Why _ASA_? Throughout 2012 the plugin was [temporarily](SHA:1fdf4992b748ef8678f57601f2739473e40718c9)
called _Advanced Sholl Analysis_, hence the acronym

##Publication
This program is described in [Nature methods](http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html).
Please cite it if you use it in your own research:

- Ferreira T, Blackman A, Oyrer J, Jayabal A, Chung A, Watt A, Sjöström J, van Meyel D.
  [Neuronal morphometry directly from bitmap images](http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html),
  Nature Methods 11(10): 982–984, 2014

The manuscript uses _Sholl Analysis_ to describe and classify morphologically challenging cells and
is accompanied by a [Supplementary Note](http://www.nature.com/nmeth/journal/v11/n10/extref/nmeth.3125-S1.pdf)
that presents the software in greater detail.

##Resources
 - [Documentation page](http://imagej.net/Sholl)
 - [Release Notes](./Notes.md)
 - [API (Javadocs)](http://tferr.github.io/ASA/apidocs/)
 - Complementary routines provided by [IJ BAR](https://github.com/tferr/Scripts#ij-bar) and its
   [update site](http://imagej.net/BAR#Installation):
   - Routines for [Bitmap Morphometry](https://github.com/tferr/Scripts#neuronal-morphometry)
   - Routines for [Data Analysis](https://github.com/tferr/Scripts#data-analysis)


##Installation
If you use Fiji, you already have _Sholl Analysis_ installed and can start [using it](http://imagej.net/Sholl_Analysis#Usage)
right away. If you are not using Fiji, download the latest stable binary from [imagej.net](http://imagej.net/Sholl_Analysis)
or from [jenkins.imagej.net](http://jenkins.imagej.net/job/Sholl-Analysis/lastStableBuild/) and drop
it into the main ImageJ window (see the [documentation page](http://imagej.net/Sholl_Analysis#Non-Fiji_users)
for further details). For information on _development builds_ see [Release Notes](./Notes.md#).


## Help?
 * Want to contribute?
    * Thanks! Please, please do! See [here](https://guides.github.com/activities/contributing-to-open-source/)
      and [here](https://help.github.com/articles/fork-a-repo) for details on how to
      [fork ASA](https://github.com/tferr/ASA/fork) or [here](https://help.github.com/articles/using-pull-requests)
      on how to initiate a [pull request](https://github.com/tferr/ASA/pulls)
    * Documentation updates are also welcome, so go ahead and improve the _Sholl Analysis_
      [documentation page](http://imagej.net/Sholl)
 * Having problems? Found a bug? Need to ask a question?
    * See the plugin's [FAQs](http://imagej.net/Sholl_Analysis#FAQ), ImageJ's [FAQs](http://imagej.net/Frequently_Asked_Questions)
      and [Bug reporting best practices](http://imagej.net/Bug_reporting_best_practices). Then, you can either:
      * [Open an issue](https://github.com/tferr/ASA/issues) on this repository
      * Report it on the [ImageJ forum](http://forum.imagej.net)
      * Use Fiji's [Report a Bug](http://imagej.net/Report_a_Bug) command


##License
This program is free software: you can redistribute them and/or modify them under the terms of the
[GNU General Public License](http://www.gnu.org/licenses/gpl.txt) as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
