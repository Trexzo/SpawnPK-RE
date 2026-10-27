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
    private World canonicalWorld;
    private java.util.function.LongSupplier worldGeneration;

    /**
     * Live-session widget binding. World membership is fenced under its
     * lifecycle barrier for prayer and non-targeted Magic activation.
     * Cross-JVM account leases and all other UI families remain separate.
     */
    LocalGameplayWidgetHandler(
        World world,
        WorldPlayer owner,
        java.util.function.LongSupplier generation
    ){
        this(owner);
        this.canonicalWorld=java.util.Objects.requireNonNull(
            world,"world");
        this.worldGeneration=java.util.Objects.requireNonNull(
            generation,"generation");
    }

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
        // The live Source LocalSession must not accept a prayer or direct
        // spell click after its exact World registration has gone stale.
        // World lifecycle ownership stays held through the semantic action.
        // Other widgets keep their previously defined independent routing.
        SpellDefinitionRepository.Spell direct=
            SpellDefinitionRepository.byWidget(widget);
        boolean sensitive=
            PrayerDefinitionRepository.byWidget(widget)!=null||
            (direct!=null&&!direct.targeted);
        if(canonicalWorld!=null&&sensitive){
            final String[] answer=new String[1];
            boolean owned=
                canonicalWorld.withOpenPlayerOwnershipIfCurrent(
                    lifecycleOwner,
                    worldGeneration.getAsLong(),
                    ()->{
                        synchronized(lifecycleOwner.mutationLock()){
                            answer[0]=handleCurrent(
                                widget,serverPackets);
                        }
                    }
                );
            if(!owned)
                return "V510_WORLD_WIDGET widget="+widget+
                    " result=REJECTED_STALE_WORLD_OWNER"+
                    " prayerAndMagicUnchanged=true"+
                    " homeTeleportToken=false";
            return answer[0];
        }
        return handleCurrent(widget,serverPackets);
    }

    private String handleCurrent(
        int widget,ServerPacketWriter serverPackets
    )throws IOException{
        PrayerDefinitionRepository.Def prayer=
            PrayerDefinitionRepository.byWidget(widget);
        if(prayer!=null){
            // Both Normal Prayer and Curses use this widget route. Keep the
            // same canonical lifecycle admission as direct magic; reject
            // before PrayerState.click can toggle activation or emit a varp.
            // Does not infer original death-time auto-deactivation rules.
            if(lifecycleOwner!=null){
                synchronized(lifecycleOwner.mutationLock()){
                    if(lifecycleOwner.lifecycle().dead()||
                       playerState.currentLevel(PlayerState.HITPOINTS)<=0){
                        return "V510_PRAYER_WIDGET widget="+widget+
                            " result=REJECTED_PLAYER_DEAD"+
                            " authority=CUSTOM_LOCALLAB_LIFECYCLE"+
                            " configStatePreserved=true";
                    }
                }
            }
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
        if(canonicalWorld!=null&&
           (canonicalWorld.closed()||
            !canonicalWorld.players().owns(
                lifecycleOwner,worldGeneration.getAsLong()))){
            acceptedHomeTeleport=false;
            return false;
        }
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
