package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Handles the processing of {@link InvalidMoveQuery}s.
 */
public class InvalidMoveHandle implements IQueryHandle<InvalidMoveQuery>, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");

    /**
     * Valid storages / containers that can be used. There are more, I can't be bothered to add them :p.
     */
    public final Map<Integer, String> VALID_BLOCK_STORAGES = new HashMap<>();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Double> QUERIES_PER_TICK = new Option<>(
            "Queries per tick",
            "The number of queries to dispatch, per tick, per player.",
            1.0
    );

    // public final Option<Integer> PLACE_BLOCK_LIMIT = new Option<>(
    //         "Place block limit",
    //         "The Paper place block limit, in milliseconds (300 on constantiam.net).",
    //         300
    // );

    public final Option<Boolean> USE_ENTITY_STORAGES = new Option<>(
            "Use entity storages",
            "Allows YesCom to use certain entities as valid storages.",
            true
    );

    public final Option<Boolean> ARZI_MODE = new Option<>(
            "ARZI mode",
            "Opens the storage before every query. Can be very useful for higher pings.",
            true
    );
    public final Option<Boolean> SWING_ARM = new Option<>(
            "Swing arm",
            "Swing the player's arm when opening the storage?",
            false
    );

    public final Option<Boolean> DIGGING_RESYNC = new Option<>(
            "Digging resync",
            "Packet loss detection and resynchronization based on digging packet responses.",
            true
    );
    public final Option<Integer> MAX_DIGGING_DISTANCE = new Option<>(
            "Max digging distance",
            "The maximum distance from the player that digging packets are still processed.",
            64
    );

    public final Option<Boolean> WID_RESYNC = new Option<>(
            "WID resync",
            "Packet loss detection and resynchronization based on window ID prediction.",
            true
    );

    /* ------------------------------ Other fields ------------------------------ */

    public final Map<Player, PlayerHandle> available = new ConcurrentHashMap<>();

    public final Queue<InvalidMoveQuery> waiting = new PriorityQueue<>();
    public final Queue<InvalidMoveQuery> rescheduled = new ArrayDeque<>();
    public final Set<InvalidMoveQuery> cancelled = new HashSet<>();

    // Record these separately
    public final Map<InvalidMoveQuery, Consumer<InvalidMoveQuery>> callbacks = new HashMap<>();

    public final Server server;
    private final Dimension dimension;

    private float maxThroughput;
    private float effectiveQPT = 0.0f;
    private float actualQPT = 0.0f;

    public InvalidMoveHandle(Server server, Dimension dimension) {
        this.server = server;
        this.dimension = dimension;

        logger.fine(String.format("New invalid move handle for server %s:%d.", server.hostname, server.port));

        YesCom.getInstance().configHandler.addConfiguration(this);

        logger.finer("Setting up storages...");
        setupStorages();

        for (Player player : this.server.getPlayers()) available.put(player, new PlayerHandle(this, player));
        logger.finer(String.format("%d player(s) available.", available.size()));

        logger.finer("Connecting emitters...");
        Emitters.ON_PLAYER_LOGIN.connect(this::onLogin);
        Emitters.ON_PLAYER_LOGOUT.connect(this::onLogout);
    }

    @Override
    public void tick() {
        maxThroughput = 0.0f;

        int waitingBefore = waiting.size() + rescheduled.size();
        int totalFinalised = 0;

        for (PlayerHandle handle : available.values()) {
            totalFinalised += handle.getFinalisedThisTick(); // Gets reset on the tick method, so need to do this here
            handle.tick();
        }

        for (PlayerHandle handle : available.values()) {
            if (handle.canQuery() /* && !waiting.isEmpty() */) {
                synchronized (this) {
                    // Handle the rescheduled queries before we starting removing from the actual waiting queue
                    while (!rescheduled.isEmpty()) {
                        InvalidMoveQuery query = rescheduled.peek();
                        if (handle.canHandle(query)) {
                            handle.dispatch(query);
                            rescheduled.poll();
                        } else {
                            break;
                        }
                    }

                    while (!waiting.isEmpty()) {
                        InvalidMoveQuery query = waiting.peek();
                        if (query.isExpired()) { // If the query is expired, don't handle it
                            waiting.poll();
                            continue;
                        }

                        if (handle.canHandle(query)) {
                            handle.dispatch(query);
                            waiting.poll();
                        } else {
                            break;
                        }
                    }
                }
            }


            handle.tick();
            if (handle.canQuery()) maxThroughput += QUERIES_PER_TICK.value;
        }

        effectiveQPT = effectiveQPT * 0.95f + Math.max(0, waitingBefore - (waiting.size() + rescheduled.size())) * 0.05f;
        actualQPT = actualQPT * 0.95f + totalFinalised * 0.05f;
    }

    @Override
    public boolean handles(IQuery<?> query) {
        // Note: we can still get queries assigned to us with different dimensions, so we also need to check for that :p
        return (query instanceof InvalidMoveQuery && query.getDimension(this) == dimension &&
                server.INVALID_MOVE_ENABLED.value);
    }

    @Override
    public void dispatch(InvalidMoveQuery query, Consumer<InvalidMoveQuery> callback) {
        if (query == null) return;
        logger.finest("Dispatching query: " + query);
        synchronized (this) {
            if (callback != null) callbacks.put(query, callback);
            waiting.add(query);
        }
    }

    @Override
    public void cancel(InvalidMoveQuery query) {
        if (callbacks.containsKey(query)) { // Is this query actually ours?
            synchronized (this) {
                callbacks.remove(query);
                if (waiting.contains(query)) { // It's waiting to be processed so we don't need to worry about telling players to cancel it
                    waiting.remove(query);
                } else {
                    cancelled.add(query);
                }
            }
        }
    }

    @Override
    public Dimension getDimension() {
        return dimension;
    }

    @Override
    public float getMaxThroughputFor(int ahead) {
        return maxThroughput * ahead;
    }

    @Override
    public float getThroughputFor(int ahead) {
        float throughput = 0.0f;
        // for (PlayerHandle handle : available.values()) throughput += handle.getThroughputFor(ahead);
        return Math.max(0.0f, throughput - waiting.size());
    }

    @Override
    public float getEffectiveQPS() {
        return effectiveQPT * 20.0f;
    }

    @Override
    public float getActualQPS() {
        return actualQPT * 20.0f;
    }

    @Override
    public int getWaitingSize() {
        return waiting.size();
    }

    @Override
    public int getProcessingSize() {
        return callbacks.size() - waiting.size(); // Lol, hack
    }

    @Override
    public String getIdentifier() {
        return String.format("invalid-move-handle-%s", dimension.name().toLowerCase());
    }

    @Override
    public IConfig getParent() {
        return server;
    }

    /**
     * Reschedules the given {@link InvalidMoveQuery}s. This means that they will be dispatched again with the
     * highest priority possible.
     */
    public void reschedule(List<InvalidMoveQuery> queries) {
        rescheduled.addAll(queries);
    }

    /**
     * Reschedules the given {@link InvalidMoveQuery}. This means that it will be dispatched again with the highest
     * priority possible.
     */
    public void reschedule(InvalidMoveQuery query) {
        rescheduled.add(query);
    }

    private void setupStorages() {
        VALID_BLOCK_STORAGES.put(54, "minecraft:chest");
        VALID_BLOCK_STORAGES.put(146, "minecraft:trapped_chest");
        VALID_BLOCK_STORAGES.put(61, "minecraft:furnace");
        VALID_BLOCK_STORAGES.put(62, "minecraft:lit_furnace");
        VALID_BLOCK_STORAGES.put(145, "minecraft:anvil");
        VALID_BLOCK_STORAGES.put(130, "minecraft:ender_chest");
        VALID_BLOCK_STORAGES.put(23, "minecraft:dispenser");
        VALID_BLOCK_STORAGES.put(158, "minecraft:dropper");
        VALID_BLOCK_STORAGES.put(154, "minecraft:hopper");
        VALID_BLOCK_STORAGES.put(137, "minecraft:command_block"); // I mean, am I wrong?
        VALID_BLOCK_STORAGES.put(210, "minecraft:repeating_command_block");
        VALID_BLOCK_STORAGES.put(211, "minecraft:chain_command_block");
        VALID_BLOCK_STORAGES.put(116, "minecraft:enchanting_table");
        VALID_BLOCK_STORAGES.put(117, "minecraft:brewing_stand");
        VALID_BLOCK_STORAGES.put(138, "minecraft:beacon");
        VALID_BLOCK_STORAGES.put(219, "minecraft:white_shulker_box");
        VALID_BLOCK_STORAGES.put(220, "minecraft:orange_shulker_box");
        VALID_BLOCK_STORAGES.put(221, "minecraft:magenta_shulker_box");
        VALID_BLOCK_STORAGES.put(222, "minecraft:light_blue_shulker_box");
        VALID_BLOCK_STORAGES.put(223, "minecraft:yellow_shulker_box");
        VALID_BLOCK_STORAGES.put(224, "minecraft:lime_shulker_box");
        VALID_BLOCK_STORAGES.put(225, "minecraft:pink_shulker_box");
        VALID_BLOCK_STORAGES.put(226, "minecraft:gray_shulker_box");
        VALID_BLOCK_STORAGES.put(227, "minecraft:silver_shulker_box");
        VALID_BLOCK_STORAGES.put(228, "minecraft:cyan_shulker_box");
        VALID_BLOCK_STORAGES.put(229, "minecraft:purple_shulker_box");
        VALID_BLOCK_STORAGES.put(230, "minecraft:blue_shulker_box");
        VALID_BLOCK_STORAGES.put(231, "minecraft:brown_shulker_box");
        VALID_BLOCK_STORAGES.put(232, "minecraft:green_shulker_box");
        VALID_BLOCK_STORAGES.put(233, "minecraft:red_shulker_box");
        VALID_BLOCK_STORAGES.put(234, "minecraft:black_shulker_box");
    }

    /* ------------------------------ Events ------------------------------ */

    private void onLogin(Player player) {
        if (!available.containsKey(player)) available.put(player, new PlayerHandle(this, player));
    }

    private void onLogout(Emitters.PlayerLogout playerLogout) {
        if (available.containsKey(playerLogout.player)) {
            PlayerHandle playerHandle = available.get(playerLogout.player);
            available.remove(playerLogout.player);
            playerHandle.logout();
        }
    }
}
