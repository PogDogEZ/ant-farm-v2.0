package ez.pogdog.yescom.api.data.player;

import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.api.data.player.death.Kill;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Stores information about a player.
 */
public final class PlayerInfo implements Cloneable {

    public final List<ServerInfo> servers = new ArrayList<>();
    public final Set<Session> sessions = new HashSet<>();
    public final Set<Death> deaths = new HashSet<>();
    public final Set<Kill> kills = new HashSet<>();

    public final int lookupID;
    public final UUID uuid;
    public final long firstSeen;

    public String username = "";
    public String skinURL = "";
    public int ping = 0;
    public GameMode gameMode = GameMode.SURVIVAL;

    public PlayerInfo(int lookupID, UUID uuid, long firstSeen, String username) {
        this.lookupID = lookupID;
        this.uuid = uuid;
        this.firstSeen = firstSeen;
        this.username = username;
    }

    public PlayerInfo(int lookupID, UUID uuid, long firstSeen) {
        this.lookupID = lookupID;
        this.uuid = uuid;
        this.firstSeen = firstSeen;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PlayerInfo that = (PlayerInfo)other;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid); // Everything else is subject to change
    }

    @Override
    public String toString() {
        return String.format("Player(uuid=%s, firstSeen=%s, username=%s)", uuid,
                new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date(firstSeen)), username);
    }

    @Override
    public PlayerInfo clone() {
        try {
            PlayerInfo clone = (PlayerInfo)super.clone();
            clone.username = username;
            clone.skinURL = skinURL;
            return clone;
        } catch (CloneNotSupportedException error) {
            throw new IllegalStateException(error);
        }
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Player game mode.
     */
    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR;
    }

    public static class ServerInfo {

        public final String hostname;
        public final int port;

        public ServerInfo(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            ServerInfo server = (ServerInfo)other;
            return port == server.port && hostname.equals(server.hostname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, port);
        }

        @Override
        public String toString() {
            return String.format("Server(hostname=%s, port=%d)", hostname, port);
        }
    }
}
