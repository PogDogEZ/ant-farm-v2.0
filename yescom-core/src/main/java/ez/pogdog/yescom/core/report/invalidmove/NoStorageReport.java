package ez.pogdog.yescom.core.report.invalidmove;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

/**
 * No usable storage was found.
 */
public class NoStorageReport extends Report<Object> {

    private final Player player;

    public NoStorageReport(Player player) {
        this.player = player;
    }

    @Override
    public String getName() {
        return "No Storage";
    }

    @Override
    public String getDescription() {
        return "No usable storage was found for the player.";
    }

    @Override
    public Object getData() {
        return null;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
