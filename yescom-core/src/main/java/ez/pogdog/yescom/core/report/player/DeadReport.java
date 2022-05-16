package ez.pogdog.yescom.core.report.player;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

public class DeadReport extends Report<Object> {

    private final Player player;

    public DeadReport(Player player) {
        this.player = player;
    }

    @Override
    public String getName() {
        return "Dead";
    }

    @Override
    public String getDescription() {
        return "Occurs when a player is dead.";
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
