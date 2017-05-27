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

import java.util.ArrayList;

/**
 * Utility class to access 2D/3D points. Inspired by fiji.math3d.Point3d
 *
 * @author Tiago Ferreira
 */
public class ShollPoint {

	public double x, y, z;

	public ShollPoint() {
	}

	public ShollPoint(final double x, final double y, final double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public ShollPoint(final int x, final int y) {
		this.x = x;
		this.y = y;
		this.z = 1;
	}

	public ShollPoint(final int x, final int y, final int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public ShollPoint minus(final ShollPoint other) {
		return new ShollPoint(x - other.x, y - other.y, z - other.z);
	}

	public ShollPoint plus(final ShollPoint other) {
		return new ShollPoint(x + other.x, y + other.y, z + other.z);
	}

	public double scalar(final ShollPoint other) {
		return x * other.x + y * other.y + z * other.z;
	}

	public ShollPoint times(final double factor) {
		return new ShollPoint(x * factor, y * factor, z * factor);
	}

	public double length() {
		return Math.sqrt(scalar(this));
	}

	public double distanceSquared(final ShollPoint other) {
		final double x1 = x - other.x;
		final double y1 = y - other.y;
		final double z1 = z - other.z;
		return x1 * x1 + y1 * y1 + z1 * z1;
	}

	public double distanceTo(final ShollPoint other) {
		return Math.sqrt(distanceSquared(other));
	}

	public static ShollPoint average(final ArrayList<ShollPoint> list) {
		ShollPoint result = new ShollPoint();
		for (final ShollPoint point : list)
			result = result.plus(point);
		return result.times(1.0 / list.size());
	}

}
