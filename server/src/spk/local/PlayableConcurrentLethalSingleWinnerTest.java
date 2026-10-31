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
 * P5.41: two real, independently owned player attacks race on the
 * same one-HP canonical NPC registration with a two-worker barrier.
 *
 * Verify exactly one lethal HP transition and at most one source
 * Wind Strike record, with no losing caster rune/packet effects.
 * Both spell/spell and melee/spell are exercised multiple times.
 *
 * This bounded source test does not certify thread scheduling,
 * original SpawnPK player-credit or original XP/loot rules.
 */
public final class PlayableConcurrentLethalSingleWinnerTest {
    private PlayableConcurrentLethalSingleWinnerTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P541_CONCURRENT_LETHAL "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1301,1302,1303,1304}));
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
        final NpcEntity visible;
        final LocalLabWindStrikeNpcSettlement magic;
        final LocalCanonicalNpcAttackHandler melee;
        final SpellTargetRequest spell;
        final NpcAction basic;
        final Capture wire=new Capture();
        final int[] startingXp;

        Actor(World world,WorldNpc target,String username)
            throws Exception{
            generation=world.registerPlayer(player,username);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            visible=scene.spawnMirroredNpc(
                target.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(visible!=null,"source race NPC scene missing");
            visible.bindCanonicalId(target.id);
            for(int item:new int[]{556,558}){
                String seeded=player.bank().spawnItem(
                    item,2,
                    G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                        new ByteArrayOutputStream()));
                check(seeded!=null&&seeded.contains("OK"),
                    "race fixture rune seed failed "+item);
            }
            check(player.installLocalLabSharedPvmAdmission(
                    world,generation),"source shared gate not installed");
            check(player.installLocalLabWindStrikeRecovery(
                    world,generation,3),"source spell cadence not installed");
            magic=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            melee=new LocalCanonicalNpcAttackHandler(
                world,player,()->generation,
                player.equipment(),player.combatStyles(),scene);
            spell=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);
            basic=new NpcAction(72,visible.sceneIndex);
            startingXp=xp();
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=player.playerState().xp(i);
            return values;
        }

        boolean spellSettled(String response){
            return response.startsWith(
                "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED");
        }

        void runes(int quantity,String phase){
            check(player.bank().inventoryCount(556)==quantity&&
                player.bank().inventoryCount(558)==quantity,
                phase+" altered wrong owner's rune counts");
        }

        void close(World world){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldNpc npc;
        final Actor a;
        final Actor b;

        Fixture(int definition,String id)throws Exception{
            npc=world.npcs().spawn(definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P541_ONE_HP");
            a=new Actor(world,npc,id+"-first");
            b=new Actor(world,npc,id+"-second");
            check(!a.player.id().equals(b.player.id())&&
                world.clock().tick()==0&&
                world.pvmRecords().receiptCount()==0,
                "race actors or initial World tick not independent");
        }

        void noPhantomReward(){
            check(Arrays.equals(a.startingXp,a.xp())&&
                Arrays.equals(b.startingXp,b.xp())&&
                world.groundItems().snapshot().isEmpty(),
                "race created unreviewed XP or ground loot");
        }

        void oneCanonicalDeath(){
            NpcLifecycleService.Snapshot death=
                world.npcLifecycle().get(npc.id);
            check(death!=null&&death.dead()&&
                death.hitpoints==0&&death.deathTick==0&&
                world.clock().tick()==0,
                "two-worker contest did not produce one source tick death");
        }

        void close(){
            a.close(world);
            b.close(world);
            world.npcLifecycle().unregisterExact(npc);
            world.npcs().remove(npc.id);
            if(!world.closed())world.close();
        }
    }

    private static final class Pair<A,B> {
        final A a;
        final B b;
        Pair(A a,B b){this.a=a;this.b=b;}
    }

    private static <A,B> Pair<A,B> race(
        Callable<A> left,Callable<B> right
    )throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);
        Future<A> a=null;
        Future<B> b=null;
        try{
            a=pool.submit(()->{
                ready.countDown();
                if(!start.await(10,TimeUnit.SECONDS))
                    throw new IllegalStateException(
                        "P541_LEFT_BARRIER_TIMEOUT");
                return left.call();
            });
            b=pool.submit(()->{
                ready.countDown();
                if(!start.await(10,TimeUnit.SECONDS))
                    throw new IllegalStateException(
                        "P541_RIGHT_BARRIER_TIMEOUT");
                return right.call();
            });
            check(ready.await(10,TimeUnit.SECONDS),
                "workers did not both reach concurrency barrier");
            start.countDown();
            return new Pair<>(
                a.get(20,TimeUnit.SECONDS),
                b.get(20,TimeUnit.SECONDS));
        }finally{
            start.countDown();
            pool.shutdownNow();
            check(pool.awaitTermination(5,TimeUnit.SECONDS),
                "racing source workers did not terminate");
        }
    }

    private static void spellAgainstSpell(
        int definition,int iteration
    )throws Exception{
        Fixture f=new Fixture(definition,"p541-spell-"+iteration);
        try{
            Pair<String,String> result=race(
                ()->f.a.magic.settle(f.a.spell,f.a.wire.writer),
                ()->f.b.magic.settle(f.b.spell,f.b.wire.writer));
            boolean aWon=f.a.spellSettled(result.a);
            boolean bWon=f.b.spellSettled(result.b);
            check(aWon!=bWon,
                "simultaneous spells failed single-winner rule "+
                result.a+" / "+result.b);
            check((aWon?result.a:result.b).contains("newlyDead=true"),
                "winning spell did not own actual death");
            check("REJECTED_NPC_NOT_ALIVE".equals(
                    aWon?result.b:result.a),
                "losing spell was not precluded before rune/queue commit");
            Actor victor=aWon?f.a:f.b;
            Actor loser=aWon?f.b:f.a;
            victor.runes(1,"winning spell");
            loser.runes(2,"losing spell");
            check(victor.wire.bytes()>0&&
                loser.wire.bytes()==0,
                "losing spell emitted gameplay presentation");
            LocalLabWindStrikeHitWitness winner=
                victor.player.lastLocalLabWindStrikeAcceptedHit();
            check(winner!=null&&winner.newlyDied&&
                winner.attacker==victor.player&&
                winner.stillCurrentLethalRegistration()&&
                loser.player.lastLocalLabWindStrikeAcceptedHit()==null,
                "losing spell obtained immutable death witness");
            f.oneCanonicalDeath();
            PvmRecordService ledger=f.world.pvmRecords();
            check(ledger.receiptCount()==0&&
                ledger.snapshot(victor.player).kills==0,
                "concurrent spell auto-granted PvM kill");
            PvmRecordService.Receipt grant=
                ledger.recordSourceWindStrikeLethalIfCurrent(winner);
            check(grant.applied&&grant.kills==1&&
                ledger.recordSourceWindStrikeLethalIfCurrent(winner)
                    .replayed&&
                ledger.receiptCount()==1&&
                ledger.snapshot(victor.player).kills==1&&
                ledger.snapshot(loser.player).kills==0,
                "concurrent spell credited wrong owner or duplicated grant");
            check(victor.wire.bytes()==0&&
                loser.wire.bytes()==0,
                "explicit source receipt emitted extra gameplay packets");
            f.noPhantomReward();
        }finally{f.close();}
    }

    private static void meleeAgainstSpell(
        int definition,int iteration
    )throws Exception{
        Fixture f=new Fixture(definition,"p541-mixed-"+iteration);
        try{
            Pair<LocalCanonicalNpcAttackHandler.Result,String> result=
                race(
                    ()->f.a.melee.handle(
                        f.a.basic,f.a.visible,f.a.wire.writer),
                    ()->f.b.magic.settle(
                        f.b.spell,f.b.wire.writer));
            boolean meleeWon=
                result.a.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT;
            boolean spellWon=f.b.spellSettled(result.b);
            check(meleeWon!=spellWon,
                "concurrent melee/spell violated single-winner rule "+
                result.a+" / "+result.b);
            f.oneCanonicalDeath();
            PvmRecordService ledger=f.world.pvmRecords();
            check(ledger.receiptCount()==0,
                "concurrent mixed hit automatically granted source record");

            if(meleeWon){
                check(result.a.newlyDied&&
                    result.a.appliedDamage==1&&
                    result.a.acceptedHit!=null&&
                    result.a.acceptedHit.attacker==f.a.player&&
                    "REJECTED_NPC_NOT_ALIVE".equals(result.b)&&
                    f.b.player.lastLocalLabWindStrikeAcceptedHit()==null,
                    "lethal melee allowed competing source spell");
                f.a.runes(2,"lethal melee owner");
                f.b.runes(2,"blocked spell owner");
                check(f.a.wire.bytes()>0&&
                    f.b.wire.bytes()==0,
                    "blocked spell emitted bytes in melee victory");
                check(ledger.snapshot(f.a.player).kills==0&&
                    ledger.snapshot(f.b.player).kills==0,
                    "melee victory fabricated Wind Strike kill record");
            }else{
                check(spellWon&&
                    result.b.contains("newlyDead=true")&&
                    result.a.status!=
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    result.a.appliedDamage==0&&
                    result.a.acceptedHit==null,
                    "lethal spell allowed competing melee hit");
                f.a.runes(2,"blocked melee owner");
                f.b.runes(1,"lethal spell owner");
                check(f.a.wire.bytes()==0&&
                    f.b.wire.bytes()>0,
                    "blocked melee emitted a hit packet");
                LocalLabWindStrikeHitWitness winner=
                    f.b.player.lastLocalLabWindStrikeAcceptedHit();
                check(winner!=null&&winner.newlyDied&&
                    winner.attacker==f.b.player&&
                    winner.stillCurrentLethalRegistration(),
                    "winning source spell lost its death witness");
                PvmRecordService.Receipt credit=
                    ledger.recordSourceWindStrikeLethalIfCurrent(winner);
                check(credit.applied&&
                    ledger.recordSourceWindStrikeLethalIfCurrent(winner)
                        .replayed&&
                    ledger.receiptCount()==1&&
                    ledger.snapshot(f.b.player).kills==1&&
                    ledger.snapshot(f.a.player).kills==0&&
                    f.a.wire.bytes()==0&&
                    f.b.wire.bytes()==0,
                    "spell victory could not credit only original caster");
            }
            f.noPhantomReward();
        }finally{f.close();}
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        int definition=
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
        // Bounded, independently initialized World/lifecycle/owners.
        // No assumption that the scheduler exercises both winners;
        // each successful interleaving must nevertheless be safe.
        for(int round=0;round<6;round++){
            spellAgainstSpell(definition,round);
            meleeAgainstSpell(definition,round);
        }
        System.out.println(
            "P541_CONCURRENT_LETHAL_SINGLE_WINNER_PASS"+
            " realTwoWorkerBarrier=true"+
            " sixSpellVersusSpellAndSixMeleeVersusSpell=true"+
            " oneCanonicalNpcDeathPerWorld=true"+
            " onlyWinningSpellWitnessCanExplicitlyCredit=true"+
            " losingSpellSpendsNoRuneOrPackets=true"+
            " noUnreviewedCombatXpGroundLoot=true"+
            " originalSpawnpkPvMRewardRulesUnverified=true");
    }
}
