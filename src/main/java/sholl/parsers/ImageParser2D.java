package sholl.parsers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.math3.stat.StatUtils;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.ShollPoint;
import sholl.ShollUtils;
import sholl.Sholl_Utils;

@Plugin(type = Command.class)
public class ImageParser2D implements Parser {

	@Parameter
	private Context context;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	private Properties properties;
	private Profile profile;
	private ShollPoint center;
	private ArrayList<Double> radii;

	private ImageProcessor ip;
	private Calibration cal;
	private double pixelSize;
	private int minX, maxX;
	private int minY, maxY;
	private int lowerT, upperT;
	private int spanSize, spanType;
	private final int MAX_N_SPANS = 10;
	private final boolean doSpikeSupression = true;

	/** Flag for integration of repeated measures: average */
	public static final int AVERAGE = 0;
	/** Flag for integration of repeated measures: median */
	private static final int MEDIAN = 1;
	/** Flag for integration of repeated measures: mode */
	public static final int MODE = 2;

	public ImageParser2D() {
		if (context == null)
			context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		if (logService == null)
			logService = context.getService(LogService.class);
		if (statusService == null)
			statusService = context.getService(StatusService.class);
	}

	public ImageParser2D(final ImagePlus imp) {
		this();
		ip = imp.getProcessor();
		if (ip.isBinary())
			setThreshold(1, 255);
		cal = imp.getCalibration(); // never null
		pixelSize = Math.sqrt(cal.pixelHeight * cal.pixelWidth);
		profile = new Profile();
		profile.assignImage(imp);
		properties = profile.getProperties();

		minX = 0;
		maxX = imp.getWidth() - 1;
		minY = 0;
		maxY = imp.getHeight() - 1;
	}

	/** Debug method **/
	public static void main(final String... args) throws Exception {
		// final ImageJ ij = net.imagej.Main.launch(args);
		final ImageParser2D parser = new ImageParser2D(Sholl_Utils.sampleImage());
		parser.parse();
	}

	@Override
	public boolean successful() {
		return profile.size() > 0;
	}

	public void setCenterPx(final int x, final int y) {
		if (cal == null)
			throw new IllegalArgumentException("Attempting to set center under unknown calibration");
		center = new ShollPoint(x, y, cal);
		profile.setCenter(center);
	}

	public void setCenter(final double x, final double y) {
		center = new ShollPoint(x, y);
		profile.setCenter(center);
	}

	public void setRadii(final double startRadius, final double step, final double endRadius) {
		setRadii(startRadius, step, endRadius, 1, -1);
	}

	public void setRadii(final double startRadius, final double step, final double endRadius, final int span,
			final int integrationFlag) {
		radii = ShollUtils.getRadii(startRadius, step, endRadius);
		setRadiiSpan(span, integrationFlag);
	}

	public void setThreshold(final int lower, final int upper) {
		lowerT = lower;
		upperT = upper;
	}

	private void setRadiiSpan(final int nSamples, final int integrationFlag) {
		spanSize = Math.max(1, Math.min(MAX_N_SPANS, nSamples));
		properties.setProperty(KEY_NSAMPLES, String.valueOf(spanSize));
		switch (integrationFlag) {
		case MEDIAN:
			properties.setProperty(KEY_NSAMPLES_INTG, INTG_MEDIAN);
			break;
		case MODE:
			properties.setProperty(KEY_NSAMPLES_INTG, INTG_MODE);
			break;
		case AVERAGE:
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

		double[] binsamples;
		int[] pixels;
		int[][] points;

		final int size = radii.size();

		// Create array for bin samples. Passed value of binSize must be at
		// least 1
		binsamples = new double[spanSize];

		statusService.showStatus(
				"Sampling " + size + " radii, " + spanSize + " measurement(s) per radius. Press 'Esc' to abort...");

		final int xc = (int) center.rawX(cal);
		final int yc = (int) center.rawY(cal);

		// Outer loop to control the analysis bins
		int i = 0;
		double counts = 0;
		for (final Double radius : radii) {

			// Retrieve the radius in pixel coordinates and set the largest
			// radius of this bin span
			int rbin = (int) Math.round(radius / pixelSize + spanSize / 2);
			final Set<ShollPoint> pointsList = new HashSet<>();

			// Inner loop to gather samples for each bin
			for (int s = 0; s < spanSize; s++) {

				// Get the circumference pixels for this radius
				points = getCircumferencePoints(xc, yc, rbin--);
				pixels = getPixels(points);

				// Count the number of intersections
				final Set<ShollPoint> thisBinIntersPoints = targetGroupsPositions(pixels, points, ip);
				binsamples[s] = thisBinIntersPoints.size();
				pointsList.addAll(thisBinIntersPoints);
			}

			statusService.showProgress(i++, size * spanSize);
			if (IJ.escapePressed()) {
				IJ.beep();
				return profile;
			}

			// Statistically combine bin data
			if (spanSize > 1) {
				if (spanType == MEDIAN) { // 50th percentile
					counts = StatUtils.percentile(binsamples, 50);
				} else if (spanType == AVERAGE) { // mean
					counts = StatUtils.mean(binsamples);
				} else if (spanType == MODE) { // the 1st max freq. element
					counts = StatUtils.mode(binsamples)[0];
				}
			} else {// There was only one sample
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

		int value;

		// Initialize the array to hold the pixel values. int arrays are
		// initialized to a default value of 0
		final int[] pixels = new int[points.length];

		// Put the pixel value for each circumference point in the pixel array
		for (int i = 0; i < pixels.length; i++) {

			// We already filtered out of bounds coordinates in
			// getCircumferencePoints
			value = ip.getPixel(points[i][0], points[i][1]);
			if (value >= lowerT && value <= upperT)
				pixels[i] = 1;
		}

		return pixels;

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

			if ((i + 1) % r != 0 && pxX >= minX && pxX <= maxX && pxY >= minY && pxY <= maxY)
				count++;
		}

		// Create the final array containing only unique points within bounds
		final int[][] refined = new int[count][2];

		for (i = 0; i < points.length; i++) {

			pxX = points[i][0];
			pxY = points[i][1];

			if ((i + 1) % r != 0 && pxX >= minX && pxX <= maxX && pxY >= minY && pxY <= maxY) {
				refined[j][0] = pxX;
				refined[j++][1] = pxY;

			}

		}

		// Return the array
		return refined;

	}

	private void checkUnsetFields() {
		if (center == null || radii == null || upperT == ImageProcessor.NO_THRESHOLD || (lowerT == 0 && upperT == 0))
			throw new NullPointerException("Cannot proceed with undefined parameters");
	}
}