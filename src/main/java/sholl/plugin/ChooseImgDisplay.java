
package sholl.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;

import org.scijava.ItemIO;
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
	private ImageDisplayService imgDisplayService;

	@Parameter
	private PrefService prefService;

	@Parameter(label = "New image", persist= false, visibility = ItemVisibility.TRANSIENT)
	private String choice;

	@Parameter(type = ItemIO.OUTPUT)
	private ImageDisplay chosen;

	private List<String> choices;
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
		choices = new ArrayList<>();
		prefService.put(ChooseImgDisplay.class, "choice", "");
		final List<ImageDisplay> list = imgDisplayService.getImageDisplays();
		if (list == null || list.size() < 2) {
			cancel("No other images are open.");
		}
		ImageDisplay activteImgDisplay = imgDisplayService.getActiveImageDisplay(); 
		for (final ImageDisplay imgDisplay : list) {
			if (imgDisplay.equals(activteImgDisplay))
				continue;
			choices.add(imgDisplay.getName());
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
