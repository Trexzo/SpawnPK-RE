package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class RaidRunServiceTest {
    public static void main(String[] args){
        PartyService parties=
            new PartyService();
        WorldInstanceService instances=
            new WorldInstanceService();

        PartyId partyId=
            PartyId.of(
                "party:raid-a"
            );

        parties.create(
            partyId,
            "player:leader",
            PartyService.Visibility.PRIVATE,
            "LOCAL_LAB_POLICY"
        );
        parties.invite(
            partyId,
            "player:member"
        );
        parties.acceptInvite(
            partyId,
            "player:member"
        );

        RaidRunService raids=
            new RaidRunService(
                parties,
                instances
            );

        RaidRunService.RaidRunId runId=
            RaidRunService.RaidRunId.of(
                "raid-run:1"
            );

        RaidRunService.Snapshot lobby=
            raids.createLobby(
                runId,
                partyId,
                "raid:local_test",
                "adept",
                "EXACT_CURRENT_CLIENT",
                "LOCAL_LAB_POLICY"
            );

        require(
            lobby.lifecycle==
                RaidRunService
                    .Lifecycle.LOBBY&&
            lobby.participants.size()==2&&
            "player:leader".equals(
                lobby.leaderRef
            )&&
            !lobby.allReady(),
            "raid lobby snapshot"
        );

        boolean outsiderReadyRejected=false;

        try{
            raids.setReady(
                runId,
                "player:outsider",
                true
            );
        }catch(
            IllegalArgumentException expected
        ){
            outsiderReadyRejected=true;
        }

        require(
            outsiderReadyRejected,
            "non-member ready accepted"
        );

        raids.setReady(
            runId,
            "player:member",
            true
        );

        boolean earlyStartRejected=false;

        try{
            raids.start(
                runId,
                "player:leader",
                WorldInstanceId.of(
                    "raid-instance:early"
                ),
                100L
            );
        }catch(
            IllegalStateException expected
        ){
            earlyStartRejected=true;
        }

        require(
            earlyStartRejected,
            "not-all-ready raid started"
        );

        raids.setReady(
            runId,
            "player:leader",
            true
        );

        boolean nonLeaderRejected=false;

        try{
            raids.start(
                runId,
                "player:member",
                WorldInstanceId.of(
                    "raid-instance:wrong-leader"
                ),
                100L
            );
        }catch(
            IllegalStateException expected
        ){
            nonLeaderRejected=true;
        }

        require(
            nonLeaderRejected,
            "non-leader started raid"
        );

        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "raid-instance:1"
            );

        RaidRunService.Snapshot active=
            raids.start(
                runId,
                "player:leader",
                instanceId,
                100L
            );

        WorldInstanceService.Snapshot instance=
            instances.get(
                instanceId
            );

        require(
            active.lifecycle==
                RaidRunService
                    .Lifecycle.ACTIVE&&
            active.instanceId.equals(
                instanceId
            )&&
            active.startTick==100L&&
            active.stage==0&&
            instance!=null&&
            instance.lifecycle==
                WorldInstanceService
                    .Lifecycle.ACTIVE&&
            instance.participants.size()==2&&
            instance.participant(
                "player:leader"
            )&&
            instance.participant(
                "player:member"
            ),
            "raid start did not compose world instance"
        );

        boolean skippedStageRejected=false;

        try{
            raids.advanceStage(
                runId,
                2
            );
        }catch(
            IllegalStateException expected
        ){
            skippedStageRejected=true;
        }

        require(
            skippedStageRejected,
            "raid stage skipped"
        );

        for(int stage=1;
            stage<=RaidRunService.MAX_STAGE;
            stage++)
            raids.advanceStage(
                runId,
                stage
            );

        boolean stageOverflowRejected=false;

        try{
            raids.advanceStage(
                runId,
                RaidRunService.MAX_STAGE+1
            );
        }catch(
            IllegalArgumentException expected
        ){
            stageOverflowRejected=true;
        }

        require(
            stageOverflowRejected,
            "raid stage overflow accepted"
        );

        raids.awardPoints(
            runId,
            "player:leader",
            10L
        );
        RaidRunService.Snapshot points=
            raids.awardPoints(
                runId,
                "player:member",
                7L
            );

        require(
            points.participant(
                "player:leader"
            ).points==10L&&
            points.participant(
                "player:member"
            ).points==7L,
            "participant-scoped raid points"
        );

        RaidRunService.Snapshot completed=
            raids.complete(
                runId,
                160L
            );

        WorldInstanceService.Snapshot
            closedInstance=
                instances.get(
                    instanceId
                );

        require(
            completed.lifecycle==
                RaidRunService
                    .Lifecycle.COMPLETED&&
            completed.terminal()&&
            completed.terminalTick==160L&&
            completed.elapsedTicks(
                999L
            )==60L&&
            closedInstance!=null&&
            closedInstance.lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED&&
            closedInstance.participants
                .isEmpty(),
            "completed raid did not close instance"
        );

        PartyService.Snapshot partyAfter=
            parties.get(
                partyId
            );

        require(
            partyAfter!=null&&
            partyAfter.members.size()==2&&
            partyAfter.member(
                "player:leader"
            )&&
            partyAfter.member(
                "player:member"
            ),
            "raid mutated Party ownership"
        );

        boolean terminalMutationRejected=false;

        try{
            raids.awardPoints(
                runId,
                "player:leader",
                1L
            );
        }catch(
            IllegalStateException expected
        ){
            terminalMutationRejected=true;
        }

        require(
            terminalMutationRejected,
            "terminal raid accepted mutation"
        );

        testWipe(
            raids,
            parties,
            instances
        );

        testCancelLobby(
            raids,
            parties
        );

        boolean immutable=false;

        try{
            completed.participants.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "raid snapshot participants mutable"
        );

        assertProtocolIndependent();

        require(
            "EXACT_CURRENT_CLIENT".equals(
                completed.presentationAuthority
            )&&
            "LOCAL_LAB_POLICY".equals(
                completed.policyAuthority
            ),
            "raid authority boundary"
        );

        System.out.println(
            "RAID_RUN_SERVICE_PASS "+
            "partyComposition=true "+
            "leaderStartPolicy=true "+
            "allReadyPolicy=true "+
            "worldInstanceComposition=true "+
            "stage0to5=true "+
            "participantPoints=true "+
            "completionClosesInstance=true "+
            "wipeClosesInstance=true "+
            "partyOwnershipPreserved=true "+
            "terminalMutationRejected=true "+
            "localPolicyExplicit=true "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void testWipe(
        RaidRunService raids,
        PartyService parties,
        WorldInstanceService instances
    ){
        PartyId partyId=
            PartyId.of(
                "party:raid-wipe"
            );

        parties.create(
            partyId,
            "player:wipe-leader",
            PartyService.Visibility.PRIVATE,
            "LOCAL_LAB_POLICY"
        );

        RaidRunService.RaidRunId runId=
            RaidRunService.RaidRunId.of(
                "raid-run:wipe"
            );

        raids.createLobby(
            runId,
            partyId,
            "raid:local_test",
            "expert",
            "EXACT_CURRENT_CLIENT",
            "LOCAL_LAB_POLICY"
        );

        raids.setReady(
            runId,
            "player:wipe-leader",
            true
        );

        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "raid-instance:wipe"
            );

        raids.start(
            runId,
            "player:wipe-leader",
            instanceId,
            200L
        );

        RaidRunService.Snapshot wiped=
            raids.wipe(
                runId,
                220L
            );

        require(
            wiped.lifecycle==
                RaidRunService
                    .Lifecycle.WIPED&&
            wiped.terminal()&&
            instances.get(
                instanceId
            ).lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED,
            "raid wipe did not close instance"
        );
    }

    private static void testCancelLobby(
        RaidRunService raids,
        PartyService parties
    ){
        PartyId partyId=
            PartyId.of(
                "party:raid-cancel"
            );

        parties.create(
            partyId,
            "player:cancel-leader",
            PartyService.Visibility.PRIVATE,
            "LOCAL_LAB_POLICY"
        );

        RaidRunService.RaidRunId runId=
            RaidRunService.RaidRunId.of(
                "raid-run:cancel"
            );

        raids.createLobby(
            runId,
            partyId,
            "raid:local_test",
            "master",
            "EXACT_CURRENT_CLIENT",
            "LOCAL_LAB_POLICY"
        );

        RaidRunService.Snapshot cancelled=
            raids.cancelLobby(
                runId,
                300L
            );

        require(
            cancelled.lifecycle==
                RaidRunService
                    .Lifecycle.CANCELLED&&
            cancelled.instanceId==null&&
            cancelled.terminal(),
            "raid lobby cancellation"
        );
    }

    private static void assertProtocolIndependent(){
        Class<?>[] types={
            RaidRunService.class,
            RaidRunService.RaidRunId.class,
            RaidRunService.Snapshot.class,
            RaidRunService.ParticipantSnapshot.class
        };

        for(Class<?> type:types){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("client")||
                   name.contains("scene")||
                   name.contains("interface"))
                    throw new AssertionError(
                        "protocol/client identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private RaidRunServiceTest(){}
}
