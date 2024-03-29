package ez.pogdog.yescom.core.account;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.accounts.Microsoft;
import ez.pogdog.yescom.core.account.accounts.Mojang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles {@link IAccount}s.
 */
public class AccountHandler {

    private final Logger logger = Logging.getLogger("yescom.core.account");

    private final Map<IAccount, Long> accounts = Collections.synchronizedMap(new HashMap<>());
    private final Set<IAccount> firstTime = new HashSet<>();
    private final Pattern accountPattern = Pattern.compile(
            "((?<type>(mojang|microsoft))( *):)?( *)(?<email>\\w.+@(.+\\..+)+)( *):( *)(?<password>.+)"
    );

    private final String accountsFile;

    public AccountHandler(String accountsFile) {
        this.accountsFile = accountsFile;

        try {
            parseAccounts();
        } catch (IOException error) {
            logger.warning("Could not parse accounts file.");
            logger.throwing(getClass().getSimpleName(), "<init>", error);
        }
    }

    private void parseAccounts() throws IOException {
        logger.fine("Parsing accounts file...");
        File accountsFile = new File(this.accountsFile);
        if (!accountsFile.exists() && !accountsFile.createNewFile()) throw new IOException("Could not create accounts file.");

        for (String line : Files.readAllLines(accountsFile.toPath())) {
            Matcher matcher = accountPattern.matcher(line);
            if (matcher.matches()) {
                String type = matcher.group("type");
                String email = matcher.group("email");
                String password = matcher.group("password");

                IAccount account;
                if (type == null || type.equals("microsoft")) {
                    account = new Microsoft(email, password);
                } else {
                    account = new Mojang(email, password);
                }

                addAccount(account);
                // accounts.put(account, System.currentTimeMillis() - 40000);
            }
        }

        logger.fine(String.format("Parsed %d account(s).", accounts.size()));
    }

    /* ------------------------------ Managing accounts ------------------------------ */

    public Set<IAccount> getAccounts() {
        return accounts.keySet();
    }

    /**
     * @return The {@link IAccount}s available for login right now.
     */
    public Set<IAccount> getAvailableAccounts() {
        Set<IAccount> available = new HashSet<>();
        for (Map.Entry<IAccount, Long> entry : accounts.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() > 30000) available.add(entry.getKey());
        }
        return available;
    }

    /**
     * Adds an account to the cache.
     * @param account The account to add.
     */
    public void addAccount(IAccount account) {
        if (!accounts.containsKey(account)) {
            accounts.put(account, System.currentTimeMillis() - 40000);
            firstTime.add(account);
        }
    }

    /**
     * Removes an account from the cache.
     * @param account The account to remove.
     */
    public void removeAccount(IAccount account) {
        if (accounts.containsKey(account)) {
            accounts.remove(account);
            Emitters.ON_ACCOUNT_REMOVED.emit(account);
        }
    }

    public boolean hasAccount(IAccount account) {
        return accounts.containsKey(account);
    }

    /**
     * @return Is the first time the {@link IAccount} is logging in?
     */
    public boolean isFirstTime(IAccount account) {
        return firstTime.contains(account);
    }

    /**
     * Logs in the provided account to the provided {@link AuthenticationService}.
     * @param account The account to log in.
     * @param authService The auth service to log it in to.
     */
    public void login(IAccount account, AuthenticationService authService) throws RequestException {
        if (accounts.getOrDefault(account, System.currentTimeMillis() - 40000) < 30000) return;

        try {
            accounts.put(account, System.currentTimeMillis());
            account.login(authService);
            // Fire this if the account was successfully logged in for the first time
            if (firstTime.contains(account)) Emitters.ON_ACCOUNT_ADDED.emit(account);
            firstTime.remove(account);

        } catch (RequestException error) {
            logger.throwing(getClass().getSimpleName(), "login", error);
            // If this is the first time, remove the account
            if (firstTime.contains(account)) {
                logger.warning("First time account login failed: " + error.getMessage());
                Emitters.ON_ACCOUNT_ERROR.emit(new Emitters.AccountError(account, error));
                removeAccount(account);
            }

            throw error;
        }
    }
}
