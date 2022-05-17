package ez.pogdog.yescom.core;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.api.event.Emitter;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.Report;

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
     * Fired when an account fails to log in the first time.
     */
    public static final Emitter<AccountError> ON_ACCOUNT_ERROR = new Emitter<>(AccountError.class);

    /**
     * Fired when an account is removed.
     */
    public static final Emitter<IAccount> ON_ACCOUNT_REMOVED = new Emitter<>(IAccount.class);

    /* ------------------------------ Players ------------------------------ */

    /**
     * Fired when a player is added.
     */
    public static final Emitter<Player> ON_PLAYER_ADDED = new Emitter<>(Player.class);

    /**
     * Fired when one of our players joins the server.
     */
    public static final Emitter<Player> ON_PLAYER_LOGIN = new Emitter<>(Player.class);

    /**
     * Fired when a player's position, angle or dimension changes.
     */
    public static final Emitter<Player> ON_PLAYER_POSITION_UPDATE = new Emitter<>(Player.class);

    /**
     * Fired when a player's health, hunger or saturation changes.
     */
    public static final Emitter<Player> ON_PLAYER_HEALTH_UPDATE = new Emitter<>(Player.class);

    /**
     * Fired when a player's estimated tickrate, ping or loaded chunks changes.
     */
    public static final Emitter<Player> ON_PLAYER_SERVER_STATS_UPDATE = new Emitter<>(Player.class);

    /**
     * Fired when one of our players logs out of the server.
     */
    public static final Emitter<PlayerLogout> ON_PLAYER_LOGOUT = new Emitter<>(PlayerLogout.class);

    public static final Emitter<PlayerPacket> ON_PLAYER_PACKET_IN = new Emitter<>(PlayerPacket.class);
    public static final Emitter<PlayerPacket> ON_PLAYER_PACKET_OUT = new Emitter<>(PlayerPacket.class);

    /**
     * Fired when a player is removed.
     */
    public static final Emitter<Player> ON_PLAYER_REMOVED = new Emitter<>(Player.class);

    /* ------------------------------ Reporting ------------------------------ */

    /**
     * Fired when a report is created.
     */
    @SuppressWarnings("rawtypes")
    public static final Emitter<Report> ON_REPORT = new Emitter<>(Report.class);

    /* ------------------------------ Evil workaround / classes ------------------------------ */

    public static class AccountError {

        public final IAccount account;
        public final RequestException error;

        public AccountError(IAccount account, RequestException error) {
            this.account = account;
            this.error = error;
        }
    }

    /**
     * Fuck generics!
     */
    public static class PlayerLogout {

        public final Player player;
        public final String reason;

        public PlayerLogout(Player player, String reason) {
            this.player = player;
            this.reason = reason;
        }
    }

    public static class PlayerPacket {

        public final Player player;
        public final Packet packet;

        public PlayerPacket(Player player, Packet packet) {
            this.player = player;
            this.packet = packet;
        }
    }
}
