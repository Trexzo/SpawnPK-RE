package spk.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class G106DuelDisconnectCleanupIntegrationTest {
    private static final String A="g106-a";
    private static final String B="g106-b";
    private static final String C="g106-c";

    public static void main(String[] args)throws Exception{
        boolean proposedCancelled=false;
        boolean activeCancelled=false;
        boolean matchCancelled=false;
        boolean instanceClosed=false;
        boolean leasesReleased=false;
        boolean participantsReleased=false;
        boolean staleGenerationNoop=false;
        boolean remainingPlayerReusable=false;
        boolean unrelatedUnregisterNoop=false;
        boolean cleanupFailureContained=false;

        World proposedWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer proposedA=new WorldPlayer();
        WorldPlayer proposedB=new WorldPlayer();
        WorldPlayer proposedC=new WorldPlayer();
        long proposedAGeneration=
            proposedWorld.registerPlayer(
                proposedA,
                A
            );
        proposedWorld.registerPlayer(
            proposedB,
            B
        );
        proposedWorld.registerPlayer(
            proposedC,
            C
        );

        try{
            LocalLabDuelRuntime runtime=
                proposedWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            require(
                proposedWorld.unregisterPlayer(
                    proposedA,
                    proposedAGeneration
                ),
                "PROPOSED Duel participant unregister failed"
            );

            DuelSessionService.Snapshot after=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );

            proposedCancelled=
                after!=null&&
                after.state==
                    DuelSessionService.State.CANCELLED;

            participantsReleased=
                runtime.openFor(A)==null&&
                runtime.openFor(B)==null;

            require(
                proposedCancelled&&
                participantsReleased,
                "PROPOSED Duel was not retired on unregister"
            );

            LocalLabDuelRuntime.ProposalResult reused=
                runtime.propose(
                    B,
                    C,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            remainingPlayerReusable=
                reused.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                runtime.openFor(B)!=null&&
                runtime.openFor(C)!=null;

            require(
                remainingPlayerReusable,
                "remaining participant stayed reserved after unregister cleanup"
            );

            runtime.duels()
                .cancelOpen(
                    reused.snapshot.challengeId,
                    B
                );
        }finally{
            proposedWorld.close();
        }

        World activeWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer activeA=new WorldPlayer();
        WorldPlayer activeB=new WorldPlayer();
        long activeAGeneration=
            activeWorld.registerPlayer(
                activeA,
                A
            );
        activeWorld.registerPlayer(
            activeB,
            B
        );

        try{
            LocalLabDuelRuntime runtime=
                activeWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            LocalLabDuelRuntime.StartResult started=
                runtime.acceptAndStart(
                    B
                );

            require(
                runtime.matches()
                    .compositionLeaseHeld(
                        started.snapshot.matchId
                    )&&
                runtime.instances()
                    .compositionLeaseHeld(
                        started.snapshot.instanceId
                    ),
                "ACTIVE Duel fixture missing composition leases"
            );

            require(
                activeWorld.unregisterPlayer(
                    activeA,
                    activeAGeneration
                ),
                "ACTIVE Duel participant unregister failed"
            );

            DuelSessionService.Snapshot after=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );
            MatchSession match=
                runtime.matches()
                    .get(
                        started.snapshot.matchId
                    );
            WorldInstanceService.Snapshot instance=
                runtime.instances()
                    .get(
                        started.snapshot.instanceId
                    );

            activeCancelled=
                after!=null&&
                after.state==
                    DuelSessionService.State.CANCELLED;

            matchCancelled=
                match!=null&&
                match.state==
                    MatchSession.State.CANCELLED&&
                "participant-disconnected".equals(
                    match.cancellationReasonKey
                )&&
                match.result==null;

            instanceClosed=
                instance!=null&&
                instance.lifecycle==
                    WorldInstanceService.Lifecycle.CLOSED;

            leasesReleased=
                !runtime.matches()
                    .compositionLeaseHeld(
                        started.snapshot.matchId
                    )&&
                !runtime.instances()
                    .compositionLeaseHeld(
                        started.snapshot.instanceId
                    );

            participantsReleased=
                participantsReleased&&
                runtime.openFor(A)==null&&
                runtime.openFor(B)==null;

            require(
                activeCancelled&&
                matchCancelled&&
                instanceClosed&&
                leasesReleased&&
                participantsReleased,
                "ACTIVE Duel unregister cleanup postimage failed"
            );
        }finally{
            activeWorld.close();
        }

        World staleWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer staleA=new WorldPlayer();
        WorldPlayer staleB=new WorldPlayer();
        long staleAGeneration=
            staleWorld.registerPlayer(
                staleA,
                A
            );
        staleWorld.registerPlayer(
            staleB,
            B
        );

        try{
            LocalLabDuelRuntime runtime=
                staleWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            boolean removed=
                staleWorld.unregisterPlayer(
                    staleA,
                    staleAGeneration+1L
                );

            DuelSessionService.Snapshot after=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );

            staleGenerationNoop=
                !removed&&
                staleA.registered()&&
                after!=null&&
                after.state==
                    DuelSessionService.State.PROPOSED&&
                runtime.openFor(A)!=null&&
                runtime.openFor(B)!=null;

            require(
                staleGenerationNoop,
                "stale-generation unregister mutated Duel state"
            );

            require(
                staleWorld.unregisterPlayer(
                    staleA,
                    staleAGeneration
                ),
                "stale fixture canonical unregister cleanup"
            );
        }finally{
            staleWorld.close();
        }

        World unrelatedWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer unrelatedA=new WorldPlayer();
        WorldPlayer unrelatedB=new WorldPlayer();
        WorldPlayer unrelatedC=new WorldPlayer();
        unrelatedWorld.registerPlayer(
            unrelatedA,
            A
        );
        unrelatedWorld.registerPlayer(
            unrelatedB,
            B
        );
        long unrelatedCGeneration=
            unrelatedWorld.registerPlayer(
                unrelatedC,
                C
            );

        try{
            LocalLabDuelRuntime runtime=
                unrelatedWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            require(
                unrelatedWorld.unregisterPlayer(
                    unrelatedC,
                    unrelatedCGeneration
                ),
                "unrelated participant unregister failed"
            );

            DuelSessionService.Snapshot after=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );

            unrelatedUnregisterNoop=
                after!=null&&
                after.state==
                    DuelSessionService.State.PROPOSED&&
                runtime.openFor(A)!=null&&
                runtime.openFor(B)!=null;

            require(
                unrelatedUnregisterNoop,
                "unrelated unregister cancelled Duel"
            );

            runtime.duels()
                .cancelOpen(
                    proposal.snapshot.challengeId,
                    A
                );
        }finally{
            unrelatedWorld.close();
        }

        List<String> cleanupOrder=
            new ArrayList<>();

        WorldPlayerUnregisterCleanup.run(
            ()->cleanupOrder.add(
                "PERSISTENCE"
            ),
            ()->cleanupOrder.add(
                "COMMAND"
            ),
            ()->cleanupOrder.add(
                "REALTIME"
            ),
            ()->{
                cleanupOrder.add(
                    "DUEL"
                );
                throw new IllegalStateException(
                    "expected-duel-cleanup-failure"
                );
            },
            ()->cleanupOrder.add(
                "PET"
            )
        );

        cleanupFailureContained=
            cleanupOrder.equals(
                Arrays.asList(
                    "PERSISTENCE",
                    "COMMAND",
                    "REALTIME",
                    "DUEL",
                    "PET"
                )
            );

        require(
            cleanupFailureContained,
            "Duel cleanup failure suppressed later unregister cleanup "+
            cleanupOrder
        );

        System.out.println(
            "G106_DUEL_DISCONNECT_CLEANUP_PASS"+
            " proposedCancelled="+proposedCancelled+
            " activeCancelled="+activeCancelled+
            " matchCancelled="+matchCancelled+
            " instanceClosed="+instanceClosed+
            " leasesReleased="+leasesReleased+
            " participantsReleased="+participantsReleased+
            " staleGenerationNoop="+staleGenerationNoop+
            " remainingPlayerReusable="+remainingPlayerReusable+
            " unrelatedUnregisterNoop="+unrelatedUnregisterNoop+
            " cleanupFailureContained="+cleanupFailureContained+
            " winnerClaim=false"+
            " rewardClaim=false"+
            " stakeClaim=false"+
            " reconnectClaim=false"+
            " persistenceClaim=false"+
            " originalSpawnpkPolicyClaim=false"
        );
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G106DuelDisconnectCleanupIntegrationTest(){}
}
