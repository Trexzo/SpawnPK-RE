package spk.local;

import java.util.*;

public final class PlayerSnapshotExtensionStateTest {
    private static final class MemoryRepository
        implements PlayerRepository {

        private PlayerSnapshot snapshot;

        MemoryRepository(
            PlayerSnapshot snapshot
        ){
            this.snapshot=snapshot;
        }

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return snapshot==null
                ?Optional.empty()
                :Optional.of(snapshot);
        }

        @Override public void save(
            PlayerSnapshot snapshot
        ){
            this.snapshot=snapshot;
        }

        PlayerSnapshot snapshot(){
            return snapshot;
        }
    }

    public static void main(String[] args)
        throws Exception{
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

        SortedMap<String,String> socialNamespace=
            live.snapshotExtensions()
                .namespace("social");

        require(
            socialNamespace.size()==2&&
            "1".equals(
                socialNamespace.get("version")
            )&&
            "alice,bob".equals(
                socialNamespace.get("friends")
            ),
            "social namespace projection incorrect"
        );

        TreeMap<String,String> replacementSocial=
            new TreeMap<>();
        replacementSocial.put(
            "version",
            "2"
        );
        replacementSocial.put(
            "friends",
            "charlie"
        );

        live.snapshotExtensions()
            .replaceNamespace(
                "social",
                replacementSocial
            );

        SortedMap<String,String> afterSocialReplace=
            live.snapshotExtensions()
                .snapshot();

        require(
            "2".equals(
                afterSocialReplace.get(
                    "extension.social.version"
                )
            )&&
            "charlie".equals(
                afterSocialReplace.get(
                    "extension.social.friends"
                )
            ),
            "social namespace replacement failed"
        );
        require(
            "room-3".equals(
                afterSocialReplace.get(
                    "extension.raid.progress"
                )
            ),
            "social namespace replacement clobbered raid namespace"
        );

        SortedMap<String,String> beforeBadNamespaceReplace=
            live.snapshotExtensions()
                .snapshot();

        TreeMap<String,String> badNamespaceValues=
            new TreeMap<>();
        badNamespaceValues.put(
            "bad..key",
            "x"
        );

        boolean namespaceReplaceRejected=false;

        try{
            live.snapshotExtensions()
                .replaceNamespace(
                    "social",
                    badNamespaceValues
                );
        }catch(
            IllegalArgumentException expected
        ){
            namespaceReplaceRejected=true;
        }

        require(
            namespaceReplaceRejected,
            "malformed namespace replacement accepted"
        );
        require(
            beforeBadNamespaceReplace.equals(
                live.snapshotExtensions()
                    .snapshot()
            ),
            "failed namespace replacement mutated other state"
        );

        boolean namespaceImmutable=false;

        try{
            socialNamespace.put(
                "late",
                "mutation"
            );
        }catch(
            UnsupportedOperationException expected
        ){
            namespaceImmutable=true;
        }

        require(
            namespaceImmutable,
            "namespace projection mutable"
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

        MemoryRepository repository=
            new MemoryRepository(
                snapshot
            );
        WorldPlayer lifecyclePlayer=
            new WorldPlayer();

        LocalAccountLifecycle.LoadResult load=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "extension-owner",
                    true
                ),
                lifecyclePlayer,
                repository,
                value->true,
                "[extension-state-test] "
            );

        require(
            load.loaded,
            "real account lifecycle did not load snapshot"
        );
        require(
            lifecyclePlayer.snapshotExtensions()
                .snapshot()
                .equals(
                    PlayerSnapshotExtensionState
                        .extract(
                            snapshot.values()
                        )
                ),
            "real account lifecycle lost extension state"
        );

        PlayerSnapshot lifecycleSaved=
            LocalAccountLifecycle.captureAndSave(
                "extension-owner",
                lifecyclePlayer,
                repository,
                0
            );

        require(
            repository.snapshot()==
                lifecycleSaved,
            "repository did not receive recaptured snapshot"
        );
        require(
            "alice,bob".equals(
                lifecycleSaved.value(
                    "extension.social.friends"
                )
            )&&
            "room-3".equals(
                lifecycleSaved.value(
                    "extension.raid.progress"
                )
            ),
            "real account lifecycle save lost extensions"
        );
        require(
            lifecycleSaved.value(
                "unknown.fixture"
            )==null,
            "real account lifecycle promoted ordinary unknown key"
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
            "accountLifecyclePreserves=true "+
            "namespaceIsolation=true "+
            "namespaceFailureAtomic=true "+
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
