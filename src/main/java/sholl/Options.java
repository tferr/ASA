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

/* Copyright 2016 Tiago Ferreira
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import fiji.Debug;
import ij.IJ;
import ij.Prefs;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import sholl.gui.EnhancedGenericDialog;
import sholl.gui.Utils;

/**
 * This class implements the "Sholl Options and Metrics" command.
 *
 * @author Tiago Ferreira
 */
public class Options implements PlugIn {

	/** The Menu entry of this plugin as specified in plugins.config **/
	public static final String COMMAND_LABEL = "Metrics & Options...";

	/** Argument for {@link #run(String)} **/
	public static final String SKIP_BITMAP_OPTIONS_LABEL = "skip-bitmap";
	/** Argument for {@link #run(String)} **/
	static final String RESET_OPTIONS_LABEL = "reset";

	/* Columns in "Sholl Results" table */
	static final int DIRECTORY = 1;
	static final int UNIT = 2;
	static final int THRESHOLD = 4;
	static final int CENTER = 8;
	static final int STARTING_RADIUS = 16;
	static final int ENDING_RADIUS = 32;
	static final int RADIUS_STEP = 64;
	static final int SAMPLES_PER_RADIUS = 128;
	static final int ENCLOSING_RADIUS = 256;
	static final int INTERSECTING_RADII = 512;
	static final int SUM_INTERS = 1024;
	static final int MEAN_INTERS = 2048;
	static final int MEDIAN_INTERS = 4096;
	static final int SKEWNESS = 8192;
	static final int KURTOSIS = 16384;
	static final int CENTROID = 0x8000;
	static final int P1090_REGRESSION = 0x10000;
	static final int NO_TABLE = 0x40000;

	private final static String METRICS_KEY = "sholl.metrics";

	/* Sholl mask */
	static final int SAMPLED_MASK = 0;
	static final int FITTED_MASK = 1;
	private static final String[] MASK_TYPES = new String[] { "Sampled values", "Fitted values" };
	private final static String MASK_KEY = "sholl.mask";
	private final static int DEFAULT_MASK_BACKGROUND = 228;
	private final static int DEFAULT_MASK_TYPE = SAMPLED_MASK;

	/* Sholl plots */
	static final int ALL_PLOTS = 0;
	static final int ONLY_LINEAR_PLOT = 1;
	static final int NO_PLOTS = 2;
	private static final String[] PLOT_OUTPUTS = new String[] { "All chosen methods", "Only linear profile",
			"No plots" };
	private final static String PLOT_OUTPUT_KEY = "sholl.plots";
	private final static int DEFAULT_PLOT_OUTPUT = ALL_PLOTS;

	private final static int UNSET_PREFS = -1;
	private static int currentMetrics = UNSET_PREFS;
	private static int maskBackground = UNSET_PREFS;
	private static int maskType = UNSET_PREFS;
	private static int plotOutputType = UNSET_PREFS;
	private static String commentString = null;

	private boolean skipBitmapOptions;

	/**
	 * Debug helper
	 *
	 * @param args
	 *            See {@link fiji.Debug#run(java.lang.String, java.lang.String)}
	 */
	public static void main(final String[] args) {
		Debug.run("Metrics & Options...", "");
	}

	/**
	 * This method is called when the plugin is loaded. {@code arg} can be
	 * specified in {@code plugins.config}.
	 *
	 * @param arg
	 *            If {@link #RESET_OPTIONS_LABEL} options and preferences are
	 *            reset to defaults. If {@link #SKIP_BITMAP_OPTIONS_LABEL}
	 *            options specific to bitmap analysis are not displayed in GUI
	 *            when displaying prompt.
	 */
	@Override
	public void run(final String arg) {
		if (arg.equals(RESET_OPTIONS_LABEL)) {
			resetOptions();
			return;
		}
		skipBitmapOptions = arg.equals(SKIP_BITMAP_OPTIONS_LABEL);
		promptForOptions();
	}

	private static int getDefaultMetrics() {
		return UNIT + THRESHOLD + CENTER + STARTING_RADIUS + ENDING_RADIUS + RADIUS_STEP + SAMPLES_PER_RADIUS
				+ ENCLOSING_RADIUS + INTERSECTING_RADII + SUM_INTERS + MEAN_INTERS + MEDIAN_INTERS + SKEWNESS + KURTOSIS
				+ CENTROID + P1090_REGRESSION;
	}

	static int getMetrics() {
		if (currentMetrics == UNSET_PREFS)
			currentMetrics = Prefs.getInt(METRICS_KEY, getDefaultMetrics());
		return currentMetrics;
	}

	/**
	 * Returns the background color of the Sholl mask image
	 *
	 * @return the gray value (8-bit scale) of the first entry of the LUT of the
	 *         Sholl mask's LUT image
	 * @see Sholl_Utils#matlabJetColorMap(int)
	 */
	static int getMaskBackground() {
		if (maskBackground == UNSET_PREFS)
			maskBackground = Prefs.getInt(MASK_KEY, DEFAULT_MASK_BACKGROUND);
		return maskBackground;
	}

	/**
	 * Sets the background color of the Sholl mask
	 *
	 * @param grayLevel
	 *            the gray value (8-bit scale) to be used as the first entry of
	 *            the LUT of the Sholl mask image
	 * @see Sholl_Utils#matlabJetColorMap(int)
	 */
	private void setMaskBackground(final int grayLevel) {
		Prefs.set(MASK_KEY, grayLevel);
		maskBackground = grayLevel;
	}

	/**
	 * Returns the type of data to be used in Sholl mask.
	 *
	 * @return the type of mask. Either Options.SAMPLED_MASK or
	 *         Options.FITTED_MASK.
	 */
	static int getMaskType() {
		if (maskType == UNSET_PREFS)
			maskType = Prefs.getInt(MASK_KEY + ".type", DEFAULT_MASK_TYPE);
		return maskType;
	}

	/**
	 * Sets the flag for output plots.
	 *
	 * @param output
	 *            the output flag. Either Options.ALL_PLOTS,
	 *            Options.ONLY_LINEAR_PLOT or Options.NO_PLOTS
	 */
	private void setPlotOutput(final int output) {
		Prefs.set(PLOT_OUTPUT_KEY + ".out", output);
		plotOutputType = output;
	}

	/**
	 * Returns the flag for outputPlots.
	 *
	 * @return the output flag. Either Options.ALL_PLOTS,
	 *         Options.ONLY_LINEAR_PLOT or Options.NO_PLOTS
	 */
	static int getPlotOutput() {
		if (plotOutputType == UNSET_PREFS)
			plotOutputType = Prefs.getInt(PLOT_OUTPUT_KEY + ".out", DEFAULT_PLOT_OUTPUT);
		return plotOutputType;
	}

	/**
	 * Sets the type of data to be used in Sholl mask.
	 *
	 * @param type
	 *            the type of mask. Either Options.SAMPLED_MASK or
	 *            Options.FITTED_MASK.
	 */
	private void setMaskType(final int type) {
		Prefs.set(MASK_KEY + ".type", type);
		maskType = type;
	}

	static String getCommentString() {
		if (commentString == null)
			commentString = Prefs.getString(METRICS_KEY + ".comment", null);
		return commentString;
	}

	private void setCommentString(String comment) {
		if (comment.trim().isEmpty())
			comment = null;
		Prefs.set(METRICS_KEY + ".comment", comment);
		commentString = comment;
	}

	private void resetOptions() {
		// Reset Sholl metrics
		Prefs.set(METRICS_KEY, null);
		Prefs.set(METRICS_KEY + ".comment", null);
		Prefs.set(MASK_KEY, null);
		Prefs.set(MASK_KEY + ".type", null);
		currentMetrics = UNSET_PREFS;
		commentString = null;
		maskBackground = UNSET_PREFS;
		maskType = UNSET_PREFS;
		// Reset Analyzer prefs
		Analyzer.setPrecision(3);
		Analyzer.setMeasurement(Measurements.SCIENTIFIC_NOTATION, false);
		// Reset other global IJ prefs
		Prefs.setThreads(Runtime.getRuntime().availableProcessors());
		Prefs.set("options.ext", null);
	}

	private void promptForOptions() {

		currentMetrics = getMetrics();
		final int bitmapOptions = 2;
		int nOptions = 16;
		if (skipBitmapOptions)
			nOptions -= bitmapOptions;
		final String[] labels = new String[nOptions];
		final int[] items = new int[nOptions];
		final boolean[] states = new boolean[nOptions];
		int idx = 0;

		items[idx] = DIRECTORY;
		labels[idx] = "Image / Input directory";
		states[idx++] = (currentMetrics & DIRECTORY) != 0;

		items[idx] = UNIT;
		labels[idx] = "Spatial unit";
		states[idx++] = (currentMetrics & UNIT) != 0;

		if (!skipBitmapOptions) {
			items[idx] = THRESHOLD;
			labels[idx] = "Threshold levels";
			states[idx++] = (currentMetrics & THRESHOLD) != 0;
		}

		items[idx] = CENTER;
		labels[idx] = "Center of analysis";
		states[idx++] = (currentMetrics & CENTER) != 0;

		items[idx] = STARTING_RADIUS;
		labels[idx] = "Starting radius";
		states[idx++] = (currentMetrics & STARTING_RADIUS) != 0;

		items[idx] = RADIUS_STEP;
		labels[idx] = "Radius step size";
		states[idx++] = (currentMetrics & RADIUS_STEP) != 0;

		if (!skipBitmapOptions) {
			items[idx] = SAMPLES_PER_RADIUS;
			labels[idx] = "Samples per radius";
			states[idx++] = (currentMetrics & SAMPLES_PER_RADIUS) != 0;
		}

		items[idx] = ENCLOSING_RADIUS;
		labels[idx] = "Enclosing radius";
		states[idx++] = (currentMetrics & ENCLOSING_RADIUS) != 0;

		items[idx] = INTERSECTING_RADII;
		labels[idx] = "Intersecting radii";
		states[idx++] = (currentMetrics & INTERSECTING_RADII) != 0;

		items[idx] = SUM_INTERS;
		labels[idx] = "Sum of intersections";
		states[idx++] = (currentMetrics & SUM_INTERS) != 0;

		items[idx] = MEAN_INTERS;
		labels[idx] = "Mean n. of intersections";
		states[idx++] = (currentMetrics & MEAN_INTERS) != 0;

		items[idx] = MEDIAN_INTERS;
		labels[idx] = "Median n. of intersections";
		states[idx++] = (currentMetrics & MEDIAN_INTERS) != 0;

		items[idx] = SKEWNESS;
		labels[idx] = "Skewness";
		states[idx++] = (currentMetrics & SKEWNESS) != 0;

		items[idx] = KURTOSIS;
		labels[idx] = "Kurtosis";
		states[idx++] = (currentMetrics & KURTOSIS) != 0;

		items[idx] = CENTROID;
		labels[idx] = "Centroid";
		states[idx++] = (currentMetrics & CENTROID) != 0;

		items[idx] = P1090_REGRESSION;
		labels[idx] = "P10-P90 regression";
		states[idx++] = (currentMetrics & P1090_REGRESSION) != 0;

		// Output options
		final int outputOptions = 1;
		final String[] outputLabels = new String[outputOptions];
		final int[] outputItems = new int[outputOptions];
		final boolean[] outputStates = new boolean[outputOptions];
		idx = 0;

		outputItems[idx] = NO_TABLE;
		outputLabels[idx] = "Do_not_generate_detailed_table";
		outputStates[idx++] = (currentMetrics & NO_TABLE) != 0;

		final EnhancedGenericDialog gd = new EnhancedGenericDialog("Sholl Metrics and Options");
		final Font font = new Font("SansSerif", Font.BOLD, 12);

		// Metrics (columns of Sholl Results table)
		gd.setInsets(0, 0, 0);
		gd.addMessage("Sholl Results Table:", font);
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(nOptions / 2, 2, labels, states);
		gd.addStringField("Append comment:", getCommentString(), 20);

		// Output files
		gd.setInsets(15, 0, 0);
		gd.addMessage("Output Files:", font);
		gd.addCheckboxGroup(outputOptions, 1, outputLabels, outputStates);
		gd.addChoice("Plots to be generated:", PLOT_OUTPUTS, PLOT_OUTPUTS[getPlotOutput()]);

		// Intersections mask
		if (!skipBitmapOptions) {
			gd.setInsets(15, 0, 0);
			gd.addMessage("Intersections mask:", font);
			gd.addSlider("  Background (grayscale):", 0, 255, getMaskBackground());
			gd.addChoice("Preferred data:", MASK_TYPES, MASK_TYPES[getMaskType()]);
		}

		// Include IJ preferences for convenience
		gd.setInsets(15, 0, 2);
		gd.addMessage("Global preferences (shared by all ImageJ commands):", font);
		gd.addNumericField("Parallel threads (3D images):", Prefs.getThreads(), 0, 4, "");
		gd.addStringField("File extension for tables:", Prefs.defaultResultsExtension(), 4);
		gd.setInsets(0, 0, 0);
		gd.addNumericField("Decimal places (0-9):", Analyzer.getPrecision(), 0, 4, "");
		gd.setInsets(0, 70, 0);
		gd.addCheckbox("Scientific notation", (Analyzer.getMeasurements() & Measurements.SCIENTIFIC_NOTATION) != 0);
		gd.assignPopupToHelpButton(createOptionsMenu());
		gd.enableYesNoCancel("OK", "Revert to Defaults");
		gd.showDialog();

		if (gd.wasCanceled()) {
			return;
		} else if (gd.wasOKed()) {

			// Sholl Table prefs
			for (int i = 0; i < nOptions; i++) {
				if (gd.getNextBoolean())
					currentMetrics |= items[i];
				else
					currentMetrics &= ~items[i];
			}
			setCommentString(gd.getNextString());

			// Output prefs
			for (int i = 0; i < outputOptions; i++) {
				if (gd.getNextBoolean())
					currentMetrics |= outputItems[i];
				else
					currentMetrics &= ~outputItems[i];
			}
			Prefs.set(METRICS_KEY, currentMetrics);

			// Output prefs II: plot options
			setPlotOutput(gd.getNextChoiceIndex());

			if (!skipBitmapOptions) {
				setMaskBackground(Math.min(Math.max((int) gd.getNextNumber(), 0), 255));
				setMaskType(gd.getNextChoiceIndex());
			}

			// IJ prefs
			Prefs.setThreads((int) gd.getNextNumber());
			String extension = gd.getNextString();
			if (!extension.startsWith("."))
				extension = "." + extension;
			Prefs.set("options.ext", extension);
			Analyzer.setPrecision(Math.min(Math.max((int) gd.getNextNumber(), 0), 9));
			Analyzer.setMeasurement(Measurements.SCIENTIFIC_NOTATION, gd.getNextBoolean());

		} else {
			if (Recorder.record)
				Recorder.recordOption(RESET_OPTIONS_LABEL);
			resetOptions();
		}
	}

	/** Creates optionsMenu */
	private JPopupMenu createOptionsMenu() {
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem mi;
		mi = new JMenuItem("Plot Options...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.doCommand("Plots...");
			}
		});
		popup.add(mi);
		mi = new JMenuItem("Input/Output Options...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.doCommand("Input/Output...");
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = Utils.menuItemTrigerringURL("Help on Sholl metrics", Sholl_Analysis.URL + "#Metrics");
		popup.add(mi);
		return popup;
	}

	/** Retrieves precision according to Analyze>Set Measurements... */
	static int getScientificNotationAwarePrecision() {
		final boolean sNotation = (Analyzer.getMeasurements() & Measurements.SCIENTIFIC_NOTATION) != 0;
		int precision = Analyzer.getPrecision();
		if (sNotation)
			precision = -precision;
		return precision;
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
		currentMetrics = getMetrics();
		if (noPlots)
			currentMetrics |= NO_PLOTS;
		else
			currentMetrics &= ~NO_PLOTS;
		Prefs.set(METRICS_KEY, currentMetrics);
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
		currentMetrics = getMetrics();
		if (noTable)
			currentMetrics |= NO_TABLE;
		else
			currentMetrics &= ~NO_TABLE;
		Prefs.set(METRICS_KEY, currentMetrics);
	}

}
