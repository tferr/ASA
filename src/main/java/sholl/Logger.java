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

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(final boolean debug) {
		this.debug = debug;
	}

}
