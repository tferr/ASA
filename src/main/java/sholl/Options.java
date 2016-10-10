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

/* Copyright 2010-2016 Tiago Ferreira
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
	static final String COMMAND_LABEL = "Metrics & Options...";

	/** Argument for {@link #run(String)} **/
	static final String SKIP_BITMAP_OPTIONS_LABEL = "skip-bitmap";
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

	private final static String METRICS_KEY = "sholl.metrics";

	/* Sholl mask */
	static final int SAMPLED_MASK = 0;
	static final int FITTED_MASK = 1;
	private static final String[] MASK_TYPES = new String[] { "Sampled values", "Fitted values" };
	private final static String MASK_KEY = "sholl.mask";
	private final static int DEFAULT_MASK_BACKGROUND = 228;
	private final static int DEFAULT_MASK_TYPE = SAMPLED_MASK;

	private final static int UNSET_PREFS = -1;
	private static int currentMetrics = UNSET_PREFS;
	private static int maskBackground = UNSET_PREFS;
	private static int maskType = UNSET_PREFS;
	private static String commentString = null;

	private static boolean skipBitmapOptions;

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
		final String[] labels = new String[16];
		final int[] items = new int[16];
		final boolean[] states = new boolean[16];

		items[0] = DIRECTORY;
		labels[0] = "Image directory";
		states[0] = (currentMetrics & DIRECTORY) != 0;

		items[1] = UNIT;
		labels[1] = "Spatial unit";
		states[1] = (currentMetrics & UNIT) != 0;

		items[2] = THRESHOLD;
		labels[2] = "Threshold levels";
		states[2] = (currentMetrics & THRESHOLD) != 0;

		items[3] = CENTER;
		labels[3] = "Center of analysis";
		states[3] = (currentMetrics & CENTER) != 0;

		items[4] = STARTING_RADIUS;
		labels[4] = "Starting radius";
		states[4] = (currentMetrics & STARTING_RADIUS) != 0;

		items[5] = RADIUS_STEP;
		labels[5] = "Radius step size";
		states[5] = (currentMetrics & RADIUS_STEP) != 0;

		items[6] = SAMPLES_PER_RADIUS;
		labels[6] = "Samples per radius";
		states[6] = (currentMetrics & SAMPLES_PER_RADIUS) != 0;

		items[7] = ENCLOSING_RADIUS;
		labels[7] = "Enclosing radius";
		states[7] = (currentMetrics & ENCLOSING_RADIUS) != 0;

		items[8] = INTERSECTING_RADII;
		labels[8] = "Intersecting radii";
		states[8] = (currentMetrics & INTERSECTING_RADII) != 0;

		items[9] = SUM_INTERS;
		labels[9] = "Sum of intersections";
		states[9] = (currentMetrics & SUM_INTERS) != 0;

		items[10] = MEAN_INTERS;
		labels[10] = "Mean n. of intersections";
		states[10] = (currentMetrics & MEAN_INTERS) != 0;

		items[11] = MEDIAN_INTERS;
		labels[11] = "Median n. of intersections";
		states[11] = (currentMetrics & MEDIAN_INTERS) != 0;

		items[12] = SKEWNESS;
		labels[12] = "Skewness";
		states[12] = (currentMetrics & SKEWNESS) != 0;

		items[13] = KURTOSIS;
		labels[13] = "Kurtosis";
		states[13] = (currentMetrics & KURTOSIS) != 0;

		items[14] = CENTROID;
		labels[14] = "Centroid";
		states[14] = (currentMetrics & CENTROID) != 0;

		items[15] = P1090_REGRESSION;
		labels[15] = "P10-P90 regression";
		states[15] = (currentMetrics & P1090_REGRESSION) != 0;

		final EnhancedGenericDialog gd = new EnhancedGenericDialog("Sholl Metrics and Options");
		final Font font = new Font("SansSerif", Font.BOLD, 12);

		// Metrics (columns of Sholl Results table)
		gd.setInsets(0, 0, 0);
		gd.addMessage("Include in Sholl Results:", font);
		gd.setInsets(0, 0, 0);
		gd.addCheckboxGroup(8, 2, labels, states);
		gd.addStringField("Append comment:", getCommentString(), 20);

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

			// Sholl prefs
			boolean b = false;
			for (int i = 0; i < labels.length; i++) {
				b = gd.getNextBoolean();
				if (b)
					currentMetrics |= items[i];
				else
					currentMetrics &= ~items[i];
			}
			Prefs.set(METRICS_KEY, currentMetrics);
			setCommentString(gd.getNextString());
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
				Recorder.recordOption("reset");
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
}
