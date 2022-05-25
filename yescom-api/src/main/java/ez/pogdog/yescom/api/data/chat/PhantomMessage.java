package ez.pogdog.yescom.api.data.chat;

import java.util.UUID;

/**
 * A message sent by Phantom, damn.
 */
public class PhantomMessage extends ChatMessage {

    public PhantomMessage(long timestamp, UUID receiver, String message) {
        super(timestamp, receiver, message);
    }

    @Override
    public Type getType() {
        return Type.PHANTOM;
    }
}
