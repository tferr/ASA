/*-
 * #%L
 * Sholl Analysis plugin for ImageJ.
 * %%
 * Copyright (C) 2005 - 2020 Tiago Ferreira.
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
/* OP_1-Test.ijm
 */

// Parameters:
vW = 0.3296485;      // voxel width and height (um)
vD = 3.03*vW;        // voxel depth (um)
maxRad = 60;         // largest Sholl radius (um)
x = 335;             // x,y,z coordinates (pixels) of analysis center
y = 213;
z = 34;

open(getDirectory("home") +"/code/ASA/src/test/OP_1.tif");
setVoxelSize(vW, vW, vD, "um");
setThreshold(131, 255);

// Set center of analysis
setSlice(z);
makePoint(x, y);
call("sholl.Sholl_Analysis.setNoTable", "true");
call("sholl.Sholl_Analysis.setNoPlots", "true");

run("Sholl Analysis...", "starting=0 ending="+ maxRad
      +" radius_step=0 _=[Above line] ignore enclosing=1 #_primary=[] fit"
      +" linear polynomial=[Best fitting degree] ");
