package sholl.gui;

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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JPopupMenu;
import javax.swing.UIManager;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.BrowserLauncher;

/**
 * Enhances GenericDialog with a few additional methods.
 */
public class EnhancedGenericDialog extends GenericDialog {
	private static final long serialVersionUID = 1L;

	private String labelOfHelpActionButton = null;
	private ActionListener helpActionButtonListener = null;
	private MouseAdapter helpActionMouseListener = null;

	public EnhancedGenericDialog(final String title) {
		super(title);
	}

	public EnhancedGenericDialog(final String title, final Frame parent) {
		super(title, parent);
	}

	/**
	 * Adds a functional URL consisting of one or more lines of text, which will
	 * be displayed using the specified font and color.
	 * 
	 * @param text
	 *            The label contents
	 * @param font
	 *            the label font
	 * @param color
	 *            the label color
	 * @param url
	 *            The URL to be opened by the default browser of the OS
	 */
	public void addHyperlinkMessage(final String text, final Font font, final Color color, final String url) {
		if (isHeadless())
			return;
		super.addMessage(text, font, color);
		final Component msgLabel = super.getMessage();
		if (msgLabel != null && url != null) {
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
					// IJ.showStatus("Click to open URL...");
				}

				@Override
				public void mouseExited(final MouseEvent paramAnonymousMouseEvent) {
					msgLabel.setForeground(color);
					msgLabel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					// IJ.showStatus("");
				}
			});
		}
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object source = e.getSource();
		if (!isHeadless() && source != null && labelOfHelpActionButton != null
				&& source.toString().contains(labelOfHelpActionButton) && helpActionButtonListener != null) {
			helpActionButtonListener.actionPerformed(e);
		} else {
			super.actionPerformed(e);
		}
	}

	public void assignListenerToHelpButton(final String buttonLabel, final ActionListener listener) {
		if (!isHeadless() && buttonLabel != null && listener != null) {
			super.addHelp("");
			super.setHelpLabel(buttonLabel);
			labelOfHelpActionButton = buttonLabel;
			helpActionButtonListener = listener;
		}
	}

	public void assignPopupToHelpButton(final String buttonLabel, final JPopupMenu popupmenu) {
		if (!isHeadless() && buttonLabel != null && popupmenu != null) {
			// Ensure swing component is displayed with a java.awt look and feel
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				popupmenu.updateUI();
			} catch (final Exception ignored) {
			}
			super.addHelp("");
			super.setHelpLabel(buttonLabel);
			labelOfHelpActionButton = buttonLabel;
			helpActionMouseListener = new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					popupmenu.show((Button) e.getSource(), 0, 0);
				}
			};

			helpActionButtonListener = new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					final MouseEvent me = new MouseEvent((Component) e.getSource(), MouseEvent.MOUSE_CLICKED,
							e.getWhen(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, true);
					helpActionMouseListener.mousePressed(me);
				}
			};
		}
	}

	private static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	public void showScrollableDialog() {
		if (!isHeadless())
			addScrollBars();
		super.showDialog();
	}

	/**
	 * Adds AWT scroll bars to the GenericDialog. From bio-formats Window.Tools,
	 * licensed under GNU GPLv2 (April 2013)
	 * 
	 * @see <a href=
	 *      "http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/util/WindowTools.java;hb=HEAD">
	 *      git.openmicroscopy</a>
	 */
	private void addScrollBars() {

		final GridBagLayout layout = (GridBagLayout) this.getLayout();

		// extract components
		final int count = this.getComponentCount();
		final Component[] c = new Component[count];
		final GridBagConstraints[] gbc = new GridBagConstraints[count];
		for (int i = 0; i < count; i++) {
			c[i] = this.getComponent(i);
			gbc[i] = layout.getConstraints(c[i]);
		}

		// clear components
		this.removeAll();
		layout.invalidateLayout(this);

		// create new container panel
		final Panel newPane = new Panel();
		final GridBagLayout newLayout = new GridBagLayout();
		newPane.setLayout(newLayout);
		for (int i = 0; i < count; i++) {
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
		if (size.width > maxWidth)
			size.width = maxWidth;
		if (size.height > maxHeight)
			size.height = maxHeight;

		// Create scroll pane. This has some problems: In some installations
		// horizontal scrollbars will be displayed even if not required. In
		// others, setScrollPosition will not be respected and panel will not
		// be anchored to top
		final ScrollPane scroll = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED) {

			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				return size;
			}
		};

		// Tweak for Mac OS (mainly 10.9/10.10): The background of ScrollPane
		// does not match that of ij.gui.GenericDialog, i.e., SystemColor.control.
		// Imposing SystemColor.control is not reliable as it appears to be always
		// Color.WHITE in Java 7 and higher. On the other hand, using UIManager to
		// retrieve control color seems to work consistently across platforms:
		// nadeausoftware.com/articles/2010/07/java_tip_systemcolors_mac_os_x_user_interface_themes
		final Color background = javax.swing.UIManager.getColor("control");
		newPane.setBackground(background);
		scroll.setBackground(background);
		scroll.add(newPane);
		scroll.validate();
		scroll.setScrollPosition(0, 0); // TODO this does not seem to work with java 6, mac 10.9

		// Create an "awt border" around the scrollpanel in cases where such
		// border is absent Platforms tested: Ubuntu: Open JDK7,8, Windows XP:
		// Sun JDK7, Mac OS: Sun JDK7,8
		final Panel borderPanel = new Panel();
		if (IJ.isMacOSX() && IJ.isJava17()) {
			final float hsbVals[] = Color.RGBtoHSB(background.getRed(), background.getGreen(), background.getBlue(),
					null);
			borderPanel.setBackground(Color.getHSBColor(hsbVals[0], hsbVals[1], 0.92f * hsbVals[2]));
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
		this.add(borderPanel);

	}

	public Color getDisabledComponentColor() {
		try {
			return UIManager.getColor("CheckBox.disabledText");
		} catch (Exception ignored) {
			return Color.GRAY;
		}
	}
}