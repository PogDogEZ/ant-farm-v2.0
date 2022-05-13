package ez.pogdog.yescom.core;

import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.api.event.Emitter;
import ez.pogdog.yescom.api.util.Pair;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.connection.Player;

/**
 * Global {@link Emitter}s for YesCom.
 */
public class Emitters {

    /**
     * Fired at the beginning of every global tick.
     */
    public static final Emitter<?> ON_PRE_TICK = new Emitter<>(null);

    /**
     * Fired at the end of every global tick.
     */
    public static final Emitter<?> ON_POST_TICK = new Emitter<>(null);

    /* ------------------------------ Players ------------------------------ */

    /**
     * Fired when an account is added.
     */
    public static final Emitter<IAccount> ON_ACCOUNT_ADDED = new Emitter<>(IAccount.class);

    /**
     * Fired when an account is removed.
     */
    public static final Emitter<IAccount> ON_ACCOUNT_REMOVED = new Emitter<>(IAccount.class);

    /* ------------------------------ Players ------------------------------ */

    /**
     * Fired when one of our players joins the server.
     */
    public static final Emitter<Player> ON_LOGIN = new Emitter<>(Player.class);

    /**
     * Fired when one of our players logs out of the server.
     */
    public static final Emitter<PlayerLogout> ON_LOGOUT = new Emitter<>(PlayerLogout.class);

    public static final Emitter<PlayerPacket> ON_PACKET_IN = new Emitter<>(PlayerPacket.class);
    public static final Emitter<PlayerPacket> ON_PACKET_OUT = new Emitter<>(PlayerPacket.class);

    /* ------------------------------ Evil workaround / classes ------------------------------ */

    /**
     * Fuck generics!
     */
    public static class PlayerLogout extends Pair<Player, String> {

        public PlayerLogout(Player player, String reason) {
            super(player, reason);
        }

        public Player getPlayer() {
            return getFirst();
        }

        public String getReason() {
            return getSecond();
        }
    }

    public static class PlayerPacket extends Pair<Player, Packet> {

        public PlayerPacket(Player player, Packet packet) {
            super(player, packet);
        }

        public Player getPlayer() {
            return getFirst();
        }

        public Packet getPacket() {
            return getSecond();
        }
    }
}
