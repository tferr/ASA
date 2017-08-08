
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

import sholl.Helper;
import sholl.ShollUtils;

/**
 * @author Tiago Ferreira
 */

@Plugin(type = Command.class, label = "Sholl Options", visible = false, initializer = "init")
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
	public final static int DEF_ENCLOSING_RADIUS_CUTOFF = 1;
	public final static int DEF_MIN_DEGREE = 2;
	public final static int DEF_MAX_DEGREE = 20;
	public final static double DEF_RSQUARED = 0.80;
	public final static double DEF_PVALUE = 0.05;
	public final static boolean DEF_DEBUG_MODE = false;
	public final static boolean DEF_AUTO_CLOSE = false;

	/* Fields */
	private final static String PLACEHOLDER_CHOICE = "Choose resource...";
	private final static String HELP_URL = "https://imagej.net/Sholl_Analysis";
	private Helper helper;
	private boolean restartRequired;

	/* Prompt */
	protected static final String HEADER_HTML = "<html><body><div style='width:170;font-weight:bold;padding-left:0;padding-right:0'>";

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE,//
			label = HEADER_HTML + "Sampling:")
	private String HEADER1;

	@Parameter(label = "Enclosing radius cuttoff", min = "1", callback = "flagRestart")
	private int enclosingRadiusCutoff = DEF_ENCLOSING_RADIUS_CUTOFF;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE,//
			label = HEADER_HTML +"Polynomial Regression:")
	private String HEADER1B;
	@Parameter(label = "Min. degree", min = "2", max = "100", callback = "flagRestart")
	private int minDegree = DEF_MIN_DEGREE;

	@Parameter(label = "Max. degree", min = "2", max = "100", callback = "flagRestart")
	private int maxDegree = DEF_MAX_DEGREE;
	
	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE,//
			label = HEADER_HTML + "Goodness-of-Fit Criteria:")
	private String HEADER1C;

	@Parameter(label = "R-squared >", min = "0.5", stepSize = "0.01", max = "1")
	private double rSquared = DEF_RSQUARED;

	@Parameter(label = "P-value <", min = "0.0001", stepSize = "0.01", max = "0.05")
	private double pValue = DEF_PVALUE;

//	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = ShollAnalysis.HEADER_HTML
//			+ "<br>Saving:")
//private String HEADER2;
//
//	@Parameter(label = "Auto-save", required = false, callback = "saveChoiceChanged", //
//			choices = { "Do not auto-save files",
//		"Save to image directory (if available)",
//		"Use a common directory, specified below"})
//private String saveChoice = "Do not auto-save files";
//
//	@Parameter(label = "Path", required = false, style=FileWidget.DIRECTORY_STYLE)
//	private File savePath;
//
//	@Parameter(label = "Do not display saved files", required = false, callback = "saveChoiceChanged")
//	private boolean hideSaved = false;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE,//
			label = HEADER_HTML + "<br>Plugin Settings:")
	private String HEADER3;

	@Parameter(label = "Debug mode", callback = "flagRestart")
	private boolean debugMode = DEF_DEBUG_MODE;

	@Parameter(label = "Auto-close dialog", callback = "flagRestart")
	private boolean autoClose = DEF_AUTO_CLOSE;

	@Parameter(label = "Reset All Preferences...", callback = "reset")
	private Button resetPrefs;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE,//
			label = HEADER_HTML +"<br>Help and Resources:")
	private String HEADER4;

	@Parameter(label = "Online resources", required = false, persist = false, callback = "help", //
		choices = { PLACEHOLDER_CHOICE, "ImageJ forum", "Documentation", "Source code (GitHub page)" })
	private String helpChoice = " ";

	@Parameter(label = "About Sholl Analysis...", persist = false, callback = "about")
	private Button about;


	@SuppressWarnings("unused")
	private void init() {
		helper = new Helper(context());
		helper.debug("Prefs successfully initialized");
	}

	@SuppressWarnings("unused")
	private void help() {
		if (PLACEHOLDER_CHOICE.equals(helpChoice)) return; // do nothing
		String url = "";
		if (helpChoice.contains("forum")) url = "https://forum.imagej.net";
		else if (helpChoice.contains("code")) url = "https://github.com/tferr/ASA";
		else url = HELP_URL;
		helpChoice = PLACEHOLDER_CHOICE;
		try {
			platformService.open(new URL(url));
		}
		catch (final IOException e) {
			helper.debug(e);
			helper.error("<HTML><div WIDTH=400>Web page could not be open. " +
				"Please visit " + url + " using your web browser.", null);
		}
	}

	@SuppressWarnings("unused")
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
			"Please restart the plugin for changes to take effect.",
			"New Preferences Set");
	}

	//@Override
	public void reset() {

		final Result result = helper.yesNoPrompt(
			"Reset all preferences to defaults?", "Confirm Reset");
		if (result == Result.NO_OPTION || result == Result.CANCEL_OPTION) return;

		// Reset preferences
		super.reset();
		pService.clear(ShollAnalysis.class);
		pService.clear(ChooseDataset.class);

		// Reset inputs in prompt
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
