package spk.local;

import java.util.*;

public final class PlayerSnapshotExtensionStateTest {
    public static void main(String[] args){
        WorldPlayer source=
            new WorldPlayer();

        TreeMap<String,String> values=
            new TreeMap<>(
                PlayerSnapshotSchemaV1.capture(
                    source,
                    0
                )
            );

        values.put(
            "extension.social.version",
            "1"
        );
        values.put(
            "extension.social.friends",
            "alice,bob"
        );
        values.put(
            "extension.raid.progress",
            "room-3"
        );
        values.put(
            "unknown.fixture",
            "must-not-be-promoted"
        );

        PlayerSnapshot snapshot=
            new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "extension-owner",
                values
            );

        PlayerSnapshot normalized=
            PlayerSnapshotCodec
                .validateAndNormalize(
                    snapshot
                );

        require(
            "1".equals(
                normalized.value(
                    "extension.social.version"
                )
            ),
            "normalized social version missing"
        );
        require(
            "alice,bob".equals(
                normalized.value(
                    "extension.social.friends"
                )
            ),
            "normalized social friends missing"
        );
        require(
            "room-3".equals(
                normalized.value(
                    "extension.raid.progress"
                )
            ),
            "normalized second namespace missing"
        );
        require(
            normalized.value(
                "unknown.fixture"
            )==null,
            "ordinary unknown key promoted"
        );

        WorldPlayer live=
            new WorldPlayer();

        PlayerSnapshot applied=
            PlayerSnapshotCodec
                .applyValidated(
                    snapshot,
                    live
                );

        SortedMap<String,String> liveExtensions=
            live.snapshotExtensions()
                .snapshot();

        require(
            liveExtensions.size()==3,
            "live extension count="+
            liveExtensions.size()
        );
        require(
            liveExtensions.equals(
                PlayerSnapshotExtensionState
                    .extract(
                        applied.values()
                    )
            ),
            "live extension state differs from validated snapshot"
        );

        PlayerSnapshot recaptured=
            PlayerSnapshotCodec.capture(
                "extension-owner",
                live,
                0
            );

        require(
            "1".equals(
                recaptured.value(
                    "extension.social.version"
                )
            )&&
            "alice,bob".equals(
                recaptured.value(
                    "extension.social.friends"
                )
            )&&
            "room-3".equals(
                recaptured.value(
                    "extension.raid.progress"
                )
            ),
            "recapture lost extension state"
        );
        require(
            recaptured.value(
                "unknown.fixture"
            )==null,
            "recapture retained ordinary unknown key"
        );

        boolean immutable=false;

        try{
            liveExtensions.put(
                "extension.bad.mutation",
                "x"
            );
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "extension snapshot mutable"
        );

        SortedMap<String,String> before=
            live.snapshotExtensions()
                .snapshot();

        TreeMap<String,String> malformed=
            new TreeMap<>();
        malformed.put(
            "extension.social.version",
            "2"
        );
        malformed.put(
            "extension.",
            "invalid"
        );

        boolean rejected=false;

        try{
            live.snapshotExtensions()
                .replace(
                    malformed
                );
        }catch(
            IllegalArgumentException expected
        ){
            rejected=true;
        }

        require(
            rejected,
            "malformed extension replacement accepted"
        );
        require(
            before.equals(
                live.snapshotExtensions()
                    .snapshot()
            ),
            "failed replacement partially mutated extension state"
        );

        TreeMap<String,String> malformedSnapshotValues=
            new TreeMap<>(
                PlayerSnapshotSchemaV1.capture(
                    new WorldPlayer(),
                    0
                )
            );
        malformedSnapshotValues.put(
            "extension.",
            "invalid"
        );

        boolean malformedSnapshotRejected=false;

        try{
            PlayerSnapshotCodec
                .validateAndNormalize(
                    new PlayerSnapshot(
                        PlayerSnapshot.CURRENT_VERSION,
                        "bad-extension",
                        malformedSnapshotValues
                    )
                );
        }catch(
            IllegalArgumentException expected
        ){
            malformedSnapshotRejected=true;
        }

        require(
            malformedSnapshotRejected,
            "malformed namespaced snapshot accepted"
        );

        System.out.println(
            "PLAYER_SNAPSHOT_EXTENSION_STATE_PASS "+
            "normalizePreserves=true "+
            "liveApplyPreserves=true "+
            "recapturePreserves=true "+
            "unknownNotPromoted=true "+
            "immutable=true "+
            "replacementFailureAtomic=true "+
            "malformedFailClosed=true"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private PlayerSnapshotExtensionStateTest(){}
}
