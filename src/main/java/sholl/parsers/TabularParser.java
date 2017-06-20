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
package sholl.parsers;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import ij.measure.ResultsTable;
import sholl.Profile;
import sholl.ProfileEntry;

/**
 * @author Tiago Ferreira
 *
 */
public class TabularParser implements Parser {

	private final Profile profile;
	private final Set<ProfileEntry> profileData;

	private final String radiiColumnHeader;
	private final String countsColumnHeader;

	public TabularParser(final File table, final String radiiColumnHeader, final String countsColumnHeader)
			throws IOException {
		final ResultsTable rt = ResultsTable.open(table.getAbsolutePath());
		this.radiiColumnHeader = radiiColumnHeader;
		this.countsColumnHeader = countsColumnHeader;
		profile = new Profile(this);
		profileData = profile.entries();
		profile.setSpatialUnit(guessSpatialUnit(radiiColumnHeader));
		profile.setIdentifier(table.getName());
		buildProfile(rt, -1, -1);
	}

	public TabularParser(final String filePath, final String radiiColumnHeader, final String countsColumnHeader)
			throws IOException {
		this(new File(filePath), radiiColumnHeader, countsColumnHeader);
	}

	/**
	 *
	 */
	public TabularParser(final ResultsTable table, final String radiiColumnHeader, final String countsColumnHeader,
			final int startRow, final int endRow) {
		this.radiiColumnHeader = radiiColumnHeader;
		this.countsColumnHeader = countsColumnHeader;
		profile = new Profile(this);
		profileData = profile.entries();
		profile.setSpatialUnit(guessSpatialUnit(radiiColumnHeader));
		profile.setIdentifier(table.toString());
		buildProfile(table, startRow, endRow);
	}

	private String guessSpatialUnit(final String columnHeader) {
		if (columnHeader == null)
			return null;

		final String[] tokens = columnHeader.toLowerCase().split("\\W");
		final String[] knownUnits = new String("\u00B5 micron mm cm pixels").split(" ");
		for (final String token : tokens) {
			for (final String unit : knownUnits) {
				if (token.contains(unit))
					return unit;
			}
		}
		return null;
	}

	private void buildProfile(final ResultsTable table, final int startRow, final int endRow) {

		final int radiiCol = table.getColumnIndex(radiiColumnHeader);
		final int countsCol = table.getColumnIndex(countsColumnHeader);
		if (radiiCol == ResultsTable.COLUMN_NOT_FOUND || countsCol == ResultsTable.COLUMN_NOT_FOUND)
			throw new IllegalArgumentException("Specified columns not found");

		final int lastRow = table.getCounter() - 1;
		final int sRow = (startRow == -1) ? 0 : startRow;
		final int eRow = (endRow == -1) ? lastRow : endRow;
		if (sRow > eRow || eRow > lastRow)
			throw new IllegalArgumentException("Specified rows are out of range");

		final double[] radii = table.getColumnAsDoubles(radiiCol);
		final double[] counts = table.getColumnAsDoubles(countsCol);
		for (int i = sRow; i <= eRow; i++) {
			final ProfileEntry entry = new ProfileEntry(radii[i], counts[i], null);
			profileData.add(entry);
		}

	}

	@Override
	public boolean successful() {
		return profile != null && profileData.size() > 0;
	}

	@Override
	public int space() {
		return UNKNOWN_D;
	}

	@Override
	public int source() {
		return SOURCE_TABULAR;
	}

	@Override
	public Profile profile() {
		return profile;
	}

}
