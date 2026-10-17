package spk.local;

import java.io.ByteArrayOutputStream;

public final class G91TournamentRegistrationIntegrationTest {
    private static final String FIRST="g91-first";
    private static final String SECOND="g91-second";

    public static void main(String[] args)throws Exception{
        boolean exactRoot27400=false;
        boolean exactEnter56044=false;
        boolean c2s185=false;
        boolean worldOwned=false;
        boolean scheduledOnly=false;
        boolean enterRegisters=false;
        boolean duplicateEnterIdempotent=false;
        boolean playerIsolation=false;
        boolean spectateUnsupported=false;
        boolean shopUnsupported=false;
        boolean rootLifecycle=false;
        boolean closedUiNoop=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer first=
            new WorldPlayer();
        WorldPlayer second=
            new WorldPlayer();

        world.registerPlayer(
            first,
            FIRST
        );
        world.registerPlayer(
            second,
            SECOND
        );

        try{
            LocalLabTournamentRuntime runtime=
                world.localTournament();

            worldOwned=
                runtime!=null&&
                runtime==world.localTournament();

            TournamentService.Snapshot initial=
                runtime.snapshot();

            scheduledOnly=
                initial.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.SCHEDULED&&
                runtime.entrantCount()==0&&
                runtime.events()
                    .get(
                        LocalLabTournamentRuntime
                            .EVENT_ID
                    )
                    .lifecycle==
                        GlobalEventService
                            .Lifecycle.SCHEDULED&&
                runtime.matches().size()==0&&
                runtime.instances().size()==0;

            TournamentPresentation.Input enter=
                TournamentPresentation.resolveWidget(
                    TournamentPresentation.ENTER_WIDGET
                );

            exactRoot27400=
                TournamentPresentation
                    .TOURNAMENT_ROOT==27400;
            exactEnter56044=
                TournamentPresentation
                    .ENTER_WIDGET==56044&&
                enter!=null&&
                enter.kind==
                    TournamentPresentation
                        .InputKind.ENTER;
            c2s185=
                TournamentPresentation
                    .WIDGET_ACTION_OPCODE==185;

            require(
                exactRoot27400&&
                exactEnter56044&&
                c2s185&&
                worldOwned&&
                scheduledOnly,
                "G9.1 exact/runtime authority setup failed"
            );

            LocalTournamentUiHandler firstUi=
                new LocalTournamentUiHandler(
                    first,
                    runtime
                );
            LocalTournamentUiHandler secondUi=
                new LocalTournamentUiHandler(
                    second,
                    runtime
                );

            LocalTournamentUiHandler.Result preOpen=
                firstUi.handle(
                    enter
                );

            closedUiNoop=
                preOpen.status==
                    LocalTournamentUiHandler
                        .Status.CLOSED_UI_NOOP&&
                runtime.entrantCount()==0;

            require(
                closedUiNoop,
                "closed Tournament root accepted ENTER"
            );

            ByteArrayOutputStream firstWire=
                new ByteArrayOutputStream();
            ServerPacketWriter firstWriter=
                writer(
                    firstWire,
                    11
                );

            int beforeOpen=
                firstWire.size();

            LocalTournamentUiHandler.Result opened=
                firstUi.open(
                    firstWriter
                );

            rootLifecycle=
                opened.status==
                    LocalTournamentUiHandler
                        .Status.OPENED&&
                firstUi.isOpen()&&
                firstWire.size()>beforeOpen;

            require(
                rootLifecycle,
                "Tournament exact root did not open"
            );

            LocalTournamentUiHandler.Result firstEnter=
                firstUi.handle(
                    enter
                );

            TournamentService.Snapshot afterFirst=
                runtime.snapshot();

            TournamentService.EntrantSnapshot
                firstEntrant=
                    afterFirst.entrant(
                        FIRST
                    );

            enterRegisters=
                firstEnter.status==
                    LocalTournamentUiHandler
                        .Status.REGISTERED&&
                firstEnter.entrantCount==1&&
                firstEntrant!=null&&
                firstEntrant.state==
                    TournamentService
                        .EntrantState.REGISTERED;

            require(
                enterRegisters,
                "exact Tournament ENTER did not register current player"
            );

            LocalTournamentUiHandler.Result duplicate=
                firstUi.handle(
                    enter
                );

            duplicateEnterIdempotent=
                duplicate.status==
                    LocalTournamentUiHandler
                        .Status.ALREADY_REGISTERED&&
                duplicate.entrantCount==1&&
                runtime.entrantCount()==1;

            require(
                duplicateEnterIdempotent,
                "duplicate Tournament ENTER mutated registration"
            );

            ByteArrayOutputStream secondWire=
                new ByteArrayOutputStream();
            ServerPacketWriter secondWriter=
                writer(
                    secondWire,
                    21
                );

            secondUi.open(
                secondWriter
            );

            LocalTournamentUiHandler.Result secondEnter=
                secondUi.handle(
                    enter
                );

            TournamentService.Snapshot afterSecond=
                runtime.snapshot();

            playerIsolation=
                secondEnter.status==
                    LocalTournamentUiHandler
                        .Status.REGISTERED&&
                secondEnter.entrantCount==2&&
                afterSecond.entrant(
                    FIRST
                )!=null&&
                afterSecond.entrant(
                    SECOND
                )!=null&&
                afterSecond.entrant(
                    FIRST
                ).state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                afterSecond.entrant(
                    SECOND
                ).state==
                    TournamentService
                        .EntrantState.REGISTERED;

            require(
                playerIsolation,
                "Tournament registrations were not player-isolated"
            );

            TournamentPresentation.Input spectate=
                TournamentPresentation.resolveWidget(
                    TournamentPresentation
                        .SPECTATE_WIDGET
                );
            TournamentPresentation.Input shop=
                TournamentPresentation.resolveWidget(
                    TournamentPresentation
                        .SHOP_WIDGET
                );

            int beforeUnsupported=
                runtime.entrantCount();

            LocalTournamentUiHandler.Result
                spectateResult=
                    firstUi.handle(
                        spectate
                    );
            LocalTournamentUiHandler.Result
                shopResult=
                    firstUi.handle(
                        shop
                    );

            spectateUnsupported=
                spectateResult.status==
                    LocalTournamentUiHandler
                        .Status.UNSUPPORTED&&
                runtime.entrantCount()==
                    beforeUnsupported;
            shopUnsupported=
                shopResult.status==
                    LocalTournamentUiHandler
                        .Status.UNSUPPORTED&&
                runtime.entrantCount()==
                    beforeUnsupported;

            require(
                spectateUnsupported&&
                shopUnsupported,
                "unsupported Tournament controls mutated state"
            );

            require(
                firstUi.close()&&
                !firstUi.isOpen(),
                "Tournament root did not close"
            );

            LocalTournamentUiHandler.Result postClose=
                firstUi.handle(
                    enter
                );

            closedUiNoop=
                closedUiNoop&&
                postClose.status==
                    LocalTournamentUiHandler
                        .Status.CLOSED_UI_NOOP&&
                runtime.entrantCount()==2;

            rootLifecycle=
                rootLifecycle&&
                !firstUi.isOpen()&&
                secondUi.isOpen();

            require(
                rootLifecycle&&
                closedUiNoop,
                "Tournament root lifecycle/input fence failed"
            );

            TournamentService.Snapshot terminalCheck=
                runtime.snapshot();

            scheduledOnly=
                scheduledOnly&&
                terminalCheck.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.SCHEDULED&&
                runtime.events()
                    .get(
                        LocalLabTournamentRuntime
                            .EVENT_ID
                    )
                    .lifecycle==
                        GlobalEventService
                            .Lifecycle.SCHEDULED&&
                runtime.matches().size()==0&&
                runtime.instances().size()==0;

            require(
                scheduledOnly,
                "G9.1 registration unexpectedly advanced Tournament lifecycle"
            );

            System.out.println(
                "G91_TOURNAMENT_REGISTRATION_PASS"+
                " exactRoot27400="+exactRoot27400+
                " exactEnter56044="+exactEnter56044+
                " c2s185="+c2s185+
                " worldOwned="+worldOwned+
                " scheduledOnly="+scheduledOnly+
                " enterRegisters="+enterRegisters+
                " duplicateEnterIdempotent="+
                    duplicateEnterIdempotent+
                " playerIsolation="+playerIsolation+
                " spectateUnsupported="+
                    spectateUnsupported+
                " shopUnsupported="+shopUnsupported+
                " rootLifecycle="+rootLifecycle+
                " closedUiNoop="+closedUiNoop+
                " autoStartClaim=false"+
                " bracketClaim=false"+
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

    private G91TournamentRegistrationIntegrationTest(){}
}
