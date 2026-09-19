package spk.local;

import java.io.*;

final class MagicState {
    private SpellDefinitionRepository.Book book=SpellDefinitionRepository.Book.MODERN;
    private String last="NONE";

    SpellDefinitionRepository.Book book(){return book;}
    int root(){return book.root;}
    String last(){return last;}

    String switchBook(String token,ServerPacketWriter w)throws IOException{
        SpellDefinitionRepository.Book next;
        if("modern".equalsIgnoreCase(token)||"normal".equalsIgnoreCase(token)) next=SpellDefinitionRepository.Book.MODERN;
        else if("ancient".equalsIgnoreCase(token)||"ancients".equalsIgnoreCase(token)) next=SpellDefinitionRepository.Book.ANCIENT;
        else if("lunar".equalsIgnoreCase(token)||"lunars".equalsIgnoreCase(token)) next=SpellDefinitionRepository.Book.LUNAR;
        else return "REJECTED_BOOK expected=modern|ancient|lunar";
        book=next;
        w.fixed(71,BootstrapPackets.sidebar71(book.root,6));
        w.fixed(106,BootstrapPackets.selectTab106(6));
        last="BOOK_SWITCH "+book;
        return "SPELLBOOK_SET book="+book+" root="+book.root;
    }

    Check direct(int widget,BankState bank,EquipmentState equipment,PlayerState player){
        SpellDefinitionRepository.Spell s=SpellDefinitionRepository.byWidget(widget);
        if(s==null||s.targeted)return Check.notSpell();
        Check c=validate(s,null,bank,equipment,player);
        if(c.accepted)last="DIRECT_ACCEPT "+s;
        else if(c.handled)last=c.message;
        return c;
    }

    Check target(SpellTargetRequest req,BankState bank,EquipmentState equipment,PlayerState player){
        SpellDefinitionRepository.Spell s=SpellDefinitionRepository.byWidget(req.spellWidget);
        if(s==null||!s.targeted)return Check.rejectHandled("REJECTED_UNKNOWN_TARGETED_SPELL widget="+req.spellWidget);
        Check c=validate(s,req.kind,bank,equipment,player);
        if(c.accepted)last="TARGET_ACCEPT "+s+" target="+req.kind;
        else last=c.message;
        return c;
    }

    private Check validate(SpellDefinitionRepository.Spell s,SpellTargetRequest.Kind kind,BankState bank,EquipmentState equipment,PlayerState player){
        if(s.book!=book)return Check.rejectHandled("REJECTED_WRONG_SPELLBOOK active="+book+" spellBook="+s.book+" spell="+s.name);
        if(player.currentLevel(PlayerState.MAGIC)<s.level)
            return Check.rejectHandled("REJECTED_MAGIC_LEVEL spell="+s.name+" required="+s.level+" current="+player.currentLevel(PlayerState.MAGIC));
        if(kind!=null && !s.supports(kind))
            return Check.rejectHandled("REJECTED_TARGET_MASK spell="+s.name+" mask="+s.targetMask+" target="+kind+" bit="+kind.mask);
        for(SpellDefinitionRepository.Resource r:s.resources){
            boolean ok=false;
            for(int id:r.inventoryIds) if(bank.inventoryCount(id)>=r.required){ok=true;break;}
            if(!ok) for(int id:r.equippedProviderIds) if(equipment.hasEquipped(id)){ok=true;break;}
            if(!ok)
                return Check.rejectHandled("REJECTED_VISIBLE_RESOURCE_REQUIREMENT spell="+s.name+" required="+r.required+
                    " inventoryAlternatives="+java.util.Arrays.toString(r.inventoryIds)+
                    " equippedProviderAlternatives="+java.util.Arrays.toString(r.equippedProviderIds));
        }
        return Check.accept(s,"ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell="+s.name+
            " runeConsumption=NOT_APPLIED_SERVER_RULE_UNKNOWN effects=ROUTER_ONLY");
    }

    String summary(){return "book="+book+" root="+book.root+" last="+last;}

    static final class Check {
        final boolean handled,accepted; final SpellDefinitionRepository.Spell spell; final String message;
        private Check(boolean handled,boolean accepted,SpellDefinitionRepository.Spell spell,String message){
            this.handled=handled;this.accepted=accepted;this.spell=spell;this.message=message;
        }
        static Check notSpell(){return new Check(false,false,null,"NOT_SPELL_WIDGET");}
        static Check rejectHandled(String m){return new Check(true,false,null,m);}
        static Check accept(SpellDefinitionRepository.Spell s,String m){return new Check(true,true,s,m);}
    }
}
