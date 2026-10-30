package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * P5.35: same canonical NPC ID and World death tick need not imply
 * the same NPC lifecycle. Only the exact source witness can replay a
 * source-recorded kill. This is in-memory LocalLab evidence, NOT
 * native SpawnPK reward or crash-durable receipt certification.
 */
public final class PlayableWindStrikeSourceDeathCollisionTest {
    private static final int[] SEED={701,702,703,704};
    private PlayableWindStrikeSourceDeathCollisionTest(){}

    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(
            "P535_WIND_STRIKE_SOURCE_DEATH_COLLISION "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));

        int drainBytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static void addRune(WorldPlayer player,int item)throws IOException{
        String result=player.bank().spawnItem(
            item,3,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{711,712,713,714})));
        require(result!=null&&result.contains("OK"),
            "rune fixture missing item="+item);
    }

    private static SpellTargetRequest cast(NpcEntity view){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            view.sceneIndex,-1,-1,-1,-1,-1);
    }

    private static void denied(
        PvmRecordService.Receipt result,String reason
    ){
        require(result!=null&&!result.applied&&!result.replayed&&
            reason.equals(result.reason),
            "expected safe refusal="+reason+
            " actual="+(result==null?"null":result.reason));
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long first=world.registerPlayer(
            owner,"p535-same-tick-source-life");
        long current=first;
        NpcRegistry view=new NpcRegistry(
            new DevAuthorityWorkbench(),world.petNpcs(),owner.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{721,722,723,724}));
        boolean relayRegistered=false;
        WorldNpc npc=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,owner,view,owner.movement());
            relayRegistered=true;
            npc=world.npcs().spawn(100,3088,3495,0);
            NpcLifecycleService.Snapshot life1=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P535_FIRST_LIFE");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity shown=view.canonical(npc.id);
            require(shown!=null&&
                npc.id.equals(shown.canonicalId()),
                "canonical first-life viewer absent");

            addRune(owner,556);
            addRune(owner,558);
            SpellTargetRequest request=cast(shown);
            LocalLabWindStrikeNpcSettlement firstSpell=
                new LocalLabWindStrikeNpcSettlement(
                    world,owner,()->first,view);
            PvmRecordService records=world.pvmRecords();

            Capture firstWire=new Capture();
            String firstResult=firstSpell.settle(
                request,firstWire.writer);
            LocalLabWindStrikeHitWitness firstDeath=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(firstResult.contains("newlyDead=true")&&
                firstWire.drainBytes()>0&&
                firstDeath!=null&&firstDeath.newlyDied&&
                firstDeath.npcRegistrationIdentity==
                    life1.registrationIdentity&&
                firstDeath.stillCurrentLethalRegistration()&&
                records.receiptCount()==0,
                "first source death not settled as current witness");

            PvmRecordService.Receipt recorded=
                records.recordSourceWindStrikeLethalIfCurrent(
                    firstDeath);
            require(recorded.applied&&recorded.kills==1&&
                records.receiptCount()==1&&
                records.snapshot(owner).kills==1,
                "first source kill did not apply exactly once");
            PvmRecordService.Receipt replay=
                records.recordSourceWindStrikeLethalIfCurrent(
                    firstDeath);
            require(replay.replayed&&!replay.applied&&
                replay.kills==1&&records.receiptCount()==1,
                "exact same source witness not replayed");
            long originalDeathTick=firstDeath.deathTick;

            // Re-register the EXACT SAME WorldNpc with same ID and
            // definition before the World tick advances. A new
            // WorldPlayer generation permits a separate genuine
            // settlement at this tick, independently of P5.22's
            // correct per-generation once-per-tick spell admission.
            require(world.npcLifecycle().unregisterExact(npc),
                "first NPC life could not unregister");
            NpcLifecycleService.Snapshot life2=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P535_SECOND_LIFE");
            require(life2.registrationIdentity!=
                    life1.registrationIdentity&&life2.alive(),
                "NPC lifecycle ABA not independently identified");
            require(world.unregisterPlayer(owner,first),
                "first player generation not retired");
            current=world.registerPlayer(
                owner,"p535-same-tick-source-life");
            final long secondGen=current;
            require(secondGen!=first&&
                !firstDeath.stillCurrentLethalRegistration(),
                "old player generation retained death authority");
            LocalLabWindStrikeNpcSettlement secondSpell=
                new LocalLabWindStrikeNpcSettlement(
                    world,owner,()->secondGen,view);

            Capture secondWire=new Capture();
            String secondResult=secondSpell.settle(
                request,secondWire.writer);
            LocalLabWindStrikeHitWitness secondDeath=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(secondResult.contains("newlyDead=true")&&
                secondWire.drainBytes()>0&&
                secondDeath!=null&&secondDeath!=firstDeath&&
                secondDeath.stillCurrentLethalRegistration()&&
                secondDeath.npcRegistrationIdentity==
                    life2.registrationIdentity&&
                secondDeath.deathTick==originalDeathTick&&
                secondDeath.npcId.equals(firstDeath.npcId),
                "same-ID, same-tick second genuine death not proven");

            // Do NOT call this an idempotent replay of the first
            // source death. Its current NPC life has a distinct
            // opaque registration identity and cannot gain a new
            // credit under the historical id+tick ledger key.
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    secondDeath),
                "SOURCE_DEATH_LIFETIME_COLLISION");
            require(records.receiptCount()==1&&
                records.snapshot(owner).kills==1,
                "same-tick NPC life was falsely counted twice");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    firstDeath),
                "STALE_PLAYER_GENERATION");

            // An older non-witness legacy record with the chosen
            // NPC ID/tick has no lifecycle proof. P5.35 must NOT
            // retroactively bless it as a trusted source receipt.
            world.clock().advance();
            require(world.npcLifecycle().unregisterExact(npc),
                "second NPC life could not unregister");
            NpcLifecycleService.Snapshot life3=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P535_THIRD_LIFE");
            long thirdTick=world.clock().tick();
            PvmRecordService.Receipt legacy=
                records.recordIfCurrent(
                    owner,secondGen,npc.id,thirdTick);
            require(legacy.applied&&legacy.kills==2&&
                records.receiptCount()==2,
                "legacy compatibility record fixture refused");

            Capture thirdWire=new Capture();
            String thirdResult=secondSpell.settle(
                request,thirdWire.writer);
            LocalLabWindStrikeHitWitness thirdDeath=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(thirdResult.contains("newlyDead=true")&&
                thirdWire.drainBytes()>0&&
                thirdDeath!=null&&thirdDeath!=secondDeath&&
                thirdDeath.deathTick==thirdTick&&
                thirdDeath.npcRegistrationIdentity==
                    life3.registrationIdentity&&
                thirdDeath.stillCurrentLethalRegistration(),
                "third actual death not independently witnessed");

            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    thirdDeath),
                "SOURCE_DEATH_RECEIPT_UNATTRIBUTED");
            require(records.receiptCount()==2&&
                records.snapshot(owner).kills==2&&
                world.groundItems().snapshot().isEmpty()&&
                owner.bank().inventoryCount(556)==0&&
                owner.bank().inventoryCount(558)==0,
                "unattributed receipt produced duplicate count or loot");

            System.out.println(
                "P535_SOURCE_DEATH_COLLISION_FENCE_PASS"+
                " firstSourceReceiptAppliedOnce=true"+
                " exactWitnessReplayOnly=true"+
                " sameNpcObjectIdTickDifferentLifeRejected=true"+
                " trueSecondDeathNotMisreportedAsReplay=true"+
                " legacyUnattributedReceiptNotBlessed=true"+
                " currentDeathRevalidatedUnderOwnerLocks=true"+
                " noExtraKillXpOrLoot=true"+
                " originalServerAndCrashDurabilityUnknown=true");
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
