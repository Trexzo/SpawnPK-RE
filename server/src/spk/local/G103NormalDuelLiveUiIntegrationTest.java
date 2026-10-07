package spk.local;

import java.io.ByteArrayOutputStream;

public final class G103NormalDuelLiveUiIntegrationTest {
    private static final String A="g103-a";
    private static final String B="g103-b";
    private static final String C="g103-c";

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalDuelUiHandler duelUi;
        boolean tournamentOpen;

        Bridge(LocalDuelUiHandler duelUi){
            this.duelUi=duelUi;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter serverPackets,
            String tag
        )throws java.io.IOException{}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public boolean retireTournamentRoot(){
            boolean wasOpen=tournamentOpen;
            tournamentOpen=false;
            return wasOpen;
        }

        @Override public boolean retireDuelRoot(){
            return duelUi.close();
        }

        @Override public LocalDuelUiHandler.Result
            handleDuelWidget(
                NormalDuelPresentation.Input input,
                String tag
            ){
            return duelUi.handle(input);
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean commandRoute=false;
        boolean exactRoot25754=false;
        boolean exactC2S185=false;
        boolean targetBound=false;
        boolean standardProposal=false;
        boolean participantExclusive=false;
        boolean rootReplacement=false;
        boolean interfaceCloseRetires=false;
        boolean closedUiNoop=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        WorldPlayer c=new WorldPlayer();

        world.registerPlayer(a,A);
        world.registerPlayer(b,B);
        world.registerPlayer(c,C);

        try{
            LocalDuelUiHandler duelUi=
                new LocalDuelUiHandler(
                    a,
                    world.localDuels()
                );

            Bridge bridge=
                new Bridge(
                    duelUi
                );

            LocalSessionUiActionHandler ui=
                uiHandler(
                    a,
                    bridge
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                writer(
                    wire,
                    11
                );

            String[] route=
                LocalCommandDispatcher.tokens(
                    "duel "+B
                );

            commandRoute=
                LocalCommandDispatcher
                    .isDuelRoute(route)&&
                B.equals(
                    LocalCommandDispatcher
                        .duelTarget(route)
                );

            exactRoot25754=
                NormalDuelPresentation
                    .SELECTOR_ROOT==25754;
            exactC2S185=
                NormalDuelPresentation
                    .WIDGET_ACTION_OPCODE==185;

            require(
                commandRoute&&
                exactRoot25754&&
                exactC2S185,
                "G10.3 route/exact authority setup"
            );

            int beforeOpen=wire.size();

            String opened=
                ui.replaceMonsterSpawnerWithDuelRoot(
                    ()->{
                        duelUi.open(
                            B,
                            writer
                        );
                        return "DUEL_OPENED";
                    }
                );

            targetBound=
                "DUEL_OPENED".equals(opened)&&
                duelUi.isOpen()&&
                B.equals(
                    duelUi.targetRef()
                )&&
                wire.size()>beforeOpen;

            require(
                targetBound,
                "live Duel target/root binding"
            );

            bridge.tournamentOpen=false;

            String tournament=
                ui.replaceMonsterSpawnerWithTournamentRoot(
                    ()->{
                        bridge.tournamentOpen=true;
                        return "TOURNAMENT_OPENED";
                    }
                );

            rootReplacement=
                "TOURNAMENT_OPENED".equals(
                    tournament
                )&&
                bridge.tournamentOpen&&
                !duelUi.isOpen();

            require(
                rootReplacement,
                "competing Tournament root did not retire Duel"
            );

            ui.replaceMonsterSpawnerWithDuelRoot(
                ()->{
                    duelUi.open(
                        B,
                        writer
                    );
                    return "DUEL_REOPENED";
                }
            );

            require(
                !bridge.tournamentOpen&&
                duelUi.isOpen(),
                "Duel target root did not retire Tournament"
            );

            ui.handleWidget(
                NormalDuelPresentation
                    .STANDARD_WIDGET,
                writer,
                "[g103] "
            );

            require(
                duelUi.mode()==
                    NormalDuelPresentation
                        .DuelMode.STANDARD,
                "live exact Standard widget did not select mode"
            );

            ui.handleWidget(
                NormalDuelPresentation
                    .INVITE_WIDGET,
                writer,
                "[g103] "
            );

            DuelSessionService.Snapshot proposal=
                world.localDuels()
                    .openFor(A);

            standardProposal=
                proposal!=null&&
                proposal.state==
                    DuelSessionService
                        .State.PROPOSED&&
                "standard".equals(
                    proposal.duelTypeKey
                )&&
                world.localDuels()
                    .openFor(B)!=null&&
                !duelUi.isOpen();

            require(
                standardProposal,
                "live exact Duel Invite did not create proposal"
            );

            try{
                world.localDuels()
                    .propose(
                        C,
                        B,
                        NormalDuelPresentation
                            .DuelMode.STANDARD
                    );
            }catch(IllegalStateException expected){
                participantExclusive=
                    world.localDuels()
                        .openFor(C)==null&&
                    world.localDuels()
                        .openFor(B)!=null;
            }

            require(
                participantExclusive,
                "live Duel proposal lost participant exclusivity"
            );

            ui.replaceMonsterSpawnerWithDuelRoot(
                ()->{
                    duelUi.open(
                        B,
                        writer
                    );
                    return "DUEL_FOR_CLOSE";
                }
            );

            require(
                duelUi.isOpen(),
                "Duel root did not reopen before interface close"
            );

            ui.handleInterfaceClose(
                true,
                writer,
                "[g103] "
            );

            interfaceCloseRetires=
                !duelUi.isOpen();

            require(
                interfaceCloseRetires,
                "opcode-130 interface close did not retire Duel root"
            );

            DuelSessionService.Snapshot beforeClosedAction=
                world.localDuels()
                    .openFor(A);

            ui.handleWidget(
                NormalDuelPresentation
                    .WHIP_WIDGET,
                writer,
                "[g103-closed] "
            );

            DuelSessionService.Snapshot afterClosedAction=
                world.localDuels()
                    .openFor(A);

            closedUiNoop=
                !duelUi.isOpen()&&
                beforeClosedAction!=null&&
                afterClosedAction!=null&&
                beforeClosedAction.challengeId.equals(
                    afterClosedAction.challengeId
                )&&
                beforeClosedAction.state==
                    afterClosedAction.state;

            require(
                closedUiNoop,
                "closed Duel widget mutated live state"
            );

            System.out.println(
                "G103_NORMAL_DUEL_LIVE_UI_PASS"+
                " commandRoute="+commandRoute+
                " exactRoot25754="+
                    exactRoot25754+
                " exactC2S185="+exactC2S185+
                " targetBound="+targetBound+
                " standardProposal="+
                    standardProposal+
                " participantExclusive="+
                    participantExclusive+
                " rootReplacement="+
                    rootReplacement+
                " interfaceCloseRetires="+
                    interfaceCloseRetires+
                " closedUiNoop="+
                    closedUiNoop+
                " targetTransportClaim=false"+
                " acceptWidgetClaim=false"+
                " arenaClaim=false"+
                " stakeClaim=false"+
                " restrictionClaim=false"+
                " winnerClaim=false"+
                " rewardClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static LocalSessionUiActionHandler uiHandler(
        WorldPlayer player,
        Bridge bridge
    ){
        BankState bank=player.bank();
        EquipmentState equipment=
            player.equipment();
        MovementState movement=
            player.movement();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=
            new NpcRegistry(dev);

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                player.petAccessoryState()
            );

        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                player.prayers(),
                player.playerState(),
                equipment,
                player.combatStyles(),
                player.magic(),
                bank
            );

        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                bank,
                player.playerState()
            );

        return new LocalSessionUiActionHandler(
            player,
            new NativeItemLibraryService(),
            new DevControlCenter(),
            bank,
            compCape,
            petDialogs,
            gameplay,
            movement,
            true,
            equipment,
            bridge
        );
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

    private G103NormalDuelLiveUiIntegrationTest(){}
}
