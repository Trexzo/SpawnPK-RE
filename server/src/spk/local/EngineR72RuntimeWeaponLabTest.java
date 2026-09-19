package spk.local;

import java.io.ByteArrayOutputStream;

public final class EngineR72RuntimeWeaponLabTest {
    public static void main(String[] args)throws Exception{
        if(V913WeaponRuntimeAuthority.count()!=11)throw new AssertionError("runtime profile count drift "+V913WeaponRuntimeAuthority.count());
        if(V913WeaponRuntimeAuthority.directlyBasicAttackCount()!=10)throw new AssertionError("direct basic count drift "+V913WeaponRuntimeAuthority.directlyBasicAttackCount());
        int safe=0;for(V913WeaponRuntimeAuthority.Profile p:V913WeaponRuntimeAuthority.all())if(RuntimeWeaponPresentationLab.previewSafe(p))safe++;
        if(safe!=10)throw new AssertionError("preview-safe count drift "+safe);
        V913WeaponRuntimeAuthority.Profile spell=V913WeaponRuntimeAuthority.resolve(11791);
        if(RuntimeWeaponPresentationLab.previewSafe(spell))throw new AssertionError("spell action promoted into basic preview");
        V913WeaponRuntimeAuthority.Profile scorch=V913WeaponRuntimeAuthority.resolve(28860);
        if(scorch==null||scorch.attackAnimation!=15409||scorch.projectileId!=4079||!scorch.hasProjectileGeometry())throw new AssertionError("scorching runtime authority drift");
        if(!RuntimeWeaponPresentationLab.PROJECTILE_POLICY.contains("OTHERS_SUPPRESSED"))throw new AssertionError("projectile allowlist policy drift");

        NpcRegistry npcs=new NpcRegistry();MovementState movement=new MovementState();
        java.lang.reflect.Field vf=NpcRegistry.class.getDeclaredField("visible");vf.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.ArrayList<NpcEntity> visible=(java.util.ArrayList<NpcEntity>)vf.get(npcs);
        visible.add(new NpcEntity(NpcRegistry.PVM_DUMMY_INDEX,1,movement.x()+1,movement.y()));
        if(npcs.scene(NpcRegistry.PVM_DUMMY_INDEX)==null)throw new AssertionError("test dummy injection failed");

        int[] seed={7,11,13,17};ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(seed.clone()));
        V913WeaponRuntimeAuthority.Profile blood=V913WeaponRuntimeAuthority.resolve(25001);
        String result=RuntimeWeaponPresentationLab.preview(blood,npcs,null,null,w);w.flush();
        if(!result.startsWith("OK presentationOnly=true")||!result.contains("damage=NONE")||!result.contains(RuntimeWeaponPresentationLab.PROJECTILE_POLICY))throw new AssertionError("preview result boundary drift "+result);
        byte[] wire=out.toByteArray();IsaacCipher decode=new IsaacCipher(seed.clone());int pos=0;boolean saw81=false,saw174=false,saw117=false;
        while(pos<wire.length){int op=((wire[pos++]&255)-decode.nextInt())&255;if(op==81){saw81=true;if(pos+2>wire.length)throw new AssertionError("short varshort");int len=((wire[pos]&255)<<8)|(wire[pos+1]&255);pos+=2+len;}
            else if(op==174){saw174=true;pos+=6;}
            else if(op==117){saw117=true;pos+=15;}
            else throw new AssertionError("unexpected preview opcode "+op+" at "+(pos-1));
        }
        if(!saw81||!saw174||saw117)throw new AssertionError("wire policy drift 81="+saw81+" 174="+saw174+" 117="+saw117);
        System.out.println("V5172_ENGINE_R72_RUNTIME_WEAPON_LAB_PASS profiles=11 directBasic=10 previewSafe=10 player81=true exactSound174=true projectile117=falseOnLegacyNoSceneOverload=true damageMutation=false spellActionRejected=true");
    }
}
