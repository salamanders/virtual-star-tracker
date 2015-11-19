package info.benjaminhill.vst;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import com.google.common.primitives.Shorts;

/**
 * Grab bag of image read/write utilities
 *
 * @author benjaminhill@gmail.com
 */
public class ImageUtils {

	private static final Logger LOG = Logger.getLogger(ImageUtils.class.getName());

	/**
	 * @param colorImage
	 * @return
	 */
	public static BufferedImage getGray(final BufferedImage colorImage) {
		assert colorImage != null;
		final int width = colorImage.getWidth(), height = colorImage.getHeight();
		final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		final Graphics g = result.getGraphics();
		g.drawImage(colorImage, 0, 0, null);
		g.dispose();
		return result;
	}

	/**
	 * Image flattened and turned grey
	 *
	 * @param imageFile
	 * @return
	 */
	public static BufferedImage getGray(final File imageFile) {
		try {
			assert imageFile.canRead();
			final BufferedImage colorImage = ImageIO.read(imageFile);
			assert colorImage != null;
			return getGray(colorImage);
		} catch (final IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Sorted list of image files
	 *
	 * @param dir
	 * @return
	 */
	public static SortedSet<File> getImageFiles(final Path dir) {
		// final Path dir = Paths.get("", paths);
		final SortedSet<File> imagePaths = new ConcurrentSkipListSet<>();
		try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{jpg,JPG,png,PNG}")) {
			for (final Path entry : stream) {
				imagePaths.add(entry.toFile());
			}
		} catch (IOException x) {
			System.err.println(x);
		}
		return imagePaths;
	}

	/**
	 * @return
	 */
	public static Path getStartingPath() {
		final JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Choose the folder to scan");
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		jfc.setApproveButtonText("Start in this Folder");
		final int returnVal = jfc.showOpenDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			throw new RuntimeException("No directory chosen.");
		}
		return Paths.get(jfc.getSelectedFile().toURI());
	}

	/** Ignores that some arrays can be even length, doesn't average the two middle. */
	private static short median(final short[] data) {
		Arrays.sort(data);
		return data[data.length / 2];
	}

	/**
	 * @param fileName
	 * @param pixelLums
	 * @param width
	 * @param height
	 * @param normalize
	 */
	public static void writeImageBW(final String fileName, final short[] pixelLums, final int width, final int height,
			final boolean normalize) {
		final float maxValue = normalize ? Shorts.max(pixelLums) : 255f;

		LOG.log(Level.INFO, "Writing to {0} with max pixel lum of {1} normalise:{2}", new Object[] { fileName, Shorts.max(
				pixelLums), normalize });

		final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				result.setRGB(x, y, Color.HSBtoRGB(0, 0, Math.min(1f, pixelLums[y * width + x] / maxValue)));
			}
		}
		try {
			ImageIO.write(result, "png", new File(fileName + ".png"));
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, null, ex);
		}
	}

	private ImageUtils() {
		// empty
	}

	public void lumMedian(final SortedSet<File> files) {

		// Original image data
		final Map<File, BufferedImage> bis = new ConcurrentHashMap<>();
		// all images lum values as array of short
		final Map<File, short[]> lums = new ConcurrentHashMap<>();

		files.parallelStream().limit(75).forEach((imageFile) -> {
			try {
				final BufferedImage bi = ImageIO.read(imageFile);
				bis.put(imageFile, bi);
				final BufferedImage grayImage = ImageUtils.getGray(bi);
				final byte[] data = ((DataBufferByte) grayImage.getRaster().getDataBuffer()).getData();
				final short[] converted = new short[data.length];
				for (int i = 0; i < data.length; i++) {
					converted[i] = (short) (data[i] & 0xFF);
				}
				lums.put(imageFile, converted);
			} catch (IOException ex) {
				LOG.log(Level.SEVERE, null, ex);
			}
		});

		final int numPixels = lums.get(files.first()).length;

		lums.entrySet().stream().forEach((imgEnt) -> {
			if (numPixels != imgEnt.getValue().length) {
				System.err.println("File " + imgEnt.getKey().toString() + " has " + imgEnt.getValue().length
						+ " pixels instead of " + numPixels);
				files.remove(imgEnt.getKey());
				lums.remove(imgEnt.getKey());
				bis.remove(imgEnt.getKey());
			}
		});

		LOG.log(Level.INFO, "Images loaded:{0}", bis.size());
		LOG.log(Level.INFO, "Luminance loaded:{0}", lums.size());

		// Find the median lum value
		// Find the average pixel color for all frames matching that particular lum value
		final short[] median = new short[numPixels], sum = new short[numPixels], average = new short[numPixels];
		final List<short[]> lumAsList = new ArrayList<>(lums.values());

		IntStream.range(0, numPixels).parallel().forEach(pixelId -> {
			final short[] oneFromEach = new short[lumAsList.size()];
			IntStream.range(0, lumAsList.size()).parallel().forEach(frameId -> {
				// assert frameId < oneFromEach.length;
				// assert frameId < lumAsList.size();
				// assert pixelId < lumAsList.get(frameId).length : "Frame " + frameId + " pixel not less than length:" +
				// pixelId + " " + lumAsList.get(frameId).length;
				oneFromEach[frameId] = lumAsList.get(frameId)[pixelId];
				sum[pixelId] += oneFromEach[frameId];
			});
			median[pixelId] = median(oneFromEach);
		});
		// System.out.print(Arrays.toString(median));

		final int width = bis.get(files.first()).getWidth(), height = bis.get(files.first()).getHeight();
		for (int i = 0; i < sum.length; i++) {
			average[i] = (short) ((50f * sum[i]) / lumAsList.size());
		}
		LOG.log(Level.INFO, "Max of the averages:{0}", Shorts.max(average));

		ImageUtils.writeImageBW("sum", sum, width, height, false);
		ImageUtils.writeImageBW("median", median, width, height, false);
		ImageUtils.writeImageBW("average", median, width, height, true);
	}
}
