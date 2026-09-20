package spk.local;

import java.io.IOException;

/**
 * Native numeric-prompt action coordinator for the LocalLab developer panel.
 *
 * Owns the PendingAmount action switch. Session-owned persistence, mutable
 * SceneUpdatePublisher assignment, tagged logging and final reopen/render
 * orchestration remain outside this class.
 */
final class LocalDevPanelAmountHandler {
    static final class Outcome {
        final DevControlCenter.PendingAmount pending;
        final String resultText;
        final boolean reopen;
        final String saveReason;
        final SceneUpdatePublisher scenePublisher;

        Outcome(
            DevControlCenter.PendingAmount pending,
            String resultText,
            boolean reopen,
            String saveReason,
            SceneUpdatePublisher scenePublisher
        ){
            this.pending=pending;
            this.resultText=resultText;
            this.reopen=reopen;
            this.saveReason=saveReason;
            this.scenePublisher=scenePublisher;
        }
    }

    private final DevControlCenter panel;
    private final DevAuthorityWorkbench dev;
    private final EquipmentState equipment;
    private final CombatEngine combat;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final LocalRegionDevCommandHandler regionDevCommands;
    private final NativeItemLibraryService itemLibrary;
    private final PlayerPresentationService playerPresentation;
    private final PlayerState playerState;
    private final PrayerState prayers;
    private final LocalDevPanelRenderer renderer;
    private final Runnable clearDialogNumberKeys;

    LocalDevPanelAmountHandler(
        DevControlCenter panel,
        DevAuthorityWorkbench dev,
        EquipmentState equipment,
        CombatEngine combat,
        NpcRegistry npcs,
        MovementState movement,
        LocalRegionDevCommandHandler regionDevCommands,
        NativeItemLibraryService itemLibrary,
        PlayerPresentationService playerPresentation,
        PlayerState playerState,
        PrayerState prayers,
        LocalDevPanelRenderer renderer,
        Runnable clearDialogNumberKeys
    ){
        this.panel=java.util.Objects.requireNonNull(panel,"panel");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.regionDevCommands=java.util.Objects.requireNonNull(
            regionDevCommands,"regionDevCommands");
        this.itemLibrary=java.util.Objects.requireNonNull(
            itemLibrary,"itemLibrary");
        this.playerPresentation=java.util.Objects.requireNonNull(
            playerPresentation,"playerPresentation");
        this.playerState=java.util.Objects.requireNonNull(
            playerState,"playerState");
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.renderer=java.util.Objects.requireNonNull(renderer,"renderer");
        this.clearDialogNumberKeys=java.util.Objects.requireNonNull(
            clearDialogNumberKeys,"clearDialogNumberKeys");
    }

    Outcome handle(
        int value,
        String username,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        DevControlCenter.PendingAmount pending=panel.pending();
        boolean reopen=true;
        String result;
        String saveReason=null;
        SceneUpdatePublisher nextScenePublisher=currentScenePublisher;

        try{
            switch(pending){
                case COMBAT_ANIM:
                    if(value<0||value>65535){
                        result="REJECTED animation 0..65535";
                    }else{
                        dev.setCombatAnimationOverride(
                            equipment.weapon(),value);
                        result="weapon="+equipment.weapon()+
                            " animationOverride="+value;
                    }
                    break;

                case HIT_DAMAGE:
                    if(value<0||value>255){
                        result="REJECTED damage 0..255";
                    }else{
                        result=combat.devHitCommand(
                            new String[]{
                                "devhit",
                                "damage",
                                String.valueOf(value)
                            });
                    }
                    break;

                case HIT_TYPE:
                    if(value<0||value>255){
                        result="REJECTED type 0..255";
                    }else{
                        result=combat.devHitCommand(
                            new String[]{
                                "devhit",
                                "type",
                                String.valueOf(value)
                            });
                    }
                    break;

                case PET_FX:
                    if(value<0||value>255){
                        result="REJECTED selector 0..255";
                    }else{
                        result=npcs.devSetParticleSelector(
                            value,movement,writer);
                    }
                    break;

                case PET_ANIM:
                    if(value<0||value>65535){
                        result="REJECTED animation 0..65535";
                    }else{
                        result=npcs.animatePet(
                            value,0,writer);
                    }
                    break;

                case PET_GFX:
                    if(value<0||value>65535){
                        result="REJECTED gfx 0..65535";
                    }else{
                        result=npcs.gfxPet(
                            value,0,0,writer);
                    }
                    break;

                case PET_NATIVE_STATE:
                    if(value<0||value>3){
                        result="REJECTED state 0..3";
                    }else{
                        result=npcs.setPetNativeState(
                            value,writer);
                    }
                    break;

                case REGION_ID:{
                    LocalRegionDevCommandHandler.Result region=
                        regionDevCommands.enterForPanel(
                            value,
                            0,
                            currentScenePublisher,
                            writer);

                    if(region.scenePublisher!=null){
                        nextScenePublisher=region.scenePublisher;
                    }
                    saveReason=region.saveReason;
                    result=region.detailText;
                    reopen=false;
                    break;
                }

                case ITEM_LIBRARY_ID:
                    if(ItemAuthorityRepository.get(value)==null){
                        result="REJECTED unknown item "+value;
                    }else{
                        panel.close();
                        clearDialogNumberKeys.run();
                        result=itemLibrary.open(writer,value);
                        reopen=false;
                    }
                    break;

                case PLAYER_MORPH:
                    if(value<0||value>16383){
                        result="REJECTED npc 0..16383";
                    }else{
                        result=playerPresentation.morph(
                            value,
                            username,
                            equipment,
                            playerState,
                            writer);
                    }
                    break;

                case PLAYER_ANIM:
                    if(value<0||value>65535){
                        result="REJECTED animation 0..65535";
                    }else{
                        writer.varShort(
                            81,
                            CombatSync.player81AnimationOnly(value));
                        result="player anim="+value;
                    }
                    break;

                case PLAYER_GFX:
                    if(value<0||value>65535){
                        result="REJECTED gfx 0..65535";
                    }else{
                        writer.varShort(
                            81,
                            CombatSync.player81GfxOnly(
                                value,0,0));
                        result="player gfx="+value;
                    }
                    break;

                case NPC_SPAWN:
                    if(value<0||value>16383){
                        result="REJECTED npc 0..16383";
                    }else{
                        result=npcs.devSpawnNpc(
                            value,1,0,movement,writer);
                    }
                    break;

                case PRAYER_WIDGET:{
                    PrayerDefinitionRepository.Def definition=
                        PrayerDefinitionRepository.byWidget(value);

                    if(definition==null){
                        result="REJECTED unknown prayer widget "+value;
                    }else if(definition.book!=prayers.book()){
                        result="REJECTED prayer belongs to "+
                            definition.book+
                            " current="+prayers.book()+
                            " name="+definition.name;
                    }else{
                        result=prayers.click(
                            definition,playerState,writer);
                    }
                    break;
                }

                case PRAYER_ICON:
                    result=prayers.publishManualHeadIcon(
                        value,writer);
                    break;

                case AUTH_SPELL_WIDGET:{
                    SpellDefinitionRepository.Spell definition=
                        SpellDefinitionRepository.byWidget(value);

                    if(definition==null){
                        result="REJECTED unknown spell widget "+value;
                    }else{
                        panel.selectSpellWidget(value);
                        result=renderer.spellAuthoritySummary(value);
                    }
                    break;
                }

                case AUTH_PRAYER_WIDGET:{
                    PrayerDefinitionRepository.Def definition=
                        PrayerDefinitionRepository.byWidget(value);

                    if(definition==null){
                        result="REJECTED unknown prayer widget "+value;
                    }else{
                        panel.selectPrayerWidget(value);
                        result=renderer.prayerAuthoritySummary(value);
                    }
                    break;
                }

                case AUTH_ITEM_ID:
                    if(ItemAuthorityRepository.get(value)==null){
                        result="REJECTED unknown item "+value;
                    }else{
                        panel.selectItemId(value);
                        result=renderer.itemAuthorityBrowserSummary(value);
                    }
                    break;

                case AUTH_REGION_ID:
                    if(WorldRegionAuthorityRepository.get(value)==null){
                        result="REJECTED unknown region "+value;
                    }else{
                        panel.selectRegionId(value);
                        result=renderer.regionAuthoritySummary(value);
                    }
                    break;

                case RUNTIME_WEAPON_ITEM:{
                    V913WeaponRuntimeAuthority.Profile profile=
                        V913WeaponRuntimeAuthority.resolve(value);

                    if(profile==null){
                        result="REJECTED item has no V9.13 runtime weapon profile: "+
                            value;
                    }else{
                        panel.selectRuntimeWeaponItemId(value);
                        result=renderer.runtimeWeaponAuthoritySummary(value);
                    }
                    break;
                }

                case RESEARCH_EQUIP_ITEM:
                    if(ItemAuthorityRepository.get(value)==null){
                        result="REJECTED unknown item "+value;
                    }else{
                        panel.selectResearchEquipItemId(value);
                        result=EquipmentResearchAuthority.itemSummary(value);
                    }
                    break;

                case RESEARCH_PET_ROW:{
                    PetProcResearchAuthority.Row row=
                        PetProcResearchAuthority.byIndex(value);

                    if(row==null){
                        result="REJECTED pet research row must be 1.."+
                            PetProcResearchAuthority.count();
                    }else{
                        panel.selectResearchPetRow(value);
                        result=PetProcResearchAuthority.summary(row);
                    }
                    break;
                }

                case RESEARCH_TELE_ITEM:{
                    WorldTransitionResearchAuthority.TeleportItem teleport=
                        WorldTransitionResearchAuthority.teleport(value);

                    if(teleport==null){
                        result="REJECTED item has no static teleport-candidate row: "+
                            value;
                    }else{
                        panel.selectResearchTeleportItemId(value);
                        result=WorldTransitionResearchAuthority
                            .teleportSummary(value);
                    }
                    break;
                }

                case RESEARCH_TRANSITION_REGION:
                    if(WorldRegionAuthorityRepository.get(value)==null){
                        result="REJECTED unknown region "+value;
                    }else{
                        panel.selectResearchTransitionRegion(value);
                        result=WorldTransitionResearchAuthority
                            .regionSummary(value);
                    }
                    break;

                default:
                    result="IGNORED no pending developer amount";
                    break;
            }
        }catch(IllegalArgumentException exception){
            result="REJECTED "+exception.getMessage();
        }

        return new Outcome(
            pending,
            result,
            reopen,
            saveReason,
            nextScenePublisher
        );
    }
}
