package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class G109NormalDuelLiveCommandsIntegrationTest {
    private static final String A="g109-a";
    private static final String B="g109-b";
    private static final String C="g109-c";
    private static final String D="g109-d";
    private static final String E="g109-e";
    private static final String F="g109-f";

    private static final class Bridge
        implements LocalCommandDispatcher.SessionBridge
    {
        final LocalDuelCommandHandler handler;
        LocalDuelCommandHandler.Result last;

        Bridge(
            LocalDuelCommandHandler handler
        ){
            this.handler=handler;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return null;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){}

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void openDevPanel(
            ServerPacketWriter serverPackets
        )throws IOException{}

        @Override public LocalDuelCommandHandler.Result
            handleDuelCommand(
                String[] tokens
            ){
            last=handler.handle(tokens);
            return last;
        }

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}
    }

    public static void main(String[] args)throws Exception{
        boolean acceptRoute=false;
        boolean declineRoute=false;
        boolean cancelRoute=false;
        boolean commandAuthority=false;
        boolean acceptStartsActive=false;
        boolean declineTerminal=false;
        boolean cancelTerminal=false;
        boolean participantsReleased=false;
        boolean invalidStateNoMutation=false;
        boolean clientFeedback=false;

        World world=
            World.isolatedForTest(
                60_000L
            );

        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        WorldPlayer c=new WorldPlayer();
        WorldPlayer d=new WorldPlayer();
        WorldPlayer e=new WorldPlayer();
        WorldPlayer f=new WorldPlayer();

        world.registerPlayer(a,A);
        world.registerPlayer(b,B);
        world.registerPlayer(c,C);
        world.registerPlayer(d,D);
        world.registerPlayer(e,E);
        world.registerPlayer(f,F);

        try{
            LocalLabDuelRuntime runtime=
                world.localDuels();

            Bridge bridgeB=
                new Bridge(
                    new LocalDuelCommandHandler(
                        b,
                        runtime
                    )
                );
            Bridge bridgeD=
                new Bridge(
                    new LocalDuelCommandHandler(
                        d,
                        runtime
                    )
                );
            Bridge bridgeE=
                new Bridge(
                    new LocalDuelCommandHandler(
                        e,
                        runtime
                    )
                );

            acceptRoute=
                LocalDuelCommandHandler.isRoute(
                    new String[]{"duelaccept"}
                );
            declineRoute=
                LocalDuelCommandHandler.isRoute(
                    new String[]{"dueldecline"}
                );
            cancelRoute=
                LocalDuelCommandHandler.isRoute(
                    new String[]{"duelcancel"}
                );
            commandAuthority=
                "CUSTOM_LOCALLAB".equals(
                    LocalDuelCommandHandler.ROUTE_AUTHORITY
                )&&
                LocalLabDuelRuntime.AUTHORITY.equals(
                    LocalDuelCommandHandler.AUTHORITY
                );

            require(
                acceptRoute&&
                declineRoute&&
                cancelRoute&&
                commandAuthority,
                "G10.9 command routing authority setup failed"
            );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                writer(
                    wire,
                    11
                );

            LocalLabDuelRuntime.ProposalResult acceptProposal=
                runtime.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            int beforeAccept=wire.size();

            require(
                LocalCommandDispatcher
                    .dispatchDuelLifecycleCommand(
                        new String[]{"duelaccept"},
                        bridgeB,
                        writer,
                        "[g109-accept] "
                    ),
                "duelaccept was not handled"
            );

            DuelSessionService.Snapshot accepted=
                runtime.duels().get(
                    acceptProposal.snapshot.challengeId
                );

            acceptStartsActive=
                bridgeB.last!=null&&
                bridgeB.last.mutated&&
                accepted!=null&&
                accepted.state==
                    DuelSessionService.State.ACTIVE&&
                accepted.matchId!=null&&
                accepted.instanceId!=null&&
                wire.size()>beforeAccept;

            require(
                acceptStartsActive,
                "duelaccept did not start active Duel"
            );

            LocalLabDuelRuntime.ProposalResult declineProposal=
                runtime.propose(
                    C,
                    D,
                    NormalDuelPresentation.DuelMode.WHIP_ONLY
                );

            int beforeDecline=wire.size();

            require(
                LocalCommandDispatcher
                    .dispatchDuelLifecycleCommand(
                        new String[]{"dueldecline"},
                        bridgeD,
                        writer,
                        "[g109-decline] "
                    ),
                "dueldecline was not handled"
            );

            DuelSessionService.Snapshot declined=
                runtime.duels().get(
                    declineProposal.snapshot.challengeId
                );

            declineTerminal=
                bridgeD.last!=null&&
                bridgeD.last.mutated&&
                declined!=null&&
                declined.state==
                    DuelSessionService.State.DECLINED&&
                wire.size()>beforeDecline;

            require(
                declineTerminal,
                "dueldecline did not terminalize proposal"
            );

            LocalLabDuelRuntime.ProposalResult cancelProposal=
                runtime.propose(
                    E,
                    F,
                    NormalDuelPresentation.DuelMode.WHIP_DDS_ONLY
                );

            int beforeCancel=wire.size();

            require(
                LocalCommandDispatcher
                    .dispatchDuelLifecycleCommand(
                        new String[]{"duelcancel"},
                        bridgeE,
                        writer,
                        "[g109-cancel] "
                    ),
                "duelcancel was not handled"
            );

            DuelSessionService.Snapshot cancelled=
                runtime.duels().get(
                    cancelProposal.snapshot.challengeId
                );

            cancelTerminal=
                bridgeE.last!=null&&
                bridgeE.last.mutated&&
                cancelled!=null&&
                cancelled.state==
                    DuelSessionService.State.CANCELLED&&
                wire.size()>beforeCancel;

            participantsReleased=
                runtime.openFor(C)==null&&
                runtime.openFor(D)==null&&
                runtime.openFor(E)==null&&
                runtime.openFor(F)==null;

            require(
                cancelTerminal&&
                participantsReleased,
                "duelcancel did not release proposal participants"
            );

            int beforeInvalidEntries=
                runtime.size();
            int beforeInvalidWire=
                wire.size();

            require(
                LocalCommandDispatcher
                    .dispatchDuelLifecycleCommand(
                        new String[]{"dueldecline"},
                        bridgeD,
                        writer,
                        "[g109-invalid] "
                    ),
                "invalid-state dueldecline route was not claimed"
            );

            invalidStateNoMutation=
                bridgeD.last!=null&&
                !bridgeD.last.mutated&&
                runtime.size()==
                    beforeInvalidEntries&&
                runtime.openFor(C)==null&&
                runtime.openFor(D)==null;

            clientFeedback=
                wire.size()>beforeInvalidWire&&
                beforeDecline>beforeAccept&&
                beforeCancel>beforeDecline;

            require(
                invalidStateNoMutation&&
                clientFeedback,
                "invalid Duel command mutated state or lost feedback"
            );

            System.out.println(
                "G109_NORMAL_DUEL_LIVE_COMMANDS_PASS"+
                " acceptRoute="+acceptRoute+
                " declineRoute="+declineRoute+
                " cancelRoute="+cancelRoute+
                " commandAuthority="+commandAuthority+
                " acceptStartsActive="+acceptStartsActive+
                " declineTerminal="+declineTerminal+
                " cancelTerminal="+cancelTerminal+
                " participantsReleased="+participantsReleased+
                " invalidStateNoMutation="+invalidStateNoMutation+
                " clientFeedback="+clientFeedback+
                " nativeAcceptWidgetClaim=false"+
                " nativeDeclineWidgetClaim=false"+
                " nativeCancelWidgetClaim=false"+
                " originalCommandClaim=false"+
                " arenaClaim=false"+
                " stakeClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
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

    private G109NormalDuelLiveCommandsIntegrationTest(){}
}
