package spk.local;

/**
 * G18.4.3 / #2547: characterize the CURRENT CUSTOM_LOCALLAB activity lifetime.
 *
 * This is deliberately not an original SpawnPK policy, a reset-policy
 * recommendation, or a durable-account-persistence acceptance test.
 * The existing runtime is keyed by normalized username within one World.
 */
public final class G1843DailyMoneyMakingWorldLifetimeCharacterizationTest {
    private static final String PLAYER="g1843-returning-player";

    public static void main(String[] args)throws Exception{
        World firstWorld=World.isolatedForTest(60_000L);
        World secondWorld=null;

        try{
            WorldPlayer firstOwner=new WorldPlayer();
            long firstGeneration=firstWorld.registerPlayer(
                firstOwner,PLAYER
            );
            LocalLabDailyMoneyMakingRuntime firstRuntime=
                firstWorld.localLabDailyMoneyMaking();

            boolean startsUntracked=
                firstRuntime.snapshot(PLAYER)==null&&
                firstRuntime.activePlayerCount()==0;

            LocalLabDailyMoneyMakingRuntime.ActivationResult initial=
                firstRuntime.activateEasyIfCurrent(
                    PLAYER,firstOwner,firstGeneration
                );
            require(initial!=null&&initial.activatedNow,
                "first World activation");

            LocalLabDailyMoneyMakingRuntime.ProgressResult creditOne=
                firstRuntime.recordMonsterSpawnerFinalization(
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    1L
                );
            LocalLabDailyMoneyMakingRuntime.ProgressResult creditTwo=
                firstRuntime.recordMonsterSpawnerFinalization(
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    2L
                );

            boolean creditedTwo=
                creditOne.progressed&&creditTwo.progressed&&
                creditTwo.snapshot.objective.progress==2L&&
                !creditTwo.snapshot.objective.complete;

            firstWorld.unregisterPlayer(firstOwner,firstGeneration);
            boolean detachedStillWorldScoped=
                firstRuntime.activePlayerCount()==1;
            boolean oldGenerationRefused=
                firstRuntime.activateEasyIfCurrent(
                    PLAYER,firstOwner,firstGeneration
                )==null;

            WorldPlayer replacement=new WorldPlayer();
            long replacementGeneration=firstWorld.registerPlayer(
                replacement,PLAYER
            );

            LocalLabDailyMoneyMakingRuntime.Snapshot resumed=
                firstRuntime.snapshot(PLAYER);
            LocalLabDailyMoneyMakingRuntime.ActivationResult next=
                firstRuntime.activateEasyIfCurrent(
                    PLAYER,replacement,replacementGeneration
                );

            boolean sameWorldReplacementResumes=
                resumed!=null&&resumed.objective.progress==2L&&
                next!=null&&!next.activatedNow&&
                next.snapshot.objective.progress==2L&&
                firstRuntime.activePlayerCount()==1;

            LocalLabDailyMoneyMakingRuntime.ProgressResult finalCredit=
                firstRuntime.recordMonsterSpawnerFinalization(
                    PLAYER,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    3L
                );
            boolean replacementCanCompleteExisting=
                finalCredit.progressed&&finalCredit.completedNow&&
                finalCredit.snapshot.objective.progress==3L&&
                finalCredit.snapshot.objective.complete;

            // The same exact old owner/generation stays invalid even
            // after the replacement's activity reaches completion.
            boolean oldOwnerNeverReadmitted=
                firstRuntime.activateEasyIfCurrent(
                    PLAYER,firstOwner,firstGeneration
                )==null;

            firstWorld.close();

            // An entirely new isolated World constructs a different
            // in-memory runtime. No account persistence is modeled here.
            secondWorld=World.isolatedForTest(60_000L);
            WorldPlayer newWorldOwner=new WorldPlayer();
            long newWorldGeneration=secondWorld.registerPlayer(
                newWorldOwner,PLAYER
            );
            LocalLabDailyMoneyMakingRuntime newRuntime=
                secondWorld.localLabDailyMoneyMaking();

            boolean freshWorldHasNoActivity=
                newRuntime!=firstRuntime&&
                newRuntime.snapshot(PLAYER)==null&&
                newRuntime.activePlayerCount()==0;

            LocalLabDailyMoneyMakingRuntime.ActivationResult newWorldActivation=
                newRuntime.activateEasyIfCurrent(
                    PLAYER,newWorldOwner,newWorldGeneration
                );

            boolean freshWorldStartsZero=
                newWorldActivation!=null&&
                newWorldActivation.activatedNow&&
                newWorldActivation.snapshot.objective.progress==0L&&
                !newWorldActivation.snapshot.objective.complete&&
                newRuntime.activePlayerCount()==1;

            require(
                startsUntracked&&creditedTwo&&
                detachedStillWorldScoped&&oldGenerationRefused&&
                sameWorldReplacementResumes&&
                replacementCanCompleteExisting&&oldOwnerNeverReadmitted&&
                freshWorldHasNoActivity&&freshWorldStartsZero,
                "G18.4.3 observed activity-lifetime characterization"
            );

            System.out.println(
                "G1843_DMM_WORLD_LIFETIME_CHARACTERIZATION_PASS"+
                " startsUntracked="+startsUntracked+
                " creditedTwo="+creditedTwo+
                " detachedStillWorldScoped="+detachedStillWorldScoped+
                " oldGenerationRefused="+oldGenerationRefused+
                " sameWorldReplacementResumes="+sameWorldReplacementResumes+
                " replacementCanCompleteExisting="+replacementCanCompleteExisting+
                " oldOwnerNeverReadmitted="+oldOwnerNeverReadmitted+
                " freshWorldHasNoActivity="+freshWorldHasNoActivity+
                " freshWorldStartsZero="+freshWorldStartsZero+
                " originalServerPolicyClaim=false"+
                " durableActivityPersistenceClaim=false"+
                " concurrentOldEventAttributionClaim=false"
            );
        }finally{
            if(secondWorld!=null)
                secondWorld.close();
            firstWorld.close();
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)
            throw new AssertionError(message);
    }

    private G1843DailyMoneyMakingWorldLifetimeCharacterizationTest(){}
}
