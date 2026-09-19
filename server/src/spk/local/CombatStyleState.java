package spk.local;

import java.io.*;

final class CombatStyleState {
    private int value=0;

    int value(){return value;}
    CombatStyleRepository.Style current(int root){
        CombatStyleRepository.Style s=CombatStyleRepository.byValue(root,value);
        return s!=null?s:CombatStyleRepository.defaultForRoot(root);
    }

    String click(int root,int widget,ServerPacketWriter w)throws IOException{
        CombatStyleRepository.Style s=CombatStyleRepository.byWidget(root,widget);
        if(s==null)return "NOT_STYLE_WIDGET root="+root+" widget="+widget;
        value=s.value;
        w.fixed(36,BootstrapPackets.config36(CombatStyleRepository.VARP,value));
        return "COMBAT_STYLE_SELECTED "+s+" clientPredictionConfirmed=true combatMath=UNCHANGED_SERVER_FORMULAS_UNKNOWN";
    }

    String reconcileRoot(int root,ServerPacketWriter w)throws IOException{
        CombatStyleRepository.Style s=CombatStyleRepository.byValue(root,value);
        if(s!=null)return "STYLE_RETAINED "+s;
        s=CombatStyleRepository.defaultForRoot(root);
        if(s==null)return "STYLE_ROOT_UNMAPPED root="+root+" value="+value;
        value=s.value;
        w.fixed(36,BootstrapPackets.config36(CombatStyleRepository.VARP,value));
        return "STYLE_RECONCILED "+s;
    }

    String summary(int root){
        CombatStyleRepository.Style s=current(root);
        return s==null?"root="+root+" value="+value+" unresolved":"root="+root+" value="+value+" "+s;
    }
}
