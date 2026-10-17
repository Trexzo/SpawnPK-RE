package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class PetFollowTransportAtomicityTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SEED=
        new int[]{631,632,633,634};

    public static void main(String[] args)throws Exception{
        assertQueueRetractPreservesFollowState();
        assertQueueRetractPreservesReanchorState();
        assertDirectFailureRestoresSemanticState();

        System.out.println(
            "PET_FOLLOW_TRANSPORT_ATOMICITY_PASS "+
            "queueRetractPreservesState=true "+
            "retryCommitsOnce=true "+
            "reanchorRetractPreservesState=true "+
            "directFailureRestoresState=true"
        );
    }

    private static void assertQueueRetractPreservesFollowState()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "follow-retract"
            );

        try{
            fixture.spawnMain();
            fixture.npcs.onOwnerRouteReplaced();

            WorldNpc before=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            int startX=before.x();
            int startY=before.y();

            fixture.movement.enterTransientRegion(
                startX+3,
                startY,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            StateSnapshot semantic=
                snapshot(
                    fixture
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    fixture.queue,
                    QUEUE_CAPACITY
                );

            String retracted;

            try{
                retracted=
                    fixture.npcs.tickFollow(
                        fixture.movement,
                        fixture.writer
                    );
            }finally{
                pressure.release();
            }

            if(retracted==null||
               !retracted.contains(
                   "RETRACTED_RETRYABLE"))
                throw new AssertionError(
                    "queue pressure did not retract follower publication: "+
                    retracted
                );

            assertState(
                fixture,
                semantic,
                "ordinary retract"
            );

            if(fixture.writer.terminal())
                throw new AssertionError(
                    "queue-retracted follower writer became terminal"
                );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "queue-retracted follower emitted bytes="+
                    fixture.queue.queuedBytes()
                );

            String committed=
                fixture.npcs.tickFollow(
                    fixture.movement,
                    fixture.writer
                );

            if(committed==null||
               committed.contains(
                   "RETRACTED_RETRYABLE"))
                throw new AssertionError(
                    "follower retry did not commit: "+
                    committed
                );

            WorldNpc after=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            if(after.x()!=startX+2||
               after.y()!=startY)
                throw new AssertionError(
                    "follower retry did not commit exactly one route transition result="+
                    after.x()+","+after.y()+
                    " expected="+(startX+2)+","+startY
                );

            int committedX=after.x();
            int committedY=after.y();

            String settled=
                fixture.npcs.tickFollow(
                    fixture.movement,
                    fixture.writer
                );

            if(settled!=null)
                throw new AssertionError(
                    "already-settled follower committed twice: "+
                    settled
                );

            WorldNpc afterSettled=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            if(afterSettled.x()!=committedX||
               afterSettled.y()!=committedY)
                throw new AssertionError(
                    "settled follower moved after exact retry commit"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertQueueRetractPreservesReanchorState()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "reanchor-retract"
            );

        try{
            fixture.spawnMainAndMini();
            fixture.npcs.onOwnerRouteReplaced();

            WorldNpc main=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            fixture.movement.enterTransientRegion(
                main.x()+10,
                main.y(),
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            StateSnapshot semantic=
                snapshot(
                    fixture
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    fixture.queue,
                    QUEUE_CAPACITY
                );

            String retracted;

            try{
                retracted=
                    fixture.npcs.tickFollow(
                        fixture.movement,
                        fixture.writer
                    );
            }finally{
                pressure.release();
            }

            if(retracted==null||
               !retracted.contains(
                   "RETRACTED_RETRYABLE"))
                throw new AssertionError(
                    "queue pressure did not retract reanchor: "+
                    retracted
                );

            assertState(
                fixture,
                semantic,
                "reanchor retract"
            );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "retracted reanchor emitted partial bytes="+
                    fixture.queue.queuedBytes()
                );

            String committed=
                fixture.npcs.tickFollow(
                    fixture.movement,
                    fixture.writer
                );

            if(committed==null||
               !committed.contains(
                   "REANCHOR"))
                throw new AssertionError(
                    "reanchor retry did not commit: "+
                    committed
                );

            WorldNpc movedMain=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            if(LocalSession.chebyshev(
                    movedMain.x(),
                    movedMain.y(),
                    fixture.movement.x(),
                    fixture.movement.y())>1)
                throw new AssertionError(
                    "successful reanchor did not restore owner adjacency"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertDirectFailureRestoresSemanticState()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "direct-failure"
            );

        try{
            fixture.spawnMain();
            fixture.npcs.onOwnerRouteReplaced();

            WorldNpc main=
                fixture.world.petNpcs()
                    .main(
                        fixture.owner.id()
                    );

            fixture.movement.enterTransientRegion(
                main.x()+3,
                main.y(),
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            StateSnapshot semantic=
                snapshot(
                    fixture
                );

            PrefixThenFailOutputStream out=
                new PrefixThenFailOutputStream();
            ServerPacketWriter direct=
                new ServerPacketWriter(
                    out,
                    new IsaacCipher(
                        new int[]{641,642,643,644}
                    )
                );

            boolean failed=false;

            try{
                fixture.npcs.tickFollow(
                    fixture.movement,
                    direct
                );
            }catch(IOException expected){
                failed=true;
            }

            if(!failed)
                throw new AssertionError(
                    "direct follower transport failure did not escape"
                );

            if(!direct.terminal())
                throw new AssertionError(
                    "direct follower transport failure did not terminal-latch writer"
                );

            if(out.bytes.size()!=1)
                throw new AssertionError(
                    "direct failure fixture did not publish exact partial prefix bytes="+
                    out.bytes.size()
                );

            assertState(
                fixture,
                semantic,
                "direct transport failure"
            );
        }finally{
            fixture.close();
        }
    }

    private static StateSnapshot snapshot(
        Fixture fixture
    )throws Exception{
        NpcEntity pet=
            fixture.npcs.pet();
        NpcEntity mini=
            fixture.npcs.miniPet();
        WorldNpc main=
            fixture.world.petNpcs()
                .main(
                    fixture.owner.id()
                );
        WorldNpc canonicalMini=
            fixture.world.petNpcs()
                .mini(
                    fixture.owner.id()
                );

        return new StateSnapshot(
            main==null?null:main.id,
            main==null?0:main.x(),
            main==null?0:main.y(),
            canonicalMini==null?null:canonicalMini.id,
            canonicalMini==null?0:canonicalMini.x(),
            canonicalMini==null?0:canonicalMini.y(),
            pet==null?0:pet.x,
            pet==null?0:pet.y,
            mini==null?0:mini.x,
            mini==null?0:mini.y,
            visibleScenes(
                fixture.npcs
            ),
            trailState(
                fixture.npcs,
                "ownerTrail"
            ),
            trailState(
                fixture.npcs,
                "miniTrail"
            ),
            booleanField(
                fixture.npcs,
                "hasMiniTrail"
            ),
            intField(
                fixture.npcs,
                "miniTrailX"
            ),
            intField(
                fixture.npcs,
                "miniTrailY"
            ),
            intField(
                fixture.npcs,
                "petDiscontinuityTicks"
            ),
            intField(
                fixture.npcs,
                "miniDiscontinuityTicks"
            )
        );
    }

    private static void assertState(
        Fixture fixture,
        StateSnapshot expected,
        String stage
    )throws Exception{
        StateSnapshot actual=
            snapshot(
                fixture
            );

        if(!expected.equalsState(
                actual))
            throw new AssertionError(
                stage+
                " changed follower semantic state\nexpected="+
                expected+
                "\nactual="+
                actual
            );
    }

    private static String visibleScenes(
        NpcRegistry npcs
    ){
        StringBuilder result=
            new StringBuilder();

        for(NpcEntity npc:npcs.snapshot()){
            if(result.length()>0)
                result.append(',');
            result.append(
                npc.sceneIndex
            );
        }

        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static String trailState(
        NpcRegistry npcs,
        String fieldName
    )throws Exception{
        Field field=
            NpcRegistry.class
                .getDeclaredField(
                    fieldName
                );
        field.setAccessible(true);

        ArrayDeque<int[]> trail=
            (ArrayDeque<int[]>)
                field.get(npcs);

        StringBuilder result=
            new StringBuilder();

        for(int[] tile:trail){
            if(result.length()>0)
                result.append(';');
            result.append(
                tile[0]
            ).append(
                ','
            ).append(
                tile[1]
            );
        }

        return result.toString();
    }

    private static boolean booleanField(
        NpcRegistry npcs,
        String fieldName
    )throws Exception{
        Field field=
            NpcRegistry.class
                .getDeclaredField(
                    fieldName
                );
        field.setAccessible(true);
        return field.getBoolean(
            npcs
        );
    }

    private static int intField(
        NpcRegistry npcs,
        String fieldName
    )throws Exception{
        Field field=
            NpcRegistry.class
                .getDeclaredField(
                    fieldName
                );
        field.setAccessible(true);
        return field.getInt(
            npcs
        );
    }

    private static final class StateSnapshot {
        final EntityId mainId;
        final int mainX;
        final int mainY;
        final EntityId miniId;
        final int miniX;
        final int miniY;
        final int localMainX;
        final int localMainY;
        final int localMiniX;
        final int localMiniY;
        final String visible;
        final String ownerTrail;
        final String miniTrail;
        final boolean hasMiniTrail;
        final int miniTrailX;
        final int miniTrailY;
        final int petDiscontinuityTicks;
        final int miniDiscontinuityTicks;

        StateSnapshot(
            EntityId mainId,
            int mainX,
            int mainY,
            EntityId miniId,
            int miniX,
            int miniY,
            int localMainX,
            int localMainY,
            int localMiniX,
            int localMiniY,
            String visible,
            String ownerTrail,
            String miniTrail,
            boolean hasMiniTrail,
            int miniTrailX,
            int miniTrailY,
            int petDiscontinuityTicks,
            int miniDiscontinuityTicks
        ){
            this.mainId=mainId;
            this.mainX=mainX;
            this.mainY=mainY;
            this.miniId=miniId;
            this.miniX=miniX;
            this.miniY=miniY;
            this.localMainX=localMainX;
            this.localMainY=localMainY;
            this.localMiniX=localMiniX;
            this.localMiniY=localMiniY;
            this.visible=visible;
            this.ownerTrail=ownerTrail;
            this.miniTrail=miniTrail;
            this.hasMiniTrail=hasMiniTrail;
            this.miniTrailX=miniTrailX;
            this.miniTrailY=miniTrailY;
            this.petDiscontinuityTicks=
                petDiscontinuityTicks;
            this.miniDiscontinuityTicks=
                miniDiscontinuityTicks;
        }

        boolean equalsState(
            StateSnapshot other
        ){
            return sameId(mainId,other.mainId)&&
                mainX==other.mainX&&
                mainY==other.mainY&&
                sameId(miniId,other.miniId)&&
                miniX==other.miniX&&
                miniY==other.miniY&&
                localMainX==other.localMainX&&
                localMainY==other.localMainY&&
                localMiniX==other.localMiniX&&
                localMiniY==other.localMiniY&&
                visible.equals(other.visible)&&
                ownerTrail.equals(other.ownerTrail)&&
                miniTrail.equals(other.miniTrail)&&
                hasMiniTrail==other.hasMiniTrail&&
                miniTrailX==other.miniTrailX&&
                miniTrailY==other.miniTrailY&&
                petDiscontinuityTicks==
                    other.petDiscontinuityTicks&&
                miniDiscontinuityTicks==
                    other.miniDiscontinuityTicks;
        }

        private static boolean sameId(
            EntityId first,
            EntityId second
        ){
            return first==null
                ?second==null
                :first.equals(
                    second
                );
        }

        @Override public String toString(){
            return "main="+mainId+"@"+mainX+","+mainY+
                " localMain="+localMainX+","+localMainY+
                " mini="+miniId+"@"+miniX+","+miniY+
                " localMini="+localMiniX+","+localMiniY+
                " visible="+visible+
                " ownerTrail="+ownerTrail+
                " miniTrail="+miniTrail+
                " hasMiniTrail="+hasMiniTrail+
                " miniSummary="+miniTrailX+","+miniTrailY+
                " discontinuity="+petDiscontinuityTicks+
                "/"+miniDiscontinuityTicks;
        }
    }

    private static final class Fixture {
        final World world;
        final WorldPlayer owner;
        final long generation;
        final MovementState movement;
        final NpcRegistry npcs;
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;

        Fixture(
            String username
        )throws Exception{
            world=
                World.isolatedForTest(
                    606L
                );
            owner=
                new WorldPlayer();
            generation=
                world.registerPlayer(
                    owner,
                    username
                );
            movement=owner.movement();
            npcs=
                new NpcRegistry(
                    new DevAuthorityWorkbench(),
                    world.petNpcs(),
                    owner.id()
                );
            queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        SEED.clone()
                    )
                );
        }

        void spawnMain()throws Exception{
            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(
                    22519
                );

            if(def==null)
                throw new AssertionError(
                    "missing pet 22519"
                );

            String spawned=
                npcs.spawnPet(
                    def,
                    movement,
                    writer
                );

            if(spawned==null||
               !spawned.startsWith(
                   "PET_SPAWN_OK"))
                throw new AssertionError(
                    "pet setup failed: "+
                    spawned
                );

            drain(
                queue
            );
        }

        void spawnMainAndMini()throws Exception{
            spawnMain();

            PetState state=
                new PetState();
            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(
                    22519
                );
            state.activate(
                def
            );

            MiniPetService minis=
                new MiniPetService();

            String configured=
                minis.configure(
                    23629,
                    state,
                    npcs,
                    movement,
                    writer
                );

            if(configured==null||
               configured.startsWith(
                   "REJECTED"))
                throw new AssertionError(
                    "mini setup failed: "+
                    configured
                );

            drain(
                queue
            );
        }

        void close(){
            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    generation
                );
            world.close();
        }
    }

    private static final class PrefixThenFailOutputStream
        extends OutputStream
    {
        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();

        @Override public void write(
            int value
        )throws IOException{
            bytes.write(
                value
            );
            throw new IOException(
                "forced follower direct failure"
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            if(length>0)
                bytes.write(
                    data[offset]
                );

            throw new IOException(
                "forced follower direct failure"
            );
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        queue.drainTo(
            new ByteArrayOutputStream(),
            Integer.MAX_VALUE
        );
    }
}
