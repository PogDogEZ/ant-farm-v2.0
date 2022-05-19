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

public class MCToken {

    public static MCTokenType getFor(LSToken.LSTokenType lsToken) throws AccountException, IOException {
        try {
            URL url = new URL("https://api.minecraftservices.com/authentication/login_with_xbox");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("POST"); // PUT is another valid option
            http.setDoOutput(true);

            JsonObject request = new JsonObject();
            request.add("identityToken", new JsonPrimitive("XBL3.0 x="+lsToken.uhs+";"+lsToken.token));

            String body = request.toString();

            http.setFixedLengthStreamingMode(body.length());
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Host","api.minecraftservices.com");
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

            JsonObject json;
            try {
                json = Globals.JSON.parse(lines).getAsJsonObject();
            } catch (Exception error) {
                throw new AccountException("Error requesting xbox login: " + lines);
            }

            return new MCTokenType(json.get("access_token").getAsString(), json.get("username").getAsString());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static Profile getProfile(MCTokenType minecraftToken) throws AccountException, IOException {
        try {
            URL url = new URL("https://api.minecraftservices.com/minecraft/profile");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("GET");

            http.setRequestProperty("Authorization", "Bearer "+minecraftToken.accessToken);
            http.setRequestProperty("Host","api.minecraftservices.com");
            http.connect();

            BufferedReader reader;
            if (http.getResponseCode()!=200) {
                reader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
            }
            String lines = reader.lines().collect(Collectors.joining());

            JsonObject json;
            try {
                json = Globals.JSON.parse(lines).getAsJsonObject();
            } catch (Exception error) {
                throw new AccountException("Could not retrieve profile: " + lines);
            }
            if (json.has("error"))
                throw new AccountException("Error retrieving profile: " + json.get("errorMessage").getAsString());

            String skinURL = json.get("skins").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
            return new Profile(json.get("id").getAsString(), json.get("name").getAsString(), skinURL);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static class Profile {
        public String uuid;
        public String name;
        public String skinURL;
        public Profile(String a, String b, String c){
            uuid=a;
            name=b;
            skinURL=c;
        }
    }

    public static class MCTokenType {
        public String accessToken;
        public String username;
        public MCTokenType(String a, String b) {
            accessToken=a;
            username=b;
        }
    }
}
