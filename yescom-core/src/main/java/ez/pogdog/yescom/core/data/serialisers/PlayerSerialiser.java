package ez.pogdog.yescom.core.data.serialisers;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.Session;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.api.data.player.death.Kill;
import ez.pogdog.yescom.core.ITickable;
import ez.pogdog.yescom.core.data.ISerialiser;
import ez.pogdog.yescom.core.data.Serial;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Serialises {@link PlayerInfo} data.
 */
public class PlayerSerialiser implements ISerialiser, ITickable {

    public static final byte[] PLAYER_CACHE_HEADER = new byte[] { 65, 78, 84, 0 };
    public static final byte[] SESSIONS_FILE_HEADER = new byte[] { 65, 78, 84, 2 };
    public static final byte[] DEATHS_FILE_HEADER = new byte[] { 65, 78, 84, 3 };
    public static final byte[] KILLS_FILE_HEADER = new byte[] { 65, 78, 84, 4 };

    private final YesCom yesCom = YesCom.getInstance();
    private final Logger logger = Logging.getLogger("yescom.core.data.serialisers");

    private final Set<AbstractFile<Session>> sessionsFiles = new HashSet<>();
    private final Set<AbstractFile<Death>> deathsFiles = new HashSet<>();
    private final Set<AbstractFile<Kill>> killsFiles = new HashSet<>();

    public PlayerSerialiser() {
        yesCom.slowAsyncUpdater.tickables.add(this);
    }

    @Override
    public synchronized void load(File dataDirectory) throws IOException {
        loadPlayerCache(dataDirectory);

        loadFiles(dataDirectory, "sessions", sessionsFiles, SessionsFile::new);
        loadFiles(dataDirectory, "deaths", deathsFiles, DeathsFile::new);
        loadFiles(dataDirectory, "kills", killsFiles, KillsFile::new);

        /*
        for (AbstractFile<Session> file : sessionsFiles) {
            for (Map.Entry<UUID, Set<Session>> entry : file.readAll().entrySet()) {
                System.out.println(entry.getKey());
                for (Session session : entry.getValue()) System.out.println(session);
            }
        }

        for (AbstractFile<Death> file : deathsFiles) {
            for (Map.Entry<UUID, Set<Death>> entry : file.readAll().entrySet()) {
                System.out.println(entry.getKey());
                for (Death death : entry.getValue()) System.out.println(death);
            }
        }

        for (AbstractFile<Kill> file : killsFiles) {
            for (Map.Entry<UUID, Set<Kill>> entry : file.readAll().entrySet()) {
                System.out.println(entry.getKey());
                for (Kill kill : entry.getValue()) System.out.println(kill);
            }
        }
         */
    }

    @Override
    public synchronized void save(File dataDirectory, boolean force) throws IOException {
        savePlayerCache(dataDirectory);

        saveDirty(dataDirectory, "sessions", sessionsFiles, info -> info.sessions, SessionsFile::new);
        saveDirty(dataDirectory, "deaths", deathsFiles, info -> info.deaths, DeathsFile::new);
        saveDirty(dataDirectory, "kills", killsFiles, info -> info.kills, KillsFile::new);
    }

    @Override
    public /* synchronized */ void tick() {
        // Remove expired sessions caches
        // for (Map.Entry<UUID, SessionsCache> entry : sessionsCache.entrySet()) {
        //     if (System.currentTimeMillis() - entry.getValue().lastAccessTime > 60000) sessionsCache.remove(entry.getKey());
        // }
    }

    /* ------------------------------ Serialisation ------------------------------ */

    private void loadPlayerCache(File dataDirectory) throws IOException {
        File playersFile = new File(dataDirectory, "players.ycom");
        if (!playersFile.exists()) {
            if (!playersFile.createNewFile()) throw new IOException("Could not create players file.");
            return;
        }

        InputStream inputStream = new FileInputStream(playersFile);
        if (!Arrays.equals(PLAYER_CACHE_HEADER, inputStream.readNBytes(4))) throw new IOException("Invalid header check.");

        logger.finer("Reading player cache...");
        long start = System.currentTimeMillis();

        Map<UUID, PlayerInfo> playerCache = yesCom.playersHandler.getPlayerCache();
        int count = Serial.Read.readInteger(inputStream);
        for (int index = 0; index < count; ++index) {
            PlayerInfo info = Serial.Read.readPlayerInfo(inputStream);
            playerCache.put(info.uuid, info);
        }

        inputStream.close();

        logger.finer(String.format("Read %d player cache entries in %dms.", yesCom.playersHandler.getPlayerCache().size(),
                System.currentTimeMillis() - start));
    }

    private <T> void loadFiles(
            File dataDirectory,
            String name,
            Set<AbstractFile<T>> files,
            Function<File, ? extends AbstractFile<T>> constructor
    ) throws IOException {
        File directory = new File(dataDirectory, name);
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException(String.format("Could not create %s directory.", name));

        logger.finer(String.format("Indexing %s file(s)...", name));
        File[] directoryFiles = directory.listFiles();
        if (directoryFiles == null) throw new IOException(String.format("Directory %s file listing is null.", name));

        files.clear();

        long start = System.currentTimeMillis();
        for (File file : directoryFiles) {
            if (!file.isDirectory() && file.getName().endsWith(".ycom")) files.add(constructor.apply(file));
        }

        logger.finest(String.format("Found %d potential %s file(s).", files.size(), name));

        for (AbstractFile<T> file : new ArrayList<>(files)) {
            try {
                file.index();
            } catch (IOException error) {
                logger.warning(String.format("File %s is not a valid %s file: %s", file, name, error.getMessage()));
                logger.throwing(getClass().getSimpleName(), "loadFiles", error);
                files.remove(file); // Don't index this file
            }
        }
        logger.finer(String.format("Indexed %d %s file(s) in %dms.", files.size(), name, System.currentTimeMillis() - start));
    }

    private void savePlayerCache(File dataDirectory) throws IOException {
        File playersFile = new File(dataDirectory, "players.ycom");
        if (!playersFile.exists() && !playersFile.createNewFile()) throw new IOException("Could not create players file.");

        Map<UUID, PlayerInfo> playerCache = yesCom.playersHandler.getPlayerCache();

        logger.finer("Writing player cache...");
        long start = System.currentTimeMillis();

        OutputStream outputStream = new FileOutputStream(playersFile);
        outputStream.write(PLAYER_CACHE_HEADER);
        Serial.Write.writeInteger(playerCache.size(), outputStream);

        for (PlayerInfo info : playerCache.values()) Serial.Write.writePlayerInfo(info, outputStream);

        outputStream.close();

        logger.finer(String.format("Wrote %d player cache entries in %dms.", playerCache.size(),
                System.currentTimeMillis() - start));
    }

    private <T> void saveDirty(
            File dataDirectory,
            String name,
            Set<AbstractFile<T>> files,
            Function<PlayerInfo, Set<T>> getter,
            Function<File, ? extends AbstractFile<T>> constructor
    ) throws IOException {
        File directory = new File(dataDirectory, name);
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException(String.format("Could not create %s directory.", name));

        Set<PlayerInfo> newDirty = new HashSet<>();
        Map<PlayerInfo, AbstractFile<T>> oldDirty = new HashMap<>();

        for (PlayerInfo info : yesCom.playersHandler.getPlayerCache().values()) {
            if (!getter.apply(info).isEmpty()) {
                newDirty.add(info);
                for (AbstractFile<T> file : files) { // Find out if we are already storing this info in a file
                    if (file.offsets.containsKey(info.lookupID)) {
                        newDirty.remove(info);
                        oldDirty.put(info, file);
                        break;
                    }
                }
                // FIXME: Caching
                // sessionsCache.remove(info.uuid); // If we've cached the old version, we need to invalidate it
            }
        }

        if (newDirty.isEmpty() && oldDirty.isEmpty()) return;
        logger.finer(String.format("Saving %d dirty %s info(s).", newDirty.size() + oldDirty.size(), name));

        long start = System.currentTimeMillis();
        for (PlayerInfo info : newDirty) {
            boolean found = false;
            for (AbstractFile<T> file : files) {
                if (file.offsets.size() < 10) {
                    found = true;
                    file.offsets.put(info.lookupID, -1L);
                    // We shouldn't have any elements before the first time we ever saw this player, hopefully
                    if (info.firstSeen < file.minTimestamp) file.minTimestamp = info.firstSeen;
                    for (PlayerInfo.ServerInfo server : info.servers) { // Append new servers if needed
                        if (!file.servers.contains(server)) file.servers.add(server);
                    }
                    oldDirty.put(info, file);
                    break;
                }
            }

            if (!found) { // Need to create a new file
                File file = new File(directory, String.format("%sdata_%x.ycom", name.charAt(0), files.size()));
                for (int index = 0; index < 1000; ++index) {
                    if (!file.exists()) break; // Want to create a new one, so find a valid filename to use
                    file = new File(directory, String.format("%sdata_%x.ycom", name.charAt(0), files.size() + index));
                }
                AbstractFile<T> file1 = constructor.apply(file);
                file1.offsets.put(info.lookupID, -1L); // Offsets will be calculated later
                file1.servers.addAll(info.servers);
                file1.minTimestamp = info.firstSeen;
                files.add(file1);
                oldDirty.put(info, file1);
            }
        }

        Map<AbstractFile<T>, Map<Integer, Set<T>>> toWrite = new HashMap<>();
        newDirty.clear();
        // oldDirty.clear();

        for (Map.Entry<PlayerInfo, AbstractFile<T>> entry : oldDirty.entrySet()) {
            Map<Integer, Set<T>> oldElements;
            if (!toWrite.containsKey(entry.getValue())) {
                if (entry.getValue().offsets.isEmpty()) { // File hasn't been indexed, don't attempt to read
                    oldElements = new HashMap<>();
                } else {
                    oldElements = entry.getValue().readAll(); // I wish lambdas could handle exceptions :(
                }
                toWrite.put(entry.getValue(), oldElements);
            } else {
                oldElements = toWrite.get(entry.getValue());
            }
            // Add new elements
            oldElements.computeIfAbsent(entry.getKey().lookupID, lookupID -> new HashSet<>()).addAll(getter.apply(entry.getKey()));
            getter.apply(entry.getKey()).clear(); // Clear cached
        }
        for (Map.Entry<AbstractFile<T>, Map<Integer, Set<T>>> entry : toWrite.entrySet()) entry.getKey().writeAll(entry.getValue());

        logger.finer(String.format("Saved %d dirty %s info(s) to %d file(s) in %dms.", oldDirty.size(), name, toWrite.size(),
                System.currentTimeMillis() - start));
        oldDirty.clear();
        toWrite.clear();
    }

    /* ------------------------------ Public API ------------------------------ */

    /**
     * Gets the sessions from disk, for the given {@link PlayerInfo}.
     * @param info The player to look up.
     * @return The sessions.
     */
    public synchronized Set<Session> getSessions(PlayerInfo info) {
        // SessionsCache cache = sessionsCache.get(uuid);
        // if (cache != null) {
        //     cache.lastAccessTime = System.currentTimeMillis();
        //     return cache.sessions;
        // }
        Set<Session> sessions = new HashSet<>();
        if (!info.sessions.isEmpty()) sessions.addAll(info.sessions); // Any dirty sessions

        for (AbstractFile<Session> sessionsFile : sessionsFiles) {
            if (sessionsFile.offsets.containsKey(info.lookupID)) {
                try {
                    sessions.addAll(sessionsFile.readFor(info.lookupID));
                    // sessionsCache.put(uuid, new SessionsCache(sessions));
                    return sessions;

                } catch (IOException error) {
                    logger.warning(String.format("Failed to read sessions from file %s for %s: %s", sessionsFile.file,
                            info, error));
                    logger.throwing(getClass().getSimpleName(), "getSessions", error);
                    break;
                }
            }
        }

        return sessions;
    }

    /**
     * Gets the deaths from disk, for the given {@link PlayerInfo}.
     * @param info The player to look up.
     * @return The deaths.
     */
    public synchronized Set<Death> getDeaths(PlayerInfo info) {
        Set<Death> deaths = new HashSet<>();
        if (!info.deaths.isEmpty()) deaths.addAll(info.deaths);

        for (AbstractFile<Death> deathsFile : deathsFiles) {
            if (deathsFile.offsets.containsKey(info.lookupID)) {
                try {
                    deaths.addAll(deathsFile.readFor(info.lookupID));
                    return deaths;
                } catch (IOException error) {
                    logger.warning(String.format("Failed to read deaths from file %s for %s: %s", deathsFile.file,
                            info, error));
                    logger.throwing(getClass().getSimpleName(), "getDeaths", error);
                }
            }
        }

        return deaths;
    }

    /**
     * Gets the kills from disk, for the given {@link PlayerInfo}.
     * @param info The player to look up.
     * @return The kills.
     */
    public synchronized Set<Kill> getKills(PlayerInfo info) {
        Set<Kill> kills = new HashSet<>();
        if (!info.kills.isEmpty()) kills.addAll(info.kills);

        for (AbstractFile<Kill> killsFile : killsFiles) {
            if (killsFile.offsets.containsKey(info.lookupID)) {
                try {
                    kills.addAll(killsFile.readFor(info.lookupID));
                    return kills;
                } catch (IOException error) {
                    logger.warning(String.format("Failed to read kills from file %s for %s: %s", killsFile.file,
                            info, error));
                    logger.throwing(getClass().getSimpleName(), "getKills", error);
                }
            }
        }

        return kills;
    }

    /* ------------------------------ Classes ------------------------------ */

    public static abstract class AbstractFile<T> {

        public final List<PlayerInfo.ServerInfo> servers = new ArrayList<>();
        public final Map<Integer, Long> offsets = new LinkedHashMap<>();

        private final byte[] header;
        protected final File file;

        private long headerSkip;
        private int playersCount; // Stored from the last time we indexed / wrote to the file
        private int serversCount;

        protected long minTimestamp;

        public AbstractFile(byte[] header, File file) {
            this.header = header;
            this.file = file;
        }

        protected abstract Set<T> readElements(InputStream inputStream) throws IOException;
        protected abstract void writeElements(Set<T> elements, OutputStream outputStream) throws IOException;

        /**
         * Checks that the header being read from a binary input stream is valid.
         * @param inputStream The input stream to read from.
         * @throws IOException Thrown if the header is not valid.
         */
        private void headerCheck(InputStream inputStream) throws IOException {
            if (!Arrays.equals(header, inputStream.readNBytes(4)))
                throw new IOException("Invalid header check.");
        }

        /**
         * Indexes this file.
         * @throws IOException If the file is not valid.
         */
        public synchronized void index() throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create file.");
                return; // Can't index an empty file
            }

            FileInputStream inputStream = new FileInputStream(file);
            headerCheck(inputStream);

            playersCount = Serial.Read.readInteger(inputStream);
            if (playersCount > 10) throw new IOException("Too many players to be valid.");
            // uuids.clear();
            // for (int index = 0; index < uuidsCount; ++index) uuids.add(Serial.Read.readUUID(inputStream));

            serversCount = Serial.Read.readInteger(inputStream);
            servers.clear();
            for (int index = 0; index < serversCount; ++index) {
                String hostname = Serial.Read.readString(inputStream);
                int port = Serial.Read.readInteger(inputStream);
                servers.add(new PlayerInfo.ServerInfo(hostname, port));
            }

            minTimestamp = Serial.Read.readLong(inputStream);
            headerSkip = inputStream.getChannel().position(); // Record this, so we can just skip over this info later

            offsets.clear();
            for (int index = 0; index < playersCount; ++index) {
                int lookupID = Serial.Read.readInteger(inputStream);
                long offset = inputStream.getChannel().position();
                // uuids.add(Serial.Read.readUUID(inputStream));
                readElements(inputStream);

                offsets.put(lookupID, offset);
            }

            inputStream.close();
        }

        /**
         * Reads all the elements from this file.
         * @return The elements read with a mapping to the {@link PlayerInfo} they belong to.
         */
        public synchronized Map<Integer, Set<T>> readAll() throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create file.");
                return new HashMap<>();
            }

            // We won't have saved a completely blank file, if we have, there's another problem lol
            if (offsets.isEmpty() || servers.isEmpty()) throw new IOException("File not indexed.");

            FileInputStream inputStream = new FileInputStream(file);
            headerCheck(inputStream);
            if (inputStream.skip(headerSkip - 4) != headerSkip - 4) throw new IOException("Failed to skip header.");

            Map<Integer, Set<T>> elements = new HashMap<>();
            for (int index = 0; index < playersCount; ++index) {
                // UUID uuid = Serial.Read.readUUID(inputStream);
                int lookupID = Serial.Read.readInteger(inputStream);
                Set<T> elements1 = readElements(inputStream);
                elements.put(lookupID, elements1);
            }

            inputStream.close();
            return elements;
        }

        /**
         * Reads a specific {@link PlayerInfo}'s data from the file.
         * @param lookupID The unique lookup ID given to all players.
         * @return The set of values.
         */
        public synchronized Set<T> readFor(int lookupID) throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create file.");
                return new HashSet<>();
            }

            if (offsets.isEmpty() || servers.isEmpty()) throw new IOException("File not indexed.");
            if (!offsets.containsKey(lookupID)) throw new IOException("Player is not in this file.");

            FileInputStream inputStream = new FileInputStream(file); // FIXME: Too much overhead?
            headerCheck(inputStream);
            if (inputStream.skip(headerSkip - 4) != headerSkip - 4) throw new IOException("Failed to skip header.");

            long offset = offsets.get(lookupID);
            if (offset < 0 || inputStream.skip(offset - headerSkip) != offset - headerSkip)
                throw new IOException("Failed to skip to lookup ID offset.");

            Set<T> elements = readElements(inputStream);

            inputStream.close();
            return elements;
        }

        /**
         * Writes all the provided elements to the file.
         * @param values The elements to write.
         */
        public synchronized void writeAll(Map<Integer, Set<T>> values) throws IOException {
            if (!file.exists() && !file.createNewFile()) throw new IOException("Couldn't create file.");

            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(header);

            Serial.Write.writeInteger(offsets.size(), outputStream);
            playersCount = offsets.size();
            // for (UUID uuid : uuids) Serial.Write.writeUUID(uuid, outputStream);

            Serial.Write.writeInteger(servers.size(), outputStream);
            serversCount = servers.size();
            for (PlayerInfo.ServerInfo server : servers) {
                Serial.Write.writeString(server.hostname, outputStream);
                Serial.Write.writeInteger(server.port, outputStream);
            }

            Serial.Write.writeLong(minTimestamp, outputStream); // Min timestamp should be set when new sessions are added
            headerSkip = outputStream.getChannel().position();

            // offsets.clear();
            for (int lookupID : offsets.keySet()) {
                Serial.Write.writeInteger(lookupID, outputStream);
                offsets.put(lookupID, outputStream.getChannel().position()); // We'll re-index here

                // Serial.Write.writeUUID(uuid, outputStream);
                writeElements(values.getOrDefault(lookupID, Collections.emptySet()), outputStream);
            }

            outputStream.close();
        }
    }

    /**
     * Represents a sessions file that is stored on disk. These store multiple {@link Session}s for multiple {@link PlayerInfo}s.
     */
    private static class SessionsFile extends AbstractFile<Session> {

        public SessionsFile(File file) {
            super(SESSIONS_FILE_HEADER, file);
        }

        @Override
        protected Set<Session> readElements(InputStream inputStream) throws IOException {
            int sessionsCount = Serial.Read.readInteger(inputStream);
            Set<Session> sessions = new HashSet<>();
            for (int index = 0; index < sessionsCount; ++index) {
                PlayerInfo.ServerInfo server = servers.get(Serial.Read.readInteger(inputStream));
                long start = Serial.Read.readLong(inputStream);
                long delta = Serial.Read.readLong(inputStream);
                sessions.add(new Session(server, minTimestamp + start, minTimestamp + start + delta));
            }
            return sessions;
        }

        @Override
        protected void writeElements(Set<Session> elements, OutputStream outputStream) throws IOException {
            Serial.Write.writeInteger(elements.size(), outputStream);
            for (Session session : elements) {
                Serial.Write.writeInteger(servers.indexOf(session.server), outputStream);
                Serial.Write.writeLong(session.start - minTimestamp, outputStream);
                Serial.Write.writeLong(session.end - session.start, outputStream);
            }
        }
    }

    /**
     * Represents a deaths file that is stored on isk. There store multiple {@link Death}s for multiple {@link PlayerInfo}s.
     */
    private static class DeathsFile extends AbstractFile<Death> {

        public DeathsFile(File file) {
            super(DEATHS_FILE_HEADER, file);
        }

        @Override
        protected Set<Death> readElements(InputStream inputStream) throws IOException {
            int deathsCount = Serial.Read.readInteger(inputStream);
            Set<Death> deaths = new HashSet<>();
            for (int index = 0; index < deathsCount; ++index) {
                PlayerInfo.ServerInfo server = servers.get(Serial.Read.readInteger(inputStream));
                long timestamp = Serial.Read.readLong(inputStream) + minTimestamp;
                Death.Type type = Death.Type.values()[Serial.Read.readInteger(inputStream)];
                UUID killer = null;
                if (Serial.Read.readInteger(inputStream) == 1) killer = Serial.Read.readUUID(inputStream);
                deaths.add(new Death(server, timestamp, type, killer));
            }
            return deaths;
        }

        @Override
        protected void writeElements(Set<Death> elements, OutputStream outputStream) throws IOException {
            Serial.Write.writeInteger(elements.size(), outputStream);
            for (Death death : elements) {
                Serial.Write.writeInteger(servers.indexOf(death.server), outputStream);
                Serial.Write.writeLong(death.timestamp - minTimestamp, outputStream);
                Serial.Write.writeInteger(death.type.ordinal(), outputStream);
                if (death.killer != null) {
                    Serial.Write.writeInteger(1, outputStream);
                    Serial.Write.writeUUID(death.killer, outputStream);
                } else {
                    Serial.Write.writeInteger(0, outputStream);
                }
            }
        }
    }

    /**
     * Represents a deaths file that is stored on isk. There store multiple {@link Kill}s for multiple {@link PlayerInfo}s.
     */
    private static class KillsFile extends AbstractFile<Kill> {

        public KillsFile(File file) {
            super(KILLS_FILE_HEADER, file);
        }

        @Override
        protected Set<Kill> readElements(InputStream inputStream) throws IOException {
            int killsCount = Serial.Read.readInteger(inputStream);
            Set<Kill> kills = new HashSet<>();
            for (int index = 0; index < killsCount; ++index) {
                PlayerInfo.ServerInfo server = servers.get(Serial.Read.readInteger(inputStream));
                long timestamp = Serial.Read.readLong(inputStream) + minTimestamp;
                UUID victim = Serial.Read.readUUID(inputStream);
                kills.add(new Kill(server, timestamp, victim));
            }
            return kills;
        }

        @Override
        protected void writeElements(Set<Kill> elements, OutputStream outputStream) throws IOException {
            Serial.Write.writeInteger(elements.size(), outputStream);
            for (Kill kill : elements) {
                Serial.Write.writeInteger(servers.indexOf(kill.server), outputStream);
                Serial.Write.writeLong(kill.timestamp - minTimestamp, outputStream);
                Serial.Write.writeUUID(kill.victim, outputStream);
            }
        }
    }
}
