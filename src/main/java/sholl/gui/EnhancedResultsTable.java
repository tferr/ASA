package sholl.gui;

import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import ij.IJ;
import ij.ImageJ;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.MeasurementsWriter;
import ij.text.TextPanel;
import ij.text.TextWindow;

/** Slight improvements to {@link ij.measure.ResultsTable} */
public class EnhancedResultsTable extends ResultsTable {

	private static boolean unsavedMeasurements;
	private static boolean listenerAdded;

	public EnhancedResultsTable() {
		super();
		listenerAdded = false;
	}

	/**
	 * Calls the default {@code incrementCounter()} while monitoring unsaved
	 * measurements
	 */
	@Override
	public synchronized void incrementCounter() {
		super.incrementCounter();
		unsavedMeasurements = true;
	}

	/**
	 * Calls the default {@code show()} method while attaching a WindowListener
	 * used to prompt users to save unsaved measurements when closing
	 * (non-programmatically) the ResultsTable window
	 *
	 * @param windowTitle
	 *            the title of the window displaying the ResultsTable
	 */
	@Override
	public void show(final String windowTitle) {

		super.show(windowTitle);

		if (listenerAdded)
			return;
		final TextWindow window = getWindow(windowTitle);
		if (window == null)
			return;
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {

				final TextPanel tp = getPanel(windowTitle);
				final int counter = getCounter();
				final int lineCount = tp != null ? tp.getLineCount() : 0;
				final ImageJ ij = IJ.getInstance();
				final boolean macro = (IJ.macroRunning()) || Interpreter.isBatchMode();
				if (counter > 0 && lineCount > 0 && unsavedMeasurements && !macro && ij != null && !ij.quitting()) {
					promptForSave(windowTitle);
				}
			}

		});
		listenerAdded = true;
	}

	private synchronized boolean promptForSave(final String tableTitle) {
		final GenericDialog gd = new GenericDialog("Unsaved Data");
		gd.addMessage("Save measurements in " + tableTitle + "?", new Font("SansSerif", Font.BOLD, 12));
		gd.addMessage("Data will be discarded if you dismiss this prompt!", new Font("SansSerif", Font.PLAIN, 12),
				sholl.gui.Utils.getDisabledComponentColor());
		gd.setCancelLabel("No. Discard measurements");
		gd.setOKLabel("Yes. Save to...");
		gd.showDialog();
		if (gd.wasOKed() && (new MeasurementsWriter()).save("")) {
			unsavedMeasurements = false;
			return true;
		}
		return false;
	}

	TextWindow getWindow(final String title) {
		return (TextWindow) WindowManager.getFrame(title);
	}

	TextPanel getPanel(final String title) {
		final TextWindow window = getWindow(title);
		if (window == null)
			return null;
		return window.getTextPanel();
	}

}
