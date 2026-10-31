package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * P5.42: real two-worker contention around one genuine, committed
 * source-only Wind Strike lethal witness and its explicit kill record.
 *
 * Identical concurrent record attempts must have exactly one apply;
 * concurrent NPC registration retirement or WorldPlayer retirement
 * must result in either an authorized pre-retirement apply OR a safe
 * post-retirement refusal. Never a phantom or duplicate grant.
 *
 * This is in-JVM LocalLab/source acceptance, not durable XP/loot
 * authorization, a cross-JVM account lease or original game policy.
 */
public final class PlayableConcurrentSourceReceiptRetirementTest {
    private PlayableConcurrentSourceReceiptRetirementTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P542_SOURCE_RECEIPT_RACE "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1401,1402,1403,1404}));
        int bytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        final long generation;
        final WorldNpc npc;
        final NpcRegistry scene;
        final LocalLabWindStrikeHitWitness witness;
        final Capture packets=new Capture();
        final int[] startingXp;
        final String afterCastInventory;

        Fixture(int definition,String name)throws Exception{
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            npc=world.npcs().spawn(definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P542_ONE_HP");
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity visible=scene.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(visible!=null&&visible.sceneIndex>=0,
                "one-HP canonical target not visible");
            visible.bindCanonicalId(npc.id);
            for(int id:new int[]{556,558}){
                String seed=player.bank().spawnItem(
                    id,1,G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(new ByteArrayOutputStream()));
                check(seed!=null&&seed.contains("OK"),
                    "source rune seed refused "+id);
            }
            startingXp=xp();

            LocalLabWindStrikeNpcSettlement magic=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);
            SpellTargetRequest request=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);
            String settled=magic.settle(request,packets.writer);
            witness=player.lastLocalLabWindStrikeAcceptedHit();
            check(settled.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                settled.contains("newlyDead=true")&&
                witness!=null&&witness.newlyDied&&
                witness.attacker==player&&
                witness.generation==generation&&
                witness.stillCurrentLethalRegistration()&&
                world.npcLifecycle().get(npc.id).dead()&&
                packets.bytes()>0,
                "real lethal spell witness unavailable "+settled);
            check(player.bank().inventoryCount(556)==0&&
                player.bank().inventoryCount(558)==0&&
                world.pvmRecords().receiptCount()==0,
                "spell did not spend exactly its reviewed rune pair");
            afterCastInventory=inventory();
            unchanged("after source spell");
        }

        int[] xp(){
            int[] result=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<result.length;i++)
                result[i]=player.playerState().xp(i);
            return result;
        }

        String inventory(){
            StringBuilder result=new StringBuilder();
            for(int slot=0;slot<BankState.INVENTORY_CAPACITY;slot++){
                BankState.Stack item=player.bank().inventoryAt(slot);
                if(item==null)result.append('-');
                else result.append(item.itemId).append(':').append(item.qty);
                result.append('|');
            }
            return result.toString();
        }

        void unchanged(String phase)throws IOException{
            check(Arrays.equals(startingXp,xp())&&
                afterCastInventory.equals(inventory())&&
                world.groundItems().snapshot().isEmpty()&&
                packets.bytes()==0,
                phase+" added unreviewed XP, inventory, ground or packets");
        }

        void close(){
            world.npcLifecycle().unregisterExact(npc);
            world.npcs().remove(npc.id);
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }

    private static final class Pair<A,B> {
        final A left;
        final B right;
        Pair(A left,B right){
            this.left=left;
            this.right=right;
        }
    }

    private static <A,B> Pair<A,B> race(
        Callable<A> left,Callable<B> right
    )throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch release=new CountDownLatch(1);
        try{
            Future<A> a=workers.submit(()->{
                ready.countDown();
                if(!release.await(10,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "left receipt worker start timeout");
                return left.call();
            });
            Future<B> b=workers.submit(()->{
                ready.countDown();
                if(!release.await(10,TimeUnit.SECONDS))
                    throw new AssertionError(
                        "right receipt worker start timeout");
                return right.call();
            });
            check(ready.await(10,TimeUnit.SECONDS),
                "two receipt workers failed start barrier");
            release.countDown();
            return new Pair<>(
                a.get(20,TimeUnit.SECONDS),
                b.get(20,TimeUnit.SECONDS));
        }finally{
            release.countDown();
            workers.shutdownNow();
            check(workers.awaitTermination(5,TimeUnit.SECONDS),
                "source record contention worker leaked");
        }
    }

    private static void duplicateConcurrentRecord(
        int definition,int round
    )throws Exception{
        Fixture f=new Fixture(definition,"p542-duplicate-"+round);
        try{
            PvmRecordService records=f.world.pvmRecords();
            Pair<PvmRecordService.Receipt,PvmRecordService.Receipt> race=
                race(
                    ()->records.recordSourceWindStrikeLethalIfCurrent(
                        f.witness),
                    ()->records.recordSourceWindStrikeLethalIfCurrent(
                        f.witness));
            int applied=(race.left.applied?1:0)+
                (race.right.applied?1:0);
            int replay=(race.left.replayed?1:0)+
                (race.right.replayed?1:0);
            check(applied==1&&replay==1&&
                records.receiptCount()==1&&
                records.snapshot(f.player).kills==1&&
                race.left.kills==1&&race.right.kills==1,
                "concurrent same-witness record was not apply + replay");
            check(records.recordSourceWindStrikeLethalIfCurrent(
                    f.witness).replayed&&
                records.receiptCount()==1,
                "post-race exact witness failed idempotency");
            f.unchanged("duplicate record race");
        }finally{f.close();}
    }

    private static void concurrentNpcRetirement(
        int definition,int round
    )throws Exception{
        Fixture f=new Fixture(definition,"p542-npc-retire-"+round);
        try{
            PvmRecordService records=f.world.pvmRecords();
            Pair<PvmRecordService.Receipt,Boolean> raced=race(
                ()->records.recordSourceWindStrikeLethalIfCurrent(
                    f.witness),
                ()->f.world.npcLifecycle().unregisterExact(f.npc));
            check(raced.right,
                "NPC lifetime failed to unregister exactly once");
            PvmRecordService.Receipt grant=raced.left;
            check(grant.applied||
                (!grant.applied&&!grant.replayed&&
                    "STALE_NPC_DEATH_REGISTRATION".equals(grant.reason)),
                "NPC retire race returned an unsafe source grant outcome "+
                grant.reason);
            long expected=grant.applied?1L:0L;
            check(records.receiptCount()==expected&&
                records.snapshot(f.player).kills==expected,
                "NPC retirement returned inconsistent in-JVM kill count");
            PvmRecordService.Receipt after=
                records.recordSourceWindStrikeLethalIfCurrent(
                    f.witness);
            check(!after.applied&&!after.replayed&&
                "STALE_NPC_DEATH_REGISTRATION".equals(after.reason)&&
                !f.witness.stillCurrentLethalRegistration(),
                "retired NPC source witness could be replayed or granted");
            f.unchanged("NPC retirement race");
        }finally{f.close();}
    }

    private static void concurrentPlayerRetirement(
        int definition,int round
    )throws Exception{
        Fixture f=new Fixture(definition,"p542-player-retire-"+round);
        try{
            PvmRecordService records=f.world.pvmRecords();
            Pair<PvmRecordService.Receipt,Boolean> raced=race(
                ()->records.recordSourceWindStrikeLethalIfCurrent(
                    f.witness),
                ()->f.world.unregisterPlayer(
                    f.player,f.generation));
            check(raced.right,
                "player generation failed to detach exactly once");
            PvmRecordService.Receipt grant=raced.left;
            check(grant.applied||
                (!grant.applied&&!grant.replayed&&
                    "STALE_PLAYER_GENERATION".equals(grant.reason)),
                "player retire race returned an unsafe source outcome "+
                grant.reason);
            long expected=grant.applied?1L:0L;
            check(records.receiptCount()==expected&&
                records.snapshot(f.player).kills==expected,
                "player retirement returned inconsistent source counter");
            PvmRecordService.Receipt after=
                records.recordSourceWindStrikeLethalIfCurrent(
                    f.witness);
            check(!after.applied&&!after.replayed&&
                "STALE_PLAYER_GENERATION".equals(after.reason)&&
                !f.witness.attackerStillCurrent(),
                "old generation source witness was recordable after detach");
            f.unchanged("player retirement race");
        }finally{f.close();}
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected command-line arguments");
        int definition=
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
        // The thread scheduler is intentionally nondeterministic:
        // both legal serializations must be safe, including the
        // retirement-first no-grant outcome.
        for(int i=0;i<8;i++){
            duplicateConcurrentRecord(definition,i);
            concurrentNpcRetirement(definition,i);
            concurrentPlayerRetirement(definition,i);
        }
        System.out.println(
            "P542_CONCURRENT_SOURCE_RECEIPT_RETIREMENT_PASS"+
            " eightConcurrentDuplicateReceiptRaces=true"+
            " eightConcurrentNpcRegistrationRetirements=true"+
            " eightConcurrentPlayerGenerationRetirements=true"+
            " oneApplyOneReplayWhenBothRecordsValid=true"+
            " postRetirementSourceRecordAlwaysRefused=true"+
            " noUnreviewedXpLootInventoryOrPackets=true"+
            " durableOriginalServerRewardsUnverified=true");
    }
}
