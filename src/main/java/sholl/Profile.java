/*
 * #%L
 * Sholl Analysis plugin for ImageJ.
 * %%
 * Copyright (C) 2005 - 2020 Tiago Ferreira.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.gui.ShollOverlay;
import sholl.gui.ShollPlot;

/**
 * Class defining a Sholl profile
 *
 * @author Tiago Ferreira
 */
public class Profile implements ProfileProperties {

	private SortedSet<ProfileEntry> profile;
	private UPoint center;
	private Calibration cal = new Calibration();
	private Properties properties;
	private double stepRadius = -1;

	/** Instantiates a new empty profile. */
	public Profile() {
		initialize();
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
		if (radii == null || sampledInters == null)
			throw new IllegalArgumentException("Lists cannot be null");
		final int n = radii.size();
		if (n == 0 || n != sampledInters.size())
			throw new IllegalArgumentException("Lists cannot be empty and must have the same size");
		initialize();
		for (int i = 0; i < radii.size(); i++) {
			profile.add(new ProfileEntry(radii.get(i), sampledInters.get(i)));
		}

	}

	/**
	 * Legacy constructor accepting arrays.
	 *
	 * @param radii
	 *            sampled radii
	 * @param sampledInters
	 *            sampled intersection counts
	 */
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
			throw new IllegalArgumentException("Matrix cannot be null");
		initialize();
		for (int i = 0; i < sampledData.length; i++) {
			final double r = sampledData[i][0];
			final double c = sampledData[i][1];
			profile.add(new ProfileEntry(r, c));
		}
	}

	private void initialize() {
		profile = Collections.synchronizedSortedSet(new TreeSet<ProfileEntry>());
		properties = new Properties();
	}

	public String identifier() {
		return properties.getProperty(KEY_ID);
	}

	public void setIdentifier(final String identifier) {
		properties.setProperty(KEY_ID, identifier);
	}

	public ArrayList<Double> radii() {
		final ArrayList<Double> radii = new ArrayList<>();
		for (final ProfileEntry e : profile)
			radii.add(e.radius);
		return radii;
	}

	public ArrayList<Double> radiiSquared() {
		final ArrayList<Double> radii = new ArrayList<>();
		for (final ProfileEntry e : profile)
			radii.add(e.radius * e.radius);
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

	public ArrayList<Set<UPoint>> points() {
		final ArrayList<Set<UPoint>> allPoints = new ArrayList<>();
		for (final ProfileEntry e : profile)
			allPoints.add(e.points);
		return allPoints;
	}

	public double getCountAtRadius(final double radius) {
		if (stepRadius == -1) stepRadius = calculateStepRadius();
		for (final ProfileEntry entry : profile) {
			if (entry.radius < radius + stepRadius && entry.radius >= radius - stepRadius) {
				return entry.count;
			}
		}
		return Double.NaN;
	}

	public void trimZeroEntries() {
		final Iterator<ProfileEntry> iter = profile.iterator();
		while (iter.hasNext()) {
			final ProfileEntry entry = iter.next();
			if (entry.radius == 0 || entry.count == 0)
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

	public void scale(final double xScale, final double yScale, final double zScale) {
		final double isotropicScale = Math.cbrt(xScale * yScale * zScale);
		if (Double.isNaN(isotropicScale) || isotropicScale <= 0)
			throw new IllegalArgumentException("Invalid scaling factors");
		final Iterator<ProfileEntry> iter = profile.iterator();
		if (center != null)
			center.scale(xScale, yScale, zScale);
		while (iter.hasNext()) {
			final ProfileEntry entry = iter.next();
			entry.radius *= isotropicScale;
			if (entry.points == null)
				continue;
			for (final UPoint point : entry.points)
				point.scale(xScale, yScale, zScale);
		}
	}

	public SortedSet<ProfileEntry> entries() {
		return profile;
	}

	public String source() {
		return properties.getProperty(KEY_SOURCE, UNSET);
	}

	public int nDimensions() {
		try {
			return Integer.valueOf(properties.getProperty(KEY_2D3D, "-1"));
		} catch (final NumberFormatException exc) {
			return -1;
		}
	}

	public boolean is2D() {
		return nDimensions() == 2;
	}

	public void setNDimensions(final int twoDthreeD) {
		switch (twoDthreeD) {
		case 1:
		case 2:
		case 3:
			properties.setProperty(KEY_2D3D, String.valueOf(twoDthreeD));
			return;
		default:
			properties.setProperty(KEY_2D3D, UNSET);
			return;
		}
	}

	public boolean scaled() {
		return cal != null && cal.scaled() && !String.valueOf(Double.NaN).equals(cal.getUnit());
	}

	public boolean hasPoints() {
		for (final Iterator<ProfileEntry> it = profile.iterator(); it.hasNext();) {
			final Set<UPoint> entryPoints = it.next().points;
			if (entryPoints != null && entryPoints.size() > 0)
				return true;
		}
		return false;
	}

	public ShollPlot plot() {
		return new ShollPlot(this);
	}

	@Override
	public String toString() {
		return properties.toString();
	}

	public UPoint center() {
		return center;
	}

	public void setCenter(final UPoint center) {
		this.center = center;
		properties.setProperty(KEY_CENTER, center.toString());
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

	public double startRadius() {
		return profile.first().radius;
	}

	public double stepSize() {
		if (stepRadius == -1) stepRadius = calculateStepRadius();
		return stepRadius;
	}

	private double calculateStepRadius() {
		double stepSize = 0;
		ProfileEntry previousEntry = null;
		for (final Iterator<ProfileEntry> it = profile.iterator(); it.hasNext();) {
			final ProfileEntry currentEntry = it.next();
			if (previousEntry != null)
				stepSize += currentEntry.radius - previousEntry.radius;
			previousEntry = currentEntry;
		}
		return stepSize / profile.size();
	}

	public double endRadius() {
		return profile.last().radius;
	}

	public Calibration spatialCalibration() {
		return cal;
	}

	public void assignImage(final ImagePlus imp) {
		if (imp == null)
			return;
		setSpatialCalibration(imp.getCalibration());
		setIdentifier(imp.getTitle());
		setNDimensions(imp.getNDimensions());
		properties.setProperty(KEY_SOURCE, SRC_IMG);
		if (imp.getProcessor().isBinary()) {
			properties.setProperty(KEY_THRESHOLD_RANGE, "255:255");
		}
		else {
			double lowerT =  imp.getProcessor().getMinThreshold();
			if (lowerT == ImageProcessor.NO_THRESHOLD) lowerT = -1;
			double upperT =  imp.getProcessor().getMaxThreshold();
			if (upperT == ImageProcessor.NO_THRESHOLD) upperT = -1;
			properties.setProperty(KEY_THRESHOLD_RANGE, ""+lowerT+":"+upperT);
		}
	}

	public void setSpatialCalibration(final Calibration cal) {
		this.cal = cal;
		properties.setProperty(KEY_CALIBRATION, cal.toString());
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(final Properties properties) {
		this.properties = properties;

		// Center
		center = UPoint.fromString(properties.getProperty(KEY_CENTER, UNSET));

		// Calibration
		final String[] calLines = properties.getProperty(KEY_CALIBRATION, UNSET).split(",");
		final Double vx = Double.parseDouble(getCalibrationValue(calLines, "w="));
		final Double vy = Double.parseDouble(getCalibrationValue(calLines, "h="));
		final Double vz = Double.parseDouble(getCalibrationValue(calLines, "d="));
		final String unit = getCalibrationValue(calLines, "unit=");
		cal = new Calibration();
		cal.setUnit(unit);
		cal.pixelWidth = vx;
		cal.pixelHeight = vy;
		cal.pixelDepth = vz;

	}

	private String getCalibrationValue(final String[] splittedCalibrationString, final String key) {
		for (String line : splittedCalibrationString) {
			line = line.trim();
			final int idx = line.indexOf(key);
			if (idx == -1)
				continue;
			return line.substring(idx + key.length());
		}
		return String.valueOf(Double.NaN);
	}

	public Overlay getROIs() {
		return getROIs(null);
	}

	public Overlay getROIs(final ImagePlus imp) {
		final ShollOverlay so = new ShollOverlay(this, imp);
		so.addCenter();
		if (!is2D())
			so.setShellsColor(null); // same as so.setShellsThickness(0);
		so.setPointsLUT("mpl-viridis.lut");
		return so.getOverlay();
	}

	public boolean add(final ProfileEntry entry) {
		final boolean result = profile.add(entry);
		if (result) stepRadius = -1;
		return result;
	}

	public int zeroCounts() {
		final Iterator<ProfileEntry> iter = profile.iterator();
		int count = 0;
		while (iter.hasNext()) {
			if (iter.next().count == 0)
				count++;
		}
		return count;
	}

	public boolean isEmpty() {
		return profile == null || profile.size() == zeroCounts();
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (getClass() != o.getClass()) {
			return false;
		}
		final Profile other = (Profile) o;
		if (size() != other.size()) {
			return false;
		}
		if (startRadius() != other.startRadius()) {
			return false;
		}
		if (endRadius() != other.endRadius()) {
			return false;
		}
		return counts().equals(other.counts());
	}
}
