package ez.pogdog.yescom.core.account;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;

/**
 * Represents a Minecraft account.
 */
public interface IAccount {
    /**
     * Logs in the account to the auth service. Use {@link AccountHandler#login(IAccount, AuthenticationService)}
     * instead of this.
     * @param authService The auth service to log in to.
     */
    void login(AuthenticationService authService) throws RequestException;
}
