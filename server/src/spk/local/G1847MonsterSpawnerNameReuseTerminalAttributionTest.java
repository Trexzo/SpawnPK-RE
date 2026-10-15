package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G18.4.7 / #2547 / #2558:
 * Characterizes the EXISTING name-only production terminal observer when
 * a live WorldPlayer replaces the original spawn recipient between spawn
 * and finalization. This deliberately records a negative LocalLab behavior,
 * NOT a policy fix or original SpawnPK reward semantics.
 */
public final class G1847MonsterSpawnerNameReuseTerminalAttributionTest {
    private static final String USER="g1847-reused-recipient";

    public static void main(String[] args)throws Exception{
        CaseResult replacement=runCase(true);
        CaseResult absent=runCase(false);
        boolean lateTicketReuse=lateReplacementAfterTicketScheduled();
        boolean lateSettlementCredit=pendingSettlementBeforeReconnect();
        boolean consumerReplayGap=controlledConsumerReplayIdempotencyGap();
        boolean serviceReceiptReset=pvmRecordInstanceReceiptReset();
        boolean fileRoundTripReceiptLoss=pvmRecordFileRoundTripReceiptLoss();
        boolean replayPrecedesOwnership=pvmReceiptReplayPrecedesOriginCheck();
        boolean secondTickDistinct=pvmSameNpcSecondDeathTickCountsAsNewReceipt();
        boolean rejectedG1PartialTerminal=g1InvalidPvmStateAllowsOtherTerminalConsumers();
        boolean g18FailureNonRetryable=actualG18ConsumerFailureAfterG1();
        boolean earlyG4Failure=actualG4FailureStopsLaterG18();
        boolean stickyG4Invalid=invalidG4PersistenceWhileRealOtherConsumersAdvance();
        boolean generationBoundary=g4InvalidCacheScopedToReplacedWorldPlayer();
        boolean liveG4Transferred=activeG4TaskFinishesForNewSameNamePlayer();
        boolean staleG4Collision=staleG4ActiveSnapshotRejectedAfterReplacementCompletion();
        boolean worldScopeContrast=validActiveSnapshotCollidesOnlyWithExistingWorldTask();
        boolean tickProvenanceDrift=validActiveG4RehydrationChangesAssignedTick();

        boolean replacementCredited=
            replacement.finalized&&
            replacement.originalOriginPreserved&&
            replacement.replacementGotLegacyCredit&&
            replacement.oldGenerationOptInRefused&&
            replacement.progressWasExactlyOne&&
            replacement.g1RecordCreditedReplacement&&
            replacement.oldGenerationRespawnedForReplacement;
        boolean noCurrentRecipientNoCredit=
            absent.finalized&&
            absent.originalOriginPreserved&&
            absent.progressWasExactlyZero&&
            absent.g1RecordNotCreditedWhenAbsent&&
            absent.noOwnerNoRespawn;

        require(replacementCredited,
            "same username replacement did not reproduce observed credit");
        require(noCurrentRecipientNoCredit,
            "no-current-recipient terminal event unexpectedly credited");

        System.out.println(
            "G1847_NAME_REUSE_TERMINAL_ATTRIBUTION_CHARACTERIZATION_PASS"+
            " actualProvisioningObserver=true"+
            " sameUsernameReplacementCredited="+replacementCredited+
            " oldSpawnGenerationRefusedByOptIn=true"+
            " noCurrentRecipientNoCredit="+noCurrentRecipientNoCredit+
            " liveObserverStillNameOnly=true"+
            " originalServerRewardPolicyClaim=false"+
            " securityRemediationClaim=false"
        );
        System.out.println(
            "G1848_PVM_RECORD_USERNAME_REUSE_CHARACTERIZATION_PASS"+
            " originalSpawnGenerationNotCredited=true"+
            " replacementCurrentGenerationCredited="+
                replacement.g1RecordCreditedReplacement+
            " noCurrentRecipientNoRecord="+
                absent.g1RecordNotCreditedWhenAbsent+
            " originalServerKillOwnershipClaim=false"+
            " productionRecordPolicyChanged=false"
        );
        require(lateTicketReuse,
            "old-generation pending respawn ticket did not cross reconnect");
        System.out.println(
            "G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS"+
            " oldNpcRespawnedForCurrentName="+
                replacement.oldGenerationRespawnedForReplacement+
            " scheduledUnderOldGenerationThenReused="+
                lateTicketReuse+
            " missingAtTerminalNoRespawn="+
                absent.noOwnerNoRespawn+
            " originalServerRespawnPolicyClaim=false"+
            " productionRespawnPolicyChanged=false"
        );
        require(lateSettlementCredit,
            "pending settlement retry did not expose current-name credit");
        System.out.println(
            "G1850_PENDING_SETTLEMENT_GENERATION_REUSE_PASS"+
            " pendingBeforeReconnect=true"+
            " creditOnlyAfterSettlement=true"+
            " originalSpawnGenerationRetained=true"+
            " replacementDmmCredited="+lateSettlementCredit+
            " replacementG1RecordCredited="+lateSettlementCredit+
            " dropResolutionReplayed=false"+
            " originalServerRewardPolicyClaim=false"+
            " productionCreditPolicyChanged=false"
        );
        require(consumerReplayGap,
            "manual replay must show G1 dedup but G18 duplicate progress");
        System.out.println(
            "G1852_TERMINAL_CONSUMER_REPLAY_ASYMMETRY_PASS"+
            " installedObserverExecutedOnce=true"+
            " manualG1ReplayDeduplicated="+consumerReplayGap+
            " manualProgressionReplayAdvancedDmmTwice="+consumerReplayGap+
            " productionAutomaticReplay=false"+
            " additionalDropSettlement=false"+
            " originalServerReplayPolicyClaim=false"
        );
        require(serviceReceiptReset,
            "PvmRecordService receipt lifetime unexpectedly survived reconstruction");
        System.out.println(
            "G1853_PVM_RECORD_SERVICE_RECEIPT_LIFETIME_PASS"+
            " installedObserverFirstDelivery=true"+
            " originalServiceDeduplicates=true"+
            " freshServiceAppliesSameKillAgain="+serviceReceiptReset+
            " playerNamespaceKillCounterIncrementsTwice="+serviceReceiptReset+
            " JVMRestartOrFilePersistenceTested=false"+
            " productionReceiptPersistenceChanged=false"+
            " originalServerReceiptPolicyClaim=false"
        );
        require(fileRoundTripReceiptLoss,
            "direct file snapshot round trip unexpectedly retained event receipt");
        System.out.println(
            "G1854_PVM_RECORD_FILE_ROUNDTRIP_RECEIPT_GAP_PASS"+
            " actualInstalledG1InitialCredit=true"+
            " realFileRepositorySavedSnapshot=true"+
            " restoredPlayerNamespaceKillsOne=true"+
            " freshWorldReceiptMapEmpty=true"+
            " manuallyRepeatedKillAppliedAfterReload="+fileRoundTripReceiptLoss+
            " persistedCounterAdvancedToTwo="+fileRoundTripReceiptLoss+
            " actualSessionSaveOrJVMRestartTested=false"+
            " originalServerReceiptPolicyClaim=false"
        );
        require(replayPrecedesOwnership,
            "G1855 receipt replay ordering did not match generation fence");
        System.out.println(
            "G1855_PVM_RECEIPT_REPLAY_PRECEDES_OWNERSHIP_PASS"+
            " staleOriginalGotReplayReceipt="+replayPrecedesOwnership+
            " replacementGotReplayButZeroKills="+replayPrecedesOwnership+
            " unseenDeathTickRejectedForStaleGeneration=true"+
            " noMutationOnReplay=true"+
            " consumerReceiptNotAuthorizationProof=true"+
            " originalServerCreditPolicyClaim=false"+
            " productionPolicyChanged=false"
        );
        require(secondTickDistinct,
            "G1856 same canonical NPC with second manual tick was not new receipt");
        System.out.println(
            "G1856_PVM_SAME_NPC_DIFFERENT_TICK_RECORD_IDENTITY_PASS"+
            " realInstalledObserverInitialKill=true"+
            " sameNpcSameTickReplay=true"+
            " sameNpcNewTickManualRecordAccepted="+secondTickDistinct+
            " killsIncrementedWithoutNewNpcDeath="+secondTickDistinct+
            " extraGroundSettlement=false"+
            " productionAutomaticDelivery=false"+
            " originalServerReceiptPolicyClaim=false"+
            " productionPolicyChanged=false"
        );
        require(rejectedG1PartialTerminal,
            "G1857 rejected G1 record must leave observed partial terminal");
        System.out.println(
            "G1857_G1_INVALID_PVM_STATE_PARTIAL_TERMINAL_PASS"+
            " installedObserverExecuted=true"+
            " oneCanonicalNpcDeath=true"+
            " invalidPvmNamespaceRejected=true"+
            " g1RecordReceiptAbsent=true"+
            " otherDmmProgressAdvanced="+rejectedG1PartialTerminal+
            " actualGroundSettlementOne=true"+
            " respawnDespiteRejectedRecord="+rejectedG1PartialTerminal+
            " originalServerPolicyClaim=false"+
            " productionCodeChanged=false"
        );
        require(g18FailureNonRetryable,
            "G1858 downstream G18 failure did not leave committed nonretryable finalization");
        System.out.println(
            "G1858_INSTALLED_G18_CONSUMER_FAILURE_NO_RETRY_PASS"+
            " oneRealNpcDeathAndDrop=true"+
            " g1ReceiptAndKillCommitted="+g18FailureNonRetryable+
            " g18ObjectiveFixtureCorrupted=true"+
            " realInstalledObserverFailureCaught=true"+
            " terminalResultStillFinalized=true"+
            " settlementRetryNotOwned=true"+
            " realG1RespawnStillScheduled=true"+
            " repairedConsumerStillZeroProgress="+g18FailureNonRetryable+
            " productionObserverUnchanged=true"+
            " originalServerPolicyClaim=false"
        );
        require(earlyG4Failure,
            "G1859 early G4 exception did not block downstream G18");
        System.out.println(
            "G1859_INSTALLED_G4_EARLY_FAILURE_BLOCKS_G18_PASS"+
            " realG1KillAndDrop=true"+
            " activeG4TaskModeMismatch=true"+
            " upstreamG1ReceiptCommitted="+earlyG4Failure+
            " repairedG4TaskStillZero="+earlyG4Failure+
            " downstreamG18NeverCredited="+earlyG4Failure+
            " retrySettlementCannotRedeliver=true"+
            " realDueTickRespawnScheduled=true"+
            " oneCanonicalNpcDeath=true"+
            " installedObserverUnchanged=true"+
            " externalClientCorruptionProven=false"+
            " originalServerPolicyClaim=false"
        );
        require(stickyG4Invalid,
            "G1860 invalid G4 persistence did not remain fail-closed across actual terminal events");
        System.out.println(
            "G1860_G4_INVALID_PERSISTENCE_STICKY_PARTIAL_DELIVERY_PASS"+
            " installedObserver=true"+
            " twoDistinctCanonicalDeaths="+stickyG4Invalid+
            " g4InvalidNonthrowing=true"+
            " repairedNamespaceStillQuarantined="+stickyG4Invalid+
            " g1KillsTwoAndReceiptsTwo="+stickyG4Invalid+
            " g18DmmProgressTwo="+stickyG4Invalid+
            " twoGroundSettlements="+stickyG4Invalid+
            " g1DueTickRespawnObserved=true"+
            " manualReplayOrGrant=false"+
            " originalServerPolicyClaim=false"+
            " productionCodeChanged=false"
        );
        require(generationBoundary,
            "G1861 G4 cache leaked to replacement or World-owned DMM lost progress");
        System.out.println(
            "G1861_G4_GENERATION_REPLACEMENT_DMM_RESUME_PASS"+
            " twoRealCanonicalDeaths=true"+
            " oldG4InvalidPersistenceCached=true"+
            " oldGenerationDetached=true"+
            " replacementG4StartsClean="+generationBoundary+
            " replacementG4RealKillCompletesTask="+generationBoundary+
            " replacementDmmResumesOneThenTwo="+generationBoundary+
            " g1KillOnePerWorldPlayer="+generationBoundary+
            " independentG1ReceiptsTwo=true"+
            " respawnBoundToReplacementGeneration=true"+
            " twoOneCoinGroundSettlements=true"+
            " customLocalLabOnly=true"+
            " productionCodeChanged=false"+
            " originalServerPolicyClaim=false"
        );
        require(liveG4Transferred,
            "G1862 original G4 task did not transfer to new same-name WorldPlayer as characterized");
        System.out.println(
            "G1862_G4_ACTIVE_TASK_NAME_REUSE_PERSISTENCE_SPLIT_PASS"+
            " actualInstalledObserver=true"+
            " oneCanonicalNpcDeath=true"+
            " immutableSpawnOriginOldPlayer=true"+
            " originalG4ActivePersistenceRemains="+liveG4Transferred+
            " replacementG4CompletedPersistence="+liveG4Transferred+
            " liveG4TaskTransferredByName="+liveG4Transferred+
            " g1KillCreditedOnlyReplacement="+liveG4Transferred+
            " g18DmmCreditedSameName=true"+
            " actualGroundSettlementOnce=true"+
            " externalTcpReconnectTested=false"+
            " originalServerCreditAuthority=false"+
            " productionCodeChanged=false"
        );
        require(staleG4Collision,
            "G1863 stale ACTIVE G4 snapshot collision did not stay quarantined on third owner");
        System.out.println(
            "G1863_REHYDRATED_ACTIVE_G4_COLLIDES_WITH_COMPLETED_WORLD_TASK_PASS"+
            " exactValidOldActiveSnapshot=true"+
            " actualTwoNpcDeaths=true"+
            " aActiveBCompletedCActiveSnapshot=true"+
            " thirdOwnerHydrationCollision="+staleG4Collision+
            " g4SoftRefusalAfterCollision="+staleG4Collision+
            " g1BAndCKillsDistinct=true"+
            " g18WorldProgressTwo=true"+
            " originalG4TaskNotReplayed=true"+
            " realDelayedRespawnToC=true"+
            " twoCoinSettlements=true"+
            " fileSaveOrTcpRelogTested=false"+
            " originalServerPolicyClaim=false"+
            " productionChanged=false"
        );
        require(worldScopeContrast,
            "G1864 identical valid G4 ACTIVE bytes must collide in completed World but rehydrate in fresh World");
        System.out.println(
            "G1864_G4_VALID_ACTIVE_WORLD_SCOPE_CONTRAST_PASS"+
            " identicalCodecValidActiveSnapshot=true"+
            " originalWorldCompletedTask=true"+
            " originalWorldSameNameHydrationCollision="+worldScopeContrast+
            " independentFreshWorldHydrationAccepted="+worldScopeContrast+
            " separateInstalledNpcTerminals=true"+
            " newWorldG4CompletedNormally="+worldScopeContrast+
            " eachWorldOneG1Receipt=true"+
            " noAccountFileOrSocketReload=true"+
            " noOriginalServerPolicyClaim=true"+
            " productionCodeChanged=false"
        );
        require(tickProvenanceDrift,
            "G1865 valid ACTIVE G4 task hydration unexpectedly preserved original assignedTick");
        System.out.println(
            "G1865_G4_ACTIVE_SOURCE_ASSIGNED_TICK_PROVENANCE_DRIFT_PASS"+
            " originalCodecValidAssignedTick77=true"+
            " freshWorldReplayTickDifferent="+tickProvenanceDrift+
            " hydrationRebuildsRuntimeAssignedTick="+tickProvenanceDrift+
            " oneRealInstalledNpcDeath=true"+
            " persistedPostimageRewritesAssignedTick="+tickProvenanceDrift+
            " originalPersistedSnapshotUnchanged=true"+
            " noActualFileRepositoryOrTcp=true"+
            " originalServerPolicyClaim=false"+
            " productionCodeChanged=false"
        );
    }

    /**
     * G18.6.5: G4 correctly decodes sourceAssignedTick from a valid
     * ACTIVE extension but uses current World clock tick for replay
     * task assignment; after a genuine terminal, the persisted
     * postimage contains the new replay tick rather than original
     * provenance. Controlled test-only in-memory copy; NOT a file
     * reload or durable/original SpawnPK time semantics.
     */
    private static boolean validActiveG4RehydrationChangesAssignedTick()
        throws Exception{
        final String account=USER+"-g4-assigned-tick-drift";
        final long originalAssignedTick=77L;
        World sourceWorld=World.isolatedForTest(60_000L);
        World replayWorld=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer restored=new WorldPlayer();
        long originalGeneration=-1L;
        long restoredGeneration=-1L;
        try{
            originalGeneration=sourceWorld.registerPlayer(
                original,account
            );
            LocalLabSlayerRuntime.StartResult active=
                sourceWorld.localLabSlayer().startMonsterHunter(
                    account,originalAssignedTick
                );
            require(active!=null&&active.created&&
                    active.status.task!=null&&
                    active.status.task.assignedTick==
                        originalAssignedTick&&
                    active.status.task.objective.progress==0L,
                "G1865 original task assignment retains explicit source tick");

            java.util.SortedMap<String,String> sourceExtension=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE
                );
            LocalLabSlayerPersistence.Snapshot before=
                LocalLabSlayerPersistence.decode(
                    sourceExtension
                );
            require(before!=null&&
                    before.state==
                        LocalLabSlayerPersistence.State.ACTIVE&&
                    before.sourceAssignedTick==
                        originalAssignedTick&&
                    before.progress==0L,
                "G1865 original persisted ACTIVE snapshot source tick intact");

            final long replayTick=replayWorld.clock().tick();
            require(replayTick!=originalAssignedTick&&
                    replayTick>=0L,
                "G1865 fresh World replay clock deliberately differs");
            restoredGeneration=replayWorld.registerPlayer(
                restored,account
            );
            // Exactly the same valid extension, copied test-side;
            // no file repository/real account login is exercised.
            synchronized(restored.mutationLock()){
                restored.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    sourceExtension
                );
            }
            LocalLabSlayerRuntime slayer=
                replayWorld.localLabSlayer();
            LocalLabSlayerRuntime.StatusSnapshot rebuilt=
                slayer.status(account);
            require(rebuilt!=null&&
                    rebuilt.persistenceError==null&&
                    rebuilt.task!=null&&
                    rebuilt.active()&&
                    rebuilt.task.assignedTick==replayTick&&
                    rebuilt.task.assignedTick!=originalAssignedTick&&
                    rebuilt.task.objective.progress==0L&&
                    sourceExtension.equals(
                        restored.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE
                        )
                    ),
                "G1865 fresh World restores task but changes runtime assignedTick");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(replayWorld);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==restored&&
                    spawned.runtime.recipientOrigin.generation==
                        restoredGeneration&&
                    replayWorld.npcLifecycle().applyDamage(
                        npc.id,99,211L
                    ).newlyDied,
                "G1865 independent World canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            LocalLabSlayerRuntime.StatusSnapshot completed=
                slayer.status(account);
            LocalLabSlayerPersistence.Snapshot postimage=
                LocalLabSlayerPersistence.decode(
                    restored.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );
            return terminal!=null&&
                terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&
                terminal.settlement.groundItems.size()==1&&
                terminal.settlement.groundItems.get(0)
                    .settledAmount==1&&
                completed!=null&&completed.complete()&&
                completed.task!=null&&
                completed.task.assignedTick==replayTick&&
                completed.task.objective.progress==1L&&
                postimage!=null&&
                postimage.state==
                    LocalLabSlayerPersistence.State.COMPLETED&&
                postimage.progress==1L&&
                postimage.sourceAssignedTick==replayTick&&
                postimage.sourceAssignedTick!=
                    originalAssignedTick&&
                LocalLabSlayerPersistence.decode(
                    original.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                ).sourceAssignedTick==originalAssignedTick&&
                replayWorld.pvmRecords().snapshot(restored).kills==1L&&
                replayWorld.pvmRecords().receiptCount()==1&&
                replayWorld.groundItems().size()==1&&
                provisioned.runtime().get(npc.id)==null;
        }finally{
            if(restoredGeneration>=0L&&
                    replayWorld.players().owns(restored,restoredGeneration))
                replayWorld.unregisterPlayer(restored,restoredGeneration);
            if(originalGeneration>=0L&&
                    sourceWorld.players().owns(original,originalGeneration))
                sourceWorld.unregisterPlayer(original,originalGeneration);
            replayWorld.close();
            sourceWorld.close();
        }
    }

    /**
     * G18.6.4: same EXACT codec-valid G4 ACTIVE extension has
     * different semantic outcome based on World-local SlayerTaskService.
     * It is refused against a completed task of the same username
     * in World A but legitimately restores on a clean independent
     * World B and completes after its own real canonical NPC death.
     * Deliberate test-only in-memory extension copies. No actual disk
     * save/load, power loss, account session, original SpawnPK rules.
     */
    private static boolean validActiveSnapshotCollidesOnlyWithExistingWorldTask()
        throws Exception{
        final String account=USER+"-g4-world-scope-contrast";
        World first=World.isolatedForTest(60_000L);
        World second=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer oldWorldCopy=null;
        WorldPlayer newWorldCopy=null;
        long genA=-1L;
        long genOldCopy=-1L;
        long genNewCopy=-1L;
        try{
            genA=first.registerPlayer(original,account);
            LocalLabSlayerRuntime originalSlayer=first.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                originalSlayer.startMonsterHunter(
                    account,first.clock().tick()
                );
            require(started!=null&&started.created&&
                    started.status.task!=null&&
                    started.status.task.objective.progress==0L,
                "G1864 original G4 task valid before kill");
            java.util.SortedMap<String,String> activeCopy=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE
                );
            LocalLabSlayerPersistence.Snapshot parsed=
                LocalLabSlayerPersistence.decode(activeCopy);
            require(parsed!=null&&parsed.state==
                    LocalLabSlayerPersistence.State.ACTIVE&&
                    parsed.progress==0L&&parsed.completions==0L,
                "G1864 captured original valid ACTIVE namespace");

            LocalMonsterSpawnerActivationRuntime firstProvisioning=
                LocalLabMonsterSpawnerProvisioning.create(first);
            MonsterSpawnerService firstSpawner=
                firstProvisioning.service();
            firstSpawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            firstSpawner.selectRow(account,0);
            firstSpawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                firstProvisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npcA=firstSpawn.spawn.combat.spawn.npc;
            require(first.npcLifecycle().applyDamage(
                    npcA.id,99,701L
                ).newlyDied,
                "G1864 first canonical World death");
            MonsterSpawnerPvmRuntime.FinalizeResult settledA=
                firstProvisioning.runtime().finalizeIfOwned(npcA);
            LocalLabSlayerPersistence.Snapshot completedA=
                LocalLabSlayerPersistence.decode(
                    original.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );
            require(settledA!=null&&settledA.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settledA.settlement!=null&&
                    settledA.settlement.groundItems.size()==1&&
                    settledA.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    completedA!=null&&completedA.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    completedA.progress==1L&&
                    completedA.completions==1L&&
                    first.pvmRecords().snapshot(original).kills==1L&&
                    first.pvmRecords().receiptCount()==1,
                "G1864 original World committed G4 completion");
            require(first.unregisterPlayer(original,genA),
                "G1864 original World first owner detach");

            // SAME WORLD: completed latestTask(account) is present,
            // so an old valid ACTIVE namespace cannot be replayed.
            oldWorldCopy=new WorldPlayer();
            genOldCopy=first.registerPlayer(oldWorldCopy,account);
            synchronized(oldWorldCopy.mutationLock()){
                oldWorldCopy.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,activeCopy
                );
            }
            LocalLabSlayerRuntime.StatusSnapshot collided=
                originalSlayer.status(account);
            require(collided!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        collided.persistenceError
                    )&&
                    collided.task==null&&
                    activeCopy.equals(
                        oldWorldCopy.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE
                        )
                    ),
                "G1864 same-World older ACTIVE namespace rejected");

            // FRESH WORLD: no previous latestTask/account state;
            // exactly the same valid persisted bytes can restore.
            newWorldCopy=new WorldPlayer();
            genNewCopy=second.registerPlayer(newWorldCopy,account);
            synchronized(newWorldCopy.mutationLock()){
                newWorldCopy.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,activeCopy
                );
            }
            require(activeCopy.equals(
                    newWorldCopy.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                ),
                "G1864 two World copies must have exact same ACTIVE extension");
            LocalLabSlayerRuntime freshSlayer=second.localLabSlayer();
            LocalLabSlayerRuntime.StatusSnapshot restored=
                freshSlayer.status(account);
            require(restored!=null&&restored.persistenceError==null&&
                    restored.active()&&restored.task!=null&&
                    restored.task.objective.progress==0L,
                "G1864 clean World restores the exact same ACTIVE state");

            LocalMonsterSpawnerActivationRuntime secondProvisioning=
                LocalLabMonsterSpawnerProvisioning.create(second);
            MonsterSpawnerService nextSpawner=
                secondProvisioning.service();
            nextSpawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            nextSpawner.selectRow(account,0);
            nextSpawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult nextSpawn=
                secondProvisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npcB=nextSpawn.spawn.combat.spawn.npc;
            require(nextSpawn.runtime.recipientOrigin!=null&&
                    nextSpawn.runtime.recipientOrigin.player==newWorldCopy&&
                    nextSpawn.runtime.recipientOrigin.generation==
                        genNewCopy&&
                    second.npcLifecycle().applyDamage(
                        npcB.id,99,801L
                    ).newlyDied,
                "G1864 independent World actual NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult settledB=
                secondProvisioning.runtime().finalizeIfOwned(npcB);
            LocalLabSlayerRuntime.StatusSnapshot afterB=
                freshSlayer.status(account);
            LocalLabSlayerPersistence.Snapshot completedB=
                LocalLabSlayerPersistence.decode(
                    newWorldCopy.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );
            return settledB!=null&&settledB.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                settledB.settlement!=null&&
                settledB.settlement.groundItems.size()==1&&
                settledB.settlement.groundItems.get(0)
                    .settledAmount==1&&
                completedB!=null&&completedB.state==
                    LocalLabSlayerPersistence.State.COMPLETED&&
                completedB.progress==1L&&
                completedB.completions==1L&&
                afterB.persistenceError==null&&
                afterB.task!=null&&
                afterB.task.objective.complete&&
                first.pvmRecords().receiptCount()==1&&
                first.pvmRecords().snapshot(oldWorldCopy).kills==0L&&
                second.pvmRecords().receiptCount()==1&&
                second.pvmRecords().snapshot(newWorldCopy).kills==1L&&
                first.groundItems().size()==1&&
                second.groundItems().size()==1&&
                "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                    originalSlayer.status(account).persistenceError
                )&&
                activeCopy.equals(
                    oldWorldCopy.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                )&&
                firstProvisioning.runtime().get(npcA.id)==null&&
                secondProvisioning.runtime().get(npcB.id)==null;
        }finally{
            if(newWorldCopy!=null&&genNewCopy>=0L&&
                    second.players().owns(newWorldCopy,genNewCopy))
                second.unregisterPlayer(newWorldCopy,genNewCopy);
            if(oldWorldCopy!=null&&genOldCopy>=0L&&
                    first.players().owns(oldWorldCopy,genOldCopy))
                first.unregisterPlayer(oldWorldCopy,genOldCopy);
            if(genA>=0L&&first.players().owns(original,genA))
                first.unregisterPlayer(original,genA);
            second.close();
            first.close();
        }
    }

    /**
     * G18.6.3: an old, VALID ACTIVE G4 namespace still clashes with a
     * completed World-owned task after same-username A->B->C.
     * The third WorldPlayer C uses a test-only COPY of A's extension,
     * not a physical file or an actual network login. G4 detects
     * latestTask(name)!=null, latches an invalidPersistence error and
     * refuses the next REAL terminal nonthrowingly; G1 and G18 continue.
     */
    private static boolean staleG4ActiveSnapshotRejectedAfterReplacementCompletion()
        throws Exception{
        final String account=USER+"-g4-valid-stale-active";
        final long firstDeathTick=601L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=null;
        WorldPlayer ownerC=null;
        long generationA=-1L;
        long generationB=-1L;
        long generationC=-1L;
        try{
            generationA=world.registerPlayer(ownerA,account);
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult began=
                slayer.startMonsterHunter(
                    account,world.clock().tick()
                );
            require(began!=null&&began.created&&
                    began.status.task!=null&&
                    began.status.task.objective.progress==0L,
                "G1863 A starts real G4 Monster Hunter task");

            java.util.SortedMap<String,String> activeA=
                ownerA.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE
                );
            LocalLabSlayerPersistence.Snapshot decodedA=
                LocalLabSlayerPersistence.decode(activeA);
            require(decodedA!=null&&
                    decodedA.state==
                        LocalLabSlayerPersistence.State.ACTIVE&&
                    decodedA.progress==0L&&
                    decodedA.completions==0L,
                "G1863 A's ACTIVE extension must be a VALID codec snapshot");

            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            require(dmm.activateEasyIfCurrent(
                    account,ownerA,generationA
                ).activatedNow,
                "G1863 DMM activation before name replacement");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npcA=firstSpawn.spawn.combat.spawn.npc;
            require(firstSpawn.runtime.recipientOrigin!=null&&
                    firstSpawn.runtime.recipientOrigin.player==ownerA&&
                    firstSpawn.runtime.recipientOrigin.generation==
                        generationA,
                "G1863 A owns immutable original spawn origin");

            require(world.unregisterPlayer(ownerA,generationA),
                "G1863 unregister original A before first death");
            ownerB=new WorldPlayer();
            generationB=world.registerPlayer(ownerB,account);
            require(ownerB.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE
                ).isEmpty()&&
                    slayer.status(account).task!=null,
                "G1863 B sees A's live World task but has no stored G4");
            require(world.npcLifecycle().applyDamage(
                    npcA.id,99,firstDeathTick
                ).newlyDied,
                "G1863 first canonical NPC death under B");
            MonsterSpawnerPvmRuntime.FinalizeResult firstTerminal=
                provisioned.runtime().finalizeIfOwned(npcA);
            PvmRecordService records=world.pvmRecords();
            LocalLabSlayerPersistence.Snapshot completedB=
                LocalLabSlayerPersistence.decode(
                    ownerB.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );
            require(firstTerminal!=null&&
                    firstTerminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    firstTerminal.settlement!=null&&
                    firstTerminal.settlement.groundItems.size()==1&&
                    firstTerminal.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    completedB!=null&&
                    completedB.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    completedB.progress==1L&&
                    completedB.completions==1L&&
                    records.snapshot(ownerA).kills==0L&&
                    records.snapshot(ownerB).kills==1L&&
                    dmm.snapshot(account).objective.progress==1L,
                "G1863 B completed A's active World G4 task");

            require(world.unregisterPlayer(ownerB,generationB),
                "G1863 B leaves after completing A's original task");
            ownerC=new WorldPlayer();
            generationC=world.registerPlayer(ownerC,account);
            // Simulate only a validated stale account namespace after
            // current registration; do NOT call a repository or socket.
            synchronized(ownerC.mutationLock()){
                ownerC.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    activeA
                );
            }
            require(activeA.equals(
                    ownerC.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                )&&
                    LocalLabSlayerPersistence.decode(
                        ownerC.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE
                        )
                    ).state==LocalLabSlayerPersistence.State.ACTIVE,
                "G1863 valid older persisted ACTIVE namespace copied to C");

            LocalLabSlayerRuntime.StatusSnapshot refused=
                slayer.status(account);
            require(refused!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        refused.persistenceError
                    )&&
                    refused.task==null&&
                    dmm.snapshot(account).objective.progress==1L,
                "G1863 completed World task refuses A's valid stale ACTIVE snapshot");

            world.events().runDue(
                firstDeathTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot afterSpawn=
                spawner.getSession(account);
            require(afterSpawn!=null&&
                    afterSpawn.spawnedNpcIds.size()==1,
                "G1863 due-time G1 respawn to C");
            EntityId secondId=afterSpawn.spawnedNpcIds.get(0);
            WorldNpc npcC=world.npcs().byId(secondId);
            MonsterSpawnerPvmRuntime.Snapshot runtimeC=
                provisioned.runtime().get(secondId);
            require(npcC!=null&&
                    !npcA.id.equals(secondId)&&
                    runtimeC!=null&&
                    runtimeC.recipientOrigin!=null&&
                    runtimeC.recipientOrigin.player==ownerC&&
                    runtimeC.recipientOrigin.generation==generationC,
                "G1863 second genuine spawn captures C origin");
            require(world.npcLifecycle().applyDamage(
                    npcC.id,99,firstDeathTick+
                        G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS+1L
                ).newlyDied,
                "G1863 second independently spawned NPC dies");
            MonsterSpawnerPvmRuntime.FinalizeResult secondTerminal=
                provisioned.runtime().finalizeIfOwned(npcC);
            LocalLabSlayerRuntime.StatusSnapshot stillRefused=
                slayer.status(account);
            return secondTerminal!=null&&
                secondTerminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                secondTerminal.settlement!=null&&
                secondTerminal.settlement.groundItems.size()==1&&
                secondTerminal.settlement.groundItems.get(0)
                    .settledAmount==1&&
                stillRefused!=null&&
                "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                    stillRefused.persistenceError
                )&&
                stillRefused.task==null&&
                slayer.status(account).task==null&&
                activeA.equals(
                    ownerA.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                )&&
                activeA.equals(
                    ownerC.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                )&&
                LocalLabSlayerPersistence.decode(
                    ownerB.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                ).state==LocalLabSlayerPersistence.State.COMPLETED&&
                records.snapshot(ownerA).kills==0L&&
                records.snapshot(ownerB).kills==1L&&
                records.snapshot(ownerC).kills==1L&&
                records.receiptCount()==2&&
                dmm.snapshot(account).objective.progress==2L&&
                world.groundItems().snapshot().stream()
                    .filter(item->item.itemId==995&&
                        account.equals(item.owner))
                    .mapToInt(item->item.amount).sum()==2&&
                provisioned.runtime().get(npcA.id)==null&&
                provisioned.runtime().get(npcC.id)==null;
        }finally{
            if(ownerC!=null&&generationC>=0L&&
                    world.players().owns(ownerC,generationC))
                world.unregisterPlayer(ownerC,generationC);
            if(ownerB!=null&&generationB>=0L&&
                    world.players().owns(ownerB,generationB))
                world.unregisterPlayer(ownerB,generationB);
            if(generationA>=0L&&
                    world.players().owns(ownerA,generationA))
                world.unregisterPlayer(ownerA,generationA);
            world.close();
        }
    }

    /**
     * G18.6.2 / #2558 / #2587: A legitimate *active* G4 Monster
     * Hunter task survives WorldPlayer A's unregister because the
     * World-owned SlayerTaskService is keyed by username. Before a
     * single actual NPC terminal event, same-name WorldPlayer B
     * appears with no account snapshot hydration or task assignment.
     *
     * The installed name-only observer then finishes the existing
     * G4 task and writes the completed persistence to B, although
     * A's detached snapshot still contains ACTIVE for that task.
     * NPC RecipientOrigin still records A. This is a negative
     * Custom LocalLab ownership/persistence split, NOT recovered
     * SpawnPK kill policy or user-authorized task transfer.
     */
    private static boolean activeG4TaskFinishesForNewSameNamePlayer()
        throws Exception{
        final String account=USER+"-g4-active-transfer";
        final long deathTick=521L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L;
        long replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(
                original,account
            );
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                slayer.startMonsterHunter(
                    account,world.clock().tick()
                );
            require(started!=null&&started.created&&
                    started.status.task!=null&&
                    started.status.task.objective!=null&&
                    started.status.task.objective.progress==0L,
                "G1862 original player legitimately starts G4 task");
            java.util.SortedMap<String,String> aBefore=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE
                );
            LocalLabSlayerPersistence.Snapshot oldActive=
                LocalLabSlayerPersistence.decode(aBefore);
            require(oldActive!=null&&
                    oldActive.state==
                        LocalLabSlayerPersistence.State.ACTIVE&&
                    oldActive.progress==0L&&
                    oldActive.completions==0L,
                "G1862 A has valid persisted ACTIVE G4 task");

            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activeDmm=
                dmm.activateEasyIfCurrent(
                    account,original,originalGeneration
                );
            require(activeDmm!=null&&activeDmm.activatedNow&&
                    activeDmm.snapshot.objective.progress==0L,
                "G1862 legitimate original DMM activation");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                spawned.runtime.recipientOrigin;
            require(origin!=null&&origin.player==original&&
                    origin.generation==originalGeneration,
                "G1862 actual spawn captured original recipient");

            require(world.unregisterPlayer(
                    original,originalGeneration
                ),
                "G1862 remove original after G4 task/spawn");
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                replacement,account
            );
            require(world.players().owns(
                        replacement,replacementGeneration
                    )&&
                    !origin.stillCurrentIn(world)&&
                    replacement.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    ).isEmpty(),
                "G1862 fresh B has no inherited G4 persistence");

            // World-owned name map still exposes A's active task to B,
            // even before B requests/accepts any G4 task.
            LocalLabSlayerRuntime.StatusSnapshot adopted=
                slayer.status(account);
            require(adopted!=null&&
                    adopted.persistenceError==null&&
                    adopted.task!=null&&
                    adopted.task.taskId.equals(
                        started.status.task.taskId
                    )&&
                    adopted.task.objective.progress==0L,
                "G1862 G4 active task is already visible to replacement");
            require(dmm.snapshot(account).objective.progress==0L,
                "G1862 DMM still World-owned before terminal");

            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1862 one real canonical death of A-spawned NPC");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            PvmRecordService records=world.pvmRecords();
            LocalLabSlayerRuntime.StatusSnapshot after=
                slayer.status(account);
            LocalLabSlayerPersistence.Snapshot aPersisted=
                LocalLabSlayerPersistence.decode(
                    original.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );
            LocalLabSlayerPersistence.Snapshot bPersisted=
                LocalLabSlayerPersistence.decode(
                    replacement.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                );

            return terminal!=null&&
                terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&
                terminal.settlement.groundItems.size()==1&&
                terminal.settlement.groundItems.get(0)
                    .settledAmount==1&&
                terminal.recipientOrigin==origin&&
                terminal.recipientOrigin.player==original&&
                !origin.stillCurrentIn(world)&&
                after!=null&&after.task!=null&&
                after.task.taskId.equals(
                    started.status.task.taskId
                )&&
                after.task.objective.progress==1L&&
                after.task.objective.complete&&
                aPersisted!=null&&
                aPersisted.state==
                    LocalLabSlayerPersistence.State.ACTIVE&&
                aPersisted.progress==0L&&
                aPersisted.completions==0L&&
                bPersisted!=null&&
                bPersisted.state==
                    LocalLabSlayerPersistence.State.COMPLETED&&
                bPersisted.progress==1L&&
                bPersisted.completions==1L&&
                aBefore.equals(
                    original.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE
                    )
                )&&
                records.snapshot(original).kills==0L&&
                records.snapshot(replacement).kills==1L&&
                records.receiptCount()==1&&
                dmm.snapshot(account).objective.progress==1L&&
                world.groundItems().size()==1&&
                provisioned.runtime().get(npc.id)==null;
        }finally{
            if(replacement!=null&&
                    replacementGeneration>=0L&&
                    world.players().owns(
                        replacement,replacementGeneration
                    ))
                world.unregisterPlayer(
                    replacement,replacementGeneration
                );
            if(originalGeneration>=0L&&
                    world.players().owns(
                        original,originalGeneration
                    ))
                world.unregisterPlayer(
                    original,originalGeneration
                );
            world.close();
        }
    }

    /**
     * G18.6.1: demonstrate DIFFERENT lifecycle scopes of three
     * LocalLab consumer states under a new WorldPlayer with the SAME name.
     *
     * G4's invalidPersistence cache uses WorldPlayer identity; G18's DMM
     * map uses the normalized account name and survives World detach;
     * G1's recorded kill counter is in each WorldPlayer's extension.
     * All terminal events use the real installed composition.
     *
     * No account-file save, socket login, original server ownership rule,
     * automated repair, or synthetic duplicate event.
     */
    private static boolean g4InvalidCacheScopedToReplacedWorldPlayer()
        throws Exception{
        final String account=USER+"-g4-generation-boundary";
        final long initialTick=461L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L;
        long replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmm.activateEasyIfCurrent(
                    account,original,originalGeneration
                );
            require(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "G1861 DMM begins before G4 corruption");

            // Deliberately malformed test-only G4 extension on the OLD
            // WorldPlayer; its failed hydration is identity-cached.
            synchronized(original.mutationLock()){
                original.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    java.util.Collections.singletonMap(
                        "version","invalid-test-only")
                );
            }
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            require(slayer.status(account).persistenceError!=null,
                "G1861 old player G4 failure cached");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc first=spawned.spawn.combat.spawn.npc;
            require(spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==original&&
                    spawned.runtime.recipientOrigin.generation==
                        originalGeneration,
                "G1861 first spawn captures original WorldPlayer");

            require(world.npcLifecycle().applyDamage(
                    first.id,99,initialTick
                ).newlyDied,
                "G1861 first canonical death");
            MonsterSpawnerPvmRuntime.FinalizeResult firstResult=
                provisioned.runtime().finalizeIfOwned(first);
            PvmRecordService records=world.pvmRecords();
            require(firstResult!=null&&firstResult.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    firstResult.settlement!=null&&
                    firstResult.settlement.groundItems.size()==1&&
                    firstResult.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(original).kills==1L&&
                    records.receiptCount()==1&&
                    dmm.snapshot(account).objective.progress==1L&&
                    slayer.status(account).persistenceError!=null,
                "G1861 first real observer credited G1+G18 but G4 refused");

            require(world.unregisterPlayer(original,originalGeneration),
                "G1861 original generation detached");
            require(dmm.activePlayerCount()==1&&
                    !world.players().owns(original,originalGeneration),
                "G1861 DMM remains World-scoped after detach");

            // New WorldPlayer, identical account ref, EMPTY extensions.
            // Old identity-cached invalid G4 must not poison new owner.
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                replacement,account
            );
            LocalLabSlayerRuntime.StatusSnapshot newG4=
                slayer.status(account);
            require(newG4!=null&&newG4.persistenceError==null&&
                    newG4.task==null,
                "G1861 exact new WorldPlayer must not inherit G4 invalid cache");
            require(dmm.snapshot(account).objective.progress==1L&&
                    !dmm.activateEasyIfCurrent(
                        account,replacement,replacementGeneration
                    ).activatedNow,
                "G1861 same-name replacement resumes World-owned DMM");

            LocalLabSlayerRuntime.StartResult newTask=
                slayer.startMonsterHunter(account,world.clock().tick());
            require(newTask!=null&&newTask.created&&
                    newTask.status.task!=null&&
                    newTask.status.task.objective.progress==0L,
                "G1861 new generation starts legitimate G4 task");

            // Real G1 delayed respawn uses the current username,
            // and its new recipient origin must be the replacement.
            world.events().runDue(
                initialTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot afterRespawn=
                spawner.getSession(account);
            require(afterRespawn!=null&&
                    afterRespawn.spawnedNpcIds.size()==1,
                "G1861 expected G1 actual delayed respawn");
            EntityId secondId=afterRespawn.spawnedNpcIds.get(0);
            WorldNpc second=world.npcs().byId(secondId);
            MonsterSpawnerPvmRuntime.Snapshot secondOwned=
                provisioned.runtime().get(secondId);
            require(second!=null&&!secondId.equals(first.id)&&
                    secondOwned!=null&&
                    secondOwned.recipientOrigin!=null&&
                    secondOwned.recipientOrigin.player==replacement&&
                    secondOwned.recipientOrigin.generation==
                        replacementGeneration,
                "G1861 real respawn captured the new WorldPlayer generation");

            require(world.npcLifecycle().applyDamage(
                    second.id,99,initialTick+
                        G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS+1L
                ).newlyDied,
                "G1861 second canonical death from replacement spawn");
            MonsterSpawnerPvmRuntime.FinalizeResult secondResult=
                provisioned.runtime().finalizeIfOwned(second);
            LocalLabSlayerRuntime.StatusSnapshot secondG4=
                slayer.status(account);

            return secondResult!=null&&
                secondResult.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                secondResult.settlement!=null&&
                secondResult.settlement.groundItems.size()==1&&
                secondResult.settlement.groundItems.get(0)
                    .settledAmount==1&&
                secondG4!=null&&secondG4.persistenceError==null&&
                secondG4.task!=null&&
                secondG4.task.objective.progress==1L&&
                secondG4.task.objective.complete&&
                records.snapshot(original).kills==1L&&
                records.snapshot(replacement).kills==1L&&
                records.receiptCount()==2&&
                dmm.snapshot(account).objective.progress==2L&&
                world.groundItems().snapshot().stream()
                    .filter(item->
                        item.itemId==995&&
                        account.equals(item.owner)
                    )
                    .mapToInt(item->item.amount).sum()==2&&
                provisioned.runtime().get(first.id)==null&&
                provisioned.runtime().get(second.id)==null;
        }finally{
            if(replacement!=null&&replacementGeneration>=0L&&
                    world.players().owns(replacement,replacementGeneration))
                world.unregisterPlayer(
                    replacement,replacementGeneration
                );
            if(originalGeneration>=0L&&
                    world.players().owns(original,originalGeneration))
                world.unregisterPlayer(original,originalGeneration);
            world.close();
        }
    }

    /**
     * G18.6.0 / #2587: a G4 invalid persisted extension is swallowed by
     * LocalLabSlayerRuntime.ensureRestoredOwned into invalidPersistence for
     * this exact WorldPlayer. G4 returns a soft non-progress result, NOT a
     * thrown observer error. Later installed G18 consumer still receives
     * canonical terminal events. Deleting bad snapshot bytes alone does
     * NOT clear this in-memory fail-closed G4 state.
     *
     * All invalid input is test-owned internal state (not client data).
     */
    private static boolean invalidG4PersistenceWhileRealOtherConsumersAdvance()
        throws Exception{
        final String account=USER+"-g4-invalid-soft-refusal";
        final long firstTick=401L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmm.activateEasyIfCurrent(account,player,generation);
            require(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "G1860 initial G18 objective activated");

            // Internal fixture only: malformed Blood Slayer persistence
            // before its first hydration attempt on this WorldPlayer.
            synchronized(player.mutationLock()){
                player.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    java.util.Collections.singletonMap(
                        "version","invalid-test-only")
                );
            }
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StatusSnapshot blocked=
                slayer.status(account);
            require(blocked!=null&&
                    blocked.persistenceError!=null&&
                    blocked.task==null,
                "G1860 G4 invalid extension must fail closed without throwing");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc first=firstSpawn.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    first.id,99,firstTick
                ).newlyDied,
                "G1860 first actual canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminalOne=
                provisioned.runtime().finalizeIfOwned(first);
            PvmRecordService records=world.pvmRecords();
            require(terminalOne!=null&&
                    terminalOne.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalOne.settlement!=null&&
                    terminalOne.settlement.groundItems.size()==1&&
                    terminalOne.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(player).kills==1L&&
                    records.receiptCount()==1&&
                    dmm.snapshot(account).objective.progress==1L&&
                    slayer.status(account).persistenceError!=null&&
                    world.groundItems().size()==1,
                "G1860 installed observer first G1/G18 accepted with invalid G4 refusal");

            // Removing malformed persisted bytes cannot silently clear
            // G4's cached invalidPersistence on this player instance.
            synchronized(player.mutationLock()){
                player.snapshotExtensions().replaceNamespace(
                    LocalLabSlayerPersistence.NAMESPACE,
                    java.util.Collections.emptyMap()
                );
            }
            LocalLabSlayerRuntime.StatusSnapshot stillBlocked=
                slayer.status(account);
            require(stillBlocked.persistenceError!=null&&
                    stillBlocked.task==null&&
                    dmm.snapshot(account).objective.progress==1L,
                "G1860 repaired persisted bytes must not reauthorize G4 mid-session");

            // Trigger the ACTUAL G1 due-time respawn, then an independent
            // second lifecycle death; no test-only consumer replay.
            world.events().runDue(
                firstTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(account);
            require(session!=null&&session.spawnedNpcIds.size()==1&&
                    !first.id.equals(session.spawnedNpcIds.get(0)),
                "G1860 expected real due-time replacement NPC");
            WorldNpc second=world.npcs().byId(
                session.spawnedNpcIds.get(0)
            );
            require(second!=null&&
                    world.npcLifecycle().applyDamage(
                        second.id,99,
                        firstTick+G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS+1L
                    ).newlyDied,
                "G1860 second distinct canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminalTwo=
                provisioned.runtime().finalizeIfOwned(second);
            LocalLabSlayerRuntime.StatusSnapshot after=
                slayer.status(account);
            return terminalTwo!=null&&
                terminalTwo.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminalTwo.settlement!=null&&
                terminalTwo.settlement.groundItems.size()==1&&
                terminalTwo.settlement.groundItems.get(0)
                    .settledAmount==1&&
                after.persistenceError!=null&&
                after.task==null&&
                records.snapshot(player).kills==2L&&
                records.receiptCount()==2&&
                dmm.snapshot(account).objective.progress==2L&&
                world.groundItems().snapshot().stream()
                    .filter(item->
                        item.itemId==995&&
                        account.equals(item.owner)
                    )
                    .mapToInt(item->item.amount)
                    .sum()==2&&
                provisioned.runtime().get(first.id)==null&&
                provisioned.runtime().get(second.id)==null;
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.9: A G4 failure happens EARLIER in the unchanged installed
     * G1->G4->G14->G15->G16->G18 consumer sequence than G18.5.8's
     * late G18 failure. Mutate only private test-side Blood Slayer
     * selected-mode bookkeeping after legitimate active task creation.
     *
     * No production error injection, client-side manipulation, rewards,
     * outbox, replay, or original server claim.
     */
    private static boolean actualG4FailureStopsLaterG18()
        throws Exception{
        final String account=USER+"-g4-early-throw";
        final long deathTick=341L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                slayer.startMonsterHunter(account,world.clock().tick());
            require(started!=null&&started.created&&
                    started.status.task!=null&&
                    started.status.task.objective!=null&&
                    started.status.task.objective.progress==0L,
                "G1859 valid G4 monster-hunter task before corruption");
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activeDmm=
                dmm.activateEasyIfCurrent(account,player,generation);
            require(activeDmm!=null&&activeDmm.activatedNow&&
                    activeDmm.snapshot.objective.progress==0L,
                "G1859 DMM activated before upstream G4 fault");

            // Deliberate in-memory test-only mismatch. Live G4 protects
            // active task selectors; do not invoke them to bypass policy.
            java.lang.reflect.Field bridge=
                LocalLabSlayerRuntime.class.getDeclaredField("bloodSlayer");
            bridge.setAccessible(true);
            BloodSlayerModeService modes=(BloodSlayerModeService)
                bridge.get(slayer);
            java.lang.reflect.Field selected=
                BloodSlayerModeService.class.getDeclaredField(
                    "selectedByPlayer"
                );
            selected.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String,BloodSlayerModeService.Mode> mapping=
                (java.util.Map<String,BloodSlayerModeService.Mode>)
                    selected.get(modes);
            BloodSlayerModeService.Mode prior=mapping.put(
                account,BloodSlayerModeService.Mode.BOSS_HUNTER_PVM
            );
            require(prior==
                    BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM,
                "G1859 only active G4 task mode mismatched");
            require(slayer.status(account).task!=null&&
                    slayer.status(account).task.objective.progress==0L,
                "G1859 active G4 task still intact");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1859 one real canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            PvmRecordService records=world.pvmRecords();
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    provisioned.runtime().get(npc.id)==null&&
                    world.groundItems().size()==1&&
                    records.snapshot(player).kills==1L&&
                    records.receiptCount()==1&&
                    dmm.snapshot(account).objective.progress==0L,
                "G1859 upstream G1 committed before G4 blocked downstream G18");

            // Repair the exact private mode value before testing the
            // normal retry path; healthy G4 and G18 still cannot recover
            // their missing terminal event.
            require(mapping.put(account,prior)==
                    BloodSlayerModeService.Mode.BOSS_HUNTER_PVM,
                "G1859 restore original live G4 mode");
            require(slayer.status(account).task.objective.progress==0L&&
                    dmm.snapshot(account).objective.progress==0L,
                "G1859 repaired G4 and downstream G18 remain uncredited");
            MonsterSpawnerPvmRuntime.FinalizeResult retry=
                provisioned.runtime().retrySettlement(npc.id);
            MonsterSpawnerPvmRuntime.FinalizeResult rerun=
                provisioned.runtime().finalizeIfOwned(npc);
            require(retry!=null&&retry.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    rerun!=null&&rerun.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    slayer.status(account).task.objective.progress==0L&&
                    dmm.snapshot(account).objective.progress==0L,
                "G1859 no post-repair original terminal replay");

            world.events().runDue(
                deathTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot after=
                spawner.getSession(account);
            return after!=null&&
                after.spawnedNpcIds.size()==1&&
                !npc.id.equals(after.spawnedNpcIds.get(0))&&
                records.receiptCount()==1&&
                records.snapshot(player).kills==1L&&
                slayer.status(account).task.objective.progress==0L&&
                dmm.snapshot(account).objective.progress==0L&&
                world.groundItems().size()==1;
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.8 / #2587: deliberately corrupt ONLY the in-memory G18
     * objective bookkeeping AFTER legitimate objective activation.
     * This triggers the REAL installed downstream G18 consumer's
     * Objects.requireNonNull("objective") in state.snapshot(), after
     * G1 has already committed its kill receipt.
     *
     * Reflection is confined to this regression test. There is no
     * network/client corruption, production injection or reward repair.
     */
    private static boolean actualG18ConsumerFailureAfterG1()
        throws Exception{
        final String account=USER+"-installed-g18-throw";
        final long deathTick=301L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmm.activateEasyIfCurrent(account,player,generation);
            require(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "G1858 valid DMM activation before controlled corruption");

            // Private test-only state corruption: remove exactly one
            // objective entry, not the DMM player or the World owner.
            java.lang.reflect.Field byPlayerField=
                LocalLabDailyMoneyMakingRuntime.class
                    .getDeclaredField("byPlayer");
            byPlayerField.setAccessible(true);
            Object dmmPlayer=((java.util.Map<?,?>)
                byPlayerField.get(dmm)).get(account);
            require(dmmPlayer!=null,
                "G1858 expected registered DMM player state");
            java.lang.reflect.Field objectivesField=
                dmmPlayer.getClass().getDeclaredField("objectives");
            objectivesField.setAccessible(true);
            Object objectives=objectivesField.get(dmmPlayer);
            require(objectives instanceof ObjectiveProgressService,
                "G1858 expected semantic objective service");
            java.lang.reflect.Field entriesField=
                ObjectiveProgressService.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String,Object> entries=
                (java.util.Map<String,Object>)entriesField.get(objectives);
            Object removedObjective=entries.remove(
                LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY
            );
            require(removedObjective!=null,
                "G1858 controlled missing objective entry");
            boolean invalidDmm=false;
            try{
                dmm.snapshot(account);
            }catch(NullPointerException expected){
                invalidDmm="objective".equals(expected.getMessage());
            }
            require(invalidDmm,
                "G1858 failed to arm real G18 snapshot exception");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1858 exactly one canonical NPC death");

            // The production-installed combined observer will run:
            // G1 credits first, later G18 throws; runtime catches Throwable
            // after settlement has removed the canonical pending entry.
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            PvmRecordService records=world.pvmRecords();
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    provisioned.runtime().get(npc.id)==null&&
                    world.groundItems().size()==1&&
                    records.snapshot(player).kills==1L&&
                    records.receiptCount()==1,
                "G1858 actual G1 credit and ground settlement survive G18 exception");

            // Repair ONLY our synthetic corruption. The G18 consumer is
            // healthy again but must not silently receive an old event.
            require(entries.put(
                    LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY,
                    removedObjective
                )==null,
                "G1858 restore exact removed objective entry");
            require(dmm.snapshot(account).objective.progress==0L,
                "G1858 repaired G18 objective was never credited");
            MonsterSpawnerPvmRuntime.FinalizeResult retry=
                provisioned.runtime().retrySettlement(npc.id);
            require(retry!=null&&
                    retry.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    dmm.snapshot(account).objective.progress==0L,
                "G1858 repaired consumer still has no settlement replay");

            // G1 scheduled its next NPC before downstream G18 failed.
            world.events().runDue(
                deathTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot current=
                spawner.getSession(account);
            return current!=null&&
                current.spawnedNpcIds.size()==1&&
                !current.spawnedNpcIds.get(0).equals(npc.id)&&
                world.groundItems().size()==1&&
                records.snapshot(player).kills==1L&&
                records.receiptCount()==1&&
                entries.get(
                    LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY)==
                    removedObjective&&
                dmm.snapshot(account).objective.progress==0L;
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.7 / #2587 — actual installed terminal observer, WITHOUT
     * replacing G1 with a synthetic callback. An intentionally invalid
     * pre-existing PvM extension leads G1 recordIfCurrent to return
     * INVALID_STATE, but the observer ignores that receipt status and
     * proceeds to progression consumers and the delayed respawn.
     *
     * This is a controlled bad LocalLab test fixture, not proof that
     * the real client can inject invalid account bytes. No recovery,
     * original SpawnPK reward rules, or production fix is asserted.
     */
    private static boolean g1InvalidPvmStateAllowsOtherTerminalConsumers()
        throws Exception{
        final String account=USER+"-invalid-g1-extension";
        final long deathTick=241L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult started=
                dmm.activateEasyIfCurrent(account,player,generation);
            require(started!=null&&started.activatedNow&&
                    started.snapshot.objective.progress==0L,
                "G1857 start eligible DMM objective");

            // Controlled invalid local snapshot-extension fixture;
            // G1 must reject without recording a receipt.
            synchronized(player.mutationLock()){
                player.snapshotExtensions().replaceNamespace(
                    PvmRecordService.NAMESPACE,
                    java.util.Collections.singletonMap(
                        "version","invalid-fixture")
                );
            }
            PvmRecordService records=world.pvmRecords();
            require(records.receiptCount()==0&&
                    records.snapshot(player).kills==0L,
                "G1857 invalid initial record and empty receipts");

            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1857 one real canonical lifecycle death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    records.receiptCount()==0&&
                    records.snapshot(player).kills==0L&&
                    dmm.snapshot(account).objective.progress==1L&&
                    world.groundItems().size()==1,
                "G1857 installed observer settled drop and progressed DMM despite rejected G1");

            // Diagnostic package-internal call only: characterize explicit
            // INVALID_STATE on the same pair without mutating gameplay.
            PvmRecordService.Receipt rejected=
                records.recordIfCurrent(
                    player,generation,npc.id,deathTick
                );
            require(!rejected.applied&&!rejected.replayed&&
                    "INVALID_STATE".equals(rejected.reason)&&
                    records.receiptCount()==0,
                "G1857 invalid PvM state rejection reason");

            // Observe real delayed G1 ticket instead of assuming it existed.
            world.events().runDue(
                deathTick+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot after=
                spawner.getSession(account);
            return after!=null&&
                after.spawnedNpcIds.size()==1&&
                !after.spawnedNpcIds.get(0).equals(npc.id)&&
                dmm.snapshot(account).objective.progress==1L&&
                records.snapshot(player).kills==0L&&
                records.receiptCount()==0&&
                world.groundItems().size()==1;
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.6: G1's receipt identity is (npcId,deathTick), NOT
     * (npcId,recipientGeneration) or a canonical lifecycle authorization.
     * Package-internal, deliberately MANUAL recordIfCurrent invocation.
     *
     * This characterizes the custom LocalLab service contract. It is NOT
     * a second canonical NPC death, an external client call, a live exploit,
     * or an assumption about original SpawnPK kill-credit authority.
     */
    private static boolean pvmSameNpcSecondDeathTickCountsAsNewReceipt()
        throws Exception{
        final String account=USER+"-same-npc-different-tick";
        final long deathTick=181L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1856 single actual canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioned.runtime().finalizeIfOwned(npc);
            PvmRecordService records=world.pvmRecords();
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    records.snapshot(player).kills==1L&&
                    records.receiptCount()==1&&
                    world.groundItems().size()==1,
                "G1856 one installed observer receipt and ground settlement");

            // Same event identity is correctly deduplicated.
            PvmRecordService.Receipt same=
                records.recordIfCurrent(player,generation,npc.id,deathTick);
            require(!same.applied&&same.replayed&&
                    "REPLAY".equals(same.reason)&&
                    records.snapshot(player).kills==1L,
                "G1856 exact same event must replay without another credit");

            // No new lifecycle death, finalization, NPC spawn, or settlement:
            // a direct PACKAGE-INTERNAL manual API call supplies another tick.
            PvmRecordService.Receipt differentTick=
                records.recordIfCurrent(
                    player,generation,npc.id,deathTick+1L
                );
            PvmRecordService.Receipt differentTickAgain=
                records.recordIfCurrent(
                    player,generation,npc.id,deathTick+1L
                );

            return differentTick.applied&&
                !differentTick.replayed&&
                "APPLIED".equals(differentTick.reason)&&
                differentTick.kills==2L&&
                !differentTickAgain.applied&&
                differentTickAgain.replayed&&
                "REPLAY".equals(differentTickAgain.reason)&&
                records.snapshot(player).kills==2L&&
                records.receiptCount()==2&&
                world.groundItems().size()==1;
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.5 / #2587: PvmRecordService looks up its World-scoped kill
     * receipt BEFORE checking the caller's WorldPlayer generation. Therefore
     * REPLAY is a fact about prior event identity, NOT proof that this caller
     * still owns the credited account/generation.
     *
     * All second deliveries are explicit test-only manual API calls; no
     * historical SpawnPK server authority is inferred.
     */
    private static boolean pvmReceiptReplayPrecedesOriginCheck()
        throws Exception{
        final String account=USER+"-receipt-ownership-order";
        final long deathTick=151L;
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L;
        long replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,account);
            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1855 real canonical NPC lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult finalized=
                provisioned.runtime().finalizeIfOwned(npc);
            PvmRecordService records=world.pvmRecords();
            require(finalized!=null&&
                    finalized.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finalized.settlement!=null&&
                    records.snapshot(original).kills==1L&&
                    records.receiptCount()==1,
                "G1855 actual G1 observer first kill receipt");

            require(world.unregisterPlayer(
                    original,originalGeneration
                ),
                "G1855 original generation logout");
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                replacement,account
            );
            require(!world.players().owns(
                    original,originalGeneration
                )&&
                    world.players().owns(
                        replacement,replacementGeneration
                    )&&records.snapshot(replacement).kills==0L,
                "G1855 replacement must have no G1 kill credit");

            // Receipt hits are handled before withOpenPlayerMutationOwnership.
            PvmRecordService.Receipt staleOriginal=
                records.recordIfCurrent(
                    original,originalGeneration,npc.id,deathTick
                );
            PvmRecordService.Receipt unrelatedReplacement=
                records.recordIfCurrent(
                    replacement,replacementGeneration,npc.id,deathTick
                );
            // A never-seen event ID DOES reach the ownership check.
            PvmRecordService.Receipt unknownEvent=
                records.recordIfCurrent(
                    original,originalGeneration,npc.id,deathTick+1L
                );

            return staleOriginal.replayed&&
                !staleOriginal.applied&&
                "REPLAY".equals(staleOriginal.reason)&&
                unrelatedReplacement.replayed&&
                !unrelatedReplacement.applied&&
                "REPLAY".equals(unrelatedReplacement.reason)&&
                !unknownEvent.applied&&
                !unknownEvent.replayed&&
                "STALE_PLAYER_GENERATION".equals(unknownEvent.reason)&&
                records.snapshot(original).kills==1L&&
                records.snapshot(replacement).kills==0L&&
                records.receiptCount()==1&&
                world.groundItems().size()==1;
        }finally{
            if(replacement!=null&&
                    world.players().owns(
                        replacement,replacementGeneration
                    ))
                world.unregisterPlayer(
                    replacement,replacementGeneration
                );
            if(originalGeneration>=0L&&
                    world.players().owns(
                        original,originalGeneration
                    ))
                world.unregisterPlayer(
                    original,originalGeneration
                );
            world.close();
        }
    }

    /**
     * Stronger G18.4.9 separation: schedule the real G1 ticket while
     * original A is CURRENT, then replace A with B only BEFORE due-time
     * execution. This is distinct from old-NPC finalization after a
     * replacement has already joined.
     */
    private static boolean lateReplacementAfterTicketScheduled()
        throws Exception{
        final String account=USER+"-late-ticket";
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L;
        long replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,account);
            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin oldOrigin=
                spawned.runtime.recipientOrigin;
            require(oldOrigin!=null&&oldOrigin.player==original&&
                    oldOrigin.generation==originalGeneration&&
                    oldOrigin.stillCurrentIn(world),
                "original player must own pre-finalization NPC");

            require(world.npcLifecycle().applyDamage(
                    npc.id,99,54L
                ).newlyDied,
                "scheduled-before-logout lethal event");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioning.runtime().finalizeIfOwned(npc);
            require(terminal!=null&&terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.recipientOrigin==oldOrigin&&
                    oldOrigin.stillCurrentIn(world),
                "ticket must originate while old generation remains live");

            MonsterSpawnerService.SessionSnapshot pending=
                spawner.getSession(account);
            require(pending!=null&&pending.spawnedNpcIds.isEmpty()&&
                    !pending.active,
                "old generation must have a post-terminal pending session");

            require(world.unregisterPlayer(original,originalGeneration),
                "old owner disconnect after G1 ticket scheduled");
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                replacement,account
            );
            require(!oldOrigin.stillCurrentIn(world)&&
                    world.players().byName(account)==replacement,
                "replacement must join after ticket publication");

            world.events().runDue(
                54L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS
            );
            MonsterSpawnerService.SessionSnapshot fresh=
                spawner.getSession(account);
            if(fresh==null||fresh.spawnedNpcIds.size()!=1)return false;
            EntityId freshNpcId=fresh.spawnedNpcIds.get(0);
            MonsterSpawnerPvmRuntime.Snapshot newNpc=
                provisioning.runtime().get(freshNpcId);
            return !npc.id.equals(freshNpcId)&&
                newNpc!=null&&newNpc.recipientOrigin!=null&&
                newNpc.recipientOrigin.player==replacement&&
                newNpc.recipientOrigin.generation==replacementGeneration&&
                !oldOrigin.stillCurrentIn(world);
        }finally{
            if(replacement!=null&&
                    world.players().owns(replacement,replacementGeneration))
                world.unregisterPlayer(replacement,replacementGeneration);
            if(originalGeneration>=0L&&
                    world.players().owns(original,originalGeneration))
                world.unregisterPlayer(original,originalGeneration);
            world.close();
        }
    }

    /**
     * G18.5.0: terminal delivery must remain pending while A is the
     * live recipient, then only after A disconnects/B replaces A does
     * the REAL installed G1+five-progression observer execute on retry.
     */
    private static boolean pendingSettlementBeforeReconnect()
        throws Exception{
        final String account=USER+"-settlement-retry";
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long oldGeneration=-1L;
        long newGeneration=-1L;
        try{
            oldGeneration=world.registerPlayer(original,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult started=
                dmm.activateEasyIfCurrent(account,original,oldGeneration);
            require(started!=null&&started.activatedNow&&
                    started.snapshot.objective.progress==0L,
                "setup original objective before pending settlement");

            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                spawned.runtime.recipientOrigin;
            require(origin!=null&&origin.player==original&&
                    origin.generation==oldGeneration&&
                    origin.stillCurrentIn(world),
                "old generation should own NPC before settlement");

            // The exact existing settlement overflow fixture: a stacked
            // item at Integer.MAX_VALUE prevents the next same-item
            // drop from being committed until the blocker is removed.
            GroundItem blocker=world.groundItems().add(
                995,Integer.MAX_VALUE,npc.tile(),account,1L,false
            );
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,75L
                ).newlyDied,
                "pending settlement lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult pending=
                provisioning.runtime().finalizeIfOwned(npc);
            require(pending!=null&&
                    pending.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.SETTLEMENT_PENDING&&
                    pending.settlement==null&&
                    pending.recipientOrigin==origin&&
                    origin.stillCurrentIn(world)&&
                    dmm.snapshot(account).objective.progress==0L&&
                    world.pvmRecords().snapshot(original).kills==0L&&
                    world.pvmRecords().receiptCount()==0,
                "pending finalization must not publish G1 or G18 credit");

            require(world.unregisterPlayer(original,oldGeneration),
                "disconnect old generation while settlement pending");
            replacement=new WorldPlayer();
            newGeneration=world.registerPlayer(replacement,account);
            require(!origin.stillCurrentIn(world)&&
                    world.players().owns(replacement,newGeneration),
                "pending NPC origin must not retarget at replacement login");

            require(world.groundItems().remove(blocker.id),
                "clear overflow blocker before terminal retry");
            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                provisioning.runtime().retrySettlement(npc.id);
            require(finished!=null&&
                    finished.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finished.settlement!=null&&
                    finished.recipientOrigin==origin&&
                    finished.recipientOrigin.player==original&&
                    !finished.recipientOrigin.stillCurrentIn(world),
                "settlement retry must retain original A/generation witness");

            boolean credited=
                dmm.snapshot(account).objective.progress==1L&&
                world.pvmRecords().snapshot(original).kills==0L&&
                world.pvmRecords().snapshot(replacement).kills==1L&&
                world.pvmRecords().receiptCount()==1&&
                world.players().owns(replacement,newGeneration);

            // The terminalized NPC has been removed from the runtime;
            // neither a second finalized record nor another reward
            // event is allowed when retrying an already-settled ID.
            MonsterSpawnerPvmRuntime.FinalizeResult replay=
                provisioning.runtime().retrySettlement(npc.id);
            return credited&&
                (replay==null||
                 replay.status!=
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED)&&
                dmm.snapshot(account).objective.progress==1L&&
                world.pvmRecords().receiptCount()==1;
        }finally{
            if(replacement!=null&&
                    world.players().owns(replacement,newGeneration))
                world.unregisterPlayer(replacement,newGeneration);
            if(oldGeneration>=0L&&
                    world.players().owns(original,oldGeneration))
                world.unregisterPlayer(original,oldGeneration);
            world.close();
        }
    }

    /**
     * G18.5.2 / #2587: a real terminal event is delivered ONCE using the
     * unchanged installed combined observer. Afterwards the test manually
     * re-delivers only the same event to two real consumer APIs:
     * G1 PvM receipt deduplication and G4/G14/G15/G16/G18 aggregate credit.
     * It demonstrates asymmetric idempotency if a future outbox naively
     * replays consumers; the current runtime never performs this replay.
     */
    private static boolean controlledConsumerReplayIdempotencyGap()
        throws Exception{
        final String account=USER+"-consumer-replay";
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult started=
                dmm.activateEasyIfCurrent(account,player,generation);
            require(started!=null&&started.activatedNow&&
                    started.snapshot.objective.progress==0L,
                "G1852 original DMM objective setup");

            // Leave the production LocalLab terminal observer installed.
            // The real G1 and progression consumers both execute once.
            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,93L
                ).newlyDied,
                "G1852 canonical lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioning.runtime().finalizeIfOwned(npc);
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    terminal.finalization!=null&&
                    terminal.finalization.definitionId==
                        LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID&&
                    world.pvmRecords().snapshot(player).kills==1L&&
                    world.pvmRecords().receiptCount()==1&&
                    dmm.snapshot(account).objective.progress==1L&&
                    world.groundItems().size()==1,
                "real installed observer first terminal delivery");

            // Synthetic consumer-only REPLAY; this is not runtime behavior.
            MonsterSpawnerNpcDeathFinalizationService.Result death=
                terminal.finalization;
            PvmRecordService.Receipt repeatedG1=
                world.pvmRecords().recordIfCurrent(
                    player,generation,death.npcId,death.deathTick
                );
            boolean repeatedAggregate=
                LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(
                    world,
                    death.recipientRef,
                    death.definitionId,
                    death.deathTile,
                    death.deathTick
                );

            boolean asymmetry=
                repeatedG1!=null&&repeatedG1.replayed&&
                !repeatedG1.applied&&
                repeatedAggregate&&
                world.pvmRecords().snapshot(player).kills==1L&&
                world.pvmRecords().receiptCount()==1&&
                dmm.snapshot(account).objective.progress==2L&&
                world.groundItems().size()==1;

            MonsterSpawnerPvmRuntime.FinalizeResult retry=
                provisioning.runtime().retrySettlement(npc.id);
            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                provisioning.runtime().finalizeIfOwned(npc);
            return asymmetry&&
                retry.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                duplicate.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                world.pvmRecords().snapshot(player).kills==1L&&
                world.pvmRecords().receiptCount()==1&&
                dmm.snapshot(account).objective.progress==2L&&
                world.groundItems().size()==1;
        }finally{
            if(generation>=0L&&
                    world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.3 / #2587: even G1's per-(npcId,deathTick) deduplication is
     * scoped to a particular PvmRecordService object. Reconstructing
     * a fresh service over the SAME WorldPlayer leaves its receipt map
     * empty while the actual persistent player namespace still says kills=1.
     * This is an explicitly test-only service-replacement cutpoint,
     * NOT a JVM crash/restart or disk persistence experiment.
     */
    private static boolean pvmRecordInstanceReceiptReset()
        throws Exception{
        final String account=USER+"-receipt-lifetime";
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,account);
            LocalMonsterSpawnerActivationRuntime provisioned=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioned.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioned.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            final long deathTick=101L;
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1853 original lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult first=
                provisioned.runtime().finalizeIfOwned(npc);

            PvmRecordService installed=world.pvmRecords();
            require(first!=null&&first.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    first.settlement!=null&&
                    installed.snapshot(player).kills==1L&&
                    installed.receiptCount()==1&&
                    world.groundItems().size()==1,
                "G1853 installed production G1 consumer first event");

            PvmRecordService.Receipt sameServiceReplay=
                installed.recordIfCurrent(
                    player,generation,npc.id,deathTick
                );
            require(sameServiceReplay.replayed&&
                    !sameServiceReplay.applied&&
                    installed.snapshot(player).kills==1L&&
                    installed.receiptCount()==1,
                "G1853 original instance correctly deduplicates");

            // A second service object starts with NO historical receipts
            // but mutates the SAME current WorldPlayer extension namespace.
            // No second NPC terminal event or ground settlement happens.
            PvmRecordService recreated=new PvmRecordService(world);
            require(recreated.receiptCount()==0&&
                    recreated.snapshot(player).kills==1L,
                "fresh G1 service should see kills but lack receipt map");

            PvmRecordService.Receipt repeated=
                recreated.recordIfCurrent(
                    player,generation,npc.id,deathTick
                );
            PvmRecordService.Receipt secondDuplicate=
                recreated.recordIfCurrent(
                    player,generation,npc.id,deathTick
                );

            return repeated.applied&&
                !repeated.replayed&&
                secondDuplicate.replayed&&
                !secondDuplicate.applied&&
                recreated.receiptCount()==1&&
                installed.receiptCount()==1&&
                installed.snapshot(player).kills==2L&&
                recreated.snapshot(player).kills==2L&&
                world.groundItems().size()==1&&
                provisioned.runtime().get(npc.id)==null;
        }finally{
            if(generation>=0L&&
                    world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    /**
     * G18.5.4 / #2587: extend G18.5.3 beyond an in-memory service rebuild.
     * Capture and save an account using the REAL FilePlayerRepository direct
     * forensic save API. Close the original World and use an independent
     * fresh World + validated account snapshot restoration. The PvM kills
     * namespace survives the file round trip, but the service's event
     * receipt map is not part of the PlayerSnapshot or repository file.
     *
     * This is NOT an admitted World session final save / real TCP login,
     * NOT a killed JVM or a claim of historical server replay semantics.
     */
    private static boolean pvmRecordFileRoundTripReceiptLoss()
        throws Exception{
        final String account=USER+"-file-roundtrip";
        final long deathTick=113L;
        Path directory=Files.createTempDirectory(
            "g1854-pvm-file-roundtrip-"
        );
        Path accountFile=directory.resolve(account+".properties");
        FilePlayerRepository repository=
            new FilePlayerRepository(username->
                directory.resolve(username+".properties")
            );
        World sourceWorld=null;
        World restoredWorld=null;
        WorldPlayer original=new WorldPlayer();
        WorldPlayer rehydrated=new WorldPlayer();
        long sourceGeneration=-1L;
        long restoredGeneration=-1L;
        try{
            sourceWorld=World.isolatedForTest(60_000L,repository);
            sourceGeneration=sourceWorld.registerPlayer(original,account);
            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(sourceWorld);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                account,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(account,0);
            spawner.activate(account,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    account,account,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            require(sourceWorld.npcLifecycle().applyDamage(
                    npc.id,99,deathTick
                ).newlyDied,
                "G1854 original canonical death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioning.runtime().finalizeIfOwned(npc);
            require(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    sourceWorld.pvmRecords().snapshot(original).kills==1L&&
                    sourceWorld.pvmRecords().receiptCount()==1&&
                    sourceWorld.groundItems().size()==1,
                "G1854 real G1 installed observer initial record");

            // Explicit direct RAW file save - NOT guarded admitted World save.
            PlayerSnapshot captured=PlayerSnapshotCodec.capture(
                account,original
            );
            repository.save(captured);
            require(Files.isRegularFile(accountFile),
                "G1854 explicit repository save did not create account");
            PlayerSnapshot stored=repository.load(account)
                .orElseThrow(()->new AssertionError(
                    "G1854 saved file not readable"
                ));
            require("1".equals(stored.value(
                    "extension.pvm-record.kills"
                )),
                "G1854 account file did not contain G1 kill namespace");

            require(sourceWorld.unregisterPlayer(
                    original,sourceGeneration
                ),"G1854 original owner unregister");
            sourceWorld.close();
            sourceWorld=null;

            // Independent World with a fresh PvmRecordService and file read.
            // Applying validated state is an explicit test hydration seam,
            // NOT a real authenticated LocalSession admission operation.
            restoredWorld=World.isolatedForTest(
                60_000L,
                new FilePlayerRepository(username->
                    directory.resolve(username+".properties")
                )
            );
            PlayerSnapshotCodec.applyValidated(stored,rehydrated);
            restoredGeneration=
                restoredWorld.registerPlayer(rehydrated,account);
            PvmRecordService records=restoredWorld.pvmRecords();
            require(records.snapshot(rehydrated).kills==1L&&
                    records.receiptCount()==0&&
                    restoredWorld.groundItems().size()==0,
                "G1854 new World has persisted G1 counter but no event receipt");

            PvmRecordService.Receipt manualReplay=
                records.recordIfCurrent(
                    rehydrated,restoredGeneration,npc.id,deathTick
                );
            PvmRecordService.Receipt repeatedAgain=
                records.recordIfCurrent(
                    rehydrated,restoredGeneration,npc.id,deathTick
                );
            require(manualReplay.applied&&
                    !manualReplay.replayed&&
                    repeatedAgain.replayed&&
                    !repeatedAgain.applied&&
                    records.snapshot(rehydrated).kills==2L&&
                    records.receiptCount()==1&&
                    restoredWorld.groundItems().size()==0,
                "G1854 file round trip did not reproduce receipt lifetime");

            // Verify newly accepted duplicate survives another direct file
            // save. This does not authorize an admitted World persistence
            // receipt or recovery of the original event identity.
            repository.save(PlayerSnapshotCodec.capture(
                account,rehydrated
            ));
            PlayerSnapshot second=repository.load(account)
                .orElseThrow(()->new AssertionError(
                    "G1854 recaptured account file missing"
                ));
            return "2".equals(second.value(
                    "extension.pvm-record.kills"
                ))&&records.receiptCount()==1;
        }finally{
            if(restoredWorld!=null){
                if(restoredGeneration>=0L&&
                        restoredWorld.players().owns(
                            rehydrated,restoredGeneration
                        ))
                    restoredWorld.unregisterPlayer(
                        rehydrated,restoredGeneration
                    );
                restoredWorld.close();
            }
            if(sourceWorld!=null){
                if(sourceGeneration>=0L&&
                        sourceWorld.players().owns(
                            original,sourceGeneration
                        ))
                    sourceWorld.unregisterPlayer(
                        original,sourceGeneration
                    );
                sourceWorld.close();
            }
            // Only delete this test's privately created temporary tree.
            try(Stream<Path> paths=Files.walk(directory)){
                paths.sorted(Comparator.reverseOrder())
                    .forEach(item->{
                        try{
                            Files.deleteIfExists(item);
                        }catch(IOException failure){
                            throw new IllegalStateException(
                                "G1854 temporary test cleanup "+item,
                                failure
                            );
                        }
                    });
            }
        }
    }

    private static CaseResult runCase(boolean reconnect)
        throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L;
        long replacementGeneration=-1L;

        try{
            originalGeneration=world.registerPlayer(original,USER);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmm.activateEasyIfCurrent(
                    USER,original,originalGeneration
                );
            require(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "original owner DMM setup");

            // Use the REAL LocalLab provisioning composition: the returned
            // PvM runtime has the observer that invokes G1 plus five actual
            // progression runtimes after terminal settlement.
            LocalMonsterSpawnerActivationRuntime provisioning=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=provisioning.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                provisioning.runtime().spawnAndBind(
                    USER,USER,3088,3495,0
                );
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                spawned.runtime.recipientOrigin;
            require(origin!=null&&origin.player==original&&
                    origin.generation==originalGeneration&&
                    origin.stillCurrentIn(world),
                "canonical NPC spawn did not capture original owner");

            require(world.unregisterPlayer(original,originalGeneration),
                "old generation unregister");

            if(reconnect){
                replacement=new WorldPlayer();
                replacementGeneration=world.registerPlayer(
                    replacement,USER
                );
                require(world.players().byName(USER)==replacement&&
                        !origin.stillCurrentIn(world),
                    "new same-username generation must replace owner");
            }else{
                require(world.players().byName(USER)==null&&
                        !origin.stillCurrentIn(world),
                    "no-current-owner case");
            }

            // The lethal damage is synthetic, but the terminal finalizer,
            // settlement and observer are the actual installed production
            // LocalLab composition, not a recreated credit hook.
            require(world.npcLifecycle().applyDamage(
                    npc.id,99,41L
                ).newlyDied,
                "canonical NPC lethal transition");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                provisioning.runtime().finalizeIfOwned(npc);

            CaseResult result=new CaseResult();
            result.finalized=terminal!=null&&
                terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&
                terminal.finalization!=null;
            result.originalOriginPreserved=
                terminal!=null&&terminal.recipientOrigin==origin&&
                terminal.recipientOrigin.player==original&&
                terminal.recipientOrigin.generation==originalGeneration&&
                !origin.stillCurrentIn(world);

            // G18.4.9: the real installed G1 terminal loop schedules an
            // automatic respawn if ANY current owner of the stored username
            // exists. Its delayed ticket carries only strings and expected
            // MonsterSpawner session; neither is a player-generation fence.
            // Resolve the real due tick, without mutating production policy.
            final long dueTick=41L+
                G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS;
            int dueCallbacks=world.events().runDue(dueTick);
            MonsterSpawnerService.SessionSnapshot afterRespawn=
                spawner.getSession(USER);

            if(reconnect){
                boolean registeredReplacement=
                    world.players().owns(
                        replacement,replacementGeneration
                    );
                if(afterRespawn!=null&&
                        afterRespawn.spawnedNpcIds.size()==1){
                    EntityId newNpcId=
                        afterRespawn.spawnedNpcIds.get(0);
                    MonsterSpawnerPvmRuntime.Snapshot newborn=
                        provisioning.runtime().get(newNpcId);
                    result.oldGenerationRespawnedForReplacement=
                        dueCallbacks>=1&&registeredReplacement&&
                        !npc.id.equals(newNpcId)&&
                        newborn!=null&&
                        newborn.recipientOrigin!=null&&
                        newborn.recipientOrigin.player==replacement&&
                        newborn.recipientOrigin.generation==
                            replacementGeneration&&
                        !origin.stillCurrentIn(world);
                }
            }else{
                // WorldEventQueue.runDue returns ALL due callbacks,
                // not just this G1 loop's ticket. The correct negative
                // evidence is the absence of a newly tracked/spawned NPC.
                // The terminal teardown may retire the now-empty
                // Monster Spawner session altogether. Null session and
                // a retained zero-NPC session both mean NO respawn.
                result.noOwnerNoRespawn=
                    afterRespawn==null||
                    afterRespawn.spawnedNpcIds.isEmpty();
            }

            // snapshot(USER) deliberately requires a current player.
            // In the no-replacement control, reattach ONLY AFTER the
            // terminal observer has already run; doing so cannot change
            // whether the earlier terminal event found a recipient.
            if(!reconnect){
                replacement=new WorldPlayer();
                replacementGeneration=world.registerPlayer(
                    replacement,USER
                );
            }

            LocalLabDailyMoneyMakingRuntime.Snapshot after=
                dmm.snapshot(USER);
            require(after!=null,"World-owned DMM state vanished");

            result.progressWasExactlyOne=
                after.objective.progress==1L;
            result.progressWasExactlyZero=
                after.objective.progress==0L;

            // G18.4.8: the *actual G1 terminal observer* also resolves
            // finalization.recipientRef by CURRENT username and records on
            // that generation. The original spawn WorldPlayer remains an
            // independent object with no accepted PvM record.
            PvmRecordService records=world.pvmRecords();
            long originalKills=records.snapshot(original).kills;
            long currentKills=records.snapshot(replacement).kills;
            int receiptCount=records.receiptCount();
            result.g1RecordCreditedReplacement=
                reconnect&&originalKills==0L&&
                currentKills==1L&&receiptCount==1&&
                world.players().owns(
                    replacement,replacementGeneration
                );
            result.g1RecordNotCreditedWhenAbsent=
                !reconnect&&originalKills==0L&&
                currentKills==0L&&receiptCount==0;

            result.replacementGotLegacyCredit=
                reconnect&&replacement!=null&&
                world.players().owns(
                    replacement,replacementGeneration
                )&&result.progressWasExactlyOne;

            if(reconnect){
                LocalLabDailyMoneyMakingRuntime.ProgressResult
                    exactOld=dmm.recordMonsterSpawnerFinalizationIfCurrent(
                        USER,
                        original,
                        originalGeneration,
                        LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                        42L
                    );
                result.oldGenerationOptInRefused=
                    exactOld!=null&&!exactOld.activated&&
                    !exactOld.progressed&&exactOld.snapshot==null&&
                    dmm.snapshot(USER).objective.progress==1L;
            }

            return result;
        }finally{
            if(replacement!=null&&
                    world.players().owns(
                        replacement,replacementGeneration
                    ))
                world.unregisterPlayer(
                    replacement,replacementGeneration
                );
            if(originalGeneration>=0L&&
                    world.players().owns(
                        original,originalGeneration
                    ))
                world.unregisterPlayer(
                    original,originalGeneration
                );
            world.close();
        }
    }

    private static final class CaseResult {
        boolean finalized;
        boolean originalOriginPreserved;
        boolean replacementGotLegacyCredit;
        boolean oldGenerationOptInRefused;
        boolean progressWasExactlyOne;
        boolean progressWasExactlyZero;
        boolean g1RecordCreditedReplacement;
        boolean g1RecordNotCreditedWhenAbsent;
        boolean oldGenerationRespawnedForReplacement;
        boolean noOwnerNoRespawn;
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private G1847MonsterSpawnerNameReuseTerminalAttributionTest(){}
}
