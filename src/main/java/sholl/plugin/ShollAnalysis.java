package sholl.plugin;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.swing.Timer;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imagej.event.DataDeletedEvent;
import net.imagej.legacy.LegacyService;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable;

import org.scijava.Cancelable;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Interactive;
import org.scijava.convert.ConvertService;
import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.module.MutableModuleItem;
//import org.scijava.options.OptionsService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.thread.ThreadService;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import sholl.Helper;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.ProfileProperties;
import sholl.ShollUtils;
import sholl.UPoint;
import sholl.gui.ShollOverlay;
import sholl.gui.ShollPlot;
import sholl.math.LinearProfileStats;
import sholl.math.NormalizedProfileStats;
import sholl.parsers.ImageParser;
import sholl.parsers.ImageParser2D;
import sholl.parsers.ImageParser3D;

@Plugin(type = Command.class, menu = { @Menu(label = "Analyze"), @Menu(label = "Sholl", weight = 0.01d),
		@Menu(label = "Sholl Analysis (Experimental Version)...") }, initializer = "init")
public class ShollAnalysis extends DynamicCommand implements Interactive, Cancelable {

	@Parameter
	private CommandService cmdService;
	@Parameter
	private ConvertService convertService;
	@Parameter
	private EventService eventService;
	@Parameter
	private ImageDisplayService imageDisplayService;
	@Parameter
	private LegacyService legacyService;
	@Parameter
	private LUTService lutService;
//	@Parameter(visibility = ItemVisibility.INVISIBLE)
//	private OptionsService optionsService;
	@Parameter
	private PrefService prefService;
	@Parameter(visibility = ItemVisibility.INVISIBLE)
	private StatusService statusService;
	@Parameter
	private ThreadService threadService;
	@Parameter
	private UIService uiService;

	/* constants */
	private static final List<String> NORM2D_CHOICES = Arrays.asList("Area", "Perimeter", "Annulus");
	private static final List<String> NORM3D_CHOICES = Arrays.asList("Volume", "Surface area", "Spherical shell");

	private static final String HEADER_HTML = "<html><body><div style='width:160;font-weight:bold;padding-left:0;padding-right:0'>";
	private static final String SUBHEADER_HTML = "<html><body><div style='width:130;font-weight:bold;padding-left:0;padding-right:0'>";
	private static final String EMPTY_LABEL = "<html>&nbsp;";
	private static final String REFRESH_BUTTON_COLOR = "#000080";
	private static final int MAX_SPANS = 10;

	private static final String NO_IMAGE = "Image no longer available";
	private static final String NO_CENTER = "Invalid center";
	private static final String NO_RADII = "Invalid radii";
	private static final String NO_THRESHOLD = "Invalid threshold levels";
	private static final String NO_ROI = "No ROI detected";
	private static final String RUNNING = "Analysis currently running";
	private static final int SCOPE_IMP = 0;
	private static final int SCOPE_PROFILE = 1;
	private static final int SCOPE_ABORT = 2;
	private static final int SCOPE_CHANGE_DATASET = 3;

	/* Parameters */
	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = HEADER_HTML + "Shells:")
	private String HEADER1;

	@Parameter(label = "Starting radius", required = false, callback = "startRadiusStepSizeChanged", min = "0",style = NumberWidget.SCROLL_BAR_STYLE)
	private double startRadius;

	@Parameter(label = "Radius step size", required = false, callback = "startRadiusStepSizeChanged", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private double stepSize;

	@Parameter(label = "Ending radius", persist = false, required = false, callback = "endRadiusChanged", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private double endRadius;

	@Parameter(label = "Hemishells", required = false, callback = "overlayShells", choices = { "None. Use full shells",
			"Above center", "Below center", "Left of center", "Right of center" })
	private String hemiShellChoice;

	@Parameter(label = "Set Center from Active ROI", callback = "setCenterFromROI", persist = false)
	private Button centerButton;

	@Parameter(label = "Preview", persist = false, callback = "overlayShells")
	private boolean previewShells;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = HEADER_HTML
			+ "<br>Segmentation:")
	private String HEADER2;

	@Parameter(label = "Ignore isolated voxels")
	private boolean ignoreIsolatedVoxels;

	@Parameter(label = "# samples per radius", callback = "nSpansChanged", min = "1", max = ""
			+ MAX_SPANS, style = NumberWidget.SCROLL_BAR_STYLE)
	private int nSpans;

	@Parameter(label = "Integration", callback = "nSpansIntChoiceChanged", choices = { "N/A", "Mean", "Median",
			"Mode" })
	private String nSpansIntChoice;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = HEADER_HTML
			+ "<br>Metrics:")
	private String HEADER3;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = SUBHEADER_HTML
			+ "Linear Profile:")
	private String HEADER3A;

	@Parameter(label = "Polynomial fit", callback = "polynomialChoiceChanged", required = false, choices = {
			"None. Skip curve fitting", "'Best fitting' degree (2nd-20th)", "Use degree specified below:" })
	private String polynomialChoice;

	@Parameter(label = "<html>&nbsp;", callback = "polynomialDegreeChanged", min = "0", max = "20", style = NumberWidget.SLIDER_STYLE)
	private int polynomialDegree;

	@Parameter(label = "Primary branches", callback = "primaryBranchesChoiceChanged", choices = {
			"Infer from starting radius", "Infer from multipoint ROI", "Use no. specified below:" })
	private String primaryBranchesChoice;

	@Parameter(label = EMPTY_LABEL, callback = "primaryBranchesChanged", min = "-1", max = "100", style = NumberWidget.SCROLL_BAR_STYLE)
	private double primaryBranches;

	@Parameter(label = "Enclosing radius cuttoff", min = "1", max = "100", style = NumberWidget.SCROLL_BAR_STYLE)
	private double enclosingCutoff;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = SUBHEADER_HTML
			+ "<br>Normalized Profile:")
	private String HEADER3B;

	@Parameter(label = "Method", choices = { "Automatically choose", "Semi-Log", "Log-log" })
	private String normalizationMethodDescription;

	@Parameter(label = "Normalizer", callback = "normalizerDescriptionChanged")
	private String normalizerDescription;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, label = HEADER_HTML
			+ "<br>Output:")
	private String HEADER4;

	@Parameter(label = "Plots", choices = { "Linear plot", "Normalized plot", "Linear & normalized plots",
			"None. Show no plots" })
	private String plotOutputDescription;

	@Parameter(label = "Tables", choices = { "Detailed table", "Summary table",
		"Detailed & summary tables", "None. Show no tables" })
	private String tableOutputDescription;

	@Parameter(label = "Annotations", callback = "annotationsDescriptionChanged",
		choices = { "ROIs (Sholl points only)", "ROIs (points and 2D shells)",
			"ROIs and mask", "None. Show no annotations" })
	private String annotationsDescription;

	@Parameter(label = "Annotations LUT", persist = false, callback = "lutChoiceChanged")
	private String lutChoice;

	@Parameter(required = false, label = EMPTY_LABEL, persist = false)
	private ColorTable lutTable;

	@Parameter(persist = false, required = false, visibility = ItemVisibility.MESSAGE, //
			label = HEADER_HTML + "<br>Run:")
	private String SPACER;

	// @Parameter(label = "Debug mode", required = false, visibility =
	// ItemVisibility.INVISIBLE)
	// private boolean verbose;

	// @Parameter(label = "More Options...", callback = "optionsButtonAction")
	// private Button test;

	@Parameter(label = "Action", required = false, style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, //
			visibility = ItemVisibility.TRANSIENT, //
			callback = "setAnalysisScope", choices = { "Analyze image", "Re-analyze parsed data",
					"Abort current analysis", "Change image..." })
	private String analysisAction;

	@Parameter(label = "<html><b>Analyze Image", callback = "runAnalysis")
	private Button analyzeButton;

	/* Instance variables */
	private Dataset dataset;
	private Helper helper;
	private PreviewOverlay previewOverlay;
	private Map<String, URL> luts;
	private ImagePlus imp;
	private ImageParser parser;
	private Overlay overlaySnapshot;
	private Calibration cal;
	private UPoint center;
	private double voxelSize;
	private boolean twoD;
	private double maxPossibleRadius;
	private int posC;
	private int posT;
	private double upperT;
	private double lowerT;
	private Thread analysisThread;
	private AnalysisRunner analysisRunner;
	private Profile profile;
	private int scope;

	@EventHandler
	public void onEvent(final DataDeletedEvent evt) {
		if (evt.getObject().equals(dataset)) {
			imp = null;
			cancel(NO_IMAGE);
		}
	}

	@Override
	public void run() {
		// Do nothing. Actually analysis is performed by runAnalysis();
	}

	/*
	 * Triggered every time user interacts with prompt (NB: buttons in the
	 * prompt are excluded from this
	 */
	@Override
	public void preview() {
		if (imp == null) {
			cancelAndFreezeUI(NO_IMAGE);
		} else if (dataset != imageDisplayService.getActiveDataset()) {
			imp.getWindow().requestFocus(); //FIXME: Only works on legacy mode
		}
	}

	private boolean validRequirements() {
		String cancelReason = "";
		if (imp == null) {
			cancelReason = NO_IMAGE;
		} else if (ongoingAnalysis()) {
			cancelReason = RUNNING;
		} else {
			cancelReason = validateRequirements(true);
		}
		final boolean successfullCheck = cancelReason.isEmpty();
		if (!successfullCheck)
			cancelAndFreezeUI(cancelReason);
		return successfullCheck;
	}

	private boolean ongoingAnalysis() {
		return analysisThread != null && analysisThread.isAlive();
	}

	protected void runAnalysis() throws InterruptedException {
		switch (scope) {
		case SCOPE_IMP:
			if (!validRequirements())
				return;
			updateHyperStackPosition(); // Did channel/frame changed?
			previewShells = false;
			imp.setOverlay(overlaySnapshot);
			parser.reset();
			startAnalysisThread(false);
			break;
		case SCOPE_PROFILE:
			if (!validProfileExists())
				return;
			startAnalysisThread(true);
			break;
		case SCOPE_ABORT:
			if (analysisRunner == null || !ongoingAnalysis())
				return;
			statusService.showStatus("Aborting analysis...");
			analysisRunner.terminate();
			helper.log("Analysis aborted...");
			break;
		case SCOPE_CHANGE_DATASET:
			threadService.newThread(new Runnable() {
				@Override
				public void run() {
					getNewDataset();
				}
			}).start();
			break;
		default:
			throw new IllegalArgumentException("Unrecognized option: " + scope);
		}
	}

	private void getNewDataset() {

		try {
			Future<CommandModule> cmdModule = cmdService.run(ChooseImgDisplay.class, true);
			cmdModule.get();
			// FIXME: this throws a ClassCastException. not sure why
			//ImageDisplay imgDisplay = (ImageDisplay) cmdModule.get().getOutput("chosen");
		} catch (InterruptedException | ExecutionException | ClassCastException exc) {
			exc.printStackTrace();
		}

		final String result = prefService.get(ChooseImgDisplay.class, "choice");
		if (result.isEmpty()) {
			return; // ChooseImgDisplay canceled / not initialized
		}
		ImageDisplay newImgDisplay = null;
		for (final ImageDisplay imgDisplay : imageDisplayService .getImageDisplays()) {
			if (result.equals(imgDisplay.getName())) {
				newImgDisplay = imgDisplay;
				break;
			}
		}
		if (newImgDisplay == null) {
			helper.error("Could not retrieve new dataset", null);
			helper.log("Failed to change dataset");
			return;
		}
		final ImagePlus newImp = convertService.convert(newImgDisplay, ImagePlus.class);
		if (twoD != (newImp.getNSlices() == 1)) {
			helper.error("Z-dimension of new dataset differs which requires a reset.\n" +
				"Please restart the command to analyze " + newImp.getTitle(),
				"Not a Suitable Choice");
			return;
		}
		loadDataset(newImp);
		preview(); // activate new image
		helper.infoMsg("Target image is now " + newImp.getTitle(), null);
		helper.log("Changed scope of analysis to: " + newImp.getTitle());
	}

	private void startAnalysisThread(final boolean skipImageParsing) {
		analysisRunner = new AnalysisRunner(parser);
		analysisRunner.setSkipParsing(skipImageParsing);
		statusService.showStatus("Analysis started");
		helper.log("Analysis started...");
		analysisThread = threadService.newThread(analysisRunner);
		analysisThread.start();
	}

	private boolean validProfileExists() {
		return getProfile() != null && !getProfile().isEmpty();
	}

	private NormalizedProfileStats getNormalizedProfileStats(final Profile profile) {
		final int normFlag = NormalizedProfileStats.getNormalizerFlag(normalizerDescription);
		final int methodFlag = NormalizedProfileStats.getMethodFlag(normalizationMethodDescription);
		return new NormalizedProfileStats(profile, normFlag, methodFlag);
	}

	protected void setProfile(final Profile profile) {
		this.profile = profile;
	}

	protected Profile getProfile() {
		return profile;
	}

	/* initializer method running before displaying prompt */
	protected void init() {
		helper = new Helper(context());
		headsupWarning();
		imp = legacyService.getImageMap().lookupImagePlus(imageDisplayService.getActiveImageDisplay());
		if (imp == null)
			imp = getDemoImp();
		if (imp == null) {
			helper.error("A dataset is required but none was found", null);
			cancel(null);
			return;
		}
		getInfo().setLabel("Sholl Analysis " + ShollUtils.version());
		previewOverlay = new PreviewOverlay();
		legacyService.syncActiveImage();
		setLUTs();
		loadDataset(imp);
		adjustSamplingOptions();
		setNormalizerChoices();
	}

	private void headsupWarning() {
		helper.infoMsg("<HTML><div WIDTH=480><p>"
				+ "This is an experimental version of the new version of the Sholl plugin for ImageJ2 "
				+ "(Development of IJ1 version is now stopped). This version is focused on sampling "
				+ "accuracy, extended metrics and scriptability. Several other refinements should "
				+ "also be noticeblable (e.g., parsing of 3D stacks is now much faster).</p>"
				+ "<p>Please report any bugs you find (in the ImageJ Forum or through GitHub) "
				+ "and keeep in mind this is still a work in progress.</p></div></HTML>", "Warning");
	}

	private void loadDataset(final ImagePlus imp) {
		this.imp = imp;
		dataset = convertService.convert(imp, Dataset.class);
		twoD = dataset.getDepth() == 1;
		posC = imp.getC();
		posT = imp.getFrame();
		cal = imp.getCalibration();
		overlaySnapshot = imp.getOverlay();
		initializeParser();
		voxelSize = parser.getIsotropicVoxelSize();
		adjustRadiiInputs(true);
		center = getCenterFromROI(true);
	}

	private void initializeParser() {
		parser = (twoD) ? new ImageParser2D(imp, context()) : new ImageParser3D(imp, context());
	}

	private void adjustRadiiInputs(final boolean startUpAdjust) {
		maxPossibleRadius = parser.maxPossibleRadius();
		final List<String> names = Arrays.asList("startRadius", "stepSize", "endRadius");
		final List<String> labels = Arrays.asList("Start radius", "Step size", "End radius");
		final String unit = cal.getUnit();
		for (int i = 0; i < names.size(); i++) {
			final MutableModuleItem<Double> mItem = getInfo().getMutableInput(names.get(i), Double.class);
			mItem.setMaximumValue(maxPossibleRadius);
			if (startUpAdjust) {
				mItem.setStepSize(voxelSize);
				mItem.setLabel(labels.get(i) + " (" + unit + ")");
			}
		}
	}

	private void adjustSamplingOptions() {
		if (twoD) {
			final MutableModuleItem<Boolean> ignoreIsolatedVoxelsInput = getInfo()
					.getMutableInput("ignoreIsolatedVoxels", Boolean.class);
			removeInput(ignoreIsolatedVoxelsInput);
		} else {
			final MutableModuleItem<Integer> nSpansInput = getInfo().getMutableInput("nSpans", Integer.class);
			removeInput(nSpansInput);
			final MutableModuleItem<String> nSpansIntChoiceInput = getInfo().getMutableInput("nSpansIntChoice",
					String.class);
			removeInput(nSpansIntChoiceInput);
		}

	}

	protected void setAnalysisScope() {
		final MutableModuleItem<Button> aButton = getInfo().getMutableInput("analyzeButton", Button.class);
		String label;
		boolean disable = false;
		if (analysisAction.contains("Analyze image")) {
			scope = SCOPE_IMP;
			if (imp != null) {
				final String title = imp.getTitle().substring(0, Math.min(imp.getTitle().length(), 40));
				label = "Analyze " + title;
			} else {
				label = NO_IMAGE;
				disable = true;
			}
		} else if (analysisAction.contains("Abort")) {
			scope = SCOPE_ABORT;
			if (ongoingAnalysis()) {
				label = "Press to abort";
			} else {
				label = "No analysis is currently running";
				disable = true;
			}
		} else if (analysisAction.contains("parsed")) {
			scope = SCOPE_PROFILE;
			if (validProfileExists()) {
				label = "Press to re-run analysis";
			} else {
				label = "No profile has yet been obtained";
				disable = true;
			}
		} else if (analysisAction.contains("Change")) {
			scope = SCOPE_CHANGE_DATASET;
			label = "Choose new image";
		} else
			label = analysisAction;
		aButton.setLabel(
				String.format("<html><font color='%s'><b>%s</b></font></html>", disable ? "#555555" : "#000", label));
	}

	// private void adjustSegmentationOptions() {
	// if (imp.getProcessor().isBinary()) {
	// for (final String name : Arrays.asList("lowerT", "upperT")) {
	// final MutableModuleItem<Double> mItem = getInfo().getMutableInput(name,
	// Double.class);
	// removeInput(mItem);
	// }
	// final MutableModuleItem<Button> mItem =
	// getInfo().getMutableInput("thresholdButton", Button.class);
	// removeInput(mItem);
	// } else {
	// readThresholdFromImp();
	// setThresholdLimits();
	// }
	// }

	private void setNormalizerChoices() {
		final List<String> choices = (twoD) ? NORM2D_CHOICES : NORM3D_CHOICES;
		final MutableModuleItem<String> mItem = getInfo().getMutableInput("normalizerDescription", String.class);
		mItem.setChoices((twoD) ? NORM2D_CHOICES : NORM3D_CHOICES);
		mItem.setValue(this, choices.get(0));
	}

	private boolean validRadiiOptions() {
		return (!Double.isNaN(startRadius) && !Double.isNaN(stepSize) && !Double
			.isNaN(endRadius) && endRadius > startRadius);
	}

	private UPoint getCenterFromROI(final boolean setEndRadius) {
		final Roi roi = imp.getRoi();
		if (roi == null)
			return null;
		if (roi.getType() == Roi.LINE) {
			final Line line = (Line) roi;
			if (setEndRadius)
				endRadius = line.getLength();
			return new UPoint(line.x1, line.y1, imp.getZ(), cal);
		}
		if (roi.getType() == Roi.POINT) {
			final Rectangle rect = roi.getBounds();
			return new UPoint(rect.x, rect.y, imp.getZ(), cal);
		}
		if (setEndRadius)
				endRadius = roi.getFeretsDiameter() / 2;
		final double[] ctd = roi.getContourCentroid();
		return new UPoint((int) Math.round(ctd[0]), (int) Math.round(ctd[1]), imp
			.getZ(), cal);
	}

	protected boolean updateHyperStackPosition() {
		if (imp == null)
			return false;
		final boolean posChanged = imp.getC() != posC || imp.getFrame() != posT;
		if (!posChanged)
			return false;
		final String oldPosition = "Channel " + posC + ", Frame " + posT;
		final String newPosition = "Channel " + imp.getC() + ", Frame " + imp.getFrame();
		final String msg = "Scope of analysis is currently %s.\nUpdate scope to active position (%s)?";
		final Result result = helper.yesNoPrompt(String.format(msg, oldPosition, newPosition),
				"Dataset Position Changed");
		if (result == Result.YES_OPTION) {
			posC = imp.getC();
			posT = imp.getFrame();
			initializeParser();// call parser.setPosition();
			readThresholdFromImp();
			return true;
		}
		return false;
	}

	protected void setCenterFromROI() {
		if (imp == null) {
			cancelAndFreezeUI(NO_IMAGE);
			return;
		}
		final UPoint newCenter = getCenterFromROI(false);
		if (newCenter == null) {
			cancelAndFreezeUI(NO_ROI);
			return;
		}
		if (center != null && center.equals(newCenter)) {
			helper.error("ROI already defines the same center currently in use\n(" + centerDescription()
					+ "). No changes were made.", "Center Already Defined");
			return;
		}
		if (center != null && newCenter.z != center.z) {
			final Result result = helper.yesNoPrompt(
					String.format("Current center was set at Z-position %s.\n" + "Move center to active Z-position %s?",
							ShollUtils.d2s(center.z), ShollUtils.d2s(newCenter.z)),
					"Z-Position Changed");
			if (result == Result.NO_OPTION)
				newCenter.z = center.z;
		}
		center = newCenter;
		if (!previewShells) {
			helper.infoMsg("New center set to " + centerDescription(), "Center Updated");
		}
		overlayShells();
	}

	private String centerDescription() {
		final StringBuilder sb = new StringBuilder();
		sb.append("X=").append(ShollUtils.d2s(center.x));
		sb.append(" Y=").append(ShollUtils.d2s(center.y));
		if (!twoD)
			sb.append(" Z=").append(ShollUtils.d2s(center.z));
		if (imp.getNChannels() > 1)
			sb.append(" C=").append(posC);
		if (imp.getNFrames() > 1)
			sb.append(" T=").append(posT);
		return sb.toString();
	}

	private void cancelAndFreezeUI(final String cancelReason) {
		String uiMsg;
		switch (cancelReason) {
		case NO_CENTER:
			uiMsg = "Please set an ROI, then press \"" + "Set New Center from Active ROI\".\n"
					+ "Center coordinates will be defined by the ROI's centroid.";
			break;
		case NO_RADII:
			uiMsg = "Ending radius and Radius step size must be within range.";
			break;
		case NO_THRESHOLD:
			uiMsg = "Image is not segmented. Please adjust threshold levels.";
			break;
		case RUNNING:
			uiMsg = "An analysis is currently running. Please wait...";
			break;
		default:
			if (cancelReason.contains(",")) {
				uiMsg = "Image cannot be analyzed. Muliple invalid requirements:\n- "
						+ cancelReason.replace(", ", "\n- ");
			} else {
				uiMsg = cancelReason;
			}
			break;
		}
		cancel(cancelReason);
		// previewShells = false;
		helper.errorPrompt(uiMsg + ".", null);
	}

	protected void thresholdButtonAction() {
		final double prevLowerT = lowerT;
		final double prevupperT = upperT;
		if (!readThresholdFromImp()) {
			final Result result = helper.yesNoPrompt("Image is not thresholded. Open threshold widget?", null);
			if (result == Result.YES_OPTION)
				legacyService.runLegacyCommand(ij.plugin.frame.ThresholdAdjuster.class.getName(), "");
			return;
		}
		String label = "Threshold values updated...";
		if (prevLowerT == lowerT && prevupperT == upperT)
			label = "Values are the same. No changes.";
		else if (prevLowerT == lowerT && prevupperT != upperT)
			label = "Upper threshold updated...";
		else if (prevLowerT != lowerT && prevupperT == upperT)
			label = "Lower threshold updated...";
		toggleButtonLabel("thresholdButton", label, REFRESH_BUTTON_COLOR, true);
	}

	private boolean readThresholdFromImp() {
		boolean successfulRead = true;
		final double minT = imp.getProcessor().getMinThreshold();
		final double maxT = imp.getProcessor().getMaxThreshold();
		if (imp.getProcessor().isBinary()) {
			lowerT = 1;
			upperT = 255;
		} else if (imp.isThreshold()) {
			lowerT = minT;
			upperT = maxT;
		} else {
			successfulRead = false;
		}
		return successfulRead;
	}

	private ImagePlus getDemoImp() {
		ImagePlus imp = null;
		final Result result = helper.yesNoPrompt("No images are currently open. Run plugin on demo image?", null);
		if (result == Result.YES_OPTION) {
			imp = ShollUtils.sampleImage();
			if (imp == null)
				helper.error("Demo image could not be loaded.", null);
			else {
				imp.show(); // uiService.show(imp);
			}
		}
		return imp;
	}

	private void toggleButtonLabel(final String buttonName, final String newLabel, final String color,
			final boolean temporarily) {
		final MutableModuleItem<Button> input = getInfo().getMutableInput(buttonName, Button.class);
		final String oldLabel = input.getLabel();
		input.setLabel(String.format("<html><font color='%s'>%s</font></html>", color, newLabel));
		if (temporarily) {
			final Timer timer = new Timer(5000, new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					toggleButtonLabel(buttonName, oldLabel, color, false);
				}
			});
			timer.setRepeats(false);
			timer.start();
		}
		input.getInfo().update(eventService); // force UI update (?)
	}

	private void setLUTs() {
		// see net.imagej.lut.LUTSelector
		luts = lutService.findLUTs();
		final ArrayList<String> choices = new ArrayList<>();
		for (final Map.Entry<String, URL> entry : luts.entrySet()) {
			choices.add(entry.getKey());
		}
		Collections.sort(choices);
		choices.add(0, "No LUT. Use active ROI color");
		final MutableModuleItem<String> input = getInfo().getMutableInput("lutChoice", String.class);
		input.setChoices(choices);
		input.setValue(this, choices.get(0));
		lutChoiceChanged();
	}

	private double adjustedStepSize() {
		return Math.max(stepSize, voxelSize);
	}

	/* callbacks */
	protected void startRadiusStepSizeChanged() {
		if (startRadius > endRadius || stepSize > endRadius)
			endRadius = Math.min(endRadius + adjustedStepSize(), maxPossibleRadius);
		previewShells = previewShells && validRadiiOptions();
		overlayShells();
	}

	protected void endRadiusChanged() {
		if (endRadius < startRadius + stepSize)
			startRadius = Math.max(endRadius - adjustedStepSize(), 0);
		previewShells = previewShells && validRadiiOptions();
		overlayShells();
	}

	protected void nSpansChanged() {
		nSpansIntChoice = (nSpans == 1) ? "N/A" : "Mean";
		if (previewShells)
			overlayShells();
	}

	protected void nSpansIntChoiceChanged() {
		int nSpansBefore = nSpans;
		if (nSpansIntChoice.contains("N/A"))
			nSpans = 1;
		else if (nSpans == 1)
			nSpans++;
		if (previewShells && nSpansBefore != nSpans)
			overlayShells();
	}

	protected void polynomialChoiceChanged() {
		if (!polynomialChoice.contains("specified")) {
			polynomialDegree = 0;
		} else if (polynomialDegree == 0) {
			polynomialDegree = 10;
		}
	}

	protected void normalizerDescriptionChanged() {
		if (stepSize < voxelSize
				&& (normalizerDescription.contains("Annulus") || normalizerDescription.contains("shell"))) {
			helper.error(normalizerDescription + " normalization requires radius step size to be ≥ "
					+ ShollUtils.d2s(voxelSize) + cal.getUnit(), null);
			normalizerDescription = (twoD) ? NORM2D_CHOICES.get(0) : NORM3D_CHOICES.get(0);
		}
	}

	protected void annotationsDescriptionChanged() {
		if (annotationsDescription.contains("None")) {
			lutChoice = "No LUT. Use active ROI color";
			lutChoiceChanged();
		}
	}

	protected void polynomialDegreeChanged() {
		if (polynomialDegree == 0)
			polynomialChoice = "'Best fitting' degree (2nd-20th)";
		else
			polynomialChoice = "Use degree specified below:";
	}

	protected void primaryBranchesChoiceChanged() {
		if (primaryBranchesChoice.contains("starting radius")) {
			primaryBranches = -1;
		} else if (primaryBranchesChoice.contains("multipoint") && imp != null) {
			final Roi roi = imp.getRoi();
			if (roi == null || roi.getType() != Roi.POINT) {
				helper.error("Please activate a multipoint ROI marking primary branches.", "No Multipoint ROI Exists");
				primaryBranchesChoice = "Infer from starting radius";
				primaryBranches = -1;
				return;
			}
			final PointRoi point = (PointRoi) roi;
			primaryBranches = point.getCount(point.getCounter());
		} else if (primaryBranches == -1)
			primaryBranches = 1;
	}

	protected void primaryBranchesChanged() {
		if (primaryBranches == -1)
			primaryBranchesChoice = "Infer from starting radius";
		else
			primaryBranchesChoice = "Use no. specified below:";
	}

	protected void lutChoiceChanged() {
		try {
			lutTable = lutService.loadLUT(luts.get(lutChoice));
		} catch (final Exception ignored) {
			// presumably "No Lut" was chosen by user
			lutTable = ShollUtils.constantLUT(Roi.getColor());
		}
	}

	protected void overlayShells() {
		if (imp == null)
			return;
		previewShells = previewShells && validRadii();
		threadService.newThread(previewOverlay).start();
	}

	private boolean validRadii() {
		final String reasonToInvalidateRaddi = validateRequirements(false);
		final boolean validRadii = reasonToInvalidateRaddi.isEmpty();
		if (!validRadii)
			cancelAndFreezeUI(reasonToInvalidateRaddi);
		return validRadii;
	}

	private String validateRequirements(final boolean includeThresholdCheck) {
		final List<String> cancelReasons = new ArrayList<>();
		if (center == null)
			cancelReasons.add(NO_CENTER);
		if (!validRadiiOptions())
			cancelReasons.add(NO_RADII);
		if (!includeThresholdCheck)
			return String.join(", ", cancelReasons);
		if (!readThresholdFromImp())
			cancelReasons.add(NO_THRESHOLD);
		return String.join(", ", cancelReasons);
	}

	protected void optionsButtonAction() {
		legacyService.runLegacyCommand(sholl.Options.class.getName(), "");
	}

	/** Private classes **/
	class AnalysisRunner implements Runnable {

		private final ImageParser parser;
		private boolean skipParsing;

		public AnalysisRunner(final ImageParser parser) {
			this.parser = parser;
			parser.setCenter(center.x, center.y, center.z);
			parser.setRadii(startRadius, adjustedStepSize(), endRadius);
			parser.setHemiShells(hemiShellChoice);
			parser.setThreshold(lowerT, upperT);
		}

		public void setSkipParsing(final boolean skipParsing) {
			this.skipParsing = skipParsing;
		}

		public void terminate() {
			parser.terminate();
			statusService.showProgress(0, 0);
		}

		@Override
		public void run() {
			if (!validOutput()) return;

			if (!skipParsing) {
				parser.parse();
				if (!parser.successful()) {
					helper.error("No valid profile retrieved.", null);
					return;
				}
			}
			final Profile profile = parser.getProfile();

			// Linear profile stats
			final LinearProfileStats lStats = new LinearProfileStats(profile);
			if (plotOutputDescription.contains("Linear")) {
				if (polynomialChoice.contains("Best"))
					lStats.findBestFit(2, 20, 0.8, 0.05);
				else if (polynomialChoice.contains("degree") && polynomialDegree > 1)
					lStats.fitPolynomial(polynomialDegree);
			}
			helper.log("Enclosing radius: " + lStats.getEnclosingRadius(enclosingCutoff));

			/// Normalized profile stats
			final NormalizedProfileStats nStats = getNormalizedProfileStats(profile);
			helper.log("Sholl decay: " + nStats.getShollDecay());

			// Set ROIs
			if (!annotationsDescription.contains("None")) {
				final ShollOverlay sOverlay = new ShollOverlay(profile, imp, true);
				sOverlay.addCenter();
				if (annotationsDescription.contains("shells"))
					sOverlay.setShellsLUT(lutTable, ShollOverlay.COUNT);
				sOverlay.setPointsLUT(lutTable, ShollOverlay.COUNT);
				sOverlay.updateDisplay();
				overlaySnapshot = imp.getOverlay();
				if (annotationsDescription.contains("mask")) showMask();
			}

			// Set Plots
			if (plotOutputDescription.toLowerCase().contains("linear")) {
				final ShollPlot lPlot = lStats.getPlot();
				lPlot.show();
			}
			if (plotOutputDescription.toLowerCase().contains("normalized")) {
				final ShollPlot nPlot = nStats.getPlot();
				nPlot.show();
			}

			// TODO: implement tables
			helper.log("Tables are not yet implemented");
			setProfile(profile);

		}

		private void showMask() {
			final ImageDisplay imgDisplay = convertService.convert(parser.getMask(),
				ImageDisplay.class);
			lutService.applyLUT(lutTable, imgDisplay);
			uiService.show(imgDisplay);
		}

		private boolean validOutput() {
			boolean noOutput = plotOutputDescription.contains("None");
			noOutput = noOutput && tableOutputDescription.contains("None");
			noOutput = noOutput && annotationsDescription.contains("None");
			if (noOutput) {
				helper.error("Analysis can only proceed if at least one type\n" +
					"of output (plot, table, annotation) is chosen.", "No Valid Output");
			}
			return !noOutput;
		}

	}

	private class PreviewOverlay implements Runnable {
		@Override
		public void run() {
			if (!previewShells) {
				ShollOverlay.remove(overlaySnapshot, "temp");
				imp.setOverlay(overlaySnapshot);
				return;
			}
			try {
				overlaySnapshot = imp.getOverlay();
				final ArrayList<Double> radii = ShollUtils.getRadii(startRadius, adjustedStepSize(), endRadius);
				final Profile profile = new Profile();
				profile.assignImage(imp);
				for (final double r : radii)
					profile.add(new ProfileEntry(r, 0));
				profile.setCenter(center);
				profile.getProperties().setProperty(ProfileProperties.KEY_HEMISHELLS,
						ShollUtils.extractHemiShellFlag(hemiShellChoice));
				final ShollOverlay so = new ShollOverlay(profile);
				so.setShellsThickness(nSpans);
				so.setShellsColor(Roi.getColor());
				// OptionsOverlay optOv =
				// optionsService.getOptions(OptionsOverlay.class);
				// Color color = AWTColors.getColor(optOv.getFillColor());
				// so.setShellsColor(color);
				so.addCenter();
				so.assignProperty("temp");
				imp.setOverlay(so.getOverlay());
			} catch (final IllegalArgumentException ignored) {
				return; // invalid parameters: do nothing
			}
		}
	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ShollAnalysis.class, true);
	}
}
