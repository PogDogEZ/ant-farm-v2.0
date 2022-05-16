package me.nathan.futureclient.framework.auth.phase;

import me.nathan.futureclient.client.altmanager.AccountException;
import org.json.JSONException;
import org.json.JSONObject;

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

            JSONObject request = new JSONObject();
            request.put("identityToken","XBL3.0 x="+lsToken.uhs+";"+lsToken.token);

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

            JSONObject json;
            try {
                json = new JSONObject(lines);
            } catch (JSONException error) {
                throw new AccountException("Error requesting xbox login: " + lines);
            }

            return new MCTokenType(json.getString("access_token"), json.getString("username"));
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

            JSONObject json;
            try {
                json = new JSONObject(lines);
            } catch (JSONException error) {
                throw new AccountException("Could not retrieve profile: " + lines);
            }
            if (json.keySet().contains("error"))
                throw new AccountException("Error retrieving profile: "+json.getString("errorMessage"));

            String skinURL = json.getJSONArray("skins").getJSONObject(0).getString("url");
            return new Profile(json.getString("id"), json.getString("name"), skinURL);
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
