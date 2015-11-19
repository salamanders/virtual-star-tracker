package info.benjaminhill.vst;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;

/**
 * @author benhill
 */
public class Brightest {

	/**
	 * Must be at least this bright to be considered
	 */
	private static final int MINIMUM_LUM = 5;
	private static final Set<Integer> BUCKETS = ImmutableSet.of(13, 17, 71, 113);

	private static final Logger LOG = Logger.getLogger(Brightest.class.getName());

	/**
	 * All bright pixels using two prime number bucket sizes. Overkill? Maybe.
	 *
	 * @param img
	 * @return
	 */
	public static Set<Pixel> getBrightest(final String img) {

		final BufferedImage gray = ImageUtils.getGray(new File(img));

		Set<Pixel> result = new HashSet<>();
		BUCKETS.stream().forEach((bucketPixelSize) -> {
			result.addAll(getBrightestPerBucket(bucketPixelSize, gray).values().stream().filter((
					brightestInBucket) -> (brightestInBucket.lum >= MINIMUM_LUM)).collect(Collectors.toSet()));
		});
		return result;
	}

	/**
	 * For each bucket, find the brightest pixel location
	 *
	 * @param imageFile
	 *          grayscale
	 * @return
	 */
	private static Map<Integer, Pixel> getBrightestPerBucket(final int bucketPixelSize, final BufferedImage gray) {
		final int width = gray.getWidth(), height = gray.getHeight();
		final int widthInBuckets = 1 + (width / bucketPixelSize);

		final byte[] pixels = ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();

		final Map<Integer, Pixel> result = new HashMap<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int idx = (y * width) + x;
				final short lum = pixels[idx];

				final int bucketX = x / bucketPixelSize;
				final int bucketY = y / bucketPixelSize;

				final int bucketId = bucketY * widthInBuckets + bucketX;

				if (!result.containsKey(bucketId) || result.get(bucketId).lum < lum) {
					result.put(bucketId, new Pixel(x, y, lum));
				}
			}
		}
		return result;
	}

	private Brightest() {
		// empty
	}
}
