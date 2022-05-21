package ez.pogdog.yescom.core.servers.behaviours;

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
    public void tick() {
    }

    @Override
    public Set<Class<? extends IServerBehaviour>> getOverrides() {
        return Collections.emptySet(); // Doesn't override anything
    }
}
