package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.167: in-place raw lock-leaf edits with restored lastModifiedTime
 * are detectable via POSIX unix:ctime where supported. No grant path.
 */
public final class G21167MailboxCutoverLockLeafCtimeIntegrationTest {
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.167 "+reason);
    }

    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    private static void veto(Checked call,String expected)
        throws Exception{
        try{
            call.run();
        }catch(IOException denied){
            check(String.valueOf(denied.getMessage()).contains(expected),
                "unexpected cutover veto "+denied+" expected "+expected);
            return;
        }
        throw new AssertionError("G21.167 positive cutover return "+expected);
    }

    private static FileTime optionalCtime(Path path)throws IOException{
        try{
            return (FileTime)Files.getAttribute(
                path,"unix:ctime",LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|
                IllegalArgumentException unavailable){
            return null;
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21167-lock-ctime-");
        try{
            FilePlayerRepository.PathResolver resolver=
                name->root.resolve(name+".properties");
            FilePlayerRepository repo=new FilePlayerRepository(resolver);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String account="g21167-legacy";
            WorldPlayer player=new WorldPlayer();
            player.markRegistered(account);
            writer.saveStrict(PlayerSnapshotCodec.capture(account,player));
            Path accountPath=resolver.resolve(account);
            byte[] accountOriginal=Files.readAllBytes(accountPath);
            byte[] signerCandidate=new byte[]{0,1,2};
            final String legacyVeto=
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION";

            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,signerCandidate),legacyVeto);
            Path lock=MailboxAccountPublicationCoordinator.lockPath(
                accountPath);
            byte[] initial="LOCK-BASE-V1\n".getBytes(
                StandardCharsets.US_ASCII);
            byte[] replaced="LOCK-EDIT-V1\n".getBytes(
                StandardCharsets.US_ASCII);
            check(initial.length==replaced.length,
                "replacement must retain lock-leaf size");
            Files.write(lock,initial,StandardOpenOption.TRUNCATE_EXISTING);
            FileTime priorMtime=Files.getLastModifiedTime(
                lock,LinkOption.NOFOLLOW_LINKS);
            FileTime priorCtime=optionalCtime(lock);
            BasicFileAttributes originalAttrs=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            final boolean supported=priorCtime!=null;
            AtomicBoolean changed=new AtomicBoolean(false);

            final String ctimeVeto=
                "CUTOVER_LOCK_CTIME_CHANGED_UNSAFE_NO_AUTHORITY";
            if(supported){
                veto(()->repo.requireFreshCutoverUnderPublicationLock(
                    account,signerCandidate,lockedAccount->{
                        check(account.equals(lockedAccount),
                            "cutover account identity drift");
                        // Force a distinct ctime at coarse time resolution
                        // without changing the default 3s lock-acquire SLA
                        // (which only applies before the lock is held).
                        try{
                            Thread.sleep(1150L);
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new IOException(
                                "G21.167 interrupted test hook",
                                interrupted);
                        }
                        Files.write(lock,replaced,
                            StandardOpenOption.TRUNCATE_EXISTING);
                        Files.setLastModifiedTime(lock,priorMtime);
                        changed.set(true);
                    }),ctimeVeto);
            }else{
                // No unix:ctime view means there is no claim of
                // same-inode restored-mtime raw rewrite detection.
                veto(()->repo.requireFreshCutoverUnderPublicationLock(
                    account,signerCandidate),legacyVeto);
            }

            FileTime afterCtime=optionalCtime(lock);
            BasicFileAttributes afterAttrs=Files.readAttributes(
                lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            check(Objects.equals(
                originalAttrs.fileKey(),afterAttrs.fileKey())&&
                originalAttrs.size()==afterAttrs.size()&&
                originalAttrs.creationTime().equals(
                    afterAttrs.creationTime())&&
                priorMtime.equals(afterAttrs.lastModifiedTime()),
                "ctime case changed file identity, size or mtime");
            if(supported){
                check(changed.get()&&
                    !priorCtime.equals(afterCtime)&&
                    Arrays.equals(replaced,Files.readAllBytes(lock)),
                    "real same-inode restored-mtime rewrite not preserved");
            }else{
                check(Arrays.equals(initial,Files.readAllBytes(lock)),
                    "no-ctime control unexpectedly rewrote lock");
            }
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,signerCandidate),legacyVeto);
            check(Arrays.equals(
                accountOriginal,Files.readAllBytes(accountPath)),
                "cutover touched account bytes");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM lock or watch leak");
            System.out.println("G21167_LOCK_CTIME_DIAGNOSTICS"+
                " posixCtimeAvailable="+supported+
                " rawSameInodeRestoredMtimeVetoes="+
                    (supported?1:0)+
                " preservedRawLockBytes=true"+
                " healthyLegacyDenials=2"+
                " unchangedAccount=true zeroJvmLeases="+noLeaks);
            System.out.println("G21167_LOCK_CTIME_PASS"+
                " optionalCtimeCheckedInsideDenial=true"+
                " unsupportedProviderNotClaimed=true"+
                " completeWriterExclusion=false"+
                " independentProvenance=false atomicEpoch=false"+
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
