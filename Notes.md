##Release Notes for [Sholl Analysis](http://imagej.net/Sholl)

The latest [release build](#release-builds) is available through the Fiji Updater. [Development builds](#development-builds) may have new
features (see [Wish List](#wish-list)) but are bug-prone and probably undocumented. A release is
pushed to the Fiji Updater once new features mature and no major issues are found. Pre-compiled
binaries (and respective sources) of the versions listed here can be downloaded from
[jenkins.imagej.net](http://jenkins.imagej.net/job/Sholl-Analysis/) or from the
[releases](https://github.com/tferr/ASA/releases) page.


Development Builds
------------------
* Fixed non-responsive commands in contextual menu

Release Builds
--------------
####Version 3.6.8 (October 2016)
[![DOI](https://zenodo.org/badge/4622/tferr/ASA.svg)](https://zenodo.org/badge/latestdoi/4622/tferr/ASA)

* Bug fixes ([#18](https://github.com/tferr/ASA/issues/18), [#19](https://github.com/tferr/ASA/issues/19))
* Allow plugin to run headless ([example](http://imagej.net/Sholl_Analysis#Advanced_Usage))
* The _Analyze>Sholl>Sholl (Tracings)..._ plugin is now part of [SNT](https://github.com/fiji/Simple_Neurite_Tracer) to allow:
   * Loading of tracings without associated image
   * Perform filtering by SWC labels
   * Allow true batch analysis of traced data
* Updated [documentation page](http://imagej.net/Sholl) to reflect v3.6 changes

####Version 3.6.7 (October 2016)
* Fixed an issue that did not allow _Analyze>Sholl>Sholl (Tracings)..._ to be macro recordable
* Results files can be saved in any directory, not just the input one
* _Analyze>Sholl>Metrics & Options..._ now includes settings for output files

####Version 3.6.6 (October 2016)
* Implemented the _Analyze>Sholl>Sholl (Tracings)..._ command that performs the analysis
  on Simple Neurite Tracer `.traces`/`.swc` files directly.
* The plugin can now be called directly from Simple Neurite Tracer.
* Tables with unsaved data can now be saved before dismissal

####Version 3.6.5 (August 2016)
* Option to display sampling shells in the image overlay (2D images only)
* _More >> About & Resources..._ lists several online resources that complement the
  [documentation page](http://imagej.net/Sholl) including the
  [IJ Forum](http://forum.imagej.net/search?q=sholl)
* The plugin now requires Java 8 and third party dependencies bundled with Fiji (i.e., it
  requires an ImageJ installation subscribed to both the Java8 and the Fiji update site).
  In other words, we will no longer support running the plugin outside Fiji: It is
  becoming too cumbersome, and it is slowing down its modernization.

####Version 3.6.4 (July 2016)
* Support for multichannel (composite) images
* Preference for preferred data type (sampled/fitted) when rendering Sholl masks
* Masks can now be generated for normalized methods

####Version 3.6.2 (May 2016)
* Analysis of 3D images is now multithreaded. The number of threads can be set in _Metrics & Options..._
* Fixed an exception triggered by IJ1.51a and later when sampling edge voxels
* Bugs Fixed:
   * 3D sampling could be shifted by one voxel in Z
   * _Reset_ option in _Metrics & Options..._ command did not reset all of global preferences
   * _Plot Options..._ from the _More >>_ dropdown menu would not open _Edit>Options>Plots..._

####Version 3.6.1 (March 2016)
* UI overhaul: Functionality of the plugin has been split into several commands. These now reside
  in the `Analyze> Sholl>` submenu, and, in the plugin prompts, in the _More>>_ dropdown menu.
* Implemented the _Sholl Metrics & Options_ command, that allows for full customization of the
  analysis output. In addition, it is now also possible to log file paths and to tag groups of images
  using a _Comments_ field
* Analysis of tabular data:
   * Key modifiers are no longer required. `Sholl> Sholl Analysis (Tabular Data)...` can be used directly
   * Data can be read from any Textwindow containing table, the system clipboard or an external file
     (previously the plugin was only aware of the ImageJ _Results_ table)
* Development: Improved [API](http://tferr.github.io/ASA/apidocs/)
* NB: This release is identical to v3.6.0, that suffered from an issue that did not allow
  [automated compilation by jenkins](https://github.com/tferr/ASA/commit/25f2c2e7d918e83b6daa60ff1723658b505eb699)

####Version 3.4.6 (February 2016)
* Primary branches can be retrieved from [multi-point counters](http://imagej.net/Sholl_Analysis#Startup_ROI)
* Ramification indices (RI) are only calculated when explicitly requested. To disable RI calculations
  deselect the _Infer from starting radius_ checkbox <u>and</u> invalidate the _# Primary branches_
  field by setting it to `0` or `NaN`
* API (Javadocs) are now [online](http://tferr.github.io/ASA/apidocs/) (using a static
  [GitHub page](http://tferr.github.io/ASA/)), and can be accessed using _Help>About Plugins>
  About Sholl Analysis..._
* More UI tweaks to ensure dialog prompts are more informative and display properly in Mac OS with
  Java 1.8

####Version 3.4.5 (June 2015)
 * UI Improvements:
   * Redesign plot labels that were missing since IJ 1.49t
     [(#11)](https://github.com/tferr/ASA/issues/11)
   * Improved plots using the [new plotting capabilities](http://imagej.nih.gov/ij/notes.html) of
     IJ 1.49
 * Accuracy improvements:
   * Improved accuracy of _Nav_ calculation. Ensure it remains accurate for odd shaped profiles
     (highly skewed or containing plateaus) [(#12)](https://github.com/tferr/ASA/issues/12). A
     Riemann sum is now used to calculate definite integrals
 * Scripting improvements:
   * Several (advanced) options that are not present in the main dialog can now be set by calling
     the `Sholl_Analysis` API. For more details see the
     [Advanced Options](http://fiji.sc/Sholl#Advanced_Options) section of the manual.
   * Improved Javadocs
     [(download)](https://github.com/tferr/ASA/releases/download/3.4.5/Sholl_Analysis-3.4.5-javadoc.jar)
 * Minimum requirements: ImageJ 1.49t

####Version 3.4.4 (March 2015)
 * UI Improvements:
   * Exit messages with tooltips [(#8)](https://github.com/tferr/ASA/issues/8)
   * _Cf. Segmentation_: Clarified information. Position of main prompt is restored after
     _Cf. Segmentation_ has been dismissed
   * Tables: Auto-formatting of decimal places; Plot tables without row numbers; Distance
     unit is reported on a dedicated column [(#9)](https://github.com/tferr/ASA/issues/9)
   * Fixed: main dialogs were not properly rendered in Mac OS with Java 7 and higher
 * Minimum requirements: ImageJ 1.49i

####Version 3.4.3 (October 2014)
 * Analysis of tabular data:
    * Improvement: Analysis can be restricted to selected rows
    * Fixed: Parameters such as _Radius Step_, _Starting radius_, etc., were being calculated every
      time a choice was made in the dialog prompt. This was spurious, and could cause the plugin to
      abort
    * Fixed: Exception triggered when _Intersections column_ contained no numeric data
    * Fixed: Reported _Radius Step_ was innacurate: it was being extracted from the difference between
      the first two rows in _Intersections column_ rather than _Distance column_. This was due to a
      silly overlook
 * Bitmap analysis:
    * Fixed: _Cf. Segmentation_ could not parse binary images with display ranges other than 0-255
 * General:
    * Fixed: Auto-restart triggered by the _Analyze Sample Image_ command was unreliable
    * Improvement: Enclosing radius is now set to _NaN_ if the respective cutoff is not met
    * Improvement: Ensure coherence in abbreviations (some metrics were reported using multiple
      abbreviations)

####Version 3.4.2 (July 2014)
  * Fitting to higher order polynomials is now possible using a
    [BAR command](https://github.com/tferr/Scripts) (documentation:
    [GitHub](https://github.com/tferr/Scripts/blob/master/Data_Analysis/README.md#fit-polynomial),
    [Fiji](http://fiji.sc/Sholl_Analysis#Complementary_Tools))
  * Added _Mode_ to the list of choices for integration of multiple samples (2D images)
  * Improved user interface:
    * Macro/script recording: The Alt key modifier is now logged, so that users no longer need
      to find out how to trigger the key event in their macros
    * Tooltips are displayed in the ImageJ status bar when key options are specified
    * Dialog for tabular (CSV) data is much interactive and imported data can be replaced at any
      time using the _Import Other Data_ command
    * With error messages, the plugin now auto-restarts when user chooses to analyze the ddaC
      sample image (_File>Open Samples>ddaC Neuron_) or when the imported CSV data is not valid
    * Users can shuttle between _Bitmap_ and _CSV_ modes by dismissing prompts while holding the
      Alt key
  * Bug fixes:
    * Fixed a bug that made the Macro Recorder to record an invalid command after pressing
      _Cf. Segmentation_
    * Fixed a but that did not give users the opportunity to save the Results table before it
       would be cleared by the plugin
    * Fixed a but that caused some settings to be logged as _NaN_ when analysing CSV data

####Version 3.4.1 (March 2014)
  * Improved prompts: live validation for _Ending radius_/_Radius step size_. Center
    of analysis is displayed on status bar. Section headings are clickable URLs
    pointing to respective section of the [manual](http://fiji.sc/Sholl_Analysis)
  * Added _Cf. Segmentation_, that allow users to confirm which phase of the image is being
    sampled. This should help inexperienced users to make sure they are measuring neuronal
    processes and not the interstitial spaces between them.

####Version 3.4.0 (January 2014)
  * Guesses which of the normalization methods (semi-log or log-log) is the most
    informative using the concept of _determination ratio_
  * Added 2nd and 3rd polynomials as fitting choices
  * Dialog becomes scrollable if larger than 80% of screen height/width. This ensures
    dialog prompts are displayed properly on small laptops such as netbooks
  * If the same image is repeatedly analyzed, detailed data from previous analysis is
    discarded
  * Fixed: macros calling the plugin could not parse filenames containing spaces
  * Versioning conforms with [SemVer](http://semver.org)

> Unfortunately, the numbering scheme used up to version 3.4.0 is somewhat arbitrary and
  not that meaningful. However, large increments usually indicate changes in the user
  interface.

####Version 3.3 (December 2013)
  * Calculates skewness and kurtosis for both sampled and fitted data
  * Fixed v3.1 bug in which values from log-log plot were not listed in table

####Version 3.2 (August 2013)
  * Enclosing radius is defined as the widest distance associated with a specified cutoff
    value of intersection counts. At the default cutoff (1), it is the largest of
    intersecting radii

####Version 3.1 (June 2013)
  * Hold dow _Alt_ to analyze profiles obtained elsewhere, including those from [Simple
    Neurite Tracer](http://fiji.sc/Simple_Neurite_Tracer)
  * Multiple profiles can be obtained at once
  * Plugin can now predict (or at least try) the polynomial of best approximation
  * New descriptors: Median and Barycenter of sampled profile
  * Added a batch mode option that saves profiles without displaying them
  * Sholl decay is calculated using the full rage of data or values within percentiles 10
    and 90, which usually provides a better fit. However, Sholl decay is only calculated
    when logarithmic methods are chosen (v2.2 behavior)
  * Normalization can be performed against: Area of circle/Volume of sphere, Perimeter of
    circumference/Surface of sphere or Area of annulus/Volume of spheric shell
  * Made main dialog more intuitive. This may brake previous macros created with the Macro
    Recorder
  * Barycenter and other descriptors are highlighted on plot
  * Updated the URL of help page to [fiji.sc/Sholl_Analysis](http://fiji.sc/Sholl_Analysis)
  * Eliminated the _Sholl LUT_ command (with IJ 1.48a an later LUTs of non-8-bit TIFFs are
    saved with the image)
  * Sholl mask reports only values from _Linear Sholl_. _Semi-log_ and _Linear-norm._
    masks are no longer created. This could be reinstated if needed

####Version 3.0
  * 3D Sholl: Better handling of anisotropic voxels
  * 3D Sholl: Option to ignore isolated (noise) voxels
  * 3D Sholl: Minimized rounding errors related to digitization of spheres
  * 2D Sholl: Replaced _Radius span_ with _Samples per radius_ to avoid misinterpretations
    of the former parameter. A draconian maximum of 10 _Samples per radius_ is deliberately
    imposed to encourage _Continuos Sholl_
  * 3D and 2D algorithms are ~25% faster (3D analysis remains rather slow)
  * Allows thresholded grayscale images as input
  * Analyzes polarized arbors by restricting the analysis to hemicircles/hemispheres.
      This requires the user to define the center of analysis with an orthogonal radius,
      using the Straight Line Tool and holding Shift
  * Arbor size is calculated as the area/volume of the smallest bounding circle/sphere
      containing the segmented arbor
  * When _End radius_ is empty (or NaN), the largest (hemi)circle/sphere is used. This
      allows macros to easily process images without line selections
  * Precision (Scientific notation and number of decimal places) in Sholl table is set
      by _Analyze>Set Measurements..._
  * Sholl mask is coded with Matlab's _jet_ color map
  * Major code clean up, which solved several issues
  * Plugin is now registered in the Analyze Menu: _Analyze>Sholl Analysis..._
  * Implemented the auxiliary commands _File>Open Samples>ddaC neuron_ (2D sample image),
    _Image>Lookup Tables>Sholl LUT_ and _Help>About Plugins...>Sholl Plugins_
  * Prettified plot

####Version 3d
  * 3D Analysis is now restricted to the volume of each Sholl shell, rather than parsing
    all the segmented voxels

####Version 3c
  * 3D Sholl is usable for the first time, but remains extremely slow

####Version 3a-b
  * Experimental: Performs 3D Sholl without spike suppression if input image is a stack

####Version 2.5
  * Fixed duplicated points in Bresenham's algorithm
  * Option to save data on image directory, if available
  * Long analyses can be aborted by holding Esc

####Version 2.4
  * Fixed a 2.3 regression in which -1 tagged pixels were being counted
  * Improved Sholl mask: Minimized rounding errors. Center of analysis is now marked
  * Improved Sholl Table and Plot display

####Version 2.3
  * Results are printed to a dedicated table
  * Sholl decay is always calculated
  * Fixed exception in the calculation of critical value in non bell-shaped profiles
  * Impose valid parameters in dialog, rather than keep prompting the user (which was not
    amenable to macro recording)
  * Major code cleaning

####Version 2.1-2
  * Fixed several bugs and regressions introduced in v2.0
  * Checks for reasonable _Radius span values_

####Version 2.0
   * Curve Fitting, modernization of Tom's code

####Version 1.0
   * Code imported from Tom Maddock's implementation


Wish List
---------
  * ~~Allow analysis of multichannel images~~ ([Version 3.6.4](#version-364-july-2016))
  * ~~Obtain a mask for non-linear methods~~ ([Version 3.6.4](#version-364-july-2016))
  * ~~Speed up 3D analysis~~ ([Version 3.6.2](#version-362-may-2016))
  * ~~Allow manual counting of primary branches~~ ([Version 3.4.6](#version-346-february-2016))
  * ~~Fitting to polynomials of higher degree~~ ([Version 3.4.2](#version-342-july-2014))
  * Restrict analysis to area ROIs (rather than hemicircles/spheres?)
