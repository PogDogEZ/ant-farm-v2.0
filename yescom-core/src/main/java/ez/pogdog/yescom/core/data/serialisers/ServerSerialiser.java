package ez.pogdog.yescom.core.data.serialisers;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.api.data.chat.CommandMessage;
import ez.pogdog.yescom.api.data.chat.DeathMessage;
import ez.pogdog.yescom.api.data.chat.JoinLeaveMessage;
import ez.pogdog.yescom.api.data.chat.PartyMessage;
import ez.pogdog.yescom.api.data.chat.PhantomMessage;
import ez.pogdog.yescom.api.data.chat.RegularMessage;
import ez.pogdog.yescom.api.data.chat.StatusMessage;
import ez.pogdog.yescom.api.data.chat.WhisperMessage;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.core.Emitters;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.data.ISerialiser;
import ez.pogdog.yescom.core.data.Serial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Serialises data from {@link Server}s.
 */
public class ServerSerialiser implements ISerialiser, IConfig {

    public static final byte[] CHAT_FILE_HEADER = new byte[] { 65, 78, 84, 1 };

    private final Logger logger = Logging.getLogger("yescom.core.data.serialisers");
    private final YesCom yesCom = YesCom.getInstance();

    private final DateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");

    /* ------------------------------ Options ------------------------------ */

    public final Option<Integer> MAX_CHAT_MESSAGES = new Option<>(
            "Max chat messages",
            "The maximum number of chat messages to store in a file.",
            8192
    );

    /* ------------------------------ Other fields ------------------------------ */

    private final Queue<Emitters.PlayerChat> dirtyChats = new ArrayDeque<>();

    private final Map<PlayerInfo.ServerInfo, ServerChatFile> openChatFiles = new HashMap<>();
    private final Set<ServerChatFile> closedChatFiles = new HashSet<>();

    public ServerSerialiser() {
        Emitters.ON_PLAYER_CHAT.connect(this::onPlayerChat);
    }

    @Override
    public void load(File dataDirectory) throws IOException {
        loadChatMessages(dataDirectory);
    }

    @Override
    public synchronized void save(File dataDirectory, boolean force) throws IOException { // TODO: Force, when shutting down
        saveDirtyChatMessages(dataDirectory, force);
    }

    @Override
    public String getIdentifier() {
        return "server-serialiser";
    }

    @Override
    public IConfig getParent() {
        return yesCom.dataHandler;
    }

    /* ------------------------------ Events ------------------------------ */

    private synchronized void onPlayerChat(Emitters.PlayerChat playerChat) {
        dirtyChats.add(playerChat);
    }

    /* ------------------------------ Serialisation ------------------------------ */

    private void loadChatMessages(File dataDirectory) throws IOException {
        File chatDirectory = new File(dataDirectory, "chat");
        if ((!chatDirectory.exists() || !chatDirectory.isDirectory()) && !chatDirectory.mkdirs())
            throw new IOException("Cannot create chat directory.");

        logger.finer("Indexing chat file(s)...");
        File[] directoryFiles = chatDirectory.listFiles();
        if (directoryFiles == null) throw new IOException("Chat directory file listing is null.");

        openChatFiles.clear();
        closedChatFiles.clear();

        long start = System.currentTimeMillis();
        for (File file : directoryFiles) {
            if (!file.isDirectory() && file.getName().endsWith(".ycom")) {
                ServerChatFile chatFile = new ServerChatFile(file);
                try {
                    chatFile.index();
                } catch (IOException error) {
                    logger.warning(String.format("Error while reading chat file %s: %s", file, error.getMessage()));
                    logger.throwing(getClass().getSimpleName(), "loadChatMessages", error);
                    continue;
                }
                if (!chatFile.chatMessages.isEmpty()) {
                    openChatFiles.put(chatFile.server, chatFile); // Chat file was open before it was saved
                } else {
                    closedChatFiles.add(chatFile);
                }
            }
        }
        logger.finer(String.format("Indexed %d chat files in %dms.", openChatFiles.size() + closedChatFiles.size(),
                System.currentTimeMillis() - start));
    }

    private void saveDirtyChatMessages(File dataDirectory, boolean force) throws IOException {
        File chatDirectory = new File(dataDirectory, "chat");
        if ((!chatDirectory.exists() || !chatDirectory.isDirectory()) && !chatDirectory.mkdirs())
            throw new IOException("Cannot create chat directory.");

        if (!dirtyChats.isEmpty()) logger.finer(String.format("Flushing %d dirty chat messages...", dirtyChats.size()));

        while (!dirtyChats.isEmpty()) {
            Emitters.PlayerChat playerChat = dirtyChats.poll();

            if (!openChatFiles.containsKey(playerChat.server.serverInfo)) {
                // TODO: New server chat file
                File file = new File(chatDirectory, String.format("cdata_%s-%d-%s.ycom",
                        playerChat.server.hostname.replaceAll("\\.", "_"), playerChat.server.port,
                        dateFormatter.format(new Date(playerChat.chatMessage.timestamp))));
                for (int index = 1; index < 1000; ++index) {
                    if (!file.exists()) break;
                    file = new File(chatDirectory, String.format("cdata_%s-%d-%s-%d.ycom",
                            playerChat.server.hostname.replaceAll("\\.", "_"), playerChat.server.port,
                            dateFormatter.format(new Date(playerChat.chatMessage.timestamp)), index));
                }
                openChatFiles.put(playerChat.server.serverInfo, new ServerChatFile(file, playerChat.server));
            }

            ServerChatFile chatFile = openChatFiles.get(playerChat.server.serverInfo);
            if (chatFile.minTimestamp > playerChat.chatMessage.timestamp) chatFile.minTimestamp = playerChat.chatMessage.timestamp;
            if (chatFile.maxTimestamp < playerChat.chatMessage.timestamp) chatFile.maxTimestamp = playerChat.chatMessage.timestamp;
            chatFile.chatMessages.add(playerChat.chatMessage);
        }

        for (Map.Entry<PlayerInfo.ServerInfo, ServerChatFile> entry : new ArrayList<>(openChatFiles.entrySet())) {
            if (force || entry.getValue().chatMessages.size() >= MAX_CHAT_MESSAGES.value) {
                boolean open = force && entry.getValue().chatMessages.size() < MAX_CHAT_MESSAGES.value;
                entry.getValue().writeAll(open);
                if (!open) { // If the file hasn't been forced to close, and is actually closed, remove from cache
                    openChatFiles.remove(entry.getKey());
                    closedChatFiles.add(entry.getValue());
                }
            }
        }
    }

    /* ------------------------------ Classes ------------------------------ */

    /**
     * Stores {@link ChatMessage}s. A directory is created per-server and the files in the directory correspond to
     * different timestamps (see {@link ServerSerialiser#MAX_CHAT_MESSAGES}).
     */
    private class ServerChatFile {

        public final List<ChatMessage> chatMessages = new ArrayList<>();

        private final File file;

        private long headerSkip;
        private int messagesCount;

        private PlayerInfo.ServerInfo server;
        private long minTimestamp = Long.MAX_VALUE;
        private long maxTimestamp = Long.MIN_VALUE;

        public ServerChatFile(File file, Server server) {
            this.file = file;
            this.server = server.serverInfo;
        }

        public ServerChatFile(File file) {
            this.file = file;
        }

        private void headerCheck(InputStream inputStream) throws IOException {
            if (!Arrays.equals(CHAT_FILE_HEADER, inputStream.readNBytes(4))) throw new IOException("Invalid header check.");
        }

        private ChatMessage read(InputStream inputStream, long previousTimestamp) throws IOException {
            ChatMessage.Type type = ChatMessage.Type.values()[Serial.Read.readInteger(inputStream)];
            long timestamp = Serial.Read.readLong(inputStream) + previousTimestamp;
            UUID receiver = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
            String message = Serial.Read.readString(inputStream);

            switch (type) {
                case COMMAND: {
                    return new CommandMessage(timestamp, receiver, message);
                }
                case DEATH: {
                    UUID player = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    Death.Type deathType = Death.Type.values()[Serial.Read.readInteger(inputStream)];
                    UUID killer = null;
                    if (Serial.Read.readInteger(inputStream) == 1)
                        killer = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    return new DeathMessage(timestamp, receiver, message, player, new Death(server, timestamp, deathType, killer));
                }
                case JOIN_LEAVE: {
                    UUID player = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    boolean joining = Serial.Read.readInteger(inputStream) == 1;
                    return new JoinLeaveMessage(timestamp, receiver, message, player, joining);
                }
                case PARTY: {
                    UUID sender = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    String actualMessage = Serial.Read.readString(inputStream);
                    return new PartyMessage(timestamp, receiver, message, sender, actualMessage);
                }
                case PHANTOM: {
                    return new PhantomMessage(timestamp, receiver, message);
                }
                case REGULAR: {
                    UUID sender = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    String actualMessage = Serial.Read.readString(inputStream);
                    return new RegularMessage(timestamp, receiver, message, sender, actualMessage);
                }
                case STATUS: {
                    return new StatusMessage(timestamp, receiver, message);
                }
                case WHISPER: {
                    UUID recipient = yesCom.playersHandler.getInfo(Serial.Read.readInteger(inputStream)).uuid;
                    boolean sending = Serial.Read.readInteger(inputStream) == 1;
                    String actualMessage = Serial.Read.readString(inputStream);
                    return new WhisperMessage(timestamp, receiver, message, recipient, sending, actualMessage);
                }
            }

            throw new IllegalStateException("Unknown chat message type."); // Shouldn't happen, I think
        }

        private void write(ChatMessage chatMessage, long previousTimestamp, OutputStream outputStream) throws IOException {
            Serial.Write.writeInteger(chatMessage.getType().ordinal(), outputStream);
            Serial.Write.writeLong(chatMessage.timestamp - previousTimestamp, outputStream);
            Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(chatMessage.receiver), outputStream);
            Serial.Write.writeString(chatMessage.message, outputStream);

            switch (chatMessage.getType()) {
                case DEATH: {
                    DeathMessage deathMessage = (DeathMessage)chatMessage;
                    Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(deathMessage.player), outputStream);
                    // Assume the server and timestamp are the same as the message, as otherwise, wtf?
                    Serial.Write.writeInteger(deathMessage.death.type.ordinal(), outputStream);
                    if (deathMessage.death.killer != null) {
                        Serial.Write.writeInteger(1, outputStream);
                        Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(deathMessage.death.killer), outputStream);
                    } else {
                        Serial.Write.writeInteger(0, outputStream);
                    }
                    break;
                }
                case JOIN_LEAVE: {
                    JoinLeaveMessage joinLeaveMessage = (JoinLeaveMessage)chatMessage;
                    Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(joinLeaveMessage.player), outputStream);
                    Serial.Write.writeInteger(joinLeaveMessage.joining ? 1 : 0, outputStream);
                    break;
                }
                case PARTY: {
                    PartyMessage partyMessage = (PartyMessage)chatMessage;
                    Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(partyMessage.sender), outputStream);
                    Serial.Write.writeString(partyMessage.actualMessage, outputStream);
                    break;
                }
                case REGULAR: {
                    RegularMessage regularMessage = (RegularMessage)chatMessage;
                    Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(regularMessage.sender), outputStream);
                    Serial.Write.writeString(regularMessage.actualMessage, outputStream);
                    break;
                }
                case WHISPER: {
                    WhisperMessage whisperMessage = (WhisperMessage)chatMessage;
                    Serial.Write.writeInteger(yesCom.playersHandler.getLookupID(whisperMessage.recipient), outputStream);
                    Serial.Write.writeInteger(whisperMessage.sending ? 1 : 0, outputStream);
                    Serial.Write.writeString(whisperMessage.actualMessage, outputStream);
                    break;
                }
            }
        }

        /**
         * Indexes this server chat file.
         * @throws IOException If the file is not valid.
         */
        public void index() throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create new file.");
                return;
            }

            FileInputStream inputStream = new FileInputStream(file);
            headerCheck(inputStream);

            messagesCount = Serial.Read.readInteger(inputStream);
            boolean open = Serial.Read.readInteger(inputStream) == 1;

            String hostname = Serial.Read.readString(inputStream);
            int port = Serial.Read.readInteger(inputStream);
            server = new PlayerInfo.ServerInfo(hostname, port);

            minTimestamp = Serial.Read.readLong(inputStream);
            maxTimestamp = Serial.Read.readLong(inputStream) + minTimestamp;

            headerSkip = inputStream.getChannel().position();
            inputStream.close();

            if (open) chatMessages.addAll(readAll()); // Indicate that this file is still open by reading the contents
        }

        /**
         * Reads all the {@link ChatMessage}s present in this file.
         * @return The chat messages, in order of timestamp.
         */
        public List<ChatMessage> readAll() throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create new file.");
                return new ArrayList<>();
            }

            FileInputStream inputStream = new FileInputStream(file);
            headerCheck(inputStream);

            if (inputStream.skip(headerSkip - 4) != headerSkip - 4) throw new IOException("Couldn't skip header.");

            List<ChatMessage> chatMessages = new ArrayList<>();
            long previousTimestamp = minTimestamp;
            for (int index = 0; index < messagesCount; ++index) {
                ChatMessage chatMessage = read(inputStream, previousTimestamp);
                chatMessages.add(chatMessage);
                previousTimestamp = chatMessage.timestamp;
            }

            inputStream.close();
            return chatMessages;
        }

        /**
         * Reads all the {@link ChatMessage}s in this file that were received between a given time frame.
         * @param minTimestamp The minimum timestamp.
         * @param maxTimestamp The maximum timestamp.
         * @return The chat messages, in order.
         */
        public List<ChatMessage> readBetween(long minTimestamp, long maxTimestamp) throws IOException {
            if (!file.exists()) {
                if (!file.createNewFile()) throw new IOException("Couldn't create new file.");
                return new ArrayList<>();
            }

            FileInputStream inputStream = new FileInputStream(file);
            headerCheck(inputStream);

            if (inputStream.skip(headerSkip - 4) != headerSkip - 4) throw new IOException("Couldn't skip header.");

            List<ChatMessage> chatMessages = new ArrayList<>();
            int count = Serial.Read.readInteger(inputStream);
            long previousTimestamp = minTimestamp;
            for (int index = 0; index < count; ++index) {
                ChatMessage chatMessage = read(inputStream, previousTimestamp);
                if (chatMessage.timestamp >= minTimestamp && chatMessage.timestamp < maxTimestamp) {
                    chatMessages.add(chatMessage);
                } else if (chatMessage.timestamp >= maxTimestamp) {
                    inputStream.close();
                    return chatMessages;
                }
                previousTimestamp = chatMessage.timestamp;
            }

            inputStream.close();
            return chatMessages;
        }

        /**
         * Writes all the provided {@link ChatMessage}s to the file.
         * @throws IOException Thrown if they could not be written for whatever reason.
         */
        public void writeAll(boolean open) throws IOException {
            if (!file.exists() && !file.createNewFile()) throw new IOException("Couldn't create new file.");

            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(CHAT_FILE_HEADER);

            Serial.Write.writeInteger(chatMessages.size(), outputStream);
            messagesCount = chatMessages.size();
            Serial.Write.writeInteger(open ? 1 : 0, outputStream);

            Serial.Write.writeString(server.hostname, outputStream);
            Serial.Write.writeInteger(server.port, outputStream);

            Serial.Write.writeLong(minTimestamp, outputStream);
            Serial.Write.writeLong(maxTimestamp - minTimestamp, outputStream);

            headerSkip = outputStream.getChannel().position();

            long previousTimestamp = minTimestamp;
            for (ChatMessage chatMessage : chatMessages.stream()
                    .sorted(Comparator.comparingLong(chatMessage -> chatMessage.timestamp))
                    .collect(Collectors.toList())) {
                write(chatMessage, previousTimestamp, outputStream);
                previousTimestamp = chatMessage.timestamp;
            }

            outputStream.close();
            if (!open) chatMessages.clear(); // Chat file is now closed, free memory
        }

        /* ------------------------------ Getters ------------------------------ */

        public long getMinTimestamp() {
            return minTimestamp;
        }

        public long getMaxTimestamp() {
            return maxTimestamp;
        }
    }
}
