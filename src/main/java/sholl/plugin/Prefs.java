/*
 * #%L
 * Sholl_Analysis plugin for ImageJ
 * %%
 * Copyright (C) 2017 Tiago Ferreira
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

package sholl.plugin;

import java.io.IOException;
import java.net.URL;

import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.app.AppService;
import org.scijava.command.Command;
import org.scijava.options.OptionsPlugin;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

import sholl.Logger;
import sholl.ShollUtils;
import sholl.gui.Helper;

/**
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, label = "Sholl Options", visible = false,
	initializer = "init")
public class Prefs extends OptionsPlugin implements Command {

	@Parameter
	private AppService appService;
	@Parameter
	private ImageJ ij;
	@Parameter
	private PrefService pService;
	@Parameter
	private PlatformService platformService;
	@Parameter
	private UIService uiService;

	/* DEFAULTS */
	public final static boolean DEF_SKIP_SINGLE_VOXELS = true;
	public final static int DEF_ENCLOSING_RADIUS_CUTOFF = 1;
	public final static int DEF_MIN_DEGREE = 2;
	public final static int DEF_MAX_DEGREE = 20;
	public final static double DEF_RSQUARED = 0.80;
	public final static double DEF_PVALUE = 0.05;
	public final static boolean DEF_DEBUG_MODE = false;
	public final static boolean DEF_AUTO_CLOSE = false;

	/* Fields */
	private final static String PLACEHOLDER_CHOICE = "Choose...";
	private final static String HELP_URL = "https://imagej.net/Sholl_Analysis";
	private Helper helper;
	private Logger logger;
	private boolean restartRequired;

	/* Prompt */
	private static final String HEADER_HTML = "<html><body><div style='font-weight:bold;'>";

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "Sampling:")
	private String HEADER1;

	@Parameter(label = "Ignore isolated voxels")
	private boolean skipSingleVoxels;

	@Parameter(label = "Enclosing radius cuttoff", min = "1")
	private int enclosingRadiusCutoff = DEF_ENCLOSING_RADIUS_CUTOFF;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "Polynomial Regression:")
	private String HEADER1B;
	@Parameter(label = "Min. degree", min = "2", max = "100",
		callback = "flagRestart")
	private int minDegree = DEF_MIN_DEGREE;

	@Parameter(label = "Max. degree", min = "2", max = "60",
		callback = "flagRestart")
	private int maxDegree = DEF_MAX_DEGREE;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "Goodness of Fit Criteria:")
	private String HEADER1C;

	@Parameter(label = "R-squared >", min = "0.5", stepSize = "0.01", max = "1")
	private double rSquared = DEF_RSQUARED;

	@Parameter(label = "P-value <", min = "0.0001", stepSize = "0.01",
		max = "0.05")
	private double pValue = DEF_PVALUE;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "Plugin Settings:")
	private String HEADER3;

	@Parameter(label = "Debug mode", callback = "flagRestart")
	private boolean debugMode = DEF_DEBUG_MODE;

	@Parameter(label = "Auto-close dialog", callback = "flagRestart")
	private boolean autoClose = DEF_AUTO_CLOSE;

	@Parameter(label = "Reset All Preferences...", callback = "reset")
	private Button resetPrefs;

	@Parameter(required = false, visibility = ItemVisibility.MESSAGE,
		label = HEADER_HTML + "<br>Help and Resources:")
	private String HEADER4;

	@Parameter(label = "Resource", required = false, persist = false,
		callback = "help", choices = { PLACEHOLDER_CHOICE, "About...",
			"ImageJ forum", "Documentation", "Source code" })
	private String helpChoice = " ";

	@Parameter(required = false, visibility = ItemVisibility.INVISIBLE)
	private boolean ignoreBitmapOptions;

	@SuppressWarnings("unused")
	private void init() {
		helper = new Helper(context());
		logger = new Logger(context());
		logger.debug("Prefs successfully initialized");
		if (ignoreBitmapOptions) {
			resolveInput("skipSingleVoxels");
		}
	}

	@SuppressWarnings("unused")
	private void help() {
		if (PLACEHOLDER_CHOICE.equals(helpChoice)) return; // do nothing
		final String choice = helpChoice;
		helpChoice = PLACEHOLDER_CHOICE;
		if (choice.contains("About")) {
			about();
			return;
		}
		String url = "";
		if (choice.contains("forum")) url = "https://forum.image.sc/";
		else if (choice.contains("code")) url = "https://github.com/tferr/ASA";
		else url = HELP_URL;
		try {
			platformService.open(new URL(url));
		}
		catch (final IOException e) {
			logger.debug(e);
			helper.error("<HTML><div WIDTH=400>Web page could not be open. " +
				"Please visit " + url + " using your web browser.", null);
		}
	}

	private void about() {
		final StringBuilder sb = new StringBuilder();
		sb.append("You are running Sholl Analysis v").append(ShollUtils.version());
		sb.append(" ").append(ShollUtils.buildDate()).append("\n");
		sb.append("on ImageJ ").append(ij.getVersion());
		helper.infoMsg(sb.toString(), null);
	}

	@SuppressWarnings("unused")
	private void flagRestart() {
		restartRequired = true;
		if (minDegree > maxDegree) maxDegree = minDegree;
		if (maxDegree < minDegree) minDegree = maxDegree;
	}

	@Override
	public void run() {
		super.run();
		if (restartRequired) helper.infoMsg(
			"Please restart the Sholl Analysis plugin for changes to take effect.",
			"New Preferences Set");
	}

	@Override
	public void reset() {

		final Result result = helper.yesNoPrompt(
			"Reset all preferences to defaults?", "Confirm Reset");
		if (result == Result.NO_OPTION || result == Result.CANCEL_OPTION) return;

		// Reset preferences
		super.reset();
		pService.clear(ShollAnalysisImg.class);
		pService.clear(ChooseDataset.class);

		// Reset inputs in prompt
		skipSingleVoxels = DEF_SKIP_SINGLE_VOXELS;
		enclosingRadiusCutoff = DEF_ENCLOSING_RADIUS_CUTOFF;
		minDegree = DEF_MIN_DEGREE;
		maxDegree = DEF_MAX_DEGREE;
		rSquared = DEF_RSQUARED;
		pValue = DEF_PVALUE;
		debugMode = DEF_DEBUG_MODE;
		autoClose = DEF_AUTO_CLOSE;

		helper.infoMsg("Preferences were successfully reset.", null);
		restartRequired = true;

	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(Prefs.class, true);
	}

}
