package spk.local;

/**
 * G18.4.4 / #2547: opt-in exact-origin Daily Money Making credit.
 *
 * The live Monster Spawner callback is still name-only and is NOT routed
 * through this API: this test proves refusal only when the true event
 * origin WorldPlayer+generation has been supplied by a trusted caller.
 */
public final class G1844DailyMoneyMakingExactOriginCreditTest {
    private static final String PLAYER="g1844-same-name";
    private static final String OTHER="g1844-other";
    private static final int ELIGIBLE=
        LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer foreign=new WorldPlayer();
        long originalGeneration=world.registerPlayer(original,PLAYER);
        long foreignGeneration=world.registerPlayer(foreign,OTHER);
        WorldPlayer replacement=null;
        long replacementGeneration=-1L;

        try{
            LocalLabDailyMoneyMakingRuntime runtime=
                world.localLabDailyMoneyMaking();

            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                runtime.activateEasyIfCurrent(
                    PLAYER,original,originalGeneration
                );
            require(activated!=null&&activated.activatedNow,
                "original player activation");

            LocalLabDailyMoneyMakingRuntime.ProgressResult firstCredit=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,original,originalGeneration,ELIGIBLE,1L
                );
            boolean originalCreditAccepted=
                firstCredit.activated&&firstCredit.progressed&&
                firstCredit.snapshot.objective.progress==1L;

            world.unregisterPlayer(original,originalGeneration);
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(replacement,PLAYER);

            LocalLabDailyMoneyMakingRuntime.Snapshot existing=
                runtime.snapshot(PLAYER);
            boolean sameWorldProgressIntact=
                existing.objective.progress==1L&&
                runtime.activePlayerCount()==1;

            LocalLabDailyMoneyMakingRuntime.ProgressResult stale=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,original,originalGeneration,ELIGIBLE,2L
                );
            LocalLabDailyMoneyMakingRuntime.ProgressResult staleGeneration=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,replacement,replacementGeneration+1L,ELIGIBLE,3L
                );
            LocalLabDailyMoneyMakingRuntime.ProgressResult wrongOwner=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,foreign,foreignGeneration,ELIGIBLE,4L
                );
            LocalLabDailyMoneyMakingRuntime.ProgressResult wrongName=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    OTHER,replacement,replacementGeneration,ELIGIBLE,5L
                );

            boolean staleAndCrossOwnerRefused=
                refused(stale)&&refused(staleGeneration)&&
                refused(wrongOwner)&&refused(wrongName)&&
                runtime.snapshot(PLAYER).objective.progress==1L&&
                runtime.activePlayerCount()==1;

            LocalLabDailyMoneyMakingRuntime.ProgressResult ineligible=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,replacement,replacementGeneration,ELIGIBLE+1,6L
                );
            boolean ineligibleNoCredit=
                ineligible.activated&&!ineligible.eligibleDefinition&&
                !ineligible.progressed&&
                ineligible.snapshot.objective.progress==1L;

            LocalLabDailyMoneyMakingRuntime.ProgressResult valid=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,replacement,replacementGeneration,ELIGIBLE,7L
                );
            boolean replacementExactCreditAccepted=
                valid.activated&&valid.progressed&&
                valid.snapshot.objective.progress==2L&&
                !valid.snapshot.objective.complete;

            world.close();
            LocalLabDailyMoneyMakingRuntime.ProgressResult closed=
                runtime.recordMonsterSpawnerFinalizationIfCurrent(
                    PLAYER,replacement,replacementGeneration,ELIGIBLE,8L
                );
            boolean closedWorldRefused=refused(closed);

            require(originalCreditAccepted&&sameWorldProgressIntact&&
                staleAndCrossOwnerRefused&&ineligibleNoCredit&&
                replacementExactCreditAccepted&&closedWorldRefused,
                "G18.4.4 exact-origin credit seam");

            System.out.println(
                "G1844_DMM_EXACT_ORIGIN_CREDIT_PASS"+
                " originalCreditAccepted="+originalCreditAccepted+
                " sameWorldProgressIntact="+sameWorldProgressIntact+
                " staleAndCrossOwnerRefused="+staleAndCrossOwnerRefused+
                " ineligibleNoCredit="+ineligibleNoCredit+
                " replacementExactCreditAccepted="+replacementExactCreditAccepted+
                " closedWorldRefused="+closedWorldRefused+
                " liveTerminalObserverWired=false"+
                " originalSpawnPKCreditPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean refused(
        LocalLabDailyMoneyMakingRuntime.ProgressResult value
    ){
        return value!=null&&!value.activated&&!value.progressed&&
            !value.completedNow&&value.snapshot==null;
    }

    private static void require(boolean yes,String reason){
        if(!yes)
            throw new AssertionError(reason);
    }

    private G1844DailyMoneyMakingExactOriginCreditTest(){}
}
