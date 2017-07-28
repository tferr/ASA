package sholl;

import java.awt.Color;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

import net.imagej.table.ResultsTable;
import net.imagej.table.TableLoader;
import net.imglib2.display.ColorTable8;

import org.scijava.util.VersionUtils;

import ij.ImagePlus;
import ij.io.Opener;

public class ShollUtils {

	private ShollUtils() {
	}

	public static String d2s(final double d) {
		return new DecimalFormat("#.###").format(d);
	}

	// this method is from BAR
	private static URL getResource(final String resourcePath) {
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		URL resource = null;
		try {
			final Enumeration<URL> resources = loader.getResources(resourcePath);
			while (resources.hasMoreElements()) {
				resource = resources.nextElement();
				if (resource.toString().contains(resourcePath))
					return resource;
			}
		} catch (final IOException exc) {
			// proceed with return null;
		}
		return resource;
	}

	public static ResultsTable csvSample() {
		final URL url = getResource("csv/ddaCsample.csv");
		if (url == null)
			throw new IllegalArgumentException("Could not retrieve ddaCsample.csv");
		final TableLoader loader = new TableLoader();
		try {
			// NB: this will fail for headings containing whitespace[
			return loader.valuesFromTextFile(url);
		} catch (final IOException exc) {
			exc.printStackTrace();
			return null;
		}
	}

	public static ArrayList<Double> getRadii(final double startRadius, final double incStep, final double endRadius) {

		if (Double.isNaN(startRadius) || Double.isNaN(incStep) || Double.isNaN(endRadius) || incStep <= 0
				|| endRadius < startRadius) {
			throw new IllegalArgumentException("Invalid parameters: " + startRadius + "," + incStep + "," + endRadius);
		}
		final int size = (int) ((endRadius - startRadius) / incStep) + 1;
		final ArrayList<Double> radii = new ArrayList<>();
		for (final OfInt it = IntStream.range(0, size).iterator(); it.hasNext();) {
			radii.add(startRadius + it.nextInt() * incStep);
		}
		return radii;

	}

	public static ColorTable8 constantLUT(final Color color) {
		final byte[][] values = new byte[3][256];
		for (int i = 0; i < 256; i++) {
			values[0][i] = (byte) color.getRed();
			values[1][i] = (byte) color.getGreen();
			values[2][i] = (byte) color.getBlue();
		}
		return new ColorTable8(values);
	}

	public static String extractHemiShellFlag(final String string) {
		if (string == null || string.trim().isEmpty())
			return ProfileProperties.HEMI_NONE;
		final String flag = string.toLowerCase();
		if (flag.contains("none") || flag.contains("full"))
			return ProfileProperties.HEMI_NONE;
		if (flag.contains("above") || flag.contains("north"))
			return ProfileProperties.HEMI_NORTH;
		else if (flag.contains("below") || flag.contains("south"))
			return ProfileProperties.HEMI_SOUTH;
		else if (flag.contains("left") || flag.contains("east"))
			return ProfileProperties.HEMI_EAST;
		else if (flag.contains("right") || flag.contains("west"))
			return ProfileProperties.HEMI_WEST;
		return flag;
	}

	/**
	 * Returns the plugin's sample image (File&gt;Samples&gt;ddaC Neuron).
	 *
	 * @return ddaC image, or null if image cannot be retrieved
	 */
	public static ImagePlus sampleImage() {
		final URL url = getResource("images/ddaC.tif");
		if (url == null)
			throw new NullPointerException("Could not retrieve ddaC.tif");
		ImagePlus imp = null;
		try {
			final Opener opener = new Opener();
			imp = opener.openTiff(url.openStream(), "Drosophila_ddaC_Neuron.tif");
		} catch (final IOException exc) {
			exc.printStackTrace();
		}
		return imp;
	}

	/**
	 * Retrieves Sholl Analysis version
	 *
	 * @return the version or a non-empty place holder string if version could
	 *         not be retrieved.
	 *
	 */
	public static String version() {
		final String VERSION = VersionUtils.getVersion(Sholl_Analysis.class);
		return (VERSION == null) ? "X Dev" : VERSION;
	}

}
