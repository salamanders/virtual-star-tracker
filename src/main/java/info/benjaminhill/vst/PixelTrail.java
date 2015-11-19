package info.benjaminhill.vst;

import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;

/**
 * Set of pixels within frames that form a star trail.
 *
 * @author benjaminhill@gmail.com
 */
public class PixelTrail implements Comparable<PixelTrail> {

	/**
	 * If you had a gap of this many ms, the trail has ended.
	 */
	private static final int MAX_TS_GAP = 1_000 * 60 * 5;

	/**
	 * How far a trail can jump between frames (to avoid cross-trail linking
	 */
	private static final double MAX_DISTANCE_SQ = 2;
	private static final Logger LOG = Logger.getLogger(PixelTrail.class.getName());
	private final SortedMap<Frame, Pixel> trail = new ConcurrentSkipListMap<>();

	/**
	 * Order by descending lum
	 *
	 * @param other
	 * @return
	 */
	@Override
	public int compareTo(final PixelTrail other) {
		int comp = Short.compare(this.getLastPixel().lum, other.getLastPixel().lum);
		if (comp != 0) {
			// Descending lum
			return -1 * comp;
		}
		comp = Integer.compare(this.trail.size(), other.trail.size());
		if (comp != 0) {
			// Descending size
			return -1 * comp;
		}
		comp = Integer.compare(this.getLastPixel().loc.y, other.getLastPixel().loc.y);
		if (comp != 0) {
			return comp;
		}
		return Integer.compare(this.getLastPixel().loc.x, other.getLastPixel().loc.x);
	}

	/**
	 * @param frame
	 * @return
	 */
	public boolean containsFrame(final Frame frame) {
		return trail.containsKey(frame);
	}

	/**
	 * @param frame
	 * @return
	 */
	public Pixel getByFrame(final Frame frame) {
		return trail.get(frame);
	}

	/**
	 * @return
	 */
	public long getDuration() {
		return trail.lastKey().getTs() - trail.firstKey().getTs();
	}

	/**
	 * @return
	 */
	public Frame getFirstFrame() {
		return trail.firstKey();
	}

	/**
	 * @return
	 */
	public Pixel getFirstPixel() {
		return trail.get(trail.firstKey());
	}

	/**
	 * @return
	 */
	public Set<Frame> getFrames() {
		return trail.keySet();
	}

	/**
	 * @return
	 */
	public Frame getLastFrame() {
		return trail.lastKey();
	}

	/**
	 * @return
	 */
	public Pixel getLastPixel() {
		return trail.get(trail.lastKey());
	}

	/**
	 * @return
	 */
	public double getPixelDistanceSq() {
		return trail.get(trail.firstKey()).loc.distanceSq(trail.get(trail.lastKey()).loc);
	}

	/**
	 * @param ts
	 * @return
	 */
	public boolean isWithinTimeWindow(final long ts) {
		return Math.abs(ts - getLastFrame().getTs()) < MAX_TS_GAP;
	}

	/**
	 * Checks if this pixel is a good candidate (later frame and within range)
	 *
	 * @param frame
	 * @param candidate
	 * @return
	 */
	public boolean offerPixel(final Frame frame, final Pixel candidate) {
		if (!trail.isEmpty()) {

			if (frame.getTs() <= this.getLastFrame().getTs()) {
				return false;
			}

			if (frame.getTs() - this.getLastFrame().getTs() > MAX_TS_GAP) {
				return false;
			}

			if (trail.get(trail.lastKey()).loc.distanceSq(candidate.loc) > MAX_DISTANCE_SQ) {
				return false;
			}
		}

		trail.put(frame, candidate);
		return true;
	}

}
