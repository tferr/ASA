
package sholl.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

/** ChooseImgDisplay */
@Plugin(initializer = "init", type = Command.class, visible = false, label = "Choose New Dataset")
public class ChooseImgDisplay extends DynamicCommand implements Command {

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private PrefService prefService;

	@Parameter(label = "New dataset", persist= false, visibility = ItemVisibility.TRANSIENT)
	private String choice;

	@Parameter(required=false, persist= false, visibility = ItemVisibility.TRANSIENT)
	private Dataset datasetToIgnore;

//	@Parameter(type = ItemIO.OUTPUT)
//	private ImageDisplay chosen;

//	private HashMap<String, ImageDisplay> map;

	@Override
	public void run() {
		prefService.put(ChooseImgDisplay.class, "choice", choice);
//		chosen = map.get(choice);
	}

	protected void init() {
//		map = new HashMap<>();
//		final List<ImageDisplay> list = imgDisplayService.getImageDisplays();
//		if (list == null || list.size() < 2) {
//			cancel("No other images are open.");
//		}
//		for (final ImageDisplay imgDisplay : list) {
//			map.put(imgDisplay.getName(), imgDisplay);
//		}
		final List<String> choices = new ArrayList<>();
		prefService.put(ChooseImgDisplay.class, "choice", ""); // reset pref
		final List<Dataset> list = datasetService.getDatasets();
		if (list == null || list.size() < 2) {
			cancel("No other images are open.");
		}
		for (final Dataset dataset : list) {
			if (dataset.equals(datasetToIgnore))
				continue;
			choices.add(dataset.getName());
		}
		Collections.sort(choices);
		final MutableModuleItem<String> mItem = getInfo().getMutableInput("choice",
			String.class);
		mItem.setChoices(choices);
		// mItem.setValue(this, choices.get(0));
	}

	public static void main(final String... args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(ChooseImgDisplay.class, true);
	}

}
