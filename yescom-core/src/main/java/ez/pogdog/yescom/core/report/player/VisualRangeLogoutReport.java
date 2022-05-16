package ez.pogdog.yescom.core.report.player;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

import java.util.UUID;

public class VisualRangeLogoutReport extends Report<UUID> {

    private final Player player;
    private final UUID uuid;

    public VisualRangeLogoutReport(Player player, UUID uuid) {
        this.player = player;
        this.uuid = uuid;
    }

    @Override
    public String getName() {
        return "Visual range logout";
    }

    @Override
    public String getDescription() {
        return "Occurs when a player logs out due to another player entering their visual range.";
    }

    @Override
    public UUID getData() {
        return uuid;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
