package ez.pogdog.yescom.core.query.invalidmove;

import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPlaceBlockPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerMultiBlockChangePacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Angle;
import ez.pogdog.yescom.api.data.BlockPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.report.invalidmove.NoStorageReport;
import ez.pogdog.yescom.core.report.invalidmove.PacketLossReport;

import java.util.ArrayList;
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
 * Handles the processing of invalid move queries.
 */
public class InvalidMoveHandle implements IQueryHandle<InvalidMoveQuery>, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");

    /**
     * Valid storages / containers that can be used. There are more, I can't be bothered to add them :p.
     */
    public final Map<Integer, String> VALID_STORAGES = new HashMap<>(); // TODO: Make this an option I guess

    /* ------------------------------ Options ------------------------------ */

    public final Option<Double> QUERIES_PER_TICK = new Option<>(
            "Queries per tick",
            "The number of queries to dispatch, per tick, per player.",
            1.0
    );

    public final Option<Boolean> ARZI_MODE = new Option<>(
            "ARZI mode",
            "Opens the storage before every query. Can be very useful for higher pings.",
            true
    );
    public final Option<Boolean> SWING_ARM = new Option<>(
            "Swing arm",
            "Swing the player's arm when opening the storage?",
            true
    );
    public final Option<Boolean> WID_RESYNC = new Option<>(
            "WID resync",
            "Packet loss detection and resynchronization based on window ID prediction.",
            true
    );

    /* ------------------------------ Other fields ------------------------------ */

    private final Map<Player, PlayerHandle> available = new ConcurrentHashMap<>();

    private final Queue<InvalidMoveQuery> waiting = new PriorityQueue<>();
    private final Set<InvalidMoveQuery> cancelled = new HashSet<>();

    // Record these separately
    private final Map<InvalidMoveQuery, Consumer<InvalidMoveQuery>> callbacks = new HashMap<>();

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

        for (Player player : this.server.getPlayers()) available.put(player, new PlayerHandle(player));
        logger.finer(String.format("%d player(s) available.", available.size()));

        logger.finer("Connecting emitters...");
        Emitters.ON_PLAYER_LOGIN.connect(this::onLogin);
        Emitters.ON_PLAYER_PACKET_IN.connect(this::onPacketIn);
        Emitters.ON_PLAYER_LOGOUT.connect(this::onLogout);
    }

    @Override
    public void tick() {
        maxThroughput = 0.0f;

        int waitingBefore = waiting.size();
        int totalFinalised = 0;

        for (PlayerHandle handle : available.values()) {
            totalFinalised += handle.getFinalisedThisTick();
            if (handle.canQuery() /* && !waiting.isEmpty() */) {
                synchronized (this) {
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

        effectiveQPT = effectiveQPT * 0.95f + Math.max(0, waitingBefore - waiting.size()) * 0.05f;
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
        for (PlayerHandle handle : available.values()) throughput += handle.getThroughputFor(ahead);
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

    private void setupStorages() {
        VALID_STORAGES.put(54, "minecraft:chest");
        VALID_STORAGES.put(146, "minecraft:trapped_chest");
        VALID_STORAGES.put(61, "minecraft:furnace");
        VALID_STORAGES.put(62, "minecraft:lit_furnace");
        VALID_STORAGES.put(145, "minecraft:anvil");
        VALID_STORAGES.put(130, "minecraft:ender_chest");
        VALID_STORAGES.put(23, "minecraft:dispenser");
        VALID_STORAGES.put(158, "minecraft:dropper");
        VALID_STORAGES.put(154, "minecraft:hopper");
        VALID_STORAGES.put(137, "minecraft:command_block"); // I mean, am I wrong?
        VALID_STORAGES.put(210, "minecraft:repeating_command_block");
        VALID_STORAGES.put(211, "minecraft:chain_command_block");
        VALID_STORAGES.put(116, "minecraft:enchanting_table");
        VALID_STORAGES.put(117, "minecraft:brewing_stand");
        VALID_STORAGES.put(138, "minecraft:beacon");
        VALID_STORAGES.put(219, "minecraft:white_shulker_box");
        VALID_STORAGES.put(220, "minecraft:orange_shulker_box");
        VALID_STORAGES.put(221, "minecraft:magenta_shulker_box");
        VALID_STORAGES.put(222, "minecraft:light_blue_shulker_box");
        VALID_STORAGES.put(223, "minecraft:yellow_shulker_box");
        VALID_STORAGES.put(224, "minecraft:lime_shulker_box");
        VALID_STORAGES.put(225, "minecraft:pink_shulker_box");
        VALID_STORAGES.put(226, "minecraft:gray_shulker_box");
        VALID_STORAGES.put(227, "minecraft:silver_shulker_box");
        VALID_STORAGES.put(228, "minecraft:cyan_shulker_box");
        VALID_STORAGES.put(229, "minecraft:purple_shulker_box");
        VALID_STORAGES.put(230, "minecraft:blue_shulker_box");
        VALID_STORAGES.put(231, "minecraft:brown_shulker_box");
        VALID_STORAGES.put(232, "minecraft:green_shulker_box");
        VALID_STORAGES.put(233, "minecraft:red_shulker_box");
        VALID_STORAGES.put(234, "minecraft:black_shulker_box");
    }

    /* ------------------------------ Events ------------------------------ */

    private void onLogin(Player player) {
        if (!available.containsKey(player)) available.put(player, new PlayerHandle(player));
    }

    private void onPacketIn(Emitters.PlayerPacket playerPacket) {
        if (available.containsKey(playerPacket.player))
            available.get(playerPacket.player).onPacketIn(playerPacket.packet);
    }

    private void onLogout(Emitters.PlayerLogout playerLogout) {
        if (available.containsKey(playerLogout.player)) {
            PlayerHandle playerHandle = available.get(playerLogout.player);
            available.remove(playerLogout.player);
            playerHandle.onLogout();
        }
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Handles information about individual players.
     */
    public class PlayerHandle {

        private final Set<BlockPosition> confirmedStorages = new HashSet<>();

        private final Map<Integer, InvalidMoveQuery> queryMap = new HashMap<>();
        private final Map<Integer, Long> timingsMap = new HashMap<>();
        private final Map<Integer, Integer> windowToTPIDMap = new HashMap<>();

        private final Player player;

        private BlockPosition bestStorage = null;
        private Angle requiredAngle;

        private int dispatchedThisTick = 0;
        private int finalisedThisTick = 0;

        private boolean resync = true; // When we join, we aren't synced
        private boolean storageOpen = false; // For non-ARZI mode, we need to know if the storage is open
        private boolean openingStorage;
        private int estimatedTeleportID = 1;
        private int estimatedWindowID = 1;

        private float averageResponseTime;
        private int ticksSinceTeleport;

        private ChunkState.State previousState = ChunkState.State.LOADED; // Assume loaded to force resync

        private int packetsElapsed = 0;
        private int ticksElapsed = 0;
        private int openWindowID = -1;
        private boolean gotBlockState = false;
        private int closeWindowID = -1;

        public PlayerHandle(Player player) {
            logger.finer(String.format(
                    "Registered new invalid move handle for player %s (dimension %s).",
                    player.getUsername(), dimension
            ));
            this.player = player;
        }

        /**
         * Ticks this player handle. In the tick, multiple things are handled:
         *  - Finding the best storage to use.
         *  - Dequeueing and processing queries.
         */
        private void tick() {
            dispatchedThisTick = 0;
            finalisedThisTick = 0;

            // Expect response within <our ping in ticks> + 40 more ticks for safety
            float minExpectedTicks = 40.0f + Math.max(50.0f, averageResponseTime) / 50.0f * (20.0f / Math.max(1.0f, player.getServerTPS()));
            if (player.getTSLP() < minExpectedTicks * 50.0f) { // Server lagging?
                if (++ticksSinceTeleport > minExpectedTicks && !queryMap.isEmpty()) {
                    logger.warning(String.format(
                            "%s packet loss (5) ticks=%d, queued=%d, avg_dt=%.1fms.",
                            player.getUsername(), ticksSinceTeleport, queryMap.size(), averageResponseTime
                    ));
                    Emitters.ON_REPORT.emit(new PacketLossReport(player, 5));

                    List<InvalidMoveQuery> queries;
                    synchronized (this) {
                        queries = new ArrayList<>(queryMap.values());
                        queryMap.clear();
                    }
                    for (InvalidMoveQuery query1 : queries)
                        InvalidMoveHandle.this.dispatch(query1, null);
                }
            }
            if (++ticksElapsed == Integer.MAX_VALUE) {
                logger.warning(String.format("%s ticks elapsed overflow.", player.getUsername()));
                ticksElapsed = 0;
            }

            if (player.isSpawned()) {
                // At first will have incorrect players assigned to this handle, so we need to check their dimension
                // when they eventually spawn in
                if (player.getDimension() != dimension) {
                    onLogout();
                    return;
                }

                if (bestStorage == null && !confirmedStorages.isEmpty()) {
                    synchronized (confirmedStorages) {
                        for (BlockPosition storage : new ArrayList<>(confirmedStorages)) {
                            if (player.getPosition().getDistance(storage) > 5) {
                                confirmedStorages.remove(storage);
                                continue;
                            }

                            bestStorage = storage;
                            ez.pogdog.yescom.api.data.Position storageCenter = bestStorage.toPositionCenter();
                            ez.pogdog.yescom.api.data.Position positionDiff = storageCenter.subtract(player.getPosition().add(0, 1.53, 0));

                            double diffXZ = Math.sqrt(positionDiff.getX() * positionDiff.getX() + positionDiff.getZ() * positionDiff.getZ());

                            requiredAngle = new Angle(
                                    (float)Math.toDegrees(Math.atan2(positionDiff.getZ(), positionDiff.getX())) - 90.0f,
                                    (float)(-Math.toDegrees(Math.atan2(positionDiff.getY(), diffXZ)))
                            );

                            logger.finer(String.format("Found best storage for %s: %s.", player.getUsername(), bestStorage));
                            logger.finest(String.format("%s required angle: %s.", player.getUsername(), requiredAngle));
                            break;
                        }
                    }

                    if (bestStorage == null) { // FIXME: Doesn't log if there are no storages at all
                        logger.warning(String.format("%s (%s:%d) cannot find a valid storage.", player.getUsername(),
                                server.hostname, server.port));
                        Emitters.ON_REPORT.emit(new NoStorageReport(player));
                    }
                }

                // Face the storage if we aren't already
                if (bestStorage != null) {
                    if (!player.getAngle().equals(requiredAngle)) player.setAngle(requiredAngle);

                    if (!ARZI_MODE.value && !storageOpen && !openingStorage) {
                        openingStorage = true;
                        openStorage();
                    }
                }

                // TODO: Ticking stuff
            }
        }

        /**
         * Packet in event for this player.
         * @param packet The packet.
         */
        private void onPacketIn(Packet packet) {
            if (!(packet instanceof ServerChatPacket)) ++packetsElapsed; // FIXME: Can Paper be configured to not use async chat?

            handleStorage(packet);
            handleResync(packet);
            handleState(packet);
            updateState();

            // if (openWindowID >= 0) System.out.println(packet);
        }

        /**
         * When this player logs out, we need to handle it correctly.
         */
        private void onLogout() {
            available.remove(player);
            logger.finer(String.format(
                    "Unregistered invalid move handle for player %s (dimension %s).",
                    player.getUsername(), dimension
            ));

            List<InvalidMoveQuery> queries;
            synchronized (this) {
                queries = new ArrayList<>(queryMap.values());
                queryMap.clear();
                windowToTPIDMap.clear();
            }
            logger.finer(String.format("Rescheduling %d queries.", queries.size()));
            for (InvalidMoveQuery query1 : queries)
                InvalidMoveHandle.this.dispatch(query1, null);

            synchronized (confirmedStorages) {
                confirmedStorages.clear(); // Free memory early
            }
        }

        /**
         * Handle storage updates.
         */
        private void handleStorage(Packet packet) {
            if (packet instanceof ServerChunkDataPacket) { // Find storages when we receive chunk data packets
                ServerChunkDataPacket chunkData = (ServerChunkDataPacket)packet;

                synchronized (confirmedStorages) {
                    Set<BlockPosition> inChunk = new HashSet<>();
                    // FIXME: Works with non-full chunks?
                    for (BlockPosition storage : confirmedStorages) {
                        if (storage.getX() >> 4 == chunkData.getX() && storage.getZ() >> 4 == chunkData.getZ())
                            inChunk.add(storage);
                    }
                    confirmedStorages.removeAll(inChunk);

                    for (CompoundTag tileEntity : chunkData.getTileEntities()) {
                        Map<String, Tag> values = tileEntity.getValue();
                        if (VALID_STORAGES.containsValue((String)values.getOrDefault("id", new StringTag("secret:message:WTF!!")).getValue())) {
                            if (values.containsKey("x") && values.containsKey("y") && values.containsKey("z")) {
                                BlockPosition blockPosition = new BlockPosition(
                                        (int)values.get("x").getValue(),
                                        (int)values.get("y").getValue(),
                                        (int)values.get("z").getValue()
                                );
                                confirmedStorages.add(blockPosition);
                            }
                        }
                    }

                    // Has it been replaced by another block?
                    if (bestStorage != null && bestStorage.getX() >> 4 == chunkData.getX() &&
                            bestStorage.getZ() >> 4 == chunkData.getZ() && !confirmedStorages.contains(bestStorage))
                        bestStorage = null;

                    // TODO: Fallback to checking for stuff like crafting tables?
                    /*
                    for (int index = 0; index < chunkData.getColumn().getChunks().length; ++index) {
                        Chunk chunk = chunkData.getColumn().getChunks()[index];
                        if (chunk == null || chunk.isEmpty()) continue;
                        for (int x = 0; x < 16; ++x) {
                            for (int y = 0; y < 16; ++y) {
                                for (int z = 0; z < 16; ++z) {
                                    BlockState blockState = chunk.getBlocks().get(x, y, z);
                                    BlockPosition position = new BlockPosition(
                                            x + chunkData.getColumn().getX() * 16,
                                            y + index * 16,
                                            z + chunkData.getColumn().getZ() * 16
                                    );

                                    if (bestStorage != null && bestStorage.equals(position)) bestStorage = null;
                                    confirmedStorages.remove(position); // Clear all previous storages in this chunk, they may have changed
                                    if (VALID_STORAGES.contains(blockState.getId())) {
                                        logger.finest("Found storage: " + position);
                                        confirmedStorages.add(position);
                                    }
                                }
                            }
                        }
                    }
                     */
                }

            } else if (packet instanceof ServerBlockChangePacket) {
                ServerBlockChangePacket blockChange = (ServerBlockChangePacket)packet;
                BlockPosition position = new BlockPosition(
                        blockChange.getRecord().getPosition().getX(),
                        blockChange.getRecord().getPosition().getY(),
                        blockChange.getRecord().getPosition().getZ()
                );
                boolean valid = VALID_STORAGES.containsKey(blockChange.getRecord().getBlock().getId());
                boolean isCurrent = position.equals(bestStorage);

                synchronized (confirmedStorages) {
                    confirmedStorages.remove(position);

                    if (isCurrent && !valid) { // When we open the storage, we get this packet sent to us
                        bestStorage = null;
                    } else if (!isCurrent && valid) {
                        logger.finest("Found storage: " + position);
                        confirmedStorages.add(position);
                    }
                }

            } else if (packet instanceof ServerMultiBlockChangePacket) {
                ServerMultiBlockChangePacket multiBlockChange = (ServerMultiBlockChangePacket)packet;

                synchronized (confirmedStorages) {
                    for (BlockChangeRecord record : multiBlockChange.getRecords()) {
                        BlockPosition position = new BlockPosition(
                                record.getPosition().getX(),
                                record.getPosition().getY(),
                                record.getPosition().getZ()
                        );

                        if (bestStorage != null && bestStorage.equals(position)) bestStorage = null;
                        confirmedStorages.remove(position);
                        if (VALID_STORAGES.containsKey(record.getBlock().getId())) {
                            logger.finest("Found storage: " + position);
                            confirmedStorages.add(position);
                        }
                    }
                }
            }
        }

        /**
         * Handle resync.
         */
        private void handleResync(Packet packet) {
            if (!resync) return;

            if (packet instanceof ServerOpenWindowPacket) {
                estimatedWindowID = ((ServerOpenWindowPacket)packet).getWindowId();
                resync = estimatedTeleportID != player.getCurrentTeleportID();

            } else if (packet instanceof ServerWindowItemsPacket) {
                ServerWindowItemsPacket windowItems = (ServerWindowItemsPacket)packet;

                if (windowItems.getWindowId() >= 0 && windowItems.getWindowId() <= 100) {
                    estimatedWindowID = windowItems.getWindowId();
                    resync = estimatedTeleportID != player.getCurrentTeleportID();
                }

            } else if (packet instanceof ServerWindowPropertyPacket) {
                ServerWindowPropertyPacket windowProperty = (ServerWindowPropertyPacket)packet;

                if (windowProperty.getWindowId() >= 0 && windowProperty.getWindowId() <= 100) {
                    estimatedWindowID = windowProperty.getWindowId();
                    resync = estimatedTeleportID != player.getCurrentTeleportID();
                }

            } else if (packet instanceof ServerCloseWindowPacket) {
                estimatedWindowID = ((ServerCloseWindowPacket)packet).getWindowId();
                resync = estimatedTeleportID != player.getCurrentTeleportID();

            } else if (packet instanceof ServerPlayerPositionRotationPacket) {
                estimatedTeleportID = ((ServerPlayerPositionRotationPacket)packet).getTeleportId();
                // We may not have actually received a window ID yet
                resync = player.getCurrentWindowID() >= 0 && estimatedWindowID != player.getCurrentWindowID();
            }
        }

        /**
         * Handle query state.
         */
        private void handleState(Packet packet) {
            // Understanding how the loaded chunk detection works (for dispatching explanation, see below):
            //  - Server receives the place block packet:
            //    > Sends an open window packet if successful
            //    > Sends a block update packet regardless
            //  - Server receives invalid move into the chunk:
            //    > If unloaded, an internal teleport occurs to our old position, no teleport event is fired
            //    > If loaded, a teleport event is fired and NCP closes our window, we receive a close window packet
            //    > We receive a teleport packet regardless
            //  - Server receives teleport confirm packet
            // Assuming this all goes to plan, we should have a correct mapping of window ID -> teleport ID, and further
            // teleport ID -> query.
            // Ways in which this can go wrong:
            //  - Anti-timer plugin stops our movement packet:
            //    > Detected when we receive an open window packet with no following teleport packet
            //    > The fix is to re-align the teleport ID to window ID mappings and reschedule the dropped query
            //  - Anti-cheat stops our place block packet:
            //    > Detected when we receive a block change packet with no prior open window packet
            //    > If the previous chunk was unloaded, the storage will still be open, so the detection will have gone
            //      through correctly.
            //    > If the previous chunk was loaded, we can't be sure of the state of it.
            //    > Re-align the teleport ID to window ID mappings, reschedule if required.
            //  - Paper's packet in limit stops our place block packet (1.33333 on constantiam.net):
            //    > Detected when we receive a teleport packet with no prior block change packet
            //    > Same as the point above

            if (packet instanceof ServerOpenWindowPacket) {
                ServerOpenWindowPacket openWindow = (ServerOpenWindowPacket)packet;

                if (ARZI_MODE.value) {
                    if (WID_RESYNC.value) {
                        if (openWindowID >= 0 || gotBlockState || closeWindowID >= 0) { // We've been waiting for another teleport
                            logger.warning(String.format(
                                    "%s packet loss (2) open=%d, new=%d, packets=%d.",
                                    player.getUsername(), openWindowID, openWindow.getWindowId(), packetsElapsed
                            ));
                            Emitters.ON_REPORT.emit(new PacketLossReport(player, 2));

                            // TODO: Resync
                        }

                        // Expect to get a ServerWindowItemsPacket immediately after and a ServerSetSlotPacket with ID 255
                        packetsElapsed = -2;
                        // We'll record the elapsed ticks from the first indication that we're listening to a query response
                        // (this packet)
                        openWindowID = openWindow.getWindowId();
                    }

                    // Recorded regardless of WID_RESYNC as it can still be used to measure server statistics
                    ticksElapsed = 0;

                } else {
                    storageOpen = true;
                    openingStorage = false; // We aren't opening it right now
                }

            } else if (packet instanceof ServerWindowPropertyPacket) {
                // We won't count this as an elapsed packet as we'll get these after the window opens
                if (openWindowID >= 0 && !gotBlockState && ((ServerWindowPropertyPacket)packet).getWindowId() == openWindowID)
                    --packetsElapsed;

            } else if (packet instanceof ServerSetSlotPacket) {
                if (openWindowID >= 0 && !gotBlockState && ((ServerSetSlotPacket)packet).getWindowId() == openWindowID)
                    --packetsElapsed;

            } else if (packet instanceof ServerBlockChangePacket) {
                ServerBlockChangePacket blockChange = (ServerBlockChangePacket)packet;

                if (openWindowID >= 0) { // TODO: Also check the packetsElapsed <= 1?
                    BlockPosition position = new BlockPosition(
                            blockChange.getRecord().getPosition().getX(),
                            blockChange.getRecord().getPosition().getY(),
                            blockChange.getRecord().getPosition().getZ()
                    );

                    if (position.equals(bestStorage)) {
                        packetsElapsed = -1; // Server sends two, one offset based on the face
                        gotBlockState = true;
                    }
                }

            } else if (packet instanceof ServerCloseWindowPacket) { // We will actually receive this before the position packet
                ServerCloseWindowPacket closeWindow = (ServerCloseWindowPacket)packet;

                // TODO: Extraneous close windows mean the storage is closed, but we don't account for that right now
                // ^ could be solved by ignoring the state when dispatching queries, and setting it purely based on packets
                // though would require a lot more fields to track :(

                packetsElapsed = 0;
                ticksElapsed = 0;
                closeWindowID = closeWindow.getWindowId();

            } else if (packet instanceof ServerPlayerPositionRotationPacket) {
                ServerPlayerPositionRotationPacket positionRotation = (ServerPlayerPositionRotationPacket)packet;

                ticksSinceTeleport = 0;

                // System.out.println(packet);
                // System.out.println(loadedQueryMap);
                // System.out.println(windowToTPIDMap);

                InvalidMoveQuery query;
                long responseTime = -1;
                synchronized (this) {
                    query = queryMap.get(positionRotation.getTeleportId());
                    if (timingsMap.containsKey(positionRotation.getTeleportId())) {
                        responseTime = System.currentTimeMillis() - timingsMap.get(positionRotation.getTeleportId());
                        averageResponseTime = averageResponseTime * 0.9f + (responseTime) * 0.1f;
                        timingsMap.remove(positionRotation.getTeleportId());
                    }
                }

                if (ARZI_MODE.value && WID_RESYNC.value) {
                    if (openWindowID < 0) { // Did we not manage to open the storage before this query?
                        synchronized (this) {
                            queryMap.remove(positionRotation.getTeleportId());
                        }
                        // We weren't able to open the storage for some reason, meaning that we can't accurately
                        // determine the state of the chunk, UNLESS the previous query was unloaded, as the storage
                        // will still be open, we still need to re-align the teleport ID -> window ID mappings though
                        if (previousState == ChunkState.State.UNLOADED) {
                            finalise(query, closeWindowID > 0 && packetsElapsed <= 3);
                        } else {
                            InvalidMoveHandle.this.dispatch(query, null);
                        }

                        // So this could mean that we either didn't make a query, or we reached the Paper packet in limit
                        if (!gotBlockState) {
                            if (query != null) { // Did we make a query?
                                if (previousState != ChunkState.State.UNLOADED) {
                                    logger.warning(String.format(
                                            "%s packet loss (3) tp=%d, close=%d, prev=%s, dt=%dms.",
                                            player.getUsername(), positionRotation.getTeleportId(), closeWindowID,
                                            previousState, responseTime
                                    ));
                                    Emitters.ON_REPORT.emit(new PacketLossReport(player, 3));
                                } else {
                                    logger.finest(String.format(
                                            "%s couldn't open the storage tp=%d, close=%d, prev=%s, dt=%dms.",
                                            player.getUsername(), positionRotation.getTeleportId(), closeWindowID,
                                            previousState, responseTime
                                    ));
                                }
                            } else {
                                logger.finest(String.format(
                                        "%s got teleport (id=%d) after %d packets, resync=%s, dt=%dms",
                                        player.getUsername(), positionRotation.getTeleportId(), packetsElapsed,
                                        resync, responseTime
                                ));
                            }

                        } else {
                            logger.warning(String.format(
                                    "%s packet loss (4) tp=%d, close=%d, packets=%d, prev=%s, dt=%dms.",
                                    player.getUsername(), positionRotation.getTeleportId(), closeWindowID, packetsElapsed,
                                    previousState, responseTime
                            ));
                            Emitters.ON_REPORT.emit(new PacketLossReport(player, 4));
                        }

                        synchronized (this) {
                            // If we didn't manage to open the storage we should expect that the estimated window ID
                            // we've been using is 1 bigger than it should be
                            if (estimatedWindowID == 1) estimatedWindowID = 101;
                            --estimatedWindowID;
                            Map<Integer, Integer> newMappings = new HashMap<>();
                            for (Map.Entry<Integer, Integer> entry : windowToTPIDMap.entrySet()) {
                                int windowID = entry.getKey();
                                if (windowID == 1) windowID = 101;
                                --windowID;
                                newMappings.put(windowID, entry.getValue());
                            }
                            windowToTPIDMap.clear();
                            windowToTPIDMap.putAll(newMappings);
                        }

                        packetsElapsed = 0;
                        ticksElapsed = 0;
                        gotBlockState = false;
                        closeWindowID = -1;
                        return;
                    }

                    // Our other checks should have taken care of everything, if this occurs, there's going to be a bug
                    if (windowToTPIDMap.getOrDefault(openWindowID, -1) != positionRotation.getTeleportId()) {
                        int expectedWindowID = -1;
                        if (windowToTPIDMap.containsValue(positionRotation.getTeleportId())) {
                            for (Map.Entry<Integer, Integer> entry : windowToTPIDMap.entrySet()) {
                                if (entry.getValue() == positionRotation.getTeleportId()) {
                                    expectedWindowID = entry.getKey();
                                    break;
                                }
                            }
                        }
                        logger.severe(String.format(
                                "Misalignment detected for %s, tp=%d, open=%d, expected_tp=%d, expected_open=%d, dt=%dms.",
                                player.getUsername(), positionRotation.getTeleportId(), openWindowID,
                                windowToTPIDMap.getOrDefault(openWindowID, -1), expectedWindowID,
                                responseTime
                        ));
                    }

                    synchronized (this) {
                        queryMap.remove(positionRotation.getTeleportId());
                    }

                    finalise(query, closeWindowID > 0 && packetsElapsed <= 3);

                } else { // Basic teleport ID -> query mapping
                    synchronized (this) {
                        queryMap.remove(positionRotation.getTeleportId());
                    }

                    // 3 packets of leeway cos we can also get the sound packet, and other storages may have weird
                    // things that idk about
                    if (closeWindowID > 0 && packetsElapsed <= 3) {
                        // No need to re-open if we're opening it for each query
                        if (!ARZI_MODE.value) {
                            // Window was closed, chunk is loaded, re-open the storage and reschedule all following queries
                            logger.finest(String.format(
                                    "%s got loaded chunk, rescheduling %d queries, dt=%dms.",
                                    player.getUsername(), queryMap.size(), responseTime
                            ));

                            storageOpen = false;
                            openingStorage = true; // We're attempting to open the storage again
                            openStorage();

                            List<InvalidMoveQuery> queries;
                            synchronized (this) {
                                queries = new ArrayList<>(queryMap.values());
                                queryMap.clear();
                            }
                            for (InvalidMoveQuery query1 : queries)
                                InvalidMoveHandle.this.dispatch(query1, null);
                        }

                        finalise(query, true);

                    } else if (query != null) { // Chunk is unloaded
                        finalise(query, false);

                    } else { // Random teleport
                        logger.finer(String.format(
                                "%s got random teleport (id=%d, dt=%dms), rescheduling %d queries.",
                                player.getUsername(), positionRotation.getTeleportId(), responseTime,
                                queryMap.size()
                        ));
                        player.send(new ClientTeleportConfirmPacket(positionRotation.getTeleportId()));

                        List<InvalidMoveQuery> queries;
                        synchronized (this) {
                            queries = new ArrayList<>(queryMap.values());
                            queryMap.clear();
                        }
                        for (InvalidMoveQuery query1 : queries) // Dispatch out of object monitor to avoid deadlock
                            InvalidMoveHandle.this.dispatch(query1, null);

                        // Need to reset everything too
                        resync = true;
                        storageOpen = false;
                        estimatedTeleportID = positionRotation.getTeleportId();
                    }
                }

                packetsElapsed = 0;
                ticksElapsed = 0;
                openWindowID = -1;
                gotBlockState = false;
                closeWindowID = -1;
            }
        }

        /**
         * Updates and query state related stuff.
         */
        private void updateState() {
            if (openWindowID >= 0 && packetsElapsed > 5 && !gotBlockState) {
                // Extraneous open window packet, this isn't going to be associated with any of our queries
                // Note: account for up to 5 packets as we can receive a sound packet (of the storage opening) and
                // we'll also wait for the teleport packet

                logger.finest(String.format(
                        "%s got extraneous open window packet after %d packets.", player.getUsername(),
                        packetsElapsed
                ));

                // TODO: Re-align mappings

                packetsElapsed = 0;
                ticksElapsed = 0;
                openWindowID = -1;
                gotBlockState = false;
                closeWindowID = -1;

            // Expect within ~4 server ticks
            } else if (openWindowID >= 0 && /* packetsElapsed > 5 && */ packetsElapsed > 2 &&
                    ticksElapsed > Math.min(20.0f, Math.max(0.0f, player.getServerTPS())) / 5.0f) {
                // logger.finest(String.format("%s"))

                // 200 packets still isn't enough as it turns out, especially since mobs build up around the accounts
                // while they're AFK.
                // TODO: Could average the number of packets we're getting and go from there?
                if ((packetsElapsed > 20 && ticksElapsed > 100) /* || packetsElapsed > 200 */) { // TODO: Make configurable
                    // We've been waiting for a teleport packet for too long, this isn't going to be associated with any
                    // of our queries
                    logger.warning(String.format(
                            "%s packet loss (1) open=%d, close=%d, packets=%d, ticks=%d, tickrate=%.1f, avg_dt=%.1fms",
                            player.getUsername(), openWindowID, closeWindowID, packetsElapsed, ticksElapsed,
                            player.getServerTPS(), averageResponseTime
                    ));
                    Emitters.ON_REPORT.emit(new PacketLossReport(player, 1));

                    // TODO: Resync

                    packetsElapsed = 0;
                    ticksElapsed = 0;
                    openWindowID = -1;
                    gotBlockState = false;
                    closeWindowID = -1;
                }
            }
        }

        /**
         * Attempts to open the storage.
         */
        private void openStorage() {
            if (bestStorage == null) return; // No storage to open

            logger.finest(String.format("%s opening storage.", player.getUsername()));

            // When we send this packet, we'll open the storage serverside. This happens in the following stages:
            //  1. Check preconditions (not active teleport, container in range, container in world, etc...)
            //  2. Call interact on the block, fires event which can be cancelled
            //  3. If event not cancelled, interact with storage block, which asks the player entity to open its inventory
            //  4. If the current container is not the same, close it
            //  5. Get next window ID, windowID = windowID % 100 + 1
            //  6. Send open container packet to the client with the new window ID
            //  7. Create container serverside
            player.send(new ClientPlayerPlaceBlockPacket(
                    new com.github.steveice10.mc.protocol.data.game.entity.metadata.Position(
                            bestStorage.getX(), bestStorage.getY(), bestStorage.getZ()
                    ),
                    BlockFace.UP,
                    Hand.MAIN_HAND,
                    0.0f, 0.0f, 0.0f
            ));
            if (SWING_ARM.value) player.send(new ClientPlayerSwingArmPacket(Hand.MAIN_HAND));
            estimatedWindowID = estimatedWindowID % 100 + 1;
        }

        private void finalise(InvalidMoveQuery query, boolean loaded) {
            ChunkState.State state = loaded ? ChunkState.State.LOADED : ChunkState.State.UNLOADED;
            previousState = state;
            if (query == null) return;

            ++finalisedThisTick;
            logger.finest(String.format("Finalised query %s, state: %s.", query, state));
            Consumer<InvalidMoveQuery> callback;
            synchronized (InvalidMoveHandle.this) {
                callback = callbacks.get(query);
                callbacks.remove(query);
            }

            if (!cancelled.contains(query)) {
                query.setState(state);
                if (callback != null) callback.accept(query); // TODO: Different thread perhaps
            }
            cancelled.remove(query);
        }

        /* ------------------------------ Public API ------------------------------ */

        /**
         * @return Can this player actually handle the provided query?
         */
        public boolean canHandle(InvalidMoveQuery query) {
            // Can't handle queries in other dimensions, might still happen (idk), better safe than sorry though
            if (query.dimension != player.getDimension()) return false;
            if (dispatchedThisTick >= Math.ceil(QUERIES_PER_TICK.value)) return false;
            // How many ticks should we expect the server to respond in?
            return Math.max(50.0f, averageResponseTime) / 50.0f * QUERIES_PER_TICK.value > queryMap.size();
        }

        /**
         * @return Can this player dispatch queries?
         */
        public boolean canQuery() {
            // Do we have a storage, have we spawned in and has the storage been opened?
            return bestStorage != null && player.isSpawned() && !resync && (storageOpen || ARZI_MODE.value);
        }

        public float getThroughputFor(int ahead) {
            if (ahead <= 0 || !canQuery()) return 0.0f;
            float expectedTicks = Math.max(50.0f, player.getServerPing()) / 50.0f;
            return (float)Math.max(0.0f, QUERIES_PER_TICK.value * ahead - queryMap.size() / expectedTicks);
        }

        /**
         * Dispatch the provided query with this player.
         */
        public void dispatch(InvalidMoveQuery query) {
            // Not sure why this would happen, but better safe than sorry
            // if (bestStorage == null || query.dimension != player.getDimension()) {
            //     InvalidMoveHandle.this.dispatch(query, null);
            //     return;
            // }

            logger.finest(String.format("%s is dispatching query: %s.", player.getUsername(), query));

            BlockPosition position = query.position.getPosition(8, 5000, 8);

            if (queryMap.isEmpty()) ticksSinceTeleport = 0;
            synchronized (this) {
                queryMap.put(++estimatedTeleportID, query);
                timingsMap.put(estimatedTeleportID, System.currentTimeMillis());
            }

            if (ARZI_MODE.value) openStorage(); // TODO: Open storage if not in ARZI mode elsewhere
            if (WID_RESYNC.value) {
                // We'll estimate the mapping of windowID -> teleportID even if we're expecting unloaded, cos we can
                // still perform resync checks on unloaded results
                synchronized (this) {
                    windowToTPIDMap.put(estimatedWindowID, estimatedTeleportID);
                }
            }

            int positionX = Math.min(29999999, Math.max(-29999999, position.getX())); // We'll get kicked otherwise
            int positionZ = Math.min(29999999, Math.max(-29999999, position.getZ()));

            // Sending this after we've opened the storage (with ARZI mode) means that we can guarantee something will
            // have gone wrong if we do not receive the open storage packet before the response
            player.send(new ClientPlayerPositionPacket(false, positionX, position.getY() + 0.000000001, positionZ));
            player.send(new ClientTeleportConfirmPacket(estimatedTeleportID));

            ++dispatchedThisTick;
        }

        public int getFinalisedThisTick() {
            return finalisedThisTick;
        }
    }
}
