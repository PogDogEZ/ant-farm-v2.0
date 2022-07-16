package ez.pogdog.yescom.core.query.invalidmove;

import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockChangeRecord;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPlaceBlockPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityDestroyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityMovementPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityTeleportPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnMobPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerMultiBlockChangePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUnloadChunkPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Angle;
import ez.pogdog.yescom.api.data.BlockPosition;
import ez.pogdog.yescom.api.data.ChunkState;
import ez.pogdog.yescom.api.data.Position;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.report.invalidmove.NoStorageReport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * Individual handles for {@link Player}s when handling {@link InvalidMoveQuery}s.
 */
public class PlayerHandle implements ITickable, Player.IPacketListener {

    private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");
    private final Random random = new Random();

    private final Map<BlockPosition, BlockStorage> blockStorages = new HashMap<>();
    private final Map<Integer, EntityStorage> entityStorages = new HashMap<>();

    private final List<InvalidMoveQuery> rescheduled = new ArrayList<>();
    private final Queue<ProcessingQuery> processing = new ArrayDeque<>(); // Queries being processed right now
    private final Queue<Integer> preConfirms = new ArrayDeque<>(); // Teleport IDs that we can confirmed ahead of time

    private final InvalidMoveHandle handle;
    private final Player player;

    private boolean spawned; // Quicker than calling player.isSpawned() a bunch

    private int dispatchedThisTick = 0;
    private float overshootDispatching = 0.0f;
    private int finalisedThisTick = 0;

    private boolean storageDirty = false;
    private IStorage currentStorage;
    private boolean justFound = false;
    private boolean storageOpen = false; // Is the storage currently open
    private long attemptOpen = -1; // Are we currently attempting to open the storage?

    private int ticksSinceTeleport = 0;
    private int ticksSinceStorageUpdate = 0;
    private int estimatedTeleportID = 1;
    private int estimatedWindowID = 1; // Window IDs start at 1 :p
    private int movementLimit = 0; // Used to indicate that we shouldn't be sending movement packets right now

    private boolean teleportDesync = true; // When we first spawn in, we should expect a teleport packet
    private boolean windowDesync = false;

    private float averageResponseTime = 0.0f;
    private int ticksProcessing = 0;
    private boolean previousLoaded = true; // FIXME: Why, again?

    private ProcessingQuery current;

    public PlayerHandle(InvalidMoveHandle handle, Player player) {
        logger.fine(String.format("New invalid move handle for %s (dimension %s).", player.getUsername(), handle.getDimension()));

        this.handle = handle;
        this.player = player;

        synchronized (player.packetListeners) {
            player.packetListeners.add(this);
        }
    }

    @Override
    public void tick() {
        if (dispatchedThisTick > 0) {
            overshootDispatching += handle.QUERIES_PER_TICK.value % 1.0f;
        } else {
            overshootDispatching = 0.0f;
        }

        dispatchedThisTick = 0;
        finalisedThisTick = 0;

        if (!spawned && player.isSpawned()) {
            spawned = true;

            // Registers for all dimensions when a player logs in, need to wait to find out what dimension the player
            // is actually in though
            if (player.getDimension() != handle.getDimension()) {
                logout();
                return;
            }
        }

        synchronized (this) {
            tickStorage();
            tickResync();
            tickQueries();

            ++ticksSinceTeleport;
            ++ticksSinceStorageUpdate;
            --movementLimit;
        }
    }

    @Override
    public /* synchronized */ void packetIn(Packet packet) {
        packetInStorage(packet);
        packetInResync(packet);
        packetInQuery(packet);

        if (packet instanceof ServerPlayerPositionRotationPacket) {
            ticksSinceTeleport = 0;
        } else if (packet instanceof ServerOpenWindowPacket || packet instanceof ServerCloseWindowPacket) {
            ticksSinceStorageUpdate = 0;
        }
    }

    @Override
    public synchronized void packetOut(Packet packet) {
        packetOutResync(packet);
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Called when the player logs out.
     */
    public void logout() {
        logger.fine(String.format("%s logout for invalid move handle (dimension %s).", player.getUsername(), handle.getDimension()));

        handle.available.remove(player);
        synchronized (player.packetListeners) {
            player.packetListeners.remove(this);
        }
    }

    /* ------------------------------ Storages ------------------------------ */

    /**
     * Ticks the storage logic.
     */
    private void tickStorage() {
        Position eyesPosition = player.getPosition().add(0, 1.53, 0);
        // No need to do anything if our current storage is valid
        if (currentStorage != null && currentStorage.isValid(eyesPosition)) {
            if (!windowDesync) { // Don't attempt to open the storage if we're desynced, that's handled elsewhere
                // Storage isn't open and not using ARZI mode (or just spawned in)
                if (!storageOpen && attemptOpen < 0 && (!handle.ARZI_MODE.value || justFound)) {
                    logger.finest(String.format("%s is attempting to open storage.", player.getUsername()));
                    attemptOpen = System.currentTimeMillis();
                    currentStorage.tryOpen();

                    // Have we guessed the window ID correctly, or are we waiting for the first confirmation?
                    // windowDesync = player.getCurrentWindowID() < 1 || estimatedWindowID == player.getCurrentWindowID();

                } else if (!storageOpen && System.currentTimeMillis() - attemptOpen > 5000) {
                     // Setting might have just been changed, so don't log to avoid clutter
                    if (!handle.ARZI_MODE.value || justFound)
                        logger.warning(String.format("%s failed to open storage (timed out).", player.getUsername()));
                    attemptOpen = -1; // Attempt again next tick

                } else if (storageOpen && justFound) {
                    justFound = false;
                }
            }
            return;

        } else {
            currentStorage = null; // Can still return early, so indicate that the current storage is not valid (as it's null)
            storageOpen = false;
            attemptOpen = -1; // Can't be attempting to open a storage that doesn't exist
        }
        if (!storageDirty) return; // No changes to the environment (concerning the storage) have occurred
        if (!player.isSpawned()) return; // Wait until we've spawned in before doing anything
        storageDirty = false;

        if (handle.USE_ENTITY_STORAGES.value && !entityStorages.isEmpty()) {
            for (EntityStorage storage : entityStorages.values()) {
                if (storage.isValid(eyesPosition)) {
                    logger.fine(String.format("Player %s is using storage %s.", player.getUsername(), storage));
                    currentStorage = storage;
                    justFound = true;
                    return;
                }
            }

        } else if (!blockStorages.isEmpty()) {
            for (BlockStorage storage : blockStorages.values()) {
                if (storage.isValid(eyesPosition)) {
                    logger.fine(String.format("Player %s is using storage %s.", player.getUsername(), storage));
                    currentStorage = storage;
                    justFound = true;
                    return;
                }
            }
        }

        logger.warning(String.format(
                "Couldn't find valid storage for %s (%s:%d)!",
                player.getUsername(), player.server.hostname, player.server.port
        ));
        Emitters.ON_REPORT.emit(new NoStorageReport(player));
    }

    /**
     * Listens for storage related packets and updates the current storages around the player.
     */
    private void packetInStorage(Packet packet) {
        // Block handling stuff
        if (packet instanceof ServerChunkDataPacket) {
            ServerChunkDataPacket chunkData = (ServerChunkDataPacket)packet;

            synchronized (this) {
                // if (chunkData.isFullChunk()) {
                for (BlockStorage storage : new ArrayList<>(blockStorages.values())) {
                    if (storage.position.getX() >> 4 == chunkData.getX() && storage.position.getZ() >> 4 == chunkData.getZ()) {
                        if (storage.equals(currentStorage)) {
                            logger.fine(String.format("%s lost current storage (%s).", player.getUsername(), currentStorage));
                            currentStorage = null;
                        }
                        blockStorages.remove(storage.position);
                        storageDirty = true;
                    }
                }

                for (CompoundTag tileEntity : chunkData.getTileEntities()) {
                    Map<String, Tag> values = tileEntity.getValue();
                    String blockID = (String)values.getOrDefault("id", new StringTag("secret:message:WTF!!")).getValue();
                    if (handle.VALID_BLOCK_STORAGES.containsValue(blockID)) {
                        if (values.containsKey("x") && values.containsKey("y") && values.containsKey("z")) {
                            BlockPosition blockPosition = new BlockPosition(
                                    (int)values.get("x").getValue(),
                                    (int)values.get("y").getValue(),
                                    (int)values.get("z").getValue()
                            );
                            BlockStorage storage = new BlockStorage(blockPosition, blockID);
                            logger.finest(String.format("%s has storage %s.", player.getUsername(), storage));

                            blockStorages.put(blockPosition, storage);
                            storageDirty = true;
                        }
                    }
                }
                // }

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

        } else if (packet instanceof ServerUnloadChunkPacket) {
            ServerUnloadChunkPacket unloadChunk = (ServerUnloadChunkPacket)packet;

            synchronized (this) {
                for (BlockStorage storage : new ArrayList<>(blockStorages.values())) {
                    if (storage.position.getX() >> 4 == unloadChunk.getX() && storage.position.getZ() >> 4 == unloadChunk.getZ()) {
                        if (storage.equals(currentStorage)) {
                            logger.fine(String.format("%s lost current storage (%s).", player.getUsername(), currentStorage));
                            currentStorage = null;
                        }
                        blockStorages.remove(storage.position);
                        storageDirty = true;
                    }
                }
            }

        } else if (packet instanceof ServerMultiBlockChangePacket) {
            ServerMultiBlockChangePacket multiBlockChange = (ServerMultiBlockChangePacket)packet;
            for (BlockChangeRecord record : multiBlockChange.getRecords()) handleRecord(record);

        } else if (packet instanceof ServerBlockChangePacket) {
            ServerBlockChangePacket blockChange = (ServerBlockChangePacket)packet;
            handleRecord(blockChange.getRecord());

        // Entity handling stuff

        } else if (packet instanceof ServerSpawnMobPacket) { // TODO: All below
            ServerSpawnMobPacket spawnMob = (ServerSpawnMobPacket)packet;

            switch (spawnMob.getType()) {
                case DONKEY:
                case MULE:
                case HORSE:
                case LLAMA: {
                    break;
                }
                case VILLAGER: {
                    break;
                }
            }

        } else if (packet instanceof ServerEntityDestroyPacket) {
            ServerEntityDestroyPacket entityDestroy = (ServerEntityDestroyPacket)packet;

            // TODO: Synchronise
            for (int entityID : entityDestroy.getEntityIds()) {
                EntityStorage storage = entityStorages.get(entityID);
                if (storage != null) {
                    if (storage.equals(currentStorage)) {
                        logger.fine(String.format("%s lost current storage (%s).", player.getUsername(), currentStorage));
                        currentStorage = null;
                    }
                    entityStorages.remove(entityID);
                    storageDirty = true;
                }
            }

        } else if (packet instanceof ServerEntityMovementPacket) {
            ServerEntityMovementPacket entityMovement = (ServerEntityMovementPacket)packet;

        } else if (packet instanceof ServerEntityTeleportPacket) {
            ServerEntityTeleportPacket entityTeleport = (ServerEntityTeleportPacket)packet;

        // Other stuff

        } else if (packet instanceof ServerOpenWindowPacket) {
            storageOpen = true;
            attemptOpen = -1;

        } else if (packet instanceof ServerCloseWindowPacket) {
            storageOpen = false;
            // We should also say that the current attempt has failed, not sure why this would happen though, as we
            // should always get the open window first
            attemptOpen = -1;
        }
    }

    private void handleRecord(BlockChangeRecord record) {
        BlockPosition position = new BlockPosition(
                record.getPosition().getX(),
                record.getPosition().getY(),
                record.getPosition().getZ()
        );

        BlockStorage storage = blockStorages.get(position);
        boolean valid = handle.VALID_BLOCK_STORAGES.containsKey(record.getBlock().getId());

        if (storage != null && !valid) {
            synchronized (this) {
                if (storage.equals(currentStorage)) {
                    logger.fine(String.format("%s lost current storage (%s).", player.getUsername(), currentStorage));
                    currentStorage = null;
                }
                blockStorages.remove(position);
                storageDirty = true;
            }

        } else if (storage == null && valid) {
            storage = new BlockStorage(position, handle.VALID_BLOCK_STORAGES.get(record.getBlock().getId()));
            logger.finest(String.format("%s has storage %s.", player.getUsername(), storage));

            synchronized (this) {
                blockStorages.put(position, storage);
                storageDirty = true;
            }
        }
    }

    /* ------------------------------ Synchronisation ------------------------------ */

    private void tickResync() {
        if (player.getTSLP() > 100) { // Are we still receiving packets?
            // --ticksSinceTeleport; // Don't really count this as a tick as the server isn't giving us any information
            // --ticksSinceStorageUpdate;
            return;
        }

        if (teleportDesync) {
            float expectedTicks = 40 + Math.max(50.0f, player.getServerPing()) / 50.0f;

            if (ticksSinceTeleport > expectedTicks && player.getCurrentTeleportID() == estimatedTeleportID) { // All as expected?
                logger.finer(String.format("%s is probably no longer TP desynced.", player.getUsername()));
                teleportDesync = false;

            } else if (ticksSinceTeleport > expectedTicks && movementLimit < 0) { // Can we send movement packets again?
                logger.finest(String.format("%s is forcing TP ID update.", player.getUsername()));

                movementLimit = 20; // Server should respond within that time, hopefully
                // Send a packet that we know will get the server to set us back, so that we can get a teleport ID from
                // it that we can use with certainty
                player.send(new ClientPlayerPositionPacket(
                        false, player.getPosition().getX(), player.getPosition().getY() + 100000, player.getPosition().getZ()
                ));
            }

        } else if (ticksSinceTeleport > 20 && processing.isEmpty() && spawned) {
            logger.finest(String.format("%s is idling.", player.getUsername()));
            // If we're idling, send movement packets anyway cos if we're tp desynced and don't know about it the server
            // will send us teleport packets every 20 ticks that we can use to resync
            ticksSinceTeleport = 0;
            player.send(new ClientPlayerPositionPacket(
                    false, player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ()
            ));
        }

        if (currentStorage != null && windowDesync) {
            float expectedTicks = 40 + Math.max(50.0f, player.getServerPing()) / 50.0f;

            // Due to ping, the data we're getting back from the server about the current storage state will be delayed,
            // and we don't want to influence the server state in some weird way, so if we are still receiving updates
            // from the server, don't do anything yet
            if (ticksSinceStorageUpdate > expectedTicks && player.getCurrentWindowID() == estimatedWindowID) {
                logger.finer(String.format("%s is probably no longer window desynced.", player.getUsername()));
                windowDesync = false;

            } else if (attemptOpen < 0 || System.currentTimeMillis() - attemptOpen > 5000) {
                logger.finer(String.format("%s is window desynced, attempting to open storage.", player.getUsername()));

                attemptOpen = System.currentTimeMillis();
                currentStorage.tryOpen();
            }
        }
    }

    /**
     * Listens for packets sent to us that could be used to determine the synchronisation state of the player.
     */
    private void packetInResync(Packet packet) {
        if (packet instanceof ServerPlayerPositionRotationPacket) {
            ServerPlayerPositionRotationPacket positionRotation = (ServerPlayerPositionRotationPacket)packet;

            // If we haven't pre-confirmed this, or we weren't expecting this to be the next teleport ID (can occur when
            // we sent excess movement packets while the server is still waiting for a teleport confirm) then handle
            // accordingly
            synchronized (this) {
                Integer expected = preConfirms.poll();
                if (expected == null || expected != positionRotation.getTeleportId()) {
                    logger.fine(String.format(
                            "%s ext teleport: e_tid=%d, tid=%d, preconf=%d, mlimit=%d.",
                            player.getUsername(), expected, positionRotation.getTeleportId(), preConfirms.size(), movementLimit
                    ));

                    preConfirms.clear(); // Our estimates will be useless now
                    estimatedTeleportID = positionRotation.getTeleportId();
                    movementLimit = 10; // Don't send any packets right now, just in case

                    // Set this as true, will be set as false later once we've confirmed everything is as it should be
                    teleportDesync = true;
                    // Confirm the teleport, there's really no harm in doing this as it doesn't change much if this was
                    // the incorrect ID
                    player.send(new ClientTeleportConfirmPacket(positionRotation.getTeleportId()));
                }
            }

        } else if (packet instanceof ServerOpenWindowPacket) {
            ServerOpenWindowPacket openWindow = (ServerOpenWindowPacket)packet;
            if (windowDesync) estimatedWindowID = openWindow.getWindowId(); // TODO: How does window desync even occur?

        } else if (packet instanceof ServerWindowItemsPacket) {
            ServerWindowItemsPacket windowItems = (ServerWindowItemsPacket)packet;
            if (windowDesync && windowItems.getWindowId() >= 1 && windowItems.getWindowId() <= 100)
                estimatedWindowID = windowItems.getWindowId();

        } else if (packet instanceof ServerWindowPropertyPacket) {
            ServerWindowPropertyPacket windowProperty = (ServerWindowPropertyPacket)packet;
            if (windowDesync && windowProperty.getWindowId() >= 1 && windowProperty.getWindowId() <= 100)
                estimatedWindowID = windowProperty.getWindowId();

        } else if (packet instanceof ServerCloseWindowPacket) {
            ServerCloseWindowPacket closeWindow = (ServerCloseWindowPacket)packet;
            if (windowDesync) estimatedWindowID = closeWindow.getWindowId();
        }
    }

    /**
     * Listens for packets we're sending that could be used to determine the synchronisation state of the player.
     */
    private void packetOutResync(Packet packet) {
        if (packet instanceof ClientTeleportConfirmPacket) {
            ClientTeleportConfirmPacket teleportConfirm = (ClientTeleportConfirmPacket)packet;
            // Not pre-confirmed if we have received the teleport packet from the server already
            if (!teleportDesync && teleportConfirm.getTeleportId() > player.getCurrentTeleportID()) {
                synchronized (this) {
                    preConfirms.add(teleportConfirm.getTeleportId());
                }
            }
        }
    }

    /* ------------------------------ Queries ------------------------------ */

    /**
     * Ticks the query states.
     */
    private void tickQueries() {
        int expectedTicks = (int)Math.ceil(Math.max(50.0f, averageResponseTime) / 50.0f);
        if (!processing.isEmpty()) {
            // If we're not receiving packets then we don't want to increase this as we'll simply wait for the
            // server's response. By then, if we do not have any further information about hte state of the queries
            // we will handle them appropriately.
            if (player.getTSLP() > expectedTicks * 50) ticksProcessing = 0; // --ticksProcessing;
            if (++ticksProcessing > 40 + expectedTicks) {
                logger.warning(String.format(
                        "%s query timeout: mlimit=%d, preconf=%d, proc=%d, t_p=%d, dt=%dms.",
                        player.getUsername(), movementLimit, preConfirms.size(), processing.size(),
                        ticksProcessing, System.currentTimeMillis() - processing.peek().startTime
                ));
                ticksProcessing = 0;

                // If we've reached this point, there's probably a bug in other parts of this code, or the server
                // dropped all our packets after the lag spike, for some reason?

                synchronized (this) {
                    if (current != null) rescheduled.add(current.query);
                    movementLimit = 20;

                    for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                    processing.clear();
                    preConfirms.clear();   
                }
            }

        } else {
            ticksProcessing = 0;
        }

        if (!rescheduled.isEmpty()) {
            logger.finer(String.format(
                    "%s %d queries successfully rescheduled.", 
                    player.getUsername(), rescheduled.size()
            ));

            synchronized (handle) {
                handle.rescheduled.addAll(rescheduled);
                rescheduled.clear();
            }
        }
    }

    /**
     * Listens for packets that indicate a query response from the server.
     */
    private void packetInQuery(Packet packet) {
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
        //  - NCP net flying frequency cancels our movement packet:
        //    > Detected when we receive an open window packet with no following teleport packet
        //    > The fix is to re-align the teleport ID to window ID mappings and reschedule the dropped query
        //  - Anti-cheat stops our place block packet:
        //    > Detected when we receive a block change packet with no prior open window packet
        //    > If the previous chunk was unloaded, the storage will still be open, so the detection will have gone
        //      through correctly.
        //    > If the previous chunk was loaded, we can't be sure of the state of it.
        //    > Re-align the teleport ID to window ID mappings, reschedule if required.
        //  - Paper's packet in limit stops our place block packet (9 every 300ms on constantiam.net):
        //    > Detected when we receive a teleport packet with no prior block change packet
        //    > Same as the point above

        if (current == null && processing.isEmpty()) return;
        if (current != null && !(packet instanceof ServerChatPacket)) ++current.packetsElapsed;

        // Check for if we were expecting a response to a digging packet that we might have sent
        if (handle.DIGGING_RESYNC.value && packet instanceof ServerBlockChangePacket) {
            BlockChangeRecord record = ((ServerBlockChangePacket)packet).getRecord();

            BlockPosition diggingPosition = processing.isEmpty() ? null : processing.peek().diggingPosition;
            if (diggingPosition != null && 
                    diggingPosition.getX() == record.getPosition().getX() && 
                    diggingPosition.getY() == record.getPosition().getY() && 
                    diggingPosition.getZ() == record.getPosition().getZ()) {
                if (current != null) {
                    logger.warning(String.format(
                            "%s nff, managed: mlimit=%d, preconf=%d, proc=%d, unsure=%s, e_tid=%d, e_wid=%d, o_wid=%d, c_wid=%d, gbs=%s, p_el=%d, t_el=%d, dt=%dms.",
                            player.getUsername(), movementLimit, preConfirms.size(), processing.size(),
                            current.unsure, current.teleportID, current.windowID, current.openWindowID, 
                            current.closeWindowID, current.gotBlockState, current.packetsElapsed,
                            current.ticksElapsed, System.currentTimeMillis() - current.startTime
                    ));

                    synchronized (this) {
                        // Haven't finished processing the previous query, need to reschedule it and we'll also limit
                        // the movement packets as this means we flagged the NCP net flying frequency
                        rescheduled.add(current.query);
                        movementLimit = 20; // FIXME: Is this too long or too short?

                        // Actually, let's just reschedule them all because we shouldn't be flagging NFF as it is
                        for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                        processing.clear();
                        preConfirms.clear();

                        /*
                        // If we're using WID resync then we can assume that our window ID data is accurate, and can
                        // therefore safely re-align the incorrect window IDs
                        if (handle.WID_RESYNC.value) {
                            boolean realignWID = current.openWindowID < 1;
                            for (ProcessingQuery processingQuery : processing) {
                                --processingQuery.teleportID; // Definitely didn't get a teleport packet
                                if (realignWID) {
                                    if (processingQuery.windowID == 1) processingQuery.windowID = 101;
                                    --processingQuery.windowID;
                                }
                            }
                        } else { // Otherwise, reschedule all the queries
                            for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                            processing.clear();
                        }
                         */
                    }

                    return; // Nothing we can do past this point, except wait
                }

                synchronized (this) {
                    current = processing.poll();
                    current.unsure = false;
                }
            }
        }

        if (handle.ARZI_MODE.value && handle.WID_RESYNC.value) {
            if (packet instanceof ServerOpenWindowPacket) {
                ServerOpenWindowPacket openWindow = (ServerOpenWindowPacket)packet;

                synchronized (this) {
                    if (current == null) current = processing.poll();
                    if (current.openWindowID > 0 || current.gotBlockState || current.closeWindowID > 0) {
                        logger.warning(String.format(
                                "%s nff, unmanaged: mlimit=%d, preconf=%d, proc=%d, unsure=%s, e_tid=%d, e_wid=%d, o_wid=%d, c_wid=%d, gbs=%s, p_el=%d, t_el=d, dt=%dms.",
                                player.getUsername(), movementLimit, preConfirms.size(), processing.size(),
                                current.unsure, current.teleportID, current.windowID, current.openWindowID,
                                current.closeWindowID, current.gotBlockState, current.packetsElapsed,
                                current.ticksElapsed, System.currentTimeMillis() - current.startTime
                        ));

                        // Could've flagged net flying frequency? If digging resync is not enabled we can't be
                        // sure how many packets we've actually lost to the check (especially if our storage
                        // is limited), so a safe bet is just to reschedule all in-flight. Also limit the next
                        // movement packets.
                        rescheduled.add(current.query);
                        movementLimit = 20;

                        for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                        processing.clear();

                    } else {
                        current.openWindowID = openWindow.getWindowId();
                    }
                }

            } else if (packet instanceof ServerWindowPropertyPacket) {
                ServerWindowPropertyPacket windowProperty = (ServerWindowPropertyPacket)packet;
                // We won't count this as an elapsed packet as we'll get these after the window opens
                if (current != null && current.openWindowID == windowProperty.getWindowId()) --current.packetsElapsed;

            } else if (packet instanceof ServerWindowItemsPacket) {
                ServerWindowItemsPacket windowItems = (ServerWindowItemsPacket)packet;
                if (current != null && current.openWindowID == windowItems.getWindowId()) --current.packetsElapsed;

            } else if (packet instanceof ServerBlockChangePacket) {
                ServerBlockChangePacket blockChange = (ServerBlockChangePacket)packet;

            } else if (packet instanceof ServerCloseWindowPacket) {
                ServerCloseWindowPacket closeWindow = (ServerCloseWindowPacket)packet;

                synchronized (this) {
                    if (current == null) current = processing.poll();
                }
                // Check for if we already have a close window ID associated, don't need to check the open window
                // ID as that's performed later, when we get the teleport packet
                if (/* current.openWindowID < 0 || !current.gotBlockState || */ current.closeWindowID > 0) {
                    logger.warning(String.format(
                            "%s ext close window: mlimit=%d, preconf=%d, proc=%d, unsure=%s, e_tid=%d, e_wid=%d, o_wid=%d, c_wid=%d, ac_wid=%d, p_el=%d, t_el=%d, dt=%dms.",
                            player.getUsername(), movementLimit, preConfirms.size(), processing.size(),
                            current.unsure, current.teleportID, current.windowID, current.openWindowID,
                            current.closeWindowID, closeWindow.getWindowId(), current.packetsElapsed,
                            current.ticksElapsed, System.currentTimeMillis() - current.startTime
                    ));

                    // Means that we've gotten more close windows than we expected. This could be due to NCP flags, 
                    // idk? Wouldn't indicate that we've teleported twice though because we definitely should've
                    // received the teleport packet (and processed the query).

                    // FIXME: Could this actually ever happen though, because the storage would need to have been opened
                    //        twice, maybe 9b's blatant patch could cause this though? <- ig we aren't really coord
                    //        exploiting on 9b though lol

                } else {
                    current.closeWindowID = closeWindow.getWindowId();
                }

            } else if (packet instanceof ServerPlayerPositionRotationPacket) {
                ServerPlayerPositionRotationPacket positionRotation = (ServerPlayerPositionRotationPacket)packet;

                synchronized (this) {
                    if (current == null) current = processing.poll();
                }
                if (current.teleportID != positionRotation.getTeleportId()) {
                    // TODO: Something has gone wrong here

                } else if (current.windowID < 0 || !current.gotBlockState) {
                    // TODO: Something has gone wrong here

                } else if (current.windowID != current.openWindowID) {
                    // TODO: Something has gone wrong here

                } else if (current.closeWindowID > 0 && current.windowID != current.closeWindowID) {
                    // TODO: Something has gone wrong here

                } else {

                }

                current = null;
            }

        } else {
            if (packet instanceof ServerCloseWindowPacket) {
                ServerCloseWindowPacket closeWindow = (ServerCloseWindowPacket)packet;

                // If digging resync is enabled case will fail, so check if unsure
                if (current != null && current.unsure) {
                    logger.warning(String.format(
                            "%s ext close window: mlimit=%d, preconf=%d, proc=%d, unsure=true, e_tid=%d, c_wid=%d",
                            player.getUsername(), movementLimit, preConfirms.size(), processing.size(),
                            current.teleportID, closeWindow.getWindowId() 
                    ));

                    // Extraneous close window packet, not sure exactly why this would happen. Catching this would also
                    // be pretty niche (without ARZI mode). No need to actually do anything here, as we already know
                    // that the storage is closed.

                } else if (current == null) {
                    synchronized (this) {
                        current = processing.poll();
                    }
                }

                if (current != null) current.closeWindowID = closeWindow.getWindowId();

            } else if (packet instanceof ServerPlayerPositionRotationPacket) {
                ServerPlayerPositionRotationPacket positionRotation = (ServerPlayerPositionRotationPacket)packet;

                synchronized (this) {
                    if (current == null) current = processing.poll();
                }
                if (current.teleportID != positionRotation.getTeleportId()) {
                    logger.warning(String.format(
                            "%s unexpected setback: unsure=%s, e_tid=%d, tid=%d, c_wid=%d dt=%dms",
                            player.getUsername(), current.unsure, current.teleportID, positionRotation.getTeleportId(),
                            current.closeWindowID, System.currentTimeMillis() - current.startTime
                    ));

                    // Honestly not too sure what could've happened here, if we flagged the NFF then this wouldn't occur
                    // (since even digging resync would just reschedule all). Perhaps an unconfirmed teleport occurred
                    // and we incremented the ID, or just some extraneous NCP flag.

                    synchronized (this) {
                        // TODO: Is there something more efficient that can be done?
                        rescheduled.add(current.query);
                        for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                        processing.clear();
                        preConfirms.clear();
                    }

                    // Confirm it because we're not too sure what could've happened
                    player.send(new ClientTeleportConfirmPacket(positionRotation.getTeleportId()));

                } else {
                    if (current.diggingPosition != null && current.unsure) {
                        logger.warning(String.format(
                                "%s no digging response: unsure=true, e_tid=%d, tid=%d, c_wid=%d, dt=%dms.",
                                player.getUsername(), current.teleportID, positionRotation.getTeleportId(),
                                current.closeWindowID, System.currentTimeMillis() - current.startTime
                        ));
                    }

                    // Storage wasn't open by the time we started processing this query, probably
                    if (!storageOpen && current.closeWindowID < 0) {
                        logger.finer(String.format(
                                "%s storage is closed, rescheduling current: e_tid=%d, tid=%d, dt=%dms.", 
                                player.getUsername(), current.teleportID, positionRotation.getTeleportId(),
                                System.currentTimeMillis() - current.startTime
                        ));
                        synchronized (this) {
                            rescheduled.add(current.query);
                        }

                    } else {
                        finalise(current.closeWindowID > 0);
                    }
                }

                synchronized (this) {
                    // Storage isn't open and we don't intend on opening it for the next query?
                    if (!storageOpen && (processing.isEmpty() || processing.peek().windowID < 0)) {
                        logger.finest(String.format(
                                "%s storage is closed, rescheduling %d queries.", player.getUsername(), processing.size()
                        ));

                        for (ProcessingQuery processingQuery : processing) rescheduled.add(processingQuery.query);
                        processing.clear();

                        if (currentStorage != null && attemptOpen < 0) {
                            logger.finest(String.format("%s is attempting to open storage.", player.getUsername()));
                            attemptOpen = System.currentTimeMillis();
                            currentStorage.tryOpen();
                        }
                    }
                }

                current = null;
            }
        }

    }

    /**
     * Streamlined way of "finalising" an {@link InvalidMoveQuery}.
     * @param loaded Was the query loaded?
     */
    private void finalise(boolean loaded) {
        previousLoaded = loaded;
        ++finalisedThisTick;
        ticksProcessing = 0; // Have actually finalised queries
        if (current == null) return;

        int responseTime = (int)(System.currentTimeMillis() - current.startTime);
        averageResponseTime = averageResponseTime * 0.95f + responseTime * 0.05f;

        logger.finest(String.format(
                "%s finalised query: position=(%d, %d), loaded=%s, dt=%dms.",
                player.getUsername(),
                current.query.position.getX() * 16, current.query.position.getZ() * 16,
                loaded, responseTime
        ));
        current.query.setState(loaded ? ChunkState.State.LOADED : ChunkState.State.UNLOADED);

        Consumer<InvalidMoveQuery> callback;
        synchronized (handle) {
            callback = handle.callbacks.get(current.query);
            handle.callbacks.remove(current.query);

            if (handle.cancelled.contains(current.query)) callback = null; // Indicate that it was cancelled
            handle.cancelled.remove(current.query);
        }

        // Fire the callback outside of the synchronized block to avoid deadlocking :p
        if (callback != null) callback.accept(current.query); // FIXME: Different thread perhaps?
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * @return Can this player handle the given {@link InvalidMoveQuery}?
     */
    public boolean canHandle(InvalidMoveQuery query) {
        // Can't handle queries in other dimensions, might still happen (idk), better safe than sorry though        
        if (query.dimension != player.getDimension()) return false;
        if (dispatchedThisTick >= Math.floor(handle.QUERIES_PER_TICK.value) + overshootDispatching) return false;
        // How many ticks should we expect the server to respond in?
        return Math.max(50.0f, averageResponseTime) / 50.0f * handle.QUERIES_PER_TICK.value > processing.size();
    }

    /**
     * @return Can this player make queries?
     */
    public boolean canQuery() {
        return (currentStorage != null && spawned && movementLimit <= 0 && !teleportDesync && 
                (storageOpen || handle.ARZI_MODE.value));
    }

    /**
     * Dispatches a query with this player handle.
     * @param query The {@link InvalidMoveQuery} to dispatch.
     */
    public void dispatch(InvalidMoveQuery query) {
        logger.finest(String.format("%s dispatching query %s.", player.getUsername(), query));

        BlockPosition position = query.position.getPosition(8, 100000, 8);
        BlockPosition diggingPosition = null;
        if (handle.DIGGING_RESYNC.value) {
            int distance = handle.MAX_DIGGING_DISTANCE.value - 4;
            int diggingX = random.nextInt(distance - 5) + 5;
            distance -= diggingX;
            int diggingY = distance <= 0 ? 0 : random.nextInt(distance);
            distance -= diggingY; // Cheap, but it works
            int diggingZ = distance <= 0 ? 0 : random.nextInt(distance);
            diggingPosition = new BlockPosition(
                    Math.floor(player.getPosition().getX()) + diggingX,
                    Math.floor(player.getPosition().getY()) + diggingY,
                    Math.floor(player.getPosition().getZ()) + diggingZ
            );

            player.send(new ClientPlayerActionPacket(
                    PlayerAction.CANCEL_DIGGING,
                    new com.github.steveice10.mc.protocol.data.game.entity.metadata.Position(
                            diggingPosition.getX(), diggingPosition.getY(), diggingPosition.getZ()
                    ),
                    BlockFace.UP
            ));
        }

        // if (processing.isEmpty()) ticksSinceTeleport = 0;
        synchronized (this) {
            if (handle.ARZI_MODE.value && handle.WID_RESYNC.value) estimatedWindowID = estimatedWindowID + 1 % 100;
            processing.add(new ProcessingQuery(
                    query, diggingPosition, ++estimatedTeleportID, handle.ARZI_MODE.value ? estimatedWindowID : -1
            ));
            // preConfirms.add(estimatedTeleportID);
        }

        if (handle.ARZI_MODE.value) currentStorage.tryOpen(); // Open the storage before we send the movement

        // TODO: Outside the world should always return false
        int positionX = Math.min(29999999, Math.max(-29999999, position.getX())); // Will get kicked otherwise
        int positionZ = Math.min(29999999, Math.max(-29999999, position.getZ()));

        // Sending this after we've opened the storage (with ARZI mode) means that we can guarantee something will
        // have gone wrong if we do not receive the open storage packet before the response
        player.send(new ClientPlayerPositionPacket(false, positionX, position.getY(), positionZ));
        player.send(new ClientTeleportConfirmPacket(estimatedTeleportID));

        ++dispatchedThisTick;
    }

    /**
     * @return The number of queries that were dispatched this tick.
     */
    public int getDispatchedThisTick() {
        return dispatchedThisTick;
    }

    /**
     * @return The number of queries that were finalised this tick.
     */
    public int getFinalisedThisTick() {
        return finalisedThisTick;
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Represents any storage that this {@link Player} can use.
     */
    private interface IStorage {
        /**
         * @param eyesPosition The position of the {@link Player}'s eyes.
         * @return Is the storage valid?
         */
        boolean isValid(Position eyesPosition);

        /**
         * Tries to open the storage.
         */
        void tryOpen();
    }

    /**
     * Represents a block storage that this {@link Player} can use.
     */
    private class BlockStorage implements IStorage {

        public final BlockPosition position;
        public final String blockID;

        public final Position centerPosition;

        private Position lastEyesPosition;
        private Angle requiredAngle;

        public BlockStorage(BlockPosition position, String blockID) {
            this.position = position;
            this.blockID = blockID;

            centerPosition = new Position(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            BlockStorage that = (BlockStorage)other;
            return position.equals(that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position);
        }

        @Override
        public String toString() {
            return String.format("BlockStorage(position=%s, id=%s)", position, blockID);
        }

        @Override
        public boolean isValid(Position eyesPosition) {
            Position delta = centerPosition.subtract(eyesPosition);
            boolean valid = delta.getX() * delta.getX() + delta.getY() * delta.getY() + delta.getZ() * delta.getZ() < 25.0f;
            // Only calculate the required angle for valid storages
            if (valid && (requiredAngle == null || !eyesPosition.equals(lastEyesPosition))) {
                lastEyesPosition = eyesPosition;
                requiredAngle = new Angle(
                        (float)Math.toDegrees(Math.atan2(delta.getZ(), delta.getX())) - 90.0f,
                        (float)(-Math.toDegrees(Math.atan2(
                                delta.getY(), Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ())
                        )))
                );
            }
            return valid;
        }

        @Override
        public void tryOpen() {
            if (!player.getAngle().equals(requiredAngle)) player.setAngle(requiredAngle);
            
            player.send(new ClientPlayerPlaceBlockPacket(
                    new com.github.steveice10.mc.protocol.data.game.entity.metadata.Position(
                            position.getX(), position.getY(), position.getZ()
                    ),
                    BlockFace.UP,
                    Hand.MAIN_HAND,
                    0.0f, 0.0f, 0.0f
            ));
            if (handle.SWING_ARM.value) player.send(new ClientPlayerSwingArmPacket(Hand.MAIN_HAND));
            
        }
    }

    /**
     * Represents an entity (which can be used as a storage) that this {@link Player} can see.
     */
    private class EntityStorage implements IStorage {

        public final int entityID;

        public EntityStorage(int entityID) {
            this.entityID = entityID;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) return false;
            EntityStorage entity = (EntityStorage)other;
            return entityID == entity.entityID;
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityID);
        }

        @Override
        public boolean isValid(Position eyesPosition) {
            return false; // TODO: Validate entity storage, reach distance 5?
        }

        @Override
        public void tryOpen() {

        }
    }

    /**
     * Information about a query that is currently being processed.
     */
    private static class ProcessingQuery {

        public final InvalidMoveQuery query;
        public final BlockPosition diggingPosition;
        public final long startTime;

        public int teleportID;
        public int windowID;

        public int packetsElapsed = 0;
        public int ticksElapsed = 0;

        public boolean unsure = true; // For digging resync, true if we didn't get the digging packet response
        public int openWindowID = -1;
        public boolean gotBlockState = false; // FIXME: Very block-storage-centric
        public int closeWindowID = -1;

        public ProcessingQuery(InvalidMoveQuery query, BlockPosition diggingPosition, int teleportID, int windowID) {
            this.query = query;
            this.diggingPosition = diggingPosition;
            this.teleportID = teleportID;
            this.windowID = windowID;

            startTime = System.currentTimeMillis();
        }

        public ProcessingQuery(InvalidMoveQuery query, BlockPosition diggingPosition, int teleportID) {
            this(query, diggingPosition, teleportID, -1);
        }
    }
}
