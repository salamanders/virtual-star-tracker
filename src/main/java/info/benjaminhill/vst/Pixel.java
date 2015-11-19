package info.benjaminhill.vst;

import java.awt.Point;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import info.benjaminhill.util.DBLite;

/**
 * Stores x, y, lum of a pixel
 *
 * @author benhill
 */
public class Pixel implements Comparable<Pixel> {

	private static final Logger LOG = Logger.getLogger(Pixel.class.getName());

	/**
	 * @param frame_id
	 * @return
	 */
	public static SortedSet<Pixel> load(final long frame_id) {
		return new ConcurrentSkipListSet<>(DBLite.DB.selectTable("select `rowid`,`x`,`y`,`lum` from pixel"
				+ " WHERE `frame_id`=?", frame_id).rowMap().entrySet().stream().<Pixel>map(ent -> {
					return new Pixel(ent.getValue());
				}).collect(Collectors.toSet()));
	}

	/**
	 *
	 */
	public static void schema() {
		// DBLite.DB.update("drop table if exists pixel");
		if (!DBLite.DB.tableExists("pixel")) {
			DBLite.DB.update("create table pixel (" + " frame_id integer" + ", x integer" + ", y integer" + ", lum integer"
					+ ", trail integer" + ", PRIMARY KEY (frame_id, x, y)" + ", FOREIGN KEY(frame_id) REFERENCES frame(id)"
					+ ")");
			LOG.info("Created table `pixel`");
		}
	}

	/**
	 *
	 */
	public final Point loc = new Point();

	/**
	 *
	 */
	public final short lum;
	private int trail = -1;

	/**
	 * @param x
	 * @param y
	 * @param luminosity
	 */
	public Pixel(int x, int y, byte luminosity) {
		this(x, y, (luminosity & 0xFF));
	}

	/**
	 * @param x
	 * @param y
	 * @param lum
	 */
	public Pixel(final int x, final int y, final int lum) {
		this.loc.x = x;
		this.loc.y = y;
		this.lum = (short) lum;
	}

	/**
	 * @param row
	 */
	public Pixel(final Map<String, Object> row) {
		this.loc.x = (Integer) row.get("x");
		this.loc.y = (Integer) row.get("y");
		this.lum = ((Number) row.get("lum")).shortValue();
		if (row.containsKey("trail")) {
			trail = (Integer) row.get("trail");
		}
	}

	/**
	 * Order by descending lum
	 *
	 * @param other
	 * @param o
	 * @return
	 */
	@Override
	public int compareTo(final Pixel other) {
		return -1 * Short.compare(lum, other.lum);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Pixel other = (Pixel) obj;
		return (this.loc.x == other.loc.x && this.loc.y == other.loc.y);
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 47 * hash + Objects.hashCode(this.loc);
		return hash;
	}

	/**
	 * @param frame_id
	 * @return
	 */
	public Pixel save(final long frame_id) {
		DBLite.DB.update("insert OR IGNORE into `pixel` (`frame_id`,`x`,`y`,`lum`,`trail`) VALUES(?,?,?,?,?)", frame_id,
				loc.x, loc.y, lum, trail);
		return this;
	}

	/**
	 * @param trail
	 */
	public void setTrail(final int trail) {
		this.trail = trail;
	}

	/**
	 * JSON compatible
	 *
	 * @return
	 */
	@Override
	public String toString() {
		return "{" + "x:" + loc.x + ", y:" + loc.y + ", lum:" + lum + '}';
	}

}
