package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.40 source-only cross-player NPC-life ABA acceptance.
 *
 * Two distinct current WorldPlayers produce real lethal Wind Strike
 * witnesses for the SAME canonical NPC ID on the SAME World tick,
 * separated by lifecycle re-registration. The second lifetime is
 * never a replay or a valid second kill under the old ID+tick key.
 * A later, distinct death tick may be recorded for the second owner.
 *
 * No authentic SpawnPK XP/loot, PvM credit policy, GUI or durable
 * receipt is implemented or inferred by this isolated regression.
 */
public final class PlayableCrossPlayerNpcLifeCollisionTest {
    private PlayableCrossPlayerNpcLifeCollisionTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P540_CROSS_PLAYER_NPC_LIFE "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1201,1202,1203,1204}));
        int drainBytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Actor {
        final WorldPlayer player=new WorldPlayer();
        final long generation;
        final NpcRegistry scene;
        final LocalLabWindStrikeNpcSettlement magic;
        final SpellTargetRequest spell;
        final Capture packets=new Capture();
        final int[] initialXp;

        Actor(World world,WorldNpc npc,String username)
            throws Exception{
            generation=world.registerPlayer(player,username);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),player.id());
            NpcEntity shown=scene.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(shown!=null,"source NPC scene unavailable");
            shown.bindCanonicalId(npc.id);
            for(int item:new int[]{556,558}){
                String seeded=player.bank().spawnItem(
                    item,3,
                    G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                        new ByteArrayOutputStream()));
                check(seeded!=null&&seeded.contains("OK"),
                    "failed to create source rune fixture "+item);
            }
            magic=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            spell=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                shown.sceneIndex,-1,-1,-1,-1,-1);
            initialXp=xp();
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=player.playerState().xp(i);
            return values;
        }

        LocalLabWindStrikeHitWitness lethal(
            World world,WorldNpc npc,Object registration
        )throws Exception{
            String result=magic.settle(spell,packets.writer);
            LocalLabWindStrikeHitWitness proof=
                player.lastLocalLabWindStrikeAcceptedHit();
            check(result.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                result.contains("newlyDead=true")&&
                packets.drainBytes()>0&&
                proof!=null&&proof.newlyDied&&
                proof.attacker==player&&
                proof.generation==generation&&
                proof.npcId.equals(npc.id)&&
                proof.npcRegistrationIdentity==registration&&
                proof.stillCurrentLethalRegistration()&&
                world.npcLifecycle().get(npc.id).dead(),
                "expected exact owner/current-lifetime lethal source hit "+
                result);
            return proof;
        }

        void close(World world){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
        }
    }

    private static void rejected(
        PvmRecordService.Receipt receipt,String reason
    ){
        check(receipt!=null&&!receipt.applied&&!receipt.replayed&&
            reason.equals(receipt.reason),
            "source collision denied for incorrect reason: expected "+
            reason+" actual "+(receipt==null?"null":receipt.reason));
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        World world=World.isolatedForTest(600L);
        WorldNpc npc=null;
        Actor a=null,b=null;
        try{
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            npc=world.npcs().spawn(definition,3088,3495,0);
            NpcLifecycleService.Snapshot life1=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P540_FIRST_LIFE");
            a=new Actor(world,npc,"p540-first-owner");
            b=new Actor(world,npc,"p540-second-owner");
            check(!a.player.id().equals(b.player.id())&&
                world.clock().tick()==0&&
                world.pvmRecords().receiptCount()==0,
                "source fixture owners or clock were not independent");
            PvmRecordService records=world.pvmRecords();

            LocalLabWindStrikeHitWitness first=
                a.lethal(world,npc,life1.registrationIdentity);
            PvmRecordService.Receipt initial=
                records.recordSourceWindStrikeLethalIfCurrent(first);
            check(initial.applied&&!initial.replayed&&
                initial.kills==1&&
                records.snapshot(a.player).kills==1&&
                records.snapshot(b.player).kills==0&&
                records.receiptCount()==1,
                "first exact source owner not credited once");
            check(records.recordSourceWindStrikeLethalIfCurrent(first)
                .replayed,"same immutable first life not idempotent");
            long deathTick=first.deathTick;

            // Same WorldNpc instance, ID, definition and World tick:
            // only the registration identity changes. Crucially B
            // has never attacked, so B's source once-per-tick gate
            // is independently available.
            check(world.npcLifecycle().unregisterExact(npc),
                "first lifecycle unregister failed");
            NpcLifecycleService.Snapshot life2=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P540_SECOND_LIFE");
            check(life2.alive()&&
                life2.registrationIdentity!=life1.registrationIdentity&&
                world.clock().tick()==deathTick,
                "same-tick NPC life ABA fixture did not materialize");
            LocalLabWindStrikeHitWitness second=
                b.lethal(world,npc,life2.registrationIdentity);
            check(second!=first&&
                second.attacker!=first.attacker&&
                second.npcId.equals(first.npcId)&&
                second.deathTick==first.deathTick&&
                second.npcRegistrationIdentity!=
                    first.npcRegistrationIdentity,
                "second real owner's lethal collision not distinct");
            rejected(
                records.recordSourceWindStrikeLethalIfCurrent(second),
                "SOURCE_DEATH_LIFETIME_COLLISION");
            rejected(
                records.recordSourceWindStrikeLethalIfCurrent(first),
                "STALE_NPC_DEATH_REGISTRATION");
            check(records.snapshot(a.player).kills==1&&
                records.snapshot(b.player).kills==0&&
                records.receiptCount()==1,
                "same ID/tick collision miscredited either owner");

            // A fresh World death tick is a new independent record
            // key. B is now allowed a second legitimate spell and
            // can explicitly commit their OWN valid source kill.
            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "second lifecycle unregister failed");
            NpcLifecycleService.Snapshot life3=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P540_THIRD_LIFE");
            check(life3.registrationIdentity!=
                    life2.registrationIdentity&&life3.alive(),
                "third NPC registration identity was not new");
            LocalLabWindStrikeHitWitness third=
                b.lethal(world,npc,life3.registrationIdentity);
            check(third.deathTick!=second.deathTick,
                "third genuine death reused old tick");
            rejected(
                records.recordSourceWindStrikeLethalIfCurrent(second),
                "NOT_CURRENT_SOURCE_HIT");
            PvmRecordService.Receipt grant=
                records.recordSourceWindStrikeLethalIfCurrent(third);
            check(grant.applied&&!grant.replayed&&
                grant.kills==1&&
                records.snapshot(a.player).kills==1&&
                records.snapshot(b.player).kills==1&&
                records.receiptCount()==2,
                "independent later death did not credit exact owner B");
            check(records.recordSourceWindStrikeLethalIfCurrent(third)
                    .replayed&&
                records.receiptCount()==2,
                "third-life proof replay counted twice");

            check(Arrays.equals(a.initialXp,a.xp())&&
                Arrays.equals(b.initialXp,b.xp())&&
                world.groundItems().snapshot().isEmpty()&&
                a.player.bank().inventoryCount(556)==2&&
                a.player.bank().inventoryCount(558)==2&&
                b.player.bank().inventoryCount(556)==1&&
                b.player.bank().inventoryCount(558)==1&&
                a.packets.drainBytes()==0&&
                b.packets.drainBytes()==0,
                "source receipts mutated unreviewed XP, loot, rune cost or packets");

            System.out.println(
                "P540_CROSS_PLAYER_NPC_LIFETIME_PASS"+
                " sameCanonicalNpcIdTickTwoDistinctOwners=true"+
                " sameTickSecondLifeNotReplay=true"+
                " secondOwnerCollisionDeniedNoKillCredit=true"+
                " staleFirstLifeDenied=true"+
                " nextTickIndependentOwnerLifeGrantedOnce=true"+
                " noPhantomCombatXpLootOrPackets=true"+
                " originalSpawnpkRewardRulesUnverified=true");
        }finally{
            if(a!=null)a.close(world);
            if(b!=null)b.close(world);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(!world.closed())world.close();
        }
    }
}
