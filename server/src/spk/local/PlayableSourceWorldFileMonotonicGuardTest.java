package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * P5.46 negative-only World file publisher acceptance:
 * genuine source death receipts commit account count+review marker,
 * and a stale queued PlayerSnapshot cannot roll the persisted World
 * account file back across either marker or numeric kill count.
 *
 * This invokes the REAL FilePlayerRepository.saveForWorld path under
 * its existing cooperating publication lock. It is NOT an account
 * filesystem crash/FSYNC proof or a fence against nonparticipating
 * RAW writers.
 */
public final class PlayableSourceWorldFileMonotonicGuardTest {
    private PlayableSourceWorldFileMonotonicGuardTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P546_WORLD_SOURCE_FILE_MONOTONIC "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1801,1802,1803,1804}));

        int bytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        final String account;
        final long generation;
        final WorldNpc npc;
        final NpcRegistry scene;
        final LocalLabWindStrikeNpcSettlement magic;
        final SpellTargetRequest request;
        final Capture packets=new Capture();
        final int[] initialXp;

        Fixture(String account,int definition)throws Exception{
            this.account=account;
            generation=world.registerPlayer(player,account);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            npc=world.npcs().spawn(definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P546_ONE_HP");
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity shown=scene.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(shown!=null,"canonical source scene unavailable");
            shown.bindCanonicalId(npc.id);
            magic=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            request=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                shown.sceneIndex,-1,-1,-1,-1,-1);
            for(int rune:new int[]{556,558}){
                String result=player.bank().spawnItem(
                    rune,3,
                    G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                        new ByteArrayOutputStream()));
                check(result!=null&&result.contains("OK"),
                    "canonical rune seed failed "+rune);
            }
            initialXp=xp();
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=player.playerState().xp(i);
            return values;
        }

        PvmRecordService.Receipt settleAndRecord(
            long expectedDeathTick
        )throws Exception{
            String settled=magic.settle(request,packets.writer);
            LocalLabWindStrikeHitWitness proof=
                player.lastLocalLabWindStrikeAcceptedHit();
            check(settled.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                settled.contains("newlyDead=true")&&
                proof!=null&&proof.newlyDied&&
                proof.attacker==player&&
                proof.deathTick==expectedDeathTick&&
                proof.stillCurrentLethalRegistration()&&
                packets.bytes()>0,
                "real source spell hit not committed "+settled);
            check(world.pvmRecords().receiptCount()==expectedDeathTick,
                "source spell silently created a PvM record");
            PvmRecordService.Receipt credit=
                world.pvmRecords().recordSourceWindStrikeLethalIfCurrent(
                    proof);
            check(credit.applied&&credit.kills==expectedDeathTick+1&&
                world.pvmRecords().receiptCount()==expectedDeathTick+1&&
                world.pvmRecords().recordSourceWindStrikeLethalIfCurrent(
                    proof).replayed&&
                packets.bytes()==0,
                "actual source kill receipt incorrect or not idempotent");
            return credit;
        }

        void nextLife(String label){
            check(world.npcLifecycle().unregisterExact(npc),
                "NPC previous lifetime did not retire");
            world.clock().advance();
            world.npcLifecycle().register(
                npc,1,label);
        }

        void close(){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.npcLifecycle().unregisterExact(npc);
            world.npcs().remove(npc.id);
            if(!world.closed())world.close();
        }
    }

    private static String marker(PlayerSnapshot snapshot){
        return snapshot.value(
            "extension."+PvmRecordService.NAMESPACE+"."+
            PvmRecordService.SOURCE_RESTART_REVIEW_KEY);
    }

    private static long count(PlayerSnapshot snapshot){
        String raw=snapshot.value(
            "extension."+PvmRecordService.NAMESPACE+".kills");
        return raw==null?0L:Long.parseLong(raw);
    }

    private static void deniedWithoutMove(
        FilePlayerRepository repository,PlayerSnapshot offered,
        Path accountFile,String code
    )throws Exception{
        byte[] before=Files.readAllBytes(accountFile);
        try{
            repository.saveForWorld(offered);
            throw new AssertionError(
                "unsafe source snapshot rollback was accepted "+code);
        }catch(IOException denied){
            check(denied.toString().contains(code),
                "World save refusal had wrong reason "+denied);
        }
        check(Arrays.equals(before,Files.readAllBytes(accountFile)),
            "rejected source rollback still replaced disk account bytes");
    }

    private static void checkDisk(
        FilePlayerRepository repository,String username,
        long expected
    )throws Exception{
        Optional<PlayerSnapshot> loaded=repository.load(username);
        check(loaded.isPresent()&&
            PvmRecordService.SOURCE_RESTART_REVIEW_VALUE.equals(
                marker(loaded.get()))&&
            count(loaded.get())==expected,
            "World file source count/negative marker missing");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path temp=Files.createTempDirectory(
            "spk-p546-source-monotonic-");
        String account="p546-world-source-account";
        Path accountFile=temp.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->temp.resolve(name+".properties"));
        Fixture fixture=null;
        try{
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            fixture=new Fixture(account,definition);
            PlayerSnapshot before=
                PlayerSnapshotCodec.capture(
                    account,fixture.player);
            check(marker(before)==null&&count(before)==0,
                "before source receipt already carried persisted mark");

            fixture.settleAndRecord(0L);
            PlayerSnapshot one=
                PlayerSnapshotCodec.capture(
                    account,fixture.player);
            check(count(one)==1&&
                PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(marker(one)),
                "first real source receipt lacked negative account mark");
            repository.saveForWorld(one);
            checkDisk(repository,account,1L);

            // Source review marker was published on disk: an older
            // captured account must not erase it under the actual
            // cooperating World publication lock.
            deniedWithoutMove(
                repository,before,accountFile,
                "P546_WORLD_SAVE_SOURCE_MARKER_REGRESSION_NO_MOVE");
            checkDisk(repository,account,1L);

            // A legitimate later real second source death/record must
            // still be allowed to advance the same account and marker.
            fixture.nextLife("CUSTOM_LOCALLAB_P546_SECOND_LIFE");
            fixture.settleAndRecord(1L);
            PlayerSnapshot two=
                PlayerSnapshotCodec.capture(
                    account,fixture.player);
            check(count(two)==2&&
                PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(marker(two)),
                "second source receipt lost negative account marker");
            repository.saveForWorld(two);
            checkDisk(repository,account,2L);

            // A stale snapshot WITH a correct marker is still an
            // unsafe regression when its numeric source count is old.
            deniedWithoutMove(
                repository,one,accountFile,
                "P546_WORLD_SAVE_SOURCE_COUNT_REGRESSION_NO_MOVE");
            checkDisk(repository,account,2L);

            // Exact repeat of the newest persisted image is safe,
            // as is a different account with no source history.
            repository.saveForWorld(two);
            checkDisk(repository,account,2L);
            WorldPlayer unrelated=new WorldPlayer();
            PlayerSnapshot independent=
                PlayerSnapshotCodec.capture("p546-other-account",
                    unrelated);
            repository.saveForWorld(independent);
            check(repository.load("p546-other-account").isPresent()&&
                marker(repository.load("p546-other-account").get())==null,
                "unrelated account was blocked by source file guard");

            WorldPlayer reloaded=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                repository.load(account).get(),reloaded);
            check(reloaded!=fixture.player&&
                reloaded.lastLocalLabWindStrikeAcceptedHit()==null&&
                fixture.world.pvmRecords().snapshot(reloaded).kills==2&&
                PvmRecordService.SOURCE_RESTART_REVIEW_VALUE.equals(
                    reloaded.snapshotExtensions()
                        .namespace(PvmRecordService.NAMESPACE)
                        .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "persisted account restore inherited old source hit proof");
            check(Arrays.equals(fixture.initialXp,fixture.xp())&&
                fixture.world.groundItems().snapshot().isEmpty()&&
                fixture.player.bank().inventoryCount(556)==1&&
                fixture.player.bank().inventoryCount(558)==1&&
                fixture.packets.bytes()==0,
                "World file guard changed gameplay items, XP or packets");
            System.out.println(
                "P546_WORLD_SOURCE_FILE_MONOTONIC_PASS"+
                " actualWorldPublicationPath=true"+
                " preReceiptSnapshotMarkerRegressionDeniedNoMove=true"+
                " postReceiptLowerKillCountDeniedNoMove=true"+
                " twoRealSourceKillPostimagesPersist=true"+
                " unrelatedAccountWorldSaveAllowed=true"+
                " freshSnapshotRestoreKeepsNegativeOnlyEvidence=true"+
                " rawUncooperativeWriterAndCrashDurabilityUnverified=true");
        }finally{
            if(fixture!=null)fixture.close();
            try(java.util.stream.Stream<Path> stream=Files.walk(temp)){
                stream.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(
                            "P546 test cleanup failed "+path,failure);
                    }
                });
            }
        }
    }
}
