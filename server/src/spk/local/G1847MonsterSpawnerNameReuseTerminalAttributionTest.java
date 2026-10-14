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
            replacement.progressWasExactlyOne;
        boolean noCurrentRecipientNoCredit=
            absent.finalized&&
            absent.originalOriginPreserved&&
            absent.progressWasExactlyZero;

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

            LocalLabDailyMoneyMakingRuntime.Snapshot after=
                dmm.snapshot(USER);
            require(after!=null,"World-owned DMM state vanished");

            result.progressWasExactlyOne=
                after.objective.progress==1L;
            result.progressWasExactlyZero=
                after.objective.progress==0L;
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
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private G1847MonsterSpawnerNameReuseTerminalAttributionTest(){}
}
