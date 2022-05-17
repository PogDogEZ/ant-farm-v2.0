package ez.pogdog.yescom.core.account.accounts;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.account.IAccount;

import java.util.Objects;
import java.util.logging.Logger;

public class Mojang implements IAccount {

    private final Logger logger = Logging.getLogger("yescom.core.accounts.account");

    private final String email;
    private final String password;

    public Mojang(String email, String password) {
        this.email = email;
        this.password = password;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Mojang mojang = (Mojang)other;
        return email.equals(mojang.email); // && password.equals(mojang.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email /* , password */);
    }

    @Override
    public void login(AuthenticationService authService) throws RequestException {
        if (authService.getUsername() == null) {
            authService.setUsername(email);
            authService.setPassword(password);

            String[] emailSplit = email.split("@");
            String email = String.valueOf(emailSplit[0].charAt(0));
            email += emailSplit[0].substring(1, emailSplit[0].length() - 1).replaceAll("\\w", "*");
            email += String.valueOf(emailSplit[0].charAt(emailSplit[0].length() - 1));
            if (emailSplit.length > 1) email += "@" + emailSplit[1];
            logger.fine(String.format("Authenticating Mojang account %s.", email));
        }
        authService.login();
    }
}
