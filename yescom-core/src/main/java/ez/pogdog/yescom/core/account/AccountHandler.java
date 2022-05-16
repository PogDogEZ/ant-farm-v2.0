package ez.pogdog.yescom.core.account;

import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.account.accounts.Microsoft;
import ez.pogdog.yescom.core.account.accounts.Mojang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles {@link IAccount}s.
 */
public class AccountHandler {

    private final Logger logger = Logging.getLogger("yescom.core.account");

    private final List<IAccount> accounts = new ArrayList<>();
    private final Pattern accountPattern = Pattern.compile(
            "((?<type>(mojang|microsoft))( *):)?( *)(?<email>\\w+@(\\w+\\.\\w+)+)( *):( *)(?<password>\\w+)"
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
                if (type == null || type.equals("mojang")) {
                    account = new Mojang(email, password);
                } else {
                    account = new Microsoft(email, password);
                }

                if (!accounts.contains(account)) accounts.add(account);
            }
        }

        logger.fine(String.format("Parsed %d account(s).", accounts.size()));
    }

    /* ------------------------------ Managing accounts ------------------------------ */

    public List<IAccount> getAccounts() {
        return new ArrayList<>(accounts);
    }

    public void addAccount(IAccount account) {
        if (!accounts.contains(account)) {
            accounts.add(account);
            Emitters.ON_ACCOUNT_ADDED.emit(account);
        }
    }

    public void removeAccount(IAccount account) {
        if (accounts.contains(account)) {
            accounts.remove(account);
            Emitters.ON_ACCOUNT_REMOVED.emit(account);
        }
    }
}
