package ez.pogdog.yescom.core.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that contains configuration options.
 */
public interface IConfig {

    /**
     * @return An identifier unique to this configuration.
     */
    String getIdentifier();

    /**
     * @return The fully qualified identifier for this configuration.
     */
    default String getFullIdentifier() {
        if (getParent() != null) return getParent().getFullIdentifier() + "." + getIdentifier();
        return getIdentifier();
    }

    /**
     * @return The parent configuration that this one belongs to.
     */
    IConfig getParent();

    /**
     * @param force Forcefully get all the options (including ungettable ones).
     * @return All the options that this configuration contains.
     */
    default List<Option<?>> getOptions(boolean force) {
        List<Option<?>> options = new ArrayList<>();

        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(Option.class) && (force || !field.isAnnotationPresent(Option.Ungettable.class))) {
                field.setAccessible(true);
                try {
                    options.add((Option<?>)field.get(this));
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        return options;
    }

    /**
     * @return All the options that this configuration contains.
     */
    default List<Option<?>> getOptions() {
        return getOptions(false);
    }
}
