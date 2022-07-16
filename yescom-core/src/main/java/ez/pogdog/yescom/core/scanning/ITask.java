package ez.pogdog.yescom.core.scanning;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.loaded.IsLoadedQuery;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A task is a scanner that has a finite lifespan.
 */
public interface ITask extends IScanner {

    // TODO: Repeat for, repeat until loaded, max repeats, etc...

    /* ------------------------------ Default overrides ------------------------------ */

    @Override
    default Dimension[] getApplicableDimensions() { // Doesn't really apply in this case
        return new Dimension[] { Dimension.OVERWORLD, Dimension.NETHER, Dimension.END };
    }

    @Override
    default boolean apply(Server server) {
        return apply(server, Dimension.OVERWORLD, Collections.emptyMap());
    }

    @Override
    default ChunkPosition getCurrentPosition(Dimension dimension) {
        return getCurrentPosition();
    }

    @Override
    default String getIdentifier() { // Tasks don't have options, they only have parameters
        return null;
    }

    @Override
    default IConfig getParent() {
        return null;
    }

    /* ------------------------------ Task operations ------------------------------ */

    /**
     * @return Information about the {@link Parameter}s that the task accepts as input.
     */
    Parameter<?>[] getParameters();

    /**
     * Attempts to apply the task to the given {@link Server}.
     * @param server The server to apply the task to.
     * @param dimension The dimension to apply the task in.
     * @param parameters The parameters to start this task with.
     * @return Was the task successfully applied?
     */
    boolean apply(Server server, Dimension dimension, Map<String, Object> parameters);

    /**
     * Cancels this task.
     */
    void cancel();

    /* ------------------------------ Generalised task "options" ------------------------------ */

    /**
     * @return The current target. If they are offline, the task will pause until they are online.
     */
    PlayerInfo getTarget();

    /**
     * Sets the current target. If they are offline, the task will pause until they are online.
     * @param target The target.
     */
    void setTarget(PlayerInfo target);

    /**
     * Sets the current target. If they are offline, the task will pause until they are online.
     * @param uuid The target's {@link UUID}.
     */
    default void setTarget(UUID uuid) {
        setTarget(YesCom.getInstance().playersHandler.getInfo(uuid));
    }

    /**
     * Removes the current target.
     */
    default void removeTarget() {
        setTarget((PlayerInfo)null);
    }

     // /**
     //  * @return Should the task stop on a loaded chunk?
     //  */
     // boolean getStopOnLoaded();

    // TODO: Redo recent tasks too

     // /**
     //  * Sets whether the task should stop when it finds a loaded chunk.
     //  */
     // void setStopOnLoaded(boolean stopOnLoaded);

    // /**
    //  * @return How this task will repeat, if at all, once finished.
    //  */
    // RepeatMode getRepeatMode();

    // /**
    //  * Sets how this task will repeat.
    //  * @param repeatMode An enum representing how the task will repeat.
    //  */
    // void setRepeatMode(RepeatMode repeatMode);

    // /**
    //  * @return Should the task repeat if a loaded chunk was found?
    //  */
    // boolean getRepeatOnLoaded();

    // /**
    //  * Sets whether the task should repeat if it finds a loaded chunk.
    //  */
    // void setRepeatOnLoaded(boolean repeatOnLoaded);

    // int getMaxRepeats();

    // void setMaxRepeats(int maxRepeats);

    // long getRepeatUntil();

    // void setRepeatUntil();

    /* ------------------------------ Setters and getters ------------------------------ */

    /**
     * @return Information about the current parameters and their values.
     */
    ParameterValue<?>[] getParameterValues();

    /**
     * @return The time that this task was started at, in milliseconds.
     */
    long getStartTime();

    /**
     * @return The current {@link Dimension} that this task is operating in.
     */
    Dimension getDimension();

    /**
     * @return The current position of the task.
     */
    ChunkPosition getCurrentPosition();

    /**
     * @return The current progress of the task, as a percentage.
     */
    float getProgress();

    /**
     * @return Is this task finished?
     */
    boolean isFinished();

    /* ------------------------------ Classes ------------------------------ */

    /**
     * The manner in which this task repeats.
     */
    enum RepeatMode {
        FOREVER(task -> new Parameter<?>[] {}, task -> true), 
        COUNT(
                task -> new Parameter<?>[] { 
                        new Parameter<>("Repeat count", "How many times to repeat the task.", Integer.class, server -> 10)
                }, 
                task -> true // TODO: Keep track of how many times a task has repeated?
        ), 
        UNTIL(
                task -> new Parameter<?>[] { // TODO: Specific date rather than long?
                        new Parameter<>("Repeat end", "The timestamp to stop the task repeating.", Long.class, server -> -1L)
                }, 
                task -> {
                    for (ParameterValue<?> parameterValue : task.getParameterValues()) {
                        if ("Repeat end".equals(parameterValue.parameter.name)) 
                            return System.currentTimeMillis() >= (Long)parameterValue.value;
                    }
                    return false; // Something weird has happened here, so just don't repeat
                }
        ), 
        NONE(task -> new Parameter<?>[] {}, task -> false);

        public final Function<ITask, Parameter<?>[]> parametersProvider;
        public final Function<ITask, Boolean> repeatCondition;

        RepeatMode(Function<ITask, Parameter<?>[]> parametersProvider, Function<ITask, Boolean> repeatCondition) {
            this.parametersProvider = parametersProvider;
            this.repeatCondition = repeatCondition;
        }
    }

    /**
     * Information about a parameter that the task takes.
     */
    final class Parameter<T> {

        public final String name;
        public final String description;

        public final Class<T> clazz;
        public final Function<Server, T> defaultValue;

        public Parameter(String name, String description, Class<T> clazz, Function<Server, T> defaultValue) {
            this.name = name;
            this.description = description;
            this.clazz = clazz;
            this.defaultValue = defaultValue;
        }

        /**
         * Gets the value of this parameter from a given {@link Map} of names to values.
         * @param parameters The parameters that were passed into the task.
         * @return The value of the parameter, or the default if it was not found.
         */
        @SuppressWarnings("unchecked")
        public T from(Server server, Map<String, Object> parameters) {
            return (T)parameters.getOrDefault(name, defaultValue.apply(server));
        }
    }

    /**
     * Information about an initialised {@link Parameter}.
     */
    final class ParameterValue<T> {

        public final Parameter<T> parameter;
        public final T value;

        public ParameterValue(Parameter<T> parameter, T value) {
            this.parameter = parameter;
            this.value = value;
        }
    }
}
