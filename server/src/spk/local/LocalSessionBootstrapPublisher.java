package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Owns the exact initial post-login LocalLab gameplay/bootstrap publication.
 *
 * Packet order, HOME replay order, authority wording and persistence boundary
 * are preserved from the sealed LocalSession implementation.
 */
final class LocalSessionBootstrapPublisher {
    interface SessionBridge {
        void saveAccount(String tag,String reason);
    }

    private final World world;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final PlayerState playerState;
    private final PrayerState prayers;
    private final MagicState magic;
    private final BankState bank;
    private final HomeWorldRuntimePlan homeWorld;
    private final NpcRegistry npcs;
    private final PetState petState;
    private final PetAccessoryState petAccessoryState;
    private final MiniPetService miniPets;
    private final boolean movementEnabled;
    private final SessionBridge bridge;

    LocalSessionBootstrapPublisher(
        World world,
        EquipmentState equipment,
        MovementState movement,
        PlayerState playerState,
        PrayerState prayers,
        MagicState magic,
        BankState bank,
        HomeWorldRuntimePlan homeWorld,
        NpcRegistry npcs,
        PetState petState,
        PetAccessoryState petAccessoryState,
        MiniPetService miniPets,
        boolean movementEnabled,
        SessionBridge bridge
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.playerState=Objects.requireNonNull(playerState,"playerState");
        this.prayers=Objects.requireNonNull(prayers,"prayers");
        this.magic=Objects.requireNonNull(magic,"magic");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.homeWorld=Objects.requireNonNull(homeWorld,"homeWorld");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.petState=Objects.requireNonNull(petState,"petState");
        this.petAccessoryState=Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
        this.miniPets=Objects.requireNonNull(miniPets,"miniPets");
        this.movementEnabled=movementEnabled;
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void publish(
        ServerPacketWriter serverPackets,
        SceneUpdatePublisher scenePublisher,
        String username,
        boolean persistentAccount,
        String tag
    )throws IOException{
        int appearanceRole=
            world.appearanceRoleFor(
                username,
                playerState
            );

        BootstrapPackets.send(
            serverPackets,
            username,
            equipment.appearanceItems(),
            movement.runEnergy(),
            playerState,
            appearanceRole
        );

        // BootstrapPackets retains the certified HOME spawn bootstrap. Restore a
        // persisted authoritative position immediately afterwards without changing
        // the pinned bootstrap implementation or appearance semantics.
        if(movement.x()!=MovementState.INITIAL_X||
           movement.y()!=MovementState.INITIAL_Y||
           movement.plane()!=0){
            int localX=movement.x()-MovementState.REGION_BASE_X;
            int localY=movement.y()-MovementState.REGION_BASE_Y;
            serverPackets.varShort(
                81,
                BootstrapPackets.player81TeleportNoAppearance(
                    movement.plane(),
                    localY,
                    localX
                )
            );
            System.out.println(
                tag+"V51214_POSITION_RESTORE world="+
                movement.x()+","+movement.y()+","+movement.plane()+
                " localX="+localX+
                " localY="+localY+
                " wireOrder=Y_THEN_X authority=ACCOUNT_MOVEMENT_STATE"
            );
        }

        int combatRoot=
            CombatInterfaceRepository.forWeapon(equipment.weapon());

        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                combatRoot,
                CombatInterfaceRepository.TAB_INDEX
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.SKILLS_ROOT,
                1
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.ACHIEVEMENTS_ROOT,
                BootstrapPackets.ACHIEVEMENTS_INDEX
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.INVENTORY_ROOT,
                3
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.EQUIPMENT_ROOT,
                4
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(prayers.root(),5)
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(magic.root(),6)
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.CLAN_CHAT_ROOT,
                7
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.FRIENDS_ROOT,
                8
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.IGNORE_ROOT,
                9
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.LOGOUT_ROOT,
                10
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.OPTIONS_ROOT,
                11
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.EMOTES_ROOT,
                12
            )
        );
        serverPackets.fixed(
            71,
            BootstrapPackets.sidebar71(
                BootstrapPackets.SPAWN_TAB_SHORTCUT_ROOT,
                BootstrapPackets.SPAWN_TAB_INDEX
            )
        );

        for(int skill=0;
            skill<PlayerState.COMBAT_SKILL_COUNT;
            skill++){
            serverPackets.fixed(
                134,
                BootstrapPackets.skill134(
                    skill,
                    playerState.xp(skill),
                    playerState.currentLevel(skill)
                )
            );
        }

        bank.sendNormalInventory(serverPackets);
        bank.sendEquipment(serverPackets,equipment);

        if(playerState.cosmetic().active()){
            bank.sendCosmetic(
                serverPackets,
                playerState.cosmetic()
            );
        }

        if(movement.persistentRun()){
            serverPackets.fixed(
                36,
                BootstrapPackets.config36(173,1)
            );
        }

        HomeObjectOverlayReplayer.Stats homeScene=
            homeWorld.replayScene(
                serverPackets,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

        scenePublisher.context().invalidate();
        npcs.bootstrapHome(
            serverPackets,
            movement,
            petState,
            homeWorld
        );

        if(petState.active()&&
           petAccessoryState.activeItem()!=0){
            Integer selector=
                PetAccessoryAuthority.selector(
                    petAccessoryState.activeItem()
                );
            String visual=
                npcs.devSetParticleSelector(
                    selector,
                    movement,
                    serverPackets
                );
            System.out.println(
                tag+
                "V5131_PET_ACCESSORY_PERSIST_RESTORE item="+
                petAccessoryState.activeItem()+
                " selector="+selector+
                " visual={"+visual+
                "} authority=ACCOUNT_SEMANTIC_STATE"
            );
        }

        if(petState.active()&&petState.miniConfigured()){
            System.out.println(
                tag+"V511_MINIPET_BOOTSTRAP "+
                miniPets.onMainPetSpawn(
                    petState,
                    npcs,
                    movement,
                    serverPackets
                )
            );
        }

        int groundReplay=0;
        for(GroundItem g:world.groundItems().snapshot()){
            if(g.owner==null||
               g.owner.equalsIgnoreCase(username)){
                scenePublisher.groundSpawn(g);
                groundReplay++;
            }
        }

        System.out.println(
            tag+"WORLD_R7_HOME_BOOTSTRAP scene="+homeScene+
            " npcVisible="+npcs.visibleCount()+
            " trackedHomeNpcs="+homeWorld.trackedWorldNpcCount()+
            " bloodFountain=1799@3079,3492"+
            " dummies=5x1488+1x1489 productionPositions=true"+
            " pet="+
                (petState.active()
                    ?petState.itemId()+"->"+petState.npcId()
                    :"none")+
            " miniConfigured="+
                (petState.miniConfigured()
                    ?petState.miniItemId()
                    :-1)+
            " groundReplay="+groundReplay
        );

        byte[] appearance=
            BootstrapPackets.appearanceBlock(
                username,
                equipment.appearanceItems(),
                playerState,
                null,
                appearanceRole
            );

        System.out.println(
            tag+
            "M4_BOOTSTRAP_SENT packets=249,73,81 region=385,436 local=55,55"+
            " appearanceMask=0x10 appearanceBytes="+appearance.length+
            " appearanceRole="+appearanceRole+
            " status=M4_CERTIFIED"
        );
        System.out.println(
            tag+"M5_RUN_ENERGY_SENT opcode=110 value="+
            movement.runEnergy()+
            " schema=STATIC_EXACT_FIXED1 persistentAccount="+
            persistentAccount
        );
        System.out.println(
            tag+
            "M5_RUN_ORB_WIDGET_SENT opcode=126 widget=149 text=\""+
            movement.runEnergy()+
            "%\" schema=STATIC_EXACT_VARSHORT"
        );

        EquipmentMetadataRepository.Meta bootMeta=
            EquipmentMetadataRepository.resolveKnownSlot(
                equipment.weapon(),
                EquipmentSlot.WEAPON
            );
        EquipmentPoseProfile bootPose=
            EquipmentPoseRepository.forAppearance(
                equipment.appearanceItems()
            );

        System.out.println(
            tag+
            "V522_EQUIPMENT_BOOTSTRAP weaponSlot="+
            EquipmentState.WEAPON_SLOT+
            " itemId="+equipment.weapon()+
            " item="+
            ItemCatalog.name(
                equipment.weapon()
            ).replace(' ','_')+
            " appearanceValue="+
            (512+equipment.weapon())+
            " pose="+bootPose.name+
            " pose7="+Arrays.toString(bootPose.toArray())+
            " weaponSpecificMask=0x"+
            Integer.toHexString(bootPose.weaponSpecificMask)+
            " weaponSpecificComplete="+bootPose.weaponSpecificComplete()+
            " twoHanded="+(bootMeta!=null&&bootMeta.twoHanded)+
            " inventoryRoot=3213 inventoryWidget=3214"
        );

        System.out.println(
            tag+"V57_NATIVE_SIDEBAR combat="+combatRoot+
            "@0 skills=3917@1 achievements=44100@2 inventory=3213@3 equipment=1644@4 prayer=5608@5 magic=1151@6"+
            " clan=18128@7 friends=5065@8 ignore=5715@9 logout=2449@10 options=904@11 emotes=147@12"+
            " spawnShortcut=0@"+BootstrapPackets.SPAWN_TAB_INDEX+
            "->"+BootstrapPackets.SPAWN_TAB_ROOT+
            " equipmentContainer="+EquipmentState.EQUIPMENT_WIDGET+
            " localRank=205"
        );

        System.out.println(
            tag+"V5_ITEM_REPOSITORY loaded="+
            ItemDefinitionRepository.count()+
            " genericCommands=::item|::tabitem_<id>_[amount] source=items.json+SpawnPK_i.bin"
        );
        System.out.println(
            tag+"V57_PET_REPOSITORY mapped="+
            PetDefinitionRepository.count()+
            " ambiguous="+PetDefinitionRepository.ambiguousCount()+
            " lifecycle=opcode87_DROP->packet65_FOLLOWER->opcode155_PICKUP"+
            " ownerTargetBase=32768 localPlayerIndex="+
            NpcRegistry.LOCAL_PLAYER_INDEX
        );
        System.out.println(
            tag+"V54_PLAYER_STATE skills=7 hp="+
            playerState.currentLevel(PlayerState.HITPOINTS)+
            " prayer="+playerState.currentLevel(PlayerState.PRAYER)+
            " combat="+playerState.combatLevel()+
            " xp99="+PlayerState.XP_99+
            " skillPacket=134 compSelectors="+
            playerState.compSelectorSummary()
        );
        System.out.println(
            tag+"V54_WEAPON_POSE_REPOSITORY directObserved="+
            WeaponPoseRepository.directCount()+
            " canonicalFamilies="+WeaponPoseRepository.familyCount()+
            " conflicts="+WeaponPoseRepository.conflictingFamilyCount()
        );
        System.out.println(
            tag+"V57_COMBAT_M2 weaponSlotResolved="+
            CombatWeaponRepository.count()+
            " currentEquipActionWeapons="+
            CombatWeaponRepository.currentEquipActionWeaponCount()+
            " productionPoseProfiles="+
            CombatWeaponRepository.withProductionPose()+
            " inferredPresentationProfiles="+
            CombatWeaponRepository.withInferredPresentation()+
            " mechanicsResolved="+
            CombatWeaponRepository.mechanicsResolved()+
            " targets=1488:PLAYER_PVP,1489:NPC_PVM"+
            " normalDamage=DISABLED_UNTIL_FORMULA_EVIDENCE"+
            " npcAttackOpcode72=STATIC_EXACT_BE_SHORT_A"+
            " packet81AnimationMask=0x08 packet65NpcAnimMask=0x10 packet65SingleHitMask=0x40"
        );
        System.out.println(
            tag+
            "V523_EQUIPMENT_RECONCILIATION currentActionCandidates="+
            EquipmentMetadataRepository.currentActionCandidateCount()+
            " resolved="+
            EquipmentMetadataRepository.currentActionResolvedCount()+
            " productionAppearanceIds="+
            EquipmentMetadataRepository.productionObservedCount()+
            " clientOpcode41BaseFallback=true provenanceAware=true"
        );

        if(movementEnabled){
            System.out.println(
                tag+"M5_AUTHORITY_ENABLED world="+
                movement.x()+","+movement.y()+
                " tickMs=600 collision=CLIENT_SUBMITTED_PATH regionPolicy=CURRENT_104x104"+
                " runEnergy="+movement.runEnergy()+
                " runToggle="+
                (movement.persistentRun()?1:0)+
                " runToggleWidget=152 config173=ACK minimap248=ACCEPT_PATH_CORE"
            );
        }

        bridge.saveAccount(tag,"BOOTSTRAP");
    }
}
