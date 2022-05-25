package ez.pogdog.yescom.api.data.player;

import ez.pogdog.yescom.api.Globals;

import java.util.Date;
import java.util.Objects;

/**
 * A session represents a time in which a player was online at.
 */
public class Session {

    public final PlayerInfo.Server server;
    public final long start;
    public final long end;

    public Session(PlayerInfo.Server server, long start, long end) {
        this.server = server;
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Session session = (Session)other;
        return start == session.start && end == session.end && server.equals(session.server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server, start, end);
    }

    @Override
    public String toString() {
        return String.format("Session(server=%s, start=%s, end=%s)", server, Globals.DATE_FORMAT.format(new Date(start)),
                Globals.DATE_FORMAT.format(new Date(end)));
    }

    /**
     * @return The time played in this session, in milliseconds.
     */
    public int getPlayTime() {
        return (int)(end - start);
    }
}
