package ez.pogdog.yescom.api.data.tracking;

import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;

import java.util.Objects;

/**
 * Represents a highway on a server.
 */
public class Highway {

	public final ChunkPosition startPosition;
	public final ChunkPosition endPosition;
	public final Dimension dimension;
	public final Type type;

	public Highway(ChunkPosition startPosition, ChunkPosition endPosition, Dimension dimension, Type type) {
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.dimension = dimension;
		this.type = type;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null || getClass() != other.getClass()) return false;
		Highway that = (Highway)other;
		return startPosition.equals(that.startPosition) && endPosition.equals(that.endPosition) && dimension.equals(that.dimension) && type.equals(that.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(startPosition, endPosition, type);
	}

	@Override
	public String toString() {
		return String.format("Highway(start=%s, end=%s, dimension=%s, type=%s)", startPosition, endPosition, dimension, type);
	}

	/**
	 * @return The length of this highway, in chunks.
	 */
	public float getLength() {
		int diffX = endPosition.getX() - startPosition.getX();
		int diffZ = endPosition.getZ() - startPosition.getZ();
		return (float)Math.sqrt(diffX * diffX + diffZ * diffZ);
	}

	/**
	 * The type of highway this is.
	 */
	public enum Type {
		AXIS, DIAGONAL, RINGROAD, MISC;
	}
}