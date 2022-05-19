package me.nathan.futureclient.framework.auth.phase;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import ez.pogdog.yescom.api.Globals;
import me.nathan.futureclient.client.altmanager.AccountException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class XBLToken {

    public static XBLTokenType getForUserPass(String token) throws AccountException, IOException {
        try {
            URL url = new URL("https://user.auth.xboxlive.com/user/authenticate");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setDoOutput(true);

            JsonObject request = new JsonObject();
            request.add("RelyingParty", new JsonPrimitive("http://auth.xboxlive.com"));
            request.add("TokenType", new JsonPrimitive("JWT"));

            JsonObject props = new JsonObject();
            props.add("AuthMethod", new JsonPrimitive("RPS"));
            props.add("SiteName", new JsonPrimitive("user.auth.xboxlive.com"));
            props.add("RpsTicket", new JsonPrimitive(token));

            request.add("Properties", props);

            String body = request.toString();

            http.setFixedLengthStreamingMode(body.length());
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept","application/json");
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.US_ASCII));
            }

            BufferedReader reader;

            if (http.getResponseCode() != 200) {
                reader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
            }
            String lines = reader.lines().collect(Collectors.joining());

            JsonObject json = Globals.JSON.parse(lines).getAsJsonObject();
            if (json.has("error")) {
                throw new AccountException(json.get("error").getAsString() + ": " + json.get("error_description").getAsString());
            }
            String uhs = json.get("DisplayClaims").getAsJsonObject().get("xui").getAsJsonArray().get(0).getAsJsonObject().get("uhs").getAsString();
            return new XBLTokenType(json.get("Token").getAsString(), uhs);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static class XBLTokenType {
        public String token;
        public String uhs;
        public XBLTokenType(String t, String u) {
            token=t;
            uhs=u;
        }
    }
}
