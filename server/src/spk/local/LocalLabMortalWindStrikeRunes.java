package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * R47 opt-in CUSTOM_LOCALLAB exact-inventory rune cost for one Wind Strike
 * against the R6 mortal practice NPC. No regular SpawnPK spell is enabled.
 *
 * A client-visible requirement is not itself evidence of original-server
 * consumption. This deliberately uses a clearly labeled practice-only rule.
 */
final class LocalLabMortalWindStrikeRunes {
    static final int AIR_RUNE=556;
    static final int MIND_RUNE=558;
    static final int WIDGET=1152;
    static final String AUTHORITY="CUSTOM_LOCALLAB_WIND_STRIKE_EXACT_RUNE_COST_V1";

    static boolean enabled(){
        return LocalLabMortalNpcFixture.enabled() &&
            Boolean.getBoolean("spk.local.r45MortalMagic") &&
            Boolean.getBoolean("spk.local.r47WindStrikeRunes");
    }

    static final class Plan {
        final int airSlot,mindSlot;
        private Plan(int airSlot,int mindSlot){
            this.airSlot=airSlot;
            this.mindSlot=mindSlot;
        }

        /** Never changes inventory unless both rune stacks still match. */
        void consume(BankState bank,ServerPacketWriter writer)throws IOException{
            Objects.requireNonNull(bank,"bank");
            Objects.requireNonNull(writer,"writer");
            require(bank,airSlot,AIR_RUNE);
            require(bank,mindSlot,MIND_RUNE);
            if(airSlot==mindSlot)
                throw new IllegalStateException("R47 overlapping rune slots");
            bank.consumeInventoryAmountSemantic(airSlot,AIR_RUNE,1);
            bank.consumeInventoryAmountSemantic(mindSlot,MIND_RUNE,1);
            bank.sendNormalInventory(writer);
        }
    }

    static Plan plan(BankState bank,SpellDefinitionRepository.Spell spell){
        if(!enabled()||bank==null||spell==null||
           spell.widget!=WIDGET||
           spell.book!=SpellDefinitionRepository.Book.MODERN||
           spell.resources.length!=2||
           spell.resources[0].required!=1||
           spell.resources[1].required!=1||
           !contains(spell.resources[0].inventoryIds,AIR_RUNE)||
           !contains(spell.resources[1].inventoryIds,MIND_RUNE))
            return null;
        int air=slot(bank,AIR_RUNE),mind=slot(bank,MIND_RUNE);
        return air<0||mind<0||air==mind?null:new Plan(air,mind);
    }

    private static int slot(BankState bank,int rune){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==rune&&stack.qty>=1)
                return i;
        }
        return -1;
    }

    private static void require(BankState bank,int slot,int rune){
        BankState.Stack stack=bank.inventoryAt(slot);
        if(stack==null||stack.itemId!=rune||stack.qty<1)
            throw new IllegalStateException(
                "R47 stale exact rune plan rune="+rune+" slot="+slot);
    }

    private static boolean contains(int[] values,int needle){
        for(int value:values)if(value==needle)return true;
        return false;
    }

    private LocalLabMortalWindStrikeRunes(){}
}
