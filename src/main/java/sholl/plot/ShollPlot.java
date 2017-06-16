package sholl.plot;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

import ij.IJ;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.measure.Measurements;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;
import sholl.math.LinearProfileStats;

public class ShollPlot extends Plot {

	/** Default colors for plotting sampled data */
	private final Color SDATA_COLOR = Color.GRAY;
	private final Color SDATA_ANNOT_COLOR = Color.LIGHT_GRAY;

	/** Default colors for plotting fitted data */
	private final Color FDATA_COLOR1 = Color.BLUE;
	private final Color FDATA_ANNOT_COLOR1 = new Color(0, 120, 255);
	// private final Color FDATA_COLOR2 = Color.RED;
	// private final Color FDATA_ANNOT_COLOR2 = new Color(260, 160, 0);

	/** Flag for plotting points with a thicker solid line */
	public static final int THICK_LINE = -1;

	private final static int DEFAULT_FLAGS = X_FORCE2GRID + X_TICKS + X_NUMBERS + Y_FORCE2GRID + Y_TICKS + Y_NUMBERS;
	private final static double[] DUMMY_VALUES = null;

	public ShollPlot(final String title, final LinearProfileStats stats) {
		this(title, "Distance", "No. Intersections", stats, true);
	}

	public ShollPlot(final String title, final String xLabel, final String yLabel, final LinearProfileStats stats,
			final boolean annotate) {

		// initialize empty plot, so that sampled data can be plotted with a
		// custom shape, otherwise the default Plot.Line would be used
		super(title, xLabel, yLabel, DUMMY_VALUES, DUMMY_VALUES, DEFAULT_FLAGS);

		final double[] xValues = stats.getRadii();
		final double[] yValues = stats.getCounts();

		// Set plot limits without grid lines
		final double[] xScale = Tools.getMinMax(xValues);
		final double[] yScale = Tools.getMinMax(yValues);
		final boolean gridState = PlotWindow.noGridLines;
		PlotWindow.noGridLines = false;
		setLimits(xScale[0], xScale[1], yScale[0], yScale[1]);
		PlotWindow.noGridLines = gridState;

		// Add sampled data
		if (annotate)
			annotateProfile(stats, false);
		setColor(SDATA_COLOR);
		addPoints(xValues, yValues, Plot.CROSS);

		// Add fitted data
		if (stats.validFcounts()) {
			if (annotate)
				annotateProfile(stats, true);
			setColor(FDATA_COLOR1);
			addPoints(stats.getRadii(), stats.getFcounts(), THICK_LINE);
		}
	}

	private void annotateProfile(final LinearProfileStats stats, final boolean fittedData) {

		final int DASH_STEP = 4;
		final Point2D.Double centroid = stats.getCentroid(fittedData);
		final Point2D.Double barycenter = stats.getPolygonCentroid(fittedData);
		final Point2D.Double max = stats.getCenteredMaximum(fittedData);
		final double primary = stats.getPrimaryBranches(fittedData);
		final double mv;
		Color color;
		if (fittedData) {
			mv = stats.getMeanValueOfPolynomialFit(stats.getStartRadius(), stats.getEndRadius());
			color = FDATA_ANNOT_COLOR1;
		} else {
			mv = centroid.y;
			color = SDATA_ANNOT_COLOR;
		}

		setLineWidth(1);
		setColor(color);
		final double[] limits = getLimits(); // {xMin, xMax, yMin, yMax};

		// highlight max
		drawDottedLine(limits[0], max.y, max.x, max.y, DASH_STEP);
		drawDottedLine(max.x, limits[2], max.x, max.y, DASH_STEP);

		// highlight centroids
		markPoint(barycenter, CROSS, 9);
		setColor(color);
		drawDottedLine(limits[0], centroid.y, centroid.x, centroid.y, DASH_STEP);
		drawDottedLine(centroid.x, limits[2], centroid.x, centroid.y, DASH_STEP);

		// highlight mv
		if (fittedData && mv != centroid.y)
			drawDottedLine(limits[0], mv, centroid.x, mv, DASH_STEP);

		// highlight primary branches
		drawDottedLine(stats.getStartRadius(), primary, max.x, primary, DASH_STEP);

		// build label
		if (fittedData) {
			final double rsqred = stats.getRSquaredOfPolynomialFit(true);
			final String polyType = stats.getPolynomialAsString();
			final StringBuffer legend = new StringBuffer();
			legend.append("Sampled data\n");
			legend.append(polyType).append(" fit (");
			legend.append("R\u00B2= ").append(IJ.d2s(rsqred, 3)).append(")\n");
			setLineWidth(1);
			setColor(Color.WHITE);
			setLegend(legend.toString(), AUTO_POSITION | LEGEND_TRANSPARENT);
		}
		resetDrawing();
	}

	/**
	 * Highlights a point on a plot without listing it on the Plot's table. Does
	 * nothing if point is {@code null}
	 *
	 * @param point
	 *            the point to be drawn (defined in calibrated coordinates)
	 * @param markShape
	 *            either X, CROSS or DOT. Other shapes are not supported.
	 * @param markSize
	 *            the mark size in pixels
	 */
	public void markPoint(final Point2D.Double point, final int markShape, final int markSize) {
		if (point == null)
			return;

		final double x = point.x;
		final double y = point.y;
		final double xStart = descaleX((int) (scaleXtoPxl(x) - (markSize / 2) + 0.5));
		final double yStart = descaleY((int) (scaleYtoPxl(y) - (markSize / 2) + 0.5));
		final double xEnd = descaleX((int) (scaleXtoPxl(x) + (markSize / 2) + 0.5));
		final double yEnd = descaleY((int) (scaleYtoPxl(y) + (markSize / 2) + 0.5));

		draw();
		switch (markShape) {
		case X:
			drawLine(xStart, yStart, xEnd, yEnd);
			drawLine(xEnd, yStart, xStart, yEnd);
			break;
		case CROSS:
			drawLine(xStart, y, xEnd, y);
			drawLine(x, yStart, x, yEnd);
			break;
		case DOT:
			setLineWidth(markSize);
			drawLine(x, y, x, y);
			setLineWidth(1);
			break;
		case BOX:
			drawLine(xStart, yStart, xEnd, yStart);
			drawLine(xEnd, yStart, xEnd, yEnd);
			drawLine(xEnd, yEnd, xStart, yEnd);
			drawLine(xStart, yEnd, xStart, yStart);
			break;
		default:
			throw new IllegalArgumentException("Currently only the shapes BOX, CROSS, DOT, X are supported");
		}
	}

	/**
	 * Highlights a point on a plot using the default marker.
	 *
	 * @param point
	 *            the point to be drawn (defined in calibrated coordinates)
	 * @param color
	 *            the drawing color. This will not affect consequent objects
	 * @see {@link #markPoint(Point2D.Double, int, int)}
	 */
	public void markPoint(final Point2D.Double point, final Color color) {
		setColor(color);
		markPoint(point, X, 9);
		resetDrawing();
	}

	/**
	 * Draws a label at the less crowded corner of an ImageJ plot. Height and
	 * width of label is measured so that text remains within the plot's frame.
	 * Text is added to the first free position in this sequence: NE, NW, SE,
	 * SW.
	 *
	 * @param plot
	 *            Plot object
	 * @param label
	 *            Label contents
	 * @param color
	 *            Foreground color of text. Note that this will also set the
	 *            drawing color for the next objects to be be added to the plot
	 */
	public void drawLabel(final String label, final Color color) {

		final ImageProcessor ip = getProcessor();

		int maxLength = 0;
		String maxLine = "";
		final String[] lines = Tools.split(label, "\n");
		for (int i = 0; i < lines.length; i++) {
			final int length = lines[i].length();
			if (length > maxLength) {
				maxLength = length;
				maxLine = lines[i];
			}
		}

		final Font font = new Font("Helvetica", Font.PLAIN, PlotWindow.fontSize);
		ip.setFont(font);
		setFont(font);
		final FontMetrics metrics = ip.getFontMetrics();
		final int textWidth = metrics.stringWidth(maxLine);
		final int textHeight = metrics.getHeight() * lines.length;

		final Rectangle r = getDrawingFrame();
		final int padding = 4; // space between label and axes
		final int yTop = r.y + 1 + padding;
		final int yBottom = r.y + r.height - textHeight - padding;
		final int xLeft = r.x + 1 + padding;
		final int xRight = r.x + r.width - textWidth - padding;

		final double northEast = meanRoiValue(ip, xLeft, yTop, textWidth, textHeight);
		final double northWest = meanRoiValue(ip, xRight, yTop, textWidth, textHeight);
		final double southEast = meanRoiValue(ip, xLeft, yBottom, textWidth, textHeight);
		final double southWest = meanRoiValue(ip, xRight, yBottom, textWidth, textHeight);
		final double pos = Math.max(Math.max(northEast, northWest), Math.max(southEast, southWest));

		ip.setColor(0);
		setColor(color);
		// We'll use the ImageProcessor and PlotObjects so that we can 'brand'
		// the pizmultiple labels can be added without
		// overlap
		if (pos == northEast) {
			ip.drawString(label, xLeft, yTop);
			addText(label, descaleX(xLeft), descaleY(yTop));
		} else if (pos == northWest) {
			ip.drawString(label, xRight, yTop);
			addText(label, descaleX(xRight), descaleY(yTop));
		} else if (pos == southEast) {
			ip.drawString(label, xLeft, yBottom);
			addText(label, descaleX(xLeft), descaleY(yBottom));
		} else {
			ip.drawString(label, xRight, yBottom);
			addText(label, descaleX(xRight), descaleY(yBottom));
		}
		resetDrawing();
	}

	/** Returns the mean value of a rectangular ROI */
	private double meanRoiValue(final ImageProcessor ip, final int x, final int y, final int width, final int height) {
		ip.setRoi(x, y, width, height);
		return ImageStatistics.getStatistics(ip, Measurements.MEAN, null).mean;
	}

	@Override
	public void addPoints(final double[] x, final double[] y, final int shape) {
		if (shape == THICK_LINE) {
			setLineWidth(2);
			super.addPoints(x, y, LINE);
			setLineWidth(1);
		} else {
			super.addPoints(x, y, shape);
		}
	}

	private void resetDrawing() {
		setLineWidth(1);
		setColor(Color.BLACK);
	}
}
