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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.IndexColorModel;
import java.io.InputStream;

import javax.swing.UIManager;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.io.Opener;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;
import sholl.gui.EnhancedGenericDialog;


/**
 * Auxiliary commands and routines for {@link Sholl_Analysis}
 *
 * @see <a href="https://github.com/tferr/ASA">https://github.com/tferr/ASA</a>
 * @see <a href="http://imagej.net/Sholl">http://imagej.net/Sholl</a>
 * @author Tiago Ferreira
 */
public class Sholl_Utils implements PlugIn {

	private static final String BUILD = "2016.02";
	private static final String SRC_URL = "https://github.com/tferr/ASA";
	private static final String DOC_URL = "http://imagej.net/Sholl";

	/** See {@link ij.plugin.PlugIn#run(java.lang.String)} */
	@Override
	public void run(final String arg) {
		if (arg.equalsIgnoreCase("about"))
			showAbout();
		else if (arg.equalsIgnoreCase("sample"))
			displaySample();
		else if(arg.equalsIgnoreCase("jet"))
			applyJetLut();
	}

	/**
	 * Returns the plugin's sample image (File&gt;Samples&gt;ddaC Neuron).
	 * 
	 * @return ddaC image, or null if image cannot be retrieved
	 */
	public static ImagePlus sampleImage() {
		final InputStream is = Sholl_Utils.class.getResourceAsStream("/resources/ddaC.tif");
		ImagePlus imp = null;
		if (is!=null) {
			final Opener opener = new Opener();
			imp = opener.openTiff(is, "Drosophila_ddaC_Neuron.tif");
		}
		return imp;
	}
 
	/** Displays the ddaC sample image and returns a reference to it */
	static ImagePlus displaySample() {
		final ImagePlus imp = sampleImage();
		if (imp==null) {
			IJ.showStatus("Error: Could not open ddaC.tif!"); IJ.beep();
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
	 *            the LUT.
	 * @return The "Jet" LUT with the specified background entry
	 * @see <a href=
	 *      "https://list.nih.gov/cgi-bin/wa.exe?A2=IMAGEJ;c8cb4d8d.1306">Jerome
	 *      Mutterer's alternative</a>
	 */
	public static IndexColorModel matlabJetColorMap(final int backgroundGray) {

		// Initialize colors arrays (zero-filled by default)
		final byte[] reds	= new byte[256];
		final byte[] greens = new byte[256];
		final byte[] blues	= new byte[256];

		// Set greens, index 0-32; 224-255: 0
		for( int i = 0; i < 256/4; i++ )		 // index 32-96
			greens[i+256/8] = (byte)(i*255*4/256);
		for( int i = 256*3/8; i < 256*5/8; ++i ) // index 96-160
			greens[i] = (byte)255;
		for( int i = 0; i < 256/4; i++ )		 // index 160-224
			greens[i+256*5/8] = (byte)(255-(i*255*4/256));

		// Set blues, index 224-255: 0
		for(int i = 0; i < 256*7/8; i++)		 // index 0-224
			blues[i] = greens[(i+256/4) % 256];

		// Set reds, index 0-32: 0
		for(int i = 256/8; i < 256; i++)		 // index 32-255
			reds[i] = greens[(i+256*6/8) % 256];

		// Set background color
		reds[0] = greens[0] = blues[0] = (byte)backgroundGray;

		return new IndexColorModel(8, 256, reds, greens, blues);

	}

	/** Applies matlabJetColorMap() to frontmost image */
	void applyJetLut() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp!=null && imp.getType()==ImagePlus.COLOR_RGB) {
			IJ.error("LUTs cannot be assiged to RGB Images.");
			return;
		}

		// Display LUT
		final IndexColorModel cm = matlabJetColorMap(Sholl_Analysis.getMaskBackground());
		if (imp==null) {
			imp = new ImagePlus("MATLAB Jet",ij.plugin.LutLoader.createImage(cm));
			imp.show();
		} else {
			if (imp.isComposite())
				((CompositeImage)imp).setChannelColorModel(cm);
			else
				imp.getProcessor().setColorModel(cm);
			imp.updateAndDraw();
		}
	}

	/** Displays the Sholl's plugin "about" info box */
	void showAbout() {
		final String version = Sholl_Analysis.VERSION +" "+ BUILD;
		final String summary = "Quantitative Sholl-based morphometry of untraced neuronal arbors";
		final String authors = "Tiago Ferreira, Tom Maddock (v1.0)";
		final String thanks = "Johannes Schindelin, Wayne Rasband, Mark Longair, Stephan Preibisch,\n"
				+ "Bio-Formats team";

		final Font plainf = new Font("SansSerif", Font.PLAIN, 12);
		final Font boldf = new Font("SansSerif", Font.BOLD, 12);

		final GenericDialog gd = new GenericDialog("About Sholl Analysis...");
		gd.addMessage(summary, boldf);
		gd.addMessage("Version", boldf);
		gd.setInsets(0, 20, 0);
		gd.addMessage(version, plainf);
		gd.addMessage("Authors", boldf);
		gd.setInsets(0, 20, 0);
		gd.addMessage(authors, plainf);
		gd.addMessage("Special Thanks", boldf);
		gd.setInsets(0, 20, 0);
		gd.addMessage(thanks, plainf);
		gd.enableYesNoCancel("API", "Source Code");
		gd.addHelp(DOC_URL);
		gd.setHelpLabel("User Manual");
		gd.setCancelLabel("Dismiss");
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		else if (gd.wasOKed())
			IJ.runPlugIn("ij.plugin.BrowserLauncher", "http://tferr.github.io/ASA/apidocs/");
		else
			IJ.runPlugIn("ij.plugin.BrowserLauncher", SRC_URL);
	}

	/** Converts an integer to its ordinal (http://stackoverflow.com/a/6810409) */
	static String ordinal(final int i) {
		final String[] sufixes = new String[] { "th", "st", "nd", "rd", "th",
				"th", "th", "th", "th", "th" };
		switch (i % 100) {
		case 11:
		case 12:
		case 13:
			return i + "th";
		default:
			return i + sufixes[i % 10];

		}
	}

	/** Allows users to visit the manuscript from a dialog prompt */
	static final void addCitationUrl(final GenericDialog gd) {
		gd.setInsets(10, 5, 0);
		gd.addMessage("Please be so kind as to cite this program in your own\n"
				+ "research: Ferreira et al. Nat Methods 11, 982-4 (2014)", null, Color.DARK_GRAY);
		setClickabaleMsg(gd, "http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html", Color.DARK_GRAY);

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
	 * @see <a
	 *      href="http://en.wikipedia.org/wiki/Centroid#Centroid_of_polygon">Centroid
	 *      of polygon </a>
	 */
	public static double[] baryCenter(final double[] xpoints, final double[] ypoints) {

		double area = 0, sumx = 0, sumy = 0;
		for (int i=1; i<xpoints.length; i++) {
			final double cfactor = (xpoints[i-1]*ypoints[i]) - (xpoints[i]*ypoints[i-1]);
			sumx += (xpoints[i-1] + xpoints[i]) * cfactor;
			sumy += (ypoints[i-1] + ypoints[i]) * cfactor;
			area += cfactor/2;
		}
		return new double[] { sumx/(6*area), sumy/(6*area) };

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

		int maxLength = 0; String maxLine = "";
		final String[] lines = Tools.split(label, "\n");
		for (int i = 0; i<lines.length; i++) {
			final int length = lines[i].length();
			if (length>maxLength)
				{ maxLength = length; maxLine = lines[i]; }
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
		final double pos = Math.max(Math.max(northEast, northWest), Math.max(southEast,southWest));

		ip.setColor(0);
		plot.setColor(color);
		// We'll draw the text so that multiple labels can be added without overlap
		if (pos==northEast) {
			ip.drawString(label, xLeft, yTop);
			plot.addText(label, plot.descaleX(xLeft), plot.descaleY(yTop));
		} else if (pos==northWest) {
			ip.drawString(label, xRight, yTop);
			plot.addText(label, plot.descaleX(xRight), plot.descaleY(yTop));
		} else if (pos==southEast) {
			ip.drawString(label, xLeft, yBottom);
			plot.addText(label, plot.descaleX(xLeft), plot.descaleY(yBottom));
		} else {
			ip.drawString(label, xRight, yBottom);
			plot.addText(label, plot.descaleX(xRight), plot.descaleY(yBottom));
		}

	}

	/** Returns the mean value of a rectangular ROI */
	private static double meanRoiValue(final ImageProcessor ip, final int x, final int y,
			final int width, final int height) {

		ip.setRoi(x, y, width, height);
		return ImageStatistics.getStatistics(ip, Measurements.MEAN, null).mean;

	}

	/**
	 * Highlights a point on a plot without listing it on the Plot's table.
	 *
	 * @param plot
	 *            Plot object
	 * @param coordinates
	 *            The coordinates of the point in calibrated (axes) coordinates
	 * @param color
	 *            Sets the drawing color. This will not affect the drawing color
	 *            for the next objects to be be added to the plot
	 */
	public static void markPlotPoint(final Plot plot, final double[] coordinates,
			final Color color) {

		plot.setLineWidth(6); // default markSize: 5;
		plot.setColor(color);
		plot.drawLine(coordinates[0], coordinates[1], coordinates[0], coordinates[1]);

		// restore defaults
		plot.setLineWidth(1);
		plot.setColor(Color.BLACK);

	}

	/** Checks if table in the "Results" window contains valid data */
	static boolean validResultsTable() {
		final ResultsTable rt = ResultsTable.getResultsTable();
		return (ResultsTable.getResultsWindow() != null && rt != null && rt.getCounter() != 0);
	}


	/**
	 * Retrieves text from the system clipboard.
	 *
	 * @return the text contents of the clipboard or an empty string if no text
	 *         could be retrieved
	 */
	static String getClipboardText() {
		String text = "";
		try {
			final Toolkit toolkit = Toolkit.getDefaultToolkit();
			final Clipboard clipboard = toolkit.getSystemClipboard();
			text = (String)clipboard.getData(DataFlavor.stringFlavor);
		} catch (final Exception e) {
			//if (IJ.debugMode) IJ.handleException(e);
		}
		return text;
	}

}
