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
import java.util.Properties;

import ij.measure.Calibration;
import ij.measure.ResultsTable;
import sholl.Profile;
import sholl.ProfileEntry;

/**
 * @author Tiago Ferreira
 *
 */
public class TabularParser implements Parser {

	private Profile profile;

	private ResultsTable table;
	private String tableName;
	private final String radiiColumnHeader;
	private int radiiCol;
	private int countsCol;
	private final int startRow;
	private final int endRow;

	public TabularParser(final File table, final String radiiColumnHeader, final String countsColumnHeader)
			throws IOException {
		this(ResultsTable.open(table.getAbsolutePath()), radiiColumnHeader, countsColumnHeader, -1, -1);
		tableName = table.getName();
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

		if (table == null || table.getCounter() == 0)
			throw new IllegalArgumentException("Table does not contain valid data");

		this.radiiColumnHeader = radiiColumnHeader;
		final int radiiCol = table.getColumnIndex(radiiColumnHeader);
		final int countsCol = table.getColumnIndex(countsColumnHeader);
		if (radiiCol == ResultsTable.COLUMN_NOT_FOUND || countsCol == ResultsTable.COLUMN_NOT_FOUND)
			throw new IllegalArgumentException(
					"Specified headings do not match existing ones: " + table.getColumnHeadings());

		final int lastRow = table.getCounter() - 1;
		this.startRow = (startRow == -1) ? 0 : startRow;
		this.endRow = (endRow == -1) ? lastRow : endRow;
		if (this.startRow > this.endRow || this.endRow > lastRow)
			throw new IllegalArgumentException("Specified rows are out of range");
	}

	@Override
	public Profile parse() {
		profile = new Profile();
		final double[] radii = table.getColumnAsDoubles(radiiCol);
		final double[] counts = table.getColumnAsDoubles(countsCol);
		for (int i = startRow; i <= endRow; i++) {
			final ProfileEntry entry = new ProfileEntry(radii[i], counts[i], null);
			profile.add(entry);
		}
		final Properties properties = new Properties();
		properties.setProperty(KEY_ID, (tableName == null) ? table.toString() : tableName);
		properties.setProperty(KEY_SOURCE, SRC_TABLE);
		final Calibration cal = guessCalibrationFromHeading(radiiColumnHeader);
		if (cal != null)
			profile.setSpatialCalibration(cal);
		profile.setProperties(properties);
		return profile;
	}

	private Calibration guessCalibrationFromHeading(final String colHeading) {
		if (colHeading == null)
			return null;
		final String[] tokens = colHeading.toLowerCase().split("\\W");
		final String[] knownUnits = new String("\u00B5 micron mm cm pixels").split(" ");
		for (final String token : tokens) {
			for (final String unit : knownUnits) {
				if (token.contains(unit)) {
					final Calibration cal = new Calibration();
					cal.setUnit(unit);
					return cal;
				}
			}
		}
		return null;
	}

	@Override
	public boolean successful() {
		return profile != null && profile.size() > 0;
	}

}
