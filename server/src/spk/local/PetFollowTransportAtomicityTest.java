package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class PetFollowTransportAtomicityTest {
    private static final int[] SEED={41,42,43,44};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer owner=new WorldPlayer();
            world.registerPlayer(owner,"pet-follow-transport");
            MovementState movement=owner.movement();
            NpcRegistry npcs=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),
                owner.id()
            );
            ServerPacketWriter setup=new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(SEED)
            );
            PetDefinitionRepository.Def def=PetDefinitionRepository.get(22519);
            if(def==null)throw new AssertionError("missing pet 22519");
            String spawn=npcs.spawnPet(def,movement,setup);
            if(!spawn.startsWith("PET_SPAWN_OK"))throw new AssertionError(spawn);

            npcs.onOwnerRouteReplaced();
            WorldNpc canonical=requireCanonical(world,owner);
            int originX=canonical.x(), originY=canonical.y();
            movement.enterTransientRegion(
                originX+3,originY,0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            OutboundPacketQueue queue=new OutboundPacketQueue(1024);
            ServerPacketWriter queued=new ServerPacketWriter(
                queue,new IsaacCipher(SEED)
            );
            queue.offer(new byte[1020]);

            Snapshot beforeWalk=snapshot(world,owner,npcs,movement);
            String retracted=npcs.tickFollow(movement,queued);
            if(!retracted.contains("RETRACTED_RETRYABLE"))
                throw new AssertionError("walk did not retract: "+retracted);
            if(queued.terminal())
                throw new AssertionError("retracted queue writer became terminal");
            assertSame(beforeWalk,snapshot(world,owner,npcs,movement),"walk retract");

            drain(queue);
            String retry=npcs.tickFollow(movement,queued);
            if(retry==null||retry.contains("RETRACTED_RETRYABLE"))
                throw new AssertionError("walk retry did not commit: "+retry);

            canonical=requireCanonical(world,owner);
            if(canonical.x()!=originX+2||canonical.y()!=originY)
                throw new AssertionError(
                    "walk retry did not commit once actual="+canonical.x()+","+canonical.y()
                );
            if(npcs.pet().x!=canonical.x()||npcs.pet().y!=canonical.y())
                throw new AssertionError("walk retry local/canonical divergence");

            drain(queue);
            movement.enterTransientRegion(
                canonical.x()+12,canonical.y(),0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );
            npcs.onOwnerRouteReplaced();
            queue.offer(new byte[1020]);

            Snapshot beforeReanchor=snapshot(world,owner,npcs,movement);
            String snapRetracted=npcs.devSnapToOwner(movement,queued);
            if(!snapRetracted.contains("RETRACTED_RETRYABLE"))
                throw new AssertionError("reanchor did not retract: "+snapRetracted);
            assertSame(
                beforeReanchor,
                snapshot(world,owner,npcs,movement),
                "reanchor retract"
            );
            if(npcs.scene(NpcRegistry.PET_INDEX)!=beforeReanchor.petRef)
                throw new AssertionError("reanchor retract changed visible identity");

            drain(queue);
            String snapRetry=npcs.devSnapToOwner(movement,queued);
            if(!snapRetry.startsWith("PET_TELEPORT_TO_OWNER"))
                throw new AssertionError("reanchor retry failed: "+snapRetry);

            canonical=requireCanonical(world,owner);
            if(npcs.pet().x!=canonical.x()||
               npcs.pet().y!=canonical.y()||
               LocalSession.chebyshev(
                   canonical.x(),canonical.y(),
                   movement.x(),movement.y()
               )>1)
                throw new AssertionError("reanchor retry did not commit adjacency");

            movement.enterTransientRegion(
                canonical.x()+3,canonical.y(),0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );
            npcs.onOwnerRouteReplaced();
            Snapshot beforeDirect=snapshot(world,owner,npcs,movement);

            ServerPacketWriter failing=new ServerPacketWriter(
                new FailingOutput(),
                new IsaacCipher(SEED)
            );
            boolean failed=false;
            try{
                npcs.tickFollow(movement,failing);
            }catch(IOException expected){
                failed=true;
            }
            if(!failed)throw new AssertionError("direct transport did not fail");
            if(!failing.terminal())
                throw new AssertionError("direct transport did not terminal-latch writer");
            assertSame(
                beforeDirect,
                snapshot(world,owner,npcs,movement),
                "direct failure"
            );

            System.out.println(
                "PET_FOLLOW_TRANSPORT_ATOMICITY_PASS "+
                "queueRetractPreservesState=true "+
                "retryCommitsOnce=true "+
                "reanchorRetractPreservesState=true "+
                "directFailureRestoresState=true"
            );
        }finally{
            world.close();
        }
    }

    private static WorldNpc requireCanonical(World world,WorldPlayer owner){
        WorldNpc n=world.petNpcs().main(owner.id());
        if(n==null)throw new AssertionError("canonical main pet missing");
        return n;
    }

    private static Snapshot snapshot(
        World world,
        WorldPlayer owner,
        NpcRegistry npcs,
        MovementState movement
    ){
        WorldNpc canonical=requireCanonical(world,owner);
        NpcEntity pet=npcs.pet();
        if(pet==null)throw new AssertionError("local pet missing");
        return new Snapshot(
            canonical.id,canonical.x(),canonical.y(),
            pet,pet.x,pet.y,npcs.visibleCount(),npcs.devInfo(movement)
        );
    }

    private static void assertSame(Snapshot a,Snapshot b,String label){
        if(!a.canonicalId.equals(b.canonicalId)||
           a.canonicalX!=b.canonicalX||
           a.canonicalY!=b.canonicalY||
           a.petRef!=b.petRef||
           a.petX!=b.petX||
           a.petY!=b.petY||
           a.visibleCount!=b.visibleCount||
           !a.devInfo.equals(b.devInfo))
            throw new AssertionError(label+" changed state before="+a+" after="+b);
    }

    private static void drain(OutboundPacketQueue queue)throws IOException{
        queue.drainTo(new ByteArrayOutputStream(),Integer.MAX_VALUE);
    }

    private static final class Snapshot {
        final EntityId canonicalId;
        final int canonicalX,canonicalY,petX,petY,visibleCount;
        final NpcEntity petRef;
        final String devInfo;

        Snapshot(
            EntityId id,int cx,int cy,NpcEntity pet,
            int px,int py,int visible,String info
        ){
            canonicalId=id;canonicalX=cx;canonicalY=cy;
            petRef=pet;petX=px;petY=py;visibleCount=visible;devInfo=info;
        }

        @Override public String toString(){
            return "Snapshot{"+canonicalId+" canonical="+canonicalX+","+canonicalY+
                " local="+petX+","+petY+" visible="+visibleCount+" info="+devInfo+"}";
        }
    }

    private static final class FailingOutput extends OutputStream {
        @Override public void write(int value)throws IOException{
            throw new IOException("deterministic follower transport failure");
        }
        @Override public void write(byte[] data,int offset,int length)throws IOException{
            throw new IOException("deterministic follower transport failure");
        }
    }

    private PetFollowTransportAtomicityTest(){}
}
