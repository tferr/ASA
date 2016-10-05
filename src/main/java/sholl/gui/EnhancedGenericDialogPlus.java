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
package sholl.gui;

import java.awt.Color;
import java.awt.Font;

import fiji.util.gui.GenericDialogPlus;

/**
 * Enhances GenericDialogPlus with a few additional features. Methods in this
 * class do nothing if running headless.
 */
public class EnhancedGenericDialogPlus extends GenericDialogPlus {
	private static final long serialVersionUID = 1L;

	public EnhancedGenericDialogPlus(final String title) {
		super(title);
	}

	/**
	 * Appends a message to the specified dialog consisting of one or more lines
	 * of text, and assigns a functional URL to it.
	 *
	 * @param text
	 *            The contents of the clickable label
	 * @param font
	 *            the label font. If {@code null}, the GenericDialog's default
	 *            font is used
	 * @param color
	 *            the label color. If {@code null}, the GenericDialog's default
	 *            foreground color is used
	 * @param url
	 *            The URL to be opened by the default browser of the OS
	 */
	public void addHyperlinkMessage(final String text, final Font font, final Color color, final String url) {
		super.addMessage(text, font, color);
		Utils.addClickableURLtoLabel(super.getMessage(), url, color);
	}

	/** Allows users to visit the manuscript from a dialog prompt */
	public void addCitationMessage() {
		super.addMessage(Utils.citationMsg(), null, Utils.infoColor());
		Utils.addClickableURLtoLabel(super.getMessage(), Utils.citationURL(), Utils.infoColor());
	}

}