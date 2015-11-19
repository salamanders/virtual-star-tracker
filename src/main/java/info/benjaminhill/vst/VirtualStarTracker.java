package info.benjaminhill.vst;

import java.nio.file.Paths;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import info.benjaminhill.util.DBLite;

/**
 * @author benhill
 */
public class VirtualStarTracker {

	private static final Logger LOG = Logger.getLogger(VirtualStarTracker.class.getName());
	private static final int COMBINE_EXPOSURE_MS = 1_000 * 15;

	/**
	 * @param args
	 */
	public static void main(final String... args) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
		Frame.schema();
		Pixel.schema();

		if (DBLite.DB.selectLong("SELECT count(1) FROM `frame`") < 3) {
			LOG.info("Not enough frames, reloading.");

			ImageUtils.getImageFiles(Paths.get("frames")).parallelStream().forEach(file -> new Frame(file).save());
			LOG.info("Saved all frames");
		}

		if (DBLite.DB.selectLong("SELECT count(1) FROM `pixel`") < 10) {
			LOG.info("Not enough pixels, reloading brights.");
			Frame.loadTimeGrouped(COMBINE_EXPOSURE_MS).parallelStream().forEach(frame -> {
				frame.findBrights();
				LOG.log(Level.INFO, "{0}\t{1}", new Object[] { frame.getPixels().size(), frame.getAddedPath() });
			});
			LOG.info("Finished finding pixels.");
		}

		final SortedSet<Frame> frames = Frame.loadTimeGrouped(COMBINE_EXPOSURE_MS);

		LOG.log(Level.INFO, "DB has frame groups:{0}", frames.size());

		final SortedSet<PixelTrail> activeTrails = new ConcurrentSkipListSet<>();
		final ConcurrentSkipListSet<PixelTrail> finishedTrails = new ConcurrentSkipListSet<>();

		frames.forEach(frame -> {

			finishedTrails.addAll(activeTrails.stream().filter(pt -> {
				return !pt.isWithinTimeWindow(frame.getTs());
			}).collect(Collectors.toList()));

			activeTrails.removeAll(finishedTrails);

			frame.getPixels().stream().forEach(pixel -> {
				// First check to see if it can be added to an existing trail
				for (final PixelTrail trail : activeTrails) {
					if (trail.offerPixel(frame, pixel)) {
						return;
					}
				}
				PixelTrail newTrail = new PixelTrail();
				newTrail.offerPixel(frame, pixel);
				activeTrails.add(newTrail);
			});
		});
		finishedTrails.addAll(activeTrails);
		activeTrails.clear();
		LOG.log(Level.INFO, "Trails: {0}", finishedTrails.size());

		final List<PixelTrail> longTrails = finishedTrails.stream().filter(t -> t.getDuration() > 1_000 * 60 * 10 && t
				.getPixelDistanceSq() > 10).collect(Collectors.toList());

		LOG.log(Level.INFO, "Long Trails: {0}", longTrails.size());

	}

}
