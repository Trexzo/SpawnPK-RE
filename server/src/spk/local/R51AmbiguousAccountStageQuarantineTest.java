package spk.local;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R51 independent repository crash-stage ambiguity regression. All bytes
 * live under a private disposable test directory. No real profile changes.
 */
public final class R51AmbiguousAccountStageQuarantineTest {
    @FunctionalInterface private interface IoAction {
        void run()throws IOException;
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-r51-ambiguous-stage-");
        try{
            frozenOldAccountCannotBeOverwritten(dir.resolve("existing"));
            missingCanonicalCannotBecomeDefault(dir.resolve("missing"));
            unrelatedProfileAndNamesUnaffected(dir.resolve("two-profiles"));
            activeInProgressStageAlsoFailsClosed(dir.resolve("active"));
            System.out.println(
                "R51_AMBIGUOUS_ACCOUNT_STAGE_QUARANTINE_PASS"+
                " existingAccountRefusesReadWrite=true"+
                " missingCanonicalRefusesDefaults=true"+
                " otherProfileIsolated=true"+
                " stageBytesNeverReadPromotedOrDeleted=true"+
                " activeStageAlsoDenied=true"+
                " controlledTestOnlyResolution=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> all=Files.walk(dir)){
                all.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException ignored){}
                });
            }
        }
    }

    private static void frozenOldAccountCannotBeOverwritten(Path root)
        throws Exception{
        Files.createDirectories(root);
        Path file=root.resolve("opensrc.properties");
        FilePlayerRepository repo=new FilePlayerRepository(user->file);
        PlayerSnapshot baseline=PlayerSnapshotCodec.capture(
            "opensrc",new WorldPlayer());
        repo.save(baseline);
        byte[] canonical=Files.readAllBytes(file);

        Path stage=root.resolve(".opensrc.properties.r16-recoveryfixture.stage");
        byte[] staged="TEST_ONLY_R51_AMBIGUOUS_NEWER_ACCOUNT_BYTES".getBytes(
            java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(stage,staged,StandardOpenOption.CREATE_NEW);
        expectAmbiguous(()->repo.load("opensrc"));
        expectAmbiguous(()->repo.save(baseline));
        require(Arrays.equals(Files.readAllBytes(file),canonical),
                "ambiguous load/save changed canonical");
        require(Arrays.equals(Files.readAllBytes(stage),staged),
                "ambiguous stage was read/promoted/deleted or modified");
        require(countEntries(root)==2,
                "unexpected staging sibling created while quarantined");

        // Only test-owned recovery evidence is cleared here, after equality
        // checks above. Real operator profiles must never be auto-cleaned.
        Files.delete(stage);
        require(repo.load("opensrc").orElseThrow(
            ()->new AssertionError("reviewed old account absent"))
            .values().equals(baseline.values()),
            "controlled old canonical resume failed");
        repo.save(baseline);
    }

    private static void missingCanonicalCannotBecomeDefault(Path root)
        throws Exception{
        Files.createDirectories(root);
        Path file=root.resolve("opensrc.properties");
        Path stage=root.resolve(".opensrc.properties.r16-unpublished.stage");
        Files.write(stage,new byte[]{42,8},StandardOpenOption.CREATE_NEW);
        FilePlayerRepository repo=new FilePlayerRepository(user->file);
        PlayerSnapshot baseline=PlayerSnapshotCodec.capture(
            "opensrc",new WorldPlayer());
        expectAmbiguous(()->repo.load("opensrc"));
        expectAmbiguous(()->repo.save(baseline));
        require(!Files.exists(file),"missing canonical replaced by defaults");
        require(Files.readAllBytes(stage).length==2,
                "missing canonical staged evidence changed");
        Files.delete(stage);
        require(!repo.load("opensrc").isPresent(),
                "nonexistent account should be empty after TEST-only clearance");
    }

    private static void unrelatedProfileAndNamesUnaffected(Path root)
        throws Exception{
        Files.createDirectories(root);
        FilePlayerRepository repo=new FilePlayerRepository(
            user->root.resolve(user+".properties"));
        PlayerSnapshot primary=PlayerSnapshotCodec.capture(
            "opensrc",new WorldPlayer());
        PlayerSnapshot secondary=PlayerSnapshotCodec.capture(
            "src",new WorldPlayer());
        repo.save(primary);
        repo.save(secondary);
        Path unrelated=root.resolve(".opensrc.properties.r16-.stage");
        Path lookalike=root.resolve("opensrc.properties.r16-fake.stage");
        Files.write(unrelated,new byte[]{1});
        Files.write(lookalike,new byte[]{2});
        require(repo.load("opensrc").isPresent(),
                "invalid lookalike blocked actual profile");
        Path trueStage=root.resolve(".opensrc.properties.r16-own.stage");
        Files.write(trueStage,new byte[]{3});
        expectAmbiguous(()->repo.load("opensrc"));
        require(repo.load("src").isPresent(),
                "other exact profile blocked by unrelated stage");
        repo.save(secondary);
        require(Files.exists(trueStage),"save of other profile modified evidence");
    }

    private static void activeInProgressStageAlsoFailsClosed(Path root)
        throws Exception{
        Files.createDirectories(root);
        Path file=root.resolve("opensrc.properties");
        FilePlayerRepository repo=new FilePlayerRepository(user->file);
        PlayerSnapshot baseline=PlayerSnapshotCodec.capture(
            "opensrc",new WorldPlayer());
        repo.save(baseline);
        Properties props=baseline.toLegacyProperties();
        AtomicInteger observed=new AtomicInteger();
        AtomicAccountPublisher.publish(
            file,props,"R51 active publisher test",
            (staged,target)->{
                require(Files.isRegularFile(staged),
                        "publisher did not first force a staging file");
                expectAmbiguous(()->repo.load("opensrc"));
                expectAmbiguous(()->repo.save(baseline));
                require(Files.exists(staged),
                        "active staging evidence was removed by guard");
                observed.incrementAndGet();
                Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            });
        require(observed.get()==1,"active-stage check not exercised");
        require(repo.load("opensrc").isPresent(),
                "post-replace canonical snapshot not loadable");
        require(countEntries(root)==1,
                "successful atomic publisher left a stage behind");
    }

    private static long countEntries(Path directory)throws IOException{
        try(java.util.stream.Stream<Path> entries=Files.list(directory)){
            return entries.count();
        }
    }

    private static void expectAmbiguous(IoAction action)throws IOException{
        try{
            action.run();
        }catch(IOException expected){
            require(expected.getMessage().contains(
                "R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED"),
                "unexpected failure type: "+expected);
            return;
        }
        throw new AssertionError("R51 ambiguous account operation accepted");
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }
}
