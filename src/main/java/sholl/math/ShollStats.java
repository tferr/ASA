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
package sholl.math;

/**
 * @author Tiago Ferreira
 *
 */
public interface ShollStats {

	/** Flag for area normalization (Semi-log/Log-log method) */
	public final static int AREA = 2;
	/** Flag for perimeter normalization (Semi-log/Log-log method) */
	public final static int PERIMETER = 4;
	/** Flag for annulus normalization (Semi-log/Log-log method) */
	public final static int ANNULUS = 8;
	/** Flag for volume normalization (Semi-log/Log-log method) */
	public final static int VOLUME = 16;
	/** Flag for surface normalization (Semi-log/Log-log method) */
	public final static int SURFACE = 32;
	/** Flag for spherical shell normalization (Semi-log/Log-log method) */
	public static final int S_SHELL = 64;

	/** Flag for imposing Semi-log analysis */
	public final static int SEMI_LOG = 128;
	/** Flag for imposing Log-log analysis */
	public final static int LOG_LOG = 256;
	/** Flag for automatic choice between Semi-log/Log-log analysis */
	public final static int GUESS_SLOG = 512;

	double[] getRadii();

	double[] getCounts();

	double[] getFcounts();

	int getN();

	boolean validFit();

	double getStartRadius();

	double getEndRadius();

}
