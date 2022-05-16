package ez.pogdog.yescom.core.report.player;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

public class HealthLogoutReport extends Report<Float> {

    private final Player player;
    private final float health;

    public HealthLogoutReport(Player player, float health) {
        this.player = player;
        this.health = health;
    }

    @Override
    public String getName() {
        return "Health logout";
    }

    @Override
    public String getDescription() {
        return "Occurs when a player is kicked because their health is below a certain threshold.";
    }

    @Override
    public Float getData() {
        return health;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
