package ez.pogdog.yescom.core.connection;

import ez.pogdog.yescom.YesCom;
import ez.pogdog.yescom.api.Logging;
import ez.pogdog.yescom.api.data.PlayerInfo;
import ez.pogdog.yescom.core.config.IConfig;
import ez.pogdog.yescom.core.config.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public final Map<UUID, PlayerInfo> playerCache = Collections.synchronizedMap(new HashMap<>());

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
        if (!TRUSTED_PLAYERS.value.contains(uuid)) TRUSTED_PLAYERS.value.add(uuid);
    }

    public void removeTrusted(UUID uuid) {
        TRUSTED_PLAYERS.value.remove(uuid);
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
