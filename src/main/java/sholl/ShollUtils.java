package sholl;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

public class ShollUtils {

	private ShollUtils() {
	}

	public static String d2s(final double d) {
		return new DecimalFormat("#.###").format(d);
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
