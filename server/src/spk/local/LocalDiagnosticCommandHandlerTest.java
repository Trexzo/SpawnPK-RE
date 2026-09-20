package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDiagnosticCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            LocalDiagnosticCommandHandler h=new LocalDiagnosticCommandHandler(
                world,
                player.equipment(),
                player.movement(),
                player.prayers(),
                player.magic(),
                player.combatStyles(),
                new NativeItemLibraryService()
            );

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

            if(!h.handle(new String[]{"worldauth"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("worldauth not handled");
            if(!h.handle(new String[]{"collisionauth"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("collisionauth not handled");
            if(!h.handle(new String[]{"prayerinfo"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("prayerinfo not handled");
            if(!h.handle(new String[]{"magicinfo"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("magicinfo not handled");
            if(!h.handle(new String[]{"styleinfo"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("styleinfo not handled");
            if(!h.handle(new String[]{"engine"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("engine not handled");

            int before=wire.size();
            if(!h.handle(new String[]{"equipstr","-1"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("equipstr not handled");
            if(wire.size()<=before)throw new AssertionError("equipstr did not emit fail-closed reset packet");

            if(h.handle(new String[]{"regionload","12850"},w,"[diag-test] ","opensrc","localtest",true,null))
                throw new AssertionError("mutating regionload must stay outside diagnostic handler");

            System.out.println("LOCAL_DIAGNOSTIC_COMMAND_HANDLER_PASS diagnosticsHandled=true equipstrReset=true mutatingCommandRejected=true");
        }finally{
            world.close();
        }
    }
}
