package ez.pogdog.yescom.core.report;

import ez.pogdog.yescom.core.connection.Player;

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
}
