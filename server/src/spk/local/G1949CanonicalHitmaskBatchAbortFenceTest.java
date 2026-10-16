package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * G19.4.9/50: a canonical attack calls NpcRegistry.sendMask under the
 * same source-mask batch ownership as every other shared NPC publisher.
 * The spectator event must not escape before source packet commit.
 */
public final class G1949CanonicalHitmaskBatchAbortFenceTest {
    static void run(boolean abortPacketCommit)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer source=new WorldPlayer();
        WorldPlayer viewer=new WorldPlayer();
        long sourceGen=-1L;
        long viewerGen=-1L;
        ServerPacketWriter sourceWriter=null;
        ServerPacketWriter viewerWriter=null;
        boolean sourceBound=false;
        boolean viewerBound=false;
        try{
            sourceGen=world.registerPlayer(
                source,abortPacketCommit?"g1949-source":"g1950-source");
            viewerGen=world.registerPlayer(
                viewer,abortPacketCommit?"g1949-viewer":"g1950-viewer");
            source.movement().restoreAccountState(
                false,100,3087,3495,0);
            viewer.movement().restoreAccountState(
                false,100,3089,3495,0);

            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            world.npcLifecycle().register(
                npc,30,"CUSTOM_LOCALLAB_G1949_MASK_BATCH");

            OutboundPacketQueue sourceQueue=new OutboundPacketQueue(1024);
            OutboundPacketQueue viewerQueue=new OutboundPacketQueue(1024);
            sourceWriter=new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(new int[]{3105,3106,3107,3108}));
            viewerWriter=new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(new int[]{3205,3206,3207,3208}));
            NpcRegistry sourceNpcs=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),source.id());
            NpcRegistry viewerNpcs=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),viewer.id());

            NpcEntity sourceNpc=sourceNpcs.spawnMirroredNpc(
                def,3088,3495,null,source.movement(),sourceWriter);
            // Force spectator scene translation: reserve an unrelated
            // noncanonical local view before its canonical NPC projection.
            NpcEntity viewerFiller=viewerNpcs.spawnMirroredNpc(
                def,3089,3495,null,viewer.movement(),viewerWriter);
            NpcEntity viewerNpc=viewerNpcs.spawnMirroredNpc(
                def,3088,3495,null,viewer.movement(),viewerWriter);
            check(sourceNpc!=null&&viewerFiller!=null&&
                    viewerNpc!=null&&
                    sourceNpc.sceneIndex!=viewerNpc.sceneIndex&&
                    sourceNpcs.scene(sourceNpc.sceneIndex)==sourceNpc&&
                    viewerNpcs.scene(viewerFiller.sceneIndex)==viewerFiller&&
                    viewerNpcs.scene(viewerNpc.sceneIndex)==viewerNpc,
                "independent projections have distinct scene indices");
            sourceNpc.bindCanonicalId(npc.id);
            viewerNpc.bindCanonicalId(npc.id);
            sourceQueue.drainTo(new ByteArrayOutputStream(),1<<20);
            viewerQueue.drainTo(new ByteArrayOutputStream(),1<<20);

            SharedNpcWorldRelay.register(
                sourceWriter,world,source,sourceNpcs,source.movement());
            sourceBound=true;
            SharedNpcWorldRelay.register(
                viewerWriter,world,viewer,viewerNpcs,viewer.movement());
            viewerBound=true;

            NpcActionClientRequest decoded=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        sourceNpc.sceneIndex,
                        new int[]{1949,1950,1951,1952});
            check(decoded.action().opcode==72&&
                    decoded.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "current-client opcode72 decoded");

            final long currentSourceGen=sourceGen;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,source,()->currentSourceGen,
                    source.equipment(),source.combatStyles(),sourceNpcs);

            check(SharedNpcWorldRelay.beginSourceMaskBatch(sourceWriter),
                "exact source mask batch owned");
            sourceWriter.beginBatch();

            LocalCanonicalNpcAttackHandler.Result hit=
                handler.handle(decoded.action(),sourceNpc,sourceWriter);
            check(hit.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    hit.appliedDamage==10&&hit.acceptedHit!=null&&
                    world.npcLifecycle().get(npc.id).hitpoints==20&&
                    handler.nextAllowedAttackTick()>world.clock().tick()&&
                    world.npcPresentationEvents().pendingFor(
                        viewer.id(),viewerGen,
                        System.currentTimeMillis()).isEmpty()&&
                    sourceQueue.queuedBytes()==0,
                "HP commits while staged hitmask/relay remain uncommitted");

            OutboundPacketQueue.BatchReservation pressure=null;
            if(abortPacketCommit)
                pressure=OutboundPacketQueue.reserveBatch(
                    sourceQueue,1024);
            try{
                if(abortPacketCommit){
                    boolean denied=false;
                    try{
                        LocalSession.endWorldTickBatch(sourceWriter);
                    }catch(IOException expected){
                        denied=true;
                    }
                    check(denied,"bounded source packet queue rejected batch");
                    if(pressure!=null){
                        pressure.release();
                        pressure=null;
                    }
                    check(SharedNpcWorldRelay.abortSourceMaskBatch(
                            sourceWriter)==1&&
                            sourceQueue.queuedBytes()==0&&
                            world.npcPresentationEvents().pendingFor(
                                viewer.id(),viewerGen,
                                System.currentTimeMillis()).isEmpty(),
                        "abort drops remote presentation, not committed HP");
                }else{
                    LocalSession.endWorldTickBatch(sourceWriter);
                    check(sourceQueue.queuedBytes()>0&&
                            world.npcPresentationEvents().pendingFor(
                                viewer.id(),viewerGen,
                                System.currentTimeMillis()).isEmpty(),
                        "source packet committed before semantic relay");
                    check(SharedNpcWorldRelay.commitSourceMaskBatch(
                            sourceWriter)==1,
                        "exactly one committed source relay");
                    List<WorldNpcPresentationEvents.Event> events=
                        world.npcPresentationEvents().pendingFor(
                            viewer.id(),viewerGen,
                            System.currentTimeMillis());
                    check(events.size()==1&&
                            events.get(0).target.kind==
                                WorldNpcPresentationEvents.Target.CANONICAL&&
                            events.get(0).target.canonicalId.equals(npc.id),
                        "canonical mask targets independent viewer, not source scene");
                    SharedNpcWorldRelay.flushAfterPlayer81(viewerWriter);
                    check(viewerQueue.queuedBytes()>0&&
                            world.npcPresentationEvents().pendingFor(
                                viewer.id(),viewerGen,
                                System.currentTimeMillis()).isEmpty(),
                        "spectator consumed committed hitmask exactly once");
                }
            }finally{
                if(pressure!=null)pressure.release();
            }

            check(world.pvmRecords().receiptCount()==0,
                "packet batch does not invent kill-credit authority");
        }finally{
            if(viewerBound)SharedNpcWorldRelay.unregister(viewerWriter);
            if(sourceBound)SharedNpcWorldRelay.unregister(sourceWriter);
            if(viewerGen>=0L&&world.players().owns(viewer,viewerGen))
                world.unregisterPlayer(viewer,viewerGen);
            if(sourceGen>=0L&&world.players().owns(source,sourceGen))
                world.unregisterPlayer(source,sourceGen);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1949_CANONICAL_HITMASK_BATCH_ABORT_FENCE_PASS"+
            " exactDecodedAttack=true"+
            " sourceBatchRejected=true"+
            " authoritativeHPCommitted=true"+
            " noPhantomRemoteMask=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.4.9/50 "+detail);
    }
    private G1949CanonicalHitmaskBatchAbortFenceTest(){}
}
