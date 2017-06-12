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
import java.util.Arrays;

import org.apache.commons.math3.analysis.integration.BaseAbstractUnivariateIntegrator;
import org.apache.commons.math3.analysis.integration.RombergIntegrator;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.solvers.LaguerreSolver;
import org.apache.commons.math3.exception.InsufficientDataException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

public class LinearStats {

	private final double[] radii;
	private final double[] counts;
	private final int nPoints;
	private final static double UNASSIGNED_VALUE = Double.MIN_VALUE;

	/* Sampled data */
	private double maxCount = UNASSIGNED_VALUE;
	private double sumCounts = UNASSIGNED_VALUE;
	private double sumSqCounts = UNASSIGNED_VALUE;
	private ArrayList<double[]> maxima;

	/* Polynomial fit */
	private double[] polyfitCounts;
	private PolynomialFunction pFunction;

	public LinearStats(final ArrayList<Double> radii, final ArrayList<Double> sampledInters) {
		this(radii.stream().mapToDouble(d -> d).toArray(), sampledInters.stream().mapToDouble(d -> d).toArray());
	}

	public LinearStats(final int[] radii, final int[] sampledInters) {
		this(Arrays.stream(radii).asDoubleStream().toArray(), Arrays.stream(sampledInters).asDoubleStream().toArray());
	}

	public LinearStats(final double[] radii, final double[] sampledInters) {
		if (radii == null || sampledInters == null)
			throw new IllegalArgumentException("Arrays cannot be null");
		nPoints = radii.length;
		if (nPoints == 0 || nPoints != sampledInters.length)
			throw new IllegalArgumentException("Arrays cannot be empty and must have the same size");
		this.radii = radii;
		this.counts = sampledInters;
	}

	public LinearStats(final double[][] sampledData) {
		nPoints = sampledData.length;
		radii = new double[nPoints];
		counts = new double[nPoints];
		for (int i = 0; i < nPoints; i++) {
			radii[i] = sampledData[i][0];
			counts[i] = sampledData[i][1];
		}
	}

	public double[] getCentroid(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		final double[] centroid = new double[2];
		centroid[0] = StatUtils.sum(radii) / nPoints;
		centroid[1] = StatUtils.sum(fittedData ? polyfitCounts : counts) / nPoints;
		return centroid;
	}


	public double[] getPolygonCentroid(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		double area = 0;
		double sumx = 0;
		double sumy = 0;
		final double[] y = (fittedData) ? polyfitCounts : counts;
		for (int i = 1; i < nPoints; i++) {
			final double cfactor = (radii[i - 1] * y[i]) - (radii[i] * y[i - 1]);
			sumx += (radii[i - 1] + radii[i]) * cfactor;
			sumy += (y[i - 1] + y[i]) * cfactor;
			area += cfactor / 2;
		}
		return new double[] { sumx / (6 * area), sumy / (6 * area) };
	}

	}

	public double getEnclosingRadius(final boolean fittedData, final double cutoff) {
		if (fittedData)
			checkPolynomialFit();
		final double[] y = (fittedData) ? polyfitCounts : counts;
		final double enclosingRadius = Double.NaN;
		for (int i = nPoints - 1; i > 0; i--) {
			if (y[i] >= cutoff)
				return radii[i];
		}
		return enclosingRadius;
	}

	public int getIntersectingRadii(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		int count = 0;
		for (final double c : (fittedData) ? polyfitCounts : counts) {
			if (c > 0)
				count++;
		}
		return count;
	}


	public double getKurtosis(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		final Kurtosis k = new Kurtosis();
		return k.evaluate(fittedData ? polyfitCounts : counts);
	}

	public ArrayList<double[]> getMaxima(final boolean fittedData) {
		if (!fittedData && maxima != null) {
			return maxima;
		}
		final double values[];
		final double max;
		final ArrayList<double[]> target = new ArrayList<>();
		if (fittedData) {
			checkPolynomialFit();
			values = polyfitCounts;
			max = StatUtils.max(values);
		} else {
			max = getMaxCount();
			values = counts;
		}
		for (int i = 0; i < nPoints; i++) {
			if (values[i] == max) {
				target.add(new double[] { radii[i], values[i] });
			}
		}
		if (maxima == null)
			maxima = target;
		return target;
	}

	public double[] getCenteredMaximum(final boolean fittedData) {
		final ArrayList<double[]> maxima = getMaxima(fittedData);
		double sumX = 0;
		double sumY = 0;
		for (final double[] m : maxima) {
			sumX += m[0];
			sumY += m[1];
		}
		final double[] centroid = new double[2];
		centroid[0] = sumX / maxima.size();
		centroid[1] = sumY / maxima.size();
		return centroid;
	}
	public double getMean(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.mean(polyfitCounts);
		}
		return StatUtils.mean(counts);
	}

	public int getIndexOfRadius(final double radius) {
		return getIndex(radii, radius);
	}

	public int getIndexOfInters(final boolean fittedData, final double inters) {
		if (fittedData) {
			checkPolynomialFit();
			return getIndex(polyfitCounts, inters);
		}
		return getIndex(counts, inters);
	}

	public void fitPolynomial(final int degree) {
		final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
		final ArrayList<WeightedObservedPoint> points = new ArrayList<>();
		for (int i = 0; i < nPoints; i++) {
			points.add(new WeightedObservedPoint(1, radii[i], counts[i]));
		}
		pFunction = new PolynomialFunction(fitter.fit(points));
		polyfitCounts = new double[nPoints];
		for (int i = 0; i < nPoints; i++) {
			polyfitCounts[i] = pFunction.value(radii[i]);
		}
	}

	public double[] getPolyFittedInters() {
		checkPolynomialFit();
		return polyfitCounts;
	}

	public PolynomialFunction getPolynomial() {
		checkPolynomialFit();
		return pFunction;
	}

	private void checkPolynomialFit() {
		if (pFunction == null || polyfitCounts == null)
			throw new NullPointerException("fitPolynomial() not been called");
	}

	public double[] getPolynomialFitMaximum(final double lowerBound, final double upperBound) {
		checkPolynomialFit();
		final PolynomialFunction derivative = (PolynomialFunction) pFunction.derivative();
		final LaguerreSolver solver = new LaguerreSolver();
		final double x = solver.solve(getMaxEvaluations(), derivative, lowerBound, upperBound);
		final double y = pFunction.value(x);
		return new double[] { x, y };
	}

	private int getMaxEvaluations() {
		return 1000; // TODO: fixme
	}


	public double getRSquaredOfPolynomialFit(final boolean adjusted) {
		checkPolynomialFit();
		final double sampledAvg = StatUtils.mean(counts);
		double ssTot = 0.0;
		for (final double y : counts) {
			ssTot += (y - sampledAvg) * (y - sampledAvg);
		}

		double ssRes = 0.0;
		for (int i = 0; i < nPoints; i++) {
			final double y = counts[i];
			final double f = polyfitCounts[i];
			ssRes += (y - f) * (y - f);
		}

		double rSquared = 1.0 - (ssRes / ssTot);
		if (adjusted) {
			final int p = pFunction.degree() - 1;
			rSquared = 1.0 - ((1 - rSquared) * p / (nPoints - p - 1));
		}
		return rSquared;
	}

	public double getKStestOfPolynomialFit() {
		checkPolynomialFit();
		final KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		final double pValue = test.kolmogorovSmirnovTest(counts, polyfitCounts);
		return pValue;
	}

	private int getIndex(final double[] array, final double value) {
		for (int i = 0; i < array.length; i++)
			if (array[i] == value)
				return i;
		return -1;
	}

	public double getMeanValueOfPolynomialFit(final String integrator, final double lowerBound,
			final double upperBound) {
		checkPolynomialFit();
		final UnivariateIntegrator uniIntegrator;
		if (integrator != null && integrator.toLowerCase().contains("romberg"))
			uniIntegrator = new RombergIntegrator();
		else
			uniIntegrator = new SimpsonIntegrator();
		final double integral = uniIntegrator.integrate(BaseAbstractUnivariateIntegrator.DEFAULT_MAX_ITERATIONS_COUNT,
				pFunction, lowerBound, upperBound);
		return 1 / (upperBound - lowerBound) * integral;
	}

	public double getMeanValueOfPolynomialFit(final double lowerBound, final double upperBound) {
		try {
			return getMeanValueOfPolynomialFit(null, lowerBound, upperBound);
		} catch (MathIllegalArgumentException | MaxCountExceededException ignored) {
			return Double.NaN;
		}
	}

	public double getMedian(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.percentile(polyfitCounts, 50);
		}
		return StatUtils.percentile(counts, 50);
	}

	}

	public double getPrimaryBranches(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return polyfitCounts[0];
		}
		return counts[0];
	}


	public double getRamificationIndex(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return getPolynomialFitMaximum(radii[0], radii[nPoints - 1])[1] / getPrimaryBranches(true);
		}
		return getMaxCount() / getPrimaryBranches(fittedData);
	}

	private double getMaxCount() {
		if (maxCount == UNASSIGNED_VALUE)
			maxCount = StatUtils.max(counts);
		return maxCount;
	}

	public double[] getMinMax(final boolean fittedData) {
		double[] values;
		if (fittedData) {
			checkPolynomialFit();
			values = polyfitCounts;
		} else {
			values = counts;
		}
		return new double[] { StatUtils.min(values), StatUtils.max(values) };
	}

	/** @return {@link #getMinMax(boolean) getMinMax(false)} */
	public double[] getMinMax() {
		return getMinMax(false);
	}

	public double getSkewness(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		final Skewness s = new Skewness();
		return s.evaluate(fittedData ? polyfitCounts : counts);
	}

	/** @return {@link #getSkewness(boolean) getSkewness(false)} */
	public double getSkewness() {
		return getSkewness(false);
	}

	public double getSum(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.sum(polyfitCounts);
		}
		if (sumCounts == UNASSIGNED_VALUE)
			sumCounts = StatUtils.sum(counts);
		return sumCounts;
	}

	/** @return {@link #getSum(boolean) getSum(false)} */
	public double getSum() {
		return getSum(false);
	}

	/**
	 *
	 */
	public double getSumSq(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.sumSq(polyfitCounts);
		}
		if (sumSqCounts == UNASSIGNED_VALUE)
			sumSqCounts = StatUtils.sumSq(counts);
		return sumSqCounts;
	}

	public double getVariance(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		return StatUtils.variance(fittedData ? polyfitCounts : counts);
	}

	}

}
