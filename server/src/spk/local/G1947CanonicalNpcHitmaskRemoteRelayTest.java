package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * G19.4.7/48: canonical NPC HP is World-shared. An admitted attack
 * must publish a source hitmark AND one semantic mask for another
 * exact current viewer, never just a session-local packet.
 */
public final class G1947CanonicalNpcHitmaskRemoteRelayTest {
    static void run(boolean failedAttackerWriter)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        WorldPlayer spectator=new WorldPlayer();
        long attackerGeneration=-1L;
        long spectatorGeneration=-1L;
        ServerPacketWriter attackerWriter=null;
        ServerPacketWriter spectatorWriter=null;
        boolean attackerBound=false;
        boolean spectatorBound=false;
        try{
            attackerGeneration=world.registerPlayer(
                attacker,failedAttackerWriter?
                    "g1948-source-failed":"g1947-source-success");
            spectatorGeneration=world.registerPlayer(
                spectator,failedAttackerWriter?
                    "g1948-viewer":"g1947-viewer");
            attacker.movement().restoreAccountState(
                false,100,3087,3495,0);
            spectator.movement().restoreAccountState(
                false,100,3089,3495,0);

            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc npc=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,30,"CUSTOM_LOCALLAB_G1947_REMOTE_MASK");

            NpcRegistry sourceNpcs=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),attacker.id());
            NpcRegistry viewerNpcs=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),spectator.id());
            ByteArrayOutputStream sourceOutput=new ByteArrayOutputStream();
            ByteArrayOutputStream viewerOutput=new ByteArrayOutputStream();
            attackerWriter=
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(sourceOutput);
            spectatorWriter=
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(viewerOutput);

            NpcEntity attackView=sourceNpcs.spawnMirroredNpc(
                definition,3088,3495,null,attacker.movement(),
                attackerWriter);
            // Consume the spectator's first free scene slot with a
            // local noncanonical projection so its canonical target must
            // use a DIFFERENT numeric index than the attacker's view.
            NpcEntity spectatorFiller=viewerNpcs.spawnMirroredNpc(
                definition,3089,3495,null,spectator.movement(),
                spectatorWriter);
            NpcEntity spectatorView=viewerNpcs.spawnMirroredNpc(
                definition,3088,3495,null,spectator.movement(),
                spectatorWriter);
            check(attackView!=null&&spectatorFiller!=null&&
                    spectatorView!=null&&
                    attackView.sceneIndex!=spectatorView.sceneIndex&&
                    sourceNpcs.scene(attackView.sceneIndex)==attackView&&
                    viewerNpcs.scene(spectatorFiller.sceneIndex)==
                        spectatorFiller&&
                    viewerNpcs.scene(spectatorView.sceneIndex)==spectatorView,
                "independent projections use distinct scene indices");
            attackView.bindCanonicalId(npc.id);
            spectatorView.bindCanonicalId(npc.id);

            SharedNpcWorldRelay.register(
                attackerWriter,world,attacker,sourceNpcs,
                attacker.movement());
            attackerBound=true;
            SharedNpcWorldRelay.register(
                spectatorWriter,world,spectator,viewerNpcs,
                spectator.movement());
            spectatorBound=true;
            sourceOutput.reset();
            viewerOutput.reset();

            long now=System.currentTimeMillis();
            check(world.npcPresentationEvents()
                    .pendingFor(spectator.id(),spectatorGeneration,now)
                    .isEmpty(),
                "fixture starts with no spectator mask");
            NpcActionClientRequest exact=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        attackView.sceneIndex,
                        new int[]{1947,1948,1949,1950});
            check(exact.action().opcode==72&&
                    exact.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "actual decoded current client Attack action");

            final long ownerGeneration=attackerGeneration;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,attacker,()->ownerGeneration,
                    attacker.equipment(),attacker.combatStyles(),
                    sourceNpcs);
            if(failedAttackerWriter)
                attackerWriter.markTerminal();

            Throwable outputFailure=null;
            LocalCanonicalNpcAttackHandler.Result hit=null;
            try{
                hit=handler.handle(
                    exact.action(),attackView,attackerWriter);
            }catch(IOException error){
                outputFailure=error;
            }
            check(world.npcLifecycle().get(npc.id).hitpoints==20&&
                    handler.nextAllowedAttackTick()>world.clock().tick(),
                "single canonical HP hit committed regardless of output outcome");
            List<WorldNpcPresentationEvents.Event> pending=
                world.npcPresentationEvents().pendingFor(
                    spectator.id(),spectatorGeneration,
                    System.currentTimeMillis());
            if(failedAttackerWriter){
                check(outputFailure instanceof IOException&&
                        String.valueOf(outputFailure.getMessage())
                            .contains("server packet writer terminal")&&
                        hit==null&&
                        pending.isEmpty()&&
                        sourceOutput.size()==0&&
                        viewerOutput.size()==0,
                    "source mask I/O failure must not publish phantom remote hitmask");
            }else{
                check(outputFailure==null&&hit!=null&&
                        hit.status==
                            LocalCanonicalNpcAttackHandler.Status.HIT&&
                        hit.appliedDamage==10&&
                        hit.acceptedHit!=null&&
                        sourceOutput.size()>0&&
                        pending.size()==1&&
                        pending.get(0).sourceId.equals(attacker.id())&&
                        pending.get(0).sourceGeneration==
                            attackerGeneration&&
                        pending.get(0).target.kind==
                            WorldNpcPresentationEvents.Target.CANONICAL&&
                        pending.get(0).target.canonicalId.equals(npc.id)&&
                        pending.get(0).target.definition==definition&&
                        world.npcPresentationEvents()
                            .pendingFor(attacker.id(),
                                attackerGeneration,
                                System.currentTimeMillis()).isEmpty(),
                    "admitted attacker mask queued exactly once to spectator only");

                // Delivery is a separate viewer-owned presentation barrier.
                // The canonical identity is translated through the viewer's
                // own scene, not the attacker's scene slot.
                SharedNpcWorldRelay.flushAfterPlayer81(spectatorWriter);
                check(viewerOutput.size()>0&&
                        world.npcPresentationEvents().pendingFor(
                            spectator.id(),spectatorGeneration,
                            System.currentTimeMillis()).isEmpty(),
                    "spectator flush publishes and ACKs the translated mask");
                int deliveredBytes=viewerOutput.size();
                SharedNpcWorldRelay.flushAfterPlayer81(spectatorWriter);
                check(viewerOutput.size()==deliveredBytes,
                    "second presentation flush does not duplicate hitmask");
            }
            check(world.pvmRecords().receiptCount()==0,
                "no invented reward/credit from hit presentation");
        }finally{
            if(spectatorBound)SharedNpcWorldRelay.unregister(spectatorWriter);
            if(attackerBound)SharedNpcWorldRelay.unregister(attackerWriter);
            if(spectatorGeneration>=0&&
                world.players().owns(spectator,spectatorGeneration))
                world.unregisterPlayer(spectator,spectatorGeneration);
            if(attackerGeneration>=0&&
                world.players().owns(attacker,attackerGeneration))
                world.unregisterPlayer(attacker,attackerGeneration);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1947_CANONICAL_NPC_HITMASK_REMOTE_RELAY_PASS"+
            " exactDecodedAttack=true"+
            " oneWorldOwnedRemoteMask=true"+
            " remoteSceneTranslationDelivered=true"+
            " noDuplicateViewerFlush=true"+
            " noRewardAuthorityImplied=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.4.7/48 "+detail);
    }

    private G1947CanonicalNpcHitmaskRemoteRelayTest(){}
}
