package ez.pogdog.yescom.core.report.connection;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.IReport;

/**
 * Occurs when an extreme tickrate change is detected.
 */
public class ExtremeTPSReport implements IReport<Float> {

    private final Player player;
    private final float delta;

    public ExtremeTPSReport(Player player, float delta) {
        this.player = player;
        this.delta = delta;
    }

    @Override
    public String getName() {
        return "Extreme TPS";
    }

    @Override
    public String getDescription() {
        return "Occurs when an extreme tickrate change is detected.";
    }

    @Override
    public Float getData() {
        return delta;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
