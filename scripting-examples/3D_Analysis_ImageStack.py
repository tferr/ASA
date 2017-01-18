#@ImagePlus imp
#@LogService log

from sholl import Sholl_Analysis, Options
from os.path import expanduser

xc, yc, zc = 100, 100, 10  # x,y,z coordinates of center
lowerT, upperT = 88, 255   # threshold values for segmentation
distances = [10, 20, 30, 40, 50] # sampling radii
exportPath = expanduser("~") # home directory

sa = Sholl_Analysis()
so = Options()

if sa.validateImage(imp):

    # Specify some output options
    so.setNoTable(False)
    so.setNoPlots(False)
    sa.setDescription(imp.getTitle(), True)
    sa.setExportPath(exportPath, True)
    sa.setInteractiveMode(False)

    # Specify analysis settings
    sa.setCenter(xc, yc, zc)
    sa.setThreshold(lowerT, upperT)

    # Retrieve intersection counts
    counts = sa.analyze3D(xc, yc, zc, distances, imp)

    # Check that analysis was successfull
    if all(c == 0 for c in counts):
        log.error("All intersection counts were zero")

    # Do something with sampled data
    for idx, inters in enumerate(counts):
        log.info("r="+ str(distances[idx]) +": "+ str(inters) +" intersections")

    # Retrieve metrics
    sa.analyzeProfile(distances, counts, True)

    log.info("Analysis finished. Files saved to " + exportPath)
    log.warn("Sholl Results Table has not been saved")

else:

    log.error(imp.getTitle() + " is not a valid image")
