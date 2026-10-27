package spk.local;

import java.io.IOException;

/**
 * Typed spell-target gameplay adapter.
 *
 * Packet decoding remains in ClientPacketProbe. Validation stays in MagicState
 * and unresolved original-server effects remain explicitly fail-closed.
 */
final class LocalSpellTargetHandler {
    private final MagicState magic;
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerState playerState;
    private final NpcRegistry npcs;
    private final CombatEngine combat;
    private final WorldPlayer lifecycleOwner;
    private World canonicalWorld;
    private java.util.function.LongSupplier worldGeneration;


    /**
     * Production binding. All state channels are resolved from this exact
     * WorldPlayer; the legacy composition constructor below is retained
     * only for existing source-only unit callers.
     */
    /**
     * Exact LocalSession production binding. The World ownership guard is
     * only an in-process current-session admission check, not the durable
     * cross-World account lease tracked separately under #2559.
     */
    LocalSpellTargetHandler(
        World world,
        WorldPlayer owner,
        java.util.function.LongSupplier generation,
        NpcRegistry npcs,
        CombatEngine combat
    ){
        this(owner,npcs,combat);
        this.canonicalWorld=java.util.Objects.requireNonNull(
            world,"world");
        this.worldGeneration=java.util.Objects.requireNonNull(
            generation,"generation");
    }

    LocalSpellTargetHandler(
        WorldPlayer owner,
        NpcRegistry npcs,
        CombatEngine combat
    ){
        this(
            java.util.Objects.requireNonNull(owner,"owner"),
            owner.magic(),owner.bank(),owner.equipment(),
            owner.playerState(),npcs,combat
        );
    }

    LocalSpellTargetHandler(
        MagicState magic,
        BankState bank,
        EquipmentState equipment,
        PlayerState playerState,
        NpcRegistry npcs,
        CombatEngine combat
    ){
        this(null,magic,bank,equipment,playerState,npcs,combat);
    }

    private LocalSpellTargetHandler(
        WorldPlayer owner,
        MagicState magic,
        BankState bank,
        EquipmentState equipment,
        PlayerState playerState,
        NpcRegistry npcs,
        CombatEngine combat
    ){
        this.lifecycleOwner=owner;
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
    }

    String handle(SpellTargetRequest req,ServerPacketWriter serverPackets)throws IOException{
        if(req==null)return null;

        // Production gameplay must not route caster actions after a lethal
        // hit. Check the canonical WorldPlayer lifecycle before touching
        // MagicState (which updates its "last" field on accepted validation)
        // or routing to a dummy NPC hit. This is a same-thread gameplay
        // admission guard, not a concurrent cast/death linearization proof.
        if(lifecycleOwner!=null){
            synchronized(lifecycleOwner.mutationLock()){
                if(lifecycleOwner.lifecycle().dead()||
                   playerState.currentLevel(PlayerState.HITPOINTS)<=0){
                    return "V510_MAGIC_TARGET "+req+
                        " result=REJECTED_CASTER_DEAD"+
                        " authority=CUSTOM_LOCALLAB_LIFECYCLE"+
                        " originalMagicEffectsUnverified=true";
                }
            }
        }

        // An old LocalSession must not validate a cast using a player
        // detached from this exact World generation.
        if(canonicalWorld!=null&&
           (canonicalWorld.closed()||
            !canonicalWorld.players().owns(
                lifecycleOwner,worldGeneration.getAsLong()))){
            return "V510_MAGIC_TARGET "+req+
                " result=REJECTED_STALE_CASTER_WORLD_OWNER"+
                " magicStateUnchanged=true";
        }

        // Canonical combat NPCs have a stable World ID in the viewer's
        // projection. A stale scene index is not permission to cast at an
        // unregistered/dead canonical NPC. Legacy local diagnostic dummies
        // have no canonical ID and retain their original separate route.
        if(canonicalWorld!=null&&
           req.kind==SpellTargetRequest.Kind.NPC){
            NpcEntity view=npcs.scene(req.targetIndex);
            if(view!=null&&view.canonicalId()!=null){
                EntityId id=view.canonicalId();
                WorldNpc canonical=canonicalWorld.npcs().byId(id);
                if(canonical==null||
                   canonical.definitionId!=view.definitionId)
                    return "V510_MAGIC_TARGET "+req+
                        " result=REJECTED_STALE_CANONICAL_NPC"+
                        " magicStateUnchanged=true";
                NpcLifecycleService.Snapshot state=
                    canonicalWorld.npcLifecycle().get(id);
                if(state==null||!state.alive()||
                   state.hitpoints<=0||
                   state.definitionId!=view.definitionId)
                    return "V510_MAGIC_TARGET "+req+
                        " result=REJECTED_CANONICAL_NPC_NOT_ALIVE"+
                        " magicStateUnchanged=true";
            }
        }

        // Validate the *actual* target identity before invoking MagicState,
        // whose accepted check changes the 'last' spell record. This avoids
        // phantom accepted casts on stale scene indexes or inventory slots.
        // Typed codec transport is exact-client; the target-validity policy
        // is conservative CUSTOM_LOCALLAB (no original effects inferred).
        if(req.kind==SpellTargetRequest.Kind.NPC&&
           npcs.scene(req.targetIndex)==null){
            return "V510_MAGIC_TARGET "+req+
                " result=REJECTED_TARGET_NPC_NOT_VISIBLE"+
                " scene="+req.targetIndex+
                " magicStateUnchanged=true";
        }
        if(req.kind==SpellTargetRequest.Kind.INVENTORY_ITEM){
            if(req.targetWidget!=BankState.NORMAL_INVENTORY_CONTAINER){
                return "V510_MAGIC_TARGET "+req+
                    " result=REJECTED_TARGET_UNVERIFIED_INVENTORY_WIDGET"+
                    " widget="+req.targetWidget+
                    " magicStateUnchanged=true";
            }
            BankState.Stack slot=bank.inventoryAt(req.targetSlot);
            if(slot==null||slot.itemId!=req.targetId||
               slot.qty<=0){
                return "V510_MAGIC_TARGET "+req+
                    " result=REJECTED_TARGET_INVENTORY_MISMATCH"+
                    " magicStateUnchanged=true";
            }
        }

        MagicState.Check check=magic.target(req,bank,equipment,playerState);
        if(!check.accepted){
            return "V510_MAGIC_TARGET "+req+
                " result="+check.message+
                " state={"+magic.summary()+"}";
        }

        String effect="ROUTER_ACCEPTED_EFFECT_UNIMPLEMENTED";

        if(req.kind==SpellTargetRequest.Kind.NPC){
            NpcEntity target=npcs.scene(req.targetIndex);
            if(target==null){
                effect="REJECTED_TARGET_NPC_NOT_VISIBLE scene="+req.targetIndex;
            }else if(CombatTargetRepository.isCombatDummy(target.definitionId)){
                effect=combat.magicFixtureHit(
                    req.targetIndex,check.spell,npcs,serverPackets);
            }else{
                effect="TARGET_NPC_VISIBLE def="+target.definitionId+
                    " effect=UNIMPLEMENTED_SERVER_AUTHORITY";
            }
        }else if(req.kind==SpellTargetRequest.Kind.INVENTORY_ITEM){
            BankState.Stack at=bank.inventoryAt(req.targetSlot);
            if(req.targetWidget==BankState.NORMAL_INVENTORY_CONTAINER &&
               (at==null||at.itemId!=req.targetId)){
                effect="REJECTED_TARGET_INVENTORY_MISMATCH";
            }else{
                effect="TARGET_ITEM_ROUTED effect=UNIMPLEMENTED_SERVER_AUTHORITY";
            }
        }else if(req.kind==SpellTargetRequest.Kind.PLAYER){
            effect="TARGET_PLAYER_ROUTED index="+req.targetIndex+
                " effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        }else if(req.kind==SpellTargetRequest.Kind.OBJECT){
            effect="TARGET_OBJECT_ROUTED id="+req.targetId+
                " world="+req.worldX+","+req.worldY+
                " effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        }else if(req.kind==SpellTargetRequest.Kind.GROUND_ITEM){
            effect="TARGET_GROUND_ITEM_ROUTED id="+req.targetId+
                " world="+req.worldX+","+req.worldY+
                " effect=UNIMPLEMENTED_SERVER_AUTHORITY";
        }

        return "V510_MAGIC_TARGET "+req+
            " spell="+check.spell+
            " validation="+check.message+
            " result="+effect;
    }
}
