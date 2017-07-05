package sholl.parsers;

import java.util.ArrayList;
import java.util.Properties;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.Profile;
import sholl.UPoint;
import sholl.ShollUtils;

class ImageParser implements Parser {

	@Parameter
	protected Context context;

	@Parameter
	protected LogService logService;

	@Parameter
	protected StatusService statusService;

	protected final Profile profile;
	protected final Properties properties;
	protected UPoint center;
	protected ArrayList<Double> radii;

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

	protected ImageParser(final ImagePlus imp) {
		if (context == null)
			context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		if (logService == null)
			logService = context.getService(LogService.class);
		if (statusService == null)
			statusService = context.getService(StatusService.class);
		this.imp = imp;
		if (imp.getProcessor().isBinary())
			setThreshold(1, 255);
		cal = imp.getCalibration(); // never null
		if (imp.getNDimensions() > 2)
			voxelSize = Math.cbrt(cal.pixelWidth * cal.pixelHeight * cal.pixelDepth);
		else
			voxelSize = Math.sqrt(cal.pixelWidth * cal.pixelHeight);
		profile = new Profile();
		profile.assignImage(imp);
		properties = profile.getProperties();
	}

	@Override
	public boolean successful() {
		return profile.size() > 0;
	}

	public void setCenterPx(final int x, final int y, final int z) {
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

	protected void setRadii(final double startRadius, final double step, final double endRadius) {
		final double fStartRadius = (Double.isNaN(startRadius)) ? voxelSize : Math.max(voxelSize, startRadius);
		final double maxRadius = maxPossibleRadius();
		final double fEndRadius = (Double.isNaN(endRadius)) ? maxRadius : Math.min(endRadius, maxRadius);
		final double fStep = (Double.isNaN(step)) ? voxelSize : Math.max(step, voxelSize);
		radii = ShollUtils.getRadii(fStartRadius, fStep, fEndRadius);
	}

	protected double maxPossibleRadius() {
		final double maxX = imp.getWidth() * cal.pixelWidth;
		final double maxY = imp.getHeight() * cal.pixelHeight;
		final double maxZ = imp.getNSlices() * cal.pixelDepth;
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

	protected void checkUnsetFields() {
		if (center == null || radii == null || upperT == ImageProcessor.NO_THRESHOLD
				|| lowerT == ImageProcessor.NO_THRESHOLD)
			throw new NullPointerException("Cannot proceed with undefined parameters");
	}

	public void setHemiShells(final String flag) {
		checkUnsetFields();
		final int maxRadius = (int) Math.round(radii.get(radii.size() - 1) / voxelSize);
		minX = Math.max(xc - maxRadius, 0);
		maxX = Math.min(xc + maxRadius, imp.getWidth());
		minY = Math.max(yc - maxRadius, 0);
		maxY = Math.min(yc + maxRadius, imp.getHeight());
		minZ = Math.max(zc - maxRadius, 1);
		maxZ = Math.min(zc + maxRadius, imp.getNSlices());
		final String fFlag = (flag == null || flag.isEmpty()) ? HEMI_NONE : flag.trim().toLowerCase();
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

	protected void setPosition(final int channel, final int frame) {
		if (channel < 1 || channel > imp.getNChannels() || frame < 1 || frame > imp.getNFrames())
			throw new IllegalArgumentException("Specified (channel, slice, frame) position is out of range");
		this.channel = channel;
		this.frame = frame;
		properties.setProperty(KEY_CHANNEL_POS, String.valueOf(channel));
		properties.setProperty(KEY_FRAME_POS, String.valueOf(frame));
	}

	@Override
	public Profile parse() {
		return profile;
	}
}