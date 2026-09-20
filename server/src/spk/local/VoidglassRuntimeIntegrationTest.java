package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.net.Socket;

public final class VoidglassRuntimeIntegrationTest {
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void setPendingCommand(ClientPacketProbe p,String c)throws Exception{Field f=ClientPacketProbe.class.getDeclaredField("pendingCommand");f.setAccessible(true);f.set(p,c);}
    static void command(LocalSession s,ClientPacketProbe p,ServerPacketWriter w,String c)throws Exception{
        setPendingCommand(p,c);
        LocalPendingRequestDispatcher dispatcher=(LocalPendingRequestDispatcher)field(s,"pendingRequests");
        dispatcher.drain(p,w,"[voidglass-test] ");
    }
    public static void main(String[] args)throws Exception{
        LocalSession s=new LocalSession(new Socket(),true,true);
        MovementState movement=(MovementState)field(s,"movement");
        PetState ps=(PetState)field(s,"petState");
        NpcRegistry npcs=(NpcRegistry)field(s,"npcs");
        DevAuthorityWorkbench dev=(DevAuthorityWorkbench)field(s,"dev");
        VoidglassPetState vg=(VoidglassPetState)field(s,"voidglass");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(bytes,new IsaacCipher(new int[]{11,22,33,44}));
        ClientPacketProbe probe=new ClientPacketProbe(new ByteArrayInputStream(new byte[0]),new IsaacCipher(new int[]{0,0,0,0}),"[voidglass-test] ");

        PetDefinitionRepository.Def d=PetDefinitionRepository.get(VoidglassPetProfile.BASE_ITEM_ID);
        if(d==null||d.npcId!=VoidglassPetProfile.BASE_NPC_ID)throw new AssertionError("base Vasa mapping unavailable");
        String spawn=npcs.spawnPet(d,movement,w);if(!spawn.startsWith("PET_SPAWN_OK"))throw new AssertionError(spawn);ps.activate(d);
        int before=bytes.size();
        command(s,probe,w,"voidglass on");
        if(!vg.active()||dev.petParticleSelector()==null||dev.petParticleSelector().intValue()!=6)throw new AssertionError("voidglass on");
        if(npcs.pet()==null||npcs.pet().definitionId!=3701)throw new AssertionError("pet identity changed");
        if(bytes.size()<=before)throw new AssertionError("on emitted no packets");

        command(s,probe,w,"voidglass fx 8");
        if(dev.petParticleSelector()==null||dev.petParticleSelector().intValue()!=8||vg.selectedParticle()!=8)throw new AssertionError("fx8");
        int procBefore=bytes.size();
        command(s,probe,w,"voidglass proc");
        if(vg.procCount()!=1||bytes.size()<=procBefore)throw new AssertionError("proc");
        command(s,probe,w,"voidglass off");
        if(vg.active()||dev.petParticleSelector()!=null)throw new AssertionError("off did not restore AUTO");
        if(npcs.pet()==null||npcs.pet().definitionId!=3701)throw new AssertionError("off removed base pet");
        System.out.println("VOIDGLASS_RUNTIME_INTEGRATION_PASS basePetRetained=true fx6=true fx8=true proc=true restoreAuto=true bytes="+bytes.size());
    }
}