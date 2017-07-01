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
import sholl.ProfileEntry;
import sholl.gui.ShollPlot;

class CommonStats implements ShollStats {

	protected final static double UNASSIGNED_VALUE = Double.MIN_VALUE;

	protected final double[] inputRadii;
	protected final double[] inputCounts;
	protected int nPoints;
	protected double[] fCounts;
	protected ShollPlot plot;
	protected final Profile profile;

	protected CommonStats(final Profile profile) {
		if (profile == null || profile.size() == 0)
			throw new IllegalArgumentException("Cannot instantiate analysis with a null or empty profile");
		this.profile = profile;
		nPoints = profile.size();
		inputRadii = new double[nPoints];
		inputCounts = new double[nPoints];
		int idx = 0;
		for (final ProfileEntry entry : profile.entries()) {
			inputRadii[idx] = entry.radius;
			inputCounts[idx++] = entry.count;
		}
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
	public double getKStestOfFit() {
		validateFit();
		final KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		final double pValue = test.kolmogorovSmirnovTest(inputCounts, fCounts);
		return pValue;
	}

	public double getRSquaredOfFit() {
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

	public ShollPlot getPlot() {
		if (plot == null)
			plot = new ShollPlot(this);
		return plot;
	}

	public double getAdjustedRSquaredOfFit(final int p) {
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
			throw new NullPointerException("Fitted data required but fit not yet performed");
	}

	/**
	 * Returns X-values of a Sholl plot.
	 *
	 * @return X-values of a Sholl plot
	 */
	@Override
	public double[] getXvalues() {
		return inputRadii;
	}

	@Override
	public double[] getYvalues() {
		return inputCounts;
	}

	@Override
	public int getN() {
		return nPoints;
	}

	@Override
	public double[] getFitYvalues() {
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

	@Override
	public Profile getProfile() {
		return profile;
	}

}
