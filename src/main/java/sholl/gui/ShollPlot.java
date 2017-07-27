package sholl.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.measure.Measurements;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.util.Tools;
import sholl.Profile;
import sholl.ShollUtils;
import sholl.UPoint;
import sholl.math.LinearProfileStats;
import sholl.math.NormalizedProfileStats;
import sholl.math.ShollStats;

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

	private boolean annotate;
	private ShollStats stats;
	private LinearProfileStats linearStats;
	private NormalizedProfileStats normStats;
	private StringBuffer tempLegend;
	private double xMin, xMax, yMin, yMax;

	public ShollPlot(final Profile profile) {
		this(new LinearProfileStats(profile));
	}

	public ShollPlot(final ShollStats stats) {
		this(defaultTitle(stats), defaultXtitle(stats), defaultYtitle(stats), stats, true);
	}

	public ShollPlot(final Profile... profiles) {
		super("Combined Sholl Plot", "Distance", "No. Intersections");
		final Color[] colors = uniqueColors(profiles.length);
		final StringBuffer legend = new StringBuffer();
		for (int i = 0; i < profiles.length; i++) {
			final Profile p = profiles[i];
			setColor(colors[i]);
			addPoints(p.radii(), p.counts(), LINE);
			legend.append(p.identifier()).append("\n");
		}
		setLimitsToFit(false);
		setColor(Color.WHITE);
		setLegend(legend.toString(), AUTO_POSITION | LEGEND_TRANSPARENT);
		resetDrawing();
	}

	public ShollPlot(final String title, final String xLabel, final String yLabel, final ShollStats stats,
			final boolean annotate) {

		// initialize empty plot, so that sampled data can be plotted with a
		// custom shape, otherwise the default Plot.Line would be used
		super(title, xLabel, yLabel, DUMMY_VALUES, DUMMY_VALUES, DEFAULT_FLAGS);
		this.stats = stats;
		if (stats == null)
			throw new NullPointerException("Stats instance cannot be null");

		if (stats instanceof LinearProfileStats) {
			linearStats = (LinearProfileStats) stats;
			normStats = null;
		} else if (stats instanceof NormalizedProfileStats) {
			normStats = (NormalizedProfileStats) stats;
			linearStats = null;
		} else {
			throw new IllegalArgumentException("Unrecognized ShollStats implementation");
		}

		this.annotate = annotate;
		tempLegend = new StringBuffer();

		// Set plot limits without grid lines
		final double[] xValues = stats.getXvalues();
		final double[] yValues = stats.getYvalues();
		xMin = StatUtils.min(xValues);
		xMax = StatUtils.max(xValues);
		yMin = StatUtils.min(yValues);
		yMax = StatUtils.max(yValues);
		final boolean gridState = PlotWindow.noGridLines;
		PlotWindow.noGridLines = false;
		setLimits(xMin, xMax, yMin, yMax);
		PlotWindow.noGridLines = gridState;

		// Add sampled data
		setColor(SDATA_COLOR);
		addPoints(xValues, yValues, Plot.CROSS);
		if (linearStats != null)
			annotateLinearProfile(false);

		// Add fitted data
		setColor(FDATA_COLOR1);
		if (linearStats != null && linearStats.validFit()) {
			addPoints(linearStats.getXvalues(), linearStats.getFitYvalues(), THICK_LINE);
			annotateLinearProfile(true);
		}
		if (normStats != null && normStats.validFit()) {
			final SimpleRegression reg = normStats.getRegression();
			final double y1 = reg.predict(xMin);
			final double y2 = reg.predict(xMax);

			// Plot regression: NB: with drawLine(x1, y1, x2, y2); line
			// will not have a label and will not be listed on plot's table
			addPoints(new double[] { xMin, xMax }, new double[] { y1, y2 }, THICK_LINE);
			annotateNormalizedProfile(reg);
		}

		// Append finalized legend
		final int flagPos = (annotate) ? AUTO_POSITION | LEGEND_TRANSPARENT : 0;
		final StringBuffer finalLegend = new StringBuffer("Sampled data\n");
		finalLegend.append(tempLegend);
		setLineWidth(1);
		setColor(Color.WHITE);
		setLegend(finalLegend.toString(), flagPos);
		updateImage();
		resetDrawing();
	}

	public void rebuild() {
		final PlotWindow pw = (PlotWindow) getImagePlus().getWindow();
		if (pw == null || !pw.isVisible())
			return;
		if (isFrozen())
			return;
		final ShollPlot newPlot = new ShollPlot(getTitle(), defaultXtitle(stats), defaultYtitle(stats), stats,
				annotate);
		pw.drawPlot(newPlot);
	}

	private static String defaultTitle(final Profile profile) {
		String plotTitle = profile.identifier();
		if (plotTitle == null || plotTitle.isEmpty())
			plotTitle = "Sholl Profile";
		return plotTitle;
	}

	private static String defaultTitle(final ShollStats stats) {
		return defaultTitle(stats.getProfile());
	}

	private static String defaultXtitle(final Profile profile) {
		final StringBuilder sb = new StringBuilder();
		if (profile.is2D())
			sb.append("2D ");
		sb.append("Distance");
		final UPoint center = profile.center();
		if (center != null)
			sb.append(" from ").append(center.toString());
		if (profile.scaled())
			sb.append(" (").append(profile.spatialCalibration().getUnit()).append(")");
		return sb.toString();
	}

	private static String defaultXtitle(final ShollStats stats) {
		if (stats instanceof NormalizedProfileStats
				&& (((NormalizedProfileStats) stats)).getMethod() == ShollStats.LOG_LOG) {
			return "log[ " + defaultXtitle(stats.getProfile()) + " ]";
		}
		return defaultXtitle(stats.getProfile());
	}

	private static String defaultYtitle(final ShollStats stats) {
		if (stats instanceof NormalizedProfileStats) {
			final int normMethod = (((NormalizedProfileStats) stats)).getMethod();
			switch (normMethod) {
			case ShollStats.ANNULUS:
				return "log(No. Inters./Annulus)";
			case ShollStats.AREA:
				return "log(No. Inters./Area)";
			case ShollStats.PERIMETER:
				return "log(No. Inters./Perimeter)";
			case ShollStats.S_SHELL:
				return "log(No. Inters./Spherical Shell)";
			case ShollStats.SURFACE:
				return "log(No. Inters./Surface)";
			case ShollStats.VOLUME:
				return "log(No. Inters./Volume)";
			default:
				return "Normalized Inters.";
			}
		}
		return "No. Intersections";
	}

	private void drawDottedLine(final double x1, final double y1, final double x2, final double y2) {
		final int DASH_STEP = 4;
		drawDottedLine(x1, y1, x2, y2, DASH_STEP);
	}

	private void annotateNormalizedProfile(final SimpleRegression regression) {
		if (!annotate || regression == null)
			return;

		// mark slope
		final double xCenter = (xMin + xMax) / 2;
		final double ySlope = regression.predict(xCenter);
		drawDottedLine(xMin, ySlope, xCenter, ySlope);
		drawDottedLine(xCenter, yMin, xCenter, ySlope);

		// mark intercept
		if (regression.hasIntercept())
			markPoint(new UPoint(0, regression.getIntercept()), DOT, 8);

		// assemble legend
		final double rsqred = regression.getRSquare();
		final double k = -regression.getSlope();
		tempLegend.append("k= ").append(ShollUtils.d2s(k));
		tempLegend.append(" (R\u00B2= ").append(ShollUtils.d2s(rsqred)).append(")\n");
	}

	private void annotateLinearProfile(final boolean fittedData) {

		if (!annotate || linearStats == null)
			return;

		final UPoint centroid = linearStats.getCentroid(fittedData);
		final UPoint pCentroid = linearStats.getPolygonCentroid(fittedData);
		final UPoint max = linearStats.getCenteredMaximum(fittedData);
		final double primary = linearStats.getPrimaryBranches(fittedData);
		final double mv;
		Color color;
		if (fittedData) {
			mv = linearStats.getMeanValueOfPolynomialFit(xMin, xMax);
			color = FDATA_ANNOT_COLOR1;
		} else {
			mv = centroid.y;
			color = SDATA_ANNOT_COLOR;
		}

		setLineWidth(1);
		setColor(color);

		// highlight centroids
		markPoint(pCentroid, CROSS, 8);
		setColor(color);
		drawDottedLine(xMin, centroid.y, centroid.x, centroid.y);
		drawDottedLine(centroid.x, yMin, centroid.x, centroid.y);

		// highlight mv
		if (fittedData && mv != centroid.y)
			drawDottedLine(xMin, mv, centroid.x, mv);

		// highlight primary branches
		drawDottedLine(xMin, primary, max.x, primary);

		// highlight max
		drawDottedLine(xMin, max.y, max.x, max.y);
		drawDottedLine(max.x, yMin, max.x, max.y);

		// build label
		if (fittedData) {
			final double rsqred = linearStats.getRSquaredOfFit(true);
			final String polyType = linearStats.getPolynomialAsString();
			tempLegend.append(polyType).append(" fit (");
			tempLegend.append("R\u00B2= ").append(ShollUtils.d2s(rsqred)).append(")\n");
		}

	}

	/**
	 * Highlights a point on a plot without listing it on the Plot's table. Does
	 * nothing if point is {@code null}
	 *
	 * @param pCentroid
	 *            the point to be drawn (defined in calibrated coordinates)
	 * @param markShape
	 *            either X, CROSS or DOT. Other shapes are not supported.
	 * @param markSize
	 *            the mark size in pixels
	 */
	public void markPoint(final UPoint pCentroid, final int markShape, final int markSize) {
		if (pCentroid == null)
			return;

		final double x = pCentroid.x;
		final double y = pCentroid.y;
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
	 * @see {@link #markPoint(UPoint, int, int)}
	 */
	public void markPoint(final UPoint point, final Color color) {
		setColor(color);
		markPoint(point, CROSS, 8);
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
		final int yTop = r.y + 1 + padding + metrics.getHeight(); // FIXME:
																	// Since
																	// 1.51n
																	// top-padding
																	// is offset
																	// by 1line
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

	private Color[] uniqueColors(final int n) {
		final Color[] defaults = new Color[] { Color.BLUE, Color.RED, Color.MAGENTA, Color.GREEN.darker(),
				Color.DARK_GRAY, Color.CYAN.darker(), Color.ORANGE.darker(), Color.BLACK };
		final Color[] colors = new Color[n];
		for (int j = 0, i = 0; i < n; i++) {
			colors[i] = defaults[j++];
			if (j == defaults.length - 1)
				j = 0;
		}
		return colors;
	}
}
