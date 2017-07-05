package sholl;

import net.imagej.legacy.LegacyService;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;
import org.scijava.util.VersionUtils;

public class Helper {

	@Parameter
	private Context context;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService uiService;

	private static String VERSION;

	public Helper() {
		this(new Context(LegacyService.class, PrefService.class, LogService.class, StatusService.class,
				UIService.class));
	}

	public Helper(final Context context) {
		context.inject(this);
		VERSION = VersionUtils.getVersion(sholl.Helper.class);
	}

	public void error(final String string) {
		uiService.showDialog(string, "Sholl v" + VERSION, MessageType.ERROR_MESSAGE);
	}

	public void log(final String string) {
		logService.info("[Sholl] " + string);
	}

	public void clear() {
		statusService.clearStatus();
		statusService.showProgress(0, 0);
	}

	public void warn(final String string) {
		logService.warn("[Sholl] " + string);
	}

	public void log(final String... strings) {
		if (strings != null)
			log(String.join(" ", strings));
	}

	public StatusService getStatusService() {
		return statusService;
	}

	public LogService getLogService() {
		return logService;
	}

}
