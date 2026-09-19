package spk.local;

import java.io.*;
import java.util.*;

/** Exact current-client native equipment/death interface publisher. */
final class NativeEquipmentDeathUi {
    static final int EQUIPMENT_STATS_BUTTON=27653;
    static final int DEATH_BUTTON=27654;
    static final int EQUIPMENT_STATS_ROOT=15106;
    static final int DEATH_ROOT=17100;
    static final int DEATH_GRID=17108;
    private NativeEquipmentDeathUi(){}

    static String openEquipmentStats(ServerPacketWriter w,EquipmentState equipment)throws IOException{
        w.fixed(97,BootstrapPackets.interface97(EQUIPMENT_STATS_ROOT));
        // Exact widgets; numerical base profiles are server-fed via equipstr and are not statically recoverable.
        int[] base={1675,1676,1677,1678,1679,1680,1681,1682,1683,1684,1686,1687};
        for(int id:base)w.varShort(126,BootstrapPackets.widgetText126(id,"N/A"));
        w.varShort(126,BootstrapPackets.widgetText126(15115,"Drop rate bonus: N/A"));
        w.varShort(126,BootstrapPackets.widgetText126(15116,"Blood money bonus: N/A"));
        w.varShort(126,BootstrapPackets.widgetText126(15117,"Range strength: N/A"));
        w.varShort(126,BootstrapPackets.widgetText126(15118,"Magic damage: N/A"));
        w.varShort(126,BootstrapPackets.widgetText126(15119,"Risk value: unresolved server authority"));
        return "OPENED_NATIVE_ROOT_15106 numericBaseProfiles=UNRESOLVED_SERVER_FED_EQUIPSTR weapon="+equipment.weapon();
    }

    static String openDeathPreview(ServerPacketWriter w,BankState bank,EquipmentState equipment)throws IOException{
        ArrayList<Entry> keep=new ArrayList<>(),standard=new ArrayList<>(),loss=new ArrayList<>();
        for(int i=0;i<EquipmentState.EQUIPMENT_SLOTS;i++)add(keep,standard,loss,equipment.itemAt(i),equipment.quantityAt(i));
        for(int i=0;i<bank.inventoryCapacity();i++){BankState.Stack s=bank.inventoryAt(i);if(s!=null)add(keep,standard,loss,s.itemId,s.qty);}
        ArrayList<Entry> all=new ArrayList<>(keep.size()+standard.size()+loss.size());all.addAll(keep);all.addAll(standard);all.addAll(loss);
        int cap=Math.min(64,all.size());int[] ids=new int[cap],qty=new int[cap];for(int i=0;i<cap;i++){ids[i]=all.get(i).item;qty[i]=all.get(i).qty;}
        w.fixed(97,BootstrapPackets.interface97(DEATH_ROOT));
        w.varShort(53,BootstrapPackets.itemContainer53(DEATH_GRID,ids,qty));
        // Exact script-packet transport: clear marks, then mark slots that have explicit auto-keep authority.
        w.varByte(250,new byte[]{0,9,0});
        for(int i=0;i<Math.min(keep.size(),cap);i++)w.varByte(250,new byte[]{0,9,1,(byte)(i>>>8),(byte)i});
        w.varShort(126,BootstrapPackets.widgetText126(17109,"Explicit auto-keep: "+keep.size()));
        w.varShort(126,BootstrapPackets.widgetText126(17113,"Explicit auto-loss: "+loss.size()));
        w.varShort(126,BootstrapPackets.widgetText126(17120,"Standard items: "+standard.size()));
        w.varShort(126,BootstrapPackets.widgetText126(17121,"Standard keep/loss order is server-owned"));
        w.varShort(126,BootstrapPackets.widgetText126(17122,"and is not guessed by LocalLab."));
        w.varShort(126,BootstrapPackets.widgetText126(17123,"Risk value: unresolved"));
        w.varShort(126,BootstrapPackets.widgetText126(17124,"Explicit policy rows are exact current i.bin."));
        for(int id=17125;id<=17129;id++)w.varShort(126,BootstrapPackets.widgetText126(id,""));
        return "OPENED_NATIVE_ROOT_17100 autoKeep="+keep.size()+" autoLoss="+loss.size()+" standardUnresolved="+standard.size()+" displayed="+cap;
    }

    private static void add(List<Entry> keep,List<Entry> standard,List<Entry> loss,int item,int qty){
        if(item<0||qty<=0)return;DeathPolicyRepository.Kind k=DeathPolicyRepository.get(item).kind;Entry e=new Entry(item,qty);if(k==DeathPolicyRepository.Kind.AUTO_KEEP_EXPLICIT)keep.add(e);else if(k==DeathPolicyRepository.Kind.AUTO_LOSS_EXPLICIT)loss.add(e);else standard.add(e);
    }
    private static final class Entry{final int item,qty;Entry(int i,int q){item=i;qty=q;}}
}
