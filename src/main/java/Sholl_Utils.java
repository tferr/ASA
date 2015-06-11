/* Copyright 2010-2015 Tiago Ferreira
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

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.IndexColorModel;
import java.io.InputStream;


/**
 * Auxiliary commands and routines for Sholl_Analysis. addScrollBars() is from
 * Bio-Formats Window.Tools
 *
 * @see <a href="https://github.com/tferr/ASA">https://github.com/tferr/ASA</a>
 * @see <a href="http://fiji.sc/Sholl_Analysis">http://fiji.sc/Sholl_Analysis</a>
 * @see <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/util/WindowTools.java;hb=HEAD">git.openmicroscopy</a>
 *
 * @author Tiago Ferreira
 */
public class Sholl_Utils implements PlugIn {

	private static final String BUILD = "2015.03";
	private static final String SRC_URL = "https://github.com/tferr/ASA";
	private static final String DOC_URL = "http://fiji.sc/Sholl";
	private static int background = Sholl_Analysis.maskBackground;

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
	 * Returns the ddaC sample image in ./resources. Returns null if image
	 * cannot be retrieved
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
 
	/** Displays the ddaC sample image in ./resources and returns a reference to it */
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
	 * Returns an IndexColorModel similar to MATLAB's jet color map. An 8-bit
	 * gray color specified by backgroundGray is used as the first entry of the
	 * LUT. See https://list.nih.gov/cgi-bin/wa.exe?A2=IMAGEJ;c8cb4d8d.1306 for
	 * a simplified version by Jerome Mutterer
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
		final IndexColorModel cm = matlabJetColorMap(background);
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

	/** Displays an "about" info box */
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
		gd.enableYesNoCancel("Browse Documentation", "Browse Source Code");
		gd.hideCancelButton();
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		else if (gd.wasOKed())
			IJ.runPlugIn("ij.plugin.BrowserLauncher", DOC_URL);
		else
			IJ.runPlugIn("ij.plugin.BrowserLauncher", SRC_URL);
	}

	/** Convers an integer to its ordinal (http://stackoverflow.com/a/6810409) */
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

	/**
	 * Adds AWT scroll bars to the given container. From bio-formats Window.Tools,
	 * licensed under GNU GPLv2 (April 2013)
	 * @see <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/util/WindowTools.java;hb=HEAD">git.openmicroscopy</a>
	 */
	@SuppressWarnings("serial")
	static void addScrollBars(final Container pane) {

		addCitationUrl((GenericDialog) pane);
		final GridBagLayout layout = (GridBagLayout) pane.getLayout();

		// extract components
		final int count = pane.getComponentCount();
		final Component[] c = new Component[count];
		final GridBagConstraints[] gbc = new GridBagConstraints[count];
		for (int i=0; i<count; i++) {
			c[i] = pane.getComponent(i);
			gbc[i] = layout.getConstraints(c[i]);
		}

		// clear components
		pane.removeAll();
		layout.invalidateLayout(pane);

		// create new container panel
		final Panel newPane = new Panel();
		final GridBagLayout newLayout = new GridBagLayout();
		newPane.setLayout(newLayout);
		for (int i=0; i<count; i++) {
			newLayout.setConstraints(c[i], gbc[i]);
			newPane.add(c[i]);
		}

		// HACK - get preferred size for container panel
		// NB: don't know a better way:
		// - newPane.getPreferredSize() doesn't work
		// - newLayout.preferredLayoutSize(newPane) doesn't work
		final Frame f = new Frame();
		f.setLayout(new BorderLayout());
		f.add(newPane, BorderLayout.CENTER);
		f.pack();
		final Dimension size = newPane.getSize();
		f.remove(newPane);
		f.dispose();

		// compute best size for scrollable viewport
		size.width += 35; // initially 25;
		size.height += 30; // initially 15;
		final Dimension screen = IJ.getScreenSize();
		final int maxWidth = 9 * screen.width / 10; // initially 7/8;
		final int maxHeight = 8 * screen.height / 10; // initially 3/4
		if (size.width > maxWidth) size.width = maxWidth;
		if (size.height > maxHeight) size.height = maxHeight;

		// Create scroll pane. This has some problems: In some installations horizontal
		// scrollbars will be displayed even if not required. In others, setScrollPosition
		// will not be respected and panel will not be anchored to top
		final ScrollPane scroll = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED) {
			@Override
			public Dimension getPreferredSize() {
				return size;
			}
		};

		// Tweak for Mac OS (mainly 10.9/10.10): The background of ScrollPane does not match that
		// of ij.gui.GenericDialog, i.e., SystemColor.control. Imposing SystemColor.control is not
		// reliable as it appears to be always Color.WHITE in Java 7 and higher. On the other hand,
		// Using UIManager to retrieve control color seems to work consistently across platforms:
		// nadeausoftware.com/articles/2010/07/java_tip_systemcolors_mac_os_x_user_interface_themes
		final Color background = javax.swing.UIManager.getColor("control");
		newPane.setBackground(background);
		scroll.setBackground(background);
		scroll.add(newPane);
		scroll.validate();
		scroll.setScrollPosition(0,0);

		// Create an "awt border" around the scrollpanel in cases where such border is absent
		// Platforms tested: Ubuntu: Open JDK7,8, Windows XP: Sun JDK7, Mac OS: Sun JDK7,8
		final Panel borderPanel = new Panel();
		if (IJ.isMacOSX() && IJ.isJava17()) {
			final float hsbVals[] = Color.RGBtoHSB(background.getRed(),
					background.getGreen(), background.getBlue(), null);
			borderPanel.setBackground(Color.getHSBColor(hsbVals[0], hsbVals[1],
					0.92f * hsbVals[2]));
		}
		borderPanel.add(scroll);

		// ensure constraints
		final GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		layout.setConstraints(scroll, constraints);

		// add scroll pane to original container
		pane.add(borderPanel);

	}

	/**
	 * Adds a functional URL to the last added message of a GenericDialog, i.e.,
	 * the Label created by the last addMessage() call. Based on Stephan Preibisch's
	 * Stitching plugin
	 * @see <a href="https://raw.github.com/fiji/Stitching/master/src/main/java/stitching/CommonFunctions.java>github.com/fiji/Stitching/</a>
	 */
	static final void setClickabaleMsg(final GenericDialog gd, final String url, final Color color) {
		final Component msgLabel = gd.getMessage();
		if ( msgLabel != null && url != null ) {
			msgLabel.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent paramAnonymousMouseEvent) {
					try {
						BrowserLauncher.openURL(url);
					} catch (final Exception localException) {
						IJ.error("" + localException);
					}
				}

				@Override
				public void mouseEntered(final MouseEvent paramAnonymousMouseEvent) {
					msgLabel.setForeground(Color.BLUE);
					msgLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
					//IJ.showStatus("Click to open URL...");
				}

				@Override
				public void mouseExited(final MouseEvent paramAnonymousMouseEvent) {
					msgLabel.setForeground(color);
					msgLabel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					//IJ.showStatus("");
				}
			});
		}
	}

	/** Allows users to visit the manuscript from a dialog prompt */
	static final void addCitationUrl(final GenericDialog gd) {
		gd.setInsets(10, 5, 0);
		gd.addMessage("Please be so kind as to cite this program in your own\n"
				+ "research: Ferreira et al. Nat Methods 11, 982–4 (2014)", null, Color.DARK_GRAY);
		setClickabaleMsg(gd, "http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html", Color.DARK_GRAY);

	}
}
