package ez.pogdog.yescom.core;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.api.event.Emitter;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
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

    /* ------------------------------ YesCom accounts ------------------------------ */

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

    /* ------------------------------ YesCom players ------------------------------ */

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
     * Fired when one of the players logs out of the server.
     */
    public static final Emitter<PlayerLogout> ON_PLAYER_LOGOUT = new Emitter<>(PlayerLogout.class);

    /**
     * Fired when one of the players receives a chat message.
     */
    public static final Emitter<PlayerChat> ON_PLAYER_CHAT = new Emitter<>(PlayerChat.class);

    public static final Emitter<PlayerPacket> ON_PLAYER_PACKET_IN = new Emitter<>(PlayerPacket.class);
    public static final Emitter<PlayerPacket> ON_PLAYER_PACKET_OUT = new Emitter<>(PlayerPacket.class);

    /**
     * Fired when a player is removed.
     */
    public static final Emitter<Player> ON_PLAYER_REMOVED = new Emitter<>(Player.class);

    /* ------------------------------ All players ------------------------------ */

    /**
     * Fired when any player is added to the player cache.
     */
    public static final Emitter<PlayerInfo> ON_NEW_PLAYER_CACHED = new Emitter<>(PlayerInfo.class);

    /**
     * Fired when a player is just trusted / untrusted.
     */
    public static final Emitter<PlayerInfo> ON_TRUST_STATE_CHANGED = new Emitter<>(PlayerInfo.class);

    /**
     * Fired when any player joins the server.
     */
    public static final Emitter<OnlinePlayerInfo> ON_ANY_PLAYER_JOIN = new Emitter<>(OnlinePlayerInfo.class);

    /**
     * Fired when any player's gamemode changes.
     */
    public static final Emitter<OnlinePlayerInfo> ON_ANY_PLAYER_GAMEMODE_UPDATE = new Emitter<>(OnlinePlayerInfo.class);

    /**
     * Fired when any player's ping changes.
     */
    public static final Emitter<OnlinePlayerInfo> ON_ANY_PLAYER_PING_UPDATE = new Emitter<>(OnlinePlayerInfo.class);

    /**
     * Fired when any player dies.
     */
    public static final Emitter<OnlinePlayerDeath> ON_ANY_PLAYER_DEATH = new Emitter<>(OnlinePlayerDeath.class);

    /**
     * Fired when any player leaves the server.
     */
    public static final Emitter<OnlinePlayerInfo> ON_ANY_PLAYER_LEAVE = new Emitter<>(OnlinePlayerInfo.class);

    /* ------------------------------ Server ------------------------------ */

    /**
     * Fired when a new server is added.
     */
    public static final Emitter<Server> ON_SERVER_ADDED = new Emitter<>(Server.class);

    /**
     * Fired when we've just connected to a server.
     */
    public static final Emitter<Server> ON_CONNECTION_ESTABLISHED = new Emitter<>(Server.class);

    /**
     * Fired when connection is fully lost to a server.
     */
    public static final Emitter<Server> ON_CONNECTION_LOST = new Emitter<>(Server.class);

    /**
     * Fired when a chunk state has been resolved for a given server.
     */
    public static final Emitter<ServerChunkState> ON_CHUNK_STATE = new Emitter<>(ServerChunkState.class);

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

    public static class PlayerChat {

        public final Server server;
        public final ChatMessage chatMessage;

        public PlayerChat(Server server, ChatMessage chatMessage) {
            this.server = server;
            this.chatMessage = chatMessage;
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

    public static class OnlinePlayerInfo {

        public final PlayerInfo info;
        public final Server server;

        public OnlinePlayerInfo(PlayerInfo info, Server server) {
            this.info = info;
            this.server = server;
        }
    }

    public static class OnlinePlayerDeath extends OnlinePlayerInfo {

        public final Death death;

        public OnlinePlayerDeath(PlayerInfo info, Server server, Death death) {
            super(info, server);

            this.death = death;
        }
    }

    public static class ServerChunkState {

        public final Server server;
        public final ChunkState state;

        public ServerChunkState(Server server, ChunkState state) {
            this.server = server;
            this.state = state;
        }
    }
}
