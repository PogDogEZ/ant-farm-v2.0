package ez.pogdog.yescom.core.servers.behaviours;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.servers.IServerBehaviour;

import java.util.Collections;
import java.util.Set;

/**
 * Default (Paper) server behaviour.
 */
public class DefaultBehaviour implements IServerBehaviour {

    private Server server;

    @Override
    public boolean isValid(Server server) {
        return true; // Default behaviour is always valid
    }

    @Override
    public void apply(Server server) {
        if (this.server != null) throw new IllegalStateException("Attempted to apply server behaviour when already applied.");
        this.server = server;
    }

    @Override
    public Set<Class<? extends IServerBehaviour>> getOverrides() {
        return Collections.emptySet(); // Doesn't override anything
    }

    @Override
    public void tick() {
    }

    @Override
    public void processJoin(PlayerInfo info) {
        server.handleConnect(info);
    }

    @Override
    public ChatMessage parseChatMessage(Player player, ServerChatPacket packet) {
        return null; // TODO: Default chat message parsing
    }

    @Override
    public void processLeave(PlayerInfo info) {
        server.handleDisconnect(info);
    }
}
