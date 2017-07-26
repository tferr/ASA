package sholl.plugin;

import net.imagej.ImageJ;

import org.scijava.InstantiableException;
import org.scijava.command.Command;
import org.scijava.module.MethodCallException;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;

/** DatasetChooser */
@Plugin(type = Command.class, visible = true, initializer = "init", label = "Choose New Dataset")
public class DatasetChooser implements Command { // extends ModuleCommand

	@Parameter(label = "New image")
	private ImagePlus imp1;

	@Parameter(label = MSG, required = false)
	private ImagePlus imp2;

	private static final String MSG = "<html>Please ignore this second drop-down menu.<br>"
			+ "It is just a UI glitch that is always ignored";

	@Override
	public void run() {
		// Nothing to do
	}

	protected void init() {
		// If we were extending ModuleCommand
		// resolveInput("imp2");
	}

	public static void main(final String... args) throws InstantiableException, MethodCallException {
		final ImageJ ij = net.imagej.Main.launch(args);
		ij.command().run(DatasetChooser.class, true);
	}

	// @Override
	// public Context context() {
	// // TODO Auto-generated method stub
	// return null;
	// }

}
