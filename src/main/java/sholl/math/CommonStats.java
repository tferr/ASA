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

import java.util.NavigableSet;
import java.util.TreeSet;

import org.apache.commons.math3.exception.InsufficientDataException;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

import sholl.Profile;

public class CommonStats implements ShollStats {

	protected final static double UNASSIGNED_VALUE = Double.MIN_VALUE;

	protected final double[] inputRadii;
	protected final double[] inputCounts;
	protected int nPoints;
	protected double[] fCounts;

	public CommonStats(final Profile profile) {
		if (profile == null)
			throw new NullPointerException("Cannot instantiate class with a null profile");
		inputRadii = profile.radiiAsArray();
		inputCounts = profile.countsAsArray();
		if (inputRadii == null || inputCounts == null)
			throw new NullPointerException("Cannot instantiate class with profile holding null data");
		final int n = inputRadii.length;
		if (n == 0 || n != inputCounts.length)
			throw new IllegalArgumentException("BUG: profile's size of data arrays in  differ");
		nPoints = this.inputRadii.length;
	}

	public double getStepSize() {
		double stepSize = 0;
		for (int i = 1; i < nPoints; i++) {
			stepSize += inputRadii[i] - inputRadii[i - 1];
		}
		return stepSize / nPoints;
	}

	/**
	 * Returns the Kolmogorov-Smirnov (K-S) test of the polynomial fit as a
	 * measurement of goodness of fit.
	 *
	 * @return the test statistic (p-value) used to evaluate the null hypothesis
	 *         that sampled data and polynomial fitted represent samples from
	 *         the same underlying distribution
	 *
	 * @throws NullPointerException
	 *             if curve fitting has not been performed
	 * @throws InsufficientDataException
	 *             if sampled data contains fewer than two data points
	 */
	protected double getKStestOfFit() {
		validateFit();
		final KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		final double pValue = test.kolmogorovSmirnovTest(inputCounts, fCounts);
		return pValue;
	}

	protected double getRSquaredOfFit() {
		validateFit();

		// calculate 'residual sum of squares'
		double ssRes = 0.0;
		for (int i = 0; i < nPoints; i++) {
			final double y = inputCounts[i];
			final double f = fCounts[i];
			ssRes += (y - f) * (y - f);
		}
		// calculate 'total sum of squares'
		final double sampleAvg = StatUtils.mean(inputCounts);
		double ssTot = 0.0;
		for (final double y : inputCounts) {
			ssTot += (y - sampleAvg) * (y - sampleAvg);
		}

		return 1.0 - (ssRes / ssTot);
	}

	protected double getAdjustedRSquaredOfFit(final int p) {
		double rSquared = getRSquaredOfFit();
		rSquared = rSquared - (1 - rSquared) * (p / (nPoints - p - 1));
		return rSquared;
	}

	protected int getIndex(final double[] array, final double value) {
		final NavigableSet<Double> ns = new TreeSet<>();
		for (final double element : array)
			ns.add(element);
		final Double candidate = ns.floor(value);
		if (candidate == null)
			return -1;
		for (int i = 0; i < array.length; i++)
			if (array[i] == candidate)
				return i;
		return -1;
	}

	protected void validateFit() {
		if (!validFit())
			throw new NullPointerException("fitPolynomial() not been called");
	}

	/**
	 * Returns sampled distances.
	 *
	 * @return sampling distances associated with input profile
	 */
	@Override
	public double[] getRadii() {
		return inputRadii;
	}

	/**
	 * Returns sampled counts.
	 *
	 * @return intersection counts associated with input profile
	 */
	@Override
	public double[] getCounts() {
		return inputCounts;
	}

	/**
	 * Returns the shortest sampling distance (typically the first sampling
	 * radius)
	 *
	 * @return the smallest sampling radius (typically the first)
	 */
	@Override
	public double getStartRadius() {
		return StatUtils.min(inputRadii);
	}

	/**
	 * Returns the largest sampling distance (typically the last sampling
	 * radius)
	 *
	 * @return the largest sampling radius
	 */
	@Override
	public double getEndRadius() {
		return StatUtils.max(inputRadii);
	}

	@Override
	public int getN() {
		return nPoints;
	}

	@Override
	public double[] getFcounts() {
		return fCounts;
	}

	/**
	 * Checks if valid fitted data exists.
	 *
	 * @return {@code true} if polynomial fitted data exists
	 */
	@Override
	public boolean validFit() {
		return (fCounts != null && fCounts.length > 0);
	}

}
