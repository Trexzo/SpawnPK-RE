package spk.local;

import java.io.IOException;

/**
 * Typed inventory Drop coordinator.
 *
 * Owns ordinary ground drops plus the current main-pet summon/replacement
 * transaction. Packet decoding, account persistence implementation and the
 * still-session-owned pet-pickup facing state stay outside this class.
 */
final class LocalDropItemHandler {
    @FunctionalInterface
    interface SaveCallback {
        void save(String tag,String reason);
    }

    @FunctionalInterface
    interface PickupFacingClearCallback {
        void clear(
            ServerPacketWriter writer,
            String tag,
            String reason
        )throws IOException;
    }

    @FunctionalInterface
    interface ScopesightSyncCallback {
        int sync(ServerPacketWriter writer)throws IOException;
    }

    private final World world;
    private final BankState bank;
    private final MovementState movement;
    private final PetState petState;
    private final PetEffectState petEffects;
    private final MiniPetService miniPets;
    private final NpcRegistry npcs;
    private final DevAuthorityWorkbench dev;
    private final VoidglassPetState voidglass;
    private final PetAccessoryState petAccessoryState;
    private final SaveCallback saveCallback;
    private final PickupFacingClearCallback pickupFacingClear;
    private final ScopesightSyncCallback scopesightSync;

    LocalDropItemHandler(
        World world,
        BankState bank,
        MovementState movement,
        PetState petState,
        PetEffectState petEffects,
        MiniPetService miniPets,
        NpcRegistry npcs,
        DevAuthorityWorkbench dev,
        VoidglassPetState voidglass,
        PetAccessoryState petAccessoryState,
        SaveCallback saveCallback,
        PickupFacingClearCallback pickupFacingClear,
        ScopesightSyncCallback scopesightSync
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.petEffects=java.util.Objects.requireNonNull(petEffects,"petEffects");
        this.miniPets=java.util.Objects.requireNonNull(miniPets,"miniPets");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.voidglass=java.util.Objects.requireNonNull(voidglass,"voidglass");
        this.petAccessoryState=java.util.Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
        this.saveCallback=java.util.Objects.requireNonNull(
            saveCallback,"saveCallback");
        this.pickupFacingClear=java.util.Objects.requireNonNull(
            pickupFacingClear,"pickupFacingClear");
        this.scopesightSync=java.util.Objects.requireNonNull(
            scopesightSync,"scopesightSync");
    }

    void handle(
        DropItemAction action,
        String username,
        long worldTick,
        boolean persistentAccount,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(action==null)return;

        if(action.widgetId!=BankState.NORMAL_INVENTORY_CONTAINER){
            System.out.println(
                tag+"V53_ITEM_DROP "+action+
                " result=OBSERVED_UNSUPPORTED_WIDGET itemRetained=true");
            return;
        }

        String option5=
            ItemActionResolver.inventoryOption5Semantic(
                action.itemId);

        if(!"Drop".equalsIgnoreCase(option5)){
            System.out.println(
                tag+"V511_INVENTORY_OPTION5 "+action+
                " semantic="+option5+
                " result=DECODED_NOT_DROP itemRetained=true authority=EXACT_ITEM_ACTION");
            return;
        }

        PetDefinitionRepository.Def definition=
            resolvePetDefinition(action.itemId);

        if(definition==null){
            if(PetDefinitionRepository.isAmbiguous(
                action.itemId)){
                System.out.println(
                    tag+"V591_PET_DROP "+action+
                    " result=REJECTED_AMBIGUOUS_MAPPING itemRetained=true evidence="+
                    PetDefinitionRepository.ambiguousEvidence(
                        action.itemId)+
                    " hint=use_::devpet_map_<itemId>_<npcId>_for_session_only_visual_binding");
            }else{
                dropOrdinaryGround(
                    action,
                    username,
                    worldTick,
                    scenePublisher,
                    writer,
                    tag);
            }
            return;
        }

        BankState.Stack stack=bank.inventoryAt(action.slot);
        if(stack==null||
           stack.itemId!=action.itemId||
           stack.qty<=0){
            System.out.println(
                tag+"V53_PET_DROP "+action+
                " result=REJECTED_INVENTORY_MISMATCH itemRetained=true");
            return;
        }

        boolean replacing=
            petState.active()||npcs.pet()!=null;
        int oldItem=
            replacing?petState.itemId():-1;
        int oldNpc=
            replacing?petState.npcId():-1;

        if(replacing&&
           (!petState.active()||npcs.pet()==null)){
            System.out.println(
                tag+"V56_PET_REPLACE "+action+
                " result=REJECTED_ACTIVE_STATE_MISMATCH itemRetained=true");
            return;
        }

        String inventoryMutation=
            bank.consumeInventoryOne(
                action.slot,
                action.itemId,
                writer);

        if(!inventoryMutation.startsWith(
            "INVENTORY_CONSUME_OK")){
            System.out.println(
                tag+"V56_PET_DROP "+action+
                " result="+inventoryMutation+
                " itemRetained=true");
            return;
        }

        int restoredOldSlot=-1;

        if(replacing){
            String despawn=npcs.removePet(writer);

            if(voidglass.active()){
                Integer restore=
                    voidglass.clearAndRestoreSelector();

                npcs.devSetParticleSelector(
                    restore,movement,writer);

                System.out.println(
                    tag+"CUSTOM_PET_R1_VOIDGLASS state=CLEARED reason=PET_REPLACE restoredFx="+
                    (restore==null?"AUTO":restore));
            }

            restoredOldSlot=
                bank.addInventoryOnePreferred(
                    oldItem,
                    action.slot,
                    writer);

            if(restoredOldSlot<0){
                int rollbackNew=
                    bank.addInventoryOne(
                        action.itemId,
                        writer);

                throw new IllegalStateException(
                    "pet replacement could not restore old item="+
                    oldItem+
                    " despawn="+despawn+
                    " rollbackNew="+rollbackNew);
            }

            petState.clear();
        }

        // A previous Pick-up may still own the legacy facing-clear state. Clear
        // that before a new scene-index pet is introduced.
        pickupFacingClear.clear(
            writer,tag,"PET_DROP_PRESPAWN");

        if(definition.npcId==1334||
           definition.npcId==8210){
            String resetFx=
                LocalDevVisualOverrideStore.set(
                    "intrinsicfx",null);

            System.out.println(
                tag+"V5128_SPECIAL_PET_DEFAULT_ACCESSORY_NONE npc="+
                definition.npcId+
                " item="+definition.itemId+
                " result="+resetFx+
                " bodyTreatmentPreserved=true accessoryLayer=NONE");
        }

        String spawn=
            npcs.spawnPet(
                definition,movement,writer);

        if(!spawn.startsWith("PET_SPAWN_OK")){
            int rollbackNew=
                bank.addInventoryOne(
                    action.itemId,
                    writer);

            System.out.println(
                tag+"V56_PET_DROP "+action+
                " result="+spawn+
                " rollbackNewSlot="+rollbackNew+
                " oldPetItemAlreadyRestoredSlot="+
                restoredOldSlot);
            return;
        }

        petState.activate(definition);
        petEffects.onPetChanged(
            definition.itemId,
            definition.npcId);

        String accessorySpawn="NONE";
        if(petAccessoryState.activeItem()!=0){
            Integer selector=
                PetAccessoryAuthority.selector(
                    petAccessoryState.activeItem());

            accessorySpawn=
                npcs.devSetParticleSelector(
                    selector,movement,writer);
        }

        String miniSpawn=
            petState.miniConfigured()
                ?miniPets.onMainPetSpawn(
                    petState,npcs,movement,writer)
                :"MINIPET_NONE_CONFIGURED";

        int passiveChanged=scopesightSync.sync(writer);

        // V9.08 production capture: every successful pet Drop uses owner
        // animation 827 with no owner GFX.
        writer.varShort(
            81,
            CombatSync.player81AnimationOnly(
                PetPresentationProfile
                    .OWNER_DROP_PICKUP_ANIMATION));

        saveCallback.save(
            tag,
            replacing
                ?"PET_REPLACE"
                :"PET_DROP_SUMMON");

        System.out.println(
            tag+
            (replacing
                ?"V56_PET_REPLACE "
                :"V56_PET_DROP ")+
            action+
            " result="+spawn+
            " inventoryMutation="+inventoryMutation+
            (replacing
                ?" replaced="+oldItem+
                    "->"+oldNpc+
                    " restoredOldItemSlot="+
                    restoredOldSlot
                :"")+
            " scopesightSkillMask=0x"+
            Integer.toHexString(passiveChanged)+
            " ownerAnim="+
            PetPresentationProfile
                .OWNER_DROP_PICKUP_ANIMATION+
            " ownerGfx=NONE"+
            " accessory="+
            (petAccessoryState.activeItem()==0
                ?"NONE"
                :petAccessoryState.activeItem()+
                    "/selector"+
                    PetAccessoryAuthority.selector(
                        petAccessoryState.activeItem())+
                    "/"+
                    accessorySpawn)+
            " mini="+miniSpawn+
            " persistent="+persistentAccount);
    }

    private void dropOrdinaryGround(
        DropItemAction action,
        String username,
        long worldTick,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        BankState.Stack stack=bank.inventoryAt(action.slot);

        if(stack==null||
           stack.itemId!=action.itemId||
           stack.qty<=0){
            System.out.println(
                tag+"V511_GROUND_DROP "+action+
                " result=REJECTED_INVENTORY_MISMATCH itemRetained=true");
            return;
        }

        if(stack.qty>0xffff){
            System.out.println(
                tag+"V511_GROUND_DROP "+action+
                " result=REJECTED_WIRE_AMOUNT_GT_65535 qty="+
                stack.qty+
                " itemRetained=true authority=S2C44_U16_AMOUNT");
            return;
        }

        Tile tile=
            new Tile(
                movement.x(),
                movement.y(),
                0);

        GroundItem before=
            world.groundItems().findOwned(
                action.itemId,
                tile.x,
                tile.y,
                tile.plane,
                username);

        int oldAmount=
            before==null?0:before.amount;

        if((long)oldAmount+stack.qty>0xffff){
            System.out.println(
                tag+"V511_GROUND_DROP "+action+
                " result=REJECTED_MERGED_WIRE_AMOUNT_GT_65535 existing="+
                oldAmount+
                " qty="+stack.qty+
                " itemRetained=true");
            return;
        }

        int quantity=
            bank.consumeInventoryAll(
                action.slot,
                action.itemId,
                writer);

        if(quantity<=0){
            System.out.println(
                tag+"V511_GROUND_DROP "+action+
                " result=REJECTED_CONSUME_FAILED itemRetained=true");
            return;
        }

        GroundItem ground=
            world.groundItems().add(
                action.itemId,
                quantity,
                tile,
                username,
                worldTick,
                false);

        if(oldAmount>0){
            scenePublisher.groundAmount(
                ground,oldAmount);
        }else{
            scenePublisher.groundSpawn(ground);
        }

        saveCallback.save(tag,"GROUND_DROP");

        System.out.println(
            tag+"V511_GROUND_DROP "+action+
            " result=DROP_OK amount="+quantity+
            " world="+tile+
            " registryId="+ground.id+
            " policy=LOCAL_PERSIST_UNTIL_PICKED owner="+
            username);
    }

    private PetDefinitionRepository.Def resolvePetDefinition(
        int itemId
    ){
        Integer override=dev.petNpcBinding(itemId);
        PetDefinitionRepository.Def base=
            PetDefinitionRepository.get(itemId);

        if(override==null)return base;

        if(base==null){
            // Session-only workbench mapping for an otherwise ambiguous item.
            PetDefinitionRepository.Def template=
                PetDefinitionRepository.get(24019);

            if(template==null)return null;

            return new PetDefinitionRepository.Def(
                itemId,
                override,
                ItemCatalog.name(itemId),
                "DEV NPC "+override,
                template.standAnim,
                template.walkAnim,
                template.turn180Anim,
                template.turn90CWAnim,
                template.turn90CCWAnim,
                template.size,
                template.models,
                "V593_SESSION_DEV_NPC_BINDING_NOT_PERSISTED");
        }

        return new PetDefinitionRepository.Def(
            base.itemId,
            override,
            base.itemName,
            "DEV NPC "+override,
            base.standAnim,
            base.walkAnim,
            base.turn180Anim,
            base.turn90CWAnim,
            base.turn90CCWAnim,
            base.size,
            base.models,
            base.provenance+
                "+V593_SESSION_DEV_NPC_BINDING");
    }
}
