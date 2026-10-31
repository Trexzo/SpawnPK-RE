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
 * P5.45: same-World snapshot capture vs explicit real source kill
 * record are serialized at the player mutation lock. A snapshot
 * captured just BEFORE a successful source record can be restored,
 * but must not erase the in-JVM source provenance already retained
 * by the World even if the original player changes username.
 *
 * In-memory source safety ONLY; no on-disk save ordering, crash
 * durability, login protocol, original XP/loot or cross-JVM lease.
 */
public final class PlayableSourceSnapshotRollbackGuardTest {
    private PlayableSourceSnapshotRollbackGuardTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P545_SOURCE_SNAPSHOT_ROLLBACK "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1701,1702,1703,1704}));
        int bytes()throws IOException{
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
        final SpellTargetRequest cast;
        final Capture packets=new Capture();

        Actor(World world,WorldNpc npc,String username,
              PlayerSnapshot snapshot)throws Exception{
            if(snapshot!=null)
                PlayerSnapshotCodec.applyValidated(snapshot,player);
            else
                player.movement().restoreAccountState(
                    false,100,3087,3495,0);
            generation=world.registerPlayer(player,username);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity shown=scene.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(shown!=null,"source NPC scene projection missing");
            shown.bindCanonicalId(npc.id);
            magic=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                shown.sceneIndex,-1,-1,-1,-1,-1);
        }

        void seedRunes(int quantity)throws Exception{
            for(int rune:new int[]{556,558}){
                String result=player.bank().spawnItem(
                    rune,quantity,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(new ByteArrayOutputStream()));
                check(result!=null&&result.contains("OK"),
                    "rune seed refused "+rune);
            }
        }

        LocalLabWindStrikeHitWitness lethal(
            World world,WorldNpc npc,Object life
        )throws Exception{
            String result=magic.settle(cast,packets.writer);
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
                "real one-HP source hit missing "+result);
            return hit;
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=player.playerState().xp(i);
            return values;
        }

        void noExtraRewards(
            int[] originalXp,String context)throws IOException{
            check(Arrays.equals(originalXp,xp())&&
                packets.bytes()==0&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1,
                context+" changed XP/packets or extra rune pairs");
        }

        void close(World world){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
        }
    }

    private static final class Pair<A,B> {
        final A left;
        final B right;
        Pair(A left,B right){this.left=left;this.right=right;}
    }

    private static <A,B> Pair<A,B> race(
        Callable<A> left,Callable<B> right
    )throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);
        try{
            Future<A> a=pool.submit(()->{
                ready.countDown();
                check(start.await(10,TimeUnit.SECONDS),
                    "source record worker start timeout");
                return left.call();
            });
            Future<B> b=pool.submit(()->{
                ready.countDown();
                check(start.await(10,TimeUnit.SECONDS),
                    "snapshot capture worker start timeout");
                return right.call();
            });
            check(ready.await(10,TimeUnit.SECONDS),
                "capture/record workers not both ready");
            start.countDown();
            return new Pair<>(
                a.get(20,TimeUnit.SECONDS),
                b.get(20,TimeUnit.SECONDS));
        }finally{
            start.countDown();
            pool.shutdownNow();
            check(pool.awaitTermination(5,TimeUnit.SECONDS),
                "capture/receipt race workers did not finish");
        }
    }

    private static String marker(PlayerSnapshot snapshot){
        return snapshot.values().get(
            "extension."+PvmRecordService.NAMESPACE+"."+
            PvmRecordService.SOURCE_RESTART_REVIEW_KEY);
    }

    private static long kills(PlayerSnapshot snapshot){
        String raw=snapshot.values().get(
            "extension."+PvmRecordService.NAMESPACE+".kills");
        return raw==null?0L:Long.parseLong(raw);
    }

    private static void denied(
        PvmRecordService.Receipt receipt,String reason
    ){
        check(receipt!=null&&!receipt.applied&&!receipt.replayed&&
            reason.equals(receipt.reason),
            "source snapshot rollback expected "+reason+
            " actual "+(receipt==null?"null":receipt.reason));
    }

    private static void run(int definition,int round)
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=null;
        Actor original=null,rolledBack=null,racedCopy=null;
        long renamedGeneration=-1L;
        String account="p545-rollback-"+round;
        try{
            npc=world.npcs().spawn(definition,3088,3495,0);
            NpcLifecycleService.Snapshot life1=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P545_FIRST_LIFE");
            original=new Actor(world,npc,account,null);
            original.seedRunes(3);
            int[] xp=original.xp();
            LocalLabWindStrikeHitWitness first=
                original.lethal(
                    world,npc,life1.registrationIdentity);
            PvmRecordService records=world.pvmRecords();
            check(records.receiptCount()==0&&
                records.snapshot(original.player).kills==0,
                "spell incorrectly auto-recorded source kill");

            // Guaranteed stale snapshot: taken AFTER the real spell
            // spent its runes but BEFORE the explicit record wrote
            // the numeric count and negative review marker.
            PlayerSnapshot precredit=
                PlayerSnapshotCodec.capture(account,original.player);
            check(kills(precredit)==0&&marker(precredit)==null,
                "precredit account unexpectedly contained source marker");

            final Actor owner=original;
            Pair<PvmRecordService.Receipt,PlayerSnapshot> contested=
                race(
                    ()->records.recordSourceWindStrikeLethalIfCurrent(
                        first),
                    ()->PlayerSnapshotCodec.capture(
                        account,owner.player));
            check(contested.left.applied&&
                contested.left.kills==1&&
                records.receiptCount()==1&&
                records.snapshot(original.player).kills==1,
                "actual source record race did not apply exactly once");

            PlayerSnapshot observed=contested.right;
            boolean before=marker(observed)==null;
            check((before&&kills(observed)==0)||
                (!before&&kills(observed)==1&&
                    PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                        .equals(marker(observed))),
                "concurrent snapshot captured torn count/marker postimage");
            check(records.recordSourceWindStrikeLethalIfCurrent(
                    first).replayed,
                "original exact receipt stopped replaying");
            check(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(original.player.snapshotExtensions()
                        .namespace(PvmRecordService.NAMESPACE)
                        .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "committed account marker not retained");

            // The original object can re-register under ANOTHER name
            // in the same World. Provenance must be frozen at source
            // receipt commit, not read from its now-mutated username.
            check(world.unregisterPlayer(
                    original.player,original.generation),
                "original account generation did not detach");
            renamedGeneration=world.registerPlayer(
                original.player,"p545-renamed-old-"+round);
            check(!account.equals(original.player.username())&&
                world.players().byName(account)==null,
                "re-registered original did not change active username");
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                first),"STALE_PLAYER_GENERATION");

            // Deterministic missing-marker restore: a different
            // WorldPlayer with same account name may legitimately
            // cast, but its source record must NOT reinterpret the
            // rolled-back snapshot as a first-ever source kill.
            rolledBack=new Actor(world,npc,account,precredit);
            check(rolledBack.player!=original.player&&
                records.snapshot(rolledBack.player).kills==0&&
                rolledBack.player.lastLocalLabWindStrikeAcceptedHit()
                    ==null,
                "precredit account restore inherited source proof");
            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "first NPC life could not retire");
            NpcLifecycleService.Snapshot life2=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P545_SECOND_LIFE");
            int[] restoredXp=rolledBack.xp();
            LocalLabWindStrikeHitWitness second=
                rolledBack.lethal(
                    world,npc,life2.registrationIdentity);
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                second),"SOURCE_ACCOUNT_SNAPSHOT_ROLLBACK");
            check(records.receiptCount()==1&&
                records.snapshot(rolledBack.player).kills==0,
                "rolled-back account was falsely granted another source kill");
            rolledBack.noExtraRewards(
                restoredXp,"precredit restored source denial");
            check(world.unregisterPlayer(
                    rolledBack.player,rolledBack.generation),
                "first restored owner failed detach");

            // Also replay the snapshot captured in the *actual*
            // two-thread race. A pre-credit outcome lacks marker and
            // fails rollback; post-credit retains marker but lacks
            // active in-JVM owner provenance and fails restart review.
            racedCopy=new Actor(world,npc,account,observed);
            check(records.snapshot(racedCopy.player).kills==
                    kills(observed)&&
                racedCopy.player.lastLocalLabWindStrikeAcceptedHit()
                    ==null,
                "raced snapshot copied source hit evidence");
            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "second NPC life could not retire");
            NpcLifecycleService.Snapshot life3=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P545_THIRD_LIFE");
            int[] racedXp=racedCopy.xp();
            LocalLabWindStrikeHitWitness third=
                racedCopy.lethal(
                    world,npc,life3.registrationIdentity);
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                third),before
                    ?"SOURCE_ACCOUNT_SNAPSHOT_ROLLBACK"
                    :"SOURCE_RESTART_REVIEW_REQUIRED");
            denied(records.recordSourceWindStrikeLethalIfCurrent(
                first),"STALE_PLAYER_GENERATION");
            check(records.receiptCount()==1&&
                records.snapshot(racedCopy.player).kills==
                    kills(observed)&&
                world.groundItems().snapshot().isEmpty()&&
                Arrays.equals(xp,original.xp()),
                "raced restoration invented source receipt or XP/loot");
            racedCopy.noExtraRewards(
                racedXp,"raced restored source denial");
            check(original.packets.bytes()==0&&
                rolledBack.packets.bytes()==0&&
                racedCopy.packets.bytes()==0,
                "record or snapshot wrote unexpected gameplay packets");
        }finally{
            if(racedCopy!=null)racedCopy.close(world);
            if(rolledBack!=null)rolledBack.close(world);
            if(original!=null){
                if(renamedGeneration>=0L&&
                   world.players().owns(original.player,renamedGeneration))
                    world.unregisterPlayer(
                        original.player,renamedGeneration);
                original.close(world);
            }
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        int definition=
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
        for(int round=0;round<8;round++)
            run(definition,round);
        System.out.println(
            "P545_SOURCE_SNAPSHOT_ROLLBACK_PASS"+
            " eightRealRecordVersusSnapshotCaptureBarriers=true"+
            " snapshotCountAndNegativeMarkerNeverTorn=true"+
            " deterministicMissingMarkerRestoreDenied=true"+
            " sourceAccountNameFrozenAcrossWorldPlayerRename=true"+
            " concurrentPreOrPostCreditRestoreSafelyDenied=true"+
            " sourceReceiptCountNeverDuplicated=true"+
            " noUnreviewedCombatXpLootOrPackets=true"+
            " onDiskCrashDurabilityAndCrossJvmLeaseUnproven=true");
    }
}
