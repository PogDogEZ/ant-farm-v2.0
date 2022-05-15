package ez.pogdog.yescom.core.report.invalidmove;

import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

/**
 * Reports that some form of packet loss has occurred.
 */
public class PacketLossReport extends Report<Integer> {

    private final Player player;
    private final int type;

    public PacketLossReport(Player player, int type) {
        this.player = player;
        this.type = type;
    }

    @Override
    public String getName() {
        return "Packet loss";
    }

    @Override
    public String getDescription() {
        return "Occurs when some form of packet loss is detected.";
    }

    @Override
    public Integer getData() {
        return type;
    }

    @Override
    public Player getPlayer() {
        return player;
    }
}
