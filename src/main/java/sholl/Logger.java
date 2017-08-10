
package sholl;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

import sholl.plugin.Prefs;

public class Logger {

	@Parameter
	private LogService logService;

	@Parameter
	private PrefService prefService;

	private boolean debug;

	@Deprecated
	public Logger() {
		this(new Context(LogService.class));
	}

	public Logger(final Context context) {
		context.inject(this);
		debug = prefService.getBoolean(Prefs.class, "debugMode", Prefs.DEF_DEBUG_MODE);
		setDebug(debug || logService.isDebug());
	}

	public void info(final Object msg) {
		logService.info("Sholl: " + msg );
	}

	
	public void debug(final Object msg) {
		if (debug) logService.debug("Sholl: " + msg);
	}

	public void warn(final String string) {
		logService.warn("Sholl: " + string);
	}

	public void log(final String... strings) {
		if (strings != null) log(String.join(" ", strings));
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(final boolean debug) {
		this.debug = debug;
	}

}
