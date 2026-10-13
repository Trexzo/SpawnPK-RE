package spk.local;

import java.io.ByteArrayOutputStream;

/** G18.4.2: late UI difficulty selections never modify or publish from old generations. */
public final class G1842DailyMoneyMakingDifficultyGenerationFenceTest {
    private static final String PLAYER="g1842-account";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer first=new WorldPlayer();
        long firstGeneration=world.registerPlayer(first,PLAYER);
        WorldPlayer second=null;
        long secondGeneration=-1L;
        boolean closed=false;

        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                bytes,new IsaacCipher(new int[]{1842,1843,1844,1845})
            );

            LocalDailyMoneyMakingUiHandler original=
                new LocalDailyMoneyMakingUiHandler(
                    world,first,firstGeneration,PLAYER
                );
            LocalDailyMoneyMakingUiHandler.Result hard=select(
                original,DailyMoneyMakingPresentation.HARD_WIDGET,writer
            );
            boolean currentSelectPersists=hard.succeeded()&&
                "DIFFICULTY_SELECTED".equals(hard.status)&&
                original.snapshot().selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.HARD&&
                difficulty(first)==DailyMoneyMakingStateService.Difficulty.HARD;

            int beforeStale=bytes.size();
            world.unregisterPlayer(first,firstGeneration);
            second=new WorldPlayer();
            secondGeneration=world.registerPlayer(second,PLAYER);

            LocalDailyMoneyMakingUiHandler.Result stale=select(
                original,DailyMoneyMakingPresentation.MEDIUM_WIDGET,writer
            );
            boolean staleSelectNoMutation=
                "NO_CURRENT_PLAYER".equals(stale.status)&&
                !stale.stateChanged&&bytes.size()==beforeStale&&
                original.snapshot().selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.HARD&&
                difficulty(first)==DailyMoneyMakingStateService.Difficulty.HARD&&
                difficulty(second)==null;

            LocalDailyMoneyMakingUiHandler wrongName=
                new LocalDailyMoneyMakingUiHandler(
                    world,second,secondGeneration,"g1842-other-name"
                );
            int beforeWrongName=bytes.size();
            LocalDailyMoneyMakingUiHandler.Result incorrectOwnerName=select(
                wrongName,DailyMoneyMakingPresentation.EASY_WIDGET,writer
            );
            boolean wrongNameNoMutation=
                "NO_CURRENT_PLAYER".equals(incorrectOwnerName.status)&&
                bytes.size()==beforeWrongName&&difficulty(second)==null;

            LocalDailyMoneyMakingUiHandler correct=
                new LocalDailyMoneyMakingUiHandler(
                    world,second,secondGeneration,PLAYER
                );
            LocalDailyMoneyMakingUiHandler.Result easy=select(
                correct,DailyMoneyMakingPresentation.EASY_WIDGET,writer
            );
            boolean newSessionSelectWorks=easy.succeeded()&&
                difficulty(second)==DailyMoneyMakingStateService.Difficulty.EASY&&
                difficulty(first)==DailyMoneyMakingStateService.Difficulty.HARD;

            int beforeClose=bytes.size();
            world.close();
            closed=true;
            LocalDailyMoneyMakingUiHandler.Result afterClose=select(
                correct,DailyMoneyMakingPresentation.HARD_WIDGET,writer
            );
            boolean closedWorldNoMutation=
                "NO_CURRENT_PLAYER".equals(afterClose.status)&&
                bytes.size()==beforeClose&&
                difficulty(second)==DailyMoneyMakingStateService.Difficulty.EASY;

            LocalDailyMoneyMakingUiHandler unbound=
                new LocalDailyMoneyMakingUiHandler();
            ByteArrayOutputStream fixtureBytes=new ByteArrayOutputStream();
            ServerPacketWriter fixtureWriter=new ServerPacketWriter(
                fixtureBytes,new IsaacCipher(new int[]{1842,1843,1844,1845})
            );
            boolean unboundFixturePreserved=select(
                unbound,DailyMoneyMakingPresentation.EASY_WIDGET,fixtureWriter
            ).succeeded()&&fixtureBytes.size()>0;

            require(currentSelectPersists&&staleSelectNoMutation&&
                wrongNameNoMutation&&newSessionSelectWorks&&
                closedWorldNoMutation&&unboundFixturePreserved,
                "G18.4.2 difficulty-generation ownership");

            System.out.println(
                "G1842_DAILY_MONEY_MAKING_DIFFICULTY_GENERATION_FENCE_PASS"+
                " currentSelectPersists="+currentSelectPersists+
                " staleSelectNoMutation="+staleSelectNoMutation+
                " wrongNameNoMutation="+wrongNameNoMutation+
                " newSessionSelectWorks="+newSessionSelectWorks+
                " closedWorldNoMutation="+closedWorldNoMutation+
                " unboundFixturePreserved="+unboundFixturePreserved+
                " originalServerSelectionPolicyClaim=false"
            );
        }finally{
            if(!closed){
                if(world.players().owns(first,firstGeneration))
                    world.unregisterPlayer(first,firstGeneration);
                if(second!=null&&
                   world.players().owns(second,secondGeneration))
                    world.unregisterPlayer(second,secondGeneration);
                world.close();
            }
        }
    }

    private static DailyMoneyMakingStateService.Difficulty difficulty(
        WorldPlayer owner
    ){
        return LocalDailyMoneyMakingPersistence.decode(
            owner.snapshotExtensions().namespace(
                LocalDailyMoneyMakingPersistence.NAMESPACE
            )
        );
    }

    private static LocalDailyMoneyMakingUiHandler.Result select(
        LocalDailyMoneyMakingUiHandler handler,
        int widget,
        ServerPacketWriter writer
    )throws Exception{
        return LocalSessionUiActionHandler.dispatchDailyMoneyMakingWidget(
            handler,widget,writer
        );
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private G1842DailyMoneyMakingDifficultyGenerationFenceTest(){}
}
