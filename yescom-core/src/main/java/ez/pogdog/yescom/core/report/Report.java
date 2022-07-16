package ez.pogdog.yescom.core.report;

import ez.pogdog.yescom.core.connection.Player;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A report is a message indicating that something has happened on the server. They are linked to {@link Player}s,
 * though may not be directly caused by one.
 */
public abstract class Report<T> {

    @Override
    public String toString() {
        return String.format("%s(player=%s, data=%s)", getClass().getSimpleName(), getPlayer(), getData());
    }

    /**
     * @return The name of the report.
     */
    public abstract String getName();

    /**
     * @return A human friendly description of the report.
     */
    public abstract String getDescription();

    /**
     * @return Any data associated with the report.
     */
    public abstract T getData();

    /**
     * @return The player the report is linked to.
     */
    public abstract Player getPlayer();

    /**
     * @return {@link Action}s that are available if this report occurs.
     */
    public List<Action> getActions() { // TODO: Actions
        return Collections.emptyList();
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Actions can be selected by the user. They will automatically perform recommended responses to the report.
     */
    public static final class Action {

        public final Function<Report<?>, Boolean> function;
        public final String name;
        public final String description;

        public Action(Function<Report<?>, Boolean> function, String name, String description) {
            this.function = function;
            this.name = name;
            this.description = description;
        }

    }
}
