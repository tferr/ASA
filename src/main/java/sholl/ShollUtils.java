package sholl;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

import net.imagej.table.ResultsTable;
import net.imagej.table.TableLoader;

public class ShollUtils {

	private ShollUtils() {
	}

	public static String d2s(final double d) {
		return new DecimalFormat("#.###").format(d);
	}

	// this method is from BAR
	public static URL getResource(final String resourcePath) {
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

	protected static ResultsTable csvSample() {
		final URL url = getResource("csv/ddaCsample.csv");
		if (url == null)
			throw new NullPointerException("Could not retrieve ddaCsample.csv");
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
}
