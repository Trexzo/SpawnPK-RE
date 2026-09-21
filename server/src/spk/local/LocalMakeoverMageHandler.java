package spk.local;

import java.io.IOException;

/**
 * Session-local Make-over Mage dialogue + character designer coordinator.
 *
 * Trigger/UI/protocol authority:
 * - NPC 599 / name: exact current cache
 * - dialogue/interface roots/widgets: exact current v308 interface cache
 * - C2S40 / C2S101 schemas: exact current v308 client
 * - wording/normal branch: historical SpawnPK screenshot evidence
 *
 * This remains an additive LocalLab coordinator until dialogue-tree publication
 * is exposed through the language-neutral content API (#7 migration boundary).
 */
final class LocalMakeoverMageHandler {
    static final int NPC_ID=599;

    static final int INTRO_ROOT=4882;
    static final int INTRO_HEAD_WIDGET=4883;
    static final int INTRO_NAME_WIDGET=4884;
    static final int INTRO_TEXT_WIDGET=4885;
    static final int INTRO_CONTINUE_WIDGET=4886;
    static final int KEYBOARD_CONTINUE_WIDGET=4907;

    static final int OPTIONS_ROOT=2459;
    static final int OPTIONS_TITLE_WIDGET=2460;
    static final int CHANGE_LOOK_WIDGET=2461;
    static final int NEVERMIND_WIDGET=2462;

    static final int DESIGN_ROOT=3559;

    private enum Stage { NONE, INTRO, OPTIONS, DESIGN }

    private final WorldPlayer worldPlayer;
    private final EquipmentState equipment;
    private Stage stage=Stage.NONE;

    LocalMakeoverMageHandler(
        WorldPlayer worldPlayer,
        EquipmentState equipment
    ){
        this.worldPlayer=java.util.Objects.requireNonNull(
            worldPlayer,
            "worldPlayer"
        );
        this.equipment=java.util.Objects.requireNonNull(
            equipment,
            "equipment"
        );
    }

    boolean beginIfSupported(
        NpcAction action,
        NpcEntity clicked,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(action==null||clicked==null)return false;
        if(clicked.definitionId!=NPC_ID)return false;

        NpcInteractionRouter.Route route=
            NpcInteractionRouter.resolve(
                action,
                clicked
            );

        if(route.option!=1||
           route.service!=NpcInteractionRouter.Service.TALK)
            return false;

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                INTRO_NAME_WIDGET,
                "Make-over Mage"
            )
        );
        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                INTRO_TEXT_WIDGET,
                "How may I help you?"
            )
        );
        packets.fixed(
            75,
            BootstrapPackets.interfaceNpcHead75(
                NPC_ID,
                INTRO_HEAD_WIDGET
            )
        );
        packets.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                INTRO_ROOT
            )
        );

        stage=Stage.INTRO;

        System.out.println(
            tag+
            "MAKEOVER_MAGE_DIALOG_OPEN npc=599"+
            " root="+INTRO_ROOT+
            " headWidget="+INTRO_HEAD_WIDGET+
            " authority=EXACT_CURRENT_CLIENT_UI+HISTORICAL_SCREENSHOT"
        );
        return true;
    }

    boolean handleContinue(
        int widget,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(stage!=Stage.INTRO||
           (widget!=INTRO_CONTINUE_WIDGET&&
            widget!=KEYBOARD_CONTINUE_WIDGET))
            return false;

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                OPTIONS_TITLE_WIDGET,
                "Select an Option"
            )
        );
        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                CHANGE_LOOK_WIDGET,
                "I'd like to change my look."
            )
        );
        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                NEVERMIND_WIDGET,
                "Nevermind."
            )
        );
        packets.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                OPTIONS_ROOT
            )
        );

        stage=Stage.OPTIONS;

        System.out.println(
            tag+
            "MAKEOVER_MAGE_OPTIONS_OPEN root="+
            OPTIONS_ROOT+
            " changeWidget="+CHANGE_LOOK_WIDGET+
            " nevermindWidget="+NEVERMIND_WIDGET
        );
        return true;
    }

    boolean handleWidget(
        int widget,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(stage!=Stage.OPTIONS)return false;

        if(widget==CHANGE_LOOK_WIDGET){
            packets.fixed(
                219,
                new byte[0]
            );
            packets.fixed(
                97,
                BootstrapPackets.interface97(
                    DESIGN_ROOT
                )
            );
            stage=Stage.DESIGN;

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DESIGN_OPEN root="+
                DESIGN_ROOT+
                " authority=EXACT_CURRENT_CLIENT_UI"
            );
            return true;
        }

        if(widget==NEVERMIND_WIDGET){
            packets.fixed(
                219,
                new byte[0]
            );
            stage=Stage.NONE;

            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_CANCEL reason=NEVERMIND"
            );
            return true;
        }

        return false;
    }

    Result handleDesign(
        CharacterDesignRequest request,
        String username,
        ServerPacketWriter packets,
        String tag
    )throws IOException{
        if(request==null)return Result.notHandled();

        if(stage!=Stage.DESIGN){
            return Result.handled(
                null,
                "MAKEOVER_MAGE_DESIGN_REJECTED reason=NO_ACTIVE_DESIGN request="+
                    request
            );
        }

        if(!request.valid()){
            packets.fixed(
                219,
                new byte[0]
            );
            stage=Stage.NONE;
            return Result.handled(
                null,
                "MAKEOVER_MAGE_DESIGN_REJECTED reason=INVALID_EXACT_PROFILE request="+
                    request
            );
        }

        PlayerState player=
            worldPlayer.playerState();

        if(!player.setCharacterAppearance(
                request.gender(),
                request.kits(),
                request.colours()))
            throw new IllegalStateException(
                "validated character design was rejected"
            );

        packets.fixed(
            219,
            new byte[0]
        );
        packets.varShort(
            81,
            BootstrapPackets.player81AppearanceOnly(
                username,
                equipment.appearanceItems(),
                player
            )
        );

        stage=Stage.NONE;

        return Result.handled(
            "CHARACTER_DESIGN",
            "MAKEOVER_MAGE_DESIGN_APPLIED gender="+
                request.gender()+
                " kits="+
                java.util.Arrays.toString(
                    request.kits()
                )+
                " colours="+
                java.util.Arrays.toString(
                    request.colours()
                )+
                " packet81Refresh=true"
        );
    }

    boolean cancel(){
        boolean active=stage!=Stage.NONE;
        stage=Stage.NONE;
        return active;
    }

    boolean active(){
        return stage!=Stage.NONE;
    }

    static final class Result {
        final boolean handled;
        final String saveReason;
        final String logText;

        private Result(
            boolean handled,
            String saveReason,
            String logText
        ){
            this.handled=handled;
            this.saveReason=saveReason;
            this.logText=logText;
        }

        static Result notHandled(){
            return new Result(
                false,
                null,
                null
            );
        }

        static Result handled(
            String saveReason,
            String logText
        ){
            return new Result(
                true,
                saveReason,
                logText
            );
        }
    }
}
