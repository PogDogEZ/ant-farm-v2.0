package ez.pogdog.yescom.ui.util;

/**
 * Utility class for reflection, as jep doesn't allow you to (as far as I can tell).
 */
public class ReflectionUtil {

	/**
	 * @return Is the provided class an array?
	 */
	public static boolean isArray(Class<?> clazz) {
		return clazz.isArray();
	}

	/**
     * @return Is the provided class an enum type?
     */
	public static boolean isEnum(Class<?> clazz) {
		return clazz.isEnum();
	}

	/**
	 * @return Is the "to" class assignable from the "from" class?
	 */
	public static boolean isAssignableFrom(Class<?> from, Class<?> to) {
		return to.isAssignableFrom(from);
	}
}