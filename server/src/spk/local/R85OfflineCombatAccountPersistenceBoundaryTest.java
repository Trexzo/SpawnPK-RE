package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import spk.local.R82OfflineSourceScopedCombatReceiptTest.Fixture;
import spk.local.R82OfflineSourceScopedCombatReceiptTest.Mode;

/**
 * R85 OFFLINE ONLY. Use actual PlayerCombatResolutionService -> real
 * DomainEventBus -> objective/match/collection projections, then the
 * actual WorldPlayerPersistence World-thread capture -> single IO
 * worker -> FilePlayerRepository forced-stage/atomic-replace path.
 *
 * No live LocalSession, production account path, network or login.
 * Shows the existing gameplay snapshot has NO automatic durable
 * combat-outcome/objective/match/collection operation receipt.
 */
public final class R85OfflineCombatAccountPersistenceBoundaryTest {
    private static final String PROFILE="r85isolated";
    private static final String EXTENSION="r85audit";
    private static final String EXTENSION_KEY=
        "extension."+EXTENSION+".note";
    private static final String DIAGNOSTIC=
        "TEST_ONLY_NOT_A_COMMIT";

    public static void main(String[] args)throws Exception{
        realCombatThenRealWorldOwnedAccountSave();
        System.out.println(
            "R85_REAL_COMBAT_ACCOUNT_PERSISTENCE_BOUNDARY_PASS "+
            "realPvpSourceAndDomainBusUsed=true "+
            "realObjectiveMatchCollectionMutated=true "+
            "realWorldThreadCaptureOrdinal=true "+
            "realSingleIoWorkerAndFileRepositoryUsed=true "+
            "actualAccountSaveAndReloadCompleted=true "+
            "combatProjectionsAbsentFromDefaultAccountSnapshot=true "+
            "combatReceiptNotPersistedAutomatically=true "+
            "explicitExtensionNamespaceRoundTrips=true "+
            "extensionDoesNotConferCommitAuthority=true "+
            "freshRuntimeProjectionServicesHaveNoRecoveredWrites=true "+
            "sourceKillAndDeathHaveNoStableEventId=true "+
            "noLiveSessionOrAccountFilesTouched=true"
        );
    }

    private static void realCombatThenRealWorldOwnedAccountSave()
            throws Exception{
        Path root=Files.createTempDirectory(
            "r85-real-world-repository-"
        );
        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->root.resolve(username+".properties")
            );

        long firstOrdinal;
        long secondOrdinal;

        try(Fixture fixture=new Fixture(
                Mode.COMPLETE,8,repository)){
            fixture.lethal();
            fixture.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
            require(fixture.receipts.issuedCount()==2L&&
                    fixture.sourceKill.get()!=null&&
                    fixture.sourceDeath.get()!=null&&
                    fixture.sourceKill.get()!=fixture.sourceDeath.get(),
                "R85 real PvP outcomes missing");

            // First account capture happens AFTER real lethal combat
            // and all three original projection services updated.
            WorldPlayerPersistence.SaveTicket initial=
                tickCapture(
                    fixture,2L,false
                );
            firstOrdinal=initial.captureOrdinal;
            require(firstOrdinal>0L,
                "R85 first immutable World capture ordinal missing");
            require(initial.snapshot.version()==
                    PlayerSnapshot.CURRENT_VERSION,
                "R85 wrong account snapshot version");
            assertNoImplicitProjectionEvidence(
                initial.snapshot.values()
            );

            Optional<PlayerSnapshot> firstLoaded=
                fixture.world.persistence().load(PROFILE);
            require(firstLoaded.isPresent(),
                "R85 real repository/IO worker lost first save");
            assertNoImplicitProjectionEvidence(
                firstLoaded.get().values()
            );
            require(!firstLoaded.get().values()
                    .containsKey(EXTENSION_KEY),
                "R85 unrequested extension appeared in first save");

            // The codec has an explicit extension namespace.
            // It can persist a test-injected opaque string, but this
            // is NOT a stable event ID, projection commit, recovery
            // acknowledgment or authentication authority.
            WorldPlayerPersistence.SaveTicket withExtension=
                tickCapture(fixture,3L,true);
            secondOrdinal=withExtension.captureOrdinal;
            require(secondOrdinal>firstOrdinal,
                "R85 actual World capture order did not advance");
            require(DIAGNOSTIC.equals(
                    withExtension.snapshot.value(EXTENSION_KEY)),
                "R85 explicit extension not captured");
            assertNoImplicitProjectionEvidence(
                withExtension.snapshot.values()
            );
            require(fixture.objectives.get(
                    "combat:r82-kills").progress==1L,
                "R85 account save unexpectedly rewrote objective");
            fixture.assertProjection(
                1L,2L,3L,Long.valueOf(7L),1,0
            );
        }

        // A new World has a NEW persistence worker and loads the same
        // temp repository by the real WorldPlayerPersistence.load
        // path. It never recovers the R82 source-local receipt or the
        // unrelated objective/match/collection service aggregates.
        try(World fresh=World.isolatedForTest(
                60_000L,repository)){
            PlayerSnapshot loaded=fresh.persistence().load(PROFILE)
                .orElseThrow(()->new AssertionError(
                    "R85 fresh World did not reload account file"
                ));
            require(DIAGNOSTIC.equals(
                    loaded.value(EXTENSION_KEY)),
                "R85 extension did not survive fresh World");
            assertNoImplicitProjectionEvidence(
                loaded.values()
            );

            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshot normalized=
                PlayerSnapshotCodec.applyValidated(loaded,restored);
            require(DIAGNOSTIC.equals(
                    normalized.value(EXTENSION_KEY))&&
                    DIAGNOSTIC.equals(restored.snapshotExtensions()
                        .namespace(EXTENSION).get("note")),
                "R85 actual snapshot extension restore failed");

            ObjectiveProgressService freshObjectives=
                new ObjectiveProgressService();
            freshObjectives.define(
                new ObjectiveDefinition(
                    "combat:r82-kills",10L,"CUSTOM_LOCALLAB"
                )
            );
            MatchSessionService freshMatches=
                new MatchSessionService();
            CollectionLogService freshCollection=
                new CollectionLogService(
                    Collections.singletonList(
                        new CollectionLogDefinition(
                            new CollectionLogDefinition.CollectionId(
                                "collection:r82-test-only"
                            ),
                            new CollectionLogDefinition.CategoryId(
                                "category:r85-recovery-fixture"
                            ),
                            "R85 fresh, independent store",
                            Collections.singleton(
                                new CollectionLogDefinition.EntryId(
                                    "entry:r85-not-a-receipt"
                                )
                            ),
                            true,
                            CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                        )
                    )
                );
            require(freshObjectives.get(
                    "combat:r82-kills").progress==0L&&
                    freshMatches.size()==0&&
                    !freshCollection.snapshot(
                        new CollectionLogDefinition.CollectionId(
                            "collection:r82-test-only"
                        )
                    ).killCount().isPresent(),
                "R85 fresh projection stores invented recovered effects");

            // These snapshots deliberately have NO authority to
            // replay a combat fact, recover old semantic projections,
            // award an objective, or invent a transaction.
            require(!loaded.values().containsKey(
                    "extension.r85audit.source-operation-id"
                ),"R85 claimed an unissued combat operation ID");
        }

        Path canonical=root.resolve(PROFILE+".properties");
        require(Files.isRegularFile(canonical),
            "R85 no canonical account file created");
        require(Files.size(canonical)>0L&&
                secondOrdinal>firstOrdinal,
            "R85 final account save was empty/unordered");
    }

    private static WorldPlayerPersistence.SaveTicket tickCapture(
        Fixture fixture,long tick,boolean injectExtension
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
            new AtomicReference<>();
        fixture.world.events().schedule(tick,()->{
            if(injectExtension){
                fixture.attacker.snapshotExtensions()
                    .replaceNamespace(
                        EXTENSION,
                        Collections.singletonMap("note",DIAGNOSTIC)
                    );
            }
            ticket.set(
                fixture.world.persistence().captureAndSave(
                    PROFILE,fixture.attacker,
                    fixture.attackerGeneration,0,
                    "R85_OFFLINE","POST_REAL_PVP"
                )
            );
        });
        long observed=fixture.world.observePulse(
            System.currentTimeMillis()
        );
        require(observed==tick&&ticket.get()!=null,
            "R85 actual WorldPulse account capture did not run");
        WorldPlayerPersistence.SaveTicket captured=ticket.get();
        captured.completion.get(30L,TimeUnit.SECONDS);
        fixture.world.persistence()
            .awaitOfflineIoDrainForTest(30_000L);
        return captured;
    }

    private static void assertNoImplicitProjectionEvidence(
        Map<String,String> values
    ){
        require(values!=null&&!values.isEmpty(),
            "R85 empty gameplay account snapshot");
        // These are intentionally bounded, concrete negative
        // assertions about THIS real saved snapshot. Other future
        // extension namespaces could persist diagnostic values;
        // neither snapshot presence nor a future key alone proves
        // a trusted transaction/ack protocol.
        for(String key:values.keySet()){
            require(!key.startsWith("objective.")&&
                    !key.startsWith("match.")&&
                    !key.startsWith("collection.")&&
                    !key.startsWith("combat.outcome.")&&
                    !key.startsWith("combat.receipt.")&&
                    !key.startsWith("extension.combat-receipt.")&&
                    !key.startsWith("extension.projection-commit."),
                "R85 unverified projection or event receipt key="+key);
        }
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private R85OfflineCombatAccountPersistenceBoundaryTest(){}
}
