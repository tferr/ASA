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
import java.awt.event.WindowEvent;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import fiji.Debug;
import ij.IJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import sholl.gui.EnhancedGenericDialog;

public class Options implements PlugIn {

	/* Columns in "Sholl Results" table */
	public static final int DIRECTORY = 1;
	public static final int UNIT = 2;
	public static final int THRESHOLD = 4;
	public static final int CENTER = 8;
	public static final int STARTING_RADIUS = 16;
	public static final int ENDING_RADIUS = 32;
	public static final int RADIUS_STEP = 64;
	public static final int SAMPLES_PER_RADIUS = 128;
	public static final int ENCLOSING_RADIUS = 256;
	public static final int INTERSECTING_RADII = 512;
	public static final int SUM_INTERS = 1024;
	public static final int MEAN_INTERS = 2048;
	public static final int MEDIAN_INTERS = 4096;
	public static final int SKEWNESS = 8192;
	public static final int KURTOSIS = 16384;
	public static final int CENTROID = 0x8000;
	public static final int P1090_REGRESSION = 0x10000;

	final static String METRICS_KEY = "sholl.metrics";

	/* Sholl mask */
	final static int DEFAULT_MASK_BACKGROUND = 228;
	final static String MASK_KEY = "sholl.mask";

	final static int UNSET_PREFS = -1;
	static int currentMetrics = UNSET_PREFS;
	static int maskBackground = UNSET_PREFS;
	static String commentString = null;

	/**
	 * Debug helper
	 * 
	 * @param args
	 *            See {@link fiji.Debug#run(java.lang.String, java.lang.String)}
	 */
	public static void main(final String[] args) {
		Debug.run("Metrics & Options...", "");
	}

	/** See {@link ij.plugin.PlugIn#run(java.lang.String)} */
	@Override
	public void run(final String arg) {
		if (arg.equals("reset"))
			resetOptions();
		else
			promptForOptions();
	}

	static int getDefaultMetrics() {
		return UNIT + THRESHOLD + CENTER + STARTING_RADIUS + ENDING_RADIUS + RADIUS_STEP + SAMPLES_PER_RADIUS
				+ ENCLOSING_RADIUS + INTERSECTING_RADII + SUM_INTERS + MEAN_INTERS + MEDIAN_INTERS + SKEWNESS + KURTOSIS
				+ CENTROID + P1090_REGRESSION;
	}

	public static int getMetrics() {
		if (currentMetrics == UNSET_PREFS)
			currentMetrics = Prefs.getInt(METRICS_KEY, getDefaultMetrics());
		return currentMetrics;
	}

	public static int getMaskBackground() {
		if (maskBackground == UNSET_PREFS)
			maskBackground = Prefs.getInt(MASK_KEY, DEFAULT_MASK_BACKGROUND);
		return maskBackground;
	}

	void setMaskBackground(final int grayLevel) {
		Prefs.set(MASK_KEY, grayLevel);
		maskBackground = grayLevel;
	}

	public static String getCommentString() {
		if (commentString == null)
			commentString = Prefs.getString(METRICS_KEY + ".comment", null);
		return commentString;
	}

	void setCommentString(String comment) {
		if (comment.trim().isEmpty())
			comment = null;
		Prefs.set(METRICS_KEY + ".comment", comment);
		commentString = comment;
	}

	void resetOptions() {
		// Reset Sholl metrics
		Prefs.set(METRICS_KEY, null);
		Prefs.set(METRICS_KEY+ ".comment", null);
		Prefs.set(MASK_KEY, null);
		currentMetrics = UNSET_PREFS;
		commentString = null;
		maskBackground = UNSET_PREFS;
		// Reset Analyzer prefs
		Analyzer.setPrecision(3);
		Analyzer.setMeasurement(Measurements.SCIENTIFIC_NOTATION, false);
	}

	void promptForOptions() {

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
		labels[15] = "P10-P90 Regression";
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
		gd.setInsets(15, 0, 0);
		gd.addMessage("Intersections mask:", font);
		gd.addSlider("  Background (grayscale):", 0, 255, getMaskBackground());

		// Include IJ preferences for convenience
		gd.setInsets(15, 0, 2);
		gd.addMessage("System preferences (affect all ImageJ commands):", font);
		gd.addStringField("File extension for tables:", Prefs.defaultResultsExtension(), 4);
		gd.setInsets(0, 0, 0);
		gd.addNumericField("Decimal places (0-9):", Analyzer.getPrecision(), 0, 4, "");
		gd.setInsets(0, 70, 0);
		gd.addCheckbox("Scientific notation", (Analyzer.getMeasurements()&Measurements.SCIENTIFIC_NOTATION)!=0);
		gd.assignPopupToHelpButton("More \u00bb", createOptionsMenu(gd));
		gd.enableYesNoCancel("OK", "Reset to Defaults");
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
			setMaskBackground(Math.min(Math.max((int) gd.getNextNumber(), 0), 255));

			// IJ prefs
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
	JPopupMenu createOptionsMenu(final EnhancedGenericDialog gd) {
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem mi;
		mi = new JMenuItem("Plot Options...");
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IJ.doCommand("Profile Plot Options...");
			}
		});
		popup.add(mi);
		mi = new JMenuItem("Input/Output Options...");
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IJ.doCommand("Input/Output...");
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = new JMenuItem("Help on Sholl metrics");
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", Sholl_Analysis.URL + "#Metrics");
			}
		});
		popup.add(mi);
		return popup;
	}

	/** Retrieves precision according to Analyze>Set Measurements... */
	static int getScientificNotationAwarePrecision() {
		final boolean sNotation = (Analyzer.getMeasurements()&Measurements.SCIENTIFIC_NOTATION)!=0;
		int precision = Analyzer.getPrecision();
		if (sNotation)
			precision = -precision;
		return precision;
	}
}
