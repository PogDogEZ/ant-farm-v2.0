package ez.pogdog.yescom.core.report.connection;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

public class HighTSLPReport extends Report<Integer> {

    private final Player player;
    private final int tslp;

    public HighTSLPReport(Player player, int tslp) {
        this.player = player;
        this.tslp = tslp;
    }

    @Override
    public String getName() {
        return "High TSLP";
    }

    @Override
    public String getDescription() {
        return "Occurs when the server has not responded for a large amount of time.";
    }

    @Override
    public Integer getData() {
        return tslp;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
