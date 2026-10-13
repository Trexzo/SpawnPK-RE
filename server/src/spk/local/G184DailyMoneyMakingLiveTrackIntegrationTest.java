package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** G18.4: exact 55002/55012 input -> World-owned CUSTOM_LOCALLAB PvM only. */
public final class G184DailyMoneyMakingLiveTrackIntegrationTest {
    private static final int[] SEED={171,172,173,174};
    private static final String PLAYER="g184-easy-player";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,PLAYER);
        WorldPlayer replacement=null;
        long replacementGeneration=-1L;
        try{
            LocalLabDailyMoneyMakingRuntime runtime=world.localLabDailyMoneyMaking();
            LocalDailyMoneyMakingUiHandler handler=
                new LocalDailyMoneyMakingUiHandler(world,player,generation,PLAYER);

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter writer=new ServerPacketWriter(
                wire,new IsaacCipher(SEED.clone())
            );
            LocalDailyMoneyMakingUiHandler.Result noSelection=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean noSelectionFailClosed=noSelection!=null&&
                "NO_SELECTED_DIFFICULTY".equals(noSelection.status)&&wire.size()==0&&
                runtime.activePlayerCount()==0;

            LocalDailyMoneyMakingUiHandler.Result easy=
                dispatch(handler,DailyMoneyMakingPresentation.EASY_WIDGET,writer);
            boolean runtimeBound=easy!=null&&easy.succeeded()&&
                runtime.activePlayerCount()==0;

            LocalDailyMoneyMakingUiHandler.Result first=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean easyTrackActivates=first!=null&&first.succeeded()&&
                first.stateChanged&&runtime.activePlayerCount()==1&&
                LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                    first.snapshot.trackedObjectiveKey)&&
                runtime.snapshot(PLAYER).objective.progress==0;

            LocalDailyMoneyMakingUiHandler.Result repeat=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean repeatedTrackStable=repeat!=null&&repeat.succeeded()&&
                !repeat.stateChanged&&runtime.activePlayerCount()==1;

            LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(
                world,PLAYER,LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                new Tile(3087,3495,0),12L
            );
            LocalLabMonsterSpawnerProvisioning.creditTerminalProgression(
                world,PLAYER,LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                new Tile(3087,3495,0),13L
            );
            LocalDailyMoneyMakingUiHandler.Result refreshed=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean progress2Refresh=refreshed!=null&&refreshed.succeeded()&&
                !refreshed.stateChanged&&
                runtime.snapshot(PLAYER).objective.progress==2;

            dispatch(handler,DailyMoneyMakingPresentation.MEDIUM_WIDGET,writer);
            int beforeMedium=wire.size();
            LocalDailyMoneyMakingUiHandler.Result medium=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean mediumTrackFailClosed=medium!=null&&
                "NO_CONFIGURED_ACTIVITY".equals(medium.status)&&
                !medium.stateChanged&&wire.size()==beforeMedium;

            dispatch(handler,DailyMoneyMakingPresentation.HARD_WIDGET,writer);
            int beforeHard=wire.size();
            LocalDailyMoneyMakingUiHandler.Result hard=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean hardTrackFailClosed=hard!=null&&
                "NO_CONFIGURED_ACTIVITY".equals(hard.status)&&
                !hard.stateChanged&&wire.size()==beforeHard;

            int beforeTeleport=wire.size();
            LocalDailyMoneyMakingUiHandler.Result teleport=
                dispatch(handler,DailyMoneyMakingPresentation.TELEPORT_WIDGET,writer);
            boolean teleportFailClosed=teleport!=null&&
                "NO_TELEPORT_AUTHORITY".equals(teleport.status)&&
                !teleport.stateChanged&&wire.size()==beforeTeleport;

            byte[] bytes=wire.toByteArray();
            IsaacCipher read=new IsaacCipher(SEED.clone());
            int off=0;
            off=assert126(bytes,off,read,"1",36);
            off=assert126(bytes,off,read,"LocalLab Easy PvM: 0/3",26);
            off=assert126(bytes,off,read,"LocalLab Easy PvM: 0/3",26);
            off=assert126(bytes,off,read,"LocalLab Easy PvM: 2/3",26);
            off=assert126(bytes,off,read,"2",36);
            off=assert126(bytes,off,read,"3",36);
            boolean target26Progress0=off==bytes.length;

            // Session generation matters even when a replacement reuses its name.
            world.unregisterPlayer(player,generation);
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(replacement,PLAYER);
            int beforeStale=wire.size();
            // Both difficulty selection and Track must reject a stale UI.
            // Older G18.4 intentionally allowed the stale SELECT wire here;
            // G18.4.2 removes that stale publication and persistence write.
            LocalDailyMoneyMakingUiHandler.Result staleSelection=
                dispatch(handler,DailyMoneyMakingPresentation.EASY_WIDGET,writer);
            int afterStaleSelection=wire.size();
            LocalDailyMoneyMakingUiHandler.Result stale=
                dispatch(handler,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean staleSessionFailClosed=stale!=null&&
                "NO_CURRENT_PLAYER".equals(stale.status)&&
                staleSelection!=null&&
                "NO_CURRENT_PLAYER".equals(staleSelection.status)&&
                !staleSelection.stateChanged&&
                afterStaleSelection==beforeStale&&
                wire.size()==beforeStale;

            // G18.3 persistence integration: a post-login handler must see
            // canonical stored difficulty, not the pre-login shell state.
            replacement.snapshotExtensions().replaceNamespace(
                LocalDailyMoneyMakingPersistence.NAMESPACE,
                LocalDailyMoneyMakingPersistence.encode(
                    DailyMoneyMakingStateService.Difficulty.MEDIUM
                )
            );
            LocalDailyMoneyMakingUiHandler restored=
                new LocalDailyMoneyMakingUiHandler(
                    world,replacement,replacementGeneration,PLAYER
                );
            int beforeRestoredTrack=wire.size();
            LocalDailyMoneyMakingUiHandler.Result restoredTrack=
                dispatch(restored,DailyMoneyMakingPresentation.TRACK_WIDGET,writer);
            boolean persistedDifficultyRestored=
                restored.snapshot().selectedDifficulty==
                    DailyMoneyMakingStateService.Difficulty.MEDIUM&&
                "NO_CONFIGURED_ACTIVITY".equals(restoredTrack.status)&&
                wire.size()==beforeRestoredTrack;

            require(runtimeBound&&easyTrackActivates&&target26Progress0&&
                repeatedTrackStable&&progress2Refresh&&
                mediumTrackFailClosed&&hardTrackFailClosed&&
                noSelectionFailClosed&&teleportFailClosed&&staleSessionFailClosed&&
                persistedDifficultyRestored,
                "G18.4 daily tracking acceptance");

            System.out.println(
                "G184_DAILY_MONEY_MAKING_LIVE_TRACK_PASS"+
                " runtimeBound="+runtimeBound+
                " easyTrackActivates="+easyTrackActivates+
                " target26Progress0="+target26Progress0+
                " repeatedTrackStable="+repeatedTrackStable+
                " progress2Refresh="+progress2Refresh+
                " mediumTrackFailClosed="+mediumTrackFailClosed+
                " hardTrackFailClosed="+hardTrackFailClosed+
                " noSelectionFailClosed="+noSelectionFailClosed+
                " teleportFailClosed="+teleportFailClosed+
                " staleSessionFailClosed="+staleSessionFailClosed+
                " persistedDifficultyRestored="+persistedDifficultyRestored+
                " rewardPolicyClaim=false resetPolicyClaim=false"+
                " persistenceClaim=false originalServerPolicyClaim=false"
            );
        }finally{
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(replacement!=null&&world.players().owns(
                    replacement,replacementGeneration))
                world.unregisterPlayer(replacement,replacementGeneration);
            world.close();
        }
    }

    private static LocalDailyMoneyMakingUiHandler.Result dispatch(
        LocalDailyMoneyMakingUiHandler handler,int widget,ServerPacketWriter writer
    )throws Exception{
        return LocalSessionUiActionHandler.dispatchDailyMoneyMakingWidget(
            handler,widget,writer
        );
    }

    private static int assert126(
        byte[] wire,int offset,IsaacCipher cipher,String value,int target
    ){
        int opcode=((wire[offset++]&255)-cipher.nextInt())&255;
        require(opcode==126,"unexpected opcode "+opcode);
        int len=((wire[offset++]&255)<<8)|(wire[offset++]&255);
        byte[] text=value.getBytes(StandardCharsets.ISO_8859_1);
        byte[] expected=Arrays.copyOf(text,text.length+3);
        expected[text.length]=10;
        expected[text.length+1]=(byte)(target>>>8);
        expected[text.length+2]=(byte)((target+128)&255);
        require(len==expected.length,"wrong S2C126 length");
        require(Arrays.equals(expected,Arrays.copyOfRange(
            wire,offset,offset+len)),"wrong S2C126 payload "+value);
        return offset+len;
    }

    private static void require(boolean valid,String reason){
        if(!valid)throw new AssertionError(reason);
    }

    private G184DailyMoneyMakingLiveTrackIntegrationTest(){}
}
