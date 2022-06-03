package ez.pogdog.yescom.core.servers.behaviours;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Globals;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.chat.ChatMessage;
import ez.pogdog.yescom.api.data.chat.CommandMessage;
import ez.pogdog.yescom.api.data.chat.DeathMessage;
import ez.pogdog.yescom.api.data.chat.JoinLeaveMessage;
import ez.pogdog.yescom.api.data.chat.PartyMessage;
import ez.pogdog.yescom.api.data.chat.RegularMessage;
import ez.pogdog.yescom.api.data.chat.WhisperMessage;
import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.death.Death;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;
import ez.pogdog.yescom.core.connection.Player;
import ez.pogdog.yescom.core.connection.Server;
import ez.pogdog.yescom.core.servers.IServerBehaviour;
import ez.pogdog.yescom.core.util.MinecraftChat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Behaviour for the server constantiam.net.
 */
public class ConstantiamBehaviour implements IServerBehaviour, IConfig {

    private final Logger logger = Logging.getLogger("yescom.core.servers.behaviours");
    private final YesCom yesCom = YesCom.getInstance();

    /* ------------------------------ Options ------------------------------ */

    public final Option<Integer> AEF_RESPAWN_VANISH_TICKS = new Option<>(
            "AEF respawn vanish ticks",
            "The number of ticks a respawning player vanishes for.",
            10
    );

    /* ------------------------------ Other fields ------------------------------ */

    private final Map<Pattern, Death.Type> deathMessages = new HashMap<>();
    private final Pattern joinLeavePattern = Pattern.compile(
            "^(?<player>[A-Za-z0-9_]{1,20}) (?<action>(joined|left)) the game$" // 1 - 20 for the benefit of the doubt
    );
    private final Pattern regularPattern = Pattern.compile(
            "^<(?<player>[A-Za-z0-9_]{1,20})> (?<message>.*)"
    );
    private final Pattern whisperToPattern = Pattern.compile(
            "^To (?<player>[A-Za-z0-9_]{1,20}): (?<message>.*)"
    );
    private final Pattern whisperFromPattern = Pattern.compile(
            "^(?<player>[A-Za-z0-9_]{1,20}) whispers: (?<message>.*)"
    );

    private final Map<UUID, Long> processingDisconnects = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentLeaves = new ConcurrentHashMap<>();
    private final Map<Player, Queue<ChatMessage>> queuedMessages = new ConcurrentHashMap<>();

    private float expectedTime = 150.0f;

    private /* final */ Server server;

    public ConstantiamBehaviour() {
        // :heart_eyes:
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20})'s ankles went to say hello to their throat$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) jumped from the world trade center$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) had a massive spinal compression$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) took a leap of \\(blind\\) faith$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) tripped over their own feet$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) broke their legs and died$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) forgot to turn on NoFall$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) hit the ground too hard$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) fell out of the water$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) fell off Trump's wall$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was testing gravity$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) fell off some vines$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) forgot how to fly$"), Death.Type.FALLING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) tripped$"), Death.Type.FALLING);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) attempted to glide with an Elytra$"), Death.Type.ELYTRA);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) experienced kinetic energy$"), Death.Type.ELYTRA);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) transformed themselves into a corpse$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) couldn't take it any longer$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) became a virgin sacrifice$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) died\\.\\.\\. somehow$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) ended their life$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) committed sudoku$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) did the Epstein$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) became an hero$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) met Allah$"), Death.Type.SLASH_KILL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) has voted$"), Death.Type.SLASH_KILL);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) burnt into a crisp whilist fighting (?<killer>[A-Za-z0-9_]{1,20})"), Death.Type.FIRE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) decided to jump into lava$"), Death.Type.FIRE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) tried to swim in lava$"), Death.Type.FIRE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) became charcoal$"), Death.Type.FIRE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) became lava$"), Death.Type.FIRE);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) is sleeping with the fishes$"), Death.Type.DROWNING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20})'s lungs are now an aquarium$"), Death.Type.DROWNING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) forgot their oxygen tank$"), Death.Type.DROWNING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) drowned$"), Death.Type.DROWNING);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) suffocated in a wall$"), Death.Type.SUFFOCATION);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) became a fossil$"), Death.Type.SUFFOCATION);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20})'s hunger strike gained them nothing$"), Death.Type.STARVING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) forgot their melons$"), Death.Type.STARVING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) moved to Ethiopia$"), Death.Type.STARVING);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) starved to death$"), Death.Type.STARVING);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) got head from a wither$"), Death.Type.WITHER);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) took 3 heads at once$"), Death.Type.WITHER); // TODO: Check
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) withered away$"), Death.Type.WITHER);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was blasted by (?<killer>[A-Za-z0-9_]{1,20}) with an EnderCrystal$"), Death.Type.CRYSTAL);
        deathMessages.put(Pattern.compile("^(?<killer>[A-Za-z0-9_]{1,20}) shoved a crystal up (?<player>[A-Za-z0-9_]{1,20})'s ass$"), Death.Type.CRYSTAL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was crystalled by (?<killer>[A-Za-z0-9_]{1,20})$"), Death.Type.CRYSTAL);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was cystalled to death$"), Death.Type.CRYSTAL);

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was dismantled by a Zombie$"), Death.Type.MELEE); // TODO: Check
        deathMessages.put(Pattern.compile("^A Zombie ate (?<player>[A-Za-z0-9_]{1,20})'s brain!$"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("Zombie threw (?<player>[A-Za-z0-9_]{1,20}) to their death$"), Death.Type.MELEE); // TODO: Check
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was penetrated by a Zombie( using .+)?"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was thrown off a mountain by Zombie$"), Death.Type.MELEE); // TODO: Check

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was doomed to fall by Zombie Pigman using .+"), Death.Type.MELEE);
        // TODO: <mob> threw <player> to their death

        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was completely destroyed by (?<killer>[A-Za-z0-9_]{1,20}) with their .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was doomed to fall by (?<killer>[A-Za-z0-9_]{1,20}) using .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) got oofed by (?<killer>[A-Za-z0-9_]{1,20}) using their .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) was rekt by (?<killer>[A-Za-z0-9_]{1,20}) using .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) ended (?<killer>[A-Za-z0-9_]{1,20}) using .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) never saw (?<killer>[A-Za-z0-9_]{1,20}) coming$"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<player>[A-Za-z0-9_]{1,20}) had a date with (?<killer>[A-Za-z0-9_]{1,20})'s .+"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^(?<killer>[A-Za-z0-9_]{1,20}) threw (?<player>[A-Za-z0-9_]{1,20}) to their death$"), Death.Type.MELEE);
        deathMessages.put(Pattern.compile("^ (?<player>[A-Za-z0-9_]{1,20}) was slain by (?<killer>[A-Za-z0-9_]{1,20})"), Death.Type.MELEE);

        yesCom.configHandler.addConfiguration(this);
    }

    @Override
    public boolean isValid(Server server) {
        return (server.hostname.equalsIgnoreCase("constantiam.net") ||
                server.hostname.equalsIgnoreCase("constantiam.org") ||
                server.hostname.equalsIgnoreCase("constantiam.co.uk") ||
                server.hostname.equals("95.217.83.105")) && server.port == 25565;
    }

    @Override
    public void apply(Server server) {
        if (this.server != null) throw new IllegalStateException("Attempted to apply server behaviour when already applied.");
        this.server = server;
    }

    @Override
    public Set<Class<? extends IServerBehaviour>> getOverrides() {
        return Collections.singleton(DefaultBehaviour.class);
    }

    @Override
    public synchronized void tick() {
        if (!server.isConnected()) {
            processingDisconnects.clear();
            recentDeaths.clear();
            recentLeaves.clear();
            queuedMessages.clear();
            expectedTime = 150.0f;

        } else {
            // 75ms for the benefit of the doubt that we guessed the server tickrate wrong
            expectedTime = AEF_RESPAWN_VANISH_TICKS.value * 75.0f * (20.0f / Math.min(20.0f, Math.max(0.1f, server.getTPS())));
            for (Map.Entry<UUID, Long> entry : processingDisconnects.entrySet()) {
                if (System.currentTimeMillis() - entry.getValue() > expectedTime) {
                    logger.finest(String.format("Player %s disconnect has timed out (%dms / %.1fms), handling.", entry.getKey(),
                            System.currentTimeMillis() - entry.getValue(), expectedTime));
                    processingDisconnects.remove(entry.getKey());
                    server.handleDisconnect(yesCom.playersHandler.getInfo(entry.getKey())); // Assume they disconnected, not much else we can do
                }
            }

            if (server.getTSLP() < server.HIGH_TSLP.value) { // Server isn't lagging?
                // Don't time out deaths, players may spend a while in the respawn screen
                // for (Map.Entry<PlayerInfo, Long> entry : recentDeaths.entrySet()) {
                //     if (System.currentTimeMillis() - entry.getValue() > server.HIGH_TSLP.value) {
                //         logger.finest(String.format("Player %s death has timed out (%dms).", entry.getKey(),
                //                 System.currentTimeMillis() - entry.getValue()));
                //         recentDeaths.remove(entry.getKey());
                //     }
                // }
                for (Map.Entry<UUID, Long> entry : recentLeaves.entrySet()) {
                    if (System.currentTimeMillis() - entry.getValue() > server.HIGH_TSLP.value) {
                        logger.finest(String.format("Player %s logout has timed out (%dms).", entry.getKey(),
                                System.currentTimeMillis() - entry.getValue()));
                        recentLeaves.remove(entry.getKey());
                    }
                }
            }
        }
    }

    @Override
    public synchronized void processJoin(PlayerInfo info) {
        // Even if they weren't "disconnected", the server will make sure it doesn't fire a second event
        server.handleConnect(info);
        flushQueue();
        if (processingDisconnects.containsKey(info.uuid)) {
            // Sometimes we don't get a death message so yeah
            if (System.currentTimeMillis() - processingDisconnects.get(info.uuid) < expectedTime && !recentLeaves.containsKey(info.uuid)) {
                server.handleDeath(info, new Death(server.serverInfo, processingDisconnects.get(info.uuid), Death.Type.GENERIC));
                logger.finest(String.format("Player %s has died.", info));
            }
            processingDisconnects.remove(info.uuid); // Joined again so notify immediately
        }
    }

    @Override
    public synchronized ChatMessage parseChatMessage(Player player, ServerChatPacket packet) {
        try {
            ChatMessage chatMessage = parseChatMessage(System.currentTimeMillis(), player.getUUID(), packet);
            if (chatMessage != null) logger.finest("Parsed chat message: " + chatMessage.toString());

            if (queuedMessages.containsKey(player)) {
                queuedMessages.get(player).add(chatMessage);
                return null;
            }

            if (chatMessage instanceof QueuedJoinMessage) {
                queuedMessages.put(player, new ArrayDeque<>());
                queuedMessages.get(player).add(chatMessage);
                return null;

            } else if (chatMessage instanceof DeathMessage) {
                DeathMessage deathMessage = (DeathMessage)chatMessage;

                // I LOVE moo <3. Does make it easier to detect deaths tho. Default is 10 ticks, may vary with server tickrate
                // https://github.com/moom0o/AnarchyExploitFixes/blob/2779vZwb5E8nGKEdV65299d10ddc3e8d538d8e767dfa629e23e2ca6/src/main/java/me/moomoo/anarchyexploitfixes/prevention/CoordExploits.java#L33
                if (!recentDeaths.containsKey(deathMessage.player)) { // Fire death event if needed
                    recentDeaths.put(deathMessage.player, System.currentTimeMillis());
                    server.handleDeath(yesCom.playersHandler.getInfo(deathMessage.player), deathMessage.death);
                }

                processingDisconnects.remove(deathMessage.player); // The player didn't disconnect, they just died

                logger.finest(String.format("Player %s has died.", deathMessage.player));

            } else if (chatMessage instanceof JoinLeaveMessage) {
                JoinLeaveMessage joinLeave = (JoinLeaveMessage)chatMessage;

                if (!joinLeave.joining) {
                    if (!recentLeaves.containsKey(joinLeave.player))
                        recentLeaves.put(joinLeave.player, System.currentTimeMillis());

                    if (processingDisconnects.containsKey(joinLeave.player)) {
                        processingDisconnects.remove(joinLeave.player);
                        recentLeaves.remove(joinLeave.player);
                        server.handleDisconnect(yesCom.playersHandler.getInfo(joinLeave.player));

                        logger.finest(String.format("Player %s has disconnected.", joinLeave.player));
                    }
                }
            }

            return chatMessage;

        } catch (Exception error) {
            logger.warning("Failed to parse chat message: " + error.getMessage());
            logger.throwing(getClass().getSimpleName(), "parseChatMessage", error);
        }
        return null;
    }

    @Override
    public synchronized void processLeave(PlayerInfo info) {
        if (recentDeaths.containsKey(info.uuid)) { // The player just died, don't handle as a disconnect
            recentDeaths.remove(info.uuid);
        } else if (recentLeaves.containsKey(info.uuid)) { // We can be sure that they just left
            recentLeaves.remove(info.uuid);
            server.handleDisconnect(info);
        } else {
            logger.finest(String.format("Processing disconnect for %s...", info));
            processingDisconnects.put(info.uuid, System.currentTimeMillis());
        }
        // server.handleDisconnect(info);
    }

    @Override
    public String getIdentifier() {
        return "constantiam-behaviour";
    }

    @Override
    public IConfig getParent() {
        return server;
    }

    private void flushQueue() {
        for (Map.Entry<Player, Queue<ChatMessage>> entry : queuedMessages.entrySet()) {
            while (!entry.getValue().isEmpty()) {
                ChatMessage message = entry.getValue().peek();
                if (!(message instanceof QueuedJoinMessage)) {
                    server.handleChatMessage(entry.getValue().poll());
                } else {
                    QueuedJoinMessage queuedJoin = (QueuedJoinMessage)message;
                    PlayerInfo info = yesCom.playersHandler.getInfo(queuedJoin.playerName);
                    if (info != null) {
                        server.handleChatMessage(new JoinLeaveMessage(queuedJoin.timestamp, queuedJoin.receiver,
                                queuedJoin.message, info.uuid, true));
                        entry.getValue().poll();
                    } else {
                        break; // Still haven't found the name of the player that joined
                    }
                }
            }

            if (entry.getValue().isEmpty()) { // Found the player that we were looking for
                queuedMessages.remove(entry.getKey());
                logger.finest(String.format("Flushed message queue for player %s.", entry.getKey()));
            }
        }
    }

    private void getDarkAquaStrings(JsonObject message, List<String> darkAquaStrings) {
        String text = message.get("text").getAsString();
        if (!text.isBlank() && message.has("color") && message.get("color").getAsString().equals("dark_aqua"))
            darkAquaStrings.add(text.strip());

        if (message.has("extra")) {
            for (JsonElement element : message.getAsJsonArray("extra"))
                getDarkAquaStrings(element.getAsJsonObject(), darkAquaStrings);
        }
    }

    private ChatMessage parseChatMessage(long timestamp, UUID receiver, ServerChatPacket packet) {
        JsonObject message = Globals.JSON.parse(packet.getMessageText()).getAsJsonObject();
        String fullMessage = MinecraftChat.unwrap(message, MinecraftChat.MINECRAFT_COLOURS);

        switch (packet.getType()) {
            case CHAT: {
                String unformatted = MinecraftChat.unwrap(message, Collections.emptyMap());
                for (Map.Entry<Pattern, Death.Type> entry : deathMessages.entrySet()) {
                    Matcher matcher = entry.getKey().matcher(unformatted);
                    if (matcher.matches()) {
                        PlayerInfo player = yesCom.playersHandler.getInfo(matcher.group("player"));
                        PlayerInfo killer = null;
                        try {
                            killer = yesCom.playersHandler.getInfo(matcher.group("killer"));
                        } catch (IllegalArgumentException ignored) {
                        }

                        return new DeathMessage(timestamp, receiver, fullMessage, player.uuid,
                                new Death(server.serverInfo, timestamp, entry.getValue(), killer == null ? null : killer.uuid));
                    }
                }

                // Fallback if we can't find a valid death message, at least try something
                logger.warning("Couldn't parse death message: " + unformatted);

                List<String> darkAquaStrings = new ArrayList<>();
                getDarkAquaStrings(message, darkAquaStrings);

                if (darkAquaStrings.size() == 1) { // Only one player involved
                    PlayerInfo player = yesCom.playersHandler.getInfo(darkAquaStrings.get(0).split("'")[0].strip()); // "<player>'s "
                    return new DeathMessage(timestamp, receiver, fullMessage, player.uuid,
                            new Death(server.serverInfo, timestamp, Death.Type.GENERIC));

                } else if (darkAquaStrings.size() == 2) { // Two players involved?
                    PlayerInfo player = null;
                    String[] playerUsername = darkAquaStrings.get(0).split("'");
                    // Check it's not a wither name or something similar
                    if (playerUsername.length == 1 || playerUsername[1].length() <= 2)
                        player = yesCom.playersHandler.getInfo(playerUsername[0].strip());

                    PlayerInfo killer = null;
                    String[] killerUsername = darkAquaStrings.get(1).split("'");
                    if (killerUsername.length == 1 || killerUsername[1].length() <= 2)
                        killer = yesCom.playersHandler.getInfo(darkAquaStrings.get(1).split("'")[0].strip());

                    if (player == null && killer == null) {
                        return null; // Can't get any information from this
                    } else if (player == null) {
                        player = killer; // Got these the wrong way around perhaps?
                        killer = null;
                    }

                    if (!server.isOnline(player.uuid) && killer != null && server.isOnline(killer.uuid)) {
                        PlayerInfo oldPlayer = player; // Could be a mob kill
                        player = killer;
                        killer = oldPlayer;
                    }

                    return new DeathMessage(timestamp, receiver, fullMessage, player.uuid,
                            new Death(server.serverInfo, timestamp, Death.Type.GENERIC, killer == null ? null : killer.uuid));
                }

                return new CommandMessage(timestamp, receiver, fullMessage);
            }
            case SYSTEM: {
                JsonArray extra = message.getAsJsonArray("extra");
                if (extra == null) return null; // Can occur when we first join the game

                JsonObject first = extra.get(0).getAsJsonObject();
                String firstColour = first.has("color") ? first.get("color").getAsString() : "white";

                if (extra.size() == 1) {
                    switch (firstColour) {
                        case "gray":
                        case "yellow": { // Can still get yellow join/leave messages when the server reboots
                            Matcher matcher = joinLeavePattern.matcher(first.get("text").getAsString());
                            if (matcher.matches()) {
                                PlayerInfo info = yesCom.playersHandler.getInfo(matcher.group("player"));
                                boolean joined = matcher.group("action").equals("joined");

                                if (info != null || !joined) {
                                    return new JoinLeaveMessage(timestamp, receiver, fullMessage, info.uuid, joined);
                                } else {
                                    // We're gonna get this before we get the tab list item, which sucks because it means
                                    // that we won't have cached the player's info yet, meaning getByName will return null
                                    // What's the solution then? -> queue the message until we've received the join, it
                                    // sucks I know, but whatever
                                    return new QueuedJoinMessage(timestamp, receiver, fullMessage, matcher.group("player"));
                                }
                            }
                            break;
                        }
                    }

                    // Fall through
                }

                switch (firstColour) {
                    case "light_purple": {
                        Matcher matcher = whisperToPattern.matcher(first.get("text").getAsString());
                        if (matcher.matches()) {
                            extra.remove(0);
                            message = new JsonObject();
                            message.add("extra", extra);

                            return new WhisperMessage(
                                    timestamp, receiver, fullMessage, yesCom.playersHandler.getInfo(matcher.group("player")).uuid,
                                    true, matcher.group("message") + MinecraftChat.unwrap(message, Collections.emptyMap())
                            );
                        }

                        matcher = whisperFromPattern.matcher(first.get("text").getAsString());
                        if (matcher.matches()) {
                            extra.remove(0);
                            message = new JsonObject();
                            message.add("extra", extra);

                            return new WhisperMessage(
                                    timestamp, receiver, fullMessage, yesCom.playersHandler.getInfo(matcher.group("player")).uuid,
                                    false, matcher.group("message") + MinecraftChat.unwrap(message, Collections.emptyMap())
                            );
                        }
                        break;
                    }
                    case "aqua": {
                        if (extra.size() >= 2 && "[P] ".equals(first.get("text").getAsString())) {
                            Matcher matcher = regularPattern.matcher(extra.get(1).getAsJsonObject().get("text").getAsString());
                            if (matcher.matches()) {
                                extra.remove(0);
                                extra.remove(0);
                                message = new JsonObject();
                                message.add("extra", extra);

                                return new PartyMessage(
                                        timestamp, receiver, fullMessage, yesCom.playersHandler.getInfo(matcher.group("player")).uuid,
                                        matcher.group("message") + MinecraftChat.unwrap(message, Collections.emptyMap())
                                );
                            }
                        }
                        break;
                    }
                    case "white": { // Accounts for green text too
                        Matcher matcher = regularPattern.matcher(first.get("text").getAsString());
                        if (matcher.matches()) {
                            extra.remove(0);
                            message = new JsonObject();
                            message.add("extra", extra);

                            return new RegularMessage(
                                    timestamp, receiver, fullMessage, yesCom.playersHandler.getInfo(matcher.group("player")).uuid,
                                    matcher.group("message") + MinecraftChat.unwrap(message, Collections.emptyMap())
                            );
                        }
                        break;
                    }
                }

                return new CommandMessage(timestamp, receiver, fullMessage);
            }
            case NOTIFICATION: { // Hotbar notifications, not real chat messages
                return null;
            }
        }

        // System.out.println(packet.getType());
        // System.out.println(Globals.JSON.parse(packet.getMessageText()));

        return null;
    }

    /* ------------------------------ Classes ------------------------------ */

    private static class QueuedJoinMessage extends JoinLeaveMessage {

        public final String playerName;

        public QueuedJoinMessage(long timestamp, UUID receiver, String message, String playerName) {
            super(timestamp, receiver, message, null, true);

            this.playerName = playerName;
        }

        @Override
        public String toString() {
            return String.format("QueuedJoinMessage(timestamp=%s, receiver=%s, name=%s)",
                    Globals.DATE_FORMAT.format(new Date(timestamp)), receiver, playerName);
        }
    }
}
