package ez.pogdog.yescom.core.report;

import ez.pogdog.yescom.core.connection.Player;

/**
 * A report is a message indicating that something has happened on the server. They are linked to {@link Player}s,
 * though may not be directly caused by one.
 */
public interface IReport<T> {

    /**
     * @return The name of the report.
     */
    String getName();

    /**
     * @return A human friendly description of the report.
     */
    String getDescription();

    /**
     * @return Any data associated with the report.
     */
    T getData();

    /**
     * @return The player the report is linked to.
     */
    Player getPlayer();
}
