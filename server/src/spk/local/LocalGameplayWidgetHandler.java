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
    private final WorldPlayer lifecycleOwner;

    /** Production route: binds all state to the canonical session owner. */
    LocalGameplayWidgetHandler(WorldPlayer owner){
        this(
            java.util.Objects.requireNonNull(owner,"owner"),
            owner.prayers(),owner.playerState(),owner.equipment(),
            owner.combatStyles(),owner.magic(),owner.bank()
        );
    }

    LocalGameplayWidgetHandler(
        PrayerState prayers,
        PlayerState playerState,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        MagicState magic,
        BankState bank
    ){
        this(null,prayers,playerState,equipment,combatStyles,magic,bank);
    }

    private LocalGameplayWidgetHandler(
        WorldPlayer owner,
        PrayerState prayers,
        PlayerState playerState,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        MagicState magic,
        BankState bank
    ){
        this.lifecycleOwner=owner;
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

        // Only an actual direct spell is lifecycle-gated here. Prayer,
        // combat style and unrelated UI widgets retain their existing
        // routing semantics. Reset the home-teleport token above first,
        // so a rejected click cannot replay a previous accepted teleport.
        SpellDefinitionRepository.Spell requested=
            SpellDefinitionRepository.byWidget(widget);
        if(lifecycleOwner!=null&&
           requested!=null&&!requested.targeted){
            synchronized(lifecycleOwner.mutationLock()){
                if(lifecycleOwner.lifecycle().dead()||
                   playerState.currentLevel(PlayerState.HITPOINTS)<=0){
                    return "V510_MAGIC_DIRECT widget="+widget+
                        " result=REJECTED_CASTER_DEAD"+
                        " authority=CUSTOM_LOCALLAB_LIFECYCLE"+
                        " noTeleportOrMagicMutation=true";
                }
            }
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
