package ez.pogdog.yescom.core.account.accounts;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.core.account.IAccount;
import me.nathan.futureclient.client.altmanager.AccountException;
import me.nathan.futureclient.framework.auth.phase.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public class Microsoft implements IAccount {

    private final Logger logger = Logging.getLogger("yescom.account.accounts");

    private final String email;
    private final String password;

    public Microsoft(String email, String password) {
        this.email = email;
        this.password = password;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Microsoft microsoft = (Microsoft)other;
        return email.equals(microsoft.email) && password.equals(microsoft.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, password);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void login(AuthenticationService authService) throws RequestException {
        if (authService.getUsername() == null) {
            try {
                String code = Code.getInitialCode(email, password, String.valueOf(UUID.randomUUID()));
                MSToken.TokenPair msToken = MSToken.getForUserPass(code);
                XBLToken.XBLTokenType xblToken = XBLToken.getForUserPass(msToken.token);
                LSToken.LSTokenType lsToken = LSToken.getFor(xblToken.token);
                MCToken.MCTokenType minecraftToken = MCToken.getFor(lsToken);
                MCToken.Profile profile = MCToken.getProfile(minecraftToken);

                GameProfile gameProfile = new GameProfile(
                        UUID.fromString(profile.uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                                "$1-$2-$3-$4-$5")),
                        profile.name
                );

                authService.setUsername(profile.name);
                authService.setAccessToken(minecraftToken.accessToken);

                Field loggedInField = AuthenticationService.class.getDeclaredField("loggedIn");
                loggedInField.setAccessible(true);
                loggedInField.setBoolean(authService, true);

                Field idField = AuthenticationService.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(authService, minecraftToken.username);

                Field profilesField = AuthenticationService.class.getDeclaredField("profiles");
                profilesField.setAccessible(true);
                List<GameProfile> profiles = (List<GameProfile>)profilesField.get(authService);
                profiles.add(gameProfile);

                Field selectedProfileField = AuthenticationService.class.getDeclaredField("selectedProfile");
                selectedProfileField.setAccessible(true);
                selectedProfileField.set(authService, gameProfile);

            } catch (Exception error) {
                logger.throwing(Microsoft.class.getSimpleName(), "login", error);
                throw new RequestException(error);
            }
        }
    }
}
