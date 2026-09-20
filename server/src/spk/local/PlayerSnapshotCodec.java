package spk.local;

import java.util.*;

/**
 * Transitional schema-v1 codec.
 *
 * Existing state-class property serializers remain the exact compatibility
 * authority in this first repository slice. Later #15 slices can move each
 * component codec here without changing PlayerRepository.
 */
final class PlayerSnapshotCodec {
    static PlayerSnapshot capture(
        String username,
        WorldPlayer player
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            Properties properties=
                new Properties();

            player.bank().saveAccountProperties(
                properties
            );
            player.equipment().saveAccountProperties(
                properties
            );
            player.movement().saveAccountProperties(
                properties
            );
            player.petState().saveAccountProperties(
                properties
            );
            player.playerState().saveAccountProperties(
                properties
            );

            TreeMap<String,String> values=
                new TreeMap<>();

            for(String key:
                    properties.stringPropertyNames())
                values.put(
                    key,
                    properties.getProperty(key)
                );

            return new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                username,
                values
            );
        }
    }

    /**
     * Compatibility decode used only by migration/round-trip tests in this
     * foundation slice. Runtime account loading still uses the existing loader
     * until atomic live-state application is introduced separately.
     */
    static void applyLegacy(
        PlayerSnapshot snapshot,
        WorldPlayer player
    ){
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );
        Objects.requireNonNull(
            player,
            "player"
        );

        Properties properties=
            snapshot.toLegacyProperties();

        synchronized(player.mutationLock()){
            player.bank().loadAccountProperties(
                properties
            );
            player.equipment().loadAccountProperties(
                properties
            );
            player.movement().loadAccountProperties(
                properties
            );
            player.petState().loadAccountProperties(
                properties
            );
            player.playerState().loadAccountProperties(
                properties
            );
        }
    }

    private PlayerSnapshotCodec(){}
}
