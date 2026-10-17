package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class G134VotingOpenOnlyIntegrationTest {
    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalLotteryUiHandler lottery=
            new LocalLotteryUiHandler();

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void clearDialogNumberKeys(){}

        @Override public void handleDevPanelWidget(
            int widget,
            ServerPacketWriter writer,
            String tag
        )throws IOException{}

        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        ){}

        @Override public boolean retireLotteryRoot(){
            return lottery.close();
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean exactVoteLiteral=false;
        boolean votingAlias=false;
        boolean root60050=false;
        boolean topgVisible=false;
        boolean runelocusVisible=false;
        boolean rspsListVisible=false;
        boolean moparscapeExcluded=false;
        boolean noInputRouter=false;
        boolean rootReplacementLifecycle=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g134-player"
        );

        try{
            Bridge bridge=
                new Bridge();
            LocalSessionUiActionHandler ui=
                uiHandler(
                    player,
                    bridge
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{111,112,113,114}
                    )
                );

            exactVoteLiteral=
                LocalCommandDispatcher
                    .isVotingRoute(
                        new String[]{"vote"}
                    );

            votingAlias=
                LocalCommandDispatcher
                    .isVotingRoute(
                        new String[]{"voting"}
                    )&&
                !LocalCommandDispatcher
                    .isVotingRoute(
                        new String[]{"vote","invent"}
                    );

            require(
                exactVoteLiteral&&votingAlias,
                "Voting command routes"
            );

            Set<Integer> visibleWidgets=
                new HashSet<>();

            for(VotingPresentation.SiteControl site:
                    VotingPresentation.visibleSites()){
                visibleWidgets.add(
                    site.primaryWidget
                );
                visibleWidgets.add(
                    site.pairedWidget
                );

                if(site.provider==
                        VotingService.Provider.TOPG)
                    topgVisible=
                        site.primaryWidget==60052&&
                        site.pairedWidget==60053&&
                        "TopG".equals(
                            site.displayName
                        );

                if(site.provider==
                        VotingService.Provider.RUNELOCUS)
                    runelocusVisible=
                        site.primaryWidget==60055&&
                        site.pairedWidget==60056&&
                        "RuneLocus".equals(
                            site.displayName
                        );

                if(site.provider==
                        VotingService.Provider.RSPS_LIST)
                    rspsListVisible=
                        site.primaryWidget==60058&&
                        site.pairedWidget==60059&&
                        "RSPS-List".equals(
                            site.displayName
                        );
            }

            moparscapeExcluded=
                VotingPresentation
                    .visibleSites()
                    .size()==3&&
                !visibleWidgets.contains(
                    60164
                )&&
                !visibleWidgets.contains(
                    60165
                );

            require(
                topgVisible&&
                runelocusVisible&&
                rspsListVisible&&
                moparscapeExcluded,
                "Voting exact visible providers"
            );

            boolean resolverFound=false;
            boolean opcodeFound=false;

            for(Method method:
                    VotingPresentation.class
                        .getDeclaredMethods())
                if(method.getName().equals(
                        "resolveWidget"
                    ))
                    resolverFound=true;

            for(Field field:
                    VotingPresentation.class
                        .getDeclaredFields())
                if(field.getName().contains(
                        "OPCODE"
                    ))
                    opcodeFound=true;

            noInputRouter=
                !resolverFound&&
                !opcodeFound;

            require(
                noInputRouter,
                "Voting transport fence regressed"
            );

            ui.replaceMonsterSpawnerWithLotteryRoot(
                ()->{
                    bridge.lottery.open(
                        LotteryService
                            .Channel.ORDINARY,
                        writer
                    );
                    return "LOTTERY_ROOT_OPENED";
                }
            );

            require(
                bridge.lottery.isOpen(),
                "Voting lifecycle precondition"
            );

            int beforeVoting=
                wire.size();

            String opened=
                ui.replaceMonsterSpawnerRoot(
                    ()->{
                        VotingPresentation.open(
                            writer
                        );
                        return "VOTING_ROOT_OPENED";
                    }
                );

            root60050=
                "VOTING_ROOT_OPENED"
                    .equals(opened)&&
                VotingPresentation.ROOT==
                    60050&&
                wire.size()>beforeVoting;

            rootReplacementLifecycle=
                !bridge.lottery.isOpen();

            require(
                root60050&&
                rootReplacementLifecycle,
                "Voting root replacement lifecycle"
            );

            System.out.println(
                "G134_VOTING_OPEN_ONLY_PASS"+
                " exactVoteLiteral="+
                    exactVoteLiteral+
                " votingAlias="+votingAlias+
                " root60050="+root60050+
                " topgVisible="+topgVisible+
                " runelocusVisible="+
                    runelocusVisible+
                " rspsListVisible="+
                    rspsListVisible+
                " moparscapeExcluded="+
                    moparscapeExcluded+
                " noInputRouter="+noInputRouter+
                " rootReplacementLifecycle="+
                    rootReplacementLifecycle+
                " browserActionClaim=false"+
                " verificationClaim=false"+
                " pointsClaim=false"+
                " rewardsClaim=false"+
                " persistenceClaim=false"+
                " originalServerCommandBehaviorClaim=false"
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

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G134VotingOpenOnlyIntegrationTest(){}
}
