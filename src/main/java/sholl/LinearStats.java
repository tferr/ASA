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

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;

public class LinearStats {

	public double[] radii;
	public double[] counts;
	public double max = 0d;
	public double maxRadius = 0d;

	public LinearStats(final double[] radii, final double[] inters) {
		this.radii = radii;
		this.counts = inters;
	}

	public LinearStats(final ArrayList<Double> radii, final ArrayList<Double> inters) {
		this.radii = radii.stream().mapToDouble(d -> d).toArray();
		this.counts = inters.stream().mapToDouble(d -> d).toArray();
	}

	@SuppressWarnings("unchecked")
	public boolean valid(final ArrayList<Double>... lists) {
		for (int i = 0; i < lists.length; i++) {
			for (int j = i + 1; j < lists.length; j++) {
				if (lists[i] == null || lists[j] == null || lists[i].size() != lists[j].size()) {
					return false;
				}
			}
		}
		return true;
	}

	private void calculateMaximum() {
		if (max == 0d && maxRadius == 0d) { // not yet calculated
			for (int i = 0; i < counts.length; i++) {
				if (counts[i] > max) {
					max = counts[i];
					maxRadius = radii[i];
				}
			}
		}
	}

	public double[] getMaximum() {
		calculateMaximum();
		return new double[] { maxRadius, max };
	}

	public double getRamificationIndex() {
		calculateMaximum();
		return max / counts[0];
	}

	public double getIntersectingRadii() {
		int count = 0;
		for (final double c : counts) {
			if (c > 0)
				count++;
		}
		return count;
	}

	public double getEnclosingRadius(final double cutoff) {
		final double enclosingRadius = Double.NaN;
		for (int i = counts.length - 1; i > 0; i--) {
			if (counts[i] >= cutoff)
				return radii[i];
		}
		return enclosingRadius;
	}

	public double getMean() {
		return StatUtils.mean(counts);
	}

	public double getMedian() {
		return StatUtils.percentile(counts, 50);
	}

	public double getKurtosis() {
		final Kurtosis k = new Kurtosis();
		return k.evaluate(counts);
	}

	public double getSkewness() {
		final Skewness s = new Skewness();
		return s.evaluate(counts);
	}

	public double getVariance() {
		return StatUtils.variance(counts);
	}

	public double getSum() {
		return StatUtils.sum(counts);
	}

	/**
	 * Calculates the centroid of a non-self-intersecting closed polygon. It is
	 * assumed that <code>xpoints</code> and <code>ypoints</code> have the same
	 * size
	 *
	 * @return the centroid {x,y} coordinates
	 * @see <a href="http://en.wikipedia.org/wiki/Centroid#Centroid_of_polygon">
	 *      Centroid of polygon </a>
	 */
	public double[] getCentroid() {
		double area = 0, sumx = 0, sumy = 0;
		for (int i = 1; i < radii.length; i++) {
			final double cfactor = (radii[i - 1] * counts[i]) - (radii[i] * counts[i - 1]);
			sumx += (radii[i - 1] + radii[i]) * cfactor;
			sumy += (counts[i - 1] + counts[i]) * cfactor;
			area += cfactor / 2;
		}
		return new double[] { sumx / (6 * area), sumy / (6 * area) };
	}

}
