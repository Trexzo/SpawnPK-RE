package spk.local;

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
        System.out.println(
            "G1849_MONSTER_SPAWNER_RESPAWN_NAME_REUSE_PASS"+
            " oldOwnerTicketTransferredToNewGeneration="+
                replacement.oldGenerationRespawnedForReplacement+
            " missingAtTerminalNoRespawn="+
                absent.noOwnerNoRespawn+
            " originalServerRespawnPolicyClaim=false"+
            " productionRespawnPolicyChanged=false"
        );
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
                result.noOwnerNoRespawn=
                    dueCallbacks==0&&
                    afterRespawn!=null&&
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
