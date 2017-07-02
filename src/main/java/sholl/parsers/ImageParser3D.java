package sholl.parsers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.plugin.ChannelSplitter;
import ij.util.ThreadUtil;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.UPoint;
import sholl.Sholl_Utils;

@Plugin(type = Command.class)
public class ImageParser3D extends ImageParser implements Command {

	@Parameter
	private Context context;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	private final Properties properties;
	private int minX, maxX;
	private int minY, maxY;
	private int minZ, maxZ;
	private final int channel = 1;
	private double vxWH, vxD;
	private int progressCounter;
	private boolean skipSingleVoxels;

	public ImageParser3D(final ImagePlus imp) {
		super(imp);
		if (context == null)
			context = (Context) IJ.runPlugIn("org.scijava.Context", "");
		if (logService == null)
			logService = context.getService(LogService.class);
		if (statusService == null)
			statusService = context.getService(StatusService.class);
		properties = profile.getProperties();
	}

	public static void main(final String... args) {
		// final ImageJ ij = net.imagej.Main.launch(args);
		// ij.command().run(ImageParser2D.class, true);
		new ImageParser3D(Sholl_Utils.sampleImage()).run();
	}

	@Override
	public void run() {
	}

	@Override
	public Profile parse() {
		checkUnsetFields();
		if (UNSET.equals(properties.getProperty(KEY_HEMISHELLS, UNSET)))
			setHemiShells(HEMI_NONE);

		final int nspheres = radii.size();
		final UPoint c = new UPoint(xc, yc, zc);

		// Get Image Stack
		final ImageStack stack = (imp.isComposite()) ? ChannelSplitter.getChannel(imp, channel) : imp.getStack();

		vxD = cal.pixelDepth;
		vxWH = Math.sqrt(cal.pixelWidth * cal.pixelHeight);
		// Split processing across the number of available CPUs
		final AtomicInteger ai = new AtomicInteger(0);
		final int n_cpus = Prefs.getThreads();
		final Thread[] threads = ThreadUtil.createThreadArray(n_cpus);
		setThreadedCounter(0);

		for (int ithread = 0; ithread < threads.length; ithread++) {

			final int chunkSize = (nspheres + n_cpus - 1) / n_cpus; // divide by
																	// threads
																	// rounded
																	// up.
			final int start = ithread * chunkSize;
			final int end = Math.min(start + chunkSize, nspheres);

			threads[ithread] = new Thread() {

				@Override
				public void run() {
					for (int k = ai.getAndIncrement(); k < n_cpus; k = ai.getAndIncrement()) {

						for (int s = start; s < end; s++) {
							final int counter = getThreadedCounter();
							statusService.showProgress(counter, nspheres);
							statusService.showStatus("Sampling sphere " + (counter + 1) + "/" + nspheres + " (" + n_cpus
									+ " threads). Press 'Esc' to abort...");
							setThreadedCounter(counter + 1);
							if (IJ.escapePressed()) {
								IJ.beep();
								return;
							}

							// Initialize ArrayLists to hold surface points
							final Set<UPoint> pixelPoints = new HashSet<>();

							// Restrain analysis to the smallest volume for this
							// sphere
							final double r = radii.get(s);
							final int xmin = Math.max(xc - (int) Math.round(r / vxWH), minX);
							final int ymin = Math.max(yc - (int) Math.round(r / vxWH), minY);
							final int zmin = Math.max(zc - (int) Math.round(r / vxD), minZ);
							final int xmax = Math.min(xc + (int) Math.round(r / vxWH), maxX);
							final int ymax = Math.min(yc + (int) Math.round(r / vxWH), maxY);
							final int zmax = Math.min(zc + (int) Math.round(r / vxD), maxZ);

							for (int z = zmin; z < zmax; z++) {
								for (int y = ymin; y < ymax; y++) {
									for (int x = xmin; x < xmax; x++) {
										final UPoint p = new UPoint(x, y, z);
										if (p.distanceSquared(c) == r * r) {
											final double value = stack.getVoxel(x, y, z);
											if (value < lowerT || value > upperT)
												continue;
											if (skipSingleVoxels && !hasNeighbors(x, y, z, stack))
												continue;
											pixelPoints.add(new UPoint(x, y, z));
										}
									}
								}
							}

							// We now have the the points intercepting the
							// surface of this shell: Check if they are
							// clustered and add them in world coordinates
							// to profile
							cleanse3Dgroups(pixelPoints);
							UPoint.scale(pixelPoints, cal);
							profile.add(new ProfileEntry(r, pixelPoints));

						}

					}

				}
			};
		}
		ThreadUtil.startAndJoin(threads);

		return profile;

	}

	public void cleanse3Dgroups(final Set<UPoint> points) {

		UPoint previousPoint = null;
		for (final Iterator<UPoint> it = points.iterator(); it.hasNext();) {
			final UPoint currentPoint = it.next();
			if (previousPoint != null) {
				// Compute the chessboard (Chebyshev) distance for this point. A
				// chessboard distance of 1 in xy (lateral) underlies
				// 8-connectivity within the plane. A distance of 1 in z (axial)
				// underlies 26-connectivity in 3D
				if (currentPoint.chebyshevDxTo(previousPoint) <= 1)
					it.remove();
			}
			previousPoint = currentPoint;
		}
	}

	private boolean hasNeighbors(final int x, final int y, final int z, final ImageStack stack) {

		final int[][] neighboors = new int[6][3];

		neighboors[0] = new int[] { x - 1, y, z };
		neighboors[1] = new int[] { x + 1, y, z };
		neighboors[2] = new int[] { x, y - 1, z };
		neighboors[3] = new int[] { x, y + 1, z };
		neighboors[4] = new int[] { x, y, z + 1 };
		neighboors[5] = new int[] { x, y, z - 1 };

		boolean clustered = false;
		double value;
		for (int i = 0; i < neighboors.length; i++) {
			try {
				value = stack.getVoxel(neighboors[i][0], neighboors[i][1], neighboors[i][2]);
				if (value >= lowerT && value <= upperT) {
					clustered = true;
					break;
				}
			} catch (final IndexOutOfBoundsException ignored) { // Edge voxel:
																// Neighborhood
																// unknown.
				clustered = false;
				break;
			}
		}

		return clustered;

	}

	private int getThreadedCounter() {
		return progressCounter;
	}

	private void setThreadedCounter(final int updatedCounter) {
		progressCounter = updatedCounter;
	}

	public void setHemiShells(final String flag) {
		checkUnsetFields();
		final int maxRadius = (int) Math.round(radii.get(radii.size() - 1) / voxelSize);
		minX = Math.max(xc - maxRadius, 0);
		maxX = Math.min(xc + maxRadius, imp.getWidth());
		minY = Math.max(yc - maxRadius, 0);
		maxY = Math.min(yc + maxRadius, imp.getHeight());
		minZ = Math.max(zc - maxRadius, 1);
		maxZ = Math.min(zc + maxRadius, imp.getNSlices());
		final String fFlag = (flag == null || flag.isEmpty()) ? HEMI_NONE : flag.trim().toLowerCase();
		switch (fFlag) {
		case HEMI_NORTH:
			maxY = Math.min(yc + maxRadius, yc);
			break;
		case HEMI_SOUTH:
			minY = Math.max(yc - maxRadius, yc);
			break;
		case HEMI_WEST:
			minX = xc;
			break;
		case HEMI_EAST:
			maxX = xc;
			break;
		case HEMI_NONE:
			break;
		default:
			throw new IllegalArgumentException("Unrecognized flag: " + flag);
		}
		properties.setProperty(KEY_HEMISHELLS, fFlag);
	}

	public void setSkipSingleVoxels(final boolean skip) {
		skipSingleVoxels = skip;
	}

	public boolean isSkipSingleVoxels() {
		return skipSingleVoxels;
	}
}