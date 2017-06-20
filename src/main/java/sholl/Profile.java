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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import sholl.gui.ShollPlot;
import sholl.math.LinearProfileStats;
import sholl.parsers.Parser;

/**
 * Class defining a sholl profile
 *
 * @author Tiago Ferreira
 */
public class Profile {

	private SortedSet<ProfileEntry> profile;
	private Parser parser;
	private String identifier;
	private String spatialUnit;
	private boolean hemiShells;
	private boolean fitted;
	private Point2D.Double center;

	public Profile(final Parser parser) {
		initialize();
		this.parser = parser;
	}

	/**
	 * Default constructor.
	 *
	 * @param radii
	 *            sampled radii
	 * @param sampledInters
	 *            sampled intersection counts
	 */
	public Profile(final ArrayList<Number> radii, final ArrayList<Number> sampledInters) {
		initialize();
		if (radii == null || sampledInters == null)
			throw new NullPointerException("Lists cannot be null");
		final int n = radii.size();
		if (n == 0 || n != sampledInters.size())
			throw new IllegalArgumentException("Lists cannot be empty and must have the same size");
		initialize();
		for (int i = 0; i < radii.size(); i++) {
			profile.add(new ProfileEntry(radii.get(i), sampledInters.get(i)));
		}

	}

	public Profile(final Number[] radii, final Number[] sampledInters) {
		this(arrayToList(radii), arrayToList(sampledInters));
	}

	/**
	 * Constructor accepting matrices.
	 *
	 * @param sampledData
	 *            sampled data in [n][2] format, where n = number of points
	 *            (radii: [n][0]; sampledInters: [n][1])
	 */
	public Profile(final double[][] sampledData) {
		if (sampledData == null)
			throw new NullPointerException("Matrix cannot be null");
		initialize();
		for (int i = 0; i < sampledData.length; i++) {
			final double r = sampledData[i][0];
			final double c = sampledData[i][1];
			profile.add(new ProfileEntry(r, c));
		}
	}

	private void initialize() {
		profile = Collections.synchronizedSortedSet(new TreeSet<ProfileEntry>());
	}

	public String identifier() {
		return identifier;
	}

	public void setIdentifier(final String identifier) {
		this.identifier = identifier;
	}

	public String spatialUnit() {
		return spatialUnit;
	}

	public void setSpatialUnit(final String spatialUnit) {
		this.spatialUnit = spatialUnit;
	}

	public ArrayList<Double> radii() {
		final ArrayList<Double> radii = new ArrayList<>();
		for (final ProfileEntry e : profile)
			radii.add(e.radius);
		return radii;
	}

	public double[] radiiAsArray() {
		return radii().stream().mapToDouble(d -> d).toArray();
	}

	public ArrayList<Double> counts() {
		final ArrayList<Double> counts = new ArrayList<>();
		for (final ProfileEntry e : profile)
			counts.add(e.count);
		return counts;
	}

	public double[] countsAsArray() {
		return counts().stream().mapToDouble(d -> d).toArray();
	}

	public ArrayList<ArrayList<ShollPoint>> points() {
		final ArrayList<ArrayList<ShollPoint>> allPoints = new ArrayList<>();
		for (final ProfileEntry e : profile)
			allPoints.add(e.points);
		return allPoints;
	}

	public void trimZeroCounts() {
		final Iterator<ProfileEntry> iter = profile.iterator();
		while (iter.hasNext()) {
			if (iter.next().count == 0)
				iter.remove();
		}
	}

	public void trimNaNCounts() {
		final Iterator<ProfileEntry> iter = profile.iterator();
		while (iter.hasNext()) {
			if (Double.isNaN(iter.next().count))
				iter.remove();
		}
	}

	public Set<ProfileEntry> entries() {
		return profile;
	}

	public String source() {
		switch (getParser().source()) {
		case Parser.SOURCE_IMP:
			return "Image";
		case Parser.SOURCE_TRACES:
			return "Traces";
		case Parser.SOURCE_SWC:
			return "SWC";
		case Parser.SOURCE_TABULAR:
			return "Table";
		case Parser.SOURCE_STATS:
			return "Stats";
		case Parser.SOURCE_OTHER:
		default:
			return "Other";
		}
	}

	public boolean is2D() {
		return parser != null && parser.space() == Parser.TWO_D;
	}

	public String spatialDimention() {
		if (parser == null)
			return null;
		switch (getParser().space()) {
		case Parser.THREE_D:
			return "3D";
		case Parser.TWO_D:
			return "2D";
		case Parser.UNKNOWN_D:
		default:
			return "Unknown";
		}
	}

	public boolean hemiShells() {
		return hemiShells;
	}

	public ShollPlot plot() {
		String plotTitle = idDescription().toString();
		if (plotTitle.isEmpty() || "null".equals(plotTitle))
			plotTitle = "Sholl Profile";
		String xTitle = distanceDescription().toString();
		if (xTitle.isEmpty() || "null".equals(xTitle))
			xTitle = "Distance";
		return new ShollPlot(plotTitle, xTitle, "No. Intersections", new LinearProfileStats(this), true);
	}

	@Override
	public String toString() {
		return new StringBuilder("ID=[").append(idDescription()).append("] Sampling=[").append(distanceDescription())
				.append("] N=[").append(size()).append(" entries]").toString();
	}

	private StringBuilder idDescription() {
		final StringBuilder sb = new StringBuilder();
		sb.append(identifier);
		if (center() != null)
			sb.append(" (").append(center).append(")");
		return sb;
	}

	private StringBuilder distanceDescription() {
		final StringBuilder sb = new StringBuilder();
		if (is2D())
			sb.append("2D ");
		if (hemiShells())
			sb.append(" - HemiShells");
		if (spatialUnit() != null)
			sb.append(" (").append(spatialUnit()).append(")");
		return sb;
	}

	public void setHemiShells(final boolean hemiShells) {
		this.hemiShells = hemiShells;
	}

	public Point2D.Double center() {
		return center;
	}

	public void setCenter(final Point2D.Double center) {
		this.center = center;
	}

	public boolean isFitted() {
		return fitted;
	}

	public void setIsFitted(final boolean fitted) {
		this.fitted = fitted;
	}

	public Parser getParser() {
		return parser;
	}

	private static ArrayList<Number> arrayToList(final Number[] array) {
		final ArrayList<Number> list = new ArrayList<>();
		for (final Number i : array)
			list.add(i);
		return list;
	}

	public int size() {
		return profile.size();
	}

}
