package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * P5.12 explicit LOCAL_LAB resource transaction for a fixed Wind Strike
 * (modern widget 1152) cost of one air rune (556) and one mind rune (558).
 *
 * This service is deliberately NOT connected to combat or cast routing:
 * no mana/combat/projectile/XP/damage or success is inferred. It provides
 * the inventory settlement primitive those systems must eventually share.
 *
 * Only canonical runes 556/558 and the unambiguous air-staff 1381 provider
 * are supported. Combination rune alternatives and mixed cost allocation
 * fail closed until policy is specified and separately tested.
 */
final class LocalLabWindStrikeRuneTransaction {
    static final int WIND_STRIKE_WIDGET=1152;
    static final int AIR_RUNE=556;
    static final int MIND_RUNE=558;
    static final int AIR_STAFF=1381;
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_P512_WIND_STRIKE_CANONICAL_RUNE_COST";

    private final WorldPlayer owner;

    LocalLabWindStrikeRuneTransaction(WorldPlayer owner){
        this.owner=Objects.requireNonNull(owner,"owner");
    }

    String settle(ServerPacketWriter writer)throws IOException{
        Objects.requireNonNull(writer,"writer");
        synchronized(owner.mutationLock()){
            if(owner.lifecycle().dead()||
               owner.playerState().currentLevel(
                   PlayerState.HITPOINTS)<=0)
                return "REJECTED_CASTER_DEAD";

            SpellDefinitionRepository.Spell spell=
                SpellDefinitionRepository.byWidget(
                    WIND_STRIKE_WIDGET);
            if(spell==null||
               spell.book!=SpellDefinitionRepository.Book.MODERN||
               !spell.targeted||spell.level!=1||
               !"Wind strike".equals(spell.name)||
               spell.resources.length!=2||
               spell.resources[0].required!=1||
               spell.resources[1].required!=1||
               spell.resources[0].inventoryIds.length==0||
               spell.resources[1].inventoryIds.length==0||
               spell.resources[0].inventoryIds[0]!=AIR_RUNE||
               spell.resources[1].inventoryIds[0]!=MIND_RUNE)
                return "REJECTED_UNREVIEWED_WIND_STRIKE_REQUIREMENT_DRIFT";

            if(owner.magic().book()!=SpellDefinitionRepository.Book.MODERN)
                return "REJECTED_WRONG_SPELLBOOK";
            if(owner.playerState().currentLevel(PlayerState.MAGIC)<1)
                return "REJECTED_MAGIC_LEVEL";

            BankState bank=owner.bank();
            int[] beforeIds=new int[BankState.INVENTORY_CAPACITY];
            int[] beforeQty=new int[BankState.INVENTORY_CAPACITY];
            int airSlot=-1,mindSlot=-1;
            for(int slot=0;slot<beforeIds.length;slot++){
                BankState.Stack stack=bank.inventoryAt(slot);
                beforeIds[slot]=stack==null?-1:stack.itemId;
                beforeQty[slot]=stack==null?0:stack.qty;
                if(stack!=null&&stack.qty>0){
                    if(stack.itemId==AIR_RUNE)airSlot=slot;
                    if(stack.itemId==MIND_RUNE)mindSlot=slot;
                }
            }
            boolean staff=owner.equipment().hasEquipped(AIR_STAFF);
            if(!staff&&airSlot<0)
                return "REJECTED_AIR_RUNE_OR_PROVIDER_MISSING";
            if(mindSlot<0)
                return "REJECTED_MIND_RUNE_MISSING";

            int[] afterIds=beforeIds.clone();
            int[] afterQty=beforeQty.clone();
            if(!staff)consumeOne(afterIds,afterQty,airSlot);
            consumeOne(afterIds,afterQty,mindSlot);

            // The exact visible item-container postimage is encoded first.
            // Native bank mirror 5064 is included when the bank UI is open.
            byte[][] views=bank.previewRecipeInventoryViews(
                afterIds,afterQty);
            ServerPacketWriter.RecoverablePacketResult result=
                writer.publishQueueOnlyRecoverablePacketIfIdle(()->{
                    for(byte[] body:views)
                        writer.varShort(53,body);
                });
            if(result!=
               ServerPacketWriter.RecoverablePacketResult.COMMITTED)
                return "REJECTED_WRITER_BACKPRESSURE_RETRYABLE";

            // No inventory state moves until all S2C53 bytes are accepted.
            // Does not claim all other inventory writers use this owner lock.
            bank.replaceInventorySemantic(afterIds,afterQty);
            return "LOCAL_LAB_WIND_STRIKE_RUNES_SETTLED"+
                " usedAirStaff="+staff+
                " airSpent="+(staff?0:1)+
                " mindSpent=1"+
                " authority="+AUTHORITY+
                " queueAdmittedNotClientAck=true"+
                " combatAndExperienceNotApplied=true";
        }
    }

    private static void consumeOne(
        int[] ids,int[] qty,int slot
    ){
        if(slot<0||slot>=qty.length||qty[slot]<=0)
            throw new IllegalStateException(
                "invalid single-rune postimage");
        qty[slot]--;
        if(qty[slot]==0)ids[slot]=-1;
    }
}
