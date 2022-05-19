package ez.pogdog.yescom.core.query.invalidmove;

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
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
import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Angle;
import ez.pogdog.yescom.api.data.BlockPosition;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.IsLoadedQuery;
import ez.pogdog.yescom.core.report.invalidmove.NoStorageReport;
import ez.pogdog.yescom.core.report.invalidmove.PacketLossReport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Handles the processing of invalid move queries.
 */
public class InvalidMoveHandle implements IQueryHandle<InvalidMoveQuery>, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");

    /**
     * Valid storages / containers that can be used.
     * 23 - dispenser.
     * 54 - chest.
     * 130 - ender chest.
     * 154 - hopper.
     * 158 - dropper.
     */
    public final List<Integer> VALID_STORAGES = Arrays.asList(23, 54, 130, 154, 158);

    /* ------------------------------ Options ------------------------------ */

    public final Option<Double> LOADED_QUERIES_PER_TICK = new Option<>(
            "Loaded queries per tick",
            "The number of queries (that are expected to be loaded) to dispatch, per tick, per player.",
            1.0
    );
    public final Option<Double> UNLOADED_QUERIES_PER_TICK = new Option<>(
            "Unloaded queries per tick",
            "The number of queries (that are expected to be unloaded) to dispatch, per tick, per player.",
            2.0
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

    private final Map<Player, PlayerHandle> available = new HashMap<>();

    private final Queue<InvalidMoveQuery> waiting = new PriorityQueue<>();
    private final Set<InvalidMoveQuery> cancelled = new HashSet<>();

    // Record these separately
    private final Map<InvalidMoveQuery, Consumer<InvalidMoveQuery>> callbacks = new HashMap<>();

    public final Server server;

    private float queriesPerTick = 0.0f;

    public InvalidMoveHandle(Server server) {
        logger.fine(String.format("New invalid move handle for server %s:%d.", server.hostname, server.port));
        this.server = server;
        YesCom.getInstance().configHandler.addConfiguration(this);

        for (Player player : this.server.getPlayers()) available.put(player, new PlayerHandle(player));
        logger.finer(String.format("%d player(s) available.", available.size()));

        logger.finer("Connecting emitters...");
        Emitters.ON_PLAYER_LOGIN.connect(this::onLogin);
        Emitters.ON_PLAYER_PACKET_IN.connect(this::onPacketIn);
        Emitters.ON_PLAYER_LOGOUT.connect(this::onLogout);
    }

    @Override
    public void tick() {
        int sizeBefore = waiting.size();
        synchronized (this) {
            outer: while (!waiting.isEmpty()) {
                InvalidMoveQuery query = waiting.peek();
                for (PlayerHandle handle : available.values()) {
                    if (handle.canQuery() && handle.canHandle(query)) {
                        handle.dispatch(query);
                        waiting.remove();
                        continue outer;
                    }
                }
                break; // No available players to handle this query
            }
        }
        for (PlayerHandle playerHandle : available.values()) playerHandle.tick();

        queriesPerTick = queriesPerTick * 0.95f + Math.max(0, sizeBefore - waiting.size()) * 0.05f;
    }

    @Override
    public boolean handles(IQuery<?> query) {
        return query instanceof InvalidMoveQuery && server.INVALID_MOVE_ENABLED.value;
    }

    @Override
    public void dispatch(InvalidMoveQuery query, Consumer<InvalidMoveQuery> callback) {
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
    public float getQPS() {
        return queriesPerTick * 20.0f;
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
        return "invalid-move-handle";
    }

    @Override
    public IConfig getParent() {
        return server;
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

        // Store loaded and unloaded separately because we can treat them differently
        private final Map<Integer, InvalidMoveQuery> loadedQueryMap = new HashMap<>();
        private final Map<Integer, InvalidMoveQuery> unloadedQueryMap = new HashMap<>();

        private final Map<Integer, Integer> windowToTPIDMap = new HashMap<>();

        private final Player player;

        private BlockPosition bestStorage = null;
        private Angle requiredAngle;

        private boolean resync = true; // When we join, we aren't synced
        private boolean storageOpen = false; // For non-ARZI mode, we need to know if the storage is open
        private boolean openingStorage;
        private int estimatedTeleportID;
        private int estimatedWindowID;

        private int ticksSinceTeleport;

        private IsLoadedQuery.State previousState = IsLoadedQuery.State.LOADED; // Assume loaded to force resync

        private int packetsElapsed = 0;
        private int ticksElapsed = 0;
        private int openWindowID = -1;
        private boolean gotBlockState = false;
        private int closeWindowID = -1;

        public PlayerHandle(Player player) {
            logger.finer(String.format("Registered new invalid move handle for player %s.", player.getUsername()));
            this.player = player;
        }

        /**
         * Ticks this player handle. In the tick, multiple things are handled:
         *  - Finding the best storage to use.
         *  - Dequeueing and processing queries.
         */
        private void tick() {
            float minExpectedTicks = (40.0f + Math.max(1.0f, player.getServerPing()) / 50.0f) * (20.0f / Math.max(1.0f, player.getServerTPS()));
            if (++ticksSinceTeleport > minExpectedTicks && (!loadedQueryMap.isEmpty() || !unloadedQueryMap.isEmpty())) {
                logger.warning(String.format("%s packet loss (5) ticks: %d, queued: %d.", player.getUsername(),
                        ticksSinceTeleport, loadedQueryMap.size() + unloadedQueryMap.size()));
                Emitters.ON_REPORT.emit(new PacketLossReport(player, 6));

                synchronized (this) {
                    for (InvalidMoveQuery query : loadedQueryMap.values()) InvalidMoveHandle.this.dispatch(query, null);
                    for (InvalidMoveQuery query : unloadedQueryMap.values()) InvalidMoveHandle.this.dispatch(query, null);

                    loadedQueryMap.clear();
                    unloadedQueryMap.clear();
                }
            }
            if (++ticksElapsed == Integer.MAX_VALUE) {
                logger.warning(String.format("%s ticks elapsed overflow.", player.getUsername()));
                ticksElapsed = 0;
            }

            if (player.isSpawned()) {
                if (bestStorage == null && !confirmedStorages.isEmpty()) {
                    synchronized (confirmedStorages) {
                        for (BlockPosition storage : new ArrayList<>(confirmedStorages)) {
                            if (player.getPosition().getDistance(storage) > 5) {
                                confirmedStorages.remove(storage);
                                continue;
                            }

                            bestStorage = storage;
                            ez.pogdog.yescom.api.data.Position storageCenter = bestStorage.toPositionCenter();
                            ez.pogdog.yescom.api.data.Position positionDiff = storageCenter.subtract(player.getPosition().add(0, 1.8, 0));

                            double diffXZ = Math.sqrt(positionDiff.getX() * positionDiff.getX() + positionDiff.getZ() * positionDiff.getZ());

                            requiredAngle = new Angle((float)Math.toDegrees(Math.atan2(positionDiff.getZ(), positionDiff.getX())) - 90.0f,
                                    (float)(-Math.toDegrees(Math.atan2(positionDiff.getY(), diffXZ))));

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
            logger.finer(String.format("Unregistered invalid move handle for player %s.", player.getUsername()));

            logger.finer(String.format("Rescheduling %d queries.", loadedQueryMap.size() + unloadedQueryMap.size()));
            for (InvalidMoveQuery query : loadedQueryMap.values()) InvalidMoveHandle.this.dispatch(query, null);
            for (InvalidMoveQuery query : unloadedQueryMap.values()) InvalidMoveHandle.this.dispatch(query, null);

            synchronized (confirmedStorages) {
                confirmedStorages.clear(); // Free memory early
            }
            synchronized (this) {
                loadedQueryMap.clear();
                unloadedQueryMap.clear();
                windowToTPIDMap.clear();
            }
        }

        /**
         * Handle storage updates.
         */
        private void handleStorage(Packet packet) {
            if (packet instanceof ServerChunkDataPacket) { // Find storages when we receive chunk data packets
                ServerChunkDataPacket chunkData = (ServerChunkDataPacket)packet;

                synchronized (confirmedStorages) {
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
                }

            } else if (packet instanceof ServerBlockChangePacket) {
                ServerBlockChangePacket blockChange = (ServerBlockChangePacket)packet;
                BlockPosition position = new BlockPosition(
                        blockChange.getRecord().getPosition().getX(),
                        blockChange.getRecord().getPosition().getY(),
                        blockChange.getRecord().getPosition().getZ()
                );
                boolean valid = VALID_STORAGES.contains(blockChange.getRecord().getBlock().getId());
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
                        if (VALID_STORAGES.contains(record.getBlock().getId())) {
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
                resync = estimatedWindowID != player.getCurrentWindowID();

                ticksSinceTeleport = 0;
            }
        }

        /**
         * Handle query state.
         */
        private void handleState(Packet packet) {
            if (packet instanceof ServerOpenWindowPacket) {
                ServerOpenWindowPacket openWindow = (ServerOpenWindowPacket)packet;

                if (ARZI_MODE.value) {
                    if (WID_RESYNC.value) {
                        if (openWindowID >= 0 || gotBlockState || closeWindowID >= 0) { // We've been waiting for another teleport
                            logger.warning(String.format("%s packet loss (2) open: %d, new: %d, packets: %d.",
                                    player.getUsername(), openWindowID, openWindow.getWindowId(), packetsElapsed));
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

                synchronized (this) {
                    // System.out.println(packet);
                    // System.out.println(loadedQueryMap);
                    // System.out.println(windowToTPIDMap);

                    InvalidMoveQuery query = loadedQueryMap.getOrDefault(positionRotation.getTeleportId(),
                            unloadedQueryMap.get(positionRotation.getTeleportId()));

                    if (ARZI_MODE.value && WID_RESYNC.value) {
                        if (openWindowID < 0) { // Did we not manage to open the storage before this query?
                            // We weren't able to open the storage for some reason, meaning that we can't accurately
                            // determine the state of the chunk, UNLESS the previous query was unloaded, as the storage
                            // will still be open, we still need to re-align the teleport ID -> window ID mappings though
                            if (previousState == IsLoadedQuery.State.UNLOADED) {
                                loadedQueryMap.remove(positionRotation.getTeleportId());
                                unloadedQueryMap.remove(positionRotation.getTeleportId());

                                finalise(query, closeWindowID > 0 && packetsElapsed <= 3);
                            }

                            // So this could mean that we either didn't make a query, or we reached the Paper packet in limit
                            if (!gotBlockState) {
                                if (query != null) { // Did we make a query?
                                    logger.warning(String.format("%s packet loss (3) tp: %d, close: %d, prev: %s.",
                                            player.getUsername(), positionRotation.getTeleportId(), closeWindowID,
                                            previousState));
                                    Emitters.ON_REPORT.emit(new PacketLossReport(player, 3));
                                } else {
                                    logger.finest(String.format("%s got teleport (ID %d) after %d packets, resync: %s.",
                                            player.getUsername(), positionRotation.getTeleportId(), packetsElapsed, resync));
                                }

                            } else {
                                logger.warning(String.format("%s packet loss (4) tp: %d, close: %d, packets: %d, prev: %s.",
                                        player.getUsername(), positionRotation.getTeleportId(), closeWindowID, packetsElapsed,
                                        previousState));
                                Emitters.ON_REPORT.emit(new PacketLossReport(player, 4));
                            }

                            // If we didn't manage to open the storage we should expect that the estimated window ID
                            // we've been using is 1 bigger than it should be
                            --estimatedWindowID;
                            if (estimatedWindowID < 0) estimatedWindowID += 100;
                            estimatedWindowID %= 100;
                            Map<Integer, Integer> newMappings = new HashMap<>();
                            for (Map.Entry<Integer, Integer> entry : windowToTPIDMap.entrySet()) {
                                int windowID = entry.getKey() - 1;
                                if (windowID < 0) windowID += 100;
                                newMappings.put(windowID % 100, entry.getValue());
                            }
                            windowToTPIDMap.clear();
                            windowToTPIDMap.putAll(newMappings);

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
                            logger.severe(String.format("Misalignment detected for %s, tp: %d, open: %d, expected tp: %d, expected open: %d.",
                                    player.getUsername(), positionRotation.getTeleportId(), openWindowID,
                                    windowToTPIDMap.getOrDefault(openWindowID, -1), expectedWindowID));
                        }

                        loadedQueryMap.remove(positionRotation.getTeleportId());
                        unloadedQueryMap.remove(positionRotation.getTeleportId());

                        finalise(query, closeWindowID > 0 && packetsElapsed <= 3);

                    } else { // Basic teleport ID -> query mapping
                        loadedQueryMap.remove(positionRotation.getTeleportId());
                        unloadedQueryMap.remove(positionRotation.getTeleportId());

                        // 3 packets of leeway cos we can also get the sound packet, and other storages may have weird
                        // things that idk about
                        if (closeWindowID > 0 && packetsElapsed <= 3) {
                            // No need to re-open if we're opening it for each query
                            if (!ARZI_MODE.value) {
                                // Window was closed, chunk is loaded, re-open the storage and reschedule all following queries
                                logger.finest(String.format("%s got loaded chunk, rescheduling %d queries.",
                                        player.getUsername(), loadedQueryMap.size() + unloadedQueryMap.size()));

                                storageOpen = false;
                                openingStorage = true; // We're attempting to open the storage again
                                openStorage();

                                for (InvalidMoveQuery query0 : loadedQueryMap.values())
                                    InvalidMoveHandle.this.dispatch(query0, null);
                                for (InvalidMoveQuery query0 : unloadedQueryMap.values())
                                    InvalidMoveHandle.this.dispatch(query0, null);
                                loadedQueryMap.clear();
                                unloadedQueryMap.clear();
                            }

                            finalise(query, true);

                        } else if (query != null) { // Chunk is unloaded
                            finalise(query, false);

                        } else { // Random teleport
                            logger.finer(String.format("%s got random teleport (ID %d), rescheduling %d queries.",
                                    player.getUsername(), positionRotation.getTeleportId(),
                                    loadedQueryMap.size() + unloadedQueryMap.size()));
                            player.send(new ClientTeleportConfirmPacket(positionRotation.getTeleportId()));

                            for (InvalidMoveQuery query0 : loadedQueryMap.values())
                                InvalidMoveHandle.this.dispatch(query0, null);
                            for (InvalidMoveQuery query0 : unloadedQueryMap.values())
                                InvalidMoveHandle.this.dispatch(query0, null);
                            loadedQueryMap.clear();
                            unloadedQueryMap.clear();

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
        }

        /**
         * Updates and query state related stuff.
         */
        private void updateState() {
            if (openWindowID >= 0 && packetsElapsed > 5 && !gotBlockState) {
                // Extraneous open window packet, this isn't going to be associated with any of our queries
                // Note: account for up to 5 packets as we can receive a sound packet (of the storage opening) and
                // we'll also wait for the teleport packet

                logger.finest(String.format("%s got extraneous open window packet after %d packets.", player.getUsername(),
                        packetsElapsed));

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

                if ((packetsElapsed > 20 && ticksElapsed > 100) || packetsElapsed > 200) { // TODO: Make configurable
                    // We've been waiting for a teleport packet for too long, this isn't going to be associated with any
                    // of our queries
                    logger.warning(String.format("%s packet loss (1) open: %d, close: %d, packets: %d, ticks: %d, tickrate: %.1f",
                            player.getUsername(), openWindowID, closeWindowID, packetsElapsed, ticksElapsed, player.getServerTPS()));
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
                    new com.github.steveice10.mc.protocol.data.game.entity.metadata.Position(bestStorage.getX(),
                            bestStorage.getY(), bestStorage.getZ()),
                    BlockFace.UP,
                    Hand.MAIN_HAND,
                    0.0f, 0.0f, 0.0f
            ));
            if (SWING_ARM.value) player.send(new ClientPlayerSwingArmPacket(Hand.MAIN_HAND));
            estimatedWindowID = estimatedWindowID % 100 + 1;
        }

        private void finalise(InvalidMoveQuery query, boolean loaded) {
            IsLoadedQuery.State state = loaded ? IsLoadedQuery.State.LOADED : IsLoadedQuery.State.UNLOADED;
            previousState = state;
            if (query == null) return;

            Consumer<InvalidMoveQuery> callback = callbacks.get(query);
            callbacks.remove(query);

            if (!cancelled.contains(query)) {
                query.setState(state);
                if (callback != null) callback.accept(query);
            }
            cancelled.remove(query);
        }

        /* ------------------------------ Public API ------------------------------ */

        /**
         * @return Can this player actually handle the provided query?
         */
        public boolean canHandle(InvalidMoveQuery query) {
            if (query.dimension != player.getDimension()) return false; // Can't handle queries in other dimensions
            // FIXME: Use estimated ping instead, it's more accurate
            // How many ticks should we expect the server to respond in?
            float expectedTicks = Math.max(1.0f, player.getServerPing()) / 50.0f;

            // FIXME: This needs to be split across multiple ticks as otherwise it'll send a ton of packets at first, and then even out
            if (expectedTicks * Math.max(LOADED_QUERIES_PER_TICK.value, UNLOADED_QUERIES_PER_TICK.value) <
                    loadedQueryMap.size() + unloadedQueryMap.size())
                return false; // Too many queries in flight

            if (query.expected == IsLoadedQuery.State.LOADED) {
                return expectedTicks * LOADED_QUERIES_PER_TICK.value > loadedQueryMap.size();
            } else {
                return expectedTicks * UNLOADED_QUERIES_PER_TICK.value > unloadedQueryMap.size();
            }
        }

        /**
         * @return Can this player dispatch queries?
         */
        public boolean canQuery() {
            // Do we have a storage, have we spawned in and has the storage been opened?
            return bestStorage != null && player.isSpawned() && player.getCurrentWindowID() >= 0 && !resync &&
                    (storageOpen || ARZI_MODE.value);
        }

        /**
         * Dispatch the provided query with this player.
         */
        public void dispatch(InvalidMoveQuery query) {
            // Not sure why this would happen, but better safe than sorry
            if (bestStorage == null || query.dimension != player.getDimension()) {
                InvalidMoveHandle.this.dispatch(query, null);
                return;
            }

            logger.finest(String.format("%s is dispatching query: %s.", player.getUsername(), query));

            BlockPosition position = query.position.getPosition(8, 5000, 8);

            if (loadedQueryMap.isEmpty() && unloadedQueryMap.isEmpty()) ticksSinceTeleport = 0;
            synchronized (this) {
                if (query.expected == IsLoadedQuery.State.LOADED) {
                    loadedQueryMap.put(++estimatedTeleportID, query);
                } else {
                    unloadedQueryMap.put(++estimatedTeleportID, query);
                }
            }

            if (ARZI_MODE.value) openStorage(); // TODO: Open storage if not in ARZI mode elsewhere
            if (WID_RESYNC.value) {
                // We'll estimate the mapping of windowID -> teleportID even if we're expecting unloaded, cos we can
                // still perform resync checks on unloaded results
                synchronized (this) {
                    windowToTPIDMap.put(estimatedWindowID, estimatedTeleportID);
                }
            }

            // Sending this after we've opened the storage (with ARZI mode) means that we can guarantee something will
            // have gone wrong if we do not receive the open storage packet before the response
            player.send(new ClientPlayerPositionPacket(false, position.getX(), position.getY(), position.getZ()));
            player.send(new ClientTeleportConfirmPacket(estimatedTeleportID));
        }
    }
}
