package spk.local;

import java.util.Arrays;
import java.util.LinkedHashSet;

public final class G102NormalDuelAcceptStartIntegrationTest {
    private static final String A="g102-a";
    private static final String B="g102-b";
    private static final String C="g102-c";

    public static void main(String[] args)throws Exception{
        boolean proposalOpen=false;
        boolean challengedOnlyAccept=false;
        boolean challengerCannotAccept=false;
        boolean accepted=false;
        boolean duelActive=false;
        boolean matchActive=false;
        boolean instanceActive=false;
        boolean participantsExact=false;
        boolean participantExclusive=false;

        World world=
            World.isolatedForTest(
                60_000L
            );

        world.registerPlayer(
            new WorldPlayer(),
            A
        );
        world.registerPlayer(
            new WorldPlayer(),
            B
        );
        world.registerPlayer(
            new WorldPlayer(),
            C
        );

        try{
            LocalLabDuelRuntime runtime=
                world.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation
                        .DuelMode.STANDARD
                );

            proposalOpen=
                proposal.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                runtime.openFor(A)!=null&&
                runtime.openFor(B)!=null&&
                runtime.size()==1;

            require(
                proposalOpen,
                "Duel proposal fixture missing"
            );

            try{
                runtime.acceptAndStart(A);
            }catch(IllegalArgumentException expected){
                challengerCannotAccept=true;
            }

            challengedOnlyAccept=
                challengerCannotAccept&&
                runtime.openFor(A)!=null&&
                runtime.openFor(B)!=null&&
                runtime.openFor(B).state==
                    DuelSessionService.State.PROPOSED&&
                runtime.matches().size()==0&&
                runtime.instances().size()==0;

            require(
                challengedOnlyAccept,
                "challenger self-accept mutated Duel state"
            );

            LocalLabDuelRuntime.StartResult started=
                runtime.acceptAndStart(B);

            accepted=
                started.snapshot.challengeId.equals(
                    proposal.snapshot.challengeId
                )&&
                started.snapshot.state==
                    DuelSessionService.State.ACTIVE;

            duelActive=
                started.snapshot.active()&&
                started.snapshot.matchId!=null&&
                started.snapshot.instanceId!=null;

            matchActive=
                started.match.state==
                    MatchSession.State.ACTIVE&&
                started.snapshot.matchId.equals(
                    started.match.id
                )&&
                started.snapshot.instanceId.equals(
                    started.match.instanceId
                )&&
                started.match.teams.size()==2&&
                started.match.participants.size()==2;

            instanceActive=
                started.instance.lifecycle==
                    WorldInstanceService
                        .Lifecycle.ACTIVE&&
                started.snapshot.instanceId.equals(
                    started.instance.id
                );

            participantsExact=
                new LinkedHashSet<>(
                    started.instance.participants
                ).equals(
                    new LinkedHashSet<>(
                        Arrays.asList(
                            A,
                            B
                        )
                    )
                )&&
                started.match.participant(A)!=null&&
                started.match.participant(B)!=null;

            require(
                accepted&&
                duelActive&&
                matchActive&&
                instanceActive&&
                participantsExact,
                "normal Duel active composition failed"
            );

            try{
                runtime.propose(
                    C,
                    A,
                    NormalDuelPresentation
                        .DuelMode.STANDARD
                );
            }catch(IllegalStateException expected){
                participantExclusive=
                    runtime.size()==1&&
                    runtime.openFor(C)==null&&
                    runtime.openFor(A)!=null&&
                    runtime.openFor(A).state==
                        DuelSessionService.State.ACTIVE;
            }

            require(
                participantExclusive,
                "ACTIVE Duel participant exclusivity failed"
            );

            System.out.println(
                "G102_NORMAL_DUEL_ACCEPT_START_PASS"+
                " proposalOpen="+proposalOpen+
                " challengedOnlyAccept="+
                    challengedOnlyAccept+
                " challengerCannotAccept="+
                    challengerCannotAccept+
                " accepted="+accepted+
                " duelActive="+duelActive+
                " matchActive="+matchActive+
                " instanceActive="+instanceActive+
                " participantsExact="+
                    participantsExact+
                " participantExclusive="+
                    participantExclusive+
                " acceptWidgetClaim=false"+
                " arenaClaim=false"+
                " stakeClaim=false"+
                " restrictionClaim=false"+
                " winnerClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
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

    private G102NormalDuelAcceptStartIntegrationTest(){}
}
