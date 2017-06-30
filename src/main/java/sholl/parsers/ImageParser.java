package sholl.parsers;

import java.util.ArrayList;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.Profile;
import sholl.ShollPoint;
import sholl.ShollUtils;

class ImageParser implements Parser {

	@Parameter
	protected Context context;

	@Parameter
	protected LogService logService;

	@Parameter
	protected StatusService statusService;

	protected final Profile profile;
	protected ShollPoint center;
	protected ArrayList<Double> radii;

	protected final ImagePlus imp;
	protected final Calibration cal;
	protected final double voxelSize;
	protected double lowerT = ImageProcessor.NO_THRESHOLD;
	protected double upperT = ImageProcessor.NO_THRESHOLD;
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
	}

	@Override
	public boolean successful() {
		return profile.size() > 0;
	}

	public void setCenterPx(final int x, final int y, final int z) {
		center = new ShollPoint(x, y, z, cal);
		profile.setCenter(center);
		xc = x;
		yc = y;
		zc = z;
	}

	public void setCenter(final double x, final double y, final double z) {
		center = new ShollPoint(x, y, z);
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
		final double fEndRadius = (Double.isNaN(endRadius)) ? maxPossibleRadius() : endRadius;
		final double fStep = (Double.isNaN(step)) ? voxelSize : Math.max(step, voxelSize);
		radii = ShollUtils.getRadii(fStartRadius, fStep, fEndRadius);
	}

	protected double maxPossibleRadius() {
		final double maxX = imp.getWidth() * cal.pixelWidth;
		final double maxY = imp.getHeight() * cal.pixelHeight;
		final double maxZ = imp.getNSlices() * cal.pixelDepth;
		final ShollPoint[] points = new ShollPoint[8];
		points[0] = new ShollPoint(0, 0, 0);
		points[1] = new ShollPoint(maxX, maxY, maxZ);
		if (center == null)
			return points[0].euclideanDxTo(points[1]);
		points[2] = new ShollPoint(maxX, 0, 0);
		points[3] = new ShollPoint(0, maxY, 0);
		points[4] = new ShollPoint(maxX, maxY, 0);
		points[5] = new ShollPoint(0, 0, maxZ);
		points[6] = new ShollPoint(maxX, 0, maxZ);
		points[7] = new ShollPoint(0, maxY, maxZ);
		double max = 0;
		for (final ShollPoint p : points)
			max = Math.max(max, center.distanceSquared(p));
		return Math.sqrt(max);
	}

	protected void checkUnsetFields() {
		if (center == null || radii == null || upperT == ImageProcessor.NO_THRESHOLD
				|| lowerT == ImageProcessor.NO_THRESHOLD)
			throw new NullPointerException("Cannot proceed with undefined parameters");
	}

	@Override
	public Profile parse() {
		return profile;
	}
}