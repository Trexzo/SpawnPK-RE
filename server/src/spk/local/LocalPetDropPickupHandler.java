package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns the LocalLab pet Drop/Pick-up lifecycle and its transient interaction
 * state. This is a behavior-preserving extraction from LocalSession.
 *
 * Recovered item/NPC mappings remain authority-driven. Session-only dev
 * bindings remain explicitly non-persistent.
 */
final class LocalPetDropPickupHandler {
    private static final long PET_PICKUP_REMOVE_DELAY_MS=0L;

    interface SessionBridge {
        String username();
        boolean persistentAccount();
        long sessionWorldTick();
        SceneUpdatePublisher scenePublisher();
        void saveAccount(String tag,String reason);
        int syncScopesightPassive(
            ServerPacketWriter serverPackets
        )throws IOException;
        void resetPetFollowDeadline();
        void ensurePetFollowScheduled(long now);
    }

    private final World world;
    private final BankState bank;
    private final MovementState movement;
    private final PetState petState;
    private final PetEffectState petEffects;
    private final MiniPetService miniPets;
    private final NpcRegistry npcs;
    private final VoidglassPetState voidglass;
    private final PetAccessoryState petAccessoryState;
    private final DevAuthorityWorkbench dev;
    private final SessionBridge bridge;

    private Integer pendingPetPickupScene;
    private long pendingPetPickupDeadlineMs;
    /** R2.12 compatibility state. R2.13+ Q/R pickup does not arm it. */
    private long pendingPetFacingClearAtMs=Long.MAX_VALUE;
    private long pendingPetPickupCompleteAtMs=Long.MAX_VALUE;
    private int pendingPetPickupItem=-1;
    private int pendingPetPickupNpc=-1;
    private int pendingPetPickupCompleteScene=-1;
    private String pendingPetPickupCompleteReason;
    private boolean petPickupOwnedFollowFreeze;

    LocalPetDropPickupHandler(
        World world,
        BankState bank,
        MovementState movement,
        PetState petState,
        PetEffectState petEffects,
        MiniPetService miniPets,
        NpcRegistry npcs,
        VoidglassPetState voidglass,
        PetAccessoryState petAccessoryState,
        DevAuthorityWorkbench dev,
        SessionBridge bridge
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.petState=Objects.requireNonNull(petState,"petState");
        this.petEffects=Objects.requireNonNull(petEffects,"petEffects");
        this.miniPets=Objects.requireNonNull(miniPets,"miniPets");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.voidglass=Objects.requireNonNull(voidglass,"voidglass");
        this.petAccessoryState=Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
        this.dev=Objects.requireNonNull(dev,"dev");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    boolean pickupPending(){
        return pendingPetPickupScene!=null;
    }

    boolean pendingPickupBlocksPetFollow(){
        if(pendingPetPickupScene==null)return false;
        NpcEntity pet=npcs.pet();
        // A freshly dropped pet initially shares the owner tile. Production lets
        // the pet perform its one-tile lifecycle egress before Pick-up completes.
        // That single overlap state is the only pending-Pick-up case allowed to
        // follow.
        return pet==null||
            pet.sceneIndex!=pendingPetPickupScene.intValue()||
            pet.x!=movement.x()||
            pet.y!=movement.y();
    }

    void handleDrop(
        DropItemAction a,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(a==null)return;

        if(a.widgetId!=BankState.NORMAL_INVENTORY_CONTAINER){
            System.out.println(
                tag+"V53_ITEM_DROP "+a+
                " result=OBSERVED_UNSUPPORTED_WIDGET itemRetained=true"
            );
            return;
        }

        String option5=
            ItemActionResolver.inventoryOption5Semantic(a.itemId);
        if(!"Drop".equalsIgnoreCase(option5)){
            System.out.println(
                tag+"V511_INVENTORY_OPTION5 "+a+
                " semantic="+option5+
                " result=DECODED_NOT_DROP itemRetained=true authority=EXACT_ITEM_ACTION"
            );
            return;
        }

        PetDefinitionRepository.Def def=
            resolvePetDefinitionForDrop(a.itemId);
        if(def==null){
            if(PetDefinitionRepository.isAmbiguous(a.itemId)){
                System.out.println(
                    tag+"V591_PET_DROP "+a+
                    " result=REJECTED_AMBIGUOUS_MAPPING itemRetained=true evidence="+
                    PetDefinitionRepository.ambiguousEvidence(a.itemId)+
                    " hint=use_::devpet_map_<itemId>_<npcId>_for_session_only_visual_binding"
                );
            }else{
                dropOrdinaryGround(a,serverPackets,tag);
            }
            return;
        }

        BankState.Stack st=bank.inventoryAt(a.slot);
        if(st==null||st.itemId!=a.itemId||st.qty<=0){
            System.out.println(
                tag+"V53_PET_DROP "+a+
                " result=REJECTED_INVENTORY_MISMATCH itemRetained=true"
            );
            return;
        }

        boolean replacing=petState.active()||npcs.pet()!=null;
        int oldItem=replacing?petState.itemId():-1;
        int oldNpc=replacing?petState.npcId():-1;

        if(replacing&&(!petState.active()||npcs.pet()==null)){
            System.out.println(
                tag+"V56_PET_REPLACE "+a+
                " result=REJECTED_ACTIVE_STATE_MISMATCH itemRetained=true"
            );
            return;
        }

        String inv=
            bank.consumeInventoryOne(
                a.slot,
                a.itemId,
                serverPackets
            );
        if(!inv.startsWith("INVENTORY_CONSUME_OK")){
            System.out.println(
                tag+"V56_PET_DROP "+a+
                " result="+inv+
                " itemRetained=true"
            );
            return;
        }

        int restoredOldSlot=-1;
        if(replacing){
            String despawn=npcs.removePet(serverPackets);
            if(voidglass.active()){
                Integer restore=
                    voidglass.clearAndRestoreSelector();
                npcs.devSetParticleSelector(
                    restore,
                    movement,
                    serverPackets
                );
                System.out.println(
                    tag+
                    "CUSTOM_PET_R1_VOIDGLASS state=CLEARED reason=PET_REPLACE restoredFx="+
                    (restore==null?"AUTO":restore)
                );
            }

            restoredOldSlot=
                bank.addInventoryOnePreferred(
                    oldItem,
                    a.slot,
                    serverPackets
                );
            if(restoredOldSlot<0){
                int rollbackNew=
                    bank.addInventoryOne(
                        a.itemId,
                        serverPackets
                    );
                throw new IllegalStateException(
                    "pet replacement could not restore old item="+
                    oldItem+
                    " despawn="+despawn+
                    " rollbackNew="+rollbackNew
                );
            }
            petState.clear();
        }

        clearFacingNow(
            serverPackets,
            tag,
            "PET_DROP_PRESPAWN"
        );

        if(def.npcId==1334||def.npcId==8210){
            String resetFx=
                LocalDevVisualOverrideStore.set(
                    "intrinsicfx",
                    null
                );
            System.out.println(
                tag+
                "V5128_SPECIAL_PET_DEFAULT_ACCESSORY_NONE npc="+
                def.npcId+
                " item="+def.itemId+
                " result="+resetFx+
                " bodyTreatmentPreserved=true accessoryLayer=NONE"
            );
        }

        String spawn=
            npcs.spawnPet(
                def,
                movement,
                serverPackets
            );
        if(!spawn.startsWith("PET_SPAWN_OK")){
            int rollbackNew=
                bank.addInventoryOne(
                    a.itemId,
                    serverPackets
                );
            System.out.println(
                tag+"V56_PET_DROP "+a+
                " result="+spawn+
                " rollbackNewSlot="+rollbackNew+
                " oldPetItemAlreadyRestoredSlot="+restoredOldSlot
            );
            return;
        }

        petState.activate(def);
        petEffects.onPetChanged(def.itemId,def.npcId);

        String accessorySpawn="NONE";
        if(petAccessoryState.activeItem()!=0){
            Integer selector=
                PetAccessoryAuthority.selector(
                    petAccessoryState.activeItem()
                );
            accessorySpawn=
                npcs.devSetParticleSelector(
                    selector,
                    movement,
                    serverPackets
                );
        }

        String miniSpawn=
            petState.miniConfigured()
                ?miniPets.onMainPetSpawn(
                    petState,
                    npcs,
                    movement,
                    serverPackets
                )
                :"MINIPET_NONE_CONFIGURED";

        int passiveChanged=
            bridge.syncScopesightPassive(serverPackets);

        serverPackets.varShort(
            81,
            CombatSync.player81AnimationOnly(
                PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION
            )
        );

        bridge.saveAccount(
            tag,
            replacing
                ?"PET_REPLACE"
                :"PET_DROP_SUMMON"
        );

        System.out.println(
            tag+
            (replacing?"V56_PET_REPLACE ":"V56_PET_DROP ")+
            a+
            " result="+spawn+
            " inventoryMutation="+inv+
            (replacing
                ?" replaced="+oldItem+
                    "->"+oldNpc+
                    " restoredOldItemSlot="+restoredOldSlot
                :"")+
            " scopesightSkillMask=0x"+
                Integer.toHexString(passiveChanged)+
            " ownerAnim="+
                PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION+
            " ownerGfx=NONE"+
            " accessory="+
                (petAccessoryState.activeItem()==0
                    ?"NONE"
                    :petAccessoryState.activeItem()+
                        "/selector"+
                        PetAccessoryAuthority.selector(
                            petAccessoryState.activeItem()
                        )+
                        "/"+
                        accessorySpawn)+
            " mini="+miniSpawn+
            " persistent="+bridge.persistentAccount()
        );
    }

    boolean handlePickupNpcAction(
        NpcAction a,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        NpcEntity pet=npcs.pet();
        if(!isPetPickupAction(a,pet,petState))
            return false;

        if(!bank.canAddInventoryOne(petState.itemId())){
            System.out.println(
                tag+"V56_PET_PICKUP "+a+
                " result=REJECTED_INVENTORY_FULL petRemains=true"
            );
            return true;
        }

        pendingPetPickupScene=pet.sceneIndex;
        pendingPetPickupDeadlineMs=
            System.currentTimeMillis()+10_000L;

        if(!cardinalAdjacentTo(pet.x,pet.y)&&
           !(pet.x==movement.x()&&pet.y==movement.y())){
            freezeFollowForPickup(tag);
        }

        bridge.resetPetFollowDeadline();

        if(cardinalAdjacentTo(pet.x,pet.y)){
            System.out.println(
                tag+"V51213_PET_PICKUP "+a+
                " result=QUEUED_FOR_NEXT_AUTHORITATIVE_WORLD_TICK"+
                " owner="+movement.x()+","+movement.y()+
                " pet="+pet.x+","+pet.y
            );
        }else{
            System.out.println(
                tag+"V51213_PET_PICKUP "+a+
                " result=DEFERRED_UNTIL_CARDINAL_ADJACENT distanceCheb="+
                chebyshev(
                    movement.x(),
                    movement.y(),
                    pet.x,
                    pet.y
                )+
                " owner="+movement.x()+","+movement.y()+
                " pet="+pet.x+","+pet.y
            );
        }
        return true;
    }

    void cancelDeferredForNewNpcAction(
        NpcAction a,
        String tag
    ){
        if(pendingPetPickupScene==null||
           pendingPetPickupCompleteAtMs!=Long.MAX_VALUE)
            return;

        Integer cancelled=pendingPetPickupScene;
        pendingPetPickupScene=null;
        releaseFollowAfterPickup(
            tag,
            "NEW_NPC_INTERACTION"
        );
        System.out.println(
            tag+"V5127_PET_PICKUP scene="+cancelled+
            " action=CANCELLED_BY_NEW_NPC_INTERACTION new="+a
        );
    }

    void tick(
        ServerPacketWriter serverPackets,
        String tag,
        long now
    )throws IOException{
        tryPickupDeferredPet(
            serverPackets,
            tag,
            now
        );
        tryCompletePetPickup(
            serverPackets,
            tag,
            now
        );
    }

    void cancelForMovement(
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(pendingPetPickupCompleteAtMs!=Long.MAX_VALUE){
            resetCompletion();
            pendingPetPickupScene=null;
            releaseFollowAfterPickup(
                tag,
                "MOVEMENT_AFTER_PICKUP_ANIMATION"
            );
            System.out.println(
                tag+
                "V5127_PET_PICKUP_COMPLETE action=CANCELLED_BY_MOVEMENT petRemains=true"
            );
        }

        clearFacingNow(
            serverPackets,
            tag,
            "NEW_MOVEMENT_INTENT"
        );
    }

    private PetDefinitionRepository.Def resolvePetDefinitionForDrop(
        int itemId
    ){
        Integer override=dev.petNpcBinding(itemId);
        PetDefinitionRepository.Def base=
            PetDefinitionRepository.get(itemId);

        if(override==null)
            return base;

        if(base==null){
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
                "V593_SESSION_DEV_NPC_BINDING_NOT_PERSISTED"
            );
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
                "+V593_SESSION_DEV_NPC_BINDING"
        );
    }

    private void dropOrdinaryGround(
        DropItemAction a,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        BankState.Stack st=bank.inventoryAt(a.slot);
        if(st==null||st.itemId!=a.itemId||st.qty<=0){
            System.out.println(
                tag+"V511_GROUND_DROP "+a+
                " result=REJECTED_INVENTORY_MISMATCH itemRetained=true"
            );
            return;
        }

        if(st.qty>0xffff){
            System.out.println(
                tag+"V511_GROUND_DROP "+a+
                " result=REJECTED_WIRE_AMOUNT_GT_65535 qty="+
                st.qty+
                " itemRetained=true authority=S2C44_U16_AMOUNT"
            );
            return;
        }

        Tile tile=
            new Tile(
                movement.x(),
                movement.y(),
                0
            );

        GroundItem before=
            world.groundItems().findOwned(
                a.itemId,
                tile.x,
                tile.y,
                tile.plane,
                bridge.username()
            );
        int oldAmount=before==null?0:before.amount;

        if((long)oldAmount+st.qty>0xffff){
            System.out.println(
                tag+"V511_GROUND_DROP "+a+
                " result=REJECTED_MERGED_WIRE_AMOUNT_GT_65535 existing="+
                oldAmount+
                " qty="+st.qty+
                " itemRetained=true"
            );
            return;
        }

        BankState.PreparedInventoryMutation inventoryMutation=
            bank.prepareConsumeInventoryAll(
                a.slot,
                a.itemId
            );

        if(!inventoryMutation.accepted()||
           inventoryMutation.result<=0){
            System.out.println(
                tag+"V511_GROUND_DROP "+a+
                " result=REJECTED_CONSUME_FAILED itemRetained=true"
            );
            return;
        }

        int qty=
            inventoryMutation.result;

        GroundItemRegistry.PreparedAdd groundMutation=
            world.groundItems().prepareAdd(
                a.itemId,
                qty,
                tile,
                bridge.username(),
                bridge.sessionWorldTick(),
                false
            );

        SceneUpdatePublisher scene=
            bridge.scenePublisher();
        SceneCoordinateContext.Snapshot sceneBefore=
            scene.context().snapshot();

        serverPackets.beginBatch();
        boolean ended=false;

        try{
            bank.publishPreparedInventoryMutation(
                inventoryMutation,
                serverPackets
            );

            if(groundMutation.merge())
                scene.groundAmount(
                    a.itemId,
                    groundMutation.expectedOldAmount,
                    groundMutation.newAmount,
                    tile
                );
            else
                scene.groundSpawn(
                    a.itemId,
                    groundMutation.newAmount,
                    tile
                );

            serverPackets.endBatch();
            ended=true;
        }catch(IOException failure){
            scene.context().restore(
                sceneBefore
            );
            if(!ended)
                try{
                    serverPackets.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            scene.context().restore(
                sceneBefore
            );
            if(!ended)
                try{
                    serverPackets.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            scene.context().restore(
                sceneBefore
            );
            if(!ended)
                try{
                    serverPackets.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }

        bank.commitPreparedInventoryMutation(
            inventoryMutation
        );
        GroundItem g=
            world.groundItems().commitPreparedAdd(
                groundMutation
            );

        bridge.saveAccount(tag,"GROUND_DROP");

        System.out.println(
            tag+"V511_GROUND_DROP "+a+
            " result=DROP_OK amount="+qty+
            " world="+tile+
            " registryId="+g.id+
            " policy=LOCAL_PERSIST_UNTIL_PICKED owner="+
            bridge.username()
        );
    }

    private boolean cardinalAdjacentTo(int x,int y){
        return Math.abs(x-movement.x())+
            Math.abs(y-movement.y())==1;
    }

    private void freezeFollowForPickup(String tag){
        if(petPickupOwnedFollowFreeze||
           npcs.followFrozen())
            return;

        npcs.devFollowFreeze(true);
        petPickupOwnedFollowFreeze=true;
        bridge.resetPetFollowDeadline();
        System.out.println(
            tag+
            "V5181_PET_PICKUP_FOLLOW_FREEZE owned=true reason=APPROACH_TARGET_STABILITY"
        );
    }

    private void releaseFollowAfterPickup(
        String tag,
        String reason
    ){
        if(!petPickupOwnedFollowFreeze)
            return;

        npcs.devFollowFreeze(false);
        petPickupOwnedFollowFreeze=false;
        bridge.resetPetFollowDeadline();

        if(npcs.pet()!=null&&npcs.needsFollow(movement))
            bridge.ensurePetFollowScheduled(
                System.currentTimeMillis()
            );

        System.out.println(
            tag+
            "V5181_PET_PICKUP_FOLLOW_FREEZE owned=false reason="+
            reason
        );
    }

    private void tryPickupDeferredPet(
        ServerPacketWriter serverPackets,
        String tag,
        long now
    )throws IOException{
        Integer scene=pendingPetPickupScene;
        if(scene==null)return;
        if(pendingPetPickupCompleteAtMs!=Long.MAX_VALUE)
            return;

        NpcEntity pet=npcs.pet();
        if(pet==null||
           pet.sceneIndex!=scene||
           !petState.active()||
           now>pendingPetPickupDeadlineMs){
            pendingPetPickupScene=null;
            releaseFollowAfterPickup(
                tag,
                "MISSING_OR_TIMEOUT"
            );
            System.out.println(
                tag+"V5126_PET_PICKUP scene="+scene+
                " action=CANCELLED_MISSING_OR_TIMEOUT"
            );
            return;
        }

        if(!cardinalAdjacentTo(pet.x,pet.y)){
            if(pet.x==movement.x()&&
               pet.y==movement.y())
                return;

            if(movement.queued()==0){
                pendingPetPickupScene=null;
                releaseFollowAfterPickup(
                    tag,
                    "PATH_ENDED_NOT_ADJACENT"
                );
                System.out.println(
                    tag+"V5126_PET_PICKUP scene="+scene+
                    " action=CANCELLED_PATH_ENDED_NOT_CARDINAL_ADJACENT owner="+
                    movement.x()+","+movement.y()+
                    " pet="+pet.x+","+pet.y
                );
            }
            return;
        }

        movement.clearQueuedPath();
        executePickupNow(
            new NpcAction(155,scene),
            serverPackets,
            tag,
            now,
            "PICKUP_AFTER_CARDINAL_ARRIVAL"
        );
    }

    private void executePickupNow(
        NpcAction a,
        ServerPacketWriter serverPackets,
        String tag,
        long now,
        String reason
    )throws IOException{
        NpcEntity pet=npcs.pet();
        if(pet==null||
           !petState.active()||
           a==null||
           a.sceneIndex!=pet.sceneIndex){
            pendingPetPickupScene=null;
            releaseFollowAfterPickup(
                tag,
                "STALE_TARGET"
            );
            System.out.println(
                tag+"V5126_PET_PICKUP "+a+
                " result=CANCELLED_STALE_TARGET reason="+
                reason
            );
            return;
        }

        if(!cardinalAdjacentTo(pet.x,pet.y)){
            System.out.println(
                tag+"V5126_PET_PICKUP "+a+
                " result=REJECTED_NOT_CARDINAL_ADJACENT reason="+
                reason
            );
            return;
        }

        if(!bank.canAddInventoryOne(petState.itemId())){
            pendingPetPickupScene=null;
            releaseFollowAfterPickup(
                tag,
                "INVENTORY_FULL"
            );
            System.out.println(
                tag+"V56_PET_PICKUP "+a+
                " result=REJECTED_INVENTORY_FULL petRemains=true reason="+
                reason
            );
            return;
        }

        int item=petState.itemId();
        int npc=petState.npcId();

        serverPackets.varShort(
            81,
            Player81MeasuredSync.animationAndTurnToTile(
                PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION,
                pet.x,
                pet.y
            )
        );

        int q=pet.x*2+1;
        int r=pet.y*2+1;

        pendingPetPickupItem=item;
        pendingPetPickupNpc=npc;
        pendingPetPickupCompleteScene=pet.sceneIndex;
        pendingPetPickupCompleteReason=reason;
        pendingPetPickupCompleteAtMs=
            now+PET_PICKUP_REMOVE_DELAY_MS;
        pendingPetFacingClearAtMs=Long.MAX_VALUE;

        System.out.println(
            tag+"V51213_PET_PICKUP "+a+
            " result=TURN_TILE_ANIM_AND_REMOVE_SYNCHRONIZED item="+
            item+
            " npc="+npc+
            " turnQ="+q+
            " turnR="+r+
            " petTile="+pet.x+","+pet.y+
            " ownerAnim="+
                PetPresentationProfile.OWNER_DROP_PICKUP_ANIMATION+
            " ownerGfx=NONE"+
            " interactionTargetUsed=false removeAfterMs=0 sameWorldTick=true reason="+
            reason
        );

        tryCompletePetPickup(
            serverPackets,
            tag,
            now
        );
    }

    private void tryCompletePetPickup(
        ServerPacketWriter serverPackets,
        String tag,
        long now
    )throws IOException{
        if(pendingPetPickupCompleteAtMs==Long.MAX_VALUE||
           now<pendingPetPickupCompleteAtMs)
            return;

        NpcEntity pet=npcs.pet();
        int scene=pendingPetPickupCompleteScene;

        if(pet==null||
           pet.sceneIndex!=scene||
           !petState.active()){
            pendingPetPickupCompleteAtMs=Long.MAX_VALUE;
            pendingPetPickupScene=null;
            releaseFollowAfterPickup(
                tag,
                "COMPLETE_STALE"
            );
            System.out.println(
                tag+"V5127_PET_PICKUP_COMPLETE scene="+scene+
                " result=CANCELLED_STALE"
            );
            return;
        }

        int item=pendingPetPickupItem;
        int npc=pendingPetPickupNpc;
        String reason=pendingPetPickupCompleteReason;

        String despawn=npcs.removePet(serverPackets);

        if(voidglass.active()){
            Integer restore=
                voidglass.clearAndRestoreSelector();
            npcs.devSetParticleSelector(
                restore,
                movement,
                serverPackets
            );
            System.out.println(
                tag+
                "CUSTOM_PET_R1_VOIDGLASS state=CLEARED reason=PET_PICKUP restoredFx="+
                (restore==null?"AUTO":restore)
            );
        }

        int dst=
            bank.addInventoryOne(
                item,
                serverPackets
            );
        if(dst<0)
            throw new IllegalStateException(
                "pet pickup inventory preflight mismatch"
            );

        petState.clear();
        petEffects.clear();
        pendingPetPickupScene=null;
        resetCompletion();

        releaseFollowAfterPickup(
            tag,
            "PICKUP_COMPLETE"
        );

        int passiveChanged=
            bridge.syncScopesightPassive(serverPackets);
        bridge.saveAccount(tag,"PET_PICKUP");

        System.out.println(
            tag+"V5127_PET_PICKUP_COMPLETE result="+
            despawn+
            " restoredItem="+item+
            " inventorySlot="+dst+
            " npc="+npc+
            " scopesightSkillMask=0x"+
                Integer.toHexString(passiveChanged)+
            " reason="+reason+
            " persistent="+bridge.persistentAccount()
        );
    }

    private void clearFacingNow(
        ServerPacketWriter serverPackets,
        String tag,
        String reason
    )throws IOException{
        if(pendingPetFacingClearAtMs==Long.MAX_VALUE)
            return;

        serverPackets.varShort(
            81,
            CombatSync.player81InteractionOnly(-1)
        );
        pendingPetFacingClearAtMs=Long.MAX_VALUE;

        System.out.println(
            tag+
            "V5126_PET_PICKUP_FACING_CLEAR target=-1 reason="+
            reason
        );
    }

    private void resetCompletion(){
        pendingPetPickupCompleteAtMs=Long.MAX_VALUE;
        pendingPetPickupItem=-1;
        pendingPetPickupNpc=-1;
        pendingPetPickupCompleteScene=-1;
        pendingPetPickupCompleteReason=null;
    }

    static boolean isPetPickupAction(
        NpcAction a,
        NpcEntity pet,
        PetState petState
    ){
        return a!=null&&
            a.opcode==155&&
            pet!=null&&
            petState!=null&&
            petState.active()&&
            a.sceneIndex==pet.sceneIndex;
    }

    static int chebyshev(
        int x0,
        int y0,
        int x1,
        int y1
    ){
        return Math.max(
            Math.abs(x1-x0),
            Math.abs(y1-y0)
        );
    }
}
