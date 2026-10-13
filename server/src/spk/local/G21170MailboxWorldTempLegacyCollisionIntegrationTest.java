package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.170: preserve the historical unguarded direct save() but
 * prohibit it from stealing the guarded World's serialization temp.
 * This does NOT prevent later uncooperative account overwrites.
 */
public final class G21170MailboxWorldTempLegacyCollisionIntegrationTest {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.170 "+message);
    }

    private static PlayerSnapshot state(String account,String label){
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(account);
        PlayerSnapshot baseline=PlayerSnapshotCodec.capture(account,player);
        TreeMap<String,String> values=new TreeMap<>(baseline.values());
        values.put("extension.g21170.fixture",label);
        return new PlayerSnapshot(
            PlayerSnapshot.CURRENT_VERSION,account,values);
    }

    private static boolean sameSnapshot(
        FilePlayerRepository repo,String account,PlayerSnapshot expected
    )throws IOException{
        return repo.load(account).isPresent()&&
            repo.load(account).get().values().equals(expected.values());
    }

    private static long tempCount(Path root,String account)
        throws IOException{
        try(Stream<Path> items=Files.list(root)){
            return items.filter(path->{
                String name=path.getFileName().toString();
                return name.startsWith(account+".properties.g21170-world-")&&
                    name.endsWith(".tmp");
            }).count();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21170-temp-collision-");
        try{
            FilePlayerRepository.PathResolver resolver=
                account->root.resolve(account+".properties");
            FilePlayerRepository raw=new FilePlayerRepository(resolver);
            String account="g21170-contended";
            Path file=resolver.resolve(account);
            Path legacyTemp=file.resolveSibling(
                file.getFileName().toString()+".tmp");
            PlayerSnapshot initial=state(account,"INITIAL");
            PlayerSnapshot world=state(account,"WORLD_FINAL");
            PlayerSnapshot rawInterloper=state(account,"RAW_IN_WINDOW");
            raw.save(initial);

            // A preexisting uncooperative legacy temp is NOT the new
            // guarded World temp and may not be truncated/deleted.
            byte[] sentinel="LEGACY_RAW_TEMP_SENTINEL\n"
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(legacyTemp,sentinel);
            FilePlayerRepository guarded=new FilePlayerRepository(resolver);
            guarded.saveForWorld(world);
            check(sameSnapshot(raw,account,world),
                "normal guarded World write did not land");
            check(Arrays.equals(sentinel,Files.readAllBytes(legacyTemp)),
                "World operation changed a preexisting raw temp");
            check(tempCount(root,account)==0,
                "successful guarded World leaked unique tmp");
            // Test-fixture cleanup only, never in production writer.
            Files.delete(legacyTemp);

            // Deterministic real writer collision: the World has
            // completed serialization but not acquired the G21.39
            // publication lock when this direct save intervenes.
            AtomicBoolean hookFired=new AtomicBoolean();
            AtomicBoolean uniqueTempSurvivedRaw=new AtomicBoolean();
            FilePlayerRepository interleaved=new FilePlayerRepository(
                resolver,selected->{
                    check(account.equals(selected),
                        "test hook account identity changed");
                    check(tempCount(root,account)==1,
                        "guarded World did not serialize unique tmp");
                    raw.save(rawInterloper);
                    hookFired.set(true);
                    uniqueTempSurvivedRaw.set(tempCount(root,account)==1);
                    check(sameSnapshot(raw,account,rawInterloper),
                        "actual direct raw writer did not publish");
                });
            interleaved.saveForWorld(world);
            check(hookFired.get()&&uniqueTempSurvivedRaw.get()&&
                sameSnapshot(raw,account,world),
                "raw writer collided with World temp/publication");
            check(!Files.exists(legacyTemp,LinkOption.NOFOLLOW_LINKS)&&
                tempCount(root,account)==0,
                "interleaved writers left tmp artifacts");

            // Failure after serializing the unique World temp must
            // clean ONLY that owned temp, never an unrelated .tmp.
            byte[] beforeFailure=Files.readAllBytes(file);
            Files.write(legacyTemp,sentinel);
            FilePlayerRepository interrupted=new FilePlayerRepository(
                resolver,selected->{
                    check(tempCount(root,account)==1,
                        "injected failure lacked World temp");
                    throw new IOException(
                        "G21.170 INJECTED_WORLD_BEFORE_REPLACE_NO_GRANT");
                });
            boolean refused=false;
            try{
                interrupted.saveForWorld(state(account,"MUST_NOT_PUBLISH"));
            }catch(IOException expected){
                refused=String.valueOf(expected.getMessage()).contains(
                    "G21.170 INJECTED_WORLD_BEFORE_REPLACE_NO_GRANT");
            }
            check(refused&&
                Arrays.equals(beforeFailure,Files.readAllBytes(file))&&
                Arrays.equals(sentinel,Files.readAllBytes(legacyTemp))&&
                tempCount(root,account)==0,
                "failure did not preserve raw temp/account and clean World");

            // A different account's guarded World save is unaffected.
            String other="g21170-independent";
            guarded.saveForWorld(state(other,"INDEPENDENT"));
            check(sameSnapshot(raw,other,state(other,"INDEPENDENT")),
                "independent account stopped saving");

            // Document the honest boundary: after World publishes, a
            // subsequent uncooperative raw save CAN still overwrite
            // the account. No G21.170 writer-fencing claim is possible.
            Files.delete(legacyTemp);
            raw.save(rawInterloper);
            check(sameSnapshot(raw,account,rawInterloper),
                "raw direct save unexpectedly acquired new fence");
            check(tempCount(root,account)==0,
                "post-publication raw fixture left World temp");

            boolean noLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeases,"JVM publication lease or watch leaked");
            System.out.println("G21170_WORLD_TEMP_COLLISION_DIAGNOSTICS"+
                " worldSuccessfulWithPreexistingRawTemp=1"+
                " rawInterleavingAtBeforeWorldReplace=1"+
                " uniqueWorldTempSurvivedDirectRaw=1"+
                " injectedFailureOwnTempCleaned=1"+
                " rawTempSentinelPreserved=2"+
                " independentWorldSave=1"+
                " futureRawOverwriteStillPossible=true"+
                " zeroJvmLeases="+noLeases);
            System.out.println("G21170_WORLD_TEMP_COLLISION_PASS"+
                " uniqueWorldTemp=true"+
                " legacyDirectFixedTempUnchanged=true"+
                " worldAndRawTempIndependent=true"+
                " completeRawWriterExclusion=false"+
                " rawPostpublicationOverridePossible=true"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
