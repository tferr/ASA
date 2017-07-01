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

import java.awt.Color;
import java.awt.geom.Arc2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.gui.ShollPlot;

/**
 * Class defining a sholl profile
 *
 * @author Tiago Ferreira
 */
public class Profile implements ProfileProperties {

	private SortedSet<ProfileEntry> profile;
	private UPoint center;
	private Calibration cal;
	private Properties properties;

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
		if (imp.getProcessor().isBinary())
			properties.setProperty(KEY_SOURCE, SRC_IMG_BINARY);
		else if (imp.getProcessor().getMinThreshold() != ImageProcessor.NO_THRESHOLD)
			properties.setProperty(KEY_SOURCE, SRC_IMG_THRESH);
		else
			properties.setProperty(KEY_SOURCE, SRC_IMG);
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

	private Overlay getOverlay(final ImagePlus imp) {
		if (imp == null)
			return new Overlay();
		final Overlay overlay = imp.getOverlay();
		if (overlay == null)
			return new Overlay();
		if (overlay.size() == 0)
			return overlay;
		for (int i = overlay.size() - 1; i >= 0; i--) {
			final String roiName = overlay.get(i).getName();
			if (roiName != null && (roiName.equals("center") || roiName.contains("r=")))
				overlay.remove(i);
		}
		return overlay;
	}

	public Overlay getROIs() {
		return getROIs(null);
	}

	// If set coordinates of points are scaled to pixel coordinates using the
	// profile calibration, otherwise the image calibration is used
	public Overlay getROIs(final ImagePlus imp) {

		if (center == null)
			throw new RuntimeException("ROIs cannot be generated with undefined center");

		final Overlay overlay = getOverlay(imp);
		final Calibration cal = scaled() ? this.cal : new Calibration(imp);

		// Get hyperstck position
		final int channel = Integer.valueOf(properties.getProperty(KEY_CHANNEL_POS, "1"));
		final int slice = Integer.valueOf(properties.getProperty(KEY_SLICE_POS, "1"));
		final int frame = Integer.valueOf(properties.getProperty(KEY_FRAME_POS, "1"));

		// Add center
		final double centerRawX = center.rawX(cal);
		final double centerRawY = center.rawY(cal);
		final double centerRawZ = center.rawZ(cal);
		final PointRoi cRoi = new PointRoi(centerRawX, centerRawY);
		cRoi.setPosition((int) centerRawZ);
		cRoi.setPointType(1);
		cRoi.setPosition(channel, slice, frame);
		overlay.add(cRoi, "center");

		final DecimalFormat formatter = new DecimalFormat("#000.##");
		// Add intersection points
		for (final ProfileEntry entry : profile) {
			final Set<UPoint> points = entry.points;
			if (points == null || points.isEmpty())
				continue;
			PointRoi multipointRoi = null;
			double currentRawZ = -1;
			for (final UPoint point : points) {

				final double rawX = point.rawX(cal);
				final double rawY = point.rawY(cal);
				final double rawZ = point.rawZ(cal);
				if (currentRawZ == -1 || currentRawZ != rawZ) {
					multipointRoi = new PointRoi(rawX, rawY);
					currentRawZ = rawZ;
					multipointRoi.setPointType(2);
					multipointRoi.setPosition(channel, (int) rawZ, frame);
					overlay.add(multipointRoi,
							"ShollPoints r=" + formatter.format(entry.radius) + " z=" + formatter.format(point.z));
				} else if (currentRawZ == rawZ) { // same plane
					multipointRoi.addPoint(rawX, rawY);
				}
			}
		}

		if (!is2D()) {
			// throw new RuntimeException("Non-2D ROIS are not currently
			// supported");
			return overlay;
		}

		// Add Shells
		final Color rc = Roi.getColor();
		final Color shellColor = new Color(rc.getRed(), rc.getGreen(), rc.getBlue(), 100);
		final int shellThickness = Integer.valueOf(properties.getProperty(KEY_NSAMPLES, "1"));

		// 2D analysis: circular shells
		final String sProperty = properties.getProperty(KEY_HEMISHELLS, HEMI_NONE);
		final boolean arcs = !HEMI_NONE.equals(sProperty);
		final boolean north = arcs && sProperty.contains(HEMI_NORTH);
		final boolean south = arcs && sProperty.contains(HEMI_SOUTH);
		final boolean west = arcs && sProperty.contains(HEMI_WEST);
		final boolean east = arcs && sProperty.contains(HEMI_EAST);

		for (final ProfileEntry entry : profile) {
			final double radiusX = cal.getRawX(entry.radius);
			final double radiusY = cal.getRawY(entry.radius);

			Roi shell;
			if (arcs) {
				final Arc2D.Double arc = new Arc2D.Double();
				final double radius = Math.sqrt(radiusX * radiusY);
				if (north) {
					arc.setArcByCenter(centerRawX, centerRawY, radius, 0, 180, Arc2D.OPEN);
				} else if (south) {
					arc.setArcByCenter(centerRawX, centerRawY, radius, -180, 180, Arc2D.OPEN);
				} else if (west) {
					arc.setArcByCenter(centerRawX, centerRawY, radius, 90, -180, Arc2D.OPEN);
				} else if (east) {
					arc.setArcByCenter(centerRawX, centerRawY, radius, -90, -180, Arc2D.OPEN);
				}
				shell = new ShapeRoi(arc);
			} else {
				shell = new OvalRoi(centerRawX - radiusX, centerRawY - radiusY, 2 * radiusX, 2 * radiusY);
			}
			shell.setStrokeColor(shellColor);
			shell.setStrokeWidth(shellThickness);
			overlay.add(shell, "Shell r=" + ShollUtils.d2s(entry.radius));
		}

		if (imp != null)
			imp.setOverlay(overlay);

		return overlay;
	}

	public boolean add(final ProfileEntry entry) {
		return profile.add(entry);
	}
}
