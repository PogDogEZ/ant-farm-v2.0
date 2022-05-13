package ez.pogdog.yescom.core.query.invalidmove;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.query.IQueryHandle;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Handles the processing of invalid move queries.
 */
public class InvalidMoveHandle implements IQueryHandle<InvalidMoveQuery> {

	private final Logger logger = Logging.getLogger("yescom.core.query.invalidmove");
	private final YesCom yesCom = YesCom.getInstance();

	private final Set<Player> available = new HashSet<>();

	public InvalidMoveHandle() {
		logger.finer("Connecting emitters...");
		Emitters.ON_LOGIN.connect(this::onLogin);
		Emitters.ON_PACKET_IN.connect(this::onPacketIn);
		Emitters.ON_LOGOUT.connect(this::onLogout);
	}

	@Override
	public void tick() {
		// TODO: Tick
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
		available.add(player);
	}

	private void onPacketIn(Emitters.PlayerPacket playerPacket) {
		if (available.contains(playerPacket.getPlayer())) {
			// TODO: Invalid move stuff
		}
	}

	private void onLogout(Emitters.PlayerLogout playerLogout) {
		available.remove(playerLogout.getPlayer());
	}
}
