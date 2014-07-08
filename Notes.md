##Release Notes for [Sholl Analysis](http://fiji.sc/Sholl_Analysis)

[Release builds](#release-builds) are available through the Fiji Updater or from the [plugin's
webpage](http://fiji.sc/Sholl_Analysis). [Development builds](#development-builds) may have new
features (see [Wish List](#wish-list)) but are bug-prone and probably undocumented. They are
pushed to the Fiji Updater once new features mature and no major issues are found. Pre-compiled
binaries (and respective sources) of all the versions listed here can be downloaded from
[jenkins.imagej.net](http://jenkins.imagej.net/job/Sholl-Analysis/).


Development Builds
------------------
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


Release Builds
--------------
####Version 3.4.1 (March 2014)
  * Improved prompts: live validation for _Ending radius_/_Radius step size_. Center
    of analysis is displayed on status bar. Section headings are clickable URLs
    pointing to respective section of the [manual](http://fiji.sc/Sholl_Analysis)
  * Added _Cf. Segmentation_, that allow users to confirm which phase of the image is being
    sampled. This should help inexperienced users to make sure they are measuring neuronal
    processes and not the interstitial spaces between them. It is perplexing how such blunder
    can escape [peer-review](http://www.ncbi.nlm.nih.gov/pubmed/24485871). Puzzling enough,
    a tiny fraction of the time spent trying to tuck away such embarrassment would have allowed
    the authors to read the [documentation](http://fiji.sc/Sholl_Analysis) ad nauseam

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
   * Initial version by Tom Maddock


Wish List
---------
  * ~~Fitting to polynomials of higher degree~~ ([Version 3.4.2](#version-342-june-2014))
  * Restrict analysis to area ROIs (rather than hemicircles/spheres?)
  * Obtain a mask for every chosen method (slices of an ImageStack?)
