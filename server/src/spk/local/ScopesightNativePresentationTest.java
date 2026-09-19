package spk.local;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Scopesight 28888->8330 + native forced-text SNIPE trigger + maintained stats. */
public final class ScopesightNativePresentationTest {
    public static void main(String[] args)throws Exception{
        PetDefinitionRepository.Def d=PetDefinitionRepository.get(ScopesightPetProfile.ITEM_ID);
        if(d==null||d.npcId!=8330||d.standAnim!=7416||d.walkAnim!=7411)
            throw new AssertionError("Scopesight pet mapping="+d);

        NpcEntity n=new NpcEntity(4,8330,3086,3495,true,28888,1);
        byte[] packet=NpcSyncEncoder.encode(Collections.singletonList(NpcSyncEncoder.Update.mask(n,NpcSyncEncoder.Mask.forceText("SNIPE"))),Collections.emptyList(),0,0);
        byte[] tail="SNIPE\n".getBytes(StandardCharsets.ISO_8859_1);
        boolean found=false;
        for(int i=0;i+tail.length<=packet.length;i++){
            boolean ok=true;for(int j=0;j<tail.length;j++)if(packet[i+j]!=tail[j]){ok=false;break;}if(ok){found=true;break;}
        }
        if(!found)throw new AssertionError("SNIPE newline payload absent");
        // The force-text mask itself is BE u16 0x0001 immediately before text.
        boolean maskSeen=false;
        for(int i=0;i+2+tail.length<=packet.length;i++)if(packet[i]==0&&packet[i+1]==1){
            boolean ok=true;for(int j=0;j<tail.length;j++)if(packet[i+2+j]!=tail[j]){ok=false;break;}if(ok){maskSeen=true;break;}
        }
        if(!maskSeen)throw new AssertionError("force-text mask 0x0001 absent");

        PlayerState p=new PlayerState();
        int changed=p.syncScopesightMaintenance(true);
        if(p.currentLevel(PlayerState.RANGED)!=114||p.currentLevel(PlayerState.MAGIC)!=109)throw new AssertionError("maintained stats");
        if((changed&(1<<PlayerState.RANGED))==0||(changed&(1<<PlayerState.MAGIC))==0)throw new AssertionError("changed mask");
        p.syncScopesightMaintenance(false);
        if(p.currentLevel(PlayerState.RANGED)!=99||p.currentLevel(PlayerState.MAGIC)!=99)throw new AssertionError("unwind maintained stats");

        if(ScopesightPetProfile.SNIPE_CHANCE_PCT!=5.0 || ScopesightPetProfile.PVM_MAGIC_RANGED_DAMAGE_BONUS_PCT!=25.0)
            throw new AssertionError("profile metadata");
        System.out.println("V58_SCOPESIGHT_NATIVE_PRESENTATION_PASS item=28888 npc=8330 stand=7416 walk=7411 forcedTextMask=0x0001 text=SNIPE nativeClientTrigger=true maintainedRange=114 maintainedMagic=109 combatModifiers=METADATA_ONLY");
    }
}
