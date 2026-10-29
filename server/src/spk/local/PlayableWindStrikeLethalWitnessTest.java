package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.33: immutable read-only evidence for actual canonical source
 * Wind Strike hits. No XP, PvM kill receipt, loot or native GUI claim.
 */
public final class PlayableWindStrikeLethalWitnessTest {
    private static final int[] SEED={521,522,523,524};
    private PlayableWindStrikeLethalWitnessTest(){}

    private static void require(boolean valid,String why){
        if(!valid)throw new AssertionError(
            "P533_WIND_STRIKE_LETHAL_WITNESS "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static void add(WorldPlayer owner,int id)throws Exception{
        String reply=owner.bank().spawnItem(
            id,3,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{531,532,533,534})));
        require(reply!=null&&reply.contains("OK"),
            "fixture rune missing "+id);
    }

    private static SpellTargetRequest spell(NpcEntity scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene.sceneIndex,-1,-1,-1,-1,-1);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(
            player,"p533-wind-strike-witness");
        long current=first;
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),world.petNpcs(),
            player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{541,542,543,544}));
        boolean registered=false;
        WorldNpc npc=null;
        LocalLabWindStrikeHitWitness prior=null;
        LocalLabWindStrikeHitWitness lethal=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            registered=true;
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,2,"CUSTOM_LOCALLAB_P533_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(npc.id);
            require(visible!=null&&
                npc.id.equals(visible.canonicalId()),
                "canonical NPC not projected into source viewer");
            SpellTargetRequest request=spell(visible);
            LocalLabWindStrikeNpcSettlement one=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->first,scene);
            LocalLabWindStrikeNpcSettlement sibling=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->first,scene);
            require(player.lastLocalLabWindStrikeAcceptedHit()==null,
                "source owner started with unearned hit evidence");

            Capture noRunes=new Capture();
            require(one.settle(request,noRunes.writer).contains(
                    "REJECTED_MAGIC_REQUIREMENTS")&&
                noRunes.drain().length==0&&
                player.lastLocalLabWindStrikeAcceptedHit()==null,
                "failed spell produced phantom hit evidence");

            add(player,556);
            add(player,558);
            Capture full=new Capture();
            byte[] sentinel=new byte[4096];
            Arrays.fill(sentinel,(byte)0x55);
            full.queue.offer(sentinel);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    one.settle(request,full.writer))&&
                Arrays.equals(full.drain(),sentinel)&&
                player.lastLocalLabWindStrikeAcceptedHit()==null&&
                world.npcLifecycle().get(npc.id).hitpoints==2,
                "backpressure created hit evidence or damage");

            Capture firstWire=new Capture();
            String firstHit=one.settle(request,firstWire.writer);
            prior=player.lastLocalLabWindStrikeAcceptedHit();
            require(firstHit.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                firstWire.drain().length>0&&
                prior!=null&&
                prior.world==world&&
                prior.attacker==player&&
                prior.attackerId.equals(player.id())&&
                prior.generation==first&&
                prior.npcId.equals(npc.id)&&
                prior.npcDefinitionId==npc.definitionId&&
                prior.appliedDamage==1&&
                prior.hitpointsAfter==1&&
                !prior.newlyDied&&
                prior.deathTick==NpcLifecycleService.NO_DEATH_TICK&&
                !prior.stillCurrentLethalRegistration()&&
                prior.attackerStillCurrent()&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2,
                "nonlethal committed hit witness not exact");

            Capture dup=new Capture();
            require("REJECTED_SAME_TICK_DUPLICATE".equals(
                    sibling.settle(request,dup.writer))&&
                dup.drain().length==0&&
                player.lastLocalLabWindStrikeAcceptedHit()==prior,
                "sibling duplicate replaced accepted hit witness");

            world.clock().advance();
            Capture lethalWire=new Capture();
            String last=sibling.settle(request,lethalWire.writer);
            lethal=player.lastLocalLabWindStrikeAcceptedHit();
            NpcLifecycleService.Snapshot death=
                world.npcLifecycle().get(npc.id);
            require(last.contains("newlyDead=true")&&
                lethalWire.drain().length>0&&
                lethal!=null&&lethal!=prior&&
                lethal.newlyDied&&lethal.hitpointsAfter==0&&
                lethal.deathTick==death.deathTick&&
                lethal.worldTick==death.deathTick&&
                lethal.npcRegistrationIdentity==
                    death.registrationIdentity&&
                lethal.stillCurrentLethalRegistration()&&
                !prior.stillCurrentLethalRegistration()&&
                world.pvmRecords().snapshot(player).kills==0&&
                world.pvmRecords().receiptCount()==0,
                "lethal hit forged reward or lacked current death proof");

            // Even the exact same canonical NPC object, id and viewer
            // must not make an older death tick/registration valid
            // after an unregister/re-register lifecycle ABA.
            require(world.npcLifecycle().unregisterExact(npc),
                "lethal lifecycle detach rejected");
            require(!lethal.stillCurrentLethalRegistration(),
                "old witness authorized missing NPC lifecycle");
            NpcLifecycleService.Snapshot revived=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P533_REGISTER_ABA");
            require(revived.alive()&&
                revived.registrationIdentity!=
                    lethal.npcRegistrationIdentity&&
                !lethal.stillCurrentLethalRegistration(),
                "old witness authorized new canonical NPC registration");

            world.clock().advance();
            Capture restart=new Capture();
            require(one.settle(request,restart.writer).contains(
                    "newlyDead=true")&&
                restart.drain().length>0&&
                player.bank().inventoryCount(556)==0&&
                player.bank().inventoryCount(558)==0,
                "fresh registration could not settle legitimate hit");
            LocalLabWindStrikeHitWitness fresh=
                player.lastLocalLabWindStrikeAcceptedHit();
            require(fresh!=null&&fresh!=lethal&&
                fresh.npcRegistrationIdentity==
                    revived.registrationIdentity&&
                fresh.stillCurrentLethalRegistration()&&
                !lethal.stillCurrentLethalRegistration()&&
                world.pvmRecords().receiptCount()==0,
                "same-NPC lifecycle ABA produced stale kill authority");

            require(world.unregisterPlayer(player,first),
                "player detach rejected");
            require(!fresh.attackerStillCurrent()&&
                !fresh.stillCurrentLethalRegistration(),
                "retired WorldPlayer retained current kill permission");
            current=world.registerPlayer(
                player,"p533-wind-strike-witness");
            require(current!=first&&
                !fresh.attackerStillCurrent()&&
                !fresh.stillCurrentLethalRegistration(),
                "same-instance player generation ABA resurrected evidence");

            System.out.println(
                "P533_WIND_STRIKE_LETHAL_WITNESS_PASS"+
                " exactCommittedNpcPlayerGeneration=true"+
                " crossWrapperSharedHistoricalEvidence=true"+
                " rejectionAndBackpressureNoEvidence=true"+
                " nonlethalNeverAuthorizesDeath=true"+
                " genuineLethalRegistrationReadOnlyWitness=true"+
                " npcLifecycleAbaRejected=true"+
                " playerGenerationAbaRejected=true"+
                " noPvmReceiptXpOrLootGranted=true"+
                " productionMagicStillDefaultOff=true"+
                " originalSpawnpkDeathRewardsUnverified=true");
        }finally{
            if(npc!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,npc.id);
            if(registered)
                SharedNpcWorldRelay.unregister(relay);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(world.players().owns(player,current))
                world.unregisterPlayer(player,current);
            if(!world.closed())world.close();
        }
    }
}
