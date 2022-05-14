package ez.pogdog.yescom.core.report.connection;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.IReport;

/**
 * Indicates that a weird kick has occurred (the reason is not known).
 */
public class WeirdKickReport implements IReport<String> {

    private final Player player;
    private final String reason;

    public WeirdKickReport(Player player, String reason) {
        this.player = player;
        this.reason = reason;
    }

    @Override
    public String getName() {
        return "Weird Kick";
    }

    @Override
    public String getDescription() {
        return "Occurs when a player has been kicked with an unknown kick message (could be Phantom).";
    }

    @Override
    public String getData() {
        return reason;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
