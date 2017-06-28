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

import ij.measure.Calibration;

/**
 * Utility class to access the coordinates of intersection points. Inspired by
 * fiji.math3d.Point3d
 *
 * @author Tiago Ferreira
 */
public class ShollPoint {

	/**
	 * The intersection x-coordinate in world coordinates (not pixel
	 * coordinates)
	 */
	public double x;
	/**
	 * The intersection y-coordinate in world coordinates (not pixel
	 * coordinates)
	 */
	public double y;
	/**
	 * The intersection z-coordinate in world coordinates (not pixel
	 * coordinates)
	 */
	public double z;

	public ShollPoint() {
	}

	public ShollPoint(final Number x, final Number y, final Number z) {
		this.x = x.doubleValue();
		this.y = y.doubleValue();
		this.z = z.doubleValue();
	}

	public ShollPoint(final int x, final int y, final int z, final Calibration cal) {
		this.x = cal.getX(x);
		this.y = cal.getY(y);
		this.z = cal.getZ(z);
	}

	public ShollPoint(final int x, final int y, final Calibration cal) {
		this.x = cal.getX(x);
		this.y = cal.getY(y);
		this.z = cal.getZ(1);
	}

	public ShollPoint(final Number x, final Number y) {
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

	public ShollPoint minus(final ShollPoint point) {
		if (point == null)
			return this;
		return new ShollPoint(x - point.x, y - point.y, z - point.z);
	}

	public ShollPoint plus(final ShollPoint point) {
		if (point == null)
			return this;
		return new ShollPoint(x + point.x, y + point.y, z + point.z);
	}

	public double scalar(final ShollPoint point) {
		return x * point.x + y * point.y + z * point.z;
	}

	public ShollPoint times(final double factor) {
		return new ShollPoint(x * factor, y * factor, z * factor);
	}

	public double length() {
		return Math.sqrt(scalar(this));
	}

	public double distanceSquared(final ShollPoint point) {
		final double x1 = x - point.x;
		final double y1 = y - point.y;
		final double z1 = z - point.z;
		return x1 * x1 + y1 * y1 + z1 * z1;
	}

	public double distanceTo(final ShollPoint point) {
		return Math.sqrt(distanceSquared(point));
	}

	public ShollPoint average(final ShollPoint... points) {
		ShollPoint result = new ShollPoint();
		for (final ShollPoint point : points)
			result = result.plus(point);
		return result.times(1.0 / points.length);
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

}
