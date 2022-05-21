package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.PlayerInfo;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;

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
public class PlayersHandler implements IConfig, ISerialiser {

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

    private final Map<UUID, PlayerInfo> playerCache = Collections.synchronizedMap(new ConcurrentHashMap<>());
    private final Map<UUID, File> sessionFilesCache = new HashMap<>();

    public PlayersHandler() {
        yesCom.dataHandler.serialisers.add(this);
    }

    @Override
    public String getIdentifier() {
        return "players";
    }

    @Override
    public IConfig getParent() {
        return YesCom.getInstance();
    }

    @Override
    public void load(File dataDirectory) throws IOException {
        loadPlayerCache(dataDirectory);
        loadSessionFilesCache(dataDirectory);
    }

    @Override
    public void save(File dataDirectory) throws IOException {
        savePlayerCache(dataDirectory);

        Set<PlayerInfo> dirty = new HashSet<>();
        for (PlayerInfo info : playerCache.values()) {
            if (!info.sessions.isEmpty()) dirty.add(info);
        }
        saveDirtySession(dataDirectory, dirty);
        for (PlayerInfo info : dirty) info.sessions.clear(); // Servers don't take up enough space to care about
        // saveSessionFilesCache(dataDirectory);
    }

    /* ------------------------------ Serialisation ------------------------------ */

    /**
     * Reads the basic player cache (UUID, username and skin URL) from the local database.
     */
    private void loadPlayerCache(File dataDirectory) throws IOException {
        File playersFile = new File(dataDirectory, "players.bin");
        if (!playersFile.exists() && !playersFile.createNewFile()) throw new IOException("Could not create players file.");

        logger.finer("Reading player cache...");
        long start = System.currentTimeMillis();
        InputStream inputStream = new FileInputStream(playersFile);
        int count = 0;
        try {
            count = Serial.Read.readInteger(inputStream);
        } catch (EOFException ignored) { // First read will be empty if the file didn't exist
        }
        for (int index = 0; index < count; ++index) {
            PlayerInfo info = Serial.Read.readPlayerInfo(inputStream);
            playerCache.put(info.uuid, info);
        }
        inputStream.close();
        logger.finer(String.format("Read %d player cache entries in %dms.", yesCom.playersHandler.getPlayerCache().size(),
                System.currentTimeMillis() - start));
    }

    /**
     * Saves the basic player cache (UUID, username and skin URL) to the local database.
     */
    private void savePlayerCache(File dataDirectory) throws IOException {
        File playersFile = new File(dataDirectory, "players.bin");
        if (!playersFile.exists() && !playersFile.createNewFile()) throw new IOException("Could not create players file.");

        logger.finer("Writing player cache...");
        long start = System.currentTimeMillis();
        OutputStream outputStream = new FileOutputStream(playersFile);
        Serial.Write.writeInteger(playerCache.size(), outputStream);
        for (PlayerInfo info : playerCache.values()) Serial.Write.writePlayerInfo(info, outputStream);
        outputStream.close();
        logger.finer(String.format("Wrote %d player cache entries in %dms.", playerCache.size(),
                System.currentTimeMillis() - start));
    }

    private void loadSessionFilesCache(File dataDirectory) throws IOException {
        File sessionsDirectory = new File(dataDirectory, "sessions");
        if (!sessionsDirectory.exists() && !sessionsDirectory.mkdirs())
            throw new IOException("Could not create sessions directory.");

        logger.finer("Indexing player sessions cache...");
        File[] files = sessionsDirectory.listFiles();
        if (files == null) throw new IOException("Session directory file listing is null.");

        long start = System.currentTimeMillis();
        for (File file : files) {
            if (file.isDirectory()) continue; // Skip obvious junk

            try {
                // Skipping the session data, for time saving, would be annoying and probably wouldn't save *that much*
                // time, so we'll just read it fully, sorry lol
                for (PlayerInfo info : readSessionFile(file, Collections.emptyMap())) sessionFilesCache.put(info.uuid, file);

            } catch (IOException error) {
                logger.warning(String.format("Session file %s might be corrupt: %s", file, error));
                logger.throwing(getClass().getSimpleName(), "loadSessionFilesCache", error);
            }
        }
        logger.finer(String.format("Indexed %d sessions from %d files in %dms.", sessionFilesCache.size(), files.length,
                System.currentTimeMillis() - start));
    }

    /**
     * Reads a single session file and returns the {@link PlayerInfo} stubs from it.
     */
    private List<PlayerInfo> readSessionFile(File file, Map<UUID, PlayerInfo> infos) throws IOException {
        List<PlayerInfo> stubs = new ArrayList<>();

        InputStream inputStream = new FileInputStream(file);
        int count = Serial.Read.readInteger(inputStream);
        for (int index = 0; index < count; ++index) stubs.add(Serial.Read.readSessions(inputStream, infos));
        inputStream.close();

        return stubs;
    }

    private void saveDirtySession(File dataDirectory, Set<PlayerInfo> infos) throws IOException {
        File sessionsDirectory = new File(dataDirectory, "sessions");
        if (!sessionsDirectory.exists() && !sessionsDirectory.mkdirs())
            throw new IOException("Could not create sessions directory.");

        logger.finer(String.format("Writing %d dirty session infos...", infos.size()));
        long start = System.currentTimeMillis();

        Map<UUID, PlayerInfo> newlySaved = new HashMap<>();
        Map<File, Map<UUID, PlayerInfo>> toOverwrite = new HashMap<>();

        for (PlayerInfo info : infos) {
            if (sessionFilesCache.containsKey(info.uuid)) {
                toOverwrite.computeIfAbsent(sessionFilesCache.get(info.uuid), file -> new HashMap<>()).put(info.uuid, info);
            } else {
                newlySaved.put(info.uuid, info);
            }
        }

        if (!newlySaved.isEmpty()) {
            for (int index = 0; index < 1000; ++index) {
                File newFile = new File(sessionsDirectory, String.format("%x.bin", Math.abs(newlySaved.hashCode() + index)));
                if (!newFile.exists()) {
                    writeSessionFile(newFile, new ArrayList<>(newlySaved.values()));
                    for (UUID uuid : newlySaved.keySet()) sessionFilesCache.put(uuid, newFile);
                    break;
                }
            }
        }

        for (Map.Entry<File, Map<UUID, PlayerInfo>> entry : toOverwrite.entrySet()) {
            List<PlayerInfo> stubs = readSessionFile(entry.getKey(), entry.getValue());
            writeSessionFile(entry.getKey(), stubs);
        }

        logger.finer(String.format("Wrote %d infos with dirty sessions into %d files in %dms.", infos.size(),
                toOverwrite.size() + (newlySaved.isEmpty() ? 0 : 1), System.currentTimeMillis() - start));
    }

    private void writeSessionFile(File sessionFile, List<PlayerInfo> stubs) throws IOException {
        if (!sessionFile.exists() && !sessionFile.createNewFile())
            throw new IOException(String.format("Could not create new session file %s.", sessionFile));

        OutputStream outputStream = new FileOutputStream(sessionFile);
        Serial.Write.writeInteger(stubs.size(), outputStream);
        for (PlayerInfo info : stubs) Serial.Write.writeSessions(info, outputStream);
        outputStream.close();
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
            info = new PlayerInfo(uuid, System.currentTimeMillis());
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

    public PlayerInfo getInfo(UUID uuid) {
        return getInfo(uuid, null, null, -1, null);
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
