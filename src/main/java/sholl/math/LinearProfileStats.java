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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.math3.analysis.integration.BaseAbstractUnivariateIntegrator;
import org.apache.commons.math3.analysis.integration.RombergIntegrator;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.solvers.LaguerreSolver;
import org.apache.commons.math3.complex.Complex;
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

/**
 * Retrieves descriptive statistics and calculates Sholl Metrics from sampled
 * Sholl profiles, including those relying on polynomial fitting. (Fitting to
 * polynomials of arbitrary degree is supported. Relies heavily on the
 * {@code org.apache.commons.math} package.
 *
 * @author Tiago Ferreira
 */
public class LinearProfileStats {

	private final double[] radii;
	private final double[] counts;
	private final int nPoints;
	private final static double UNASSIGNED_VALUE = Double.MIN_VALUE;

	/* Sampled data */
	private double maxCount = UNASSIGNED_VALUE;
	private double sumCounts = UNASSIGNED_VALUE;
	private double sumSqCounts = UNASSIGNED_VALUE;
	private ArrayList<Point2D.Double> maxima;

	/* Polynomial fit */
	private double[] polyfitCounts;
	private PolynomialFunction pFunction;
	private int maxEval = 1000; // number of function evaluations

	/**
	 * List based constructor.
	 *
	 * @param radii
	 *            sampled radii
	 * @param sampledInters
	 *            sampled intersection counts
	 */
	public LinearProfileStats(final ArrayList<Double> radii, final ArrayList<Double> sampledInters) {
		this(radii.stream().mapToDouble(d -> d).toArray(), sampledInters.stream().mapToDouble(d -> d).toArray(), false);
	}

	/**
	 * Constructor accepting integer data.
	 *
	 * @param radii
	 *            sampled radii
	 * @param sampledInters
	 *            sampled intersection counts
	 */
	public LinearProfileStats(final int[] radii, final int[] sampledInters, final boolean zapZeroes) {
		this(Arrays.stream(radii).asDoubleStream().toArray(), Arrays.stream(sampledInters).asDoubleStream().toArray(),
				zapZeroes);
	}

	/**
	 * Default constructor.
	 *
	 * @param radii
	 *            sampled radii
	 * @param sampledInters
	 *            sampled intersection counts
	 */
	public LinearProfileStats(final double[] radii, final double[] sampledInters, final boolean zapZeroes) {
		if (radii == null || sampledInters == null)
			throw new IllegalArgumentException("Arrays cannot be null");
		final int n = radii.length;
		if (n == 0 || n != sampledInters.length)
			throw new IllegalArgumentException("Arrays cannot be empty and must have the same size");
		if (zapZeroes) {
			final Object[] data = trimZeroes(radii, sampledInters);
			this.radii = (double[]) data[0];
			this.counts = (double[]) data[1];
			this.nPoints = this.radii.length;
		} else {
			this.radii = radii;
			this.counts = sampledInters;
			this.nPoints = n;
		}
	}

	private Object[] trimZeroes(final double[] radii, final double[] counts) {
		final int n = radii.length;
		int nTrimmed = 0;
		for (int i = 0; i < n; i++) {
			if (counts[i] > 0)
				nTrimmed++;
		}
		if (nTrimmed == n)
			return new Object[] { radii, counts };

		final double[] radiiTrimmed = new double[nTrimmed];
		final double[] countsTrimmed = new double[nTrimmed];
		for (int i = 0, j = 0; i < n; i++) {
			if (counts[i] > 0) {
				radiiTrimmed[j] = radii[i];
				countsTrimmed[j++] = radii[i];
			}
		}
		return new Object[] { radiiTrimmed, countsTrimmed };
	}

	/**
	 * Constructor accepting matrices.
	 *
	 * @param sampledData
	 *            sampled data in double[n][2] format, where n = number of
	 *            points (radii: double[n][0]; sampledInters: double[n][1])
	 */
	public LinearProfileStats(final double[][] sampledData) {
		nPoints = sampledData.length;
		radii = new double[nPoints];
		counts = new double[nPoints];
		for (int i = 0; i < nPoints; i++) {
			radii[i] = sampledData[i][0];
			counts[i] = sampledData[i][1];
		}
	}

	/**
	 * Retrieves the centroid from all pairs of data (radius, inters. counts).
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the centroid {x,y} coordinates
	 */
	public Point2D.Double getCentroid(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		final double x = StatUtils.sum(radii) / nPoints;
		final double y = StatUtils.sum(fittedData ? polyfitCounts : counts) / nPoints;
		return new Point2D.Double(x, y);
	}

	/** @return {@link #getCentroid(boolean) getCentroid(false)} */
	public Point2D.Double getCentroid() {
		return getCentroid(false);
	}

	/**
	 * Calculates the centroid from all (radius, inters. counts) pairs assuming
	 * such points define a non-self-intersecting closed polygon. Implementation
	 * from <a href=
	 * "https://en.wikipedia.org/wiki/Centroid#Centroid_of_a_polygon">wikipedia</a>.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the point by the centroid {x,y} coordinates
	 */
	public Point2D.Double getPolygonCentroid(final boolean fittedData) {
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
		return new Point2D.Double(sumx / (6 * area), sumy / (6 * area));
	}

	/**
	 * @return {@link #getPolygonCentroid(boolean) getPolygonCentroid(false)}
	 */
	public Point2D.Double getPolygonCentroid() {
		return getPolygonCentroid(false);
	}

	/**
	 * Returns the largest radius associated with at least the number of
	 * specified counts.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @param cutoff
	 *            the cutoff for intersection counts
	 * @return the largest radius associated with the same or more cutoff counts
	 */
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

	/**
	 * @param cutoff
	 *            the cutoff for intersection counts
	 * @return {@link #getEnclosingRadius(boolean, double)
	 *         getEnclosingRadius(false, cutoff)}
	 */
	public double getEnclosingRadius(final double cutoff) {
		return getEnclosingRadius(false, cutoff);
	}

	/**
	 * Returns the number of intersecting radii.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the count of all radii associated with at at least one
	 *         intersection
	 */
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

	/**
	 * @return {@link #getIntersectingRadii(boolean)
	 *         getIntersectingRadii(false)}
	 */
	public int getIntersectingRadii() {
		return getIntersectingRadii(false);
	}

	/**
	 * Calculates the kurtosis.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return kurtosis of intersection counts
	 */
	public double getKurtosis(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		final Kurtosis k = new Kurtosis();
		return k.evaluate(fittedData ? polyfitCounts : counts);
	}

	/** @return {@link #getKurtosis(boolean) getKurtosis(false)} */
	public double getKurtosis() {
		return getKurtosis(false);
	}

	/**
	 * Returns a list of all the points in the linear Sholl profile associated
	 * with the highest intersections count
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 * @return the list of points of all maxima
	 */
	public ArrayList<Point2D.Double> getMaxima(final boolean fittedData) {
		if (!fittedData && maxima != null) {
			return maxima;
		}
		final double values[];
		final double max;
		final ArrayList<Point2D.Double> target = new ArrayList<>();
		if (fittedData) {
			checkPolynomialFit();
			values = polyfitCounts;
			max = StatUtils.max(values);
		} else {
			max = getMaxCount(fittedData);
			values = counts;
		}
		for (int i = 0; i < nPoints; i++) {
			if (values[i] == max) {
				target.add(new Point2D.Double(radii[i], values[i]));
			}
		}
		if (maxima == null)
			maxima = target;
		return target;
	}

	/** @return {@link #getMaxima(boolean) getMaxima(false)} */
	public ArrayList<Point2D.Double> getMaxima() {
		return getMaxima(false);
	}

	/**
	 * Returns the average coordinates of all maxima.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 * @return the averaged x,y coordinates of maxima
	 */
	public Point2D.Double getCenteredMaximum(final boolean fittedData) {
		final ArrayList<Point2D.Double> maxima = getMaxima(fittedData);
		double sumX = 0;
		double sumY = 0;
		for (final Point2D.Double p : maxima) {
			sumX += p.x;
			sumY += p.y;
		}
		final double avgX = sumX / maxima.size();
		final double avgY = sumY / maxima.size();
		return new Point2D.Double(avgX, avgY);
	}

	/**
	 * @return {@link #getCenteredMaximum(boolean) getCenteredMaximum(false)}
	 */
	public Point2D.Double getCenteredMaximum() {
		return getCenteredMaximum(false);
	}

	/**
	 * Calculates the arithmetic mean.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the mean of intersection counts
	 */
	public double getMean(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.mean(polyfitCounts);
		}
		return StatUtils.mean(counts);
	}

	/** @return {@link #getMean(boolean) getMean(false)} */
	public double getMean() {
		return getMean(false);
	}

	/**
	 * Returns the closest index of sampled distances associated with the
	 * specified value
	 *
	 * @param radius
	 *            the query value
	 * @return the position index (zero-based) or -1 if no index could be
	 *         calculated
	 */
	public int getIndexOfRadius(final double radius) {
		return getIndex(radii, radius);
	}

	/**
	 * Returns the closest index of the intersections data associated with the
	 * specified value
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 * @param inters
	 *            the query value
	 * @return the position index (zero-based) or -1 if no index could be
	 *         calculated
	 */
	public int getIndexOfInters(final boolean fittedData, final double inters) {
		if (fittedData) {
			checkPolynomialFit();
			return getIndex(polyfitCounts, inters);
		}
		return getIndex(counts, inters);
	}

	/**
	 * Fits sampled data to a polynomial function and keeps the fit in memory.
	 *
	 * @param degree
	 *            Degree of the polynomial to be fitted
	 * @throws NullArgumentException
	 *             if the computed polynomial coefficients were null
	 * @throws NoDataException
	 *             if the computed polynomial coefficients were empty
	 */
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

	/**
	 * Returns the sampled distances.
	 *
	 * @return the abscissae of the sampled profile
	 */
	public double[] getRadii() {
		return radii;
	}

	/**
	 * Returns the sampled intersection counts.
	 *
	 * @return the ordinates of the sampled profile
	 */
	public double[] getCounts() {
		return counts;
	}

	/**
	 * Returns the polynomial fitted intersection counts.
	 *
	 * @return the y-values of the polynomial fit retrieved at sampling
	 *         distances
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int) } has not been called
	 */
	public double[] getFcounts() {
		checkPolynomialFit();
		return polyfitCounts;
	}

	/**
	 * Checks if valid fitted data exists.
	 *
	 * @return {@code true} if polynomial fitted data exists
	 */
	public boolean validFcounts() {
		return (polyfitCounts != null && polyfitCounts.length > 0);
	}

	/**
	 * Returns the polynomial function currently fitting the data
	 *
	 * @return the polynomial
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public PolynomialFunction setPolynomial(final PolynomialFunction polynomial) {
		checkPolynomialFit();
		return pFunction;
	}

	/**
	 * Gets the polynomial function.
	 *
	 * @return the polynomial
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public PolynomialFunction getPolynomial() {
		checkPolynomialFit();
		return pFunction;
	}

	/**
	 * Returns the degree of the polynomial.
	 *
	 * @return the polynomial degree
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public int getPolynomialDegree() {
		checkPolynomialFit();
		return pFunction.degree();
	}

	/**
	 * Returns a string describing the polynomial fit
	 *
	 * @return the description, e.g., 8th degree
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public String getPolynomialAsString() {
		final int deg = getPolynomialDegree();
		String degOrd = "";
		final String[] sufixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
		switch (deg % 100) {
		case 11:
		case 12:
		case 13:
			degOrd = deg + "th";
		default:
			degOrd = deg + sufixes[deg % 10];
		}
		return degOrd + " deg.";
	}

	private void checkPolynomialFit() {
		if (pFunction == null || polyfitCounts == null)
			throw new NullPointerException("fitPolynomial() not been called");
	}

	/**
	 * Calculates local maxima (critical points at which the derivative of the
	 * polynomial is zero) within the specified interval.
	 *
	 * @param lowerBound
	 *            the lower bound of the interval
	 * @param upperBound
	 *            the upper bound of the interval
	 * @param initialGuess
	 *            initial guess for a solution (solver's starting point)
	 * @return the list of Points defined by the {x,y} coordinates of maxima
	 *         (sorted by descendant order)
	 * @throws TooManyEvaluationsException
	 *             if the maximum number of evaluations is exceeded when solving
	 *             for one of the roots
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public Set<Point2D.Double> getPolynomialMaxima(final double lowerBound, final double upperBound,
			final double initialGuess) {
		checkPolynomialFit();
		final PolynomialFunction derivative = pFunction.polynomialDerivative();
		final LaguerreSolver solver = new LaguerreSolver();
		final Complex[] roots = solver.solveAllComplex(derivative.getCoefficients(), initialGuess, getMaxEvaluations());
		if (roots == null)
			return null;
		final Set<Point2D.Double> maxima = new TreeSet<>(new Comparator<Point2D.Double>() {
			@Override
			public int compare(final Point2D.Double p1, final Point2D.Double p2) {
				return Double.compare(p2.y, p1.y); // descendant order of
													// ordinates
			}
		});
		final double tolerance = getAverageStepSize();
		for (final Complex root : roots) {
			final double x = root.getReal();
			if (x < lowerBound || x > upperBound)
				continue;
			final double y = pFunction.value(x);
			if (y > pFunction.value(x - tolerance) && y > pFunction.value(x + tolerance)) {
				maxima.add(new Point2D.Double(x, y));
			}
		}
		return maxima;
	}

	private double getAverageStepSize() {
		double stepSize = 0;
		for (int i = 1; i < nPoints; i++) {
			stepSize = radii[i] - radii[0];
		}
		return stepSize / nPoints;
	}

	/**
	 * Gets the function evaluation limit for solvers
	 *
	 * @return the set maximum of evaluations (1000 by default)
	 */
	public int getMaxEvaluations() {
		return maxEval;
	}

	/**
	 * Sets the the function evaluation limit for solvers.
	 *
	 * @param maxEval
	 *            the new maximum of evaluations
	 */
	public void setMaxEvaluations(final int maxEval) {
		this.maxEval = maxEval;
	}

	/**
	 * Returns RSquared of the polynomial fit. Implementation from <a href=
	 * "https://en.wikipedia.org/wiki/Coefficient_of_determination">wikipedia</a>.
	 * \( R^2 = 1 - (SSres/SStot) \) with \( SSres = SUM(i) (yi - fi)^2 \) \(
	 * SStot = SUM(i) (yi - yavg)^2 \)
	 *
	 * @param adjusted
	 *            if {@code true} returns adjusted RSquared, i.e., adjusted for
	 *            the number of terms of the polynomial model
	 * @return RSquared, a measure for the goodness of fit
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */

	public double getRSquaredOfPolynomialFit(final boolean adjusted) {
		checkPolynomialFit();
		// calculate 'residual sum of squares'
		double ssRes = 0.0;
		for (int i = 0; i < nPoints; i++) {
			final double y = counts[i];
			final double f = polyfitCounts[i];
			ssRes += (y - f) * (y - f);
		}
		// calculate 'total sum of squares'
		final double sampleAvg = StatUtils.mean(counts);
		double ssTot = 0.0;
		for (final double y : counts) {
			ssTot += (y - sampleAvg) * (y - sampleAvg);
		}

		double rSquared = 1.0 - (ssRes / ssTot);
		if (adjusted) {
			final int p = pFunction.degree() - 1;
			rSquared = rSquared - (1 - rSquared) * (p / (nPoints - p - 1));
		}
		return rSquared;
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
	 *             if {@link #fitPolynomial(int)} has not been called
	 * @throws InsufficientDataException
	 *             if sampled data contains fewer than two data points
	 */
	public double getKStestOfPolynomialFit() {
		checkPolynomialFit();
		final KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		final double pValue = test.kolmogorovSmirnovTest(counts, polyfitCounts);
		return pValue;
	}

	private int getIndex(final double[] array, final double value) {
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

	/**
	 * Gets the mean value of polynomial fit.
	 *
	 * @param integrator
	 *            the integration method to retrieve the integral of the
	 *            polynomial fit. Either "Simpson" (the default), or "Romberg"
	 *            (case insensitive)
	 * @param lowerBound
	 *            the lower bound (smallest radius) for the interval
	 * @param upperBound
	 *            the upper bound (largest radius) for the interval
	 * @return the mean value of polynomial fit
	 * @throws MathIllegalArgumentException
	 *             if bounds do not satisfy the integrator requirements
	 * @throws TooManyEvaluationsException
	 *             if the maximum number of function evaluations is exceeded by
	 *             the integrator
	 * @throws MaxCountExceededException
	 *             if the maximum iteration count is exceeded by the integrator
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
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

	/**
	 * Calculates the mean value of polynomial fit using the default integration
	 * method (Simpson's).
	 *
	 * @param lowerBound
	 *            the lower bound (smallest radius) for the interval
	 * @param upperBound
	 *            the upper bound (largest radius) for the interval
	 * @return the mean value of polynomial fit, or {@code Double.NaN} if
	 *         calculation failed.
	 * @throws NullPointerException
	 *             if {@link #fitPolynomial(int)} has not been called
	 */
	public double getMeanValueOfPolynomialFit(final double lowerBound, final double upperBound) {
		try {
			return getMeanValueOfPolynomialFit(null, lowerBound, upperBound);
		} catch (MathIllegalArgumentException | MaxCountExceededException ignored) {
			return Double.NaN;
		}
	}

	/**
	 * Calculates the median.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the median of intersection counts
	 */
	public double getMedian(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.percentile(polyfitCounts, 50);
		}
		return StatUtils.percentile(counts, 50);
	}

	/** @return {@link #getMedian(boolean) getMedian(false)} */
	public double getMedian() {
		return getMedian(false);
	}

	/**
	 * Returns intersection counts at the smallest radius.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 * @return the count for inferred no. of primary branches
	 */
	public double getPrimaryBranches(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
		}
		return startRadiusCount(fittedData);
	}

	private double startRadiusCount(final boolean fittedData) {
		return (fittedData) ? polyfitCounts[0] : counts[0];
	}

	/**
	 * @return {@link #getPrimaryBranches(boolean) getPrimaryBranches(false)}
	 */
	public double getPrimaryBranches() {
		return getPrimaryBranches(false);
	}

	/**
	 * Calculates the ramification index (the highest intersections count
	 * divided by the n. of primary branches, assumed to be the n. intersections
	 * at starting radius.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the ramification index
	 */
	public double getRamificationIndex(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		return getMaxCount(fittedData) / startRadiusCount(fittedData);
	}

	private double getMaxCount(final boolean fittedData) {
		if (fittedData)
			return StatUtils.max(polyfitCounts);
		if (maxCount == UNASSIGNED_VALUE)
			maxCount = StatUtils.max(counts);
		return maxCount;
	}

	/**
	 * @return {@link #getRamificationIndex(boolean)
	 *         getRamificationIndex(false)}
	 */
	public double getRamificationIndex() {
		return getRamificationIndex(false);
	}

	/**
	 * Returns the largest value of intersection count.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the largest value of intersection counts
	 */
	public double getMax(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		return getMaxCount(fittedData);
	}

	/** @return {@link #getMax(boolean) getMax(false)} */
	public double getMax() {
		return getMaxCount(false);
	}

	/**
	 * Returns the lowest value of intersection count.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the lowest value of intersection counts
	 */
	public double getMin(final boolean fittedData) {
		if (fittedData) {
			checkPolynomialFit();
			return StatUtils.min(polyfitCounts);
		}
		return StatUtils.min(counts);
	}

	/** @return {@link #getMin(boolean) getMin(false)} */
	public double getMin() {
		return getMin(false);
	}

	/**
	 * Returns the shortest sampling distance (typically the first sampling
	 * radius)
	 *
	 * @return the smallest sampling radius (typically the first)
	 */
	public double getStartRadius() {
		return StatUtils.min(radii);
	}

	/**
	 * Returns the largest sampling distance (typically the last sampling
	 * radius)
	 *
	 * @return the largest sampling radius
	 */
	public double getEndRadius() {
		return StatUtils.max(radii);
	}

	/**
	 * Calculates the skewness.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the skewness of intersection counts
	 */
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

	/**
	 * Calculates the sum.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the sum of intersection counts
	 */
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
	 * Calculates the sum of the squared values.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 * @return the sum of the squared values of intersection counts
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

	/** @return {@link #getSumSq(boolean) getSumSq(false)} */
	public double getSumSq() {
		return getSumSq(false);
	}

	/**
	 * Calculates the variance.
	 *
	 * @param fittedData
	 *            If {@code true}, calculation is performed on polynomial fitted
	 *            values, otherwise from sampled data
	 *
	 * @return the variance of intersection counts
	 */
	public double getVariance(final boolean fittedData) {
		if (fittedData)
			checkPolynomialFit();
		return StatUtils.variance(fittedData ? polyfitCounts : counts);
	}

	/** @return {@link #getVariance(boolean) getVariance(false)} */
	public double getVariance() {
		return getVariance(false);
	}

}
