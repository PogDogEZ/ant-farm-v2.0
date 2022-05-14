package ez.pogdog.yescom.core.account.accounts;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import ez.pogdog.yescom.core.account.IAccount;

import java.util.Objects;

public class Mojang implements IAccount {

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
        }
        authService.login();
    }
}
