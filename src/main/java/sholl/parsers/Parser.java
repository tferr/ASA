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
package sholl.parsers;

import sholl.Profile;

public interface Parser {

	public static final int SOURCE_IMP = 1;
	public static final int SOURCE_TRACES = 2;
	public static final int SOURCE_SWC = 4;
	public static final int SOURCE_TABULAR = 8;
	public static final int SOURCE_STATS = 16;
	public static final int SOURCE_OTHER = 32;
	public static final int TWO_D = 64;
	public static final int THREE_D = 128;
	public static final int UNKNOWN_D = 256;

	public boolean successful();

	public int space();

	public int source();

	public Profile profile();

}
