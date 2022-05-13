package ez.pogdog.yescom.core.account.accounts;

import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import ez.pogdog.yescom.core.account.IAccount;

import java.util.Objects;

public class Microsoft implements IAccount {

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
    public void login(AuthenticationService authService) throws RequestException {
        // TODO: Microsoft account login
    }
}
