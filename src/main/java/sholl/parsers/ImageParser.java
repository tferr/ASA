package sholl.parsers;

import java.util.ArrayList;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.Profile;
import sholl.ShollPoint;
import sholl.ShollUtils;

class ImageParser implements Parser {

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
		center = new ShollPoint(x, y);
		profile.setCenter(center);
		xc = (int) center.rawX(cal);
		yc = (int) center.rawY(cal);
		zc = (int) center.rawZ(cal);
	}

	public void setThreshold(final int lower, final int upper) {
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
		final ShollPoint p1 = new ShollPoint(0, 0, 0);
		final ShollPoint p2 = new ShollPoint(imp.getWidth() * cal.pixelWidth, imp.getHeight() * cal.pixelHeight,
				imp.getNSlices() * cal.pixelDepth);
		if (center == null)
			return p1.distanceTo(p2);
		return Math.max(center.distanceTo(p1), center.distanceTo(p2));
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