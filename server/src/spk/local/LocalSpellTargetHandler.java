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

    LocalSpellTargetHandler(
        MagicState magic,
        BankState bank,
        EquipmentState equipment,
        PlayerState playerState,
        NpcRegistry npcs,
        CombatEngine combat
    ){
        this.magic=java.util.Objects.requireNonNull(magic,"magic");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
    }

    String handle(SpellTargetRequest req,ServerPacketWriter serverPackets)throws IOException{
        if(req==null)return null;

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
