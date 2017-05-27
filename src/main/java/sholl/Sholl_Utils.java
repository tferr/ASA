/*
 * #%L
 * Sholl_Analysis plugin for ImageJ
 * %%
 * Copyright (C) 2016 Tiago Ferreira
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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.IndexColorModel;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.scijava.util.VersionUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.HTMLDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.io.Opener;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;
import ij.util.Tools;

/**
 * Utilities for {@link Sholl_Analysis}
 *
 * @see <a href="https://github.com/tferr/ASA">https://github.com/tferr/ASA</a>
 * @see <a href="http://imagej.net/Sholl">http://imagej.net/Sholl</a>
 * @author Tiago Ferreira
 */
public class Sholl_Utils implements PlugIn {

	private static final String BUILD = buildDate();
	private static final String SRC_URL = "https://github.com/tferr/ASA";

	/**
	 * This method is called when the plugin is loaded. <code>arg</code> is
	 * specified in <code>plugins.config</code>. See
	 * {@link ij.plugin.PlugIn#run(java.lang.String)}
	 *
	 * @param arg
	 *            If <code>about</code> the "About" dialog is displayed. If
	 *            <code>sample</code>, a demo image suitable for Sholl analysis
	 *            is opened.
	 */
	@Override
	public void run(final String arg) {
		if (arg.equalsIgnoreCase("about"))
			showAbout();
		else if (arg.equalsIgnoreCase("sample"))
			displaySample();
	}

	/**
	 * Returns the plugin's sample image (File&gt;Samples&gt;ddaC Neuron).
	 *
	 * @return ddaC image, or null if image cannot be retrieved
	 */
	public static ImagePlus sampleImage() {
		final InputStream is = Sholl_Utils.class.getResourceAsStream("/images/ddaC.tif");
		ImagePlus imp = null;
		if (is != null) {
			final Opener opener = new Opener();
			imp = opener.openTiff(is, "Drosophila_ddaC_Neuron.tif");
		}
		return imp;
	}

	/** Displays the ddaC sample image and returns a reference to it */
	protected static ImagePlus displaySample() {
		final ImagePlus imp = sampleImage();
		if (imp == null) {
			IJ.showStatus("Error: Could not open ddaC.tif!");
			IJ.beep();
		} else {
			imp.show();
		}
		return imp;
	}

	/**
	 * Returns an IndexColorModel similar to MATLAB's jet color map.
	 *
	 * @param backgroundGray
	 *            the gray value (8-bit scale) to be used as the first entry of
	 *            the LUT. It is ignored if negative.
	 * @return The "Jet" LUT with the specified background entry
	 * @see <a href=
	 *      "https://list.nih.gov/cgi-bin/wa.exe?A2=IMAGEJ;c8cb4d8d.1306">Jerome
	 *      Mutterer alternative</a>
	 */
	public static IndexColorModel matlabJetColorMap(final int backgroundGray) {
		return matlabJetColorMap(backgroundGray, -1);
	}

	protected static IndexColorModel matlabJetColorMap(final int backgroundGray, final int foregroundGray) {

		// Initialize colors arrays (zero-filled by default)
		final byte[] reds = new byte[256];
		final byte[] greens = new byte[256];
		final byte[] blues = new byte[256];

		// Set greens, index 0-32; 224-255: 0
		for (int i = 0; i < 256 / 4; i++) // index 32-96
			greens[i + 256 / 8] = (byte) (i * 255 * 4 / 256);
		for (int i = 256 * 3 / 8; i < 256 * 5 / 8; ++i) // index 96-160
			greens[i] = (byte) 255;
		for (int i = 0; i < 256 / 4; i++) // index 160-224
			greens[i + 256 * 5 / 8] = (byte) (255 - (i * 255 * 4 / 256));

		// Set blues, index 224-255: 0
		for (int i = 0; i < 256 * 7 / 8; i++) // index 0-224
			blues[i] = greens[(i + 256 / 4) % 256];

		// Set reds, index 0-32: 0
		for (int i = 256 / 8; i < 256; i++) // index 32-255
			reds[i] = greens[(i + 256 * 6 / 8) % 256];

		// Set background and foreground colors
		if (backgroundGray >= 0) {
			reds[0] = greens[0] = blues[0] = (byte) backgroundGray;
		}
		if (foregroundGray >= 0) {
			reds[255] = greens[255] = blues[255] = (byte) foregroundGray;
		}

		return new IndexColorModel(8, 256, reds, greens, blues);

	}

	/** Displays the Sholl's plugin "about" info box */
	private void showAbout() {

		final String version = Sholl_Analysis.VERSION + " " + BUILD;
		final String header1 = "Sholl Analysis " + version;
		final String header2 = "Quantitative Sholl-based morphometry of untraced images";
		final String author = "Tiago Ferreira";
		final String authorURL = "http://imagej.net/User:Tiago";
		final String contributorsURL = SRC_URL + "/graphs/contributors";
		final String releaseNotesURL = SRC_URL + "/blob/master/Notes.md#release-notes-for-sholl-analysis";
		final String javadocsURL = "http://tferr.github.io/ASA/apidocs/";
		final String forumURL = "http://forum.imagej.net/search?q=sholl";
		final String msURL = "http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html";
		final String spacer = "&nbsp;&nbsp;|&nbsp;&nbsp;";

		final StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<div WIDTH=480>");
		sb.append("<div align='center'>");
		sb.append("<b>" + header1 + "</b><br>");
		sb.append("<b>" + header2 + "</b><br>");
		sb.append("<a href='" + authorURL + "'>" + author + "</a> and ");
		sb.append("<a href='" + contributorsURL + "'>contributors</a>");
		sb.append("</div>");
		sb.append("<br><br>");
		sb.append("Released under <a href='https://opensource.org/licenses/GPL-3.0.html'>GPL-3.0</a>. ");
		sb.append("Many thanks to Tom Maddock for writing version 1.0; "
				+ "Johannes Schindelin, Wayne Rasband, Mark Longair, Stephan Preibisch "
				+ "and Bio-Formats team for code snippets.");
		sb.append("<br><br>");
		sb.append("<b>Resources:</b><br>");
		// sb.append("<a href='"+ msURL + "'>Manuscript</a>" + inLineSpacer);
		sb.append("<a href='" + Sholl_Analysis.URL + "'>Documentation</a>" + spacer);
		sb.append("<a href='" + forumURL + "'>Forum</a>" + spacer);
		sb.append("<a href='" + releaseNotesURL + "'>Release Notes</a>" + spacer);
		sb.append("<a href='" + SRC_URL + "'>Source</a>" + spacer);
		sb.append("<a href='" + javadocsURL + "'>Javadoc</a>");
		sb.append("<br><br>");
		sb.append("<b>Citation:</b><br>");
		sb.append("Ferreira et al. ");
		sb.append("<a href='" + msURL + "'>Nat Methods 11, 982-4 (2014)</a>");
		sb.append("</div>");
		sb.append("</html>");
		new HTMLDialog("About Sholl Analysis...", sb.toString());
	}

	/**
	 * Converts an integer to its ordinal (http://stackoverflow.com/a/6810409)
	 */
	protected static String ordinal(final int i) {
		final String[] sufixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
		switch (i % 100) {
		case 11:
		case 12:
		case 13:
			return i + "th";
		default:
			return i + sufixes[i % 10];

		}
	}

	/**
	 * Calculates the centroid of a non-self-intersecting closed polygon. It is
	 * assumed that <code>xpoints</code> and <code>ypoints</code> have the same
	 * size
	 *
	 * @param xpoints
	 *            X coordinates of vertices
	 * @param ypoints
	 *            Y coordinates of vertices
	 * @return the centroid {x,y} coordinates
	 * @see <a href="http://en.wikipedia.org/wiki/Centroid#Centroid_of_polygon">
	 *      Centroid of polygon </a>
	 */
	public static double[] baryCenter(final double[] xpoints, final double[] ypoints) {

		double area = 0, sumx = 0, sumy = 0;
		for (int i = 1; i < xpoints.length; i++) {
			final double cfactor = (xpoints[i - 1] * ypoints[i]) - (xpoints[i] * ypoints[i - 1]);
			sumx += (xpoints[i - 1] + xpoints[i]) * cfactor;
			sumy += (ypoints[i - 1] + ypoints[i]) * cfactor;
			area += cfactor / 2;
		}
		return new double[] { sumx / (6 * area), sumy / (6 * area) };

	}

	/**
	 * Draws a label at the less crowded corner of an ImageJ plot. Height and
	 * width of label is measured so that text remains within the plot's frame.
	 * Text is added to the first free position in this sequence: NE, NW, SE,
	 * SW.
	 *
	 * @param plot
	 *            Plot object
	 * @param label
	 *            Label contents
	 * @param color
	 *            Foreground color of text. Note that this will also set the
	 *            drawing color for the next objects to be be added to the plot
	 */
	public static void makePlotLabel(final Plot plot, final String label, final Color color) {

		final ImageProcessor ip = plot.getProcessor();

		int maxLength = 0;
		String maxLine = "";
		final String[] lines = Tools.split(label, "\n");
		for (int i = 0; i < lines.length; i++) {
			final int length = lines[i].length();
			if (length > maxLength) {
				maxLength = length;
				maxLine = lines[i];
			}
		}

		final Font font = new Font("Helvetica", Font.PLAIN, PlotWindow.fontSize);
		ip.setFont(font);
		plot.setFont(font);
		final FontMetrics metrics = ip.getFontMetrics();
		final int textWidth = metrics.stringWidth(maxLine);
		final int textHeight = metrics.getHeight() * lines.length;

		final Rectangle r = plot.getDrawingFrame();
		final int padding = 4; // space between label and axes
		final int yTop = r.y + 1 + padding;
		final int yBottom = r.y + r.height - textHeight - padding;
		final int xLeft = r.x + 1 + padding;
		final int xRight = r.x + r.width - textWidth - padding;

		final double northEast = meanRoiValue(ip, xLeft, yTop, textWidth, textHeight);
		final double northWest = meanRoiValue(ip, xRight, yTop, textWidth, textHeight);
		final double southEast = meanRoiValue(ip, xLeft, yBottom, textWidth, textHeight);
		final double southWest = meanRoiValue(ip, xRight, yBottom, textWidth, textHeight);
		final double pos = Math.max(Math.max(northEast, northWest), Math.max(southEast, southWest));

		ip.setColor(0);
		plot.setColor(color);
		// We'll draw the text so that multiple labels can be added without
		// overlap
		if (pos == northEast) {
			ip.drawString(label, xLeft, yTop);
			plot.addText(label, plot.descaleX(xLeft), plot.descaleY(yTop));
		} else if (pos == northWest) {
			ip.drawString(label, xRight, yTop);
			plot.addText(label, plot.descaleX(xRight), plot.descaleY(yTop));
		} else if (pos == southEast) {
			ip.drawString(label, xLeft, yBottom);
			plot.addText(label, plot.descaleX(xLeft), plot.descaleY(yBottom));
		} else {
			ip.drawString(label, xRight, yBottom);
			plot.addText(label, plot.descaleX(xRight), plot.descaleY(yBottom));
		}

	}

	/** Returns the mean value of a rectangular ROI */
	private static double meanRoiValue(final ImageProcessor ip, final int x, final int y, final int width,
			final int height) {

		ip.setRoi(x, y, width, height);
		return ImageStatistics.getStatistics(ip, Measurements.MEAN, null).mean;

	}

	/**
	 * Highlights a point on a plot without listing it on the Plot's table. Does
	 * nothing if the point coordinates are <code>null</code>.
	 *
	 * @param plot
	 *            Plot object
	 * @param coordinates
	 *            The coordinates of the point in calibrated (axes) coordinates
	 * @param color
	 *            Sets the drawing color. This will not affect the drawing color
	 *            for the next objects to be be added to the plot
	 */
	public static void markPlotPoint(final Plot plot, final double[] coordinates, final Color color) {

		if (coordinates == null)
			return;
		plot.setLineWidth(6); // default markSize: 5;
		plot.setColor(color);
		plot.drawLine(coordinates[0], coordinates[1], coordinates[0], coordinates[1]);

		// restore defaults
		plot.setLineWidth(1);
		plot.setColor(Color.BLACK);

	}

	/**
	 * Retrieves text from the system clipboard.
	 *
	 * @return the text contents of the clipboard or an empty string if no text
	 *         could be retrieved
	 */
	protected static String getClipboardText() {
		String text = "";
		try {
			final Toolkit toolkit = Toolkit.getDefaultToolkit();
			final Clipboard clipboard = toolkit.getSystemClipboard();
			text = (String) clipboard.getData(DataFlavor.stringFlavor);
		} catch (final Exception e) {
			// if (IJ.debugMode) IJ.handleException(e);
		}
		return text;
	}

	protected static TextWindow getTextWindow(final String windowtitle) {
		final Frame f = WindowManager.getFrame(windowtitle);
		if (f == null || !(f instanceof TextWindow))
			return null;
		return (TextWindow) f;
	}

	/**
	 * Retrieves Sholl Analysis version
	 *
	 * @return the version or a non-empty place holder string if version could
	 *         not be retrieved.
	 *
	 */
	public static String version() {
		final String VERSION = VersionUtils.getVersion(Sholl_Analysis.class);
		return (VERSION == null) ? "X Dev" : VERSION;
	}

	/**
	 * Retrieves Sholl Analysis implementation date
	 *
	 * @return the implementation date or an empty strong if date could not be
	 *         retrieved.
	 */
	private static String buildDate() {
		String BUILD_DATE = "";
		final Class<Sholl_Analysis> clazz = Sholl_Analysis.class;
		final String className = clazz.getSimpleName() + ".class";
		final String classPath = clazz.getResource(className).toString();
		final String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
		try {
			final Manifest manifest = new Manifest(new URL(manifestPath).openStream());
			final Attributes attr = manifest.getMainAttributes();
			BUILD_DATE = attr.getValue("Implementation-Date");
			BUILD_DATE = BUILD_DATE.substring(0, BUILD_DATE.lastIndexOf("T"));
		} catch (final Exception ignored) {
			BUILD_DATE = "";
		}
		return BUILD_DATE;
	}

}
