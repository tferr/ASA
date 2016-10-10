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

import ij.gui.WaitForUserDialog;

/**
 * Enhances WaitForUserDialog with a few additional features. Customizations are
 * ignored if running headless.
 */
public class EnhancedWaitForUserDialog extends WaitForUserDialog {
	private static final long serialVersionUID = 1L;

	/**
	 * {@link ij.gui.WaitForUserDialog#WaitForUserDialog(String)
	 * WaitForUserDialog constructor}
	 *
	 * @param text
	 *            Dialog's text
	 */
	public EnhancedWaitForUserDialog(final String text) {
		super(text);
	}

	/**
	 * Adds a functional URL to the dialog's {@link ij.gui.MultiLineLabel}.
	 *
	 * @param url
	 *            The URL to be opened by the default browser of the OS
	 */
	public void addHyperlink(final String url) {
		Utils.addClickableURLtoLabel(super.label, url, null);
	}

}