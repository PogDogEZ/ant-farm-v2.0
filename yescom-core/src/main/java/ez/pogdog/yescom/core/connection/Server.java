package ez.pogdog.yescom.core.connection;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;
import ez.pogdog.yescom.core.query.invalidmove.InvalidMoveHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Represents a server that we are connected to. We can be connected to multiple servers.
 */
public class Server implements IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.connection");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Boolean> DIGGING_ENABLED = new Option<>(true);
    public final Option<Boolean> INVALID_MOVE_ENABLED = new Option<>(true);

    /**
     * Global login time, in milliseconds. All players are restricted to this.
     */
    public final Option<Integer> GLOBAL_LOGIN_TIME = new Option<>(8000);

    /**
     * On a per-player basis. How often to attempt to auto reconnect to the server.
     */
    public final Option<Integer> AUTO_RECONNECT_TIME = new Option<>(4000);

    /**
     * The amount of time to wait before reconnecting after an automatic logout.
     */
    public final Option<Integer> AUTO_LOGOUT_RECONNECT_TIME = new Option<>(120000); // 2 minutes by default

    /**
     * The TPS variation that would be reported as extreme.
     */
    public final Option<Float> EXTREME_TPS_CHANGE = new Option<>(6.0f);

    /**
     * The TSLP, in milliseconds that would be reported as high.
     */
    public final Option<Integer> HIGH_TSLP = new Option<>(1000);

    // TODO: Packet loss and latency perhaps?

    /* ------------------------------ Other fields ------------------------------ */

    public final List<Player> players = new ArrayList<>();
    public final List<IQueryHandle<?>> handles = new ArrayList<>();
    public final List<UUID> trustedPlayers = new ArrayList<>(Arrays.asList( // TODO: Make this more global
            UUID.fromString("2a802c42-c2b9-4ea3-b9e7-5c3d8618fb8f"), // node3112
            UUID.fromString("761a71ca-8aa5-4659-b170-0b98a6e22b26"), // myristicin
            UUID.fromString("9111723e-e7fd-4a51-beaa-3ff3690f912b"), // node3114
            UUID.fromString("dac18c24-9497-48a6-9b3d-966461c09bff"), // DiabolicalHacker
            UUID.fromString("9ebb3926-6499-4db9-8990-f71c3d0127da"), // InvalidMove
            UUID.fromString("9c32a1e6-2558-49fe-a66c-fd5788d18265"), // Tom_Scott
            UUID.fromString("89a905ea-78bb-4f62-8d72-0ff6b2cbf61f") // ilovephantom
    ));

    public final Map<UUID, String> playerNames = new ConcurrentHashMap<>(); // Mapping of UUID -> name for quick lookup

    public final String hostname;
    public final int port;

    private float tickrate;
    private float tslp;
    private float ping;

    private float queriesPerSecond;
    private int waitingSize;
    private int processingSize;

    private long lastLoginTime;

    private long lastStatsTime;

    public Server(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;

        lastLoginTime = System.currentTimeMillis() - GLOBAL_LOGIN_TIME.value;

        Emitters.ON_ACCOUNT_ADDED.connect(this::onAccountAdded);
        List<IAccount> accounts = yesCom.accountHandler.getAccounts();
        if (!accounts.isEmpty()) {
            logger.finer(String.format("Adding %d account(s) to %s:%d...", accounts.size(), hostname, port));
            for (IAccount account : accounts) players.add(new Player(this, account));
        }
        logger.finer(String.format("Server %s:%d has %d usable player(s).", hostname, port, players.size()));

        handles.add(new InvalidMoveHandle(this));

        lastStatsTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("Server(host=%s, port=%d, players=%d, tps=%.1f, ping=%.1f, qps=%.1f)", hostname, port,
                players.size(), tickrate, ping, queriesPerSecond);
    }

    /* ------------------------------ Events ------------------------------ */

    private void onAccountAdded(IAccount account) {
        for (Player player : players) {
            if (player.getAccount().equals(account)) return;
        }

        players.add(new Player(this, account));
    }

	/*
	private void onAccountRemoved(IAccount account) {

	}
	 */

    /**
     * Ticks this server.
     */
    public void tick() {
        tickrate = 0.0f;
        tslp = 0.0f;
        ping = 0.0f;

        for (Player player : players) {
            player.tick();
            tickrate += player.getServerTPS();
            tslp += player.getTSLP();
            ping += player.getServerPing();
        }

        if (!players.isEmpty()) {
            tickrate /= players.size();
            tslp /= players.size();
            ping /= players.size();
        }

        queriesPerSecond = 0.0f;
        waitingSize = 0;
        processingSize = 0;
        // TODO: Calculate loss rates too

        for (IQueryHandle<?> handle : handles) {
            handle.tick();
            queriesPerSecond += handle.getQPS();
            waitingSize += handle.getWaitingSize();
            processingSize += handle.getProcessingSize();
        }

        if (!handles.isEmpty()) queriesPerSecond /= handles.size();

        if (System.currentTimeMillis() - lastStatsTime > 60000) {
            logger.finer(String.format("Server %s:%d stats: %d player(s), %d/%d queries, %.1f tps, %.1f ping, %.1f qps.",
                    hostname, port, players.size(), processingSize, waitingSize, tickrate, ping, queriesPerSecond));
            lastStatsTime = System.currentTimeMillis();
        }
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
        return trustedPlayers.contains(uuid);
    }

    /* ------------------------------ Setters and getters ------------------------------ */

    /**
     * @return The current server tickrate.
     */
    public float getTickrate() {
        return tickrate;
    }

    /**
     * @return The TSLP for all players connected to this server.
     */
    public float getTSLP() {
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
    public float getQPS() {
        return queriesPerSecond;
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
}
