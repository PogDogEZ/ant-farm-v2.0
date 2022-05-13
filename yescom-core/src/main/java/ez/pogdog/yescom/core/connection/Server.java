package ez.pogdog.yescom.core.connection;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.IAccount;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Represents a server that we are connected to. We can be connected to multiple servers.
 */
public class Server implements IConfig {

	private final Logger logger = Logging.getLogger("yescom.core.connection");
	private final YesCom yesCom = YesCom.getInstance();

	/* ------------------------------ Options ------------------------------ */

	/**
	 * Global login time, in milliseconds. All players are restricted to this.
	 */
	public final Option<Integer> GLOBAL_LOGIN_TIME = new Option<>(8000);

	/**
	 * On a per-player basis. Essentially how often the player requests to login.
	 */
	public final Option<Integer> PLAYER_LOGIN_TIME = new Option<>(4000);

	/* ------------------------------ Other fields ------------------------------ */

	public final List<Player> players = new ArrayList<>();

	public final String hostname;
	public final int port;

	private long lastLoginTime;

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
		for (Player player : players) player.tick();
	}

	/* ------------------------------ Public API ------------------------------ */

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
}
