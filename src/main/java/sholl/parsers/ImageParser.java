package sholl.parsers;

import java.util.ArrayList;
import java.util.Properties;

import org.scijava.Context;
import org.scijava.app.StatusService;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.Helper;
import sholl.Profile;
import sholl.ShollUtils;
import sholl.UPoint;

public class ImageParser implements Parser {

	protected Profile profile;
	protected Properties properties;
	protected UPoint center;
	protected ArrayList<Double> radii;

	protected Context context;
	protected StatusService statusService;
	protected final ImagePlus imp;
	protected final Calibration cal;
	protected final double voxelSize;
	protected double lowerT = ImageProcessor.NO_THRESHOLD;
	protected double upperT = ImageProcessor.NO_THRESHOLD;
	protected int channel;
	protected int frame;
	protected int minX, maxX;
	protected int minY, maxY;
	protected int minZ, maxZ;
	protected int xc;
	protected int yc;
	protected int zc;
	protected long start;

	protected volatile boolean running = true;

	protected ImageParser(final ImagePlus imp) {
		this(imp, (Context) IJ.runPlugIn("org.scijava.Context", ""));
	}

	protected ImageParser(final ImagePlus imp, final Context context) {
		this.imp = imp;
		this.context = context;
		if (imp.getProcessor().isBinary())
			setThreshold(1, 255);
		cal = imp.getCalibration(); // never null
		if (imp.getNDimensions() > 2) {
			voxelSize = (cal.pixelWidth + cal.pixelHeight + cal.pixelDepth) / 3;
		} else {
			voxelSize = (cal.pixelWidth + cal.pixelHeight) / 2;
		}
		statusService = context.getService(StatusService.class);
		initProfile();
	}

	private void initProfile() {
		profile = new Profile();
		profile.assignImage(imp);
		properties = profile.getProperties();
	}

	public double getIsotropicVoxelSize() {
		return voxelSize;
	}

	public void setCenterPx(final int x, final int y, final int z) {
		if (x > imp.getWidth() - 1 || y > imp.getHeight() || z > imp.getNSlices())
			throw new IndexOutOfBoundsException("specified coordinates cannot be aplied to image");
		center = new UPoint(x, y, z, cal);
		profile.setCenter(center);
		xc = x;
		yc = y;
		zc = z;
	}

	public void setCenter(final double x, final double y, final double z) {
		center = new UPoint(x, y, z);
		profile.setCenter(center);
		xc = (int) center.rawX(cal);
		yc = (int) center.rawY(cal);
		zc = (int) center.rawZ(cal);
	}

	public void setThreshold(final double lower, final double upper) {
		lowerT = lower;
		upperT = upper;
	}

	public void setRadii(final double[] radiiArray) {
		if (radiiArray == null) {
			throw new NullPointerException("radii array cannot be null");
		}
		if (radii == null)
			radii = new ArrayList<>();
		radii.clear();
		for (final double r : radiiArray) {
			radii.add(r);
		}
	}

	public void setRadii(final double startRadius, final double step, final double endRadius) {
		final double fStartRadius = (Double.isNaN(startRadius)) ? voxelSize : Math.max(voxelSize, startRadius);
		final double maxRadius = maxPossibleRadius();
		final double fEndRadius = (Double.isNaN(endRadius)) ? maxRadius : Math.min(endRadius, maxRadius);
		final double fStep = (Double.isNaN(step)) ? voxelSize : Math.max(step, voxelSize);
		radii = ShollUtils.getRadii(fStartRadius, fStep, fEndRadius);
	}

	public double maxPossibleRadius() {
		final double maxX = imp.getWidth() - 1 * cal.pixelWidth;
		final double maxY = imp.getHeight() - 1 * cal.pixelHeight;
		final double maxZ = imp.getNSlices() - 1 * cal.pixelDepth;
		final UPoint[] points = new UPoint[8];
		points[0] = new UPoint(0, 0, 0);
		points[1] = new UPoint(maxX, maxY, maxZ);
		if (center == null)
			return points[0].euclideanDxTo(points[1]);
		points[2] = new UPoint(maxX, 0, 0);
		points[3] = new UPoint(0, maxY, 0);
		points[4] = new UPoint(maxX, maxY, 0);
		points[5] = new UPoint(0, 0, maxZ);
		points[6] = new UPoint(maxX, 0, maxZ);
		points[7] = new UPoint(0, maxY, maxZ);
		double max = 0;
		for (final UPoint p : points)
			max = Math.max(max, center.distanceSquared(p));
		return Math.sqrt(max);
	}

	protected void checkUnsetFields(final boolean includeThreshold) {
		if (center == null || radii == null)
			throw new NullPointerException("Cannot proceed with undefined parameters");
		if (includeThreshold && (upperT == ImageProcessor.NO_THRESHOLD || lowerT == ImageProcessor.NO_THRESHOLD))
			throw new NullPointerException("Cannot proceed with undefined threshold levels");
	}

	protected void checkUnsetFields() {
		checkUnsetFields(true);
	}

	public void setHemiShells(final String flag) {
		checkUnsetFields(false);
		final int maxRadius = (int) Math.round(radii.get(radii.size() - 1) / voxelSize);
		minX = Math.max(xc - maxRadius, 0);
		maxX = Math.min(xc + maxRadius, imp.getWidth() - 1);
		minY = Math.max(yc - maxRadius, 0);
		maxY = Math.min(yc + maxRadius, imp.getHeight() - 1);
		minZ = Math.max(zc - maxRadius, 0);
		maxZ = Math.min(zc + maxRadius, imp.getNSlices() - 1);

		final String fFlag = ShollUtils.extractHemiShellFlag(flag);
		switch (fFlag) {
		case HEMI_NORTH:
			maxY = Math.min(yc + maxRadius, yc);
			break;
		case HEMI_SOUTH:
			minY = Math.max(yc - maxRadius, yc);
			break;
		case HEMI_WEST:
			minX = xc;
			break;
		case HEMI_EAST:
			maxX = xc;
			break;
		case HEMI_NONE:
			break;
		default:
			throw new IllegalArgumentException("Unrecognized flag: " + flag);
		}
		properties.setProperty(KEY_HEMISHELLS, fFlag);

	}

	public int[][] getCircumferencePoints(final int cx, final int cy, final int radius) {

		// Initialize algorithm variables
		int i = 0, x = 0, y = radius;
		final int r = radius + 1;
		int err = 0, errR, errD;

		// Array to store first 1/8 of points relative to center
		final int[][] data = new int[r][2];

		do {
			// Add this point as part of the circumference
			data[i][0] = x;
			data[i++][1] = y;

			// Calculate the errors for going right and down
			errR = err + 2 * x + 1;
			errD = err - 2 * y + 1;

			// Choose which direction to go
			if (Math.abs(errD) < Math.abs(errR)) {
				y--;
				err = errD; // Go down
			} else {
				x++;
				err = errR; // Go right
			}
		} while (x <= y);

		// Create an array to hold the absolute coordinates
		final int[][] points = new int[r * 8][2];

		// Loop through the relative circumference points
		for (i = 0; i < r; i++) {

			// Pull out the point for quick access;
			x = data[i][0];
			y = data[i][1];

			// Convert the relative point to an absolute point
			points[i][0] = x + cx;
			points[i][1] = y + cy;

			// Use geometry to calculate remaining 7/8 of the circumference
			// points
			points[r * 4 - i - 1][0] = x + cx;
			points[r * 4 - i - 1][1] = -y + cy;
			points[r * 8 - i - 1][0] = -x + cx;
			points[r * 8 - i - 1][1] = y + cy;
			points[r * 4 + i][0] = -x + cx;
			points[r * 4 + i][1] = -y + cy;
			points[r * 2 - i - 1][0] = y + cx;
			points[r * 2 - i - 1][1] = x + cy;
			points[r * 2 + i][0] = y + cx;
			points[r * 2 + i][1] = -x + cy;
			points[r * 6 + i][0] = -y + cx;
			points[r * 6 + i][1] = x + cy;
			points[r * 6 - i - 1][0] = -y + cx;
			points[r * 6 - i - 1][1] = -x + cy;

		}

		// Count how many points are out of bounds, while eliminating
		// duplicates. Duplicates are always at multiples of r (8 points)
		int pxX, pxY, count = 0, j = 0;
		for (i = 0; i < points.length; i++) {

			// Pull the coordinates out of the array
			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && withinXYbounds(pxX, pxY))
				count++;
		}

		// Create the final array containing only unique points within bounds
		final int[][] refined = new int[count][2];

		for (i = 0; i < points.length; i++) {

			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && withinXYbounds(pxX, pxY)) {
				refined[j][0] = pxX;
				refined[j++][1] = pxY;
			}

		}

		// Return the array
		return refined;

	}
	protected void setPosition(final int channel, final int frame) {
		if (channel < 1 || channel > imp.getNChannels() || frame < 1 || frame > imp.getNFrames())
			throw new IllegalArgumentException("Specified (channel, slice, frame) position is out of range");
		this.channel = channel; // 1-based
		this.frame = frame; // 1-based
		properties.setProperty(KEY_CHANNEL_POS, String.valueOf(channel));
		properties.setProperty(KEY_FRAME_POS, String.valueOf(frame));
	}

	protected boolean withinThreshold(final double value) {
		return (value >= lowerT && value <= upperT);
	}

	protected boolean withinXYbounds(final int x, final int y) {
		return (x >= minX && x <= maxX && y >= minY && y <= maxY);
	}

	protected boolean withinZbounds(final int z) {
		return (z >= minZ && z <= maxZ);
	}

	protected boolean withinBounds(final int x, final int y, final int z) {
		return withinXYbounds(x, y) && withinZbounds(z);
	}

	protected void clearStatus() {
		final String a = Helper.getElapsedTime(start);
		statusService.showStatus(0, 0, "Finished. " + a);
	}

	@Override
	public void parse() {
	}

	@Override
	public Profile getProfile() {
		return profile;
	}

	@Override
	public boolean successful() {
		return !profile.isEmpty();
	}

	@Override
	public void terminate() {
		running = false;
	}

	public void reset() {
		initProfile();
	}

}
