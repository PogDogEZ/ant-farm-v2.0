package ez.pogdog.yescom.core.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single configuration option.
 * @param <T> The type of the option.
 */
public class Option<T> {

	public T value;

	public Option(T value) {
		this.value = value;
	}

	/**
	 * Used to denote that an option cannot be obtained reflectively (internal to the framework).
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Ungettable {
	}

	/**
	 * Used to denote that an option is not intended to be set by the user.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Unsettable {
	}
}
