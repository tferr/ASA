package sholl.parsers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.math3.stat.StatUtils;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.TypeConverter;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.ShollPoint;

public class ImageParser2D extends ImageParser {

	@Parameter
	private Context context;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	private final Properties properties;
	private ImageProcessor ip;
	private int minX, maxX;
	private int minY, maxY;
	private int nSpans, spanType;
	private final boolean doSpikeSupression = true;
	private int channel, slice, frame;

	/** Flag for integration of repeated measures: average */
	public static final int MEAN = 0;
	/** Flag for integration of repeated measures: median */
	private static final int MEDIAN = 1;
	/** Flag for integration of repeated measures: mode */
	public static final int MODE = 2;
	private static final int NONE = -1;
	private final int MAX_N_SPANS = 10;

	public ImageParser2D(final ImagePlus imp) {
		super(imp);
		if (context == null)
			context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		if (logService == null)
			logService = context.getService(LogService.class);
		if (statusService == null)
			statusService = context.getService(StatusService.class);
		properties = profile.getProperties();
		setPosition(imp.getC(), imp.getZ(), imp.getT());
	}

	/** Debug method **/
	public static void main(final String... args) throws Exception {
		// final ImageJ ij = net.imagej.Main.launch(args);
		final ImageParser2D parser = new ImageParser2D(Sholl_Utils.sampleImage());
		parser.parse();
	}

	public void setCenterPx(final int x, final int y) {
		super.setCenterPx(x, y, 1);
	}

	public void setCenter(final double x, final double y) {
		super.setCenter(x, y, 0);
	}

	@Override
	public void setRadii(final double startRadius, final double step, final double endRadius) {
		setRadii(startRadius, step, endRadius, 1, NONE);
	}

	public void setRadii(final double startRadius, final double step, final double endRadius, final int span,
			final int integrationFlag) {
		super.setRadii(startRadius, step, endRadius);
		setRadiiSpan(span, integrationFlag);
	}

	private void setRadiiSpan(final int nSamples, final int integrationFlag) {
		nSpans = Math.max(1, Math.min(MAX_N_SPANS, nSamples));
		properties.setProperty(KEY_NSAMPLES, String.valueOf(nSpans));
		switch (integrationFlag) {
		case NONE:
			break;
		case MEDIAN:
			properties.setProperty(KEY_NSAMPLES_INTG, INTG_MEDIAN);
			break;
		case MODE:
			properties.setProperty(KEY_NSAMPLES_INTG, INTG_MODE);
			break;
		case MEAN:
			properties.setProperty(KEY_NSAMPLES_INTG, INTG_MEAN);
			break;
		default:
			throw new IllegalArgumentException("Unrecognized integration flag");
		}
		spanType = integrationFlag;
	}

	@Override
	public Profile parse() {
		checkUnsetFields();
		ip = getProcessor();
		if (UNSET.equals(properties.getProperty(KEY_HEMISHELLS, UNSET)))
			setHemiShells(HEMI_NONE);

		double[] binsamples;
		int[] pixels;
		int[][] points;

		final int size = radii.size();

		// Create array for bin samples. Passed value of binSize must be at
		// least 1
		binsamples = new double[nSpans];

		statusService.showStatus(
				"Sampling " + size + " radii, " + nSpans + " measurement(s) per radius. Press 'Esc' to abort...");

		// Outer loop to control the analysis bins
		int i = 0;
		for (final Double radius : radii) {

			// Retrieve the radius in pixel coordinates and set the largest
			// radius of this bin span
			int intRadius = (int) Math.round(radius / voxelSize + nSpans / 2);
			final Set<ShollPoint> pointsList = new HashSet<>();

			// Inner loop to gather samples for each sample
			for (int s = 0; s < nSpans; s++) {

				if (intRadius < 1)
					break;

				// Get the circumference pixels for this int radius
				points = getCircumferencePoints(xc, yc, intRadius--);
				pixels = getPixels(points);

				// Count the number of intersections
				final Set<ShollPoint> thisBinIntersPoints = targetGroupsPositions(pixels, points, ip);
				binsamples[s] = thisBinIntersPoints.size();
				pointsList.addAll(thisBinIntersPoints);
			}

			if (IJ.escapePressed()) {
				IJ.beep();
				return profile;
			}
			statusService.showProgress(i++, size * nSpans);

			// Statistically combine bin data
			double counts = 0;
			if (nSpans > 1) {
				if (spanType == MEDIAN) { // 50th percentile
					counts = StatUtils.percentile(binsamples, 50);
				} else if (spanType == MEAN) { // mean
					counts = StatUtils.mean(binsamples);
				} else if (spanType == MODE) { // the 1st max freq. element
					counts = StatUtils.mode(binsamples)[0];
				}
			} else { // There was only one sample
				counts = binsamples[0];
			}
			profile.add(new ProfileEntry(radius, counts, pointsList));

		}

		return profile;
	}

	public Set<ShollPoint> targetGroupsPositions(final int[] pixels, final int[][] rawpoints, final ImageProcessor ip) {

		int i, j;
		int[][] points;

		// Count how many target pixels (i.e., foreground, non-zero) we have
		for (i = 0, j = 0; i < pixels.length; i++)
			if (pixels[i] != 0.0)
				j++;

		// Create an array to hold target pixels
		points = new int[j][2];

		// Copy all target pixels into the array
		for (i = 0, j = 0; i < pixels.length; i++)
			if (pixels[i] != 0.0)
				points[j++] = rawpoints[i];

		return groupPositions(points, ip);

	}

	private void removeSinglePixels(final int[][] points, final int pointsLength, final int[] grouping,
			final ImageProcessor ip, final HashSet<Integer> positions) {

		for (int i = 0; i < pointsLength; i++) {

			// Check for other members of this group
			boolean multigroup = false;
			for (int j = 0; j < pointsLength; j++) {
				if (i == j)
					continue;
				if (grouping[i] == grouping[j]) {
					multigroup = true;
					break;
				}
			}

			// If not a single-pixel group, try again
			if (multigroup)
				continue;

			// Store the coordinates of this point
			final int dx = points[i][0];
			final int dy = points[i][1];

			// Calculate the 8 neighbors surrounding this point
			final int[][] testpoints = new int[8][2];
			testpoints[0][0] = dx - 1;
			testpoints[0][1] = dy + 1;
			testpoints[1][0] = dx;
			testpoints[1][1] = dy + 1;
			testpoints[2][0] = dx + 1;
			testpoints[2][1] = dy + 1;
			testpoints[3][0] = dx - 1;
			testpoints[3][1] = dy;
			testpoints[4][0] = dx + 1;
			testpoints[4][1] = dy;
			testpoints[5][0] = dx - 1;
			testpoints[5][1] = dy - 1;
			testpoints[6][0] = dx;
			testpoints[6][1] = dy - 1;
			testpoints[7][0] = dx + 1;
			testpoints[7][1] = dy - 1;

			// Pull out the pixel values for these points
			final int[] px = getPixels(testpoints);

			// Now perform the stair checks
			if ((px[0] != 0 && px[1] != 0 && px[3] != 0 && px[4] == 0 && px[6] == 0 && px[7] == 0)
					|| (px[1] != 0 && px[2] != 0 && px[4] != 0 && px[3] == 0 && px[5] == 0 && px[6] == 0)
					|| (px[4] != 0 && px[6] != 0 && px[7] != 0 && px[0] == 0 && px[1] == 0 && px[3] == 0)
					|| (px[3] != 0 && px[5] != 0 && px[6] != 0 && px[1] == 0 && px[2] == 0 && px[4] == 0)) {

				positions.remove(i);
			}

		}

	}

	public Set<ShollPoint> groupPositions(final int[][] points, final ImageProcessor ip) {

		int i, j, k, target, source, len, dx;

		// Create an array to hold the point grouping data
		final int[] grouping = new int[len = points.length];

		// Initialize each point to be in a unique group
		final HashSet<Integer> positions = new HashSet<>();
		for (i = 0; i < len; i++) {
			grouping[i] = i + 1;
			positions.add(i);
		}
		// int groups = len;

		for (i = 0; i < len; i++) {
			for (j = 0; j < len; j++) {

				// Don't compare the same point with itself
				if (i == j)
					continue;

				// Compute the chessboard (Chebyshev) distance. A distance of 1
				// underlies 8-connectivity
				dx = Math.max(Math.abs(points[i][0] - points[j][0]), Math.abs(points[i][1] - points[j][1]));

				// Should these two points be in the same group?
				if ((dx == 1) && (grouping[i] != grouping[j])) {

					// Record which numbers we're changing
					source = grouping[i];
					target = grouping[j];

					// Change all targets to sources
					for (k = 0; k < len; k++)
						if (grouping[k] == target)
							grouping[k] = source;

					// Update the number of groups
					if (!positions.remove(j)) // we must trim the set even if
												// this position has been
												// visited
						positions.remove(positions.iterator().next());
					// groups--;
				}

			}
		}

		// System.out.println("i=" + i);
		// System.out.println("groups/positions before doSpikeSupression: " +
		// groups + "/" + positions.size());
		if (doSpikeSupression) {
			removeSinglePixels(points, len, grouping, ip, positions);
			// System.out.println("groups/positions after doSpikeSupression: " +
			// groups + "/" + positions.size());
		}

		final Set<ShollPoint> sPoints = new HashSet<>();
		for (final Integer pos : positions)
			sPoints.add(new ShollPoint(points[pos][0], points[pos][1], cal));

		return sPoints;
	}

	public int[] getPixels(final int[][] points) {

		// Initialize the array to hold the pixel values. int arrays are
		// initialized to a default value of 0
		final int[] pixels = new int[points.length];

		// Put the pixel value for each circumference point in the pixel array
		for (int i = 0; i < pixels.length; i++) {

			// We already filtered out of bounds coordinates in
			// getCircumferencePoints
			if (withinBoundsAndThreshold(points[i][0], points[i][1]))
				pixels[i] = 1;
		}

		return pixels;

	}

	private boolean withinBoundsAndThreshold(final int x, final int y) {
		return withinBounds(x, y) && withinThreshold(x, y);
	}

	private boolean withinBounds(final int x, final int y) {
		return (x >= minX && x <= maxX && y >= minY && y <= maxY);
	}

	private boolean withinThreshold(final int x, final int y) {
		final double value = ip.getPixel(x, y);
		return (value >= lowerT && value <= upperT);
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

			if ((i + 1) % r != 0 && withinBounds(pxX, pxY))
				count++;
		}

		// Create the final array containing only unique points within bounds
		final int[][] refined = new int[count][2];

		for (i = 0; i < points.length; i++) {

			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && withinBounds(pxX, pxY)) {
				refined[j][0] = pxX;
				refined[j++][1] = pxY;
			}

		}

		// Return the array
		return refined;

	}

	public void setHemiShells(final String flag) {
		checkUnsetFields();
		final int maxRadius = (int) Math.round(radii.get(radii.size() - 1) / voxelSize);
		minX = Math.max(xc - maxRadius, 0);
		maxX = Math.min(xc + maxRadius, imp.getWidth());
		minY = Math.max(yc - maxRadius, 0);
		maxY = Math.min(yc + maxRadius, imp.getHeight());
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

	public void setPosition(final int channel, final int slice, final int frame) {
		if (channel < 1 || channel > imp.getNChannels() || slice < 1 || slice > imp.getNSlices() || frame < 1
				|| frame > imp.getNFrames())
			throw new IllegalArgumentException("Specified (channel, slice, frame) position is out of range");
		this.channel = channel;
		this.slice = slice;
		this.frame = frame;
		properties.setProperty(KEY_CHANNEL_POS, String.valueOf(channel));
		properties.setProperty(KEY_SLICE_POS, String.valueOf(slice));
		properties.setProperty(KEY_FRAME_POS, String.valueOf(frame));
	}

	private ImageProcessor getProcessor() {
		imp.setPositionWithoutUpdate(channel, slice, frame);
		final ImageProcessor ip = imp.getChannelProcessor();
		if (ip instanceof FloatProcessor || ip instanceof ColorProcessor)
			return new TypeConverter(ip, false).convertToShort();
		return ip;
	}
}
