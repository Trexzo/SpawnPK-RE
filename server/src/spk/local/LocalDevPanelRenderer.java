package spk.local;

import java.io.IOException;

/**
 * Read-only presentation projection for the LocalLab developer control center.
 *
 * This class composes the exact current panel labels and publishes the classic
 * four-choice chatbox. It owns no panel actions, persistence, filesystem state
 * or gameplay mutation.
 */
final class LocalDevPanelRenderer {
    private final DevControlCenter panel;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final DevAuthorityWorkbench dev;
    private final CombatEngine combat;
    private final NpcRegistry npcs;
    private final PetState petState;
    private final MagicState magic;
    private final PrayerState prayers;
    private final MovementState movement;
    private final PlayerPresentationService playerPresentation;

    LocalDevPanelRenderer(
        DevControlCenter panel,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        DevAuthorityWorkbench dev,
        CombatEngine combat,
        NpcRegistry npcs,
        PetState petState,
        MagicState magic,
        PrayerState prayers,
        MovementState movement,
        PlayerPresentationService playerPresentation
    ){
        this.panel=java.util.Objects.requireNonNull(panel,"panel");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=java.util.Objects.requireNonNull(combatStyles,"combatStyles");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.prayers=java.util.Objects.requireNonNull(prayers,"prayers");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.playerPresentation=java.util.Objects.requireNonNull(
            playerPresentation,"playerPresentation");
    }

    /**
     * @return true when an open panel was rendered; false when the panel is closed.
     */
    boolean render(ServerPacketWriter writer)throws IOException{
        return render(
            writer,
            panel.snapshot()
        );
    }

    boolean render(
        ServerPacketWriter writer,
        DevControlCenter.StateSnapshot state
    )throws IOException{
        if(state==null)
            throw new NullPointerException("state");
        if(!state.open)
            return false;

        String title="LocalLab Dev Control Center";
        String[] options={"","","",""};

        switch(state.page){
            case MAIN:
                title="LocalLab Dev Control Center | "+BuildInfo.VERSION;
                options[0]="Combat & weapons";
                options[1]="Pets";
                options[2]="Magic & prayer";
                options[3]="More systems...";
                break;

            case MORE:
                title="More systems";
                options[0]="World & collision";
                options[1]="Items & native UI";
                options[2]="Player / NPC lab";
                options[3]="Diagnostics";
                break;

            case COMBAT:{
                int weapon=equipment.weapon();
                CombatStyleRepository.Style style=
                    combatStyles.current(
                        CombatInterfaceRepository.forWeapon(weapon));
                title="Combat | "+weapon+" "+devItemName(weapon);
                options[0]="Attack animation...";
                options[1]="Hitsplat lab...";
                options[2]="Cycle style ["+
                    (style==null?"?":style.label)+"]";
                options[3]="More combat...";
                break;
            }

            case COMBAT_MORE:{
                int weapon=equipment.weapon();
                V913WeaponRuntimeAuthority.Profile runtime=
                    V913WeaponRuntimeAuthority.resolve(weapon);
                title="Combat systems | runtime="+
                    (runtime==null?"none":weapon);
                options[0]="Runtime weapon lab...";
                options[1]="Current authority summary";
                options[2]="Back to main";
                options[3]="Back to combat";
                break;
            }

            case COMBAT_RUNTIME:{
                V913WeaponRuntimeAuthority.Profile runtime=
                    selectedRuntimeWeaponProfile();
                title="Runtime weapon lab | "+
                    (runtime==null
                        ?"none"
                        :runtime.itemId+" "+runtime.name);
                options[0]="Use equipped weapon";
                options[1]="Browse runtime item ID...";
                options[2]="Preview safe presentation";
                options[3]="Back";
                break;
            }

            case COMBAT_ANIM:{
                int weapon=equipment.weapon();
                String override=
                    dev.hasCombatAnimationOverride(weapon)
                        ?String.valueOf(
                            dev.combatAnimationOverride(weapon))
                        :"AUTO";
                title="Attack animation | weapon "+
                    weapon+" | "+override;
                options[0]="Set animation ID...";
                options[1]="Reset to authority";
                options[2]="Play current now";
                options[3]="Back";
                break;
            }

            case COMBAT_HIT:
                title="Hitsplat lab | "+
                    clip(combat.devHitSummary(),58);
                options[0]="Set damage...";
                options[1]="Set type...";
                options[2]="Auto normal/max";
                options[3]="Back";
                break;

            case PETS:{
                NpcEntity pet=npcs.pet();
                title="Pets | "+
                    (pet==null
                        ?"none"
                        :"item "+pet.petItemId+
                            " npc "+pet.definitionId);
                options[0]="Particle FX...";
                options[1]="Follow controls...";
                options[2]="Presentation...";
                options[3]="Back";
                break;
            }

            case PET_FX:
                title="Pet particle selector | "+
                    (dev.petParticleSelector()==null
                        ?"AUTO"
                        :dev.petParticleSelector());
                options[0]="Next selector";
                options[1]="Auto / clear";
                options[2]="Set selector ID...";
                options[3]="Back";
                break;

            case PET_FOLLOW:
                title="Pet follow | frozen="+
                    npcs.followFrozen()+
                    " delay="+
                    (dev.petFollowDelayMs()==null
                        ?"AUTO"
                        :dev.petFollowDelayMs()+"ms");
                options[0]=
                    npcs.followFrozen()
                        ?"Resume follow"
                        :"Freeze follow";
                options[1]="Step once";
                options[2]="Snap to owner";
                options[3]="Back";
                break;

            case PET_PRESENT:
                title="Pet presentation | native state="+
                    npcs.petNativeState();
                options[0]="Play animation ID...";
                options[1]="Play GFX ID...";
                options[2]="Cycle native state";
                options[3]="More presentation...";
                break;

            case PET_PRESENT_MORE:
                title="Pet presentation / custom | "+
                    (npcs.pet()==null
                        ?"no active pet"
                        :"npc "+npcs.pet().definitionId);
                options[0]="Custom content...";
                options[1]="Show pet info";
                options[2]="R1 Voidglass status";
                options[3]="Back";
                break;

            case CUSTOM_CONTENT:
                title="Custom content | LOCAL DEV - not production authority";
                options[0]="Voidglass Nistirio R3...";
                options[1]="Boundary / provenance";
                options[2]="R1 prototype reference";
                options[3]="Back";
                break;

            case CUSTOM_VOIDGLASS:
                title="Voidglass R3 | "+
                    (VoidglassR3CustomContent.active(
                        petState,npcs.pet())
                        ?"ACTIVE"
                        :"inactive")+
                    " | item 29999";
                options[0]="Give item 29999";
                options[1]="Next visual candidate";
                options[2]="Trigger VOIDGLASS RIFT";
                options[3]="Back";
                break;

            case MAGIC_PRAYER:
                title="Magic / Prayer | spells="+
                    SpellDefinitionRepository.count()+
                    " prayers="+
                    PrayerDefinitionRepository.count();
                options[0]="Magic controls...";
                options[1]="Prayer controls...";
                options[2]="Deactivate all prayers";
                options[3]="Back";
                break;

            case MAGIC:
                title="Magic book | "+magic.book()+
                    " | root="+magic.root();
                options[0]="Modern";
                options[1]="Ancient";
                options[2]="Lunar";
                options[3]="Back";
                break;

            case PRAYER:
                title="Prayer | "+prayers.book()+
                    " active="+prayers.activeCount()+
                    " icon="+prayers.manualHeadIcon();
                options[0]="Toggle prayer book";
                options[1]="Toggle widget ID...";
                options[2]="Manual head icon...";
                options[3]="Back";
                break;

            case WORLD:{
                int regionId=
                    ((movement.x()>>6)<<8)|
                    (movement.y()>>6);
                title="World | "+movement.x()+","+
                    movement.y()+","+
                    movement.plane()+
                    " region="+regionId;
                options[0]="Load region ID...";
                options[1]="Return HOME";
                options[2]="Inspect collision";
                options[3]="Back";
                break;
            }

            case ITEMS:
                title="Items | weapon "+
                    equipment.weapon()+" "+
                    devItemName(equipment.weapon());
                options[0]="Open Item Library ID...";
                options[1]="Equipment Stats";
                options[2]="Items Kept on Death";
                options[3]="Back";
                break;

            case PLAYER_NPC:
                title="Player / NPC lab | visible NPCs="+
                    npcs.visibleCount();
                options[0]="Player presentation...";
                options[1]="NPC sandbox...";
                options[2]="Authority census";
                options[3]="Back";
                break;

            case PLAYER:
                title="Player | "+playerPresentation.info();
                options[0]="Morph to NPC ID...";
                options[1]="Clear morph";
                options[2]="Presentation...";
                options[3]="Back";
                break;

            case PLAYER_PRESENT:
                title="Player presentation probe";
                options[0]="Play animation ID...";
                options[1]="Play GFX ID...";
                options[2]="Refresh appearance";
                options[3]="Back";
                break;

            case NPC:
                title="NPC sandbox | visible="+
                    npcs.visibleCount();
                options[0]="Spawn NPC ID...";
                options[1]="Clear dev NPCs";
                options[2]="List NPCs to log";
                options[3]="Back";
                break;

            case DIAG:
                title="Diagnostics | trace="+
                    dev.trace().enabled()+
                    " | "+
                    clip(
                        ContentAuthorityRepository.summary(),
                        42);
                options[0]=
                    dev.trace().enabled()
                        ?"Disable protocol trace"
                        :"Enable protocol trace";
                options[1]="Authority browser...";
                options[2]="Reset dev overrides...";
                options[3]="Back";
                break;

            case AUTHORITY:
                title="Authority browser | read-only | "+
                    ClientAssetAlignmentAuthority.shortStatus();
                options[0]="Magic / prayer authority...";
                options[1]="World / item authority...";
                options[2]="Research closure...";
                options[3]="Back";
                break;

            case AUTH_MAGIC:
                title="Magic authority | "+
                    clip(selectedMagicAuthoritySummary(),58);
                options[0]="Browse spell widget ID...";
                options[1]="Browse prayer widget ID...";
                options[2]="Counts / boundary";
                options[3]="Back";
                break;

            case AUTH_WORLD_ITEM:
                title="World/item authority | "+
                    clip(
                        selectedWorldItemAuthoritySummary(),
                        54);
                options[0]="Browse item ID...";
                options[1]="Browse region ID...";
                options[2]="Use current region";
                options[3]="Back";
                break;

            case RESEARCH:
                title="Research closure | "+
                    clip(
                        ResearchExhaustionAuthority.summary(),
                        58);
                options[0]="Equipment/static stats...";
                options[1]="Pet proc/presentation...";
                options[2]="World transitions...";
                options[3]="Client discovery...";
                break;

            case RESEARCH_EQUIP:{
                int itemId=
                    panel.selectedResearchEquipItemId();
                if(itemId<0)itemId=equipment.weapon();
                title="Equipment research | "+
                    clip(
                        EquipmentResearchAuthority.itemSummary(
                            itemId),
                        55);
                options[0]="Browse item ID...";
                options[1]="Use equipped weapon";
                options[2]="Schema / server boundary";
                options[3]="Back";
                break;
            }

            case RESEARCH_PET:{
                PetProcResearchAuthority.Row row=
                    selectedPetProcResearchRow();
                title="Pet research | "+
                    clip(
                        PetProcResearchAuthority.summary(row)+
                        " | "+
                        PetMovementResearchAuthority.summary(),
                        55);
                options[0]="Use active pet";
                options[1]="Browse research row #...";
                options[2]="Preview first candidate";
                options[3]="Back";
                break;
            }

            case RESEARCH_WORLD:{
                int regionId=
                    panel.selectedResearchTransitionRegion();
                if(regionId<0){
                    regionId=
                        ((movement.x()>>6)<<8)|
                        (movement.y()>>6);
                }
                int itemId=
                    panel.selectedResearchTeleportItemId();
                title="World transitions | "+
                    clip(
                        (itemId<0
                            ?"tele:none"
                            :WorldTransitionResearchAuthority
                                .teleportSummary(itemId))+
                        " | "+
                        WorldTransitionResearchAuthority
                            .regionSummary(regionId),
                        55);
                options[0]="Browse teleport item ID...";
                options[1]="Use current region";
                options[2]="Service/static contracts...";
                options[3]="Back";
                break;
            }

            case RESEARCH_SERVICE:
                title="NPC / world / shop static | "+
                    clip(ServiceResearchAuthority.summary(),55);
                options[0]="Interaction router atlas";
                options[1]="Blood / enchantment contracts";
                options[2]="Shop framework / boundaries";
                options[3]="Back";
                break;

            case RESEARCH_DISCOVERY:
                title="Client discovery | "+
                    clip(ClientDiscoveryAuthority.summary(),58);
                options[0]="Tasks / achievements";
                options[1]="Magic / construction";
                options[2]="UI / controls / minigames";
                options[3]="Asset/model closure...";
                break;

            case RESEARCH_ASSET:
                title="Asset/model closure | "+
                    clip(
                        AssetRuntimeResearchAuthority
                            .archiveSummary(),
                        55);
                options[0]="Client/assets alignment...";
                options[1]="Asset/model summaries";
                options[2]="Application protocols...";
                options[3]="Back";
                break;

            case RESEARCH_PROTOCOL:
                title="Application protocols | "+
                    clip(
                        ClientApplicationProtocolAuthority
                            .summary(),
                        55);
                options[0]="S2C250 application bus";
                options[1]="S2C126 / VM / settings";
                options[2]="C2S / world / events";
                options[3]="Back";
                break;

            case ALIGNMENT:
                title="Client/assets alignment | "+
                    ClientAssetAlignmentAuthority.shortStatus();
                options[0]="Show pinned hashes";
                options[1]="Authority census";
                options[2]="Root 328 reconciliation";
                options[3]="Back";
                break;

            case RESET_CONFIRM:
                title="Reset ALL temporary dev overrides?";
                options[0]="CONFIRM reset all";
                options[1]="Cancel";
                options[2]="Close panel";
                options[3]="Back";
                break;

            default:
                break;
        }

        writer.varShort(
            126,
            BootstrapPackets.widgetText126(
                2481,
                clip(title,80)));

        for(int i=0;i<4;i++){
            writer.varShort(
                126,
                BootstrapPackets.widgetText126(
                    2482+i,
                    clip(options[i],76)));
        }

        writer.fixed(
            164,
            BootstrapPackets.chatboxInterface164(2480));

        return true;
    }

    private PetProcResearchAuthority.Row selectedPetProcResearchRow(){
        int index=panel.selectedResearchPetRow();
        if(index>0)return PetProcResearchAuthority.byIndex(index);

        NpcEntity pet=npcs.pet();
        return pet==null
            ?null
            :PetProcResearchAuthority.findForPetItem(
                pet.petItemId);
    }

    V913WeaponRuntimeAuthority.Profile selectedRuntimeWeaponProfile(){
        int itemId=panel.selectedRuntimeWeaponItemId();
        if(itemId<0)itemId=equipment.weapon();
        return V913WeaponRuntimeAuthority.resolve(itemId);
    }

    String runtimeWeaponAuthoritySummary(int itemId){
        V913WeaponRuntimeAuthority.Profile profile=
            V913WeaponRuntimeAuthority.resolve(itemId);

        return RuntimeWeaponPresentationLab.summary(profile)+
            " previewSafe="+
            RuntimeWeaponPresentationLab.previewSafe(profile)+
            " projectilePolicy="+
            RuntimeWeaponPresentationLab.PROJECTILE_POLICY+
            " preAnimationPolicy="+
            RuntimeWeaponPresentationLab.PRE_ANIMATION_POLICY;
    }

    private String selectedMagicAuthoritySummary(){
        String spell=
            panel.selectedSpellWidget()<0
                ?"spell:none"
                :clip(
                    spellAuthoritySummary(
                        panel.selectedSpellWidget()),
                    30);

        String prayer=
            panel.selectedPrayerWidget()<0
                ?"prayer:none"
                :clip(
                    prayerAuthoritySummary(
                        panel.selectedPrayerWidget()),
                    30);

        return spell+" | "+prayer;
    }

    private String selectedWorldItemAuthoritySummary(){
        String item=
            panel.selectedItemId()<0
                ?"item:none"
                :clip(
                    itemAuthorityBrowserSummary(
                        panel.selectedItemId()),
                    28);

        int regionId=panel.selectedRegionId();
        if(regionId<0){
            regionId=
                ((movement.x()>>6)<<8)|
                (movement.y()>>6);
        }

        return item+" | "+
            clip(regionAuthoritySummary(regionId),28);
    }

    String spellAuthoritySummary(int widget){
        SpellDefinitionRepository.Spell definition=
            SpellDefinitionRepository.byWidget(widget);

        if(definition==null){
            return "spell widget="+widget+" UNKNOWN";
        }

        return "spell widget="+widget+
            " "+definition.name+
            " book="+definition.book+
            " lvl="+definition.level+
            " target="+definition.targeted+
            " resources="+
            (definition.resources==null
                ?0
                :definition.resources.length);
    }

    String prayerAuthoritySummary(int widget){
        PrayerDefinitionRepository.Def definition=
            PrayerDefinitionRepository.byWidget(widget);

        if(definition==null){
            return "prayer widget="+widget+" UNKNOWN";
        }

        return "prayer widget="+widget+
            " "+definition.name+
            " book="+definition.book+
            " lvl="+definition.level+
            " varp="+definition.varp;
    }

    String itemAuthorityBrowserSummary(int itemId){
        ItemAuthorityRepository.Entry entry=
            ItemAuthorityRepository.get(itemId);

        if(entry==null){
            return "item="+itemId+" UNKNOWN";
        }

        return ContentAuthorityRepository.itemSummary(itemId)+
            " equip="+entry.equippable()+
            " effectText="+
            (entry.effectText!=null&&
             !entry.effectText.trim().isEmpty());
    }

    String regionAuthoritySummary(int regionId){
        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(regionId);

        if(region==null){
            return "region="+regionId+" UNKNOWN";
        }

        return "region="+regionId+
            " "+
            (region.name==null||region.name.isEmpty()
                ?"unnamed"
                :region.name)+
            " bounds="+
            region.x0+","+region.y0+
            ".."+
            region.x1+","+region.y1+
            " decoded="+
            (region.mapPresent&&
             region.landPresent&&
             region.terrainParseOk&&
             region.objectParseOk)+
            " placements="+region.placements+
            " usage="+region.usageStatus;
    }

    private String devItemName(int itemId){
        ItemAuthorityRepository.Entry entry=
            ItemAuthorityRepository.get(itemId);

        return entry==null
            ?"unknown"
            :clip(
                ItemAuthorityRepository.stripTags(
                    entry.name),
                30);
    }

    private static String clip(String value,int max){
        if(value==null)return "";

        String clean=value
            .replace('\n',' ')
            .replace('\r',' ')
            .replaceAll("\\s+"," ")
            .trim();

        return clean.length()<=max
            ?clean
            :clean.substring(
                0,
                Math.max(0,max-3))+
                "...";
    }
}
