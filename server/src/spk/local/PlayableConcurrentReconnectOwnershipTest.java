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
 * P5.44: source-only same-account login ownership under actual
 * two-worker logout/login and competing-login contention.
 *
 * PlayerRegistry canonical-name admission, World lifecycle lock,
 * snapshot count/negative marker and current in-JVM source hit
 * evidence must not be confused across independently constructed
 * WorldPlayer objects. No actual socket login or cross-JVM lease.
 */
public final class PlayableConcurrentReconnectOwnershipTest {
    private PlayableConcurrentReconnectOwnershipTest(){}

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P544_CONCURRENT_RECONNECT "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1601,1602,1603,1604}));

        int bytes()throws IOException{
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            queue.drainTo(output,1<<20);
            return output.size();
        }
    }

    private static final class Actor {
        final WorldPlayer player=new WorldPlayer();
        final String username;
        final Capture packets=new Capture();
        volatile long generation=-1L;

        Actor(String username,PlayerSnapshot previous){
            this.username=username;
            if(previous!=null)
                PlayerSnapshotCodec.applyValidated(previous,player);
            else
                player.movement().restoreAccountState(
                    false,100,3087,3495,0);
        }

        long registerOnce(World world){
            try{
                long admitted=world.registerPlayer(player,username);
                generation=admitted;
                return admitted;
            }catch(IllegalStateException rejected){
                if(rejected.getMessage()!=null&&
                   rejected.getMessage().startsWith(
                       "DUPLICATE_LOGIN username="))
                    return -1L;
                throw rejected;
            }
        }

        boolean owned(World world){
            return generation>=0L&&
                world.players().owns(player,generation);
        }

        LocalLabWindStrikeHitWitness lethal(
            World world,WorldNpc npc,Object life
        )throws Exception{
            check(owned(world),"unregistered caster attempted attack");
            NpcRegistry scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity visible=scene.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(visible!=null&&
                player.movement().x()==3087&&
                player.movement().y()==3495,
                "source NPC scene or restored movement invalid");
            visible.bindCanonicalId(npc.id);
            SpellTargetRequest cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);
            LocalLabWindStrikeNpcSettlement spell=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);
            String result=spell.settle(cast,packets.writer);
            LocalLabWindStrikeHitWitness hit=
                player.lastLocalLabWindStrikeAcceptedHit();
            check(result.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                result.contains("newlyDead=true")&&
                packets.bytes()>0&&
                hit!=null&&hit.newlyDied&&
                hit.attacker==player&&hit.generation==generation&&
                hit.npcId.equals(npc.id)&&
                hit.npcRegistrationIdentity==life&&
                hit.stillCurrentLethalRegistration(),
                "fresh owner failed genuine lethal source cast "+result);
            return hit;
        }

        void seedRunes(int quantity)throws Exception{
            for(int id:new int[]{556,558}){
                String result=player.bank().spawnItem(
                    id,quantity,
                    G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                        new ByteArrayOutputStream()));
                check(result!=null&&result.contains("OK"),
                    "source rune seed rejected "+id);
            }
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=player.playerState().xp(i);
            return values;
        }

        void close(World world){
            if(owned(world))
                world.unregisterPlayer(player,generation);
        }
    }

    private static final class Pair<A,B> {
        final A first;
        final B second;
        Pair(A first,B second){
            this.first=first;this.second=second;
        }
    }

    private static <A,B> Pair<A,B> race(
        Callable<A> first,Callable<B> second
    )throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch go=new CountDownLatch(1);
        try{
            Future<A> a=pool.submit(()->{
                ready.countDown();
                check(go.await(10,TimeUnit.SECONDS),
                    "first login worker timed out at start");
                return first.call();
            });
            Future<B> b=pool.submit(()->{
                ready.countDown();
                check(go.await(10,TimeUnit.SECONDS),
                    "second login worker timed out at start");
                return second.call();
            });
            check(ready.await(10,TimeUnit.SECONDS),
                "login race workers did not reach start barrier");
            go.countDown();
            return new Pair<>(
                a.get(20,TimeUnit.SECONDS),
                b.get(20,TimeUnit.SECONDS));
        }finally{
            go.countDown();
            pool.shutdownNow();
            check(pool.awaitTermination(5,TimeUnit.SECONDS),
                "login race worker did not terminate");
        }
    }

    private static void denied(
        PvmRecordService.Receipt receipt,String expected
    ){
        check(receipt!=null&&!receipt.applied&&!receipt.replayed&&
            expected.equals(receipt.reason),
            "expected source refusal "+expected+" got "+
            (receipt==null?"null":receipt.reason));
    }

    private static void persistedState(
        World world,Actor player,String phase
    ){
        check(world.pvmRecords().snapshot(player.player).kills==1&&
            PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                .equals(player.player.snapshotExtensions()
                    .namespace(PvmRecordService.NAMESPACE)
                    .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY))&&
            player.player.lastLocalLabWindStrikeAcceptedHit()==null,
            phase+" borrowed another account session's source proof");
    }

    private static void run(int definition,int round)
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=null;
        Actor old=null,incoming=null,first=null,second=null;
        final String name="p544-owner-"+round;
        try{
            npc=world.npcs().spawn(definition,3088,3495,0);
            NpcLifecycleService.Snapshot life1=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P544_FIRST_LIFE");
            old=new Actor(name,null);
            check(old.registerOnce(world)>=0,
                "original same-account registration failed");
            old.seedRunes(3);
            int[] beforeXp=old.xp();
            LocalLabWindStrikeHitWitness prior=
                old.lethal(world,npc,life1.registrationIdentity);
            PvmRecordService records=world.pvmRecords();
            check(records.recordSourceWindStrikeLethalIfCurrent(
                    prior).applied&&
                records.snapshot(old.player).kills==1&&
                records.receiptCount()==1,
                "original source kill receipt not established");
            PlayerSnapshot saved=
                PlayerSnapshotCodec.capture(name,old.player);
            incoming=new Actor(name,saved);

            // An incoming connection cannot claim the same account
            // while the old WorldPlayer remains registered.
            check(incoming.registerOnce(world)<0&&
                old.owned(world)&&
                world.players().byName(name)==old.player&&
                world.players().size()==1&&
                records.recordSourceWindStrikeLethalIfCurrent(prior)
                    .replayed,
                "duplicate login stole original player's ownership");

            final Actor retired=old,newOwner=incoming;
            Pair<Boolean,Long> handoff=race(
                ()->world.unregisterPlayer(
                    retired.player,retired.generation),
                ()->newOwner.registerOnce(world));
            check(handoff.first,
                "original owner failed exact World unregister");
            // Both legal lock orderings are supported: if the login
            // won the lifecycle lock first, it rejected duplicate;
            // it must succeed only AFTER the old owner retired.
            if(handoff.second<0L)
                check(newOwner.registerOnce(world)>=0,
                    "incoming retry failed after completed logout");
            check(incoming.owned(world)&&
                !old.owned(world)&&
                world.players().byName(name)==incoming.player&&
                world.players().size()==1&&
                !incoming.player.id().equals(old.player.id()),
                "logout/login race admitted wrong or duplicate owner");
            persistedState(world,incoming,"first handoff");
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                prior),"STALE_PLAYER_GENERATION");
            check(incoming.player.bank().inventoryCount(556)==2&&
                incoming.player.bank().inventoryCount(558)==2,
                "fresh owner did not retain exact source inventory");

            // Even with a new death tick and valid real spell hit,
            // the previous player's in-JVM companion receipt cannot
            // supply positive proof for a different WorldPlayer.
            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "first NPC life failed unregister");
            NpcLifecycleService.Snapshot life2=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P544_SECOND_LIFE");
            LocalLabWindStrikeHitWitness fresh=
                incoming.lethal(
                    world,npc,life2.registrationIdentity);
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                fresh),"SOURCE_RESTART_REVIEW_REQUIRED");
            check(records.receiptCount()==1&&
                records.snapshot(incoming.player).kills==1,
                "first handoff created unauthorized second record");
            check(world.unregisterPlayer(
                    incoming.player,incoming.generation),
                "intermediate owner did not detach");

            // Two simultaneously arriving fresh WorldPlayer objects
            // with the SAME source-marked account snapshot must
            // produce one winner and one duplicate-name rejection.
            first=new Actor(name,saved);
            second=new Actor(name,saved);
            final Actor a=first,b=second;
            Pair<Long,Long> competitors=race(
                ()->a.registerOnce(world),
                ()->b.registerOnce(world));
            boolean aWins=competitors.first>=0L;
            boolean bWins=competitors.second>=0L;
            check(aWins!=bWins,
                "concurrent same-name login admitted zero or two players");
            Actor winner=aWins?first:second;
            Actor loser=aWins?second:first;
            check(winner.owned(world)&&
                !loser.owned(world)&&
                world.players().byName(name)==winner.player&&
                world.players().size()==1,
                "competing new logins violated exclusive name ownership");
            persistedState(world,winner,"two-login race");
            persistedState(world,loser,"unadmitted contender");
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                prior),"STALE_PLAYER_GENERATION");
            check(loser.registerOnce(world)<0&&
                world.players().byName(name)==winner.player,
                "loser reattempt displaced current account owner");

            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "second NPC life failed unregister");
            NpcLifecycleService.Snapshot life3=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P544_THIRD_LIFE");
            LocalLabWindStrikeHitWitness contested=
                winner.lethal(
                    world,npc,life3.registrationIdentity);
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                contested),"SOURCE_RESTART_REVIEW_REQUIRED");
            check(records.receiptCount()==1&&
                records.snapshot(winner.player).kills==1&&
                Arrays.equals(beforeXp,old.xp())&&
                Arrays.equals(beforeXp,incoming.xp())&&
                Arrays.equals(beforeXp,winner.xp())&&
                Arrays.equals(beforeXp,loser.xp())&&
                world.groundItems().snapshot().isEmpty()&&
                incoming.player.bank().inventoryCount(556)==1&&
                incoming.player.bank().inventoryCount(558)==1&&
                winner.player.bank().inventoryCount(556)==1&&
                winner.player.bank().inventoryCount(558)==1&&
                loser.player.bank().inventoryCount(556)==2&&
                loser.player.bank().inventoryCount(558)==2&&
                old.packets.bytes()==0&&
                incoming.packets.bytes()==0&&
                winner.packets.bytes()==0&&
                loser.packets.bytes()==0,
                "concurrent reconnect produced phantom kill/XP/loot/packets");

        }finally{
            if(first!=null)first.close(world);
            if(second!=null)second.close(world);
            if(incoming!=null)incoming.close(world);
            if(old!=null)old.close(world);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        int definition=
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
        for(int i=0;i<6;i++)run(definition,i);
        System.out.println(
            "P544_CONCURRENT_RECONNECT_OWNER_PASS"+
            " sixActualLogoutLoginAndTwoLoginBarriers=true"+
            " priorSameNameDuplicateRefused=true"+
            " oldWorldPlayerGenerationRetired=true"+
            " onlyOneSameNameNewWorldPlayerAdmitted=true"+
            " inheritedKillMarkerButNoSourceHitProof=true"+
            " newRealSourceHitsStillRequireRestartReview=true"+
            " zeroPhantomReceiptXpLootOrPackets=true"+
            " actualSocketCrossJvmCrashDurabilityUnverified=true");
    }
}
