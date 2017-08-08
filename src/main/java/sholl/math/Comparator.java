/*
 * #%L
 * Sholl_Analysis plugin for ImageJ
 * %%
 * Copyright (C) 2017 Tiago Ferreira
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sholl.math;

import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.scijava.NullContextException;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;

import sholl.Helper;
import sholl.Profile;

@Plugin(type = ContextCommand.class, visible = false)
public class Comparator extends ContextCommand {

	private final Profile profile1;
	private final Profile profile2;
	private final double[] p1Counts;
	private final double[] p2Counts;
	private final int nPoints;
	private final SimpleRegression regression;

	public Comparator(final Profile profile1, final Profile profile2) {
		validateProfile(profile1, profile2);
		this.profile1 = profile1;
		this.profile2 = profile2;
		nPoints = Math.min(profile1.size(), profile2.size());
		p1Counts = profile1.countsAsArray();
		p2Counts = profile2.countsAsArray();
		regression = new SimpleRegression();
		for (int i = 0; i < nPoints; i++)
			regression.addData(p1Counts[i], p2Counts[i]);
	}

	@Override
	public void run() throws NullContextException {
		final Helper helper = new Helper(context());
		helper.log("\n*** Comparing", profile1.identifier(), "vs", profile2
			.identifier(), "***");
		helper.log("KS-test: " + getKStest());
		helper.log("Reg R: " + regression.getR());
		helper.log("Reg R^2: " + regression.getRSquare());
	}

	public SimpleRegression getRegression() {
		return regression;
	}

	public double getKStest() {
		final KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		final double pValue = test.kolmogorovSmirnovTest(p1Counts, p2Counts);
		return pValue;
	}

	private void validateProfile(final Profile... profiles) {
		for (final Profile p : profiles)
			if (p == null || p.size() == 0) throw new IllegalArgumentException(
				"Cannot compare null or empty profiles");
	}

}
