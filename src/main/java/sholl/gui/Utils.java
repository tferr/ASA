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
package sholl.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JMenuItem;
import javax.swing.UIManager;

import ij.IJ;
import ij.plugin.BrowserLauncher;
import ij.plugin.frame.Recorder;
import sholl.Sholl_Analysis;

/** Provides customizations for ImageJ dialogs when not running headless. */
public class Utils {

	/** Private constructor to prevent class instantiation. */
	private Utils() {
	}

	protected static void addClickableURLtoLabel(final Component label, final String url, final Color color) {
		if (isHeadless() || label == null || url == null)
			return;
		label.addMouseListener(new MouseAdapter() {
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
				label.setForeground(Color.BLUE);
				label.setCursor(new Cursor(Cursor.HAND_CURSOR));
				// IJ.showStatus("Click to open URL...");
			}

			@Override
			public void mouseExited(final MouseEvent paramAnonymousMouseEvent) {
				label.setForeground(color);
				label.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				// IJ.showStatus("");
			}
		});
	}

	/** Customizes macro recordings */
	public static final void improveRecording() {
		if (Recorder.record) {
			String recordString = "// Recording Sholl Analysis version " + Sholl_Analysis.VERSION + "\n" + "// Visit "
					+ Sholl_Analysis.URL + "#Batch_Processing for scripting tips\n";
			final String cmd = Recorder.getCommand();
			final String cmdOptions = Recorder.getCommandOptions();
			if (cmd == null || cmdOptions == null) {
				recordString += "// NB: Commands dismissing prompts (such the ones in the \"More\u00bb\" dropdown menu) may not\n"
						+ "// record properly. You may need to repeat recording if recorded instruction is invalid\n";
			}
			Recorder.recordString(recordString);
		}
	}

	/**
	 * Returns the foreground color of disabled components.
	 *
	 * @return The {@link UIManager} foreground color of a disabled component.
	 */
	public static Color getDisabledComponentColor() {
		try {
			return UIManager.getColor("CheckBox.disabledText");
		} catch (final Exception ignored) {
			return Color.GRAY;
		}
	}

	public static JMenuItem menuItemTrigerringURL(final String label, final String URL) {
		final JMenuItem mi = new JMenuItem(label);
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", URL);
			}
		});
		return mi;
	}

	public static JMenuItem menuItemTriggeringResources() {
		final JMenuItem mi = new JMenuItem("About & Resources...");
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				IJ.runPlugIn(sholl.Sholl_Utils.class.getName(), "about");
			}
		});
		return mi;
	}

	/**
	 * @return {@code true} if running on an headless environment
	 */
	public static boolean isHeadless() {
		return GraphicsEnvironment.isHeadless();
	}

	protected final static String citationURL() {
		return "http://www.nature.com/nmeth/journal/v11/n10/full/nmeth.3125.html";
	}

	protected final static String citationMsg() {
		return "Please be so kind as to cite this program in your own\n"
				+ "research: Ferreira et al. Nat Methods 11, 982-4 (2014)";
	}

	public final static Color infoColor() {
		return Color.DARK_GRAY;
	}

	public final static Color warningColor() {
		return Color.RED;
	}

}
