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

import java.util.Iterator;
import java.util.Set;

import ij.measure.Calibration;

/**
 * Class to access Cartesian coordinates of 2D and 3D points. Designed to
 * accommodate points from several Universes such SWC points, pixels, voxels,
 * and point ROIs.
 *
 * @author Tiago Ferreira
 */
public class UPoint {

	/**
	 * The x-coordinate in world coordinates (not pixel coordinates)
	 */
	public double x;
	/**
	 * The y-coordinate in world coordinates (not pixel coordinates)
	 */
	public double y;
	/**
	 * The z-coordinate in world coordinates (not pixel coordinates)
	 */
	public double z;

	public UPoint() {
	}

	public UPoint(final Number x, final Number y, final Number z) {
		this.x = x.doubleValue();
		this.y = y.doubleValue();
		this.z = z.doubleValue();
	}

	public UPoint(final int x, final int y, final int z, final Calibration cal) {
		this.x = cal.getX(x);
		this.y = cal.getY(y);
		this.z = cal.getZ(z);
	}

	public UPoint(final int x, final int y, final Calibration cal) {
		this.x = cal.getX(x);
		this.y = cal.getY(y);
		this.z = cal.getZ(1);
	}

	public UPoint(final Number x, final Number y) {
		this.x = x.doubleValue();
		this.y = y.doubleValue();
		this.z = 0;
	}

	public void scale(final double xScale, final double yScale, final double zScale) {
		this.x *= xScale;
		this.y *= yScale;
		this.z *= zScale;
	}

	public void applyOffset(final double xOffset, final double yOffset, final double zOffset) {
		this.x += xOffset;
		this.y += yOffset;
		this.z += zOffset;
	}

	public UPoint minus(final UPoint point) {
		return new UPoint(x - point.x, y - point.y, z - point.z);
	}

	public UPoint plus(final UPoint point) {
		return new UPoint(x + point.x, y + point.y, z + point.z);
	}

	public double scalar(final UPoint point) {
		return x * point.x + y * point.y + z * point.z;
	}

	public UPoint times(final double factor) {
		return new UPoint(x * factor, y * factor, z * factor);
	}

	public double length() {
		return Math.sqrt(scalar(this));
	}

	public double distanceSquared(final UPoint point) {
		final double x1 = x - point.x;
		final double y1 = y - point.y;
		final double z1 = z - point.z;
		return x1 * x1 + y1 * y1 + z1 * z1;
	}

	public double euclideanDxTo(final UPoint point) {
		return Math.sqrt(distanceSquared(point));
	}

	public double chebyshevDxTo(final UPoint point) {
		double max = Math.max(Math.abs(x - point.x), Math.abs(y - point.y));
		max = Math.max(Math.abs(z - point.z), max);
		return max;
	}

	public UPoint average(final UPoint... points) {
		UPoint result = new UPoint();
		for (final UPoint point : points)
			result = result.plus(point);
		return result.times(1.0 / points.length);
	}

	public static void scale(final Set<UPoint> set, final Calibration cal) {
		for (final Iterator<UPoint> it = set.iterator(); it.hasNext();) {
			final UPoint point = it.next();
			point.x = cal.getX(point.x);
			point.y = cal.getY(point.y);
			point.z = cal.getZ(point.z);
		}
	}

	protected static UPoint fromString(final String string) {
		if (string == null || string.isEmpty())
			return null;
		final String[] ccs = string.trim().split(",");
		if (ccs.length == 3) {
			return new UPoint(Double.valueOf(ccs[0]), Double.valueOf(ccs[1]), Double.valueOf(ccs[2]));
		}
		return null;
	}

	public double rawX(final Calibration cal) {
		return cal.getRawX(x);
	}

	public double rawY(final Calibration cal) {
		return cal.getRawY(y);
	}

	public double rawZ(final Calibration cal) {
		return z / cal.pixelDepth + cal.zOrigin;
	}

	@Override
	public String toString() {
		return ShollUtils.d2s(x) + "," + ShollUtils.d2s(y) + "," + ShollUtils.d2s(z);
	}
}