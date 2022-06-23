package ez.pogdog.yescom.core.connection;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntryAction;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.google.gson.JsonObject;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.api.data.player.Session;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.api.data.player.death.Kill;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveHandle;
import ez.pogdog.yescom.core.report.connection.HighTSLPReport;
import ez.pogdog.yescom.core.servers.IServerBehaviour;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Represents a server that we are connected to. We can be connected to multiple servers.
 */
public class Server implements IConfig, ITickable {

    private final Logger logger = Logging.getLogger("yescom.core.connection");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Boolean> DIGGING_ENABLED = new Option<>(
            "Digging enabled",
            "Enables digging packet queries to be made when querying for loaded chunks.",
            false
    );
    public final Option<Boolean> INVALID_MOVE_ENABLED = new Option<>(
            "Invalid move enabled",
            "Enables invalid move packet queries to be made when querying for loaded chunks.",
            true
    );

    public final Option<Integer> GLOBAL_LOGIN_TIME = new Option<>(
            "Global login time",
            "How often players can log in, in milliseconds. This is global, so players can limit other players.",
            8000
    );

    public final Option<Integer> AUTO_RECONNECT_TIME = new Option<>(
            "Auto reconnect time",
            "The auto reconnect time for players, in milliseconds.",
            4000
    );
    public final Option<Integer> AUTO_LOGOUT_RECONNECT_TIME = new Option<>(
            "Auto logout reconnect time",
            "How long to wait before reconnecting a player after an automatic logout.",
            120000
    );
    public final Option<Integer> MAX_FAILED_LOGIN_ATTEMPTS = new Option<>(
            "Max failed login attempts",
            "The maximum number of failed login attempts before disabling auto reconnect.",
            5
    );

    public final Option<Double> EXTREME_TPS_CHANGE = new Option<>( // These have to be doubles unfortunately, for YAML and Python :(
            "Extreme TPS change",
            "The TPS variation that would be reported as extreme.",
            6.0
    );
    public final Option<Integer> HIGH_TSLP = new Option<>(
            "High TSLP",
            "The TSLP that would be reported as high.",
            1000
    );

    // TODO: Packet loss and latency perhaps?

    /* ------------------------------ Other fields ------------------------------ */

    public final List<IQueryHandle<?>> handles = new ArrayList<>(); // TODO: More specific for loaded queries
    public final IServerBehaviour behaviour;

    public final ServerSessionAdapter adapter = new ServerSessionAdapter();

    public final String hostname;
    public final int port;

    public final PlayerInfo.ServerInfo serverInfo;

    // Direct references for ease, it's hacky but who cares
    public final InvalidMoveHandle overworldInvalidMoveHandle;
    public final InvalidMoveHandle netherInvalidMoveHandle;
    public final InvalidMoveHandle endInvalidMoveHandle;

    private final Map<UUID, Long> onlinePlayers = new HashMap<>();
    private final List<Player> players = new CopyOnWriteArrayList<>();
    // private final Queue<ChatMessage> chatMessages = new ArrayDeque<>();

    // Need this as multiple players can report about the death at different times
    private final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();

    private boolean connected;
    private int renderDistance;
    private float tickrate;
    private int tslp;
    private float ping;

    private float effectiveQPS;
    private float actualQPS;
    private int waitingSize;
    private int processingSize;

    private long connectionTime; // The time that we first logged into the server at (reset if we aren't connected)
    private long lastLoginTime; // The last time we logged into the server at
    private long lastRenderDistanceTime;
    private long lastStatsTime;
    private int lastHighTslp;

    public Server(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;

        serverInfo = new PlayerInfo.ServerInfo(hostname, port);

        yesCom.tickables.add(this);
        yesCom.configHandler.addConfiguration(this);

        overworldInvalidMoveHandle = new InvalidMoveHandle(this, Dimension.OVERWORLD);
        netherInvalidMoveHandle = new InvalidMoveHandle(this, Dimension.NETHER);
        endInvalidMoveHandle = new InvalidMoveHandle(this, Dimension.END);

        handles.add(overworldInvalidMoveHandle);
        handles.add(netherInvalidMoveHandle);
        handles.add(endInvalidMoveHandle);

        logger.fine(String.format("%d handle(s) for server %s:%d.", handles.size(), hostname, port));

        Set<IServerBehaviour> behaviours = new HashSet<>();
        for (IServerBehaviour behaviour : ServiceLoader.load(IServerBehaviour.class)) {
            if (behaviour.isValid(this)) behaviours.add(behaviour);
        }
        for (IServerBehaviour behaviour : new ArrayList<>(behaviours))
            behaviours.removeIf(behaviour0 -> behaviour.getOverrides().contains(behaviour0.getClass()));

        behaviour = behaviours.iterator().next();
        logger.fine(String.format("Registered valid behaviour %s for %s:%d.", behaviour, hostname, port));
        behaviour.apply(this);

        // logger.fine(String.format("%d behaviour(s) for server %s:%d.", behaviours.size(), hostname, port));

        connectionTime = System.currentTimeMillis();
        lastLoginTime = System.currentTimeMillis() - GLOBAL_LOGIN_TIME.value;
        lastRenderDistanceTime = System.currentTimeMillis() - 5000;
        lastStatsTime = System.currentTimeMillis();
        lastHighTslp = 0;

        // Emitters.ON_ACCOUNT_ADDED.connect(this::onAccountAdded);
    }

    @Override
    public String toString() {
        return String.format("Server(host=%s, port=%d, players=%d, tps=%.1f, ping=%.1f, qps=%.1f)", hostname, port,
                players.size(), tickrate, ping, queriesPerSecond);
    }

    @Override
    public String getIdentifier() {
        return String.format("server-%s-%d", hostname.replace(".", "_"), port);
    }

    @Override
    public IConfig getParent() {
        return yesCom;
    }

    /* ------------------------------ Events ------------------------------ */

    /**
     * Ticks this server.
     */
    @Override
    public synchronized void tick() {
        Set<IAccount> accounts = yesCom.accountHandler.getAvailableAccounts();
        if (!accounts.isEmpty()) {
            int loggedIn = 0;
            // logger.finer(String.format("Adding %d account(s) to %s:%d...", accounts.size(), hostname, port));
            outer: for (IAccount account : accounts) { // FIXME: Way too slow, some sort of emitter?
                for (Player player : players) {
                    if (player.account.equals(account)) continue outer;
                }

                try {
                    addPlayer(new Player(this, account));
                    ++loggedIn;
                } catch (RequestException error) {
                    logger.warning(String.format("Failed to add account %s to %s:%d: %s.", account, hostname, port, error.getMessage()));
                    logger.throwing(getClass().getSimpleName(), "onAccountAdded", error);
                }

                if (loggedIn > 2) break; // Login max 2 accounts per tick
            }
        }
        // logger.finer(String.format("Server %s:%d has %d usable player(s).", hostname, port, players.size()));

        int connectedCount = 0;
        int newRenderDistance = System.currentTimeMillis() - lastRenderDistanceTime > 5000 ? 0 : renderDistance;
        tickrate = 0.0f;
        tslp = 30000;
        ping = 0.0f;

        Player lowestTSLP = null;
        for (Player player : players) {
            player.tick();
            if (player.isConnected()) {
                ++connectedCount;

                if (newRenderDistance == 0) { // Don't recalculate if we don't need to
                    float playerRenderDistance = (float)Math.sqrt(player.loadedChunks.size());
                    // Only trust this render distance estimate if:
                    //  1. The player is spawned in
                    //  2. The player hasn't received a chunk packet in 500ms
                    //  3. The player has received a packet in under 500ms
                    //  4. The render distance is an integer
                    if (player.isSpawned() && player.getTimeSinceLastChunkPacket() > 500 && player.getTSLP() < 500 &&
                            playerRenderDistance % 1.0f == 0.0f) {
                        // Further check, if we have a bigger estimate already, don't set
                        if (playerRenderDistance > newRenderDistance) newRenderDistance = (int)playerRenderDistance;
                    }
                }

                tickrate += player.getServerTPS();
                if (player.getTSLP() < tslp) {
                    lowestTSLP = player;
                    tslp = player.getTSLP();
                }
                ping += player.getServerPing();
            }
        }

        boolean wasConnected = connected;

        if (connectedCount > 0) {
            connected = true;
            if (newRenderDistance > 0 && newRenderDistance != renderDistance) { // Worked out new render distance?
                logger.fine(String.format("%s:%d render distance is %d.", hostname, port, newRenderDistance));
                renderDistance = newRenderDistance;
                lastRenderDistanceTime = System.currentTimeMillis();
            }
            if (!wasConnected) {
                logger.info(String.format("Established connection to %s:%d.", hostname, port));
                Emitters.ON_CONNECTION_ESTABLISHED.emit(this);
            }

            // So in retrospect, obviously this isn't the actual server tickrate, what this is measuring instead is
            // perceived tickrate, which is good enough for our purposes as we're merely representing how quickly we're
            // getting packets from the server which is going to be used by other parts of the application for certain
            // calculations. Obviously though, it might be useful to record an actual server tickrate (if that is
            // actually possible to measure accurately) for the user to see and/or for the recorded server data
            tickrate /= connectedCount;
            if (tickrate < 0.0f) { // Make the tickrate more reasonable
                tickrate = 20.0f;
            } else if (tickrate > 75.0f) {
                tickrate = 75.0f;
            }

            ping /= connectedCount;
            if (tslp > HIGH_TSLP.value) {
                if (tslp - lastHighTslp > HIGH_TSLP.value) {
                    logger.fine(String.format("Server %s:%d has not responded in %dms!", hostname, port, tslp));
                    Emitters.ON_REPORT.emit(new HighTSLPReport(lowestTSLP, tslp));
                    lastHighTslp = tslp;
                }
            } else {
                lastHighTslp = 0;
            }

        } else {
            connected = false;
            if (wasConnected) {
                logger.info(String.format("Lost connection to %s:%d.", hostname, port));
                Emitters.ON_CONNECTION_LOST.emit(this);
            }

            synchronized (onlinePlayers) { // If we aren't connected then we don't know anything about the online players
                if (!onlinePlayers.isEmpty()) {
                    for (UUID uuid : new ArrayList<>(onlinePlayers.keySet())) handleDisconnect(yesCom.playersHandler.getInfo(uuid));
                    onlinePlayers.clear();
                }
            }
            recentDeaths.clear();

            tslp = 0;
            connectionTime = System.currentTimeMillis();
        }

        effectiveQPS = 0.0f;
        actualQPS = 0.0f;
        waitingSize = 0;
        processingSize = 0;
        // TODO: Calculate loss rates too

        for (IQueryHandle<?> handle : handles) {
            handle.tick();
            effectiveQPS += handle.getEffectiveQPS();
            actualQPS += handle.getActualQPS();
            waitingSize += handle.getWaitingSize();
            processingSize += handle.getProcessingSize();
        }

        // if (!handles.isEmpty()) queriesPerSecond /= handles.size();

        for (Map.Entry<UUID, Long> entry : recentDeaths.entrySet()) { // FIXME: Could lag mess with this?
            if (System.currentTimeMillis() - entry.getValue() > 100) recentDeaths.remove(entry.getKey()); // Remove expired recent deaths
        }

        if (System.currentTimeMillis() - lastStatsTime > 60000) {
            logger.finer(String.format(
                    "Server %s:%d stats: %d player(s), %d/%d queries, %.1f tps, %.1f ping, %.1f qps.",
                    hostname, port, players.size(), processingSize, waitingSize, tickrate, ping, effectiveQPS
            ));
            lastStatsTime = System.currentTimeMillis();
        }

        behaviour.tick();
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Dispatches a query to this server.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void dispatch(IQuery<?> query, Consumer<IQuery<?>> callback) {
        for (IQueryHandle handle : handles) {
            if (handle.handles(query)) handle.dispatch(query, callback);
        }
    }

    /**
     * Cancels a query.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void cancel(IQuery<?> query) {
        for (IQueryHandle handle : handles) {
            if (handle.handles(query)) handle.cancel(query);
        }
    }

    /**
     * @return All the {@link Player}s (that we own) connected to this server.
     */
    public List<Player> getPlayers() {
        return players;
    }

    /**
     * @param username The username (case-insensitive) of the player.
     * @return The player, {@code null} if not found.
     */
    public Player getPlayer(String username) {
        for (Player player : players) {
            if (player.getUsername().equalsIgnoreCase(username)) return player;
        }
        return null;
    }

    /**
     * @param uuid The UUID of the player.
     * @return The player, {@code null} if not found.
     */
    public Player getPlayer(UUID uuid) {
        for (Player player : players) {
            if (player.getUUID().equals(uuid)) return player;
        }
        return null;
    }

    /**
     * @param player The player.
     * @return Does this server have this player?
     */
    public boolean hasPlayer(Player player) {
        return players.contains(player);
    }

    /**
     * @param username The username (case-insensitive) of the player.
     * @return Is the player with that username one of our own?
     */
    public boolean hasPlayer(String username) {
        for (Player player : players) {
            if (player.getUsername().equalsIgnoreCase(username)) return player.isConnected();
        }
        return false;
    }

    /**
     * @param uuid The UUID of the player.
     * @return Is the player with that UUID one of our own?
     */
    public boolean hasPlayer(UUID uuid) { // FIXME: Make these lookups faster
        for (Player player : players) {
            if (player.getUUID().equals(uuid)) return player.isConnected();
        }
        return false;
    }

    /**
     * Adds a player to this server.
     * @param player The player to add.
     */
    public void addPlayer(Player player) {
        if (!players.contains(player) && player.server == this) {
            players.add(player);
            Emitters.ON_PLAYER_ADDED.emit(player);
        }
    }

    /**
     * Removes a player from this server.
     * @param player The player to remove.
     */
    public void removePlayer(Player player) {
        if (players.contains(player)) {
            players.remove(player);
            Emitters.ON_PLAYER_REMOVED.emit(player);
        }
    }

    /**
     * Disconnects all online players for this server.
     * @param reason The reason for the disconnect.
     * @param disableAutoReconnect Disables auto reconnect for all players.
     */
    public void disconnectAll(String reason, boolean disableAutoReconnect) {
        for (Player player : players) {
            player.disconnect(reason);
            if (disableAutoReconnect) player.AUTO_RECONNECT.value = false;
        }
    }

    /**
     * Disconnects all online players for this server.
     * @param reason The reason for the disconnect.
     */
    public void disconnectAll(String reason) {
        disconnectAll(reason, false);
    }

    /**
     * @return Can players currently log into this server? (Based on {@link #GLOBAL_LOGIN_TIME}).
     */
    public boolean canLogin() {
        return System.currentTimeMillis() - lastLoginTime > GLOBAL_LOGIN_TIME.value;
    }

    /**
     * Resets the current login time.
     */
    public void resetLoginTime() { // TODO: Package private
        lastLoginTime = System.currentTimeMillis();
    }

    /**
     * @return Is this player trusted to us?
     */
    public boolean isTrusted(UUID uuid) {
        if (uuid == null) return false;

        for (Player player : players) {
            if (uuid.equals(player.getUUID())) return true;
        }
        return yesCom.playersHandler.isTrusted(uuid);
    }

    /**
     * @return Is the provided chunk loaded by a player?
     */
    public boolean isLoadedByPlayer(ChunkPosition position) {
        for (Player player : players) {
            if (player.loadedChunks.contains(position)) return true;
        }
        return false;
    }

    /**
     * Handles when any player connects to the server.
     * @param player The player that connected.
     */
    public void handleConnect(PlayerInfo player) {
        synchronized (onlinePlayers) {
            if (!onlinePlayers.containsKey(player.uuid)) {
                if (!player.servers.contains(serverInfo)) player.servers.add(serverInfo);

                onlinePlayers.put(player.uuid, System.currentTimeMillis());
                Emitters.ON_ANY_PLAYER_JOIN.emit(new Emitters.OnlinePlayerInfo(player, this));
            }
        }
    }

    /**
     * Parses a chat message.
     * @param player The player that received the message.
     * @param packet The server chat packet.
     * @return A {@link ChatMessage}, if null this message should be discarded.
     */
    public ChatMessage parseChatMessage(Player player, ServerChatPacket packet) {
        return behaviour.parseChatMessage(player, packet);
    }

    /**
     * Handles a chat message.
     * @param chatMessage The chat message to handle.
     */
    public void handleChatMessage(ChatMessage chatMessage) {
        // No need to synchronise, we shouldn't see the same chat message twice from the same account
        Emitters.ON_PLAYER_CHAT.emit(new Emitters.PlayerChat(this, chatMessage));
    }

    /**
     * Handles when any player dies on this server.
     * @param player The player that died.
     * @param death The {@link Death}.
     */
    public void handleDeath(PlayerInfo player, Death death) {
        synchronized (onlinePlayers) {
            if (!player.deaths.contains(death) && !recentDeaths.containsKey(player.uuid)) {
                recentDeaths.put(player.uuid, System.currentTimeMillis());
                if (!player.servers.contains(serverInfo)) player.servers.add(serverInfo);

                player.deaths.add(death);
                Emitters.ON_ANY_PLAYER_DEATH.emit(new Emitters.OnlinePlayerDeath(player, this, death));

                if (death.killer != null) {
                    PlayerInfo killer = yesCom.playersHandler.getInfo(death.killer);
                    if (!killer.servers.contains(serverInfo)) killer.servers.add(serverInfo);
                    killer.kills.add(new Kill(serverInfo, death.timestamp, player.uuid));

                    // No need to fire an event as it can be handled under the death event
                }
            }
        }
    }

    /**
     * Handles when any player disconnects from this server.
     * @param player The player that disconnected.
     */
    public void handleDisconnect(PlayerInfo player) {
        synchronized (onlinePlayers) {
            if (onlinePlayers.containsKey(player.uuid)) {
                if (!player.servers.contains(serverInfo)) player.servers.add(serverInfo);

                player.sessions.add(new Session(serverInfo, onlinePlayers.get(player.uuid), System.currentTimeMillis()));
                onlinePlayers.remove(player.uuid);
                Emitters.ON_ANY_PLAYER_LEAVE.emit(new Emitters.OnlinePlayerInfo(player, this));
            }
        }
    }

    /**
     * @return Is the player with the provided UUID online?
     */
    public boolean isOnline(UUID uuid) {
        return onlinePlayers.containsKey(uuid);
    }

    /**
     * @param uuid The UUID of the player to check.
     * @return When the player's current online session started, in milliseconds.
     */
    public long getSessionStartTime(UUID uuid) {
        return onlinePlayers.getOrDefault(uuid, -1L);
    }

    /**
     * @param uuid The UUID of the player to check.
     * @return How long the player has been online for, in milliseconds.
     */
    public long getSessionPlayTime(UUID uuid) {
        long currentTime = System.currentTimeMillis();
        return currentTime - onlinePlayers.getOrDefault(uuid, currentTime + 1);
    }

    /* ------------------------------ Setters and getters ------------------------------ */

    public Map<UUID, Long> getOnlinePlayers() {
        return onlinePlayers;
    }

    /**
     * @return Are there any accounts connected to the server currently?
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * @return How long have we been connected to this server?
     */
    public int getConnectionTime() {
        return (int)((System.currentTimeMillis() - connectionTime) / 1000);
    }

    /**
     * @return The estimated render distance (*2 + 1) for this server.
     */
    public int getRenderDistance() {
        return renderDistance;
    }

    /**
     * @return The current server tickrate.
     */
    public float getTPS() {
        return tickrate;
    }

    /**
     * @return The minimum TSLP for all players connected to this server.
     */
    public int getTSLP() {
        return tslp;
    }

    /**
     * @return The average ping of all players connected to this server.
     */
    public float getPing() {
        return ping;
    }

    /**
     * @return The cumulative queries per second of all handles for this server.
     */
    public float getEffectiveQPS() {
        return effectiveQPS;
    }

    public float getActualQPS() { // TODO: Improve javadoc
        return actualQPS;
    }

    /**
     * @return The number of queries waiting to be processed across all handles.
     */
    public int getWaitingSize() {
        return waitingSize;
    }

    /**
     * @return The number of queries currently being processed across all handles.
     */
    public int getProcessingSize() {
        return processingSize;
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Responsible for directly processing packets from {@link Player}s that contribute to information shared across
     * this entire server.
     */
    private class ServerSessionAdapter extends SessionAdapter {
        @Override
        public void packetReceived(PacketReceivedEvent event) {
            if (event.getPacket() instanceof ServerPlayerListEntryPacket) {
                ServerPlayerListEntryPacket packet = event.getPacket();

                for (PlayerListEntry entry : packet.getEntries()) {
                    UUID uuid = entry.getProfile().getId();

                    if (packet.getAction() == PlayerListEntryAction.ADD_PLAYER) {
                        String skinURL = "";
                        GameProfile.Property texturesRaw = entry.getProfile().getProperty("textures");
                        if (texturesRaw != null) {
                            try {
                                JsonObject root = Globals.JSON.parse(
                                        new String(Base64.getDecoder().decode(texturesRaw.getValue()), StandardCharsets.UTF_8)
                                ).getAsJsonObject();
                                JsonObject textures = root.get("textures").getAsJsonObject();
                                JsonObject skin = textures.get("SKIN").getAsJsonObject();
                                skinURL = skin.get("url").getAsString();

                            } catch (Exception error) {
                                logger.warning("Couldn't read textures data: " + error.getMessage());
                                logger.throwing(getClass().getSimpleName(), "packetReceived", error);
                            }
                        }
                        PlayerInfo info = yesCom.playersHandler.getInfo(
                                uuid, entry.getProfile().getName(), skinURL, entry.getPing(),
                                PlayerInfo.GameMode.valueOf(entry.getGameMode().name()) // FIXME: This is a hack
                        );

                        behaviour.processJoin(info);

                    } else if (packet.getAction() == PlayerListEntryAction.REMOVE_PLAYER) {
                        behaviour.processLeave(yesCom.playersHandler.getInfo(uuid));

                    } else if (packet.getAction() == PlayerListEntryAction.UPDATE_GAMEMODE) {
                        synchronized (onlinePlayers) {
                            // Gets sent before ADD_PLAYER on constantiam.net, is this normal behaviour?
                            if (onlinePlayers.containsKey(uuid)) {
                                PlayerInfo info = yesCom.playersHandler.getInfo(uuid);
                                PlayerInfo.GameMode gameMode = PlayerInfo.GameMode.valueOf(entry.getGameMode().name()); // FIXME: This is a hack

                                if (info.gameMode != gameMode) {
                                    info.gameMode = gameMode; // FIXME: Doesn't have support for multiple servers
                                    Emitters.ON_ANY_PLAYER_GAMEMODE_UPDATE.emit(new Emitters.OnlinePlayerInfo(info, Server.this));
                                }
                            }
                        }

                    } else if (packet.getAction() == PlayerListEntryAction.UPDATE_LATENCY) {
                        synchronized (onlinePlayers) {
                            if (onlinePlayers.containsKey(uuid)) {
                                PlayerInfo info = yesCom.playersHandler.getInfo(uuid);

                                if (info.ping != entry.getPing()) {
                                    info.ping = entry.getPing();
                                    Emitters.ON_ANY_PLAYER_PING_UPDATE.emit(new Emitters.OnlinePlayerInfo(info, Server.this));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
