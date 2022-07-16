package ez.pogdog.yescom.core.servers;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.tracking.Highway;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;

import java.util.HashSet;
import java.util.Set;

/**
 * How certain servers behave due to plugins, etc.
 */
public interface IServerBehaviour extends ITickable {

    /**
     * @return Does this behaviour apply for the given server?
     */
    boolean isValid(Server server);

    /**
     * Applies this behaviour to the given server.
     * @param server The server.
     */
    void apply(Server server);

    /**
     * @return The behaviours that this behaviour overrides.
     */
    Set<Class<? extends IServerBehaviour>> getOverrides();

    /**
     * @return A list of {@link Highway}s for this server.
     */
    Set<Highway> getHighways(); // TODO: Loadable from a file?

    /**
     * Ticks this behaviour.
     */
    void tick();

    /**
     * Processes when a {@link PlayerInfo} joins the server.
     * @param info The player that joined.
     */
    void processJoin(PlayerInfo info);

    /**
     * Parses a chat message. This can be unique to different servers.
     * @param player The player that received the message.
     * @param packet The chat message packet.
     * @return The parsed {@link ChatMessage}, null if it couldn't be parsed.
     */
    ChatMessage parseChatMessage(Player player, ServerChatPacket packet);

    /**
     * Processes when a {@link PlayerInfo} leaves the server.
     * @param info The player that left.
     */
    void processLeave(PlayerInfo info);
}
