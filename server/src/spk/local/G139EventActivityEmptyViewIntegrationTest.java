package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public final class G139EventActivityEmptyViewIntegrationTest {
    private static final int[] SEED={161,162,163,164};

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalBloodFountainUiHandler blood=
            new LocalBloodFountainUiHandler();

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

        @Override public boolean retireBloodFountainRoot(){
            return blood.close();
        }

        @Override public void requestLogout(){}
    }

    public static void main(String[] args)throws Exception{
        boolean customCommand=false;
        boolean root30072=false;
        boolean subtype6=false;
        boolean clearRender=false;
        boolean zeroRows=false;
        boolean maxRows7=false;
        boolean rootReplacementLifecycle=false;
        boolean eventActivityServiceCreated=false;
        boolean quotaServiceCreated=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "g139-player"
        );

        try{
            customCommand=
                LocalCommandDispatcher
                    .isEventActivityRoute(
                        new String[]{"eventactivity"}
                    )&&
                LocalCommandDispatcher
                    .isEventActivityRoute(
                        new String[]{"activities"}
                    )&&
                !LocalCommandDispatcher
                    .isEventActivityRoute(
                        new String[]{"activities","invent"}
                    );

            require(
                customCommand,
                "Event Activity LocalLab route"
            );

            subtype6=
                EventActivityPresentation
                    .APPLICATION_SUBTYPE==6;
            maxRows7=
                EventActivityPresentation
                    .MAX_ROWS==7;

            require(
                subtype6&&maxRows7,
                "Event Activity exact constants"
            );

            Bridge bridge=
                new Bridge();
            LocalSessionUiActionHandler ui=
                uiHandler(
                    player,
                    bridge
                );
            LocalEventActivityUiHandler activity=
                new LocalEventActivityUiHandler();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );

            ui.replaceMonsterSpawnerWithBloodFountainRoot(
                ()->{
                    bridge.blood.openHub(
                        writer
                    );
                    return "BLOOD_FOUNTAIN_ROOT_OPENED";
                }
            );

            require(
                bridge.blood.isOpen(),
                "Event Activity lifecycle precondition"
            );

            int beforeActivity=
                wire.size();

            String opened=
                ui.replaceMonsterSpawnerRoot(
                    ()->{
                        activity.open(
                            writer
                        );
                        return "EVENT_ACTIVITY_ROOT_OPENED";
                    }
                );

            rootReplacementLifecycle=
                !bridge.blood.isOpen();

            require(
                "EVENT_ACTIVITY_ROOT_OPENED"
                    .equals(opened)&&
                rootReplacementLifecycle,
                "Event Activity root replacement"
            );

            byte[] all=wire.toByteArray();
            byte[] candidate=
                java.util.Arrays.copyOfRange(
                    all,
                    beforeActivity,
                    all.length
                );

            IsaacCipher decode=
                new IsaacCipher(
                    SEED.clone()
                );

            /*
             * Advance the decoder over the earlier Blood Fountain root packet.
             * That publication consumed exactly one encoded opcode.
             */
            decode.nextInt();

            int offset=0;

            int rootOpcode=
                ((candidate[offset++]&255)-
                    decode.nextInt())&
                    255;

            root30072=
                rootOpcode==97&&
                candidate.length>=3&&
                (candidate[offset++]&255)==
                    (30072>>>8)&&
                (candidate[offset++]&255)==
                    (30072&255);

            require(
                root30072,
                "Event Activity exact root packet"
            );

            int clearOpcode=
                ((candidate[offset++]&255)-
                    decode.nextInt())&
                    255;
            int clearLength=
                candidate[offset++]&255;

            clearRender=
                clearOpcode==250&&
                clearLength==3&&
                (candidate[offset++]&255)==0&&
                (candidate[offset++]&255)==6&&
                (candidate[offset++]&255)==0;

            require(
                clearRender,
                "Event Activity exact clear packet"
            );

            int renderOpcode=
                ((candidate[offset++]&255)-
                    decode.nextInt())&
                    255;
            int renderLength=
                candidate[offset++]&255;

            clearRender&=
                renderOpcode==250&&
                renderLength==3&&
                (candidate[offset++]&255)==0&&
                (candidate[offset++]&255)==6&&
                (candidate[offset++]&255)==2;

            zeroRows=
                clearRender&&
                offset==candidate.length;

            require(
                clearRender&&zeroRows,
                "Event Activity clear/render empty projection"
            );

            for(Field field:
                    LocalEventActivityUiHandler
                        .class
                        .getDeclaredFields()){
                if(field.getType()==
                        EventActivityService.class)
                    eventActivityServiceCreated=true;

                if(field.getType()==
                        UsageQuotaService.class)
                    quotaServiceCreated=true;
            }

            require(
                !eventActivityServiceCreated&&
                !quotaServiceCreated,
                "empty Event Activity adapter created policy state"
            );

            System.out.println(
                "G139_EVENT_ACTIVITY_EMPTY_VIEW_PASS"+
                " customCommand="+customCommand+
                " root30072="+root30072+
                " subtype6="+subtype6+
                " clearRender="+clearRender+
                " zeroRows="+zeroRows+
                " maxRows7="+maxRows7+
                " rootReplacementLifecycle="+
                    rootReplacementLifecycle+
                " eventActivityServiceCreated="+
                    eventActivityServiceCreated+
                " quotaServiceCreated="+
                    quotaServiceCreated+
                " activityCatalogClaim=false"+
                " quotaPolicyClaim=false"+
                " tokenEconomicsClaim=false"+
                " rewardsClaim=false"+
                " persistenceClaim=false"+
                " originalNavigationClaim=false"
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

    private G139EventActivityEmptyViewIntegrationTest(){}
}
