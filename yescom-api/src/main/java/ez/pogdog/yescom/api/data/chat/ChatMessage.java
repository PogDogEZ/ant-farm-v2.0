package ez.pogdog.yescom.api.data.chat;

import ez.pogdog.yescom.api.Globals;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all chat message types.
 */
public abstract class ChatMessage {

    public final long timestamp;
    public final UUID receiver;
    public final String message;

    /**
     * @param timestamp The time the message was received at.
     * @param receiver The player that received the message.
     * @param message The text in the received message.
     */
    public ChatMessage(long timestamp, UUID receiver, String message) {
        this.timestamp = timestamp;
        this.receiver = receiver;
        this.message = message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        ChatMessage that = (ChatMessage)other;
        return timestamp == that.timestamp && receiver.equals(that.receiver) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, receiver, message);
    }

    @Override
    public String toString() {
        return String.format("%s(timestamp=%s, message=%s)", getClass().getSimpleName(),
                Globals.DATE_FORMAT.format(new Date(timestamp)), message);
    }

    public abstract Type getType();

    public enum Type {
        COMMAND, DEATH, JOIN_LEAVE, PARTY, PHANTOM, REGULAR, STATUS, WHISPER;
    }
}
