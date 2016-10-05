/*
 * #%L
 * Sholl_Analysis plugin for ImageJ
 * %%
 * Copyright (C) 2005 - 2016 Tiago Ferreira
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package sholl;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import fiji.Debug;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.HTMLDialog;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Toolbar;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.Recorder;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.util.ThreadUtil;
import ij.util.Tools;
import sholl.gui.EnhancedGenericDialog;
import sholl.gui.EnhancedWaitForUserDialog;

/**
 * ImageJ 1 plugin that uses the Sholl technique to perform neuronal morphometry
 * directly from bitmap images.
 *
 * @author Tiago Ferreira (based on an earlier implementation by Tom Maddock)
 * @see <a href="https://github.com/tferr/ASA">https://github.com/tferr/ASA</a>
 * @see <a href="http://imagej.net/Sholl">http://imagej.net/Sholl</a>
 */
public class Sholl_Analysis implements PlugIn, DialogListener {

	/* Plugin Information */
	/** The Plugin's version */
	static final String VERSION = Sholl_Utils.version();
	static final String URL = "http://imagej.net/Sholl_Analysis";

	/* Sholl Type Definitions */
	private static final String[] SHOLL_TYPES = { "Linear", "Linear-norm", "Semi-log", "Log-log" };
	private static final int SHOLL_N = 0;
	private static final int SHOLL_NS = 1;
	private static final int SHOLL_SLOG = 2;
	private static final int SHOLL_LOG = 3;
	private static boolean shollN = true;
	private static boolean shollNS = false;
	private static boolean shollSLOG = true;
	private static boolean shollLOG = false;

	/* Data Normalization */
	private static final String[] NORMS2D = { "Area", "Perimeter", "Annulus" };
	private static final String[] NORMS3D = { "Volume", "Surface", "Spherical shell" };
	private static final int AREA_NORM = 0;
	private static final int PERIMETER_NORM = 1;
	private static final int ANNULUS_NORM = 2;
	private static int normChoice;

	/* Ramification Indices */
	private static double primaryBranches = Double.NaN;
	private static boolean inferPrimary;
	private static boolean primaryFromPointRoi = false;
	private static int multipointCount;

	/* Curve Fitting, Results and Descriptors */
	private static boolean fitCurve = true;
	private static final String[] DEGREES = { "2nd degree", "3rd degree", "4th degree", "5th degree", "6th degree",
			"7th degree", "8th degree", "Best fitting degree" };
	private static final int SMALLEST_DATASET = 6;
	private static final String SHOLLTABLE = "Sholl Results";
	private static double[] centroid = null;
	private static int enclosingCutOff = 1;
	private static boolean chooseLog = true;

	/* Image path and Output Options */
	private static boolean validPath;
	private static boolean hideSaved;
	private static String imgPath;
	private static String imgTitle;

	/* Default parameters and input values */
	private static double startRadius = 10.0;
	private static double endRadius = 100.0;
	private static double stepRadius = 1;
	private static double incStep = 0;
	private static int polyChoice = DEGREES.length - 1;
	private static boolean verbose;
	private static boolean mask;
	private static boolean overlayShells;
	private static boolean save;
	private static boolean isCSV = false;

	/* Common variables */
	private static String unit = "pixels";
	private static double vxSize = 1;
	private static double vxWH = 1;
	private static double vxD = 1;
	private static boolean is3D;
	private static double lowerT;
	private static double upperT;

	/* Boundaries and center of analysis */
	private static boolean orthoChord = false;
	private static boolean trimBounds;
	private static String[] quads = new String[2];
	private static final String QUAD_NORTH = "Above line";
	private static final String QUAD_SOUTH = "Below line";
	private static final String QUAD_EAST = "Left of line";
	private static final String QUAD_WEST = "Right of line";
	private static String quadString = "None";
	private static int quadChoice;
	private static int minX, maxX;
	private static int minY, maxY;
	private static int minZ, maxZ;
	private static int x, y, z, channel;

	/* Parameters for 3D analysis */
	private static boolean skipSingleVoxels = false;

	/* Parameters for 2D analysis */
	private static final String[] BIN_TYPES = { "Mean", "Median", "Mode" };
	/** Flag for integration of repeated measures (2D analysis): average */
	private static final int BIN_AVERAGE = 0;
	/** Flag for integration of repeated measures (2D analysis): median */
	private static final int BIN_MEDIAN = 1;
	/** Flag for integration of repeated measures (2D analysis): mode */
	private static final int BIN_MODE = 2;
	private static int binChoice = BIN_AVERAGE;
	private static int nSpans = 1;

	/* Advanced options that can be set using the API */
	static boolean noPlots = false; // Exclude plots from output?
	static boolean noTable = false; // Exclude detailed table from output?
	static boolean plotLabels = true; // Describe fitted curves in plots?
	static int fMetricsPrecision = 1000; // Discretization steps, Riemann sum &
											// local max

	// If the edge of a group of pixels lies tangent to the sampling circle,
	// multiple intersections with that circle will be counted. With this flag
	// on, we will try to find these "false positives" and throw them out. A way
	// to attempt this (we will be missing some of them) is to throw out 1-pixel
	// groups that exist solely on the edge of a "stair" of target pixels (see
	// countSinglePixels)
	private static boolean doSpikeSupression = true;

	/* Parameters for tabular data */
	private ResultsTable csvRT;
	private int rColumn;
	private int cColumn;
	private boolean limitCSV;
	private boolean tableRequired = true;

	private static double[] radii;
	private static double[] counts;
	private static int prefs;
	private ImagePlus img;
	private ImageProcessor ip;
	private static int progressCounter;

	/**
	 * Debug helper
	 *
	 * @param args
	 *            See {@link fiji.Debug#run(java.lang.String, java.lang.String)}
	 */
	public static void main(final String[] args) {
		Debug.run("Sholl Analysis...", "");
	}

	/**
	 * This method is called when the plugin is loaded. {@code arg} is specified
	 * in {@code plugins.config}. See
	 * {@link ij.plugin.PlugIn#run(java.lang.String)}
	 *
	 * @param arg
	 *            If {@code csv} the plugin is set for analysis of tabular data.
	 *            If {@code sample}, the plugin runs on a 2D demo image.
	 */
	@Override
	public void run(final String arg) {

		if (arg.equalsIgnoreCase("sample")) {
			img = Sholl_Utils.displaySample();
			if (img == null) {
				sError("Could not retrieve sample image.\nPerhaps you should restart ImageJ?");
				return;
			}
		} else
			img = WindowManager.getCurrentImage();
		final Calibration cal;
		isCSV = IJ.altKeyDown() || arg.equalsIgnoreCase("csv");

		if (isCSV) {

			if (isTableRequired() && csvRT==null) {
				csvRT = getTable();
				if (csvRT == null)
					return;
			}

			fitCurve = true; // The goal of CSV import is curve fitting

			// Update parameters for CSV data with user input. Reset the state
			// of the Alt key modifier in case user wants to access bitmapPrompt
			// by Alt-canceling the dialog. This does not apply when the user
			// Alt- clicks on the button that triggers retrieveSampleData()
			if (!IJ.macroRunning())
				IJ.setKeyUp(KeyEvent.VK_ALT);
			if (!csvPrompt()) {

				// Did the user press Alt while dismissing csvPrompt()?
				if (IJ.altKeyDown() && !IJ.macroRunning() && IJ.showMessageWithCancel("Sholll Analysis v" + VERSION,
						"Dismiss CSV import and initiate bitmap analysis?")) {
					isCSV = false;
					IJ.setKeyUp(KeyEvent.VK_ALT);
					this.run(arg);
				}
				return;

			}

			// Retrieve parameters from chosen columns
			if (isTableRequired()) {
				radii = csvRT.getColumnAsDoubles(rColumn);
				counts = csvRT.getColumnAsDoubles(cColumn);
				if (limitCSV) {
					final TextPanel tp = Sholl_Utils.getTextWindow(imgTitle).getTextPanel();
					final int startRow = tp.getSelectionStart();
					final int endRow = tp.getSelectionEnd();
					final boolean validRange = startRow != -1 && endRow != -1 && startRow != endRow;
					if (validRange) {
						radii = Arrays.copyOfRange(radii, startRow, endRow + 1);
						counts = Arrays.copyOfRange(counts, startRow, endRow + 1);
					} else {
						IJ.log("*** Warning: " + imgTitle + "\n*** Option to restrict "
								+ "analysis ignored: Not a valid selection of rows");
					}
				}
			}
			stepRadius = (radii.length > 1) ? radii[1] - radii[0] : Double.NaN;
			startRadius = radii[0];
			endRadius = radii[radii.length - 1];
			if (normChoice == NORMS3D.length - 1 && (Double.isNaN(stepRadius) || stepRadius <= 0)) {
				final String msg = (is3D) ? NORMS3D[normChoice] : NORMS2D[normChoice];
				IJ.log("*** Warning: " + imgTitle + "\n*** Could not determine" + " radius step size: " + msg
						+ " normalizations will not be relevant");
			}

			// "Reset" all variables that relate only to bitmap analysis
			x = (int) Double.NaN;
			y = (int) Double.NaN;
			z = (int) Double.NaN;
			channel = (int) Double.NaN;
			incStep = Double.NaN;
			cal = null;
			unit = "N.A.";

		} else {

			// Make sure image is of the right type, reminding the user
			// that the analysis is performed on segmented cells
			ip = getValidProcessor(img);
			if (ip == null)
				return;

			// Set the 2D/3D Sholl flag
			final int depth = img.getNSlices();
			is3D = depth > 1;

			// Get current z,c position
			z = img.getZ();
			channel = img.getC();

			// NB: Removing spaces could disrupt unique filenames: eg "test
			// 01.tif" and "test 02.tif" would both be treated as "test" by
			// img.getShortTitle()
			imgTitle = trimExtension(img.getTitle());

			// Get image calibration. Stacks are likely to have anisotropic
			// voxels with large z-steps. It is unlikely that lateral dimensions
			// will differ
			cal = img.getCalibration();
			if (cal.scaled()) {
				vxWH = Math.sqrt(cal.pixelWidth * cal.pixelHeight);
				vxD = cal.pixelDepth;
				unit = cal.getUnits();
			} else {
				vxWH = vxD = 1;
				unit = "pixels";
			}
			vxSize = (is3D) ? Math.cbrt(vxWH * vxWH * vxD) : vxWH;

			// Retrieve ROI defining center of analysis
			final Roi roi = getStartupROI();

			// Straight line: get center coordinates, end radius and angle of
			// chord.
			double chordAngle = -1.0; // Initialize line angle
			if (roi != null && roi.getType() == Roi.LINE) {

				final Line chord = (Line) roi;
				x = chord.x1;
				y = chord.y1;
				endRadius = vxSize * chord.getRawLength();
				chordAngle = Math.abs(chord.getAngle(x, y, chord.x2, chord.y2));
				primaryFromPointRoi = false;
				multipointCount = 0;

				// Point: Get center coordinates (x,y)
			} else if (roi != null && roi.getType() == Roi.POINT) {

				final PointRoi point = (PointRoi) roi;
				final Rectangle rect = point.getBounds();
				x = rect.x;
				y = rect.y;

				// If multi-point, use point count to specify # Primary branches
				multipointCount = point.getCount(point.getCounter());
				if (multipointCount > 1) {
					primaryBranches = multipointCount - 1;
					inferPrimary = false;
					primaryFromPointRoi = true;
				}

				// Not a proper ROI type
			} else {
				sError("Straight Line, Point or Multi-point selection required.");
				return;
			}

			// Show the plugin dialog: Update parameters with user input and
			// find out if analysis will be restricted to a hemicircle /
			// hemisphere
			if (!bitmapPrompt(chordAngle, is3D)) {

				// Did the user press Alt while dismissing bitmapPrompt?
				if (IJ.altKeyDown() && !IJ.macroRunning() && IJ.showMessageWithCancel("Sholll Analysis v" + VERSION,
						"Dismiss bitmap analysis and initiate CSV import?")) {
					isCSV = true;
					IJ.setKeyDown(KeyEvent.VK_ALT);
					this.run(arg);
				}
				return;

			}

			// Impose valid parameters
			final int wdth = ip.getWidth();
			final int hght = ip.getHeight();
			final double dx, dy, dz, maxEndRadius;

			dx = ((orthoChord && trimBounds && quadString.equals(QUAD_WEST)) || x <= wdth / 2) ? (x - wdth) * vxWH
					: x * vxWH;

			dy = ((orthoChord && trimBounds && quadString.equals(QUAD_SOUTH)) || y <= hght / 2) ? (y - hght) * vxWH
					: y * vxWH;

			dz = (z <= depth / 2) ? (z - depth) * vxD : z * vxD;

			maxEndRadius = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (Double.isNaN(startRadius))
				startRadius = 0;
			if (Double.isNaN(incStep))
				incStep = 0;
			endRadius = Double.isNaN(endRadius) ? maxEndRadius : Math.min(endRadius, maxEndRadius);
			stepRadius = Math.max(vxSize, incStep);

			// Calculate how many samples will be taken
			final int size = (int) ((endRadius - startRadius) / stepRadius) + 1;

			// Exit if there are no samples
			if (size <= 1) {
				sError("Invalid parameters: Starting radius must be smaller than\n"
						+ "Ending radius and Radius step size must be within range!");
				return;
			}

			img.startTiming();
			IJ.resetEscape();

			// Create arrays for radii (in physical units) and intersection
			// counts
			radii = new double[size];
			counts = new double[size];

			for (int i = 0; i < size; i++) {
				radii[i] = startRadius + i * stepRadius;
			}

			// Define boundaries of analysis according to orthogonal chords (if
			// any)
			final int xymaxradius = (int) Math.round(radii[size - 1] / vxWH);
			final int zmaxradius = (int) Math.round(radii[size - 1] / vxD);

			minX = Math.max(x - xymaxradius, 0);
			maxX = Math.min(x + xymaxradius, wdth);
			minY = Math.max(y - xymaxradius, 0);
			maxY = Math.min(y + xymaxradius, hght);
			minZ = Math.max(z - zmaxradius, 1);
			maxZ = Math.min(z + zmaxradius, depth);

			if (orthoChord && trimBounds) {
				if (quadString.equals(QUAD_NORTH))
					maxY = Math.min(y + xymaxradius, y);
				else if (quadString.equals(QUAD_SOUTH))
					minY = Math.max(y - xymaxradius, y);
				else if (quadString.equals(QUAD_WEST))
					minX = x;
				else if (quadString.equals(QUAD_EAST))
					maxX = x;
			}

			// 2D: Analyze the data and return intersection counts with nSpans
			// per radius. 3D: Analysis without nSpans
			if (is3D) {
				counts = analyze3D(x, y, z, radii, img);
			} else {
				counts = analyze2D(x, y, radii, vxSize, nSpans, binChoice, ip);
			}

		}

		IJ.showStatus("Preparing Results...");

		// Retrieve preferences
		prefs = Options.getMetrics();

		// Retrieve pairs of radii, counts for intersecting radii
		final double[][] valuesN = getNonZeroValues(radii, counts);
		final int trimmedCounts = valuesN.length;
		if (trimmedCounts == 0) {
			IJ.beep();
			IJ.showProgress(0, 0);
			IJ.showStatus("Error: All intersection counts were zero!");
			return;
		}

		// Retrieve stats on sampled data
		final ResultsTable statsTable = createStatsTable(imgTitle, x, y, z, valuesN);

		// Transform and fit data
		final double[][] valuesNS = transformValues(valuesN, true, false, false);
		final double[][] valuesSLOG = transformValues(valuesNS, false, true, false);
		final double[][] valuesLOG = transformValues(valuesSLOG, false, false, true);
		double[] fvaluesN = null;
		double[] fvaluesNS = null;
		// double[] fvaluesLOG = null;

		// Create plots
		if (shollN) {
			final Plot plotN;
			if (noPlots) {
				plotN = null;
			} else {
				plotN = plotValues("Sholl profile (" + SHOLL_TYPES[SHOLL_N] + ") for " + imgTitle,
						is3D ? "3D distance (" + unit + ")" : "2D distance (" + unit + ")", "N. of Intersections",
						valuesN);
			}
			if (fitCurve)
				fvaluesN = getFittedProfile(valuesN, SHOLL_N, statsTable, plotN);
			if (!noPlots) {
				Sholl_Utils.markPlotPoint(plotN, centroid, Color.RED);
				savePlot(plotN, SHOLL_N);
			}

		}

		// Linear (norm) is not performed when deciding between semi-log/log-log
		if (chooseLog) {

			IJ.showStatus("Calculating determination ratio...");
			// this is inefficient: we'll be splitting double arrays
			final double dratio = getDeterminationRatio(valuesSLOG, valuesLOG);
			statsTable.addValue("Determination ratio", dratio);
			shollNS = false;
			shollSLOG = (dratio >= 1);
			shollLOG = (dratio < 1);

		}

		final String normalizerString = is3D ? NORMS3D[normChoice] : NORMS2D[normChoice];
		final String distanceString = is3D ? "3D distance" : "2D distance";

		if (shollNS) {
			final Plot plotNS;
			if (noPlots) {
				plotNS = null;
			} else {
				plotNS = plotValues("Sholl profile (" + SHOLL_TYPES[SHOLL_NS] + ") for " + imgTitle,
						distanceString + " (" + unit + ")", "Inters./" + normalizerString, valuesNS);
			}
			if (fitCurve)
				fvaluesNS = getFittedProfile(valuesNS, SHOLL_NS, statsTable, plotNS);
			if (!noPlots)
				savePlot(plotNS, SHOLL_NS);

		}
		if (shollSLOG) {
			final Plot plotSLOG;
			if (noPlots) {
				plotSLOG = null;
			} else {
				plotSLOG = plotValues("Sholl profile (" + SHOLL_TYPES[SHOLL_SLOG] + ") for " + imgTitle,
						distanceString + " (" + unit + ")", "log(Inters./" + normalizerString + ")", valuesSLOG);
			}
			if (fitCurve)
				plotRegression(valuesSLOG, plotSLOG, statsTable, SHOLL_TYPES[SHOLL_SLOG]);
			if (!noPlots)
				savePlot(plotSLOG, SHOLL_SLOG);

		}
		if (shollLOG) {
			final Plot plotLOG;
			if (noPlots) {
				plotLOG = null;
			} else {
				plotLOG = plotValues("Sholl profile (" + SHOLL_TYPES[SHOLL_LOG] + ") for " + imgTitle,
						"log(" + distanceString + ")", "log(Inters./" + normalizerString + ")", valuesLOG);
			}
			if (fitCurve)
				// fvaluesLOG = getFittedProfile(valuesLOG, SHOLL_LOG,
				// statsTable, plotLOG);
				plotRegression(valuesLOG, plotLOG, statsTable, SHOLL_TYPES[SHOLL_LOG]);
			if (!noPlots)
				savePlot(plotLOG, SHOLL_LOG);

		}

		if (!noTable) {
			ResultsTable rt;
			final String profileTable = imgTitle + "_Sholl-Profiles";
			final TextWindow window = (TextWindow) WindowManager.getFrame(profileTable);
			if (window == null)
				rt = new ResultsTable();
			else {
				rt = window.getTextPanel().getResultsTable();
				rt.reset();
			}

			rt.showRowNumbers(false);
			rt.setPrecision(Options.getScientificNotationAwarePrecision());
			for (int i = 0; i < valuesN.length; i++) {
				rt.incrementCounter();
				rt.addValue("Radius", valuesN[i][0]);
				rt.addValue("Inters.", valuesN[i][1]);
				if (fvaluesN != null) {
					// rt.addValue("Radius (Polynomial fit)", valuesN[i][0]);
					rt.addValue("Inters. (Polynomial fit)", fvaluesN[i]);
				}
				rt.addValue("Inters./" + normalizerString, valuesNS[i][1]);
				if (fvaluesNS != null) {
					// rt.addValue("Radius (Power fit)", valuesNS[i][0]);
					rt.addValue("Inters./" + normalizerString + " (Power fit)", fvaluesNS[i]);
				}
				rt.addValue("log(Radius)", valuesLOG[i][0]);
				rt.addValue("log(Inters./" + normalizerString + ")", valuesLOG[i][1]);
				// if (fvaluesLOG!=null) {
				// rt.addValue("log(Radius) (Exponential fit)",
				// valuesLOG[i][0]);
				// rt.addValue("log(Inters./"+ normalizerString +") (Exponential
				// fit)", fvaluesLOG[i]);
				// }
			}

			if (validPath && save) {
				try {
					final String path = imgPath + profileTable;
					rt.saveAs(path + Prefs.defaultResultsExtension());
				} catch (final IOException e) {
					IJ.log(">>>> An error occurred when saving " + imgTitle + "'s profile(s):\n" + e);
				}
			}
			if (!validPath || (validPath && !hideSaved))
				rt.show(profileTable);
		}

		String exitmsg = "Done. ";

		if (isCSV) {
			IJ.showStatus(exitmsg);
			return;
		}

		// Create intersections mask if analyzed image remains available
		if (mask && img.getWindow() != null) {

			IJ.showStatus("Preparing intersections mask...");
			final boolean fittedData = Options.getMaskType() == Options.FITTED_MASK && fitCurve;
			ImagePlus maskimg = null;

			if (shollN && fittedData) {

				maskimg = makeMask(imgTitle, fvaluesN, x, y, cal, false);
				maskimg.setProperty("Label", "Polynomial fit");

			} else if (shollN) {

				maskimg = makeMask(imgTitle, counts, x, y, cal, false);
				maskimg.setProperty("Label", "Sampled data");

			} else if (shollNS && fittedData) {

				maskimg = makeMask(imgTitle, fvaluesNS, x, y, cal, true);
				maskimg.setProperty("Label", SHOLL_TYPES[SHOLL_NS] + " (fitted)");

			} else if (shollNS) {

				maskimg = makeMask(img, imgTitle, valuesNS, x, y, cal, true);
				maskimg.setProperty("Label", SHOLL_TYPES[SHOLL_NS] + " (sampled)");

			} else if (shollSLOG && fittedData) {

				try {
					final double b = statsTable.getValue("Regression intercept (" + SHOLL_TYPES[SHOLL_SLOG] + ")",
							statsTable.getCounter() - 1);
					final double k = statsTable.getValue("Regression coefficient (" + SHOLL_TYPES[SHOLL_SLOG] + ")",
							statsTable.getCounter() - 1);
					final double[] fvaluesSLOG = new double[valuesSLOG.length];
					for (int i = 0; i < valuesSLOG.length; i++)
						fvaluesSLOG[i] = valuesSLOG[i][0] * -k + b;
					maskimg = makeMask(imgTitle, fvaluesSLOG, x, y, cal, true);
					maskimg.setProperty("Label", SHOLL_TYPES[SHOLL_SLOG] + " (fitted)");
				} catch (final IllegalArgumentException ignored) {
					if (verbose)
						IJ.log("[Sholl] ERROR: Regression inaccessible for mask creation");
				}

			} else if (shollSLOG) {

				maskimg = makeMask(img, imgTitle, valuesSLOG, x, y, cal, true);
				maskimg.setProperty("Label", SHOLL_TYPES[SHOLL_SLOG] + " (sampled)");

			} else if (shollLOG && verbose) {

				IJ.log("[Sholl] INFO: Masks cannot be rendered for " + SHOLL_TYPES[SHOLL_LOG] + " profiles");

			}

			if (maskimg == null) {
				IJ.beep();
				exitmsg = "Error: Mask could not be created! ";
			} else if (!validPath || (validPath && !hideSaved)) {
				maskimg.show();
				maskimg.updateAndDraw();
			}

		}

		if (!is3D && overlayShells)
			overlayShells();

		IJ.showProgress(0, 0);
		IJ.showTime(img, img.getStartTime(), exitmsg);

	}

	/**
	 * Tries to retrieve a valid startup ROI. Prompts the user for a new ROI if
	 * nothing appropriate was found (if the plugin has not been called from a
	 * macro or script).
	 *
	 * @return the startup ROI
	 *
	 * @see <a href= "http://imagej.net/Sholl_Analysis#Startup_ROI">Startup_ROI
	 *      </a>
	 */
	private Roi getStartupROI() {
		Roi roi = img.getRoi();
		final boolean validRoi = roi != null && (roi.getType() == Roi.LINE || roi.getType() == Roi.POINT);
		if (!IJ.macroRunning() && !validRoi) {
			img.killRoi();
			Toolbar.getInstance().setTool("line");
			final EnhancedWaitForUserDialog wd = new EnhancedWaitForUserDialog(
					"Please define the largest Sholl radius by creating\n"
							+ "a straight line starting at the center of analysis.\n"
							+ "(Hold down \"Shift\" to draw an orthogonal radius)\n \n"
							+ "Alternatively, define the focus of the arbor using\n"
							+ "the Point/Multi-point Selection Tool.");
			wd.addHyperlink(URL + "#Startup_ROI");
			wd.show();
			if (wd.escPressed())
				return null;
			roi = img.getRoi();
		}
		return roi;
	}

	/**
	 * Performs curve fitting, adds the fitted curve to the Sholl plot and
	 * appends descriptors related to the fit to the summary table. Returns
	 * fitted values or null if values.length is less than SMALLEST_DATASET
	 */
	private static double[] getFittedProfile(final double[][] values, final int method, final ResultsTable rt,
			final Plot plot) {

		final int size = values.length;
		final double[] x = new double[size];
		final double[] y = new double[size];
		for (int i = 0; i < size; i++) {
			x[i] = values[i][0];
			y[i] = values[i][1];
		}

		// Define a global analysis title
		final String longtitle = "Sholl Profile (" + SHOLL_TYPES[method] + ") for " + imgTitle;

		// Abort curve fitting when dealing with small datasets that are prone
		// to inflated coefficients of determination
		if (fitCurve && size <= SMALLEST_DATASET) {
			IJ.log(longtitle + ":\nCurve fitting not performed: Not enough data points\n" + "At least "
					+ (SMALLEST_DATASET + 1) + " pairs of values are required for curve fitting.");
			return null;
		}

		// Perform fitting
		final CurveFitter cf = new CurveFitter(x, y);
		// cf.setRestarts(4); // default: 2;
		// cf.setMaxIterations(50000); //default: 25000

		if (method == SHOLL_N) {
			if (DEGREES[polyChoice].startsWith("2")) {
				cf.doFit(CurveFitter.POLY2, false);
			} else if (DEGREES[polyChoice].startsWith("3")) {
				cf.doFit(CurveFitter.POLY3, false);
			} else if (DEGREES[polyChoice].startsWith("4")) {
				cf.doFit(CurveFitter.POLY4, false);
			} else if (DEGREES[polyChoice].startsWith("5")) {
				cf.doFit(CurveFitter.POLY5, false);
			} else if (DEGREES[polyChoice].startsWith("6")) {
				cf.doFit(CurveFitter.POLY6, false);
			} else if (DEGREES[polyChoice].startsWith("7")) {
				cf.doFit(CurveFitter.POLY7, false);
			} else if (DEGREES[polyChoice].startsWith("8")) {
				cf.doFit(CurveFitter.POLY8, false);
			} else {
				IJ.showStatus("Choosing polynomial of best fit...");
				if (verbose)
					IJ.log("\n*** Choosing polynomial of best fit for " + imgTitle + "...");
				cf.doFit(getBestPolyFit(x, y), false);
			}
		} else if (method == SHOLL_NS) {
			cf.doFit(CurveFitter.POWER, false);
		} else if (method == SHOLL_LOG) {
			cf.doFit(CurveFitter.EXP_WITH_OFFSET, false);
		}

		// IJ.showStatus("Curve fitter status: " + cf.getStatusString());
		final double[] parameters = cf.getParams();
		final int degree = cf.getNumParams() - 1;

		// Get fitted data
		final double[] fy = new double[size];
		for (int i = 0; i < size; i++)
			fy[i] = cf.f(parameters, x[i]);

		// Initialize plotLabel
		final StringBuffer plotLabel = new StringBuffer();

		// Register quality of fit
		plotLabel.append("R\u00B2= " + IJ.d2s(cf.getRSquared(), 3));

		// Plot fitted curve
		if (plot != null) {
			plot.setColor(Color.BLUE);
			plot.setLineWidth(2);
			plot.addPoints(x, fy, PlotWindow.LINE);
			plot.setLineWidth(1);
		}

		if (verbose) {
			IJ.log("\n*** " + longtitle + ", fitting details:" + cf.getResultString());
		}

		if (method == SHOLL_N) {

			double cv = 0d; // Polyn. regression: ordinate of maximum
			double cr = 0d; // Polyn. regression: abscissa of maximum
			double mv = 0d; // Polyn. regression: Average value
			double rif = Double.NaN; // Polyn. regression: Ramification index

			// Get coordinates of cv, the local maximum of polynomial. We'll
			// iterate around the index of highest fitted value to retrieve
			// values with a precision of 1/fMetricsPrecision of radius step
			// size
			final int maxIdx = CurveFitter.getMax(fy);
			final double crLeft = (x[Math.max(maxIdx - 1, 0)] + x[maxIdx]) / 2;
			final double crRight = (x[Math.min(maxIdx + 1, size - 1)] + x[maxIdx]) / 2;
			final double crStep = (crRight - crLeft) / fMetricsPrecision;
			double crTmp, cvTmp;
			for (int i = 0; i < fMetricsPrecision; i++) {
				crTmp = crLeft + (i * crStep);
				cvTmp = cf.f(parameters, crTmp);
				if (cvTmp > cv) {
					cv = cvTmp;
					cr = crTmp;
				}
			}

			// Calculate mv, the mean value of the fitted polynomial between
			// NonZeroStartRadius and NonZeroEndRadius. We will define it as
			// a Riemann sum calculated with a 1/fMetricsPrecision of radius
			// step size. For a walk-through, see eg,
			// http://archives.math.utk.edu/visual.calculus/5/average.1/
			final double[] xRange = Tools.getMinMax(x);
			final int subintervals = size * fMetricsPrecision;
			final double deltaX = (xRange[1] - xRange[0]) / subintervals;
			for (int i = 0; i < subintervals; i++) {
				final double xi = xRange[0] + (i * deltaX);
				final double f_xi = cf.f(parameters, xi);
				mv += f_xi * deltaX;
			}
			mv = (1 / (xRange[1] - xRange[0])) * mv;

			// Highlight mean value on the plot
			if (plot != null) {
				plot.setLineWidth(1);
				plot.setColor(Color.LIGHT_GRAY);
				plot.drawLine(xRange[0], mv, xRange[1], mv);
			}

			// Calculate the "fitted" ramification index
			if (inferPrimary)
				rif = cv / y[0];
			else if (!(primaryBranches == 0 || Double.isNaN(primaryBranches)))
				rif = cv / primaryBranches;

			// Register parameters
			plotLabel.append("\nNm= " + IJ.d2s(cv, 2));
			plotLabel.append("\nrc= " + IJ.d2s(cr, 2));
			plotLabel.append("\nNav= " + IJ.d2s(mv, 2));
			plotLabel.append("\n" + Sholl_Utils.ordinal(degree)).append(" degree");

			rt.addValue("Critical value", cv);
			rt.addValue("Critical radius", cr);
			rt.addValue("Mean value", mv);
			rt.addValue("Ramification index (fit)", rif);
			final double[] moments = getMoments(fy);
			if ((prefs & Options.SKEWNESS) != 0)
				rt.addValue("Skewness (fit)", moments[2]);
			if ((prefs & Options.KURTOSIS) != 0)
				rt.addValue("Kurtosis (fit)", moments[3]);
			rt.addValue("Polyn. degree", degree);
			rt.addValue("Polyn. R^2", cf.getRSquared());

		}

		if (plot != null && plotLabels)
			Sholl_Utils.makePlotLabel(plot, plotLabel.toString(), Color.BLACK);
		rt.show(SHOLLTABLE);
		return fy;

	}

	/**
	 * Remove zeros from data. Zero intersections are problematic for logs and
	 * polynomial fits Long stretches of zeros (e.g., caused by discontinuous
	 * arbors) often cause sharp "bumps" on the fitted curve. Setting zeros to
	 * NaN is not option as it would impact the CurveFitter.
	 */
	private double[][] getNonZeroValues(final double[] xpoints, final double[] ypoints) {

		final int size = Math.min(xpoints.length, ypoints.length);
		int i, j, nsize = 0;

		for (i = 0; i < size; i++) {
			if (xpoints[i] > 0.0 && ypoints[i] > 0.0)
				nsize++;
		}

		final double[][] values = new double[nsize][2];
		for (i = 0, j = 0; i < size; i++) {
			if (xpoints[i] > 0.0 && ypoints[i] > 0.0) {
				values[j][0] = xpoints[i];
				values[j++][1] = ypoints[i];
			}
		}

		return values;

	}

	/**
	 * Obtains the "Determination Ratio", used to choose between semi-log and
	 * log-log methods: (R^2 semi-log regression)/(R^2 log-log regression)
	 */
	static private double getDeterminationRatio(final double semilog[][], final double[][] loglog) {

		final int size = semilog.length;
		final double[] slx = new double[size];
		final double[] sly = new double[size];
		final double[] llx = new double[size];
		final double[] lly = new double[size];

		for (int i = 0; i < size; i++) {
			slx[i] = semilog[i][0];
			sly[i] = semilog[i][1];
			llx[i] = loglog[i][0];
			lly[i] = loglog[i][1];
		}
		final CurveFitter cf1 = new CurveFitter(slx, sly);
		final CurveFitter cf2 = new CurveFitter(llx, lly);
		cf1.doFit(CurveFitter.STRAIGHT_LINE, false);
		cf2.doFit(CurveFitter.STRAIGHT_LINE, false);
		final double rsqrd1 = cf1.getRSquared();
		final double rsqrd2 = cf2.getRSquared();

		if (verbose) {
			// final String norm = is3D ? NORMS3D[normChoice] :
			// NORMS2D[normChoice];
			IJ.log("\n*** Choosing normalization method for " + imgTitle + "...");
			IJ.log("Semi-log: R^2= " + IJ.d2s(rsqrd1, 5) + "... " + cf1.getStatusString());
			IJ.log("Log-log: R^2= " + IJ.d2s(rsqrd2, 5) + "... " + cf2.getStatusString());
		}

		return rsqrd1 / Math.max(Double.MIN_VALUE, rsqrd2);

	}

	/**
	 * Guesses the polynomial of best fit by comparison of coefficient of
	 * determination
	 */
	static private int getBestPolyFit(final double x[], final double[] y) {

		final int[] polyList = { CurveFitter.POLY2, CurveFitter.POLY3, CurveFitter.POLY4, CurveFitter.POLY5,
				CurveFitter.POLY6, CurveFitter.POLY7, CurveFitter.POLY8 };

		int bestFit = 0;
		double bestRSquared = 0.0;
		final double[] listRSquared = new double[polyList.length];

		for (int i = 0; i < polyList.length; i++) {

			final CurveFitter cf = new CurveFitter(x, y);
			// cf.setRestarts(4); // default: 2;
			// cf.setMaxIterations(50000); //default: 25000
			cf.doFit(polyList[i], false);
			listRSquared[i] = cf.getRSquared();
			if (listRSquared[i] > bestRSquared) {
				bestRSquared = listRSquared[i];
				bestFit = i;
			}
			if (verbose)
				IJ.log(CurveFitter.fitList[polyList[i]] + ": R^2= " + IJ.d2s(listRSquared[i], 5) + "... "
						+ cf.getStatusString());

		}

		return polyList[bestFit];
	}

	/**
	 * Creates the main dialog (csvPrompt imports tabular data). Returns false
	 * if dialog was canceled or dialogItemChanged() if dialog was OKed.
	 */
	private boolean bitmapPrompt(final double chordAngle, final boolean is3D) {

		final EnhancedGenericDialog gd = new EnhancedGenericDialog("Sholl Analysis v" + VERSION);

		final Font headerFont = new Font("SansSerif", Font.BOLD, 12);
		final int xIndent = 42;

		// Part I: Definition of Shells
		gd.setInsets(0, 0, 0);
		gd.addHyperlinkMessage("I. Definition of Shells:", headerFont, Color.BLACK, URL + "#Definition_of_Shells");
		gd.addNumericField("Starting radius", startRadius, 2, 9, unit);
		gd.addNumericField("Ending radius", endRadius, 2, 9, unit);
		gd.addNumericField("Radius_step size", incStep, 2, 9, unit);

		// If an orthogonal chord exists, prompt for hemicircle/hemisphere
		// analysis
		orthoChord = (chordAngle > -1 && chordAngle % 90 == 0);
		if (orthoChord) {
			if (chordAngle == 90.0) {
				quads[0] = QUAD_EAST;
				quads[1] = QUAD_WEST;
			} else {
				quads[0] = QUAD_NORTH;
				quads[1] = QUAD_SOUTH;
			}
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("Restrict analysis to hemi" + (is3D ? "sphere:" : "circle:"), trimBounds);
			gd.setInsets(0, 0, 0);
			gd.addChoice("_", quads, quads[quadChoice]);
		}

		// Part II: Multiple samples (2D) and noise filtering (3D)
		if (is3D) {
			gd.setInsets(2, 0, 2);
			gd.addHyperlinkMessage("II. Noise Reduction:", headerFont, Color.BLACK,
					URL + "#Multiple_Samples_and_Noise_Reduction");
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("Ignore isolated (6-connected) voxels", skipSingleVoxels);
		} else {
			gd.setInsets(10, 0, 2);
			gd.addHyperlinkMessage("II. Multiple Samples per Radius:", headerFont, Color.BLACK,
					URL + "#Multiple_Samples_and_Noise_Reduction");
			gd.addSlider("#_Samples", 1, 10, nSpans);
			gd.setInsets(0, 0, 0);
			gd.addChoice("Integration", BIN_TYPES, BIN_TYPES[binChoice]);
		}

		// Part III: Indices and Curve Fitting
		gd.setInsets(10, 0, 2);
		gd.addHyperlinkMessage("III. Descriptors and Curve Fitting:", headerFont, Color.BLACK,
				URL + "#Descriptors_and_Curve_Fitting");
		gd.addNumericField(" Enclosing radius cutoff", enclosingCutOff, 0, 4, "intersection(s)");

		// We'll use the "units" label of the GenericDialog's numeric field to
		// provide some feedback on the usage of multi-point counters
		// (http://imagej.net/Sholl_Analysis#Startup_ROI). This is obviously
		// extremely hacky, but we are already at the limit of customization
		// allowed by GenericDialogs
		final String mpTip = (primaryFromPointRoi)
				? "Multi-point [2-" + multipointCount + "] count: " + String.valueOf((int) primaryBranches)
				: "(Multi-point counter absent)";
		gd.addNumericField("#_Primary branches", primaryBranches, 0, 4, mpTip);
		if (!IJ.macroRunning()) {
			try { // Access "units" label
				final Panel p = (Panel) gd.getComponent(gd.getComponentCount() - 1);
				final Label l = (Label) p.getComponent(1);
				l.setForeground(gd.getDisabledComponentColor());
			} catch (final Exception ignored) {
			}
		}

		gd.setInsets(0, 2 * xIndent, 0);
		gd.addCheckbox("Infer from starting radius", inferPrimary);
		gd.setInsets(6, xIndent, 0);
		gd.addCheckbox("Fit profile and compute descriptors", fitCurve);
		gd.setInsets(3, 2 * xIndent, 0);
		gd.addCheckbox("Show fitting details", verbose);

		// Part IV: Sholl Methods
		gd.setInsets(10, 0, 2);
		gd.addHyperlinkMessage("IV. Sholl Methods:", headerFont, Color.BLACK, URL + "#Choice_of_Methods");
		gd.setInsets(0, xIndent / 2, 2);
		gd.addMessage("Profiles Without Normalization:");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckbox("Linear", shollN);
		gd.setInsets(0, 0, 0);
		gd.addChoice("Polynomial", DEGREES, DEGREES[polyChoice]);

		gd.setInsets(8, xIndent / 2, 2);
		gd.addMessage("Normalized Profiles:");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckboxGroup(2, 2, new String[] { "Most informative", SHOLL_TYPES[SHOLL_NS], SHOLL_TYPES[SHOLL_SLOG],
				SHOLL_TYPES[SHOLL_LOG] }, new boolean[] { chooseLog, shollNS, shollSLOG, shollLOG });
		gd.setInsets(0, 0, 0);
		if (is3D) {
			gd.addChoice("Normalizer", NORMS3D, NORMS3D[normChoice]);
		} else {
			gd.addChoice("Normalizer", NORMS2D, NORMS2D[normChoice]);
		}

		// Part V: Mask and outputs
		gd.setInsets(10, 0, 2);
		gd.addHyperlinkMessage("V. Output Options:", headerFont, Color.BLACK, URL + "#Output_Options");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckbox("Create intersections mask", mask);
		gd.setInsets(0, xIndent, 0);
		gd.addCheckbox("Overlay sampling shells", overlayShells);

		// Offer to save results if local image
		if (validPath) {
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("Save results in image directory", save);
			gd.setInsets(0, 2 * xIndent, 0);
			gd.addCheckbox("Do not display saved files", hideSaved);
		} else {
			gd.setInsets(0, xIndent, 0);
			gd.addMessage("Saving options disabled. Image is not saved locally...", null,
					gd.getDisabledComponentColor());
		}

		// Add listener and scroll bars. Update prompt and status bar before
		// displaying it
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);

		Sholl_Utils.addCitationUrl(gd);
		gd.assignPopupToHelpButton(createOptionsMenu(gd));
		gd.showScrollableDialog();
		if (gd.wasCanceled()) {
			return false;
		} else if (gd.wasOKed()) {
			improveRecording();
			return dialogItemChanged(gd, null);
		} else { // User pressed any other button
			return false;
		}
	}

	/** Applies "Cf. Segmentation" LUT */
	private void applySegmentationLUT() {

		this.ip.resetMinAndMax();
		final double min = this.ip.getMin();
		final double max = this.ip.getMax();
		final double t1 = (min + (lowerT * 255.0 / (max - min)));
		final double t2 = (min + (upperT * 255.0 / (max - min)));

		final byte[] r = new byte[256];
		final byte[] g = new byte[256];
		final byte[] b = new byte[256];
		for (int i = 0; i < 256; i++) {
			if (i >= t1 && i <= t2) {
				r[i] = (byte) 0;
				g[i] = (byte) 100;
				b[i] = (byte) 255;
			} else {
				r[i] = (byte) 255;
				g[i] = (byte) 236;
				b[i] = (byte) 158;
			}
		}
		this.ip.setColorModel(new IndexColorModel(8, 256, r, g, b));
		this.img.updateAndDraw();

	}

	/**
	 * Some users have been measuring the interstitial spaces between neuronal
	 * processes rather than the processes themselves. This creates a warning
	 * message while highlighting the arbor to remember the user that
	 * highlighted pixels are the ones to be measured
	 */
	private void offlineHelp(final GenericDialog parentDialog) {

		// remember image LUT
		final LUT lut = this.ip.getLut();
		final int mode = this.ip.getLutUpdateMode();

		// apply new LUT in new thread to provide a more responsive user
		// interface
		final Thread newThread = new Thread(new Runnable() {
			@Override
			public void run() {
				applySegmentationLUT();
			}
		});
		newThread.start();

		// present dialog
		final StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<div WIDTH=400>");
		sb.append("Pixels highlighted in ");
		sb.append("<span style='background-color:#c0c0c0;color:#0000ff;font-weight:bold;'>&nbsp;Blue&nbsp;</span> ");
		sb.append("will be interpreted as <i>arbor</i>. Pixels in ");
		sb.append("<span style='background-color:#b0b0b0;color:#ffff00;font-weight:bold;'>&nbsp;Yellow&nbsp;</span> ");
		sb.append("will be interpreted as <i>background</i>. ");
		sb.append("Make sure you are sampling neuronal processes and not the interstitial spaces between them!");
		sb.append("<br><br>");
		sb.append("<b>Segmentation values:</b><br>");
		sb.append("&emsp;Lower threshold (lowest intensity in arbor):&ensp<tt>").append(IJ.d2s(lowerT, 1))
				.append("</tt><br>");
		sb.append("&emsp;Upper threshold (brightest intensity in arbor):&ensp<tt>").append(IJ.d2s(upperT, 1))
				.append("</tt><br>");
		sb.append("&emsp;Intensity at analysis center (x=").append(String.valueOf(x)).append(", y=")
				.append(String.valueOf(y)).append(", z=").append(String.valueOf(z)).append(", ch=")
				.append(String.valueOf(channel)).append("):&ensp<tt>").append(IJ.d2s(this.ip.get(x, y), 1))
				.append("</tt>");
		sb.append("<br><br>");
		sb.append("<b>Image details:</b><br>");
		sb.append("&emsp;Image type:&ensp;").append(String.valueOf(this.ip.getBitDepth())).append("-bit")
				.append((is3D) ? " (3D)" : " (2D)").append("<br>");
		sb.append("&emsp;Binary image?&ensp;").append(String.valueOf(this.ip.isBinary())).append("</tt><br>");
		sb.append("&emsp;Multi-channel image?&ensp;").append(String.valueOf(this.img.isComposite()))
				.append("</tt><br>");
		sb.append("&emsp;Spatial units:&ensp;<tt>").append(unit).append("</tt><br>");
		sb.append("&emsp;Inverted LUT (<i>Image>Lookup Tables>Invert LUT</i>)?&ensp<tt>")
				.append(String.valueOf(this.ip.isInvertedLut())).append("</tt><br>");
		sb.append("&emsp;Image saved locally?&ensp;").append(String.valueOf(validPath)).append("</tt>");
		sb.append("<br><br>");
		sb.append("<b>Analysis options:</b><br>");
		sb.append("&emsp;Orthogonal restriction allowed?&ensp<tt>").append(String.valueOf(orthoChord))
				.append("</tt><br>");
		sb.append("&emsp;Repetead measures allowed?&ensp<tt>").append(String.valueOf(!is3D)).append("</tt><br>");
		sb.append("&emsp;Noise supression allowed?&ensp<tt>").append(String.valueOf(is3D)).append("</tt><br>");
		sb.append("&emsp;Saving options available?&ensp<tt>").append(String.valueOf(validPath)).append("</tt><br>");
		sb.append("&emsp;Multi-point ROIs marking primary branches:&ensp<tt>")
				.append(String.valueOf(Math.max(0, multipointCount - 1))).append("</tt>");
		sb.append("<br><br>");
		sb.append("<b>Other settings:</b><br>");
		sb.append("&emsp;Black background (<i>Process>Binary>Options...</i>)?&ensp<tt>")
				.append(String.valueOf(Prefs.blackBackground)).append("</tt>");
		sb.append("</div>");
		sb.append("</html>");
		new HTMLDialog(parentDialog, "Segmentation Details", sb.toString());

		// HTMLDialog dismissed: revert to initial state
		this.ip.setLut(lut);
		this.ip.setThreshold(lowerT, upperT, mode);
		this.img.updateAndDraw();
	}

	/**
	 * Retrieves values from the dialog, disabling dialog components that are
	 * not applicable. Returns false if no analysis method was chosen
	 *
	 * @param gd
	 *            reference to the GenericDialog
	 * @param e
	 *            the event generated by the user in the dialog
	 * @return true, if options are valid
	 */
	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {

		// components of GenericDialog
		final Vector<?> numericfields = gd.getNumericFields();
		final Vector<?> choices = gd.getChoices();
		final Vector<?> checkboxes = gd.getCheckboxes();

		// options common to bitmapPrompt() and csvPrompt()
		final TextField ieprimaryBranches;
		final Choice iepolyChoice, ienormChoice;
		final Checkbox ieinferPrimary, iechooseLog, ieshollNS, ieshollSLOG, ieshollLOG, iemask, ieoverlay;
		Checkbox iehideSaved = null;

		// options specific to bitmapPrompt();
		Choice iequadChoice = null, iebinChoice = null;

		final Object source = (e == null) ? null : e.getSource();
		int checkboxCounter = 0;
		int choiceCounter = 0;
		int fieldCounter = 0;

		if (isCSV) { // csvPrompt()

			if (isTableRequired()) {
				imgTitle = gd.getNextString();

				// Get columns choices and ensure rColumn and cColumn are not
				// the same
				rColumn = gd.getNextChoiceIndex();
				cColumn = gd.getNextChoiceIndex();
				final Choice ierColumn = (Choice) choices.elementAt(choiceCounter++);
				final Choice iecColumn = (Choice) choices.elementAt(choiceCounter++);
				if (rColumn == cColumn) {
					final int newChoice = (rColumn < ierColumn.getItemCount() - 1) ? rColumn + 1 : 0;
					if (source == ierColumn)
						iecColumn.select(newChoice);
					else if (source == iecColumn)
						ierColumn.select(newChoice);
				}

				limitCSV = gd.getNextBoolean();
				checkboxCounter++;
				is3D = gd.getNextBoolean();
				checkboxCounter++;
			}

			enclosingCutOff = (int) Math.max(1, gd.getNextNumber());
			primaryBranches = gd.getNextNumber();
			if (primaryBranches <= 0)
				primaryBranches = Double.NaN;
			fieldCounter = 1;
			ieprimaryBranches = (TextField) numericfields.elementAt(fieldCounter++);
			inferPrimary = gd.getNextBoolean();
			ieinferPrimary = (Checkbox) checkboxes.elementAt(checkboxCounter++);

			shollN = gd.getNextBoolean();
			checkboxCounter++;
			polyChoice = gd.getNextChoiceIndex();
			iepolyChoice = (Choice) choices.elementAt(choiceCounter++);

			chooseLog = gd.getNextBoolean();
			iechooseLog = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollNS = gd.getNextBoolean();
			ieshollNS = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollSLOG = gd.getNextBoolean();
			ieshollSLOG = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollLOG = gd.getNextBoolean();
			ieshollLOG = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			normChoice = gd.getNextChoiceIndex();
			ienormChoice = (Choice) choices.elementAt(choiceCounter++);

			verbose = gd.getNextBoolean();
			checkboxCounter++;

			if (validPath) {
				save = gd.getNextBoolean();
				checkboxCounter++;
				hideSaved = gd.getNextBoolean();
				iehideSaved = (Checkbox) checkboxes.elementAt(checkboxCounter++);
				iehideSaved.setEnabled(save);
			}

		} else { // bitmapPrompt()

			// Part I: Definition of Shells
			startRadius = Math.max(0, gd.getNextNumber());
			// final TextField iestartRadius =
			// (TextField)numericfields.elementAt(fieldCounter++);
			fieldCounter++;
			endRadius = Math.max(0, gd.getNextNumber());
			final TextField ieendRadius = (TextField) numericfields.elementAt(fieldCounter++);
			// fieldCounter++;
			incStep = Math.max(0, gd.getNextNumber());
			// final TextField ieincStep =
			// (TextField)numericfields.elementAt(fieldCounter++);
			fieldCounter++;
			if (endRadius <= startRadius || endRadius <= incStep) {
				ieendRadius.setForeground(Color.RED);
				IJ.showStatus("Error: Ending radius out of range!");
				return false;
			} else {
				ieendRadius.setForeground(Color.BLACK);
			}

			// Orthogonal chord options
			if (orthoChord) {
				trimBounds = gd.getNextBoolean();
				quadChoice = gd.getNextChoiceIndex();
				quadString = quads[quadChoice];
				checkboxCounter++;
				// final Checkbox ietrimBounds =
				// (Checkbox)checkboxes.elementAt(checkboxCounter++);
				iequadChoice = (Choice) choices.elementAt(choiceCounter++);
				iequadChoice.setEnabled(trimBounds);
			}

			// Part II: Multiple samples (2D) and noise filtering (3D)
			if (is3D) {
				skipSingleVoxels = gd.getNextBoolean();
				checkboxCounter++;
			} else {
				nSpans = Math.min(Math.max((int) gd.getNextNumber(), 1), 10);
				fieldCounter++;
				binChoice = gd.getNextChoiceIndex();
				iebinChoice = (Choice) choices.elementAt(choiceCounter++);
				iebinChoice.setEnabled(nSpans > 1);
			}

			// Part III: Indices and Curve Fitting
			enclosingCutOff = (int) Math.max(1, gd.getNextNumber()); // will
																		// become
																		// zero
																		// if
																		// NaN
			fieldCounter++;
			primaryBranches = gd.getNextNumber();
			if (primaryBranches <= 0)
				primaryBranches = Double.NaN;
			ieprimaryBranches = (TextField) numericfields.elementAt(fieldCounter++);
			inferPrimary = gd.getNextBoolean();
			ieinferPrimary = (Checkbox) checkboxes.elementAt(checkboxCounter++);

			fitCurve = gd.getNextBoolean();
			checkboxCounter++;
			verbose = gd.getNextBoolean();
			final Checkbox ieverbose = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			ieverbose.setEnabled(fitCurve);

			// Part IV: Sholl Methods
			shollN = gd.getNextBoolean();
			checkboxCounter++;

			polyChoice = gd.getNextChoiceIndex();
			iepolyChoice = (Choice) choices.elementAt(choiceCounter++);

			chooseLog = gd.getNextBoolean();
			iechooseLog = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollNS = gd.getNextBoolean();
			ieshollNS = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollSLOG = gd.getNextBoolean();
			ieshollSLOG = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			shollLOG = gd.getNextBoolean();
			ieshollLOG = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			normChoice = gd.getNextChoiceIndex();
			ienormChoice = (Choice) choices.elementAt(choiceCounter++);

			// Part V: Mask and outputs
			mask = gd.getNextBoolean();
			iemask = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			iemask.setEnabled(shollN || shollNS || shollSLOG || chooseLog);
			overlayShells = gd.getNextBoolean();
			ieoverlay = (Checkbox) checkboxes.elementAt(checkboxCounter++);
			ieoverlay.setEnabled(!is3D);

			if (validPath) {
				save = gd.getNextBoolean();
				checkboxCounter++;
				hideSaved = gd.getNextBoolean();
				iehideSaved = (Checkbox) checkboxes.elementAt(checkboxCounter++);
				iehideSaved.setEnabled(save);
			}

		}

		// Disable fields common to both prompts
		ieprimaryBranches.setEnabled(!inferPrimary);
		iepolyChoice.setEnabled(fitCurve && shollN); // fitCurve is true if
														// isCSV
		ieshollNS.setEnabled(!chooseLog);
		ieshollSLOG.setEnabled(!chooseLog);
		ieshollLOG.setEnabled(!chooseLog);
		ienormChoice.setEnabled(shollNS || shollSLOG || shollLOG || chooseLog);

		// Disable the OK button if no method is chosen
		final boolean proceed = (shollN || shollNS || shollSLOG || shollLOG || chooseLog);

		// Provide some interactive feedback (of sorts)
		String tipMsg = "NB: ";
		if (!proceed)
			tipMsg = "Error: At least one method needs to be chosen!";
		if (source != null) {
			if (source == iequadChoice)
				tipMsg += "The \"Restriction\" option requires an orthogonal line.";
			else if (source == iebinChoice)
				tipMsg += "The \"Integration\" option is disabled with 3D images.";
			else if (source == iepolyChoice)
				tipMsg += "The BAR update site allows fitting to polynomials of higher order.";
			else if (source == ienormChoice)
				tipMsg += "\"Annulus/Spherical shell\" requires non-continuous sampling.";
			else if (source == iechooseLog || source == ieshollNS || source == ieshollSLOG || source == ieshollLOG)
				tipMsg += "Determination ratio chooses most informative method.";
			else if (source == ieprimaryBranches || source == ieinferPrimary)
				tipMsg += "# Primary branches are used to calculate Schoenen indices.";
			else if (source == iehideSaved)
				tipMsg += "Saving path: " + imgPath;
			else if (!isCSV) {
				if (Double.isNaN(startRadius))
					tipMsg += "Starting radius: 0   ";
				if (Double.isNaN(endRadius))
					tipMsg += "Ending radius: max.   ";
				if (Double.isNaN(incStep) || incStep == 0)
					tipMsg += "Step size: continuous";
			}
		}
		IJ.showStatus(tipMsg);

		return proceed;

	}

	/**
	 * Creates the dialog for tabular data (bitmapPrompt is the main prompt).
	 */
	private boolean csvPrompt() {

		if (isTableRequired() && !validTable(csvRT))
			return false;

		final EnhancedGenericDialog gd = new EnhancedGenericDialog("Sholl Analysis v" + VERSION + " (Tabular Data)");
		final Font headerFont = new Font("SansSerif", Font.BOLD, 12);
		final int xIndent = 40;
		gd.setInsets(0, 0, 0);

		// Part I: Import options unless analyzeTabularInput() methods have
		// been called
		if (isTableRequired()) {
			gd.addMessage("I. Results Table Import Options:", headerFont);
			gd.addStringField("Name of dataset", imgTitle, 20);
			final String[] headings = csvRT.getHeadings();
			gd.addChoice("Distance column", headings, headings[0]);
			gd.addChoice("Intersections column", headings, headings[1]);
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("Restrict analysis to selected rows only (if any)", limitCSV);
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("3D data? (uncheck if 2D profile)", is3D);
			gd.setInsets(15, 0, 2);
		}

		// Part II: Indices and Curve Fitting
		gd.addHyperlinkMessage("II. Descriptors and Ramification Indices:", headerFont, Color.BLACK,
				URL + "#Descriptors_and_Curve_Fitting");
		gd.addNumericField("Enclosing radius cutoff", enclosingCutOff, 0, 6, "intersection(s)");
		gd.addNumericField(" #_Primary branches", primaryBranches, 0);
		gd.setInsets(0, 2 * xIndent, 0);
		gd.addCheckbox("Infer from starting radius", inferPrimary);

		// Part III: Sholl Methods
		gd.setInsets(15, 0, 2);
		gd.addHyperlinkMessage("III. Sholl Methods:", headerFont, Color.BLACK, URL + "#Choice_of_Methods");
		gd.setInsets(0, xIndent / 2, 2);
		gd.addMessage("Profiles Without Normalization:");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckbox("Linear", shollN);
		gd.setInsets(0, 0, 0);
		gd.addChoice("Polynomial", DEGREES, DEGREES[polyChoice]);

		gd.setInsets(8, xIndent / 2, 2);
		gd.addMessage("Normalized Profiles:");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckboxGroup(2, 2, new String[] { "Most informative", SHOLL_TYPES[SHOLL_NS], SHOLL_TYPES[SHOLL_SLOG],
				SHOLL_TYPES[SHOLL_LOG] }, new boolean[] { chooseLog, shollNS, shollSLOG, shollLOG });

		final String[] norms = new String[NORMS3D.length];
		for (int i = 0; i < norms.length; i++) {
			norms[i] = NORMS2D[i] + "/" + NORMS3D[i];
		}
		gd.setInsets(2, 0, 0);
		gd.addChoice("Normalizer", norms, norms[normChoice]);

		gd.setInsets(15, 0, 2);
		gd.addHyperlinkMessage("IV. Output Options:", headerFont, Color.BLACK, URL + "#Output_Options");
		gd.setInsets(0, xIndent, 0);
		gd.addCheckbox("Show fitting details", verbose);
		if (validPath) {
			gd.setInsets(0, xIndent, 0);
			gd.addCheckbox("Save results in directory of imported profile", save);
			gd.setInsets(0, 2 * xIndent, 0);
			gd.addCheckbox("Do not display saved files", hideSaved);
		} else {
			gd.setInsets(0, xIndent, 0);
			gd.addMessage("Saving options disabled. Path of data unknow...", null, gd.getDisabledComponentColor());
		}

		Sholl_Utils.addCitationUrl(gd);
		gd.assignPopupToHelpButton(createOptionsMenu(gd));
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);
		gd.showScrollableDialog();

		if (gd.wasCanceled())
			return false;
		else if (gd.wasOKed()) {
			improveRecording();
			return dialogItemChanged(gd, null);
		} else {
			return false;
		}
	}

	/** Checks if table is valid while warning user about it */
	private boolean validTable(final ResultsTable table) {
		if (table == null) {
			return false;
		} else if (table.getHeadings().length < 2 || table.getCounter() < 2) {
			lError("Profile does not contain enough data points.",
					"N.B. At least " + (SMALLEST_DATASET + 1) + " pairs of values are required for curve fitting.");
			return false;
		} else
			return true;
	}

	/**
	 * Measures intersections for each sphere surface (does the actual 3D
	 * analysis). Accepts an array of radii and takes the measurement(s) at each
	 * radius. Requires threshold values to be set beforehand using
	 * {@link #setThreshold(int, int)}.
	 *
	 * @param xc
	 *            the X position of the analysis center
	 * @param yc
	 *            the Y position of the analysis center
	 * @param zc
	 *            the Z position (slice) of the analysis center
	 * @param radii
	 *            the sampling distances
	 * @param img
	 *            The image being analyzed
	 * @return intersection counts (the linear profile of sampled data)
	 */
	static public synchronized double[] analyze3D(final int xc, final int yc, final int zc, final double[] radii,
			final ImagePlus img) {

		int nspheres;

		// Create an array to hold results
		final double[] data = new double[nspheres = radii.length];

		// Get Image Stack
		final ImageStack stack = (img.isComposite()) ? ChannelSplitter.getChannel(img, channel) : img.getStack();

		// Split processing across the number of available CPUs
		final AtomicInteger ai = new AtomicInteger(0);
		final int n_cpus = Prefs.getThreads();
		final Thread[] threads = ThreadUtil.createThreadArray(n_cpus);
		setThreadedCounter(0);

		for (int ithread = 0; ithread < threads.length; ithread++) {

			final int chunkSize = (nspheres + n_cpus - 1) / n_cpus; // divide by
																	// threads
																	// rounded
																	// up.
			final int start = ithread * chunkSize;
			final int end = Math.min(start + chunkSize, nspheres);

			threads[ithread] = new Thread() {

				@Override
				public void run() {
					for (int k = ai.getAndIncrement(); k < n_cpus; k = ai.getAndIncrement()) {

						for (int s = start; s < end; s++) {
							final int counter = getThreadedCounter();
							IJ.showProgress(counter, nspheres);
							IJ.showStatus("Sampling sphere " + (counter + 1) + "/" + nspheres + " (" + n_cpus
									+ " threads). Press 'Esc' to abort...");
							setThreadedCounter(counter + 1);
							if (IJ.escapePressed()) {
								IJ.beep();
								return;
							}

							// Initialize ArrayLists to hold surface points
							final ArrayList<int[]> points = new ArrayList<int[]>();

							// Restrain analysis to the smallest volume for this
							// sphere
							final int xmin = Math.max(xc - (int) Math.round(radii[s] / vxWH), minX);
							final int ymin = Math.max(yc - (int) Math.round(radii[s] / vxWH), minY);
							final int zmin = Math.max(zc - (int) Math.round(radii[s] / vxD), minZ);
							final int xmax = Math.min(xc + (int) Math.round(radii[s] / vxWH), maxX);
							final int ymax = Math.min(yc + (int) Math.round(radii[s] / vxWH), maxY);
							final int zmax = Math.min(zc + (int) Math.round(radii[s] / vxD), maxZ);

							try {
								for (int z = zmin; z < zmax; z++) {
									for (int y = ymin; y < ymax; y++) {
										for (int x = xmin; x < xmax; x++) {
											final double dx = Math.sqrt((x - xc) * vxWH * (x - xc) * vxWH
													+ (y - yc) * vxWH * (y - yc) * vxWH
													+ (z - zc) * vxD * (z - zc) * vxD);
											if (Math.abs(dx - radii[s]) < 0.5) {
												final double value = stack.getVoxel(x, y, z);
												if (value >= lowerT && value <= upperT && (!skipSingleVoxels
														|| skipSingleVoxels && hasNeighbors(x, y, z, stack))) {
													points.add(new int[] { x, y, z });
												}
											}
										}
									}
								}

								// We now have the the points intercepting the
								// surface of this Sholl sphere. Lets check if
								// their respective pixels are clustered
								data[s] = count3Dgroups(points);

							} catch (final Exception e) {

								final StringWriter sw = new StringWriter();
								final PrintWriter pw = new PrintWriter(sw);
								e.printStackTrace(pw);
								final String spacer = "*** *** ***";
								IJ.log(" \n" + spacer);
								IJ.log("An error occurred while sampling shell " + (s + 1) + ". We'll now do our\n"
										+ "best to continue... Please include the following Exception\n"
										+ "when reporting this bug (together with the info retrieved\n"
										+ "from \"Plugins>Utilities>ImageJ Properties\"):\n \n" + sw.toString());
								IJ.log(spacer);
								return;

							}

						}

					}
				}
			};
		}
		ThreadUtil.startAndJoin(threads);

		return data;

	}

	/**
	 * Returns true if at least one of the 6-neighboring voxels of this position
	 * is thresholded and if position does not correspond to an edge voxel.
	 */
	static private boolean hasNeighbors(final int x, final int y, final int z, final ImageStack stack) {

		final int[][] neighboors = new int[6][3];

		neighboors[0] = new int[] { x - 1, y, z };
		neighboors[1] = new int[] { x + 1, y, z };
		neighboors[2] = new int[] { x, y - 1, z };
		neighboors[3] = new int[] { x, y + 1, z };
		neighboors[4] = new int[] { x, y, z + 1 };
		neighboors[5] = new int[] { x, y, z - 1 };

		boolean clustered = false;
		double value;
		for (int i = 0; i < neighboors.length; i++) {
			try {
				value = stack.getVoxel(neighboors[i][0], neighboors[i][1], neighboors[i][2]);
				if (value >= lowerT && value <= upperT) {
					clustered = true;
					break;
				}
			} catch (final IndexOutOfBoundsException ignored) { // Edge voxel:
																// Neighborhood
																// unknown.
				clustered = false;
				break;
			}
		}

		return clustered;

	}

	/**
	 * Counts clusters of 26-connected voxels from an ArrayList of 3D
	 * coordinates, as per {@link #countGroups(int[][], ImageProcessor)}. Note
	 * that 2D "spike supression" is not performed.
	 *
	 * @param points
	 *            the the x,y,z voxel positions
	 * @return the number of detected clusters
	 */
	static public int count3Dgroups(final ArrayList<int[]> points) {

		int target, source, groups, len;

		final int[] grouping = new int[len = groups = points.size()];

		for (int i = 0; i < groups; i++)
			grouping[i] = i + 1;

		for (int i = 0; i < len; i++) {
			// IJ.showProgress(i, len+1);
			for (int j = 0; j < len; j++) {
				if (i == j)
					continue;

				// Compute the chessboard (Chebyshev) distance for this point. A
				// chessboard distance of 1 in xy (lateral) underlies
				// 8-connectivity within the plane. A distance of 1 in z (axial)
				// underlies 26-connectivity in 3D
				final int lDist = Math.max(Math.abs(points.get(i)[0] - points.get(j)[0]),
						Math.abs(points.get(i)[1] - points.get(j)[1]));
				final int aDist = Math.max(Math.abs(points.get(i)[2] - points.get(j)[2]), lDist);
				if ((lDist * aDist <= 1) && (grouping[i] != grouping[j])) {
					source = grouping[i];
					target = grouping[j];
					for (int k = 0; k < len; k++)
						if (grouping[k] == target)
							grouping[k] = source;
					groups--;
				}
			}
		}
		return groups;

	}

	/**
	 * Does the actual 2D analysis. Accepts an array of radii and takes the
	 * measurement(s) at each radius. Requires threshold values to be set
	 * beforehand using {@link #setThreshold(int, int)}.
	 *
	 * @param xc
	 *            the X position of the analysis center
	 * @param yc
	 *            the Y position of the analysis center
	 * @param radii
	 *            the sampling distances
	 * @param pixelSize
	 *            pixel dimensions (spatial calibration)
	 * @param binsize
	 *            the number of samples to be retrieved at each radius
	 * @param bintype
	 *            flag for integration of multiple samples:
	 *            {@value #BIN_AVERAGE}, {@value #BIN_MEDIAN} or
	 *            {@value #BIN_MODE}
	 * @param ip
	 *            ImageProcessor of analyzed image
	 * @return intersection counts (the linear profile of sampled data)
	 */
	static public double[] analyze2D(final int xc, final int yc, final double[] radii, final double pixelSize,
			final int binsize, final int bintype, final ImageProcessor ip) {

		int i, j, k, rbin, sum, size;
		int[] binsamples, pixels;
		int[][] points;
		double[] data;

		// Create an array to hold the results
		data = new double[size = radii.length];

		// Create array for bin samples. Passed value of binsize must be at
		// least 1
		binsamples = new int[binsize];

		IJ.showStatus(
				"Sampling " + size + " radii, " + binsize + " measurement(s) per radius. Press 'Esc' to abort...");

		// Outer loop to control the analysis bins
		for (i = 0; i < size; i++) {

			// Retrieve the radius in pixel coordinates and set the largest
			// radius of this bin span
			rbin = (int) Math.round(radii[i] / pixelSize + binsize / 2);

			// Inner loop to gather samples for each bin
			for (j = 0; j < binsize; j++) {

				// Get the circumference pixels for this radius
				points = getCircumferencePoints(xc, yc, rbin--);
				pixels = getPixels(ip, points);

				// Count the number of intersections
				binsamples[j] = countTargetGroups(pixels, points, ip);

			}

			IJ.showProgress(i, size * binsize);
			if (IJ.escapePressed()) {
				IJ.beep();
				return data;
			}

			// Statistically combine bin data
			if (binsize > 1) {
				if (bintype == BIN_MEDIAN) {

					// Sort the bin data
					Arrays.sort(binsamples);

					// Pull out the median value: average the two middle values
					// if no center exists otherwise pull out the center value
					if (binsize % 2 == 0)
						data[i] = (binsamples[binsize / 2] + binsamples[binsize / 2 - 1]) / 2.0;
					else
						data[i] = binsamples[binsize / 2];

				} else if (bintype == BIN_AVERAGE) {

					// Mean: Find the samples sum and divide by n. of samples
					for (sum = 0, k = 0; k < binsize; k++)
						sum += binsamples[k];
					data[i] = ((double) sum) / ((double) binsize);

				} else if (bintype == BIN_MODE) {

					// Mode: Find the value that appears most often. The first
					// sampled value is used if no mode exists
					int mode = 0, maxCount = 0;
					for (int ma = 0; ma < binsize; ma++) {
						int tempCount = 0;
						for (int mb = 0; mb < binsize; mb++)
							if (binsamples[mb] == binsamples[ma])
								tempCount++;
						if (tempCount > maxCount) {
							maxCount = tempCount;
							mode = binsamples[ma];
						}
					}
					data[i] = mode;

				}

			} else // There was only one sample
				data[i] = binsamples[0];

		}

		return data;
	}

	/**
	 * Counts how many groups of non-zero pixels are present in a given array of
	 * "masked" pixels. A group consists of a formation of adjacent pixels,
	 * where adjacency is true for all eight neighboring positions around a
	 * given pixel. Requires threshold values to be set beforehand using
	 * {@link #setThreshold(int, int)}.
	 *
	 * Parameters: ip the image being analyzed points the x,y pixel positions
	 * Returns: the masked pixel arrays
	 *
	 * @param pixels
	 *            the array containing the masked pixels (1: foreground,
	 *            0:background) as returned by
	 *            {@link #getPixels(ImageProcessor, int[][])}
	 * @param rawpoints
	 *            the x,y pixel positions
	 * @param ip
	 *            reference to the image being analyzed
	 * @return the number of non-zero clusters
	 */
	static public int countTargetGroups(final int[] pixels, final int[][] rawpoints, final ImageProcessor ip) {

		int i, j;
		int[][] points;

		// Count how many target pixels (i.e., foreground, non-zero) we have
		for (i = 0, j = 0; i < pixels.length; i++)
			if (pixels[i] != 0.0)
				j++;

		// Create an array to hold target pixels
		points = new int[j][2];

		// Copy all target pixels into the array
		for (i = 0, j = 0; i < pixels.length; i++)
			if (pixels[i] != 0.0)
				points[j++] = rawpoints[i];

		return countGroups(points, ip);

	}

	/**
	 * For a given set of points of a segmented 2D image, counts how many groups
	 * (clusters) of 8-connected pixels exist within the set. Requires threshold
	 * values to be set beforehand using {@link #setThreshold(int, int)
	 * setThreshol()}.
	 *
	 * @param points
	 *            the x,y pixel positions
	 * @param ip
	 *            the image being analyzed
	 * @return the number of detected clusters
	 * @see #countSinglePixels
	 */
	static public int countGroups(final int[][] points, final ImageProcessor ip) {

		int i, j, k, target, source, groups, len, dx;

		// Create an array to hold the point grouping data
		final int[] grouping = new int[len = points.length];

		// Initialize each point to be in a unique group
		for (i = 0, groups = len; i < groups; i++)
			grouping[i] = i + 1;

		for (i = 0; i < len; i++)
			for (j = 0; j < len; j++) {

				// Don't compare the same point with itself
				if (i == j)
					continue;

				// Compute the chessboard (Chebyshev) distance. A distance of 1
				// underlies 8-connectivity
				dx = Math.max(Math.abs(points[i][0] - points[j][0]), Math.abs(points[i][1] - points[j][1]));

				// Should these two points be in the same group?
				if ((dx == 1) && (grouping[i] != grouping[j])) {

					// Record which numbers we're changing
					source = grouping[i];
					target = grouping[j];

					// Change all targets to sources
					for (k = 0; k < len; k++)
						if (grouping[k] == target)
							grouping[k] = source;

					// Update the number of groups
					groups--;

				}
			}

		if (doSpikeSupression)
			groups -= countSinglePixels(points, len, grouping, ip);

		return groups;
	}

	/**
	 * Counts 1-pixel groups that exist solely on the edge of a "stair" of
	 * target pixels
	 */
	static private int countSinglePixels(final int[][] points, final int pointsLength, final int[] grouping,
			final ImageProcessor ip) {

		int counts = 0;

		for (int i = 0; i < pointsLength; i++) {

			// Check for other members of this group
			boolean multigroup = false;
			for (int j = 0; j < pointsLength; j++) {
				if (i == j)
					continue;
				if (grouping[i] == grouping[j]) {
					multigroup = true;
					break;
				}
			}

			// If not a single-pixel group, try again
			if (multigroup)
				continue;

			// Store the coordinates of this point
			final int dx = points[i][0];
			final int dy = points[i][1];

			// Calculate the 8 neighbors surrounding this point
			final int[][] testpoints = new int[8][2];
			testpoints[0][0] = dx - 1;
			testpoints[0][1] = dy + 1;
			testpoints[1][0] = dx;
			testpoints[1][1] = dy + 1;
			testpoints[2][0] = dx + 1;
			testpoints[2][1] = dy + 1;
			testpoints[3][0] = dx - 1;
			testpoints[3][1] = dy;
			testpoints[4][0] = dx + 1;
			testpoints[4][1] = dy;
			testpoints[5][0] = dx - 1;
			testpoints[5][1] = dy - 1;
			testpoints[6][0] = dx;
			testpoints[6][1] = dy - 1;
			testpoints[7][0] = dx + 1;
			testpoints[7][1] = dy - 1;

			// Pull out the pixel values for these points
			final int[] px = getPixels(ip, testpoints);

			// Now perform the stair checks
			if ((px[0] != 0 && px[1] != 0 && px[3] != 0 && px[4] == 0 && px[6] == 0 && px[7] == 0)
					|| (px[1] != 0 && px[2] != 0 && px[4] != 0 && px[3] == 0 && px[5] == 0 && px[6] == 0)
					|| (px[4] != 0 && px[6] != 0 && px[7] != 0 && px[0] == 0 && px[1] == 0 && px[3] == 0)
					|| (px[3] != 0 && px[5] != 0 && px[6] != 0 && px[1] == 0 && px[2] == 0 && px[4] == 0))

				counts++;

		}

		return counts;

	}

	/**
	 * Retrieves a mask for a given set of points of a segmented 2D image. Pixel
	 * intensities within the thresholded range will be set to 1, remaining
	 * intensities to 0. Requires threshold values to be set beforehand using
	 * {@link #setThreshold(int, int)}.
	 *
	 * @param ip
	 *            the image being analyzed
	 * @param points
	 *            the x,y pixel positions
	 * @return the masked pixel arrays
	 */
	static public int[] getPixels(final ImageProcessor ip, final int[][] points) {

		int value;

		// Initialize the array to hold the pixel values. int arrays are
		// initialized to a default value of 0
		final int[] pixels = new int[points.length];

		// Put the pixel value for each circumference point in the pixel array
		for (int i = 0; i < pixels.length; i++) {

			// We already filtered out of bounds coordinates in
			// getCircumferencePoints
			value = ip.getPixel(points[i][0], points[i][1]);
			if (value >= lowerT && value <= upperT)
				pixels[i] = 1;
		}

		return pixels;

	}

	/**
	 * Returns the location of pixels clockwise along a (1-pixel wide)
	 * circumference using Bresenham's Circle Algorithm.
	 *
	 * @param cx
	 *            the X position of the analysis center
	 * @param cy
	 *            the Y position of the analysis center
	 * @param radius
	 *            the circumference radius
	 * @return the circumference points
	 */
	static public int[][] getCircumferencePoints(final int cx, final int cy, final int radius) {

		// Initialize algorithm variables
		int i = 0, x = 0, y = radius;
		final int r = radius + 1;
		int err = 0, errR, errD;

		// Array to store first 1/8 of points relative to center
		final int[][] data = new int[r][2];

		do {
			// Add this point as part of the circumference
			data[i][0] = x;
			data[i++][1] = y;

			// Calculate the errors for going right and down
			errR = err + 2 * x + 1;
			errD = err - 2 * y + 1;

			// Choose which direction to go
			if (Math.abs(errD) < Math.abs(errR)) {
				y--;
				err = errD; // Go down
			} else {
				x++;
				err = errR; // Go right
			}
		} while (x <= y);

		// Create an array to hold the absolute coordinates
		final int[][] points = new int[r * 8][2];

		// Loop through the relative circumference points
		for (i = 0; i < r; i++) {

			// Pull out the point for quick access;
			x = data[i][0];
			y = data[i][1];

			// Convert the relative point to an absolute point
			points[i][0] = x + cx;
			points[i][1] = y + cy;

			// Use geometry to calculate remaining 7/8 of the circumference
			// points
			points[r * 4 - i - 1][0] = x + cx;
			points[r * 4 - i - 1][1] = -y + cy;
			points[r * 8 - i - 1][0] = -x + cx;
			points[r * 8 - i - 1][1] = y + cy;
			points[r * 4 + i][0] = -x + cx;
			points[r * 4 + i][1] = -y + cy;
			points[r * 2 - i - 1][0] = y + cx;
			points[r * 2 - i - 1][1] = x + cy;
			points[r * 2 + i][0] = y + cx;
			points[r * 2 + i][1] = -x + cy;
			points[r * 6 + i][0] = -y + cx;
			points[r * 6 + i][1] = x + cy;
			points[r * 6 - i - 1][0] = -y + cx;
			points[r * 6 - i - 1][1] = -x + cy;

		}

		// Count how many points are out of bounds, while eliminating
		// duplicates. Duplicates are always at multiples of r (8 points)
		int pxX, pxY, count = 0, j = 0;
		for (i = 0; i < points.length; i++) {

			// Pull the coordinates out of the array
			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && pxX >= minX && pxX <= maxX && pxY >= minY && pxY <= maxY)
				count++;
		}

		// Create the final array containing only unique points within bounds
		final int[][] refined = new int[count][2];

		for (i = 0; i < points.length; i++) {

			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && pxX >= minX && pxX <= maxX && pxY >= minY && pxY <= maxY) {
				refined[j][0] = pxX;
				refined[j++][1] = pxY;

			}

		}

		// Return the array
		return refined;

	}

	/**
	 * Removes all ROIs stored the specified overlay that have been added by
	 * {@link #overlayShells()}
	 *
	 * @param overlay
	 *            the {@link ij.gui.Overlay Overlay} containing the sampling
	 *            shells
	 */
	private void removeOverlayShells(final Overlay overlay) {
		if (overlay != null) {
			for (int i = overlay.size() - 1; i >= 0; i--) {
				final String roiName = overlay.get(i).getName();
				if (roiName.equals("center") || roiName.startsWith("r="))
					overlay.remove(i);
			}
		}
	}

	/** Adds 2D sampling shells to the overlay of plugin's input image */
	private void overlayShells() {

		final Calibration cal = img.getCalibration();
		Overlay overlay = img.getOverlay();
		final boolean newOverlay = overlay == null;
		if (newOverlay)
			overlay = new Overlay();
		else
			removeOverlayShells(overlay);

		overlay.add(new PointRoi(x, y), "center");
		for (final double r : radii) {
			final double rawR = r / vxSize;
			final Roi shell;

			// TODO: Some sort of meridian geodesics overlay for 3D shells?
			// https://en.wikipedia.org/wiki/Geodesics_on_an_ellipsoid

			if (orthoChord && trimBounds) { // 2D analysis using semicircle
											// shells
				final Arc2D.Double arc = new Arc2D.Double();
				if (quadString.equals(QUAD_NORTH))
					arc.setArcByCenter(x, y, rawR, 0, 180, Arc2D.OPEN);
				else if (quadString.equals(QUAD_SOUTH))
					arc.setArcByCenter(x, y, rawR, -180, 180, Arc2D.OPEN);
				else if (quadString.equals(QUAD_WEST))
					arc.setArcByCenter(x, y, rawR, 90, -180, Arc2D.OPEN);
				else if (quadString.equals(QUAD_EAST))
					arc.setArcByCenter(x, y, rawR, -90, -180, Arc2D.OPEN);
				else
					throw new IllegalArgumentException("Invalid restriction choice: " + quadString);
				shell = new ShapeRoi(arc);
			} else { // 2D analysis using circular shells
				shell = new OvalRoi(x - rawR, y - rawR, 2 * rawR, 2 * rawR);
			}
			// shell.setStrokeColor(Color.CYAN);
			if (nSpans > 1)
				shell.setStrokeWidth(nSpans);
			overlay.add(shell, "r=" + IJ.d2s(r, 2) + cal.getUnit());
		}

		if (newOverlay)
			img.setOverlay(overlay);
		else
			img.draw();
	}

	/**
	 * Sets the threshold limits. With grayscale images, the method must be
	 * called upfront since analysis is performed on segmented arbors. Note that
	 * with binary images, background is always considered to be 0,
	 * independently of {@link ij.Prefs#blackBackground}
	 *
	 * @param lower
	 *            the lower threshold value
	 * @param upper
	 *            the upper threshol value
	 */
	public void setThreshold(final int lower, final int upper) {
		Sholl_Analysis.lowerT = lower;
		Sholl_Analysis.upperT = upper;
	}

	private ImagePlus makeMask(final ImagePlus img, final String ttl, final double[][] values, final int xc,
			final int yc, final Calibration cal, final boolean floatProcessor) {
		final double[] yvalues = new double[values.length];
		for (int i = 0; i < values.length; i++)
			yvalues[i] = values[i][1];
		return makeMask(ttl, yvalues, xc, yc, cal, floatProcessor);
	}

	/**
	 * Creates a 2D Sholl heatmap by applying measured values to the foreground
	 * pixels of a copy of the analyzed image
	 */
	private ImagePlus makeMask(final String ttl, final double[] values, final int xc, final int yc,
			final Calibration cal, final boolean floatProcessor) {

		if (values == null)
			return null;

		// Work on a stack projection when dealing with a volume
		if (is3D)
			ip = projInputImg();

		// NB: 16-bit image: Negative values will be set to 0
		final ImageProcessor mp = (floatProcessor) ? new FloatProcessor(ip.getWidth(), ip.getHeight())
				: new ShortProcessor(ip.getWidth(), ip.getHeight());

		// Retrieve drawing steps as endRadius may have never been reached (eg,
		// if user interrupted analysis by pressing Esc)
		final int drawSteps = values.length;
		final int firstRadius = (int) Math.round(startRadius / vxWH);
		final int lastRadius = (int) Math.round((startRadius + (drawSteps - 1) * stepRadius) / vxWH);
		final int drawWidth = (int) Math.round((lastRadius - startRadius) / drawSteps);

		for (int i = 0; i < drawSteps; i++) {

			IJ.showProgress(i, drawSteps);
			int drawRadius = firstRadius + (i * drawWidth);

			for (int j = 0; j < drawWidth; j++) {

				// this will already exclude pixels out of bounds
				final int[][] points = getCircumferencePoints(xc, yc, drawRadius++);
				for (int k = 0; k < points.length; k++)
					for (int l = 0; l < points[k].length; l++) {
						final double value = ip.getPixel(points[k][0], points[k][1]);
						if (value >= lowerT && value <= upperT)
							mp.putPixelValue(points[k][0], points[k][1], values[i]);
					}
			}
		}

		// Apply LUT
		final double[] range = Tools.getMinMax(values);
		final boolean logMask = floatProcessor && range[1] < 0;
		final int fcolor = (logMask) ? Options.getMaskBackground() : -1;
		final int bcolor = (fcolor == -1) ? Options.getMaskBackground() : -1;
		mp.setColorModel(Sholl_Utils.matlabJetColorMap(bcolor, fcolor));
		mp.setMinAndMax(logMask ? range[0] : 0, range[1]);

		final String title = ttl + "_ShollMask.tif";
		final ImagePlus img2 = new ImagePlus(title, mp);

		// Apply calibration, set mask label and mark center of analysis
		img2.setCalibration(cal);
		img2.setRoi(new PointRoi(xc, yc));

		if (validPath && save) {
			IJ.save(img2, imgPath + title);
		}
		return img2;

	}

	/**
	 * Returns the MIP of input image according to analysis parameters
	 * ({@code minZ}, {@code maxZ} and selected {@code channel})
	 *
	 * @return {@link ImageProcessor} of max Z-Projection
	 */
	private ImageProcessor projInputImg() {
		ImageProcessor ip;
		final ZProjector zp = new ZProjector(img);
		zp.setMethod(ZProjector.MAX_METHOD);
		zp.setStartSlice(minZ);
		zp.setStopSlice(maxZ);
		if (img.isComposite()) {
			zp.doHyperStackProjection(false);
			final ImagePlus projImp = zp.getProjection();
			projImp.setC(channel);
			ip = projImp.getChannelProcessor();
		} else {
			zp.doProjection();
			ip = zp.getProjection().getProcessor();
		}
		return ip;
	}

	/**
	 * Checks if image to be analyzed is valid.
	 *
	 * @param imp
	 *            the image being analyzed
	 * @return true, if valid (8/16 bit, 2D/3D, single or multi-channel)
	 */
	public boolean validateImage(final ImagePlus imp) {
		return (imp != null && imp.getBitDepth() < 24 && imp.getNDimensions() <= 4
				&& !(imp.getNSlices() > 1 && imp.getNFrames() > 1));
	}

	/**
	 * Checks if image is valid (segmented grayscale), sets validPath and
	 * returns its ImageProcessor
	 */
	private ImageProcessor getValidProcessor(final ImagePlus img) {

		ImageProcessor ip = null;
		String exitmsg = "";
		String tipMsg = "Run \"Image>Type>8/16-bit\" to change image type";

		if (img == null) {
			tipMsg = "Press \"Analyze Sample Image\" for a demo";
			exitmsg = "There are no images open.";
		} else {

			final int type = img.getBitDepth();
			if (type == 24)
				exitmsg = "RGB color images are not supported.";
			else if (type == 32)
				exitmsg = "32-bit grayscale images are not supported.";
			else if (img.getNDimensions() > 4 || (img.getNSlices() > 1 && img.getNFrames() > 1))
				exitmsg = "Images with a temporal axis are not supported.";
			else { // 8/16-bit grayscale image

				ip = (img.isComposite()) ? img.getChannelProcessor() : img.getProcessor();
				final double lower = ip.getMinThreshold();
				if (lower != ImageProcessor.NO_THRESHOLD) {
					lowerT = lower;
					upperT = ip.getMaxThreshold();
				} else if (ip.isBinary()) { // binary images: background is zero
					lowerT = upperT = 255;
				} else {
					tipMsg = "Run \"Image>Adjust>Threshold...\" before running the plugin";
					exitmsg = (img.isComposite()) ? "Multi-channel image is not thresholded."
							: "Image is not thresholded.";
				}
			}
		}

		if (!"".equals(exitmsg)) {
			IJ.showStatus("Tip: " + tipMsg + "...");
			lError(exitmsg,
					"This plugin requires a segmented arbor (2D/3D). Either:\n"
							+ "	 - A binary image (Arbor: non-zero value)\n"
							+ "	 - A thresholded grayscale image (8/16-bit)\n"
							+ "	 NB: To threshold a multi-channel image, display it as\n"
							+ "	        grayscale using \"Image>Color>Channels Tool...\"");
			return null;
		}

		// Retrieve image path and check if it is valid
		imgPath = IJ.getDirectory("image");
		if (imgPath == null) {
			validPath = false;
		} else {
			final File dir = new File(imgPath);
			validPath = dir.exists() && dir.isDirectory();
		}

		return ip;
	}

	/** Creates optionsMenu */
	private JPopupMenu createOptionsMenu(final EnhancedGenericDialog gd) {
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem mi;
		if (isCSV) {
			mi = new JMenuItem("Analyze Other Data...");
			mi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					gd.dispatchEvent(new WindowEvent(gd, WindowEvent.WINDOW_CLOSING));
					gd.dispose();
					improveRecording();
					run("csv");
				}
			});
			popup.add(mi);
		} else {
			mi = new JMenuItem("Cf. Segmentation");
			mi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					offlineHelp(gd);
				}
			});
			popup.add(mi);
		}
		mi = new JMenuItem("Options...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.doCommand("Metrics & Options...");
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = new JMenuItem(isCSV ? "Analyze image..." : "Analyze Tabular Data...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				gd.dispatchEvent(new WindowEvent(gd, WindowEvent.WINDOW_CLOSING));
				gd.dispose();
				improveRecording();
				run(isCSV ? "" : "csv");
				// IJ.doCommand(isCSV ? "Sholl Analysis..." : "Sholl Analysis
				// (Tabular Data)...");
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = new JMenuItem("Online documentation");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", isCSV ? URL + "#Importing" : URL);
			}
		});
		popup.add(mi);
		mi = new JMenuItem("About & Resources...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn("sholl.Sholl_Utils", "about");
			}
		});
		popup.add(mi);
		return popup;
	}

	/** Retrieves the median of an array */
	private static double getMedian(final double[] array) {
		final int size = array.length;
		Arrays.sort(array);
		final double median;
		if (size % 2 == 0)
			median = (array[size / 2] + array[size / 2 - 1]) / 2;
		else
			median = array[size / 2];
		return median;
	}

	/** Returns the Sholl Summary Table populated with profile statistics */
	private ResultsTable createStatsTable(final String rowLabel, final int xc, final int yc, final int zc,
			final double[][] values) {

		ResultsTable rt;
		final TextWindow window = (TextWindow) WindowManager.getFrame(SHOLLTABLE);
		if (window == null)
			rt = new ResultsTable();
		else
			rt = window.getTextPanel().getResultsTable();
		rt.setNaNEmptyCells(true);

		double sumY = 0, maxIntersect = 0, maxR = 0, enclosingR = Double.NaN;

		// Retrieve simple statistics
		final int size = values.length;
		final double[] x = new double[size];
		final double[] y = new double[size];
		for (int i = 0; i < size; i++) {
			x[i] = values[i][0];
			y[i] = values[i][1];
			if (y[i] > maxIntersect) {
				maxIntersect = y[i];
				maxR = x[i];
			}
			if (y[i] >= enclosingCutOff)
				enclosingR = x[i];
			sumY += y[i];
		}

		// Calculate ramification index, the maximum of intersection divided by
		// the n. of primary branches, assumed to be the n. intersections at
		// starting radius
		final double ri;
		if (inferPrimary)
			ri = maxIntersect / y[0];
		else if (!(primaryBranches == 0 || Double.isNaN(primaryBranches)))
			ri = maxIntersect / primaryBranches;
		else
			ri = Double.NaN;

		rt.incrementCounter();
		rt.setPrecision(Options.getScientificNotationAwarePrecision());
		rt.addValue("Image", rowLabel);
		if ((prefs & Options.DIRECTORY) != 0)
			rt.addValue("Directory", (validPath) ? imgPath : "Unknown");
		final String comment = Options.getCommentString();
		if (comment != null)
			rt.addValue("Comment", comment);
		if (!isCSV && img != null && img.isComposite())
			rt.addValue("Channel", channel);
		if ((prefs & Options.UNIT) != 0)
			rt.addValue("Unit", unit);
		if ((prefs & Options.THRESHOLD) != 0) {
			rt.addValue("Lower threshold", isCSV ? Double.NaN : lowerT);
			rt.addValue("Upper threshold", isCSV ? Double.NaN : upperT);
		}
		if ((prefs & Options.CENTER) != 0) {
			rt.addValue("X center (px)", isCSV ? Double.NaN : xc);
			rt.addValue("Y center (px)", isCSV ? Double.NaN : yc);
			rt.addValue("Z center (slice)", isCSV ? Double.NaN : zc);
		}
		if ((prefs & Options.STARTING_RADIUS) != 0)
			rt.addValue("Starting radius", startRadius);
		if ((prefs & Options.ENDING_RADIUS) != 0)
			rt.addValue("Ending radius", endRadius);
		if ((prefs & Options.RADIUS_STEP) != 0)
			rt.addValue("Radius step", stepRadius);
		if ((prefs & Options.SAMPLES_PER_RADIUS) != 0)
			rt.addValue("Samples/radius", (isCSV || is3D) ? 1 : nSpans);
		if ((prefs & Options.ENCLOSING_RADIUS) != 0)
			rt.addValue("Enclosing radius cutoff", enclosingCutOff);
		rt.addValue("I branches (user)", (inferPrimary) ? Double.NaN : primaryBranches);
		rt.addValue("I branches (inferred)", (inferPrimary) ? y[0] : Double.NaN);
		if ((prefs & Options.INTERSECTING_RADII) != 0)
			rt.addValue("Intersecting radii", size);
		if ((prefs & Options.SUM_INTERS) != 0)
			rt.addValue("Sum inters.", sumY);

		// Calculate skewness and kurtosis of sampled data (linear Sholl);
		final double[] moments = getMoments(y);
		if ((prefs & Options.MEAN_INTERS) != 0)
			rt.addValue("Mean inters.", moments[0]);
		if ((prefs & Options.MEDIAN_INTERS) != 0)
			rt.addValue("Median inters.", getMedian(y));
		if ((prefs & Options.SKEWNESS) != 0)
			rt.addValue("Skewness (sampled)", moments[2]);
		if ((prefs & Options.KURTOSIS) != 0)
			rt.addValue("Kurtosis (sampled)", moments[3]);
		rt.addValue("Max inters.", maxIntersect);
		rt.addValue("Max inters. radius", maxR);
		rt.addValue("Ramification index (sampled)", ri);

		// Calculate the 'center of mass' for the sampled curve (linear Sholl);
		if ((prefs & Options.CENTROID) != 0) {
			centroid = Sholl_Utils.baryCenter(x, y);
			rt.addValue("Centroid radius", centroid[0]);
			rt.addValue("Centroid value", centroid[1]);
		}
		if ((prefs & Options.ENCLOSING_RADIUS) != 0)
			rt.addValue("Enclosing radius", enclosingR);
		// rt.addValue("Enclosed field", field);
		rt.show(SHOLLTABLE);
		return rt;

	}

	/** Transforms data */
	private static double[][] transformValues(final double[][] values, final boolean normY, final boolean logY,
			final boolean logX) {

		double x, y;
		final double[][] transValues = new double[values.length][2];

		for (int i = 0; i < values.length; i++) {

			x = values[i][0];
			y = values[i][1];

			if (normY) {
				if (normChoice == AREA_NORM) {
					if (is3D)
						transValues[i][1] = y / (Math.PI * x * x * x * 4 / 3); // Volume
																				// of
																				// sphere
					else
						transValues[i][1] = y / (Math.PI * x * x); // Area of
																	// circle
				} else if (normChoice == PERIMETER_NORM) {
					if (is3D)
						transValues[i][1] = y / (Math.PI * x * x * 4); // Surface
																		// area
																		// of
																		// sphere
					else
						transValues[i][1] = y / (Math.PI * x * 2); // Length of
																	// circumference
				} else if (normChoice == ANNULUS_NORM) {
					final double r1 = x - stepRadius / 2;
					final double r2 = x + stepRadius / 2;
					if (is3D)
						transValues[i][1] = y / (Math.PI * 4 / 3 * (r2 * r2 * r2 - r1 * r1 * r1)); // Volume
																									// of
																									// spherical
																									// shell
					else
						transValues[i][1] = y / (Math.PI * (r2 * r2 - r1 * r1)); // Area
																					// of
																					// annulus
				}
			} else
				transValues[i][1] = y;

			if (logY)
				transValues[i][1] = Math.log(y);

			if (logX)
				transValues[i][0] = Math.log(x);
			else
				transValues[i][0] = x;
		}

		return transValues;

	}

	/** Returns a plot with some axes customizations */
	private static Plot plotValues(final String title, final String xLabel, final String yLabel, final double[][] xy) {

		// Extract values
		final int size = xy.length;
		final double[] x0 = new double[size];
		final double[] y0 = new double[size];
		for (int i = 0; i < size; i++) {
			x0[i] = xy[i][0];
			y0[i] = xy[i][1];
		}

		// Create an empty plot
		final double[] empty = null;
		final int flags = Plot.X_FORCE2GRID + Plot.X_TICKS + Plot.X_NUMBERS + Plot.Y_FORCE2GRID + Plot.Y_TICKS
				+ Plot.Y_NUMBERS;
		final Plot plot = new Plot(title, xLabel, yLabel, empty, empty, flags);

		// Set limits
		final double[] xScale = Tools.getMinMax(x0);
		final double[] yScale = Tools.getMinMax(y0);
		setPlotLimits(plot, xScale, yScale);

		// Add data (default color is black)
		plot.setColor(Color.GRAY);
		plot.addPoints(x0, y0, Plot.CROSS);

		return plot;

	}

	/** Sets plot limits imposing grid lines */
	private static void setPlotLimits(final Plot plot, final double[] xScale, final double[] yScale) {

		final boolean gridState = PlotWindow.noGridLines;
		PlotWindow.noGridLines = false;
		plot.setLimits(xScale[0], xScale[1], yScale[0], yScale[1]);
		PlotWindow.noGridLines = gridState;

	}

	/** Calls plotRegression for both regressions as specified in Options */
	private static void plotRegression(final double[][] values, final Plot plot, final ResultsTable rt,
			final String method) {

		final int size = values.length;
		final double[] x = new double[size];
		final double[] y = new double[size];
		for (int i = 0; i < size; i++) {
			x[i] = values[i][0];
			y[i] = values[i][1];
		}
		plotRegression(x, y, false, plot, rt, method);
		if ((prefs & Options.P1090_REGRESSION) != 0)
			plotRegression(x, y, true, plot, rt, method);

	}

	/**
	 * Performs linear regression using the full range data or percentiles 10-90
	 */
	private static void plotRegression(final double[] x, final double[] y, final boolean trim, final Plot plot,
			final ResultsTable rt, final String method) {

		final int size = x.length;
		int start = 0;
		int end = size - 1;

		String labelSufix = " (" + method + ")";

		final CurveFitter cf;
		if (trim) {

			labelSufix += " [P10-P90]"; // "[P\u2081\u2080 - P\u2089\u2080]";
			start = (int) (size * 0.10);
			end = end - start;

			// Do not proceed if there are not enough points
			if (end <= SMALLEST_DATASET)
				return;

			final double[] xtrimmed = Arrays.copyOfRange(x, start, end);
			final double[] ytrimmed = Arrays.copyOfRange(y, start, end);

			cf = new CurveFitter(xtrimmed, ytrimmed);

		} else {
			cf = new CurveFitter(x, y);
		}

		cf.doFit(CurveFitter.STRAIGHT_LINE, false);
		final double[] cfparam = cf.getParams();
		// IJ.log("Curve fitter status: " + cf.getStatusString());

		if (verbose) {
			IJ.log("\n*** " + imgTitle + ", regression details" + labelSufix + cf.getResultString());
		}

		final double k = -cfparam[1]; // slope
		final double kIntercept = cfparam[0]; // y-intercept
		final double kRSquared = cf.getRSquared(); // R^2

		rt.addValue("Regression coefficient" + labelSufix, k);
		rt.addValue("Regression intercept" + labelSufix, kIntercept);
		rt.addValue("Regression R^2" + labelSufix, kRSquared);
		rt.show(SHOLLTABLE);

		if (plot != null) {

			final double x1 = x[start];
			final double x2 = x[end];
			final double y1 = cf.f(cfparam, x1);
			final double y2 = cf.f(cfparam, x2);

			final Color color = (trim) ? Color.RED : Color.BLUE;
			plot.setLineWidth(2);
			plot.setColor(color);
			plot.drawLine(x1, y1, x2, y2);
			plot.setLineWidth(1);

			Sholl_Utils.markPlotPoint(plot, new double[] { 0, kIntercept }, color);
			if (plotLabels) {
				final StringBuffer label = new StringBuffer();
				label.append("R\u00B2= " + IJ.d2s(kRSquared, 3));
				label.append("\nk= " + IJ.d2s(k, -2));
				label.append("\nIntercept= " + IJ.d2s(kIntercept, 2));
				Sholl_Utils.makePlotLabel(plot, label.toString(), color);
			}

		}

	}

	/** Saves plot according to imgPath */
	private static void savePlot(final Plot plot, final int shollChoice) {

		if (!validPath || (validPath && !hideSaved))
			plot.show();
		if (validPath && save) {
			Recorder.disablePathRecording();
			final String path = imgPath + imgTitle + "_ShollPlot" + SHOLL_TYPES[shollChoice] + ".png";
			IJ.saveAs(plot.getImagePlus(), "png", path);
		}

	}

	/** Creates improved error messages with 'help' and 'action' buttons */
	private void error(final String boldMsg, final String plainMsg, final boolean extended) {

		if (IJ.macroRunning()) {
			final String msg = boldMsg + "\n" + plainMsg;
			IJ.error("Sholl Analysis v" + VERSION + " Error", msg);
		} else {
			final EnhancedGenericDialog gd = new EnhancedGenericDialog("Sholl Analysis v" + VERSION + " Error");
			if (boldMsg != null && !"".equals(boldMsg)) {
				gd.addMessage(boldMsg, new Font("SansSerif", Font.BOLD, 12));
			}
			if (plainMsg != null && !"".equals(plainMsg)) {
				gd.addMessage(plainMsg);
			}
			if (!isCSV) {
				gd.addHyperlinkMessage(
						"Alternatively, run \"Sholl Analysis (Tabular Data)...\" to:\n"
								+ "	 - Re-analyze data from previous runs\n"
								+ "	 - Analyze profiles from Simple Neurite Tracer",
						null, Color.DARK_GRAY, URL + "#Importing");
			}
			gd.hideCancelButton();
			if (extended) {
				gd.addHelp(isCSV ? URL + "#Importing" : URL);
				gd.setHelpLabel("Online Help");
				gd.enableYesNoCancel("OK", (isCSV) ? "Import Other Data" : "Analyze Sample Image");
			}
			gd.showDialog();
			if (extended && !gd.wasOKed() && !gd.wasCanceled())
				retrieveSampleData();
		}

	}

	/** Re-runs the plugin with sample data */
	private void retrieveSampleData() {
		if (isCSV) {
			if (Recorder.record) { // Ensure getTable() is not recorded multiple
									// times
				Recorder.setCommand(Recorder.getCommand());
				improveRecording();
			}
			this.run("csv");
		} else {
			this.run("sample");
		}
	}

	/** Simple error message */
	private void sError(final String msg) {
		error("", msg, false);
	}

	/** Extended error message */
	private void lError(final String mainMsg, final String extraMsg) {
		error(mainMsg, extraMsg, true);
	}

	/** Returns a filename that does not include extension */
	private String trimExtension(String filename) {
		final int index = filename.lastIndexOf(".");
		if (index > -1)
			filename = filename.substring(0, index);
		return filename;
	}

	/**
	 * Returns the mean, variance, skewness and kurtosis of an array of
	 * univariate data. Code from ij.process.ByteStatistics
	 */
	private final static double[] getMoments(final double values[]) {
		final int npoints = values.length;
		double v, v2, sum1 = 0.0, sum2 = 0.0, sum3 = 0.0, sum4 = 0.0;
		for (int i = 0; i < npoints; i++) {
			v = values[i];
			v2 = v * v;
			sum1 += v;
			sum2 += v2;
			sum3 += v * v2;
			sum4 += v2 * v2;
		}
		final double mean = sum1 / npoints;
		final double mean2 = mean * mean;
		final double variance = sum2 / npoints - mean2;
		final double std = Math.sqrt(variance);
		final double skewness = ((sum3 - 3.0 * mean * sum2) / npoints + 2.0 * mean * mean2) / (variance * std);
		final double kurtosis = (((sum4 - 4.0 * mean * sum3 + 6.0 * mean2 * sum2) / npoints - 3.0 * mean2 * mean2)
				/ (variance * variance) - 3.0);
		return new double[] { mean, variance, skewness, kurtosis };
	}

	/** Records IJ.setKeyDown(KeyEvent.VK_ALT); */
	private static final void improveRecording() {
		if (Recorder.record) {
			String recordString = "// Recording Sholl Analysis version " + VERSION + "\n" + "// Visit " + URL
					+ "#Batch_Processing for scripting tips\n";
			final String cmd = Recorder.getCommand();
			final String cmdOptions = Recorder.getCommandOptions();
			if (cmd == null || cmdOptions == null) {
				recordString += "// NB: Commands in the \"More\u00bb\" dropdown menu should be recorded from \"Analyze>Sholl>\"\n";
			}
			if (isCSV && (cmd != null && !cmd.toLowerCase().contains("tabular"))) {
				recordString += "// NB: The \"Alt\" key modifier is no longer required: Run \"Sholl Analysis (Tabular Data)...\" instead\n";
				if (Recorder.scriptMode()) { // JavaScript, BeanShell or Java as
												// of IJ.1.50
					// NB: using hex values seems simpler as it works with
					// JavaScript recording
					recordString += "IJ.setKeyDown(0x12); //IJ.setKeyDown(KeyEvent.VK_ALT);\n";
				} else { // IJ macro language
					recordString += "setKeyDown(\"alt\");\n";
				}
			}
			Recorder.recordString(recordString);
		}
	}

	/**
	 * Instructs {@link Sholl_Analysis} to exclude plots from output (only
	 * tables will be displayed).
	 *
	 * @param noPlots
	 *            If {@code true}, plugin will only output tables. If
	 *            {@code false}, both tables and plots will be produced (the
	 *            default)
	 */
	public static void setNoPlots(final boolean noPlots) {
		Sholl_Analysis.noPlots = noPlots;
	}

	/**
	 * Instructs {@link Sholl_Analysis} to exclude detailed table from output
	 * (Summary table is still displayed).
	 *
	 * @param noTable
	 *            If {@code true}, plugin will not output the "detailed table"
	 *            containing all the retrieved profiles. Note that the Summary
	 *            "Sholl Results" table is always displayed.
	 *
	 * @see #setNoPlots(boolean)
	 */
	public static void setNoTable(final boolean noTable) {
		Sholl_Analysis.noTable = noTable;
	}

	/**
	 * Instructs {@link Sholl_Analysis} to display fitting details in Sholl
	 * plots.
	 *
	 * @param plotLabels
	 *            If {@code true}, plotting labels will be added, otherwise they
	 *            will be omitted
	 */
	public static void setPlotLabels(final boolean plotLabels) {
		Sholl_Analysis.plotLabels = plotLabels;
	}

	/**
	 * Sets the precision used by {@link Sholl_Analysis} to calculate metrics
	 * from fitted data, such as Nav and Nm.
	 *
	 * @param precision
	 *            The precision value as a fraction of radius step size. Eg,
	 *            {@code 100} sets accuracy to radiusStepSize/100
	 */
	public static void setPrecision(final int precision) {
		Sholl_Analysis.fMetricsPrecision = precision;
	}

	/**
	 * <p>
	 * Alternative to {@link #setNoPlots(boolean) setNoPlots()} to be called by
	 * IJ macros using the
	 * <a href="http://imagej.nih.gov/ij/developer/macro/functions.html#call">
	 * call()</a> built-in macro function
	 * </p>
	 *
	 * <p>
	 * Instructs {@link Sholl_Analysis} to exclude plots from output (only
	 * tables will be displayed). An error message is displayed in the IJ Log
	 * window if {@code booleanString} can not be parsed. Usage example:
	 * {@code call("sholl.Sholl_Analysis.setNoPlots", "false");}
	 * </p>
	 *
	 * @param booleanString
	 *            If {@code "true"}, plugin will only output tables. If
	 *            {@code "false"}, both tables and plots will be produced (the
	 *            default)
	 */
	public static void setNoPlots(final String booleanString) {
		if (validateBooleanString(booleanString))
			Sholl_Analysis.noPlots = Boolean.valueOf(booleanString);
	}

	/**
	 * <p>
	 * Alternative to {@link #setNoTable(boolean) setNoTable()} to be called by
	 * IJ macros using the
	 * <a href="http://imagej.nih.gov/ij/developer/macro/functions.html#call">
	 * call()</a> built-in macro function
	 * </p>
	 *
	 * <p>
	 * Instructs {@link Sholl_Analysis} to exclude detailed table from output.
	 * An error message is displayed in the IJ Log window if
	 * {@code booleanString} can not be parsed. Usage example:
	 * {@code call("sholl.Sholl_Analysis.setNoTable", "false");}
	 * </p>
	 *
	 * @param booleanString
	 *            If {@code "true"}, plugin will not output the "detailed table"
	 *            containing all the retrieved profiles. Note that the Summary
	 *            "Sholl Results" table is always displayed.
	 */
	public static void setNoTable(final String booleanString) {
		if (validateBooleanString(booleanString))
			Sholl_Analysis.noTable = Boolean.valueOf(booleanString);
	}

	/**
	 * <p>
	 * Alternative to {@link #setPlotLabels(boolean) setPlotLabels()} to be
	 * called by IJ macros using the
	 * <a href="http://imagej.nih.gov/ij/developer/macro/functions.html#call">
	 * call()</a> built-in macro function
	 * </p>
	 *
	 * <p>
	 * Instructs {@link Sholl_Analysis} to display fitting details in Sholl
	 * plots. An error message is displayed in the IJ Log window if
	 * {@code booleanString} can not be parsed. Usage example:
	 * {@code call("sholl.Sholl_Analysis.setPlotLabels", "false");}
	 * </p>
	 *
	 * @param booleanString
	 *            If {@code "true"}, plotting labels will be added.
	 */
	public static void setPlotLabels(final String booleanString) {
		if (validateBooleanString(booleanString))
			Sholl_Analysis.plotLabels = Boolean.valueOf(booleanString);
	}

	/**
	 * <p>
	 * Alternative to {@link #setPrecision(int) setPrecision()} to be called by
	 * IJ macros using the
	 * <a href="http://imagej.nih.gov/ij/developer/macro/functions.html#call">
	 * call()</a> built-in macro function
	 * </p>
	 *
	 * <p>
	 * Sets the precision used by {@link Sholl_Analysis} to calculate metrics
	 * from fitted data, such as Nav and Nm. An error message is displayed in
	 * the IJ Log window if {@code intString} is invalid. Usage example:
	 * {@code call("sholl.Sholl_Analysis.setPrecision", "1000");}
	 * </p>
	 *
	 * @param intString
	 *            The string integer to set the precision in terms of radius
	 *            step size. Eg, {@code "100"} sets accuracy to
	 *            radiusStepSize/100
	 */
	public static void setPrecision(final String intString) {
		if (validateIntString(intString))
			Sholl_Analysis.fMetricsPrecision = Integer.parseInt(intString);
	}

	private static boolean validateBooleanString(final String string) {
		final boolean valid = string != null && (string.equalsIgnoreCase("true") || string.equalsIgnoreCase("false"));
		if (!valid)
			IJ.log(">>> Sholl Utils: Not a valid option: '" + string + "'");
		return valid;
	}

	private static boolean validateIntString(final String string) {
		boolean valid = true;
		try {
			Integer.parseInt(string);
		} catch (final NumberFormatException e) {
			valid = false;
			IJ.log(">>> Sholl Utils: Not a valid option: '" + string + "'");
		}
		return valid;
	}

	/**
	 * Prompts the user for tabular data, retrieved from several sources
	 * including 1) Importing a new text/csv file; 2) Trying to import data from
	 * the system clipboard; 3) Importing a demo dataset populated by random
	 * (Gaussian) values; or 4) any other {@link ij.measure.ResultsTable}
	 * currently opened by ImageJ.
	 *
	 * @return A populated Results table or {@code null} if chosen source did
	 *         not contain valid data.
	 */
	private ResultsTable getTable() {

		ResultsTable rt = null;
		final ArrayList<ResultsTable> tables = new ArrayList<ResultsTable>();
		final ArrayList<String> tableTitles = new ArrayList<String>();

		final Frame[] windows = WindowManager.getNonImageWindows();
		TextWindow rtWindow;
		for (final Frame w : windows) {
			if (w instanceof TextWindow) {
				rtWindow = (TextWindow) w;
				rt = ((TextWindow) w).getTextPanel().getResultsTable();
				if (rt != null) {
					tables.add(rt);
					tableTitles.add(rtWindow.getTitle());
				}
			}
		}

		final boolean noTablesOpened = tableTitles.isEmpty();

		// Append options for external sources
		tableTitles.add("External file...");
		tableTitles.add("Clipboard");

		// Build prompt
		final GenericDialog gd = new GenericDialog("Choose Sampled Profile");
		final int cols = (tableTitles.size() < 18) ? 1 : 2;
		final int rows = (tableTitles.size() % cols > 0) ? tableTitles.size() / cols + 1 : tableTitles.size() / cols;
		gd.addRadioButtonGroup("Use tabular data of sampled profiles from:",
				tableTitles.toArray(new String[tableTitles.size()]), rows, cols, tableTitles.get(0));
		// gd.hideCancelButton();
		gd.showDialog();

		if (gd.wasCanceled()) {
			return null;

		} else if (gd.wasOKed()) {

			imgPath = null; // Path of dataset
			validPath = false; // Path of dataset is not accessible
			imgTitle = "Imported data"; // Dataset name
			final String choice = gd.getNextRadioButton();

			if (choice.equals("External file...")) {

				try {
					rt = ResultsTable.open("");
					if (rt != null && validTable(rt)) {
						validPath = true;
						imgPath = OpenDialog.getLastDirectory();
						imgTitle = WindowManager.makeUniqueName(OpenDialog.getLastName());
						if (!IJ.macroRunning()) // no need to display table
							rt.show(imgTitle);
					}
				} catch (final IOException e) {
					lError("", e.getMessage());
				}

			} else if (choice.equals("Clipboard")) {

				final String clipboard = Sholl_Utils.getClipboardText();
				final String error = "Clipboard does not seem to contain valid data";
				if (clipboard.isEmpty()) {
					rt = null;
					lError("", error);
				} else {
					try {
						final File temp = File.createTempFile("IJclipboard", ".txt");
						temp.deleteOnExit();
						final PrintStream out = new PrintStream(temp.getAbsolutePath());
						out.println(clipboard);
						out.close();
						rt = ResultsTable.open(temp.getAbsolutePath());
						if (validTable(rt)) {
							imgTitle = WindowManager.makeUniqueName("Clipboard Data");
							if (!IJ.macroRunning()) // no need to display table
								rt.show(imgTitle);
						} else {
							lError("", error);
							return null;
						}
					} catch (final IOException ignored) {
						rt = null;
						lError("", "Could not extract tabular data from clipboard.");
					}
				}
			} else if (!noTablesOpened) {

				rt = tables.get(tableTitles.indexOf(choice));
				if (rt == null)
					lError("", imgTitle + " is no longer available.");
				else if (validTable(rt))
					imgTitle = choice;

			}

		}

		return rt;

	}

	private static int getThreadedCounter() {
		return progressCounter;
	}

	private static void setThreadedCounter(final int updatedCounter) {
		progressCounter = updatedCounter;
	}

	/**
	 * Instructs the plugin to parse the specified table expected to contain a
	 * sampled profile. Analysis is not headless (user is prompted for input
	 * options. Does nothing if the specified table is {@code null} or if does
	 * not contain the specified column indices.
	 *
	 * @param rt
	 *            the input {@link ResultsTable}
	 * @param rCol
	 *            the index of the radii column
	 * @param cCol
	 *            the index of the intersections count column
	 * @param threeD
	 *            3D analysis?
	 *
	 * @see #validTable(ResultsTable)
	 */
	public void analyzeTabularInput(final ResultsTable rt, final int rCol, final int cCol, final boolean threeD) {
		if (rt != null && rt.columnExists(rCol) && rt.columnExists(cCol)) {
			isCSV = true;
			setIsTableRequired(false);
			csvRT = rt;
			rColumn = rCol;
			cColumn = cCol;
			is3D = threeD;
			limitCSV = false;
			validPath = false;
			imgPath = null;
			run("csv");
			csvRT = null;
		}
		setIsTableRequired(true);
	}

	/**
	 * Instructs the plugin to parse the specified file expected to contain a
	 * sampled profile. Analysis is not headless (user is prompted for input
	 * options and will be warned if file does not contain valid data).
	 *
	 * @param csvFile
	 *            the input file expected to contain tabular data in any of the
	 *            formats recognized by {@link ij.measure.ResultsTable}
	 * @param rCol
	 *            the index of the radii column
	 * @param cCol
	 *            the index of the intersections count column
	 * @param threeD
	 *            3D analysis?
	 * @throws IOException
	 *             if file could be opened.
	 */
	public void analyzeTabularInput(final File csvFile, final int rCol, final int cCol, final boolean threeD)
			throws IOException {
		csvRT = ResultsTable.open(csvFile.getAbsolutePath());
		if (csvRT != null && csvRT.getCounter() > 2 && csvRT.columnExists(rCol) && csvRT.columnExists(cCol)) {
			isCSV = true;
			setIsTableRequired(false);
			setExportPath(csvFile.getParent());
			setDescription(trimExtension(csvFile.getName()));
			rColumn = rCol;
			cColumn = cCol;
			is3D = threeD;
			run("csv");
			csvRT = null;
		} else {
			lError("Profile could not be parsed or it does not contain enough data points.",
					"N.B.: At least " + (SMALLEST_DATASET + 1) + " pairs of values are required for curve fitting.");
		}
		setIsTableRequired(true);
	}

	/**
	 * Analyzes a sampled profile. Analysis is not headless (user is prompted
	 * for analysis options and will be warned if file does not contain valid
	 * data).
	 *
	 * @param distances
	 *            the array containing radii
	 * @param inters
	 *            the array containing intersection counts
	 * @param threeD
	 *            3D profile?
	 */
	public void analyzeProfile(final double[] distances, final double[] inters, final boolean threeD) {
		if (distances != null && inters != null) {
			setIsTableRequired(false);
			radii = distances;
			counts = inters;
			isCSV = true;
			csvRT = null;
			is3D = threeD;
			run("csv");
		} else {
			lError("Profile could not be parsed or it does not contain enough data points.",
					"N.B.: At least " + (SMALLEST_DATASET + 1) + " pairs of values are required for curve fitting.");
		}
		setIsTableRequired(true);
	}

	/**
	 * @return {@code true} Can analysis of tabular data proceed without an
	 *         input table?
	 */
	private boolean isTableRequired() {
		return this.tableRequired;
	}

	/**
	 * @param required
	 *            if {@code false}, it is assumed that input data has been
	 *            specified through API calls, and tabular data will proceed
	 *            without prompts for input options
	 */
	private void setIsTableRequired(final boolean required) {
		this.tableRequired = required;
		if (!required) {
			this.limitCSV = false;
		}
	}

	/**
	 * @param nBranches
	 *            the number of primary branches to be used in the calculation
	 *            of ramification indices
	 */
	public void setPrimaryBranches(final double nBranches) {
		primaryBranches = nBranches;
	}

	/**
	 * @param exportDir
	 *            The path to the directory where results should be saved.
	 *            {@code null} allowed.
	 */
	public void setExportPath(String exportDir) {
		if (exportDir != null && !exportDir.isEmpty() && !exportDir.endsWith(File.separator))
			exportDir += File.separator;
		final File dir = new File(exportDir);
		validPath = dir.exists() && dir.isDirectory();
		imgPath = exportDir;
	}

	/**
	 * @param verbose
	 *            Whether details on curve fitting should be displayed
	 */
	public void setVerbose(final boolean verbose) {
		Sholl_Analysis.verbose = verbose;
	}

	/**
	 * @param label
	 *            the label describing the analysis. It is used in the titles of
	 *            frames and images when displaying results
	 */
	public void setDescription(final String label) {
		imgTitle = label;
	}

	/**
	 * @param label
	 *            the label describing the analysis. It is used in the titles of
	 *            frames and images when displaying results
	 * @param makeUnique
	 *            if {@code true} and ImageJ is already displaying a window
	 *            under the same label, a suffix ("-1", "-2", etc.) is appended
	 *            to label to ensure it is unique
	 */
	public void setDescription(String label, final boolean makeUnique) {
		if (makeUnique)
			label = WindowManager.makeUniqueName(label);
		setDescription(label);
	}

}
