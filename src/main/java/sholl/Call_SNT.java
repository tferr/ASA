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

import java.awt.AWTEvent;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import ij.IJ;
import ij.Macro;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.WaitForUserDialog;
import ij.plugin.frame.Recorder;
import sholl.gui.EnhancedGenericDialog;
import sholl.gui.Utils;
import tracing.NearPoint;
import tracing.Path;
import tracing.PointInImage;
import tracing.ShollAnalysisDialog;
import tracing.SimpleNeuriteTracer;
import tracing.Simple_Neurite_Tracer;

public class Call_SNT extends Simple_Neurite_Tracer implements DialogListener {

	private static final int START_FIRST_PRIMARY = 0;
	private static final int CENTER_OF_SOMA = 1;
	private static final int START_FIRST_AXON = 2;
	private static final int START_FIRST_DENDRITE = 3;
	private static final int START_FIRST_APICAL_DENDRITE = 4;
	private static final int START_FIRST_CUSTOM = 5;
	private static final int NO_CENTER_CHOICE = 6;
	// NB: Indices of CENTER_CHOICES labels must reflect defined constants
	private static final String[] CENTER_CHOICES = new String[] { "Start of main path", "Center of soma",
			"Start of main path: Axon", "Start of main path: (Basal) Dendrite", "Start of main path: Dendrite",
			"Start of main path: Custom", "Choose manually" };

	private EnhancedGenericDialog gd;
	private int centerChoice;
	private static String imgPath;
	private static String tracesPath;
	private static Label infoMsg;
	private static final String defaultInfoMsg = getDefaultInfoMessage();
	private static final String LOAD_DIRECTORY_KEY = "tracing.Simple_Neurite_Tracer.lastTracesLoadDirectory";
	private static boolean debug = false;

	public static void main(final String[] args) {
		new ij.ImageJ(); // start ImageJ
		final Call_SNT csnt = new Call_SNT();
		csnt.run("");
	}

	private static String getDefaultInfoMessage() {
		if (haveJava3D())
			return "Simple Neurite Tracer v" + SimpleNeuriteTracer.PLUGIN_VERSION + " detected";
		return "Warning: 3D Viewer not available. Please check your installation";
	}

	@Override
	public void run(final String ignoredArgument) {

		if (!showDialog())
			return;
		if (!validImageFile(new File(imgPath)) || !validTracesFile(new File(tracesPath))) {
			IJ.error("Error", "Invalid image or invalid Traces/SWC file\n \n" + imgPath + "\n" + tracesPath);
			return;
		}

		Prefs.set(LOAD_DIRECTORY_KEY, new File(tracesPath).getParent()); //tracesPath cannot be null at this point

		IJ.showStatus("Waiting for SNT to load the data. Please wait...");

		String options = "imagefilename=[" + imgPath + "] tracesfilename=[" + tracesPath + "]";
		if (!single_pane)
			options += " use_three_pane";
		if (haveJava3D() && use3DViewer)
			options += " choice=[Create New 3D Viewer]";
		options += " resampling=1";

		// Set options for current thread before running Simple_Neurite_Tracer.
		// Reset thread's options once we are done
		final Thread thread = Thread.currentThread();
		final String thread_name = thread.getName();
		thread.setName("Run$_call_snt");
		Macro.setOptions(Thread.currentThread(), options);
		super.run("");
		thread.setName(thread_name);
		Macro.setOptions(thread, null);

		if (pathAndFillManager == null) {
			throw new RuntimeException("Connection with Simple Neurite Tracer failed.\n"
					+ "Perhaps specified image was not loaded? If this is a bug please report it at https://forum.imagej.net.\n"
					+ "Specified arguments:\n" + options);
		}

		if (centerChoice == NO_CENTER_CHOICE) {
			helpPrompt("Manual Selection of Center", "");
			return;
		}

		final Path[] primaryPaths = pathAndFillManager.getPathsStructured();
		pathAndFillManager.setSelected(primaryPaths, null);
		PointInImage shollCenter = null;
		if (pathAndFillManager.anySelected()) {
			switch (centerChoice) {
			case START_FIRST_PRIMARY:
				shollCenter = pathAndFillManager.getPath(0).getPointInImage(0);
				break;
			case CENTER_OF_SOMA:
				final ArrayList<PointInImage> somaPoints = new ArrayList<>();
				for (final Path p : primaryPaths) {
					if (p.getSWCType() == Path.SWC_SOMA) {
						for (int i = 0; i < p.size(); i++)
							somaPoints.add(p.getPointInImage(i));
					}
					double sumx = 0, sumy = 0, sumz = 0;
					for (final PointInImage sp : somaPoints) {
						sumx += sp.x;
						sumy += sp.y;
						sumz += sp.z;
					}
					final NearPoint np = pathAndFillManager.nearestPointOnAnyPath(sumx / somaPoints.size(),
							sumy / somaPoints.size(), sumz / somaPoints.size(), this.getStackDiagonalLength());
					if (np != null && np.getPath() != null)
						shollCenter = np.getPath().getPointInImage((np.getPath().size() - 1) / 2);
				}
				break;
			case START_FIRST_AXON:
				shollCenter = getFirstPathPoint(primaryPaths, Path.SWC_AXON);
				break;
			case START_FIRST_DENDRITE:
				shollCenter = getFirstPathPoint(primaryPaths, Path.SWC_DENDRITE);
				break;
			case START_FIRST_APICAL_DENDRITE:
				shollCenter = getFirstPathPoint(primaryPaths, Path.SWC_APICAL_DENDRITE);
				break;
			case START_FIRST_CUSTOM:
				shollCenter = getFirstPathPoint(primaryPaths, Path.SWC_CUSTOM);
				break;
			default:
				IJ.log("[Bug]: Somehow center choice was not understood");
				break;
			}
			if (shollCenter != null) {
				xy_tracer_canvas.getTracerPlugin().clickForTrace(shollCenter.x, shollCenter.y, shollCenter.z, false);
				new ShollAnalysisDialog("Sholl analysis for tracing of " + this.getImagePlus().getTitle(),
						shollCenter.x, shollCenter.y, shollCenter.z, pathAndFillManager, this.getImagePlus());
			} else {
				helpPrompt("Center Point Not Found",
						"No points associated with \"" + CENTER_CHOICES[centerChoice] + "\".");
				return;
			}
		}
	}

	private PointInImage getFirstPathPoint(final Path[] paths, final int swcType) {
		for (final Path p : paths) {
			if (p.getSWCType() == swcType)
				return p.getPointInImage(0);
		}
		return null;
	}

	private void helpPrompt(final String promptTitle, final String mainMsg) {
		String modKey = IJ.isMacOSX() ? "Alt" : "Ctrl";
		modKey += "+Shift";
		final String instructions = "To manually select the focal point:\n"
				+ "    1. Mouse over the path of interest and press \"G\" to activate it\n"
				+ "    2. Press \"" + modKey + "\" to select the point along the path\n"
				+ "    3. Press \"" + modKey + "+A\" to initiate Sholl analysis";
		final WaitForUserDialog wd = new WaitForUserDialog(promptTitle,
				mainMsg + (mainMsg.isEmpty() ? "" : "\n \n") + instructions);
		wd.show();
	}

	private boolean showDialog() {
		if (imgPath == null || tracesPath == null || imgPath.isEmpty() || tracesPath.isEmpty())
			guessInitialPaths();

		gd = new EnhancedGenericDialog("Sholl Analysis v" + Sholl_Utils.version());
		gd.addFileField("Image:", imgPath, 35);
		gd.addFileField("Traces/SWC file:", tracesPath, 35);
		gd.addChoice("Center of analysis", CENTER_CHOICES, CENTER_CHOICES[centerChoice]);
		gd.addCheckbox("Use_three_pane view", !single_pane);
		gd.addCheckbox("Use_3D_viewer", use3DViewer);
		gd.addMessage(defaultInfoMsg);
		infoMsg = (Label) gd.getMessage();
		gd.setInsets(10, 70, 0);
		gd.addCitationMessage();
		gd.assignPopupToHelpButton(createMenu());
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		else if (gd.wasOKed()) {
			sholl.gui.Utils.improveRecording();
			return dialogItemChanged(gd, null);
		}
		return false;
	}

	/** Creates optionsMenu */
	private JPopupMenu createMenu() {
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem mi;
		mi = new JMenuItem(Options.COMMAND_LABEL);
		mi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final Thread newThread = new Thread(new Runnable() {
					@Override
					public void run() {
						if (Recorder.record)
							Recorder.setCommand(Options.COMMAND_LABEL);
						IJ.runPlugIn(Options.class.getName(), Options.SKIP_BITMAP_OPTIONS_LABEL);
						if (Recorder.record)
							Recorder.saveCommand();
					}
				});
				newThread.start();
			}
		});
		popup.add(mi);
		popup.addSeparator();
		mi = Utils.menuItemTrigerringURL("Online Documentation", Sholl_Analysis.URL + "#Traces");
		popup.add(mi);
		mi = sholl.gui.Utils.menuItemTriggeringResources();
		popup.add(mi);
		return popup;
	}

	@Override
	public boolean dialogItemChanged(final GenericDialog arg0, final AWTEvent event) {

		// The 'Browse...' action will call OpenDialog that is macro recordable
		// and will always generate the same (non-unique) command option 'key='.
		// We'll need to nullify such calls or he Recorder will keep complaining
		// every time the 'Browse...' button is pressed
		if (Recorder.record && event != null && event.toString().contains("textfield")) {
			final String commandName = Recorder.getCommand();
			final String commandOptions = Recorder.getCommandOptions();
			if (commandName != null && commandOptions != null && commandOptions.contains("browse")) {
				Recorder.setCommand(commandName);
				return true;
			}
		}

		imgPath = normalizedPath(gd.getNextString());
		tracesPath = normalizedPath(gd.getNextString());
		centerChoice = gd.getNextChoiceIndex();
		single_pane = !gd.getNextBoolean();
		use3DViewer = gd.getNextBoolean();

		boolean enableOK = true;
		String warning = "";
		if (containsIllegalChars(imgPath) || containsIllegalChars(tracesPath)) {
			enableOK = false;
			warning += "File paths contain illegal characters (e.g., ' [ ', ' ] ', ' \" ')";
		} else {
			if (!validImageFile(new File(imgPath))) {
				enableOK = false;
				warning += "Not a valid image. ";
			}
			if (!validTracesFile(new File(tracesPath))) {
				enableOK = false;
				warning += "Not a valid .traces/.swc file";
			}
		}
		if (!warning.isEmpty()) {
			infoMsg.setForeground(Utils.warningColor());
			infoMsg.setText("Error: " + warning);
		} else {
			infoMsg.setForeground(Utils.infoColor());
			infoMsg.setText((imgPath.toLowerCase().endsWith("if")) ? defaultInfoMsg
					: "Warning: Currently some non-TIFF files are not supported");
		}
		return enableOK;
	}

	private String getFilePathWithoutExtension(final String filePath) {
		final int index = filePath.lastIndexOf(".");
		if (index > -1)
			return filePath.substring(0, index);
		return filePath;
	}

	private void guessInitialPaths() {
		final String lastDirPath = Prefs.get(LOAD_DIRECTORY_KEY, null);
		if (lastDirPath != null && !lastDirPath.isEmpty()) {
			final File lastDir = new File(lastDirPath);
			final File[] tracing_files = lastDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(final File file) {
					return !file.isHidden() && tracingsFile(file);
				}
			});
			Arrays.sort(tracing_files);
			if (tracing_files != null && tracing_files.length > 0) {
				tracesPath = tracing_files[0].getAbsolutePath();
				final File[] image_files = lastDir.listFiles(new FileFilter() {
					@Override
					public boolean accept(final File file) {
						return !file.isHidden() && expectedImageFile(file);
					}
				});
				Arrays.sort(image_files);
				if (image_files != null && image_files.length > 0)
					imgPath = image_files[0].getAbsolutePath();
			}
			if (debug && !getFilePathWithoutExtension(imgPath).equals(getFilePathWithoutExtension(tracesPath)))
				IJ.log("Could not pair image to traces file:\n" + imgPath + "\n" + tracesPath);
		}
	}

	private boolean expectedImageFile(final File file) {
		final String[] knownImgExts = new String[] { ".tif", ".tiff" };
		for (final String ext : knownImgExts)
			if (file.getName().toLowerCase().endsWith(ext))
				return true;
		final String[] knownNonImgExts = new String[] { ".txt", ".csv", ".xls", ",xlxs", ".ods", ".md" };
		for (final String ext : knownNonImgExts)
			if (file.getName().toLowerCase().endsWith(ext))
				return false;
		return true;
	}

	private boolean tracingsFile(final File file) {
		final String[] tracingsExts = new String[] { ".traces", ".swc" };
		for (final String ext : tracingsExts)
			if (file.getName().toLowerCase().endsWith(ext))
				return true;
		return false;
	}

	private boolean validFile(final File file) {
		return file != null && file.isFile() && file.exists() && !containsIllegalChars(file.getAbsolutePath());
	}

	private boolean validImageFile(final File file) {
		return validFile(file) && expectedImageFile(file);
	}

	private boolean validTracesFile(final File file) {
		return validFile(file) && tracingsFile(file);
	}

	/*
	 * Since we are stuck with passing macroOptions around we cannot use file
	 * paths containing delimiters used by ij.Macros
	 */
	private boolean containsIllegalChars(final String filePath) {
		return filePath.contains("[") || filePath.contains("]") || filePath.contains("\"");
	}

	/*
	 * FileFields in GenericDialogPlus can retrieve paths with repeated slashes.
	 * We'll need to remove those to avoid I/O errors.
	 */
	private String normalizedPath(final String path) {
		// This is way to simplistic: See
		// chase-seibert.github.io/blog/2009/04/10/java-replaceall-fileseparator.html
		return path.replaceAll(Matcher.quoteReplacement(File.separator) + "+",
				Matcher.quoteReplacement(File.separator));
	}

}
