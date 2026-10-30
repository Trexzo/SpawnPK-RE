package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.34: explicitly invoked in-JVM PvM record for a proven
 * post-settlement source Wind Strike lethal hit. Never original
 * SpawnPK XP, loot, grants, GUI or crash-durable settlement.
 */
public final class PlayableWindStrikeLethalRecordTest {
    private static final int[] WIRE_SEED={601,602,603,604};

    private PlayableWindStrikeLethalRecordTest(){}

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P534_WIND_STRIKE_LETHAL_RECORD "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,new IsaacCipher(WIRE_SEED.clone()));
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static void addRune(WorldPlayer owner,int id)
        throws IOException{
        String result=owner.bank().spawnItem(
            id,3,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{611,612,613,614})));
        require(result!=null&&result.contains("OK"),
            "rune seed declined "+id);
    }

    private static SpellTargetRequest cast(NpcEntity view){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            view.sceneIndex,-1,-1,-1,-1,-1);
    }

    private static void requireRejected(
        PvmRecordService.Receipt receipt,String why
    ){
        require(!receipt.applied&&!receipt.replayed&&
            receipt.reason.equals(why),
            "wrong source-only rejection wanted="+why+
            " got="+receipt.reason);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI arguments");
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(
            owner,"p534-source-only-record");
        long current=generation;
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),world.petNpcs(),
            owner.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{621,622,623,624}));
        boolean relayRegistered=false;
        WorldNpc npc=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,owner,scene,owner.movement());
            relayRegistered=true;
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,2,"CUSTOM_LOCALLAB_P534_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(npc.id);
            require(visible!=null&&
                visible.canonicalId().equals(npc.id),
                "real canonical NPC not projected");

            LocalLabWindStrikeNpcSettlement spell=
                new LocalLabWindStrikeNpcSettlement(
                    world,owner,()->generation,scene);
            SpellTargetRequest request=cast(visible);
            PvmRecordService records=world.pvmRecords();

            require(records.snapshot(owner).kills==0&&
                records.receiptCount()==0,
                "fixture already had a kill receipt");

            Capture rejected=new Capture();
            require(spell.settle(
                    request,rejected.writer).contains(
                        "REJECTED_MAGIC_REQUIREMENTS")&&
                rejected.drain().length==0&&
                owner.lastLocalLabWindStrikeAcceptedHit()==null&&
                records.receiptCount()==0,
                "missing runes created an accepted record");

            addRune(owner,556);
            addRune(owner,558);

            Capture full=new Capture();
            byte[] sentinel=new byte[4096];
            Arrays.fill(sentinel,(byte)0x66);
            full.queue.offer(sentinel);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    spell.settle(request,full.writer))&&
                Arrays.equals(full.drain(),sentinel)&&
                owner.lastLocalLabWindStrikeAcceptedHit()==null&&
                records.receiptCount()==0,
                "full packet queue created kill receipt");

            Capture firstPacket=new Capture();
            String first=spell.settle(
                request,firstPacket.writer);
            LocalLabWindStrikeHitWitness nonlethal=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(first.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                firstPacket.drain().length>0&&
                nonlethal!=null&&!nonlethal.newlyDied&&
                world.npcLifecycle().get(npc.id).hitpoints==1,
                "first actual nonlethal hit missing");
            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(
                    nonlethal),
                "NOT_COMMITTED_LETHAL_SOURCE_HIT");
            require(records.snapshot(owner).kills==0&&
                records.receiptCount()==0,
                "nonlethal hit made PvM kill credit");

            Capture sameTick=new Capture();
            require("REJECTED_SAME_TICK_DUPLICATE".equals(
                    spell.settle(request,sameTick.writer))&&
                sameTick.drain().length==0&&
                owner.lastLocalLabWindStrikeAcceptedHit()==nonlethal&&
                records.receiptCount()==0,
                "duplicate source cast created receipt");

            world.clock().advance();
            Capture lethalPacket=new Capture();
            String finalCast=spell.settle(
                request,lethalPacket.writer);
            LocalLabWindStrikeHitWitness lethal=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(finalCast.contains("newlyDead=true")&&
                lethalPacket.drain().length>0&&
                lethal!=null&&lethal.newlyDied&&
                lethal.stillCurrentLethalRegistration()&&
                records.receiptCount()==0,
                "lethal hit bypassed explicit record admission");

            // Source-only record is NOT wired to LocalSession or
            // automatically granted by magic settlement. Call
            // explicitly under independent World/NPC ownership.
            PvmRecordService.Receipt applied=
                records.recordSourceWindStrikeLethalIfCurrent(
                    lethal);
            require(applied.applied&&!applied.replayed&&
                applied.kills==1&&
                records.snapshot(owner).kills==1&&
                records.receiptCount()==1,
                "actual lethal source record did not apply once");

            PvmRecordService.Receipt replay=
                records.recordSourceWindStrikeLethalIfCurrent(
                    lethal);
            require(replay.replayed&&!replay.applied&&
                replay.kills==1&&
                records.snapshot(owner).kills==1&&
                records.receiptCount()==1,
                "duplicate source witness applied twice");

            World otherWorld=World.isolatedForTest(600L);
            try{
                requireRejected(
                    otherWorld.pvmRecords()
                        .recordSourceWindStrikeLethalIfCurrent(
                            lethal),
                    "FOREIGN_WORLD");
                require(otherWorld.pvmRecords().receiptCount()==0,
                    "foreign World stored source kill");
            }finally{
                otherWorld.close();
            }

            // Retirement of even the same WorldNpc object must
            // invalidate old opaque life identity before record.
            require(world.npcLifecycle().unregisterExact(npc),
                "NPC lifecycle removal failed");
            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(lethal),
                "STALE_NPC_DEATH_REGISTRATION");
            NpcLifecycleService.Snapshot revived=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P534_SECOND_LIFE");
            require(revived.alive()&&
                revived.registrationIdentity!=
                    lethal.npcRegistrationIdentity,
                "NPC registration ABA was not distinguishable");
            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(lethal),
                "STALE_NPC_DEATH_REGISTRATION");
            require(records.snapshot(owner).kills==1&&
                records.receiptCount()==1,
                "stale life replay created second count");

            world.clock().advance();
            Capture rebornHit=new Capture();
            require(spell.settle(
                    request,rebornHit.writer).contains(
                        "newlyDead=true")&&
                rebornHit.drain().length>0,
                "source NPC second legitimate lifetime not settled");
            LocalLabWindStrikeHitWitness secondLife=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(secondLife!=null&&
                secondLife!=lethal&&
                secondLife.npcRegistrationIdentity==
                    revived.registrationIdentity&&
                secondLife.stillCurrentLethalRegistration(),
                "new source life did not have own death witness");

            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(lethal),
                "NOT_CURRENT_SOURCE_HIT");

            PvmRecordService.Receipt secondApplied=
                records.recordSourceWindStrikeLethalIfCurrent(
                    secondLife);
            require(secondApplied.applied&&
                secondApplied.kills==2&&
                records.snapshot(owner).kills==2&&
                records.receiptCount()==2,
                "legitimate new life was not recorded exactly once");
            require(records.recordSourceWindStrikeLethalIfCurrent(
                    secondLife).replayed&&
                records.snapshot(owner).kills==2,
                "second lifetime repeated kill count");

            require(world.groundItems().snapshot().isEmpty(),
                "source-only kill receipt invented ground loot");

            require(world.unregisterPlayer(owner,generation),
                "WorldPlayer owner detach refused");
            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(
                    secondLife),
                "STALE_PLAYER_GENERATION");
            current=world.registerPlayer(
                owner,"p534-source-only-record");
            require(current!=generation,
                "same-player owner generation did not rotate");
            requireRejected(
                records.recordSourceWindStrikeLethalIfCurrent(
                    secondLife),
                "STALE_PLAYER_GENERATION");
            require(records.snapshot(owner).kills==2&&
                records.receiptCount()==2,
                "stale player wrote a third kill");

            System.out.println(
                "P534_SOURCE_WIND_STRIKE_KILL_RECORD_PASS"+
                " explicitOnlyNoAutoCredit=true"+
                " strictPlayerWorldNpcRegistration=true"+
                " firstLethalAppliedOnce=true"+
                " duplicateLethalReceiptReplayNoMutation=true"+
                " missingRunesAndBackpressureNoReceipt=true"+
                " nonlethalNoReceipt=true"+
                " crossWorldReplayDenied=true"+
                " npcRegistrationAbaDenied=true"+
                " newNpcLifetimeIndependentlyRecorded=true"+
                " playerGenerationAbaDenied=true"+
                " noXpLootOrOriginalServerGrant=true"+
                " persistentCrashDurabilityNotProven=true");
        }finally{
            if(npc!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,npc.id);
            if(relayRegistered)
                SharedNpcWorldRelay.unregister(relay);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(world.players().owns(owner,current))
                world.unregisterPlayer(owner,current);
            if(!world.closed())world.close();
        }
    }
}
