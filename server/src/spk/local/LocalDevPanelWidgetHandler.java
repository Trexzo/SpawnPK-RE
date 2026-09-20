package spk.local;

import java.io.IOException;

/**
 * Clickable four-choice developer-panel action coordinator.
 *
 * Owns page navigation and page-specific developer actions. Session-owned
 * persistence, mutable scene-publisher assignment, tagged logging, rendering
 * and runtime dialog-key filesystem state remain explicit orchestration edges.
 */
final class LocalDevPanelWidgetHandler {
    @FunctionalInterface
    interface PromptCallback {
        void prompt(
            DevControlCenter.PendingAmount pending,
            ServerPacketWriter writer
        )throws IOException;
    }

    static final class Outcome {
        final int choice;
        final String resultText;
        final String directLogText;
        final boolean renderAfter;
        final String saveReason;
        final SceneUpdatePublisher scenePublisher;

        Outcome(
            int choice,
            String resultText,
            String directLogText,
            boolean renderAfter,
            String saveReason,
            SceneUpdatePublisher scenePublisher
        ){
            this.choice=choice;
            this.resultText=resultText;
            this.directLogText=directLogText;
            this.renderAfter=renderAfter;
            this.saveReason=saveReason;
            this.scenePublisher=scenePublisher;
        }

        static Outcome normal(
            int choice,
            String resultText,
            SceneUpdatePublisher scenePublisher
        ){
            return new Outcome(
                choice,
                resultText,
                null,
                true,
                null,
                scenePublisher);
        }

        static Outcome terminal(
            String directLogText,
            SceneUpdatePublisher scenePublisher
        ){
            return new Outcome(
                -1,
                null,
                directLogText,
                false,
                null,
                scenePublisher);
        }

        static Outcome prompt(
            int choice,
            SceneUpdatePublisher scenePublisher
        ){
            return new Outcome(
                choice,
                null,
                null,
                false,
                null,
                scenePublisher);
        }
    }

    private final DevControlCenter panel;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final DevAuthorityWorkbench dev;
    private final CombatEngine combat;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final VoidglassPetState voidglass;
    private final LocalVoidglassCommandHandler voidglassCommands;
    private final PrayerState prayers;
    private final MagicState magic;
    private final LocalRegionDevCommandHandler regionDevCommands;
    private final BankState bank;
    private final PlayerPresentationService playerPresentation;
    private final PlayerState playerState;
    private final LocalDevSessionCommandHandler devSessionCommands;
    private final LocalDevPanelRenderer renderer;
    private final PromptCallback promptCallback;
    private final Runnable clearDialogNumberKeys;

    LocalDevPanelWidgetHandler(
        DevControlCenter panel,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        DevAuthorityWorkbench dev,
        CombatEngine combat,
        NpcRegistry npcs,
        MovementState movement,
        VoidglassPetState voidglass,
        LocalVoidglassCommandHandler voidglassCommands,
        PrayerState prayers,
        MagicState magic,
        LocalRegionDevCommandHandler regionDevCommands,
        BankState bank,
        PlayerPresentationService playerPresentation,
        PlayerState playerState,
        LocalDevSessionCommandHandler devSessionCommands,
        LocalDevPanelRenderer renderer,
        PromptCallback promptCallback,
        Runnable clearDialogNumberKeys
    ){
        this.panel=java.util.Objects.requireNonNull(panel,"panel");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=java.util.Objects.requireNonNull(
            combatStyles,"combatStyles");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.voidglass=java.util.Objects.requireNonNull(voidglass,"voidglass");
        this.voidglassCommands=java.util.Objects.requireNonNull(
            voidglassCommands,"voidglassCommands");
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.regionDevCommands=java.util.Objects.requireNonNull(
            regionDevCommands,"regionDevCommands");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.playerPresentation=java.util.Objects.requireNonNull(
            playerPresentation,"playerPresentation");
        this.playerState=java.util.Objects.requireNonNull(
            playerState,"playerState");
        this.devSessionCommands=java.util.Objects.requireNonNull(
            devSessionCommands,"devSessionCommands");
        this.renderer=java.util.Objects.requireNonNull(renderer,"renderer");
        this.promptCallback=java.util.Objects.requireNonNull(
            promptCallback,"promptCallback");
        this.clearDialogNumberKeys=java.util.Objects.requireNonNull(
            clearDialogNumberKeys,"clearDialogNumberKeys");
    }

    Outcome handle(
        int widget,
        String username,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        if(!panel.isOpen())return null;

        if(widget==54195){
            writer.fixed(219,new byte[0]);
            panel.close();
            clearDialogNumberKeys.run();
            return Outcome.terminal(
                "V5171_DEV_PANEL_CLOSE widget=54195",
                currentScenePublisher);
        }

        int choice=widget-2482;
        if(choice<0||choice>3)return null;

        String result="";
        String saveReason=null;
        SceneUpdatePublisher nextScenePublisher=currentScenePublisher;

        switch(panel.page()){
            case MAIN:
                panel.setPage(
                    choice==0
                        ?DevControlCenter.Page.COMBAT
                        :choice==1
                            ?DevControlCenter.Page.PETS
                            :choice==2
                                ?DevControlCenter.Page.MAGIC_PRAYER
                                :DevControlCenter.Page.MORE);
                break;

            case MORE:
                panel.setPage(
                    choice==0
                        ?DevControlCenter.Page.WORLD
                        :choice==1
                            ?DevControlCenter.Page.ITEMS
                            :choice==2
                                ?DevControlCenter.Page.PLAYER_NPC
                                :DevControlCenter.Page.DIAG);
                break;

            case COMBAT:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.COMBAT_ANIM);
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.COMBAT_HIT);
                }else if(choice==2){
                    result=cycleCombatStyle(writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.COMBAT_MORE);
                }
                break;

            case COMBAT_MORE:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.COMBAT_RUNTIME);
                }else if(choice==1){
                    result=renderer.runtimeWeaponAuthoritySummary(
                        equipment.weapon());
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.MAIN);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.COMBAT);
                }
                break;

            case COMBAT_RUNTIME:
                if(choice==0){
                    V913WeaponRuntimeAuthority.Profile profile=
                        V913WeaponRuntimeAuthority.resolve(
                            equipment.weapon());

                    if(profile==null){
                        result=
                            "REJECTED equipped weapon has no V9.13 runtime profile: "+
                            equipment.weapon();
                    }else{
                        panel.selectRuntimeWeaponItemId(
                            equipment.weapon());
                        result=renderer.runtimeWeaponAuthoritySummary(
                            equipment.weapon());
                    }
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount
                            .RUNTIME_WEAPON_ITEM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    V913WeaponRuntimeAuthority.Profile profile=
                        renderer.selectedRuntimeWeaponProfile();
                    result=RuntimeWeaponPresentationLab.preview(
                        profile,
                        npcs,
                        movement,
                        currentScenePublisher,
                        writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.COMBAT_MORE);
                }
                break;

            case COMBAT_ANIM:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.COMBAT_ANIM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    dev.setCombatAnimationOverride(
                        equipment.weapon(),null);
                    result="combat animation reset to authority";
                }else if(choice==2){
                    result=playCurrentAttackAnimation(writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.COMBAT);
                }
                break;

            case COMBAT_HIT:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.HIT_DAMAGE,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.HIT_TYPE,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    result=combat.devHitCommand(
                        new String[]{
                            "devhit",
                            "variant",
                            "auto"
                        });
                }else{
                    panel.setPage(
                        DevControlCenter.Page.COMBAT);
                }
                break;

            case PETS:
                panel.setPage(
                    choice==0
                        ?DevControlCenter.Page.PET_FX
                        :choice==1
                            ?DevControlCenter.Page.PET_FOLLOW
                            :choice==2
                                ?DevControlCenter.Page.PET_PRESENT
                                :DevControlCenter.Page.MAIN);
                break;

            case PET_FX:
                if(choice==0){
                    Integer current=dev.petParticleSelector();
                    Integer next=
                        current==null
                            ?0
                            :(current>=255
                                ?null
                                :current+1);
                    result=npcs.devSetParticleSelector(
                        next,movement,writer);
                }else if(choice==1){
                    result=npcs.devSetParticleSelector(
                        null,movement,writer);
                }else if(choice==2){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PET_FX,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PETS);
                }
                break;

            case PET_FOLLOW:
                if(choice==0){
                    result=npcs.devFollowFreeze(
                        !npcs.followFrozen());
                }else if(choice==1){
                    result=npcs.devFollowStep(
                        movement,writer);
                }else if(choice==2){
                    result=npcs.devSnapToOwner(
                        movement,writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PETS);
                }
                break;

            case PET_PRESENT:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PET_ANIM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PET_GFX,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    int state=(npcs.petNativeState()+1)&3;
                    result=npcs.setPetNativeState(
                        state,writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PET_PRESENT_MORE);
                }
                break;

            case PET_PRESENT_MORE:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.CUSTOM_CONTENT);
                }else if(choice==1){
                    result=npcs.devInfo(movement);
                }else if(choice==2){
                    result="R1 reference: "+
                        voidglass.summary(
                            dev.petParticleSelector())+
                        " | R3 item29999 + candidate NPC12000..12003";
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PET_PRESENT);
                }
                break;

            case CUSTOM_CONTENT:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.CUSTOM_VOIDGLASS);
                }else if(choice==1){
                    result=VoidglassR3CustomContent.boundary();
                }else if(choice==2){
                    result=
                        "R1 prototype: item22960 Vasa -> npc3701; session-only overlay. Kept for regression/reference only.";
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PET_PRESENT_MORE);
                }
                break;

            case CUSTOM_VOIDGLASS:
                if(choice==0){
                    LocalVoidglassCommandHandler.Outcome give=
                        voidglassCommands.giveR3(writer);
                    saveReason=give.saveReason;
                    result=give.text;
                }else if(choice==1){
                    result=voidglassCommands.cycleR3Candidate(
                        writer);
                }else if(choice==2){
                    result=voidglassCommands.triggerR3Proc(
                        writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.CUSTOM_CONTENT);
                }
                break;

            case MAGIC_PRAYER:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.MAGIC);
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.PRAYER);
                }else if(choice==2){
                    result=prayers.deactivateAll(writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MAIN);
                }
                break;

            case MAGIC:
                if(choice==0){
                    result=magic.switchBook("modern",writer);
                }else if(choice==1){
                    result=magic.switchBook("ancient",writer);
                }else if(choice==2){
                    result=magic.switchBook("lunar",writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MAGIC_PRAYER);
                }
                break;

            case PRAYER:
                if(choice==0){
                    result=prayers.switchBook(
                        prayers.book()==
                            PrayerDefinitionRepository.Book.NORMAL
                                ?"curses"
                                :"normal",
                        writer);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PRAYER_WIDGET,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PRAYER_ICON,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MAGIC_PRAYER);
                }
                break;

            case WORLD:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.REGION_ID,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    LocalRegionDevCommandHandler.Result region=
                        regionDevCommands.returnHomeForPanel(
                            username,
                            currentScenePublisher,
                            writer);

                    if(region.scenePublisher!=null){
                        nextScenePublisher=
                            region.scenePublisher;
                    }
                    saveReason=region.saveReason;
                    result=region.detailText;
                }else if(choice==2){
                    int mask=WorldCollisionAuthority.maskAt(
                        movement.x(),
                        movement.y(),
                        movement.plane());

                    result="collision world="+
                        movement.x()+","+
                        movement.y()+","+
                        movement.plane()+
                        " mask="+mask+
                        " blocked="+
                        WorldCollisionAuthority.blockedTile(
                            movement.x(),
                            movement.y(),
                            movement.plane());
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MORE);
                }
                break;

            case ITEMS:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.ITEM_LIBRARY_ID,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    writer.fixed(219,new byte[0]);
                    panel.close();
                    clearDialogNumberKeys.run();
                    String ui=NativeEquipmentDeathUi
                        .openEquipmentStats(
                            writer,equipment);

                    return Outcome.terminal(
                        "V5171_DEV_PANEL action=EQUIPMENT_STATS result="+ui,
                        nextScenePublisher);
                }else if(choice==2){
                    writer.fixed(219,new byte[0]);
                    panel.close();
                    clearDialogNumberKeys.run();
                    String ui=NativeEquipmentDeathUi
                        .openDeathPreview(
                            writer,bank,equipment);

                    return Outcome.terminal(
                        "V5171_DEV_PANEL action=DEATH_PREVIEW result="+ui,
                        nextScenePublisher);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MORE);
                }
                break;

            case PLAYER_NPC:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.PLAYER);
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.NPC);
                }else if(choice==2){
                    result=ContentAuthorityRepository.summary()+
                        " | "+
                        ContentAuthorityRepository.itemSummary(
                            equipment.weapon());
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MORE);
                }
                break;

            case PLAYER:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PLAYER_MORPH,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    result=playerPresentation.clear(
                        username,
                        equipment,
                        playerState,
                        writer);
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.PLAYER_PRESENT);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PLAYER_NPC);
                }
                break;

            case PLAYER_PRESENT:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PLAYER_ANIM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.PLAYER_GFX,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    playerPresentation.refresh(
                        username,
                        equipment,
                        playerState,
                        writer);
                    result="player appearance refreshed";
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PLAYER);
                }
                break;

            case NPC:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.NPC_SPAWN,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    result=npcs.devRemoveAllNpcs(writer);
                }else if(choice==2){
                    result=npcs.devNpcList(20);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.PLAYER_NPC);
                }
                break;

            case DIAG:
                if(choice==0){
                    dev.trace().setEnabled(
                        !dev.trace().enabled());
                    result=dev.trace().summary();
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.AUTHORITY);
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.RESET_CONFIRM);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.MORE);
                }
                break;

            case AUTHORITY:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.AUTH_MAGIC);
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.AUTH_WORLD_ITEM);
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.DIAG);
                }
                break;

            case AUTH_MAGIC:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.AUTH_SPELL_WIDGET,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.AUTH_PRAYER_WIDGET,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    result="read-only authority: spells="+
                        SpellDefinitionRepository.count()+
                        " prayers="+
                        PrayerDefinitionRepository.count()+
                        "; server-owned costs/effects remain evidence-gated";
                }else{
                    panel.setPage(
                        DevControlCenter.Page.AUTHORITY);
                }
                break;

            case AUTH_WORLD_ITEM:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.AUTH_ITEM_ID,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.AUTH_REGION_ID,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    int regionId=
                        ((movement.x()>>6)<<8)|
                        (movement.y()>>6);
                    panel.selectRegionId(regionId);
                    result=renderer.regionAuthoritySummary(regionId);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.AUTHORITY);
                }
                break;

            case RESEARCH:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_EQUIP);
                }else if(choice==1){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_PET);
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_WORLD);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_DISCOVERY);
                }
                break;

            case RESEARCH_EQUIP:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.RESEARCH_EQUIP_ITEM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    int itemId=equipment.weapon();
                    panel.selectResearchEquipItemId(itemId);
                    result=EquipmentResearchAuthority.itemSummary(itemId);
                }else if(choice==2){
                    result="fields="+
                        java.util.Arrays.toString(
                            EquipmentResearchAuthority.BASE_PROFILE_FIELDS)+
                        " | "+
                        EquipmentResearchAuthority.QUERY_EQUIPSTR+
                        " | "+
                        EquipmentResearchAuthority.boundary();
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH);
                }
                break;

            case RESEARCH_PET:
                if(choice==0){
                    NpcEntity pet=npcs.pet();

                    if(pet==null){
                        result="REJECTED no active main pet";
                    }else{
                        PetProcResearchAuthority.Row row=
                            PetProcResearchAuthority.findForPetItem(
                                pet.petItemId);

                        if(row==null){
                            result="activePet item="+
                                pet.petItemId+
                                " has no actual Item+Pet R4 proc tuple";
                        }else{
                            panel.selectResearchPetRow(row.index);
                            result=PetProcResearchAuthority.summary(row);
                        }
                    }
                }else if(choice==1){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.RESEARCH_PET_ROW,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==2){
                    result=previewSelectedPetResearchCandidate(
                        writer);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH);
                }
                break;

            case RESEARCH_WORLD:
                if(choice==0){
                    promptCallback.prompt(
                        DevControlCenter.PendingAmount.RESEARCH_TELE_ITEM,
                        writer);
                    return Outcome.prompt(
                        choice,nextScenePublisher);
                }else if(choice==1){
                    int regionId=
                        ((movement.x()>>6)<<8)|
                        (movement.y()>>6);
                    panel.selectResearchTransitionRegion(regionId);
                    result=WorldTransitionResearchAuthority
                        .regionSummary(regionId)+
                        " | "+
                        WorldFullResearchAuthority
                            .regionSummary(regionId);
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_SERVICE);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH);
                }
                break;

            case RESEARCH_SERVICE:
                if(choice==0){
                    result=ServiceResearchAuthority.routerSummary();
                }else if(choice==1){
                    result=ServiceResearchAuthority.bloodSummary();
                }else if(choice==2){
                    result=ServiceResearchAuthority.shopSummary();
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_WORLD);
                }
                break;

            case RESEARCH_DISCOVERY:
                if(choice==0){
                    result=ClientDiscoveryAuthority
                        .taskAchievementSummary()+
                        " | "+
                        ClientDiscoveryAuthority.boundary();
                }else if(choice==1){
                    result=ClientDiscoveryAuthority
                        .magicConstructionSummary()+
                        " | "+
                        ClientDiscoveryAuthority.boundary();
                }else if(choice==2){
                    result=ClientDiscoveryAuthority.uiControlSummary()+
                        " | "+
                        ClientDiscoveryAuthority.minigameSummary()+
                        " | "+
                        ClientDiscoveryAuthority.boundary();
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_ASSET);
                }
                break;

            case RESEARCH_ASSET:
                if(choice==0){
                    panel.setPage(
                        DevControlCenter.Page.ALIGNMENT);
                }else if(choice==1){
                    result=AssetRuntimeResearchAuthority
                        .mayaSummary()+
                        " | "+
                        AssetRuntimeResearchAuthority
                            .objectRawSummary()+
                        " | "+
                        AssetRuntimeResearchAuthority
                            .updaterBoundary();
                }else if(choice==2){
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_PROTOCOL);
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_DISCOVERY);
                }
                break;

            case RESEARCH_PROTOCOL:
                if(choice==0){
                    result=ClientApplicationProtocolAuthority
                        .applicationSummary()+
                        " | "+
                        ClientApplicationProtocolAuthority.boundary();
                }else if(choice==1){
                    result=ClientApplicationProtocolAuthority
                        .controlSummary()+
                        " | "+
                        ClientApplicationProtocolAuthority.boundary();
                }else if(choice==2){
                    result=ClientApplicationProtocolAuthority
                        .interactionSummary()+
                        " | "+
                        ClientApplicationProtocolAuthority.boundary();
                }else{
                    panel.setPage(
                        DevControlCenter.Page.RESEARCH_ASSET);
                }
                break;

            case ALIGNMENT:
                if(choice==0){
                    result="client="+
                        ClientAssetAlignmentAuthority.CLIENT_SHA256+
                        " assets="+
                        ClientAssetAlignmentAuthority.SPAWNPK_ASSET_BUNDLE_SHA256+
                        " authority="+
                        ClientAssetAlignmentAuthority.SPAWNPK_AUTHORITY_SHA256;
                }else if(choice==1){
                    result=ContentAuthorityRepository.summary()+
                        " collisionRegions="+
                        WorldCollisionAuthority.regionCount()+
                        " worldPlacements="+
                        ClientAssetAlignmentAuthority.STATIC_WORLD_PLACEMENTS;
                }else if(choice==2){
                    result=root328AlignmentSummary();
                }else{
                    panel.setPage(
                        DevControlCenter.Page.AUTHORITY);
                }
                break;

            case RESET_CONFIRM:
                if(choice==0){
                    result=devSessionCommands.resetForPanel(
                        username,currentScenePublisher,writer);
                    panel.setPage(
                        DevControlCenter.Page.DIAG);
                }else if(choice==1||choice==3){
                    panel.setPage(
                        DevControlCenter.Page.DIAG);
                }else{
                    writer.fixed(219,new byte[0]);
                    panel.close();
                    clearDialogNumberKeys.run();

                    return Outcome.terminal(
                        "V5171_DEV_PANEL_CLOSE reason=RESET_PAGE_CLOSE",
                        nextScenePublisher);
                }
                break;

            default:
                panel.setPage(
                    DevControlCenter.Page.MAIN);
                break;
        }

        Outcome outcome=Outcome.normal(
            choice,result,nextScenePublisher);

        if(saveReason==null)return outcome;

        return new Outcome(
            outcome.choice,
            outcome.resultText,
            outcome.directLogText,
            outcome.renderAfter,
            saveReason,
            outcome.scenePublisher);
    }

    private String cycleCombatStyle(
        ServerPacketWriter writer
    )throws IOException{
        int root=
            CombatInterfaceRepository.forWeapon(
                equipment.weapon());
        int current=combatStyles.value();

        for(int i=1;i<=4;i++){
            int value=(current+i)&3;
            CombatStyleRepository.Style style=
                CombatStyleRepository.byValue(
                    root,value);

            if(style!=null){
                return combatStyles.click(
                    root,style.widget,writer);
            }
        }

        return "NO_ALTERNATE_STYLE root="+root;
    }

    private String playCurrentAttackAnimation(
        ServerPacketWriter writer
    )throws IOException{
        int weapon=equipment.weapon();
        Integer animation=null;
        String authority="";

        if(dev.hasCombatAnimationOverride(weapon)){
            Integer override=
                dev.combatAnimationOverride(weapon);

            if(override!=null&&override>=0){
                animation=override;
                authority="TEMPORARY_OVERRIDE";
            }
        }

        if(animation==null){
            V913WeaponRuntimeAuthority.Profile profile=
                V913WeaponRuntimeAuthority.resolve(weapon);

            if(profile!=null&&profile.attackAnimation>=0){
                animation=profile.attackAnimation;
                authority="PRODUCTION_RUNTIME_V913";
            }
        }

        if(animation==null){
            WeaponAttackAuthorityRepository.Row row=
                WeaponAttackAuthorityRepository.resolve(weapon);

            if(row!=null&&row.attackAnimation>=0){
                animation=row.attackAnimation;
                authority=row.attackAuthority;
            }
        }

        if(animation==null){
            return "NO_RESOLVED_ATTACK_ANIMATION weapon="+weapon;
        }

        writer.varShort(
            81,
            CombatSync.player81AnimationOnly(animation));

        return "PLAY_ATTACK_ANIMATION weapon="+
            weapon+
            " anim="+animation+
            " authority="+authority;
    }

    private String previewSelectedPetResearchCandidate(
        ServerPacketWriter writer
    )throws IOException{
        int index=panel.selectedResearchPetRow();
        PetProcResearchAuthority.Row row=
            index>0
                ?PetProcResearchAuthority.byIndex(index)
                :null;

        if(row==null){
            NpcEntity pet=npcs.pet();
            if(pet!=null){
                row=PetProcResearchAuthority.findForPetItem(
                    pet.petItemId);
            }
        }

        if(row==null){
            return "REJECTED no pet research row selected / active pet not mapped";
        }

        NpcEntity pet=npcs.pet();
        if(pet==null){
            return "REJECTED no active main pet";
        }

        if(!row.previewable()){
            return "REJECTED row has no previewable animation/GFX; "+
                row.confidence+
                " binding="+row.bindingStatus+
                " runtimeNeeded="+row.minimalRuntimeNeeded;
        }

        int animation=row.firstAnimation();
        int gfx=row.firstGfx();
        String animationResult="NONE";
        String gfxResult="NONE";

        if(animation>=0){
            animationResult=npcs.animatePet(
                animation,0,writer);
        }

        if(gfx>=0){
            gfxResult=npcs.gfxPet(
                gfx,0,0,writer);
        }

        return "OK presentationOnly=true row="+row.index+
            " family="+row.family+
            " confidence="+row.confidence+
            " binding="+row.bindingStatus+
            " anim="+animation+
            " gfx="+gfx+
            " animResult={"+animationResult+"}"+
            " gfxResult={"+gfxResult+"}"+
            " mechanics=NONE projectile=NONE impact=NONE; candidate preview does not promote production binding";
    }

    private String root328AlignmentSummary(){
        CombatStyleRepository.Style first=
            CombatStyleRepository.byValue(328,0);
        CombatStyleRepository.Style second=
            CombatStyleRepository.byValue(328,1);
        CombatStyleRepository.Style third=
            CombatStyleRepository.byValue(328,2);

        return "root328="+
            (ClientAssetAlignmentAuthority.root328Aligned()
                ?"PASS"
                :"FAIL")+
            " styles="+
            (first==null?"?":first.label)+
            "/"+
            (second==null?"?":second.label)+
            "/"+
            (third==null?"?":third.label)+
            " totalRoots="+
            CombatStyleRepository.rootCount()+
            " totalStyles="+
            CombatStyleRepository.countStyles();
    }
}
