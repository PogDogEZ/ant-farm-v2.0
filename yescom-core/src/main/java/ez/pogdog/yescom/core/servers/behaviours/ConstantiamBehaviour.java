package ez.pogdog.yescom.core.servers.behaviours;

import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.servers.IServerBehaviour;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Behaviour for the server constantiam.net.
 */
public class ConstantiamBehaviour implements IServerBehaviour {

    private /* final */ Server server;

    @Override
    public boolean isValid(Server server) {
        return (server.hostname.equalsIgnoreCase("constantiam.net") ||
                server.hostname.equalsIgnoreCase("constantiam.org") ||
                server.hostname.equalsIgnoreCase("constantiam.co.uk") ||
                server.hostname.equals("95.217.83.105")) && server.port == 25565;
    }

    @Override
    public void apply(Server server) {
        if (this.server != null) throw new IllegalStateException("Attempted to apply server behaviour when already applied.");
        this.server = server;
    }

    @Override
    public void tick() {
    }

    @Override
    public Set<Class<? extends IServerBehaviour>> getOverrides() {
        return Collections.singleton(DefaultBehaviour.class);
    }
}
