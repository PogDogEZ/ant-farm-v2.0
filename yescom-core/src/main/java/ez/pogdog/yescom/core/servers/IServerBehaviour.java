package ez.pogdog.yescom.core.servers;

import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.connection.Server;

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
     * Ticks this behaviour.
     */
    void tick();

    /**
     * @return The behaviours that this behaviour overrides.
     */
    Set<Class<? extends IServerBehaviour>> getOverrides();
}
