package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G18.4.1: a UI's checked username must not become a replacement session's
 * activity authority between the preliminary UI test and runtime mutation.
 */
public final class G1841DailyMoneyMakingExactSessionFenceTest {
    private static final String USER="g1841-reused";
    private static final String OTHER="g1841-foreign";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer foreign=new WorldPlayer();
        long oldGeneration=world.registerPlayer(original,USER);
        long foreignGeneration=world.registerPlayer(foreign,OTHER);
        WorldPlayer replacement=null;
        long replacementGeneration=-1L;
        boolean closed=false;
        try{
            LocalLabDailyMoneyMakingRuntime runtime=
                world.localLabDailyMoneyMaking();

            boolean foreignOwnerRefused=
                runtime.activateEasyIfCurrent(
                    USER,foreign,foreignGeneration
                )==null&&runtime.activePlayerCount()==0;

            boolean mismatchedGenerationRefused=
                runtime.activateEasyIfCurrent(
                    USER,original,oldGeneration+1L
                )==null&&runtime.activePlayerCount()==0;

            ByteArrayOutputStream output=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                output,new IsaacCipher(new int[]{1220,1221,1222,1223})
            );
            LocalDailyMoneyMakingUiHandler oldAdapter=
                new LocalDailyMoneyMakingUiHandler(
                    world,original,oldGeneration,USER
                );

            LocalDailyMoneyMakingUiHandler.Result select=
                LocalSessionUiActionHandler.dispatchDailyMoneyMakingWidget(
                    oldAdapter,DailyMoneyMakingPresentation.EASY_WIDGET,writer
                );
            require(select.succeeded(),"pre-replacement EASY selection");
            int beforeStale=output.size();

            world.unregisterPlayer(original,oldGeneration);
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(replacement,USER);

            boolean reusedNameOldSessionRefused=
                runtime.activateEasyIfCurrent(
                    USER,original,oldGeneration
                )==null&&runtime.activePlayerCount()==0;

            LocalDailyMoneyMakingUiHandler.Result staleTrack=
                LocalSessionUiActionHandler.dispatchDailyMoneyMakingWidget(
                    oldAdapter,DailyMoneyMakingPresentation.TRACK_WIDGET,writer
                );
            boolean staleWidgetSilent=
                "NO_CURRENT_PLAYER".equals(staleTrack.status)&&
                output.size()==beforeStale&&
                runtime.activePlayerCount()==0;

            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                runtime.activateEasyIfCurrent(
                    USER,replacement,replacementGeneration
                );
            LocalLabDailyMoneyMakingRuntime.ActivationResult again=
                runtime.activateEasyIfCurrent(
                    USER,replacement,replacementGeneration
                );
            LocalLabDailyMoneyMakingRuntime.ActivationResult oldApi=
                runtime.activateEasy(USER);
            boolean currentGenerationActivatesOnce=
                activated!=null&&activated.activatedNow&&
                again!=null&&!again.activatedNow&&
                oldApi!=null&&!oldApi.activatedNow&&
                runtime.activePlayerCount()==1&&
                LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                    activated.snapshot.state.trackedObjectiveKey
                );

            world.close();
            closed=true;
            boolean closedWorldRefused=
                runtime.activateEasyIfCurrent(
                    USER,replacement,replacementGeneration
                )==null&&runtime.activePlayerCount()==1;

            require(foreignOwnerRefused&&mismatchedGenerationRefused&&
                reusedNameOldSessionRefused&&staleWidgetSilent&&
                currentGenerationActivatesOnce&&closedWorldRefused,
                "exact-session activation invariants");

            System.out.println(
                "G1841_DAILY_MONEY_MAKING_EXACT_SESSION_FENCE_PASS"+
                " foreignOwnerRefused="+foreignOwnerRefused+
                " mismatchedGenerationRefused="+mismatchedGenerationRefused+
                " reusedNameOldSessionRefused="+reusedNameOldSessionRefused+
                " staleWidgetSilent="+staleWidgetSilent+
                " currentGenerationActivatesOnce="+currentGenerationActivatesOnce+
                " closedWorldRefused="+closedWorldRefused+
                " originalServerSessionPolicyClaim=false"
            );
        }finally{
            if(!closed){
                if(world.players().owns(original,oldGeneration))
                    world.unregisterPlayer(original,oldGeneration);
                if(world.players().owns(foreign,foreignGeneration))
                    world.unregisterPlayer(foreign,foreignGeneration);
                if(replacement!=null&&
                   world.players().owns(replacement,replacementGeneration))
                    world.unregisterPlayer(replacement,replacementGeneration);
                world.close();
            }
        }
    }

    private static void require(boolean yes,String reason){
        if(!yes)throw new AssertionError(reason);
    }

    private G1841DailyMoneyMakingExactSessionFenceTest(){}
}
