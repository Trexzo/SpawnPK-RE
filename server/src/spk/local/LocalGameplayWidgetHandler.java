package spk.local;

import java.io.IOException;

/**
 * Typed gameplay widget router for prayer, combat-style and direct-spell clicks.
 *
 * Packet/widget decoding stays in ClientPacketProbe. This handler preserves the
 * current routing priority and delegates mechanics to the existing domain state.
 */
final class LocalGameplayWidgetHandler {
    private final PrayerState prayers;
    private final PlayerState playerState;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final MagicState magic;
    private final BankState bank;
    private boolean acceptedHomeTeleport;

    LocalGameplayWidgetHandler(
        PrayerState prayers,
        PlayerState playerState,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        MagicState magic,
        BankState bank
    ){
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=java.util.Objects.requireNonNull(combatStyles,"combatStyles");
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
    }

    String handle(int widget,ServerPacketWriter serverPackets)throws IOException{
        acceptedHomeTeleport=false;
        PrayerDefinitionRepository.Def prayer=
            PrayerDefinitionRepository.byWidget(widget);
        if(prayer!=null){
            String result=prayers.click(prayer,playerState,serverPackets);
            return "V510_PRAYER_WIDGET widget="+widget+
                " result="+result+
                " state={"+prayers.summary()+"}";
        }

        int combatRoot=CombatInterfaceRepository.forWeapon(equipment.weapon());
        CombatStyleRepository.Style style=
            CombatStyleRepository.byWidget(combatRoot,widget);
        if(style!=null){
            String result=combatStyles.click(
                combatRoot,widget,serverPackets);
            return "V510_COMBAT_STYLE widget="+widget+
                " weapon="+equipment.weapon()+
                " result="+result;
        }

        MagicState.Check directSpell=
            magic.direct(widget,bank,equipment,playerState);
        if(directSpell.handled){
            if(directSpell.accepted&&
               directSpell.spell!=null&&
               isHomeTeleportWidget(widget))
                acceptedHomeTeleport=true;

            return "V510_MAGIC_DIRECT widget="+widget+
                " result="+directSpell.message+
                " state={"+magic.summary()+"}";
        }

        return null;
    }

    boolean consumeAcceptedHomeTeleport(){
        boolean value=acceptedHomeTeleport;
        acceptedHomeTeleport=false;
        return value;
    }

    static boolean isHomeTeleportWidget(int widget){
        return widget==1195||
               widget==12856||
               widget==30000;
    }

}
