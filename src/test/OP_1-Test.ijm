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

run("Sholl Analysis...", "starting=0 ending="+ maxRad
      +" radius_step=0 _=[Above line] ignore enclosing=1 #_primary=[] fit"
      +" linear polynomial=[Best fitting degree] ");
