package ez.pogdog.yescom.core.scanning;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.connection.Server;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages {@link ITask}s and {@link IScanner}s for servers.
 */
public class TaskHandler {

    private final Logger logger = Logging.getLogger("yescom.core.scanning");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Public API ------------------------------ */

    /**
     * @return A list of all known {@link ITask}s.
     */
    public List<ITask> getTasks() {
        return ServiceLoader.load(ITask.class).stream().map(ServiceLoader.Provider::get).collect(Collectors.toList());
    }

    /**
     * Gets a new instance of a {@link ITask} given the name of the task.
     * @param name The name of the task.
     * @return The task instance, null if not found.
     */
    public ITask getTask(String name) {
        for (ITask task : ServiceLoader.load(ITask.class)) {
            if (name.equalsIgnoreCase(task.getName())) return task;
        }

        return null;
    }

    /**
     * Gets a new instance of a {@link ITask} and applies the task with the provided parameters.
     * @param name The name of the task.
     * @param server The server to apply the task on.
     * @param dimension The dimension to apply the task in.
     * @param parameters The parameters to initialise the task with.
     * @return The task instance, null if the task was not found or it could not be applied.
     */
    public ITask getAndApplyTask(String name, Server server, Dimension dimension, Map<String, Object> parameters) {
        ITask task = getTask(name);
        if (task == null || !task.apply(server, dimension, parameters)) return null;
        return task;
    }
}
