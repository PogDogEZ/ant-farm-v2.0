package me.nathan.futureclient.framework.auth.phase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import me.nathan.futureclient.client.altmanager.AccountException;
import org.json.JSONArray;
import org.json.JSONObject;

public class LSToken {

    public static LSTokenType getFor(String token) throws AccountException, IOException {
        try {
            URL url = new URL("https://xsts.auth.xboxlive.com/xsts/authorize");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("POST"); // PUT is another valid option
            http.setDoOutput(true);

            JSONObject request = new JSONObject();
            request.put("RelyingParty","rp://api.minecraftservices.com/");
            request.put("TokenType","JWT");

            JSONObject props = new JSONObject();
            props.put("SandboxId","RETAIL");
            JSONArray userToks = new JSONArray();
            userToks.put(token);
            props.put("UserTokens", userToks);

            request.put("Properties", props);

            String body = request.toString();

            http.setFixedLengthStreamingMode(body.length());
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept","application/json");
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.US_ASCII));
            }

            BufferedReader reader;
            if (http.getResponseCode() == 401) {
                throw new AccountException("xsts_err: User has no XBox Live account, or account is invalid.");
            }

            if (http.getResponseCode() != 200) {
                reader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
            }
            String lines = reader.lines().collect(Collectors.joining());

            JSONObject json = new JSONObject(lines);
            if (json.keySet().contains("error")) {
                throw new AccountException(json.getString("error") + ": " + json.getString("error_description"));
            }
            String uhs = ((JSONObject)((JSONObject)json.get("DisplayClaims")).getJSONArray("xui").get(0)).getString("uhs");
            return new LSTokenType(json.getString("Token"), uhs);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static class LSTokenType {
        public String token;
        public String uhs;
        public LSTokenType(String t, String u) {
            token=t;
            uhs=u;
        }
    }
}
