package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.168: real, same-inode and same-size raw lock-leaf rewrite with
 * restored mtime. Test independent SHA witness under a real OS lock,
 * then verify deny-only cutover. Never grants or repairs live state.
 */
public final class G21168MailboxCutoverLockLeafDigestIntegrationTest {
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.168 "+reason);
    }

    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    private static void veto(Checked operation,String expected)
        throws Exception{
        try{
            operation.run();
        }catch(IOException refused){
            check(String.valueOf(refused.getMessage()).contains(expected),
                "unexpected refusal "+refused+" expected "+expected);
            return;
        }
        throw new AssertionError("G21.168 unexpected positive return "+
            expected);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21168-lock-digest-");
        try{
            FilePlayerRepository.PathResolver resolve=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=new FilePlayerRepository(resolve);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolve);
            String account="g21168-legacy";
            WorldPlayer player=new WorldPlayer();
            player.markRegistered(account);
            writer.saveStrict(PlayerSnapshotCodec.capture(account,player));
            Path accountFile=resolve.resolve(account);
            byte[] accountOriginal=Files.readAllBytes(accountFile);
            byte[] candidate=new byte[]{1,2,3};
            String legacy=
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION";
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                account,candidate),legacy);

            Path lock=MailboxAccountPublicationCoordinator.lockPath(
                accountFile);
            byte[] before="LOCK-ALPHA\n".getBytes(
                StandardCharsets.US_ASCII);
            byte[] after="LOCK-BRAVO\n".getBytes(
                StandardCharsets.US_ASCII);
            check(before.length==after.length,
                "raw same-inode fixture changed byte length");
            Files.write(lock,before,StandardOpenOption.TRUNCATE_EXISTING);
            FileTime originalMtime=Files.getLastModifiedTime(
                lock,LinkOption.NOFOLLOW_LINKS);

            // The production digest helper is exercised under an actual
            // held G21.39 lock, but without optional unix:ctime gating.
            // This demonstrates the byte evidence independently from
            // POSIX ctime availability.
            MailboxAccountPublicationCoordinator.withExclusivePublicationBounded(
                accountFile,3000L,()->{
                    String first=FilePlayerRepository
                        .cutoverLockLeafContentWitness(accountFile);
                    Files.write(lock,after,
                        StandardOpenOption.TRUNCATE_EXISTING);
                    Files.setLastModifiedTime(lock,originalMtime);
                    String second=FilePlayerRepository
                        .cutoverLockLeafContentWitness(accountFile);
                    check(!first.equals(second),
                        "same-size raw lock rewrite escaped SHA-256");
                    return null;
                });
            check(Arrays.equals(after,Files.readAllBytes(lock))&&
                originalMtime.equals(Files.getLastModifiedTime(
                    lock,LinkOption.NOFOLLOW_LINKS)),
                "raw SHA case bytes/mtime were not preserved");

            // Real integrated cutover still refuses, whether POSIX
            // ctime or SHA-256 is the first evidence to reject.
            Files.write(lock,before,StandardOpenOption.TRUNCATE_EXISTING);
            FileTime sampleMtime=Files.getLastModifiedTime(
                lock,LinkOption.NOFOLLOW_LINKS);
            try{
                repository.requireFreshCutoverUnderPublicationLock(
                    account,candidate,selected->{
                        check(account.equals(selected),
                            "cutover callback account drift");
                        Files.write(lock,after,
                            StandardOpenOption.TRUNCATE_EXISTING);
                        Files.setLastModifiedTime(lock,sampleMtime);
                    });
                throw new AssertionError(
                    "G21.168 missing negative cutover veto");
            }catch(IOException refusal){
                String reason=String.valueOf(refusal.getMessage());
                check(reason.contains(
                        "CUTOVER_LOCK_CTIME_CHANGED_UNSAFE_NO_AUTHORITY")||
                      reason.contains(
                        "CUTOVER_LOCK_BYTES_CHANGED_UNSAFE_NO_AUTHORITY"),
                    "raw lock edit was not rejected by ctime or SHA "+
                    refusal);
            }
            check(Arrays.equals(after,Files.readAllBytes(lock)),
                "denial removed attack evidence");
            check(Arrays.equals(accountOriginal,Files.readAllBytes(
                accountFile)),"cutover changed original account");

            // Too-large lock leaf must refuse BEFORE hashing unbounded
            // attacker-controlled data; evidence stays on disk.
            byte[] oversized=new byte[4097];
            Arrays.fill(oversized,(byte)'x');
            Files.write(lock,oversized,
                StandardOpenOption.TRUNCATE_EXISTING);
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                account,candidate),
                "CUTOVER_LOCK_CONTENT_UNSAFE_NO_AUTHORITY");
            check(Arrays.equals(oversized,Files.readAllBytes(lock)),
                "oversized lock leaf overwritten or cleaned");
            // This cleanup is TEST-ONLY; production cutover never mutates
            // lock contents or archives.
            Files.write(lock,before,
                StandardOpenOption.TRUNCATE_EXISTING);
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                account,candidate),legacy);

            check(Arrays.equals(accountOriginal,Files.readAllBytes(
                accountFile)),"test left account mutation");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM lock/watch scope leaked");
            System.out.println("G21168_LOCK_DIGEST_DIAGNOSTICS"+
                " realSameInodeSameSizeRawMutation=1"+
                " restoredMtime=true independentShaMismatch=1"+
                " integratedRawVetoes=1 oversizedLeafVetoes=1"+
                " healthyLegacyDenials=2"+
                " preservedAttackEvidence=true"+
                " originalAccountUnchanged=true zeroJvmLeases="+noLeaks);
            System.out.println("G21168_LOCK_DIGEST_PASS"+
                " cappedNoFollowSha256=true"+
                " independentOfOptionalCtime=true"+
                " antiAba=false fullWriterExclusion=false"+
                " independentProvenance=false atomicEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
