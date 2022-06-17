package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.Session;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles stuff to do with players (trusted, UUID to name maps).
 */
public class PlayersHandler implements IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.connection");
    private final YesCom yesCom = YesCom.getInstance();

    @Option.Ungettable
    @Option.Unsettable
    public final Option<List<UUID>> TRUSTED_PLAYERS = new Option<>( // Uh, I'm not lazy, I swear
            "Trusted players",
            "Players (by UUID) that are exempt from certain checks.",
            new ArrayList<>(Arrays.asList(
                    UUID.fromString("2a802c42-c2b9-4ea3-b9e7-5c3d8618fb8f"), // node3112
                    UUID.fromString("761a71ca-8aa5-4659-b170-0b98a6e22b26"), // myristicin
                    UUID.fromString("9111723e-e7fd-4a51-beaa-3ff3690f912b"), // node3114
                    UUID.fromString("dac18c24-9497-48a6-9b3d-966461c09bff"), // DiabolicalHacker
                    UUID.fromString("9ebb3926-6499-4db9-8990-f71c3d0127da"), // InvalidMove
                    UUID.fromString("9c32a1e6-2558-49fe-a66c-fd5788d18265"), // Tom_Scott
                    UUID.fromString("89a905ea-78bb-4f62-8d72-0ff6b2cbf61f") // ilovephantom
            ))
    );

    private final Map<UUID, PlayerInfo> playerCache = Collections.synchronizedMap(new HashMap<>());

    @Override
    public String getIdentifier() {
        return "players";
    }

    @Override
    public IConfig getParent() {
        return YesCom.getInstance();
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * @return Is the UUID of the player trusted?
     */
    public boolean isTrusted(UUID uuid) {
        return TRUSTED_PLAYERS.value.contains(uuid);
    }

    public void addTrusted(UUID uuid) {
        if (!TRUSTED_PLAYERS.value.contains(uuid)) {
            TRUSTED_PLAYERS.value.add(uuid);
            if (playerCache.containsKey(uuid)) Emitters.ON_TRUST_STATE_CHANGED.emit(playerCache.get(uuid));
        }
    }

    public void removeTrusted(UUID uuid) {
        if (TRUSTED_PLAYERS.value.contains(uuid)) {
            TRUSTED_PLAYERS.value.remove(uuid);
            if (playerCache.containsKey(uuid)) Emitters.ON_TRUST_STATE_CHANGED.emit(playerCache.get(uuid));
        }
    }

    /**
     * @return The reference to the internal map (it's a lot faster). PLEASE do not use this incorrectly thanks :pray:.
     */
    public Map<UUID, PlayerInfo> getPlayerCache() {
        return playerCache;
    }

    /**
     * Gets the {@link PlayerInfo} for a given UUID. If the info is not known, it is created.
     * @param uuid The UUID of the player.
     * @param username The username of the player.
     * @param skinURL The URL to the skin of the player.
     * @param gameMode The gamemode of the player.
     * @return The info about the player.
     */
    public PlayerInfo getInfo(UUID uuid, String username, String skinURL, int ping, PlayerInfo.GameMode gameMode) {
        PlayerInfo info = playerCache.get(uuid);
        boolean newCache = info == null;
        if (newCache) {
            info = new PlayerInfo(playerCache.size(), uuid, System.currentTimeMillis());
            playerCache.put(uuid, info);
        }

        // Apply the information we have to the player
        if (username != null) info.username = username;
        if (skinURL != null) info.skinURL = skinURL;
        if (ping > 0) info.ping = ping;
        if (gameMode != null) info.gameMode = gameMode;

        if (newCache) Emitters.ON_NEW_PLAYER_CACHED.emit(info); // Emit once we have all the information about the player
        return info;
    }

    public PlayerInfo getInfo(UUID uuid, String username) {
        return getInfo(uuid, username, null, -1, null);
    }

    public PlayerInfo getInfo(UUID uuid) {
        return getInfo(uuid, null, null, -1, null);
    }

    public PlayerInfo getInfo(int lookupID) {
        for (PlayerInfo info : playerCache.values()) {
            if (info.lookupID == lookupID) return info;
        }
        return null;
    }

    public PlayerInfo getInfo(String username) {
        for (PlayerInfo info : playerCache.values()) {
            if (info.username.equalsIgnoreCase(username)) return info;
        }
        return null;
    }

    /**
     * @return The unique (to this database) lookup ID of a given UUID.
     */
    public int getLookupID(UUID uuid) {
        PlayerInfo info = playerCache.get(uuid);
        if (info == null) return -1;
        return info.lookupID;
    }

    /**
     * @param uuid The UUID to look up.
     * @param defaultValue The default value to return if the UUID is not found.
     * @return The name associated with the UUID.
     */
    public String getName(UUID uuid, String defaultValue) {
        PlayerInfo info = playerCache.get(uuid);
        if (info == null || info.username.isBlank()) return defaultValue;
        return info.username;
    }

    /**
     * @param uuid The UUID to look up.
     * @return The name associated with the UUID, null if not found.
     */
    public String getName(UUID uuid) {
        return getName(uuid, null);
    }
}
