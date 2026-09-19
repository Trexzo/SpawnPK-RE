package spk.local;

import java.io.IOException;

/**
 * Exact-current S2C250 VAR_BYTE state publishers imported from SpawnPK-Authority R22.
 * Presentation/state only: no death policy, shop economy, recipe, or consumption rules.
 */
final class AuthorityR22ScriptPacket250Publisher {
    private AuthorityR22ScriptPacket250Publisher(){}

    private static PacketPayloadWriter p(int subtype){
        return new PacketPayloadWriter().putU16BE(subtype);
    }
    static void send(ServerPacketWriter w,byte[] payload)throws IOException{ w.varByte(250,payload); }

    // subtype 9: Items Kept on Death overlay marks. Integer is a slot index, not item id.
    static byte[] deathClear(){ return p(9).putU8(0).toByteArray(); }
    static byte[] deathMarkSlot(int slotIndex){
        if(slotIndex<0||slotIndex>0xffff)throw new IllegalArgumentException("slotIndex="+slotIndex);
        return p(9).putU8(1).putU16BE(slotIndex).toByteArray();
    }

    // subtype 17: native shop tab header/state only.
    static byte[] shopReset(){ return p(17).putU8(0).toByteArray(); }
    static byte[] shopRebuild(int selectedIndex,String... names){
        if(names==null)names=new String[0];
        if(names.length>5)throw new IllegalArgumentException("tabCount="+names.length);
        if(selectedIndex<0||selectedIndex>4)throw new IllegalArgumentException("selectedIndex="+selectedIndex);
        if(names.length>0 && selectedIndex>=names.length)throw new IllegalArgumentException("selectedIndex="+selectedIndex+" tabCount="+names.length);
        PacketPayloadWriter b=p(17).putU8(1).putU8(selectedIndex).putU8(names.length);
        for(String name:names)b.putStringNl(name==null?"":name);
        return b.toByteArray();
    }
    static byte[] shopSelect(int selectedIndex){
        if(selectedIndex<0||selectedIndex>4)throw new IllegalArgumentException("selectedIndex="+selectedIndex);
        return p(17).putU8(2).putU8(selectedIndex).toByteArray();
    }

    // subtype 35: Make/quantity UI presentation state only.
    static byte[] makePreviewValue(int optionIndex,int value){
        option(optionIndex); return p(35).putU8(0).putU8(optionIndex).putI32BE(value).toByteArray();
    }
    static byte[] makeQuantitySelected(int quantityIndex){
        if(quantityIndex<0||quantityIndex>4)throw new IllegalArgumentException("quantityIndex="+quantityIndex);
        return p(35).putU8(1).putU8(quantityIndex).toByteArray();
    }
    static byte[] makeHeaderDefault(){ return p(35).putU8(2).putU8(0).toByteArray(); }
    static byte[] makeHeader(String title,String subtitle){
        PacketPayloadWriter b=p(35).putU8(2).putU8(1).putStringNl(title==null?"":title);
        if(subtitle==null)b.putU8(0); else b.putU8(1).putStringNl(subtitle);
        return b.toByteArray();
    }
    static byte[] makeOptionDetail(int optionIndex,String detail){
        option(optionIndex); return p(35).putU8(3).putU8(optionIndex).putStringNl(detail==null?"":detail).toByteArray();
    }
    static byte[] makeOptionResource(int optionIndex,String resource){
        option(optionIndex); return p(35).putU8(4).putU8(optionIndex).putStringNl(resource==null?"":resource).toByteArray();
    }
    private static void option(int optionIndex){ if(optionIndex<0||optionIndex>4)throw new IllegalArgumentException("optionIndex="+optionIndex); }
}
