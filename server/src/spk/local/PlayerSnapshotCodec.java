package spk.local;

import java.util.*;

/**
 * Immutable PlayerSnapshot capture/apply orchestration.
 *
 * Schema-v1 key encoding belongs to PlayerSnapshotSchemaV1; gameplay state
 * classes no longer know about java.util.Properties or repository storage.
 */
final class PlayerSnapshotCodec {
    static PlayerSnapshot capture(
        String username,
        WorldPlayer player
    ){
        return capture(
            username,
            player,
            0
        );
    }

    static PlayerSnapshot capture(
        String username,
        WorldPlayer player,
        int petAccessoryItem
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        synchronized(player.mutationLock()){
            TreeMap<String,String> values=
                new TreeMap<>(
                    PlayerSnapshotSchemaV1.capture(
                        player,
                        petAccessoryItem
                    )
                );

            values.putAll(
                player.snapshotExtensions()
                    .snapshot()
            );

            return new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                username,
                values
            );
        }
    }

    static PlayerSnapshot validateAndNormalize(
        PlayerSnapshot snapshot
    ){
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );

        WorldPlayer staged=
            new WorldPlayer();

        synchronized(staged.mutationLock()){
            PlayerSnapshotSchemaV1.apply(
                snapshot,
                staged
            );
            staged.snapshotExtensions()
                .replace(
                    PlayerSnapshotExtensionState
                        .extract(
                            snapshot.values()
                        )
                );
        }

        return capture(
            snapshot.username(),
            staged,
            accessoryItem(snapshot)
        );
    }

    /**
     * Stage-decodes into detached state first. Only a snapshot that has already
     * survived the full component decode is then applied to the live player.
     */
    static PlayerSnapshot applyValidated(
        PlayerSnapshot snapshot,
        WorldPlayer livePlayer
    ){
        Objects.requireNonNull(
            livePlayer,
            "livePlayer"
        );

        PlayerSnapshot normalized=
            validateAndNormalize(
                snapshot
            );

        synchronized(livePlayer.mutationLock()){
            PlayerSnapshotSchemaV1.apply(
                normalized,
                livePlayer
            );
            livePlayer.snapshotExtensions()
                .replace(
                    PlayerSnapshotExtensionState
                        .extract(
                            normalized.values()
                        )
                );
        }

        return normalized;
    }

    /**
     * Schema-v1 compatibility apply. The historical name remains as a test and
     * migration seam while all actual key decoding is owned by the schema codec.
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

        synchronized(player.mutationLock()){
            PlayerSnapshotSchemaV1.apply(
                snapshot,
                player
            );
        }
    }

    static int accessoryItem(
        PlayerSnapshot snapshot
    ){
        return PlayerSnapshotSchemaV1
            .petAccessoryItem(
                snapshot
            );
    }

    private PlayerSnapshotCodec(){}
}
