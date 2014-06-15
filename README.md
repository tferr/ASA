# [Sholl Analysis](http://fiji.sc/Sholl_Analysis)

A plugin for [ImageJ](http://imagej.nih.gov/ij/)/[Fiji](http://fiji.sc/), the standard in scientific image processing, that uses automated  Sholl to perform neuronal morphometry directly from bitmap images.

Sholl analysis [(Sholl, D.A., 1953)](http://www.ncbi.nlm.nih.gov/pmc/articles/PMC1244622/) is a method used by neuroanatomists to describe neuronal arbors. The plugin takes the original technique beyond conventional approaches, offering major advantages over other implementations:

  * It does not require previous tracing of the arbor (although analysis can be applied to previously traced arbors)
  * It combines curve fitting with several methods to automatically retrieve quantitative descriptors from sampled data
  * It allows continuous and repeated sampling around user-defined foci
  * [Many other features](./Notes.md)

Why _ASA_? Throughout 2012 the plugin was [temporarily](SHA: 1fdf4992b748ef8678f57601f2739473e40718c9) called _Advanced Sholl Analysis_, hence the acronym


##Related Resources
[Morphometry-related routines](https://github.com/tferr/Scripts#neuronal-morphometry) ([Scripts repository](https://github.com/tferr/Scripts))


##Installation
If you use Fiji, you already have _Sholl Analysis_ installed. If you are using ImageJ, download the
latest binary from [jenkins.imagej.net](http://jenkins.imagej.net/job/Sholl-Analysis/) and drop it into the main ImageJ. For 'development builds' see the [Release Notes](./Notes.md#)


##License
This program is free software: you can redistribute them and/or modify them under the terms of the
[GNU General Public License](http://www.gnu.org/licenses/gpl.txt) as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.
