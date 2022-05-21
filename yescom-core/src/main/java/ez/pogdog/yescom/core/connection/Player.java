package ez.pogdog.yescom.core.connection;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPlayerListEntryPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerHealthPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.spawn.ServerSpawnPlayerPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerCloseWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerOpenWindowPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerSetSlotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowItemsPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUnloadChunkPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.PacketSentEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Angle;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.Position;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.report.connection.ExtremeTPSReport;
import ez.pogdog.yescom.core.report.player.DeadReport;
import ez.pogdog.yescom.core.report.player.HealthLogoutReport;
import ez.pogdog.yescom.core.report.player.VisualRangeLogoutReport;
import ez.pogdog.yescom.core.util.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A player that is connected to a {@link Server}.
 */
public class Player implements IConfig, ITickable {

    private final Logger logger = Logging.getLogger("yescom.core.connection");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Boolean> AUTO_RECONNECT = new Option<>( // TODO: Option to disable on failed connection attempts
            "Auto reconnect",
            "Automatically reconnects this player to the server.",
            true
    );
    public final Option<Boolean> DISABLE_AUTO_RECONNECT_ON_LOGOUT = new Option<>(
            "Disable auto reconnect on logout",
            "Disables auto reconnect on logout (due to low health or players entering visual range).",
            false
    );

    public final Option<Double> LOGOUT_HEALTH = new Option<>(
            "Logout health",
            "The health to logout on, if reached.",
            5.0
    );
    public final Option<Boolean> VISUAL_RANGE_LOGOUT = new Option<>(
            "Visual range logout",
            "Logs the player out if another (untrusted) player enters visual range.",
            true
    );

    /* ------------------------------ Other fields ------------------------------ */

    public final Server server;
    public final IAccount account;

    private AuthenticationService authService;
    private Session session;

    /* ------------------------------ Player "stats" ------------------------------ */

    public final List<ChunkPosition> loadedChunks = new ArrayList<>();

    private final Position position = new Position(0.0, 0.0, 0.0);
    private final Angle angle = new Angle(0.0f, 0.0f);
    private boolean onGround = true;

    private Position oldPosition;
    private Angle oldAngle;

    private int currentTeleportID = -1;
    private int currentWindowID = -1;

    private Dimension dimension = null;

    private float health = 20.0f;
    private int hunger = 20;
    private float saturation = 5.0f;

    private int serverPing = 0; // TODO: Estimated ping as well?
    private float serverTPS = 0.0f; // FIXME: Will this cause issues?

    /* ------------------------------ Internal fields ------------------------------ */

    private final List<Float> tickValues = new ArrayList<>();

    private UUID uuid = null;
    private String username = "<unknown>";

    private long lastLoginTime;
    private long lastAutoLogoutTime;
    private int failedLogins;
    private int failedConnections;

    private boolean positionDirty;
    private boolean angleDirty;

    private long lastChunkPacket;
    private long lastPacketTime;
    private long lastTimeUpdate;
    private long lastWorldTicks;

    /**
     * @param server The server that this player belongs to.
     * @param account The account the player will use.
     */
    public Player(Server server, IAccount account) throws RequestException {
        this.server = server;
        this.account = account;

        authService = new AuthenticationService();
        session = null;

        yesCom.accountHandler.login(this.account, authService);
        // this.account.login(authService);
        yesCom.configHandler.addConfiguration(this);

        oldPosition = position.clone();
        oldAngle = angle.clone();

        lastLoginTime = System.currentTimeMillis() - this.server.AUTO_RECONNECT_TIME.value;
        lastAutoLogoutTime = System.currentTimeMillis() - this.server.AUTO_LOGOUT_RECONNECT_TIME.value;
        failedLogins = 0;
        failedConnections = 0;

        positionDirty = false;
        angleDirty = false;

        lastChunkPacket = System.currentTimeMillis();
        lastPacketTime = System.currentTimeMillis();
        lastTimeUpdate = -1L;
        lastWorldTicks = 0L;
    }

    @Override
    public String getIdentifier() {
        return String.format("player-%s", getUUID());
    }

    @Override
    public IConfig getParent() {
        return server;
    }

    /**
     * Ticks this player.
     */
    @Override
    public void tick() {
        if (isConnected()) {
            // Update position
            if (positionDirty && angleDirty) {
                session.send(new ClientPlayerPositionRotationPacket(onGround, position.getX(), position.getY(), position.getZ(),
                        angle.getYaw(), angle.getPitch()));
                positionDirty = false;
                angleDirty = false;

            } else if (positionDirty) {
                session.send(new ClientPlayerPositionPacket(onGround, position.getX(), position.getY(), position.getZ()));
                positionDirty = false;

            } else if (angleDirty) {
                session.send(new ClientPlayerRotationPacket(onGround, angle.getYaw(), angle.getPitch()));
                angleDirty = false;
            }

            if (isSpawned()) failedConnections = 0;
            if (!oldPosition.equals(position) || !oldAngle.equals(angle)) {
                Emitters.ON_PLAYER_POSITION_UPDATE.emit(this);
                oldPosition = position.clone();
                oldAngle = angle.clone();
            }

        } else {
            boolean autoReconnectReady = System.currentTimeMillis() - lastLoginTime > server.AUTO_RECONNECT_TIME.value;
            boolean autoLogoutReady = System.currentTimeMillis() - lastAutoLogoutTime >= this.server.AUTO_LOGOUT_RECONNECT_TIME.value;
            if (AUTO_RECONNECT.value && server.canLogin() && !server.isOnline(getUUID()) &&
                    autoReconnectReady && autoLogoutReady)
                connect();
        }
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Connects this player to the server, if they are not connected already.
     */
    public void connect() {
        if (!isConnected() && server.hasPlayer(this)) {
            lastLoginTime = System.currentTimeMillis();

            try {
                logger.fine(String.format("Connecting %s to %s:%d...", getUsername(), server.hostname, server.port));
                boolean firstTime = yesCom.accountHandler.isFirstTime(account);
                try {
                    yesCom.accountHandler.login(account, authService);
                } catch (RequestException error) {
                    if (firstTime) {
                        server.removePlayer(this); // Account is unusable, this isn't a valid player
                        return;
                    }

                    logger.finer(String.format("%s login failed, refreshing auth service.", getUsername()));
                    authService = new AuthenticationService();

                    if (++failedLogins > server.MAX_FAILED_LOGIN_ATTEMPTS.value) {
                        logger.warning(String.format("%s has %d failed login attempts, disabling auto reconnect.",
                                getUsername(), failedLogins));
                        // TODO: Report
                        AUTO_RECONNECT.value = false;
                    }

                    throw error;
                }
                failedLogins = 0;

                MinecraftProtocol protocol = new MinecraftProtocol(authService.getSelectedProfile(), authService.getAccessToken());
                Client client = new Client(server.hostname, server.port, protocol, new TcpSessionFactory(null));

                session = new TcpClientSession(server.hostname, server.port, protocol, client, null);
                session.addListener(server.adapter);
                session.addListener(new PlayerSessionAdapter());
                session.connect();

                server.resetLoginTime();

            } catch (Exception error) {
                logger.warning(String.format("Failed to connect %s to %s:%d: %s", getUsername(), server.hostname, server.port, error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "connect", error);

                // lastLoginTime += server.AUTO_RECONNECT_TIME.value * (long)failedConnections++; // Don't spam connection attempts
            }
        }
    }

    /**
     * Disconnects this player from the server, if they are connected.
     * @param reason The reason for disconnecting.
     */
    public void disconnect(String reason) {
        if (isConnected()) {
            lastLoginTime = System.currentTimeMillis(); // So we don't instantly log back in again
            session.disconnect(reason);
        }
    }

    /**
     * Sends a packet to the server, duh.
     */
    public void send(Packet packet) {
        if (isConnected()) session.send(packet);
    }

    /**
     * Sends a message into chat for this player.
     * @param message The message to send.
     */
    public void chat(String message) {
        if (isConnected()) session.send(new ClientChatPacket(message));
    }

    /* ------------------------------ Setters and getters ------------------------------ */

    /**
     * @return Is this player connected to the server?
     */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    /**
     * @return Has this player properly spawned into the server?
     */
    public boolean isSpawned() {
        return currentTeleportID >= 0 && dimension != null && !loadedChunks.isEmpty() && !tickValues.isEmpty();
    }

    /**
     * @return The time since the last chunk packet was received, in milliseconds.
     */
    public int getTimeSinceLastChunkPacket() {
        return (int)(System.currentTimeMillis() - lastChunkPacket);
    }

    public UUID getUUID() {
        if (authService.getSelectedProfile() != null) uuid = authService.getSelectedProfile().getId();
        return uuid;
    }

    public String getUsername() {
        if (authService.getSelectedProfile() != null) username = authService.getSelectedProfile().getName();
        return username;
    }

    public Position getPosition() {
        return position.clone();
    }

    public void setPosition(Position position) {
        this.position.setX(position.getX());
        this.position.setY(position.getY());
        this.position.setZ(position.getZ());

        positionDirty = true;
    }

    public void setPosition(double x, double y, double z) {
        position.setX(x);
        position.setY(y);
        position.setZ(z);

        positionDirty = true;
    }

    public Angle getAngle() {
        return angle.clone();
    }

    public void setAngle(Angle angle) {
        this.angle.setYaw(angle.getYaw());
        this.angle.setPitch(angle.getPitch());

        angleDirty = true;
    }

    public void setAngle(float yaw, float pitch) {
        angle.setYaw(yaw);
        angle.setPitch(pitch);

        angleDirty = true;
    }


    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        positionDirty = true;
    }

    /**
     * @return The CURRENT teleport ID as the server has told it to us, doesn't account for latency.
     */
    public int getCurrentTeleportID() {
        return currentTeleportID;
    }

    /**
     * @return The CURRENT window ID as the server has told it to us, doesn't account for latency.
     */
    public int getCurrentWindowID() {
        return currentWindowID;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public float getHealth() {
        return health;
    }

    public int getHunger() {
        return hunger;
    }

    public float getSaturation() {
        return saturation;
    }

    /**
     * @return Time since last packet, in milliseconds.
     */
    public int getTSLP() {
        if (!isConnected()) return 0;
        return (int)(System.currentTimeMillis() - lastPacketTime);
    }

    public int getServerPing() {
        return serverPing;
    }

    /**
     * @return Estimated server tickrate.
     */
    public float getServerTPS() {
        return serverTPS;
    }

    /* ------------------------------ Classes ------------------------------ */

    private class PlayerSessionAdapter extends SessionAdapter {
        @Override
        public void packetReceived(PacketReceivedEvent event) {
            Emitters.ON_PLAYER_PACKET_IN.emit(new Emitters.PlayerPacket(Player.this, event.getPacket()));

            // Paper has async chat
            if (!(event.getPacket() instanceof ServerChatPacket)) {
                lastPacketTime = System.currentTimeMillis();
            } else {
                // For checking that this actually works lol
                logger.finest(String.format("%s: %s", getUsername(), /* server.hostname, server.port, */
                        Chat.unwrap(((ServerChatPacket)event.getPacket()).getMessage(), true)));
                Emitters.ON_PLAYER_CHAT.emit(new Emitters.PlayerChat(Player.this, // FIXME: Improve chat messages
                        ((ServerChatPacket)event.getPacket()).getMessage().getFullText()));
            }

            if (event.getPacket() instanceof ServerPlayerPositionRotationPacket) {
                ServerPlayerPositionRotationPacket packet = event.getPacket();

                position.setX(packet.getX());
                position.setY(packet.getY());
                position.setZ(packet.getZ());
                angle.setYaw(packet.getYaw());
                angle.setPitch(packet.getPitch());

                if (currentTeleportID < 0) {
                    logger.finer(String.format("%s logged in at xyz: %.1f, %.1f, %.1f dim: %s.", getUsername(),
                            position.getX(), position.getY(), position.getZ(), dimension));

                    // We will confirm the first teleport, but subsequent ones are handled elsewhere
                    session.send(new ClientTeleportConfirmPacket(packet.getTeleportId()));
                }
                currentTeleportID = packet.getTeleportId();

            } else if (event.getPacket() instanceof ServerPlayerHealthPacket) {
                ServerPlayerHealthPacket packet = event.getPacket();

                if (health != packet.getHealth() || hunger != packet.getFood() || saturation != packet.getSaturation())
                    Emitters.ON_PLAYER_HEALTH_UPDATE.emit(Player.this);

                health = packet.getHealth();
                hunger = packet.getFood();
                saturation = packet.getSaturation();

                if (health <= 0.0f) {
                    disconnect("Dead.");
                    Emitters.ON_REPORT.emit(new DeadReport(Player.this));
                    AUTO_RECONNECT.value = false;

                } else if (health <= LOGOUT_HEALTH.value) {
                    disconnect(String.format("Low health (%.1f).", health));
                    Emitters.ON_REPORT.emit(new HealthLogoutReport(Player.this, health));
                    lastAutoLogoutTime = System.currentTimeMillis();

                    if (DISABLE_AUTO_RECONNECT_ON_LOGOUT.value) {
                        logger.info(String.format("Auto reconnect disabled for %s.", getUsername()));
                        AUTO_RECONNECT.value = false;
                    }
                }

            } else if (event.getPacket() instanceof ServerJoinGamePacket) {
                dimension = Dimension.fromMC(((ServerJoinGamePacket)event.getPacket()).getDimension());
                Emitters.ON_PLAYER_POSITION_UPDATE.emit(Player.this);

            } else if (event.getPacket() instanceof ServerRespawnPacket) {
                dimension = Dimension.fromMC(((ServerRespawnPacket)event.getPacket()).getDimension());
                Emitters.ON_PLAYER_POSITION_UPDATE.emit(Player.this);

            } else if (event.getPacket() instanceof ServerPlayerListEntryPacket) {
                ServerPlayerListEntryPacket packet = event.getPacket();

                for (PlayerListEntry entry : packet.getEntries()) {
                    UUID uuid = entry.getProfile().getId();

                    if (uuid.equals(getUUID())) {
                        serverPing = entry.getPing();
                        logger.finer(String.format("%s server ping is %dms.", getUsername(), serverPing));
                        Emitters.ON_PLAYER_SERVER_STATS_UPDATE.emit(Player.this);
                        // break;
                    }
                }

            } else if (event.getPacket() instanceof ServerUpdateTimePacket) {
                ServerUpdateTimePacket packet = event.getPacket();

                if (lastTimeUpdate == -1L) {
                    lastTimeUpdate = System.currentTimeMillis();
                    lastWorldTicks = packet.getWorldAge();
                } else {
                    float newTPS = (packet.getWorldAge() - lastWorldTicks) / ((System.currentTimeMillis() - lastTimeUpdate) / 1000.0f);
                    lastTimeUpdate = System.currentTimeMillis();
                    lastWorldTicks = packet.getWorldAge();
                    if (newTPS <= 0.0f || newTPS >= 1000.0f || !Float.isFinite(newTPS)) return; // Damn

                    tickValues.add(newTPS);
                    while (tickValues.size() > 5) tickValues.remove(0);

                    float old = serverTPS;

                    serverTPS = 0.0f;
                    for (float value : tickValues) serverTPS += value;
                    serverTPS /= tickValues.size();
                    // logger.finest(String.format("%s:%d estimated tickrate: %.1f", server.hostname, server.port, serverTPS));

                    if (old != 0.0f && Math.abs(serverTPS - old) > server.EXTREME_TPS_CHANGE.value) {
                        logger.finer(String.format("%s extreme TPS change, old: %.1f, new: %.1f.", getUsername(),
                                old, serverTPS));
                        Emitters.ON_REPORT.emit(new ExtremeTPSReport(Player.this, old));
                    }
                    Emitters.ON_PLAYER_SERVER_STATS_UPDATE.emit(Player.this);
                }

            } else if (event.getPacket() instanceof ServerOpenWindowPacket) {
                currentWindowID = ((ServerOpenWindowPacket)event.getPacket()).getWindowId();

            } else if (event.getPacket() instanceof ServerWindowItemsPacket) {
                ServerWindowItemsPacket packet = event.getPacket();
                if (packet.getWindowId() >= 0 && packet.getWindowId() <= 100) currentWindowID = packet.getWindowId();

            } else if (event.getPacket() instanceof ServerWindowPropertyPacket) {
                ServerWindowPropertyPacket packet = event.getPacket();
                if (packet.getWindowId() >= 0 && packet.getWindowId() <= 100) currentWindowID = packet.getWindowId();

            } else if (event.getPacket() instanceof ServerSetSlotPacket) {
                ServerSetSlotPacket packet = event.getPacket();
                if (packet.getWindowId() >= 0 && packet.getWindowId() <= 100) currentWindowID = packet.getWindowId();

            } else if (event.getPacket() instanceof ServerCloseWindowPacket) {
                currentWindowID = ((ServerCloseWindowPacket)event.getPacket()).getWindowId();

            } else if (event.getPacket() instanceof ServerChunkDataPacket) {
                ServerChunkDataPacket packet = event.getPacket();
                loadedChunks.add(new ChunkPosition(packet.getColumn().getX(), packet.getColumn().getZ()));
                lastChunkPacket = System.currentTimeMillis(); // FIXME: Moving across chunks, big updates, etc

                Emitters.ON_PLAYER_SERVER_STATS_UPDATE.emit(Player.this);

            } else if (event.getPacket() instanceof ServerUnloadChunkPacket) {
                ServerUnloadChunkPacket packet = event.getPacket();
                loadedChunks.remove(new ChunkPosition(packet.getX(), packet.getZ()));

                Emitters.ON_PLAYER_SERVER_STATS_UPDATE.emit(Player.this);

            } else if (event.getPacket() instanceof ServerSpawnPlayerPacket) {
                ServerSpawnPlayerPacket packet = event.getPacket();

                if (VISUAL_RANGE_LOGOUT.value && !server.isTrusted(packet.getUUID())) {
                    String playerName = yesCom.playersHandler.getName(packet.getUUID(), packet.getUUID().toString());
                    disconnect(String.format("%s entered visual range at xyz: %.1f, %.1f, %.1f.", playerName,
                            packet.getX(), packet.getY(), packet.getY()));
                    Emitters.ON_REPORT.emit(new VisualRangeLogoutReport(Player.this, packet.getUUID()));
                    lastAutoLogoutTime = System.currentTimeMillis();

                    if (DISABLE_AUTO_RECONNECT_ON_LOGOUT.value) {
                        logger.info(String.format("Auto reconnect disabled for %s.", getUsername()));
                        AUTO_RECONNECT.value = false;
                    }
                }
            }
        }

        @Override
        public void packetSent(PacketSentEvent event) {
            Emitters.ON_PLAYER_PACKET_OUT.emit(new Emitters.PlayerPacket(Player.this, event.getPacket()));
        }

        @Override
        public void connected(ConnectedEvent event) {
            logger.info(String.format("%s was successfully connected to %s:%d.", getUsername(), server.hostname, server.port));
            Emitters.ON_PLAYER_LOGIN.emit(Player.this);
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            if (event.getCause() != null) {
                logger.warning(event.getReason());
                logger.throwing(getClass().getSimpleName(), "disconnected", event.getCause());
            }

            logger.info(String.format("%s was disconnected for: %s", getUsername(), event.getReason()));
            Emitters.ON_PLAYER_LOGOUT.emit(new Emitters.PlayerLogout(Player.this, event.getReason()));

            lastLoginTime = System.currentTimeMillis();
            if (!isSpawned()) {
                lastLoginTime += server.AUTO_RECONNECT_TIME.value * (long)failedConnections++;
                logger.finer(String.format("%s has %d failed connection attempt(s).", getUsername(), failedConnections));
            }

            loadedChunks.clear();

            dimension = null;

            currentTeleportID = -1;
            currentWindowID = -1;

            tickValues.clear();

            positionDirty = false;
            angleDirty = false;

            serverPing = 0;
            serverTPS = 0.0f;

            lastChunkPacket = System.currentTimeMillis();
            lastTimeUpdate = -1L;
            lastWorldTicks = 0L;

            session = null;
        }
    }
}
