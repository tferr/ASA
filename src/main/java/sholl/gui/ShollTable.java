/*
 * #%L
 * Sholl_Analysis plugin for ImageJ
 * %%
 * Copyright (C) 2018 Tiago Ferreira
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
package sholl.gui;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.scijava.Context;
import org.scijava.NullContextException;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;

import net.imagej.table.DefaultGenericTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.GenericTable;
import sholl.Profile;
import sholl.UPoint;
import sholl.math.LinearProfileStats;
import sholl.math.NormalizedProfileStats;
import sholl.math.ShollStats;
import sholl.plugin.Prefs;

/**
 * Implementation of {@link GenericTable} for Sholl metrics and Profile lists.
 * 
 * @author Tiago Ferreira
 */
public class ShollTable extends DefaultGenericTable {

	private static final long serialVersionUID = 1L;
	private final Profile profile;
	private ShollStats[] stats;
	private boolean detailedSummary = false;

	@Parameter
	private PrefService prefService;

	/**
	 * Instantiates a new table from a {@link Profile}
	 *
	 * @param profile the profile to be listed and/or summarized by this table
	 */
	public ShollTable(final Profile profile) {
		super();
		this.profile = profile;
	}

	/**
	 * Instantiates a new table capable of detailing metrics from a
	 * {@link LinearProfileStats}, a {@link NormalizedProfileStats} instance or
	 * both.
	 *
	 * @param stats the {@link ShollStats} instances from which metrics should be
	 *              retrieved. It is assumed that all instances analyze the same
	 *              {@link Profile}
	 */
	public ShollTable(final ShollStats... stats) {
		this(stats[0].getProfile());
		this.stats = stats;
	}

	private void addCol(final String header, final List<Double> list) {
		final DoubleColumn col = new DoubleColumn(header);
		for (final double row : list) {
			col.add(row);
		}
		this.add(col);
	}

	private void addCol(final String header, final double[] array) {
		addCol(header, DoubleStream.of(array).boxed().collect(Collectors.toList()));
	}

	/**
	 * Lists (details) the {@link Profile} entries. If this table is aware of
	 * {@link ShollStats} that successfully fitted a model to the profile, XY
	 * coordinates of the fitted curve are also be listed.
	 */
	public void listProfileEntries() {

		addCol("Radius", profile.radii());
		addCol("Inters.", profile.counts());

		if (stats == null)
			return;

		for (final ShollStats stat : stats) {

			if (stat == null)
				continue;

			if (stat instanceof LinearProfileStats) {
				final LinearProfileStats lStats = (LinearProfileStats) stat;
				addCol("Radius (Polyn. fit)", lStats.getXvalues());
				addCol("Inters. (Polyn. fit)", lStats.getFitYvalues());

			} else if (stat instanceof NormalizedProfileStats) {
				final NormalizedProfileStats nStats = (NormalizedProfileStats) stat;
				final String xHeader = (nStats.getMethod() == NormalizedProfileStats.LOG_LOG) ? "log(Radius)"
						: "Radius";
				final String yHeader = "log(Inters. /" + nStats.getNormalizerDescription() + ")";
				addCol(xHeader, nStats.getXvalues());
				addCol(yHeader, nStats.getFitYvalues());
			}
		}

	}

	/**
	 * Sets whether extensive metrics should be listed when outputting summaries.
	 *
	 * @param detailed if true summaries will list verbose details, otherwise
	 *                 summaries will fall back to the 'default' repertoire of
	 *                 metrics
	 */
	public void setDetailedSummary(final boolean detailed) {
		detailedSummary = detailed;
	}

	/**
	 * Runs {@link #summarize(String)} and appends (copies) the summary row to the
	 * specified table
	 *
	 * @param table  the table to which the summary row should be copied
	 * @param header the header for the summary row. If empty or null, the profile
	 *               identifier is used
	 * @see #setDetailedSummary(boolean)
	 * @see #summarize(String)
	 */
	public void summarize(final DefaultGenericTable table, final String header) {
		summarize(header);
		table.appendRow(header);
		final int destinationRow = table.getRowCount() - 1;
		final int sourceRow = getRowCount() - 1;
		for (int col = 0; col < getColumnCount(); col++) {
			final String sourceHeader = getColumnHeader(col);
			final Object sourceObject = get(col, sourceRow);
			table.set(getCol(table, sourceHeader), destinationRow, sourceObject);
		}
	}

	/**
	 * Summarizes {@link Profile} and {@link ShollStats} metrics to a new row. Note
	 * that some of the reported metrics rely on the options set in {@link Prefs}.
	 * To ensure that those are read, you should run {@link #setContext(Context)},
	 * so that a {@link PrefService} is set.
	 *
	 * @param header the header for the summary row. If empty or null, the profile
	 *               identifier is used
	 * @see #setDetailedSummary(boolean)
	 */
	public void summarize(final String header) {

		if (header != null && !header.trim().isEmpty()) {
			appendRow(header);
		} else {
			appendRow(profile.identifier());
		}

		final int row = getRowCount() - 1;
		if (detailedSummary)
			set(getCol("Unit"), row, profile.spatialCalibration().getUnit());
		set(getCol("Center"), row, profile.center());
		set(getCol("Start radius"), row, profile.startRadius());
		set(getCol("End radius"), row, profile.endRadius());
		set(getCol("Radius step"), row, profile.stepSize());

		final Properties props = profile.getProperties();

		// Image Properties
		if (Profile.SRC_IMG.equals(props.getProperty(Profile.KEY_SOURCE))) {

			final String thresh = props.getProperty(Profile.KEY_THRESHOLD_RANGE, "-1:-1");
			set(getCol("Threshold range"), row, thresh);

			if (detailedSummary) {
				final int c = Integer.valueOf(props.getProperty(Profile.KEY_CHANNEL_POS));
				final int z = Integer.valueOf(props.getProperty(Profile.KEY_SLICE_POS));
				final int t = Integer.valueOf(props.getProperty(Profile.KEY_FRAME_POS));
				set(getCol("CZT Position "), row, "" + c + ":" + z + ":" + t);
			}
			final int nSamples = Integer.valueOf(props.getProperty(Profile.KEY_NSAMPLES, "1"));
			set(getCol("Samples/radius"), row, nSamples);
			set(getCol("Samples/radius integration"), row, (nSamples == 1) ? "NA" : nSamples);

		}

		if (stats == null)
			return;

		for (final ShollStats stat : stats) {

			if (stat == null)
				continue;

			if (stat instanceof LinearProfileStats) {
				final LinearProfileStats lStats = (LinearProfileStats) stat;
				if (detailedSummary) {
					final String pLabel = (lStats.isPrimaryBranchesInferred()) ? "(inferred)" : "(specified)";
					set(getCol("I branches " + pLabel), row, lStats.getPrimaryBranches());
				}
				addLinearStats(row, lStats, false);
				if (lStats.validFit())
					addLinearStats(row, lStats, true);

			} else if (stat instanceof NormalizedProfileStats) {
				final NormalizedProfileStats nStats = (NormalizedProfileStats) stat;
				set(getCol("Sholl decay"), row, nStats.getShollDecay());
				set(getCol("Method"), row, nStats.getMethodDescription());
				set(getCol("Normalizer"), row, nStats.getNormalizerDescription());
				set(getCol("R^2"), row, nStats.getRSquaredOfFit());
				set(getCol("r"), row, nStats.getR());
				if (detailedSummary) {
					set(getCol("Determination ratio"), row, nStats.getDeterminationRatio());
					set(getCol("Regression intercept"), row, nStats.getIntercept());
					set(getCol("Regression slope"), row, nStats.getSlope());
				}
			}
		}
	}

	private void addLinearStats(final int row, final LinearProfileStats lStats, final boolean fData) {

		set(getCol(getHeader("Max inters.", fData)), row, lStats.getMax(fData));
		set(getCol(getHeader("Max inters. radius", fData)), row, lStats.getCenteredMaximum(fData).x);
		set(getCol(getHeader("Sum inters.", fData)), row, lStats.getSum(fData));
		set(getCol(getHeader("Mean inters.", fData)), row, lStats.getMean(fData));

		if (detailedSummary) {
			set(getCol(getHeader("Median inters.", fData)), row, lStats.getMedian(fData));
			set(getCol(getHeader("Skeweness", fData)), row, lStats.getSkewness(fData));
			set(getCol(getHeader("Kurtosis", fData)), row, lStats.getKurtosis(fData));
		}

		set(getCol(getHeader("Ramification index", fData)), row, lStats.getRamificationIndex(fData));

		final UPoint centroid = lStats.getCentroid(fData);
		set(getCol(getHeader("Centroid value", fData)), row, centroid.y);
		set(getCol(getHeader("Centroid radius", fData)), row, centroid.x);

		try {
			if (prefService != null) {
				final int cutoff = prefService.getInt(Prefs.class, "enclosingRadiusCutoff",
						Prefs.DEF_ENCLOSING_RADIUS_CUTOFF);
				set(getCol(getHeader("Enclosing radius", fData)), row, lStats.getEnclosingRadius(fData, cutoff));
			}
		} catch (final NullContextException ignored) {
			// move on;
		}

		if (fData) {
			// things that only make sense for fitted data
			set(getCol("Polyn. degree"), row, lStats.getPolynomialDegree());
			set(getCol("Polyn. R^2"), row, lStats.getRSquaredOfFit(true));
			if (detailedSummary) {
				set(getCol("Polyn. R^2 (adj)"), row, lStats.getRSquaredOfFit(true));
				if (profile.size() > 2)
					set(getCol("K-S p-value"), row, lStats.getKStestOfFit());
			}
		} else {
			// things that only make sense for sampled data
			set(getCol("Intersecting radii"), row, lStats.getIntersectingRadii(fData));
		}

	}

	private String getHeader(final String metric, final boolean fittedData) {
		if ("Max inters. radius".equals(metric) && fittedData) {
			return "Critical radius";
		} else if ("Max inters.".equals(metric) && fittedData) {
			return "Critical value";
		}
		return (fittedData) ? (metric + " (fit)") : (metric + " (sampled)");
	}

	private int getCol(final String header) {
		return getCol(this, header);
	}

	private int getCol(final DefaultGenericTable table, final String header) {
		int idx = table.getColumnIndex(header);
		if (idx == -1) {
			table.appendColumn(header);
			idx = table.getColumnCount() - 1;
		}
		return idx;
	}

	/**
	 * Sets the services required by this ShollTable, namely {@link PrefService},
	 * used to read advanced options set by {@link Prefs}.
	 *
	 * @param context the SciJava application context
	 * @throws IllegalStateException    If this ShollTable already has a context
	 * @throws IllegalArgumentException If {@code context} cannot provide the
	 *                                  services required by this ShollTable
	 */
	public void setContext(final Context context) throws IllegalStateException, IllegalArgumentException {
		context.inject(this);
		if (prefService != null) {
			final boolean detailedMetrics = prefService.getBoolean(Prefs.class, "detailedMetrics", Prefs.DEF_DETAILED_METRICS);
			setDetailedSummary(detailedMetrics);
		}
	}

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(final String[] args) {
		// TODO Auto-generated method stub

	}

}
