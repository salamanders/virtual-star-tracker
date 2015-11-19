package info.benjaminhill.vst;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

import info.benjaminhill.util.DBLite;

/**
 * Wraps a frame
 *
 * @author benjaminhill@gmail.com
 */
public class Frame implements Comparable<Frame> {

	private static final Logger LOG = Logger.getLogger(Frame.class.getName());

	/**
	 * @return frames that are grouped into a time window (may be 1 or more frames under the hood)
	 */
	public static SortedSet<Frame> loadTimeGrouped(final long ms) {

		final Map<Long, List<Frame>> framesGroupByWindow = DBLite.DB.selectTable("select `ts`,`path`,`exposure` from frame")
				.rowMap().entrySet().stream().<Map<String, Object>>map(ent -> {
					final Map<String, Object> row = new HashMap<>();
					row.putAll(ent.getValue());
					row.put("ts", Long.parseLong(ent.getKey()));
					return row;
				}).<Frame>map(row -> {
					final Frame newFrame = new Frame(row);
					newFrame.pixels.addAll(Pixel.load(newFrame.ts));
					return newFrame;
				}).collect(Collectors.groupingBy(frame -> {
					return frame.getTs() / ms;
				}));

		return new ConcurrentSkipListSet<>(framesGroupByWindow.entrySet().stream().<Frame>map(ent -> {
			final SortedSet<Frame> thisWindow = new ConcurrentSkipListSet<>(ent.getValue());
			final Frame rootFrame = thisWindow.first();
			thisWindow.tailSet(rootFrame).stream().forEach(subFrame -> {
				rootFrame.addPaths.add(subFrame.path);
			});
			return rootFrame;
		}).collect(Collectors.toSet()));

	}

	/**
	 *
	 */
	public static void schema() {
		// New each time
		// DBLite.DB.update("drop table if exists frame");
		if (!DBLite.DB.tableExists("frame")) {
			DBLite.DB.update("create table frame (" + "ts integer PRIMARY KEY" + ", path string UNIQUE" + ", exposure numeric"
					+ ")");
			LOG.info("Created table `frame`");
		}
	}

	/**
	 * Timestamp is in MS
	 */
	private final long ts;
	private final String path;
	/**
	 * If this frame should use more paths. Not persisted.
	 */
	private final SortedSet<String> addPaths = new ConcurrentSkipListSet<>();
	private double exposure = -1;
	private final SortedSet<Pixel> pixels = new ConcurrentSkipListSet<>();

	/**
	 * @param frameFile
	 */
	public Frame(final File frameFile) {

		try {
			path = frameFile.getCanonicalPath();
			final Metadata metadata = ImageMetadataReader.readMetadata(frameFile);

			final ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

			if (directory != null) {

				// Original TS
				Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
				if (date == null) {
					date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME);
				}
				if (date != null) {
					ts = date.getTime();
				} else {
					throw new RuntimeException("Unable to load timestamp from image.");
				}

				// exposure time
				final Rational exTimeR = directory.getRational(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
				exposure = (1.0 * exTimeR.getNumerator()) / exTimeR.getDenominator();
			} else {
				throw new RuntimeException("Unable to load timestamp from image.");
			}
		} catch (final ImageProcessingException | IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * @param row
	 */
	public Frame(final Map<String, Object> row) {
		this.ts = (Long) row.get("ts");
		this.path = (String) row.get("path");
		if (row.containsKey("exposure")) {
			this.exposure = (Double) row.get("exposure");
		}
	}

	@Override
	public int compareTo(final Frame other) {
		return Long.compare(this.ts, other.ts);
	}

	/**
	 * Immediately saves to DB
	 */
	public void findBrights() {
		pixels.addAll(Brightest.getBrightest(getAddedPath()));
		pixels.stream().forEach(pixel -> {
			pixel.save(ts);
		});
	}

	/**
	 * Adds together all sub-frames into a single image
	 * 
	 * @throws IOException
	 */
	public String getAddedPath() {
		if (addPaths.isEmpty()) {
			return path;
		}

		try {
			final File additiveFile = Paths.get("add", Files.getNameWithoutExtension(path) + "_add.png").toFile();
			if (additiveFile.exists()) {
				return additiveFile.getCanonicalPath().toString();
			}

			Files.createParentDirs(additiveFile);

			final LinkedList<String> commands = new LinkedList<>();

			commands.addLast(additiveFile.toString());
			commands.addFirst("add");
			commands.addFirst("-evaluate-sequence");
			commands.addFirst(path);
			for (final String img : addPaths) {
				commands.addFirst(img);
			}
			commands.addFirst("/usr/local/bin/convert");
			System.out.println(Joiner.on(" ").join(commands));
			final ProcessBuilder pb = new ProcessBuilder(commands);
			final Process process = pb.start();

			try (final BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));) {
				String line;
				while ((line = br.readLine()) != null) {
					System.out.println(line);
				}
			}
			return additiveFile.toString();
		} catch (final IOException ex) {
			throw new RuntimeException(ex);
		}

	}

	/**
	 * @return
	 */
	public double getExposure() {
		return exposure;
	}

	/**
	 * @return
	 */
	public SortedSet<Pixel> getPixels() {
		return Collections.unmodifiableSortedSet(pixels);
	}

	/**
	 * @return
	 */
	public long getTs() {
		return ts;
	}

	/**
	 * @return
	 */
	public Frame save() {
		DBLite.DB.update("insert OR IGNORE into frame(`ts`,`path`,`exposure`) values(?,?,?)", ts, path, exposure);
		return this;
	}

}
