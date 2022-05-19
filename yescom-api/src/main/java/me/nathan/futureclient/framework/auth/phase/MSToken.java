package me.nathan.futureclient.framework.auth.phase;

import com.google.gson.JsonObject;
import ez.pogdog.yescom.api.Globals;
import me.nathan.futureclient.client.altmanager.AccountException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class MSToken {
    public static TokenPair getFor(String authCode) throws AccountException, IOException {
        try {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("client_id", "83fba303-ac6f-4c76-831a-c908108f13c6");
            arguments.put("client_secret", "PQF7Q~B4uVSljll6GogkZvNA~01wN_n~rUPd4");
            arguments.put("code", authCode);
            arguments.put("grant_type", "authorization_code");
            arguments.put("redirect_uri", "https://pogdog.azurewebsites.net/.auth/login/aad/callback");
            StringJoiner sj = new StringJoiner("&");
            for (Map.Entry<String, String> entry : arguments.entrySet())
                sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                        + URLEncoder.encode(entry.getValue(), "UTF-8"));
            byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
            int length = out.length;

            URL url = new URL("https://login.live.com/oauth20_token.srf");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("POST");
            http.setDoOutput(true);

            http.setFixedLengthStreamingMode(length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(out);
            }

            BufferedReader reader;
            if (http.getResponseCode()!=200) {
                reader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
            }
            String lines = reader.lines().collect(Collectors.joining());

            JsonObject json = Globals.JSON.parse(lines).getAsJsonObject();
            if (json.keySet().contains("error")) {
                throw new AccountException(json.get("error").getAsString() + ": " + json.get("error_description").getAsString());
            }
            return new TokenPair(json.get("access_token").getAsString(), json.get("refresh_token").getAsString());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static TokenPair getForUserPass(String authCode) throws AccountException, IOException {
        try {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("client_id", "00000000402b5328");
            arguments.put("code", authCode);
            arguments.put("grant_type", "authorization_code");
            arguments.put("redirect_uri", "https://login.live.com/oauth20_desktop.srf");
            arguments.put("scope","service::user.auth.xboxlive.com::MBI_SSL");
            StringJoiner sj = new StringJoiner("&");
            for (Map.Entry<String, String> entry : arguments.entrySet())
                sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                        + URLEncoder.encode(entry.getValue(), "UTF-8"));
            byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
            int length = out.length;

            URL url = new URL("https://login.live.com/oauth20_token.srf");
            URLConnection con = url.openConnection();
            HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("POST"); // PUT is another valid option
            http.setDoOutput(true);


            http.setFixedLengthStreamingMode(length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            http.connect();
            try (OutputStream os = http.getOutputStream()) {
                os.write(out);
            }

            BufferedReader reader;
            if (http.getResponseCode()!=200) {
                reader = new BufferedReader(new InputStreamReader(http.getErrorStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(http.getInputStream()));
            }
            String lines = reader.lines().collect(Collectors.joining());

            JsonObject json = Globals.JSON.parse(lines).getAsJsonObject();
            if (json.has("error")) {
                throw new AccountException(json.get("error").getAsString() + ": " + json.get("error_description").getAsString());
            }
            return new TokenPair(json.get("access_token").getAsString(), json.get("refresh_token").getAsString());
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
    public static class TokenPair {
        public String token;
        public String refreshToken;

        public TokenPair(String tok, String rtok) {
            token=tok;
            refreshToken=rtok;
        }
    }
}
