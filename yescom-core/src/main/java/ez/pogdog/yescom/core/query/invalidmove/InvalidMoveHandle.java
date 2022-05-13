package ez.pogdog.yescom.core.query.invalidmove;

import com.github.steveice10.packetlib.packet.Packet;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.BlockPosition;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.query.IQuery;
import ez.pogdog.yescom.core.query.IQueryHandle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Handles the processing of invalid move queries.
 */
public class InvalidMoveHandle implements IQueryHandle<InvalidMoveQuery>, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Boolean> ARZI_MODE = new Option<>(true);
    public final Option<Boolean> WID_RESYNC = new Option<>(true);

    /* ------------------------------ Other fields ------------------------------ */

    private final Map<Player, PlayerHandle> available = new HashMap<>();

    public final Server server;

    public InvalidMoveHandle(Server server) {
        logger.fine(String.format("New invalid move handle for server %s:%d.", server.hostname, server.port));
        this.server = server;

        for (Player player : this.server.players) available.put(player, new PlayerHandle(player));
        logger.finer(String.format("%d players available.", available.size()));

        logger.finer("Connecting emitters...");
        Emitters.ON_LOGIN.connect(this::onLogin);
        Emitters.ON_PACKET_IN.connect(this::onPacketIn);
        Emitters.ON_LOGOUT.connect(this::onLogout);
    }

    @Override
    public void tick() {
        for (PlayerHandle playerHandle : available.values()) playerHandle.tick();
    }

    @Override
    public boolean handles(IQuery<?> query) {
        return query instanceof InvalidMoveQuery;
    }

    @Override
    public void dispatch(InvalidMoveQuery query) {
        // TODO: Dispatching queries
    }

    @Override
    public void cancel(InvalidMoveQuery query) {
        // TODO: Cancelling queries
    }

    /* ------------------------------ Events ------------------------------ */

    private void onLogin(Player player) {
        if (!available.containsKey(player)) available.put(player, new PlayerHandle(player));
    }

    private void onPacketIn(Emitters.PlayerPacket playerPacket) {
        if (available.containsKey(playerPacket.getPlayer()))
            available.get(playerPacket.getPlayer()).onPacketIn(playerPacket.getPacket());
    }

    private void onLogout(Emitters.PlayerLogout playerLogout) {
        if (available.containsKey(playerLogout.getPlayer())) {
            PlayerHandle playerHandle = available.get(playerLogout.getPlayer());
            available.remove(playerLogout.getPlayer());
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
        private final Map<Integer, Integer> windowToTPIDMap = new HashMap<>();

        private final Player player;

        private BlockPosition bestStorage;

        public PlayerHandle(Player player) {
            logger.finer(String.format("Registered new invalid move handle for player %s.", player.getUsername()));
            this.player = player;

            bestStorage = null;
        }

        private void tick() {
            // TODO: Ticking stuff
            if (ARZI_MODE.value) {
            }
        }

        private void onPacketIn(Packet packet) {
            // TODO: Invalid move stuff
        }

        private void onLogout() {
            logger.finer(String.format("Unregistered invalid move handle for player %s.", player.getUsername()));

            logger.finer(String.format("Rescheduling %d queries.", queryMap.size()));
            for (InvalidMoveQuery query : queryMap.values()) InvalidMoveHandle.this.dispatch(query);
            queryMap.clear();
        }

        /* ------------------------------ Public API ------------------------------ */

        /**
         * @return Can this player dispatch queries?
         */
        public boolean canQuery() { // TODO: Take into account current query map size, etc...
            return bestStorage != null;
        }
    }
}
