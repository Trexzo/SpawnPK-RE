package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.171: reject redirected World temp ancestry BEFORE directory
 * creation and snapshot serialization. Legacy direct save is separate,
 * deliberately still unguarded. No reward/recovery authority.
 */
public final class G21171MailboxWorldTempPreSerializationAncestryIntegrationTest {
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.171 "+reason);
    }

    private static PlayerSnapshot snapshot(String account){
        WorldPlayer owner=new WorldPlayer();
        owner.markRegistered(account);
        return PlayerSnapshotCodec.capture(account,owner);
    }

    private static boolean refusedWorld(FilePlayerRepository repo,
                                        String account,String expected)
        throws Exception{
        try{
            repo.saveForWorld(snapshot(account));
        }catch(IOException denied){
            check(String.valueOf(denied.getMessage()).contains(expected),
                "unexpected refusal "+denied+" expected "+expected);
            return true;
        }
        return false;
    }

    private static long tempCount(Path root)throws IOException{
        try(Stream<Path> paths=Files.walk(root)){
            return paths.filter(p->Files.isRegularFile(p,
                LinkOption.NOFOLLOW_LINKS))
                .filter(p->p.getFileName().toString().endsWith(".tmp"))
                .count();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21171-world-temp-root-");
        try{
            String account="g21171-guarded";
            FilePlayerRepository.PathResolver normal=
                who->root.resolve("healthy").resolve(who+".properties");
            FilePlayerRepository safe=new FilePlayerRepository(normal);
            safe.saveForWorld(snapshot(account));
            Path healthy=normal.resolve(account);
            check(Files.isRegularFile(healthy)&&
                safe.load(account).isPresent(),
                "actual ordinary World save failed");

            // The parent may be absent initially. Existing ancestors
            // must be safe; mkdir is then allowed, followed by a
            // STRICT post-create ancestor check before temp I/O.
            FilePlayerRepository.PathResolver newlyCreated=
                who->root.resolve("created").resolve("deep")
                    .resolve(who+".properties");
            FilePlayerRepository nested=
                new FilePlayerRepository(newlyCreated);
            String nestedAccount="g21171-new";
            nested.saveForWorld(snapshot(nestedAccount));
            check(Files.isRegularFile(
                newlyCreated.resolve(nestedAccount)),
                "legitimate missing World directory was rejected");

            Path victim=root.resolve("external-victim");
            Files.createDirectories(victim);
            Path externalSentinel=victim.resolve("sentinel.txt");
            byte[] sentinel="G21171_VICTIM_PRESERVE\n"
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(externalSentinel,sentinel);

            // Alias to a different writable account root. G21.169
            // would deny only AFTER World serialized its temp there.
            // G21.171 must refuse before ANY output is created.
            Path alias=root.resolve("redirected-root");
            boolean symlinkSupported=true;
            try{
                Files.createSymbolicLink(alias,victim);
            }catch(UnsupportedOperationException|
                    java.nio.file.AccessDeniedException|
                    SecurityException unsupported){
                symlinkSupported=false;
            }
            AtomicBoolean readOnlyVeto=new AtomicBoolean(false);
            AtomicBoolean rawLegacyPreserved=new AtomicBoolean(false);
            if(symlinkSupported){
                FilePlayerRepository.PathResolver redirected=
                    who->alias.resolve(who+".properties");
                FilePlayerRepository hostile=new FilePlayerRepository(
                    redirected);
                String hostileAccount="g21171-hostile";
                readOnlyVeto.set(refusedWorld(hostile,hostileAccount,
                    "WORLD_TEMP_ANCESTRY_UNSAFE_NO_AUTHORITY"));
                check(!Files.exists(
                        victim.resolve(hostileAccount+".properties"),
                        LinkOption.NOFOLLOW_LINKS)&&
                    !Files.exists(victim.resolve(
                        hostileAccount+".properties.tmp"),
                        LinkOption.NOFOLLOW_LINKS)&&
                    tempCount(victim)==0,
                    "World wrote to redirected victim before refusal");
                check(Arrays.equals(
                        sentinel,Files.readAllBytes(externalSentinel)),
                    "World touched victim sentinel");

                // Explicit proof the historical direct repository save
                // was NOT silently redefined by the World-only guard.
                String rawAccount="g21171-raw-legacy";
                hostile.save(snapshot(rawAccount));
                rawLegacyPreserved.set(
                    Files.isRegularFile(victim.resolve(
                        rawAccount+".properties")));
                check(rawLegacyPreserved.get(),
                    "direct legacy writer was accidentally fenced");
            }

            // A regular file used as an ancestor must not be used as
            // a directory or allowed to reach temp serialization.
            Path notDirectory=root.resolve("non-directory");
            Files.write(notDirectory,sentinel);
            FilePlayerRepository.PathResolver blocked=
                who->notDirectory.resolve(who+".properties");
            FilePlayerRepository bad=new FilePlayerRepository(blocked);
            check(refusedWorld(bad,"g21171-invalid-parent",
                "WORLD_TEMP_ANCESTRY_UNSAFE_NO_AUTHORITY"),
                "non-directory ancestor was admitted");
            check(Arrays.equals(sentinel,Files.readAllBytes(notDirectory)),
                "non-directory ancestor was altered");

            safe.saveForWorld(snapshot("g21171-independent"));
            check(Files.isRegularFile(normal.resolve("g21171-independent")),
                "unrelated World save stopped working");
            check(tempCount(root)==0,
                "World operation left temporary artifacts");
            boolean zeroLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(zeroLeases,"JVM leases/watch scopes leaked");

            System.out.println("G21171_WORLD_TEMP_ANCESTRY_DIAGNOSTICS"+
                " normalWorldSaves=2 nestedNewDirectorySaves=1"+
                " realSymlinkAncestryVetoes="+(readOnlyVeto.get()?1:0)+
                " nonDirectoryAncestorVetoes=1"+
                " rawLegacyStillUnprotected="+rawLegacyPreserved.get()+
                " symlinkSupported="+symlinkSupported+
                " victimAccountAndTempNotCreated=true"+
                " zeroWorldTempArtifacts=true"+
                " zeroJvmLeases="+zeroLeases);
            System.out.println("G21171_WORLD_TEMP_ANCESTRY_PASS"+
                " beforeMkdirNoFollow=true"+
                " afterMkdirNoFollowBeforeSerialize=true"+
                " normalWorldAndNestedCreationWork=true"+
                " legacyRawSaveUnchanged=true"+
                " fullUncooperativeWriterExclusion=false"+
                " pathRaceFullyExcluded=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
