package sholl.parsers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.plugin.ChannelSplitter;
import ij.util.ThreadUtil;
import sholl.Profile;
import sholl.ProfileEntry;
import sholl.UPoint;

public class ImageParser3D extends ImageParser {

	private double vxWH, vxD;
	private int progressCounter;
	private boolean skipSingleVoxels;
	private ImageStack stack;
	private final double EQUALITY_PRECISION = 0.000000001;

	public ImageParser3D(final ImagePlus imp) {
		super(imp);
		skipSingleVoxels = true;
		setPosition(imp.getC(), imp.getT());
	}

	@Override
	public Profile parse() {
		checkUnsetFields();
		if (UNSET.equals(properties.getProperty(KEY_HEMISHELLS, UNSET)))
			setHemiShells(HEMI_NONE);

		final int nspheres = radii.size();
		final UPoint c = new UPoint(xc, yc, zc, cal);
		stack = (imp.isComposite()) ? ChannelSplitter.getChannel(imp, channel) : imp.getStack();
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
							statusService.showStatus(counter, nspheres, "Sampling shell " + (counter + 1) + "/"
									+ nspheres + " (" + n_cpus + " threads). Press 'Esc' to abort...");
							setThreadedCounter(counter + 1);
							if (IJ.escapePressed()) {
								IJ.beep();
								return;
							}

							// Initialize ArrayLists to hold surface points
							final ArrayList<UPoint> pixelPoints = new ArrayList<>();

							// Restrain analysis to the smallest volume for this
							// sphere
							final double r = radii.get(s);
							final double rSq = r * r;
							final int xmin = Math.max(xc - (int) Math.round(r / vxWH), minX);
							final int ymin = Math.max(yc - (int) Math.round(r / vxWH), minY);
							final int zmin = Math.max(zc - (int) Math.round(r / vxD), minZ);
							final int xmax = Math.min(xc + (int) Math.round(r / vxWH), maxX);
							final int ymax = Math.min(yc + (int) Math.round(r / vxWH), maxY);
							final int zmax = Math.min(zc + (int) Math.round(r / vxD), maxZ);

							for (int z = zmin; z < zmax; z++) {
								for (int y = ymin; y < ymax; y++) {
									for (int x = xmin; x < xmax; x++) {
										final UPoint p = new UPoint(x, y, z, cal);
										if (Math.abs(p.distanceSquared(c) - rSq) < EQUALITY_PRECISION) {
											if (!withinThreshold(stack.getVoxel(x, y, z)))
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
							final HashSet<UPoint> points = getUnique3Dgroups(pixelPoints);
							UPoint.scale(points, cal);
							profile.add(new ProfileEntry(r, points));

						}

					}

				}
			};
		}
		ThreadUtil.startAndJoin(threads);

		return profile;

	}

	protected HashSet<UPoint> getUnique3Dgroups(final ArrayList<UPoint> points) {

		for (int i = 0; i < points.size(); i++) {
			for (int j = i + 1; j < points.size(); j++) {

				final UPoint pi = points.get(i);
				final UPoint pj = points.get(j);
				// Compute the chessboard (Chebyshev) distance for this point. A
				// chessboard distance of 1 in xy (lateral) underlies
				// 8-connectivity within the plane. A distance of 1 in z (axial)
				// underlies 26-connectivity in 3D
				if (pi.chebyshevXYdxTo(pj) < 2 || pi.chebyshevZdxTo(pj) < 2) {
					pj.setFlag(UPoint.DELETE);
				}
			}
		}

		final Iterator<UPoint> it = points.iterator();
		while (it.hasNext()) {
			if (it.next().flag == UPoint.DELETE) {
				it.remove();
			}
		}

		return new HashSet<>(points);

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
				if (withinBounds(neighboors[i][0], neighboors[i][1], neighboors[i][2]) && withinThreshold(value)) {
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

	public void setSkipSingleVoxels(final boolean skip) {
		skipSingleVoxels = skip;
	}

	public boolean isSkipSingleVoxels() {
		return skipSingleVoxels;
	}
}
