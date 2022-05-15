package ez.pogdog.yescom.core.connection;

import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.game.PlayerListEntry;
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
import com.github.steveice10.mc.protocol.packet.ingame.server.window.*;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerChunkDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUnloadChunkPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
import com.github.steveice10.packetlib.Client;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.*;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpSessionFactory;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.Angle;
import ez.pogdog.yescom.api.data.ChunkPosition;
import ez.pogdog.yescom.api.data.Dimension;
import ez.pogdog.yescom.api.data.Position;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.report.connection.ExtremeTPSReport;
import ez.pogdog.yescom.core.util.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A player that is connected to a {@link Server}.
 */
public class Player {

    private final Logger logger = Logging.getLogger("yescom.core.connection");

    private final Server server;
    private final IAccount account;

    private AuthenticationService authService;
    private Session session;

    /* ------------------------------ Player "stats" ------------------------------ */

    public final List<ChunkPosition> loadedChunks = new ArrayList<>();

    private final Position position = new Position(0.0, 0.0, 0.0);
    private final Angle angle = new Angle(0.0f, 0.0f);
    private boolean onGround = true;

    private int currentTeleportID = -1;
    private int currentWindowID = -1;

    private Dimension dimension = null;

    private float health = 20.0f;
    private int hunger = 20;
    private float saturation = 5.0f;

    private int serverPing = 0; // TODO: Estimated ping as well?
    private float serverTPS = 0.0f; // FIXME: Will this cause issues?

    /* ------------------------------ Modifiable fields ------------------------------ */

    public boolean limited = false; // Limited means that it can't connect automatically

    /* ------------------------------ Internal fields ------------------------------ */

    private final List<Float> tickValues = new ArrayList<>();

    private long lastLoginTime;
    private int failedConnections;

    private boolean positionDirty;
    private boolean angleDirty;

    private long lastPacketTime;
    private long lastTimeUpdate;
    private long lastWorldTicks;

    /**
     * @param server The server that this player belongs to.
     * @param account The account the player will use.
     */
    public Player(Server server, IAccount account) {
        this.server = server;
        this.account = account;

        authService = new AuthenticationService();
        session = null;

        lastLoginTime = System.currentTimeMillis() - this.server.PLAYER_LOGIN_TIME.value;
        failedConnections = 0;

        positionDirty = false;
        angleDirty = false;

        lastPacketTime = System.currentTimeMillis();
        lastTimeUpdate = -1L;
        lastWorldTicks = 0L;
    }

    /**
     * Ticks this player.
     */
    public void tick() {
        // TODO: Account for health and other players entering our visual range
        if (!limited && server.canLogin() && !isConnected() && System.currentTimeMillis() - lastLoginTime > server.PLAYER_LOGIN_TIME.value)
            connect();

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
        }
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Connects this player to the server, if they are not connected already.
     */
    public void connect() {
        if (!isConnected()) {
            lastLoginTime = System.currentTimeMillis();

            try {
                logger.fine(String.format("Connecting %s to %s:%d...", getUsername(), server.hostname, server.port));
                account.login(authService);

                MinecraftProtocol protocol = new MinecraftProtocol(authService.getSelectedProfile(), authService.getAccessToken());
                Client client = new Client(server.hostname, server.port, protocol, new TcpSessionFactory(null));

                session = new TcpClientSession(server.hostname, server.port, protocol, client, null);
                // TODO: Add adapters here, maybe reflectively?
                session.addListener(new SessionReactionAdapter());
                session.connect();

                server.resetLoginTime();
                failedConnections = 0;

            } catch (Exception error) {
                logger.warning(String.format("Failed to connect %s to %s:%d: %s", getUsername(), server.hostname, server.port, error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "connect", error);

                // TODO: Autoreconnect time, don't increment timer if server is actually down
                lastLoginTime += server.PLAYER_LOGIN_TIME.value * (long)failedConnections++; // Don't spam connection attempts
            }
        }
    }

    /**
     * Disconnects this player from the server, if they are connected.
     * @param reason The reason for disconnecting.
     */
    public void disconnect(String reason) {
        if (isConnected()) session.disconnect(reason);
    }

    /**
     * Sends a packet to the server, duh.
     */
    public void send(Packet packet) {
        if (session != null && session.isConnected()) session.send(packet);
    }

    /* ------------------------------ Setters and getters ------------------------------ */

    public IAccount getAccount() {
        return account;
    }

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

    public UUID getUUID() {
        return authService.getSelectedProfile().getId();
    }

    public String getUsername() {
        return authService.getSelectedProfile() == null ? "<not logged in>" : authService.getSelectedProfile().getName();
    }

    public Position getPosition() {
        return position.clone();
    }

    public void setPosition(Position position) {
        synchronized (this) {
            this.position.setX(position.getX());
            this.position.setY(position.getY());
            this.position.setZ(position.getZ());
        }

        positionDirty = true;
    }

    public void setPosition(double x, double y, double z) {
        synchronized (this) {
            position.setX(x);
            position.setY(y);
            position.setZ(z);
        }

        positionDirty = true;
    }

    public Angle getAngle() {
        return angle.clone();
    }

    public void setAngle(Angle angle) {
        synchronized (this) {
            this.angle.setYaw(angle.getYaw());
            this.angle.setPitch(angle.getPitch());
        }

        angleDirty = true;
    }

    public void setAngle(float yaw, float pitch) {
        synchronized (this) {
            angle.setYaw(yaw);
            angle.setPitch(pitch);
        }

        angleDirty = true;
    }


    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        synchronized (this) {
            this.onGround = onGround;
        }

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
        if (session == null) return 0;
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

    private class SessionReactionAdapter extends SessionAdapter {
        @Override
        public void packetReceived(PacketReceivedEvent event) {
            Emitters.ON_PACKET_IN.emit(new Emitters.PlayerPacket(Player.this, event.getPacket()));

            // Paper has async chat
            if (!(event.getPacket() instanceof ServerChatPacket)) {
                lastPacketTime = System.currentTimeMillis();
            } else {
                // For checking that this actually works lol
                //logger.finest(String.format("%s: %s", getUsername(), /* server.hostname, server.port, */
                //        Chat.unwrap(((ServerChatPacket)event.getPacket()).getMessage(), true)));
            }

            synchronized (Player.this) {
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

                    health = packet.getHealth();
                    hunger = packet.getFood();
                    saturation = packet.getSaturation();

                } else if (event.getPacket() instanceof ServerJoinGamePacket) {
                    dimension = Dimension.fromMC(((ServerJoinGamePacket)event.getPacket()).getDimension());

                } else if (event.getPacket() instanceof ServerRespawnPacket) {
                    dimension = Dimension.fromMC(((ServerRespawnPacket)event.getPacket()).getDimension());

                } else if (event.getPacket() instanceof ServerPlayerListEntryPacket) {
                    ServerPlayerListEntryPacket packet = event.getPacket();

                    for (PlayerListEntry entry : packet.getEntries()) {
                        if (entry.getProfile().getId().equals(getUUID())) {
                            serverPing = entry.getPing();
                            break;
                        }
                    }

                } else if (event.getPacket() instanceof ServerUpdateTimePacket) {
                    ServerUpdateTimePacket packet = event.getPacket();

                    if (lastTimeUpdate == -1L) {
                        lastTimeUpdate = System.currentTimeMillis();
                        lastWorldTicks = packet.getWorldAge();
                    } else {
                        float delta = (packet.getWorldAge() - lastWorldTicks) / ((System.currentTimeMillis() - lastTimeUpdate) / 1000.0f);
                        if (serverTPS != 0.0f && Math.abs(delta - serverTPS) > server.EXTREME_TPS_CHANGE.value)
                            Emitters.ON_REPORT.emit(new ExtremeTPSReport(Player.this, delta));

                        tickValues.add(delta);
                        while (tickValues.size() > 5) tickValues.remove(0);
                        lastTimeUpdate = System.currentTimeMillis();
                        lastWorldTicks = packet.getWorldAge();

                        serverTPS = 0.0f;
                        for (float value : tickValues) serverTPS += value;
                        serverTPS /= tickValues.size();
                        // logger.finest(String.format("%s:%d estimated tickrate: %.1f", server.hostname, server.port, serverTPS));
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

                } else if (event.getPacket() instanceof ServerUnloadChunkPacket) {
                    ServerUnloadChunkPacket packet = event.getPacket();
                    loadedChunks.remove(new ChunkPosition(packet.getX(), packet.getZ()));
                }
            }
        }

        @Override
        public void packetSent(PacketSentEvent event) {
            Emitters.ON_PACKET_OUT.emit(new Emitters.PlayerPacket(Player.this, event.getPacket()));
        }

        @Override
        public void connected(ConnectedEvent event) {
            logger.info(String.format("%s was successfully connected to %s:%d.", getUsername(), server.hostname, server.port));
            Emitters.ON_LOGIN.emit(Player.this);
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            if (event.getCause() != null) {
                logger.warning(event.getReason());
                logger.throwing(SessionReactionAdapter.class.getSimpleName(), "disconnected", event.getCause());
            }

            logger.info(String.format("%s was disconnected for: %s", getUsername(), event.getReason()));
            Emitters.ON_LOGOUT.emit(new Emitters.PlayerLogout(Player.this, event.getReason()));

            loadedChunks.clear();

            dimension = null;

            currentTeleportID = -1;
            currentWindowID = -1;

            tickValues.clear();

            lastLoginTime = System.currentTimeMillis();
            positionDirty = false;
            angleDirty = false;

            serverPing = 0;
            serverTPS = 0.0f;

            lastTimeUpdate = -1L;
            lastWorldTicks = 0L;
        }
    }
}
