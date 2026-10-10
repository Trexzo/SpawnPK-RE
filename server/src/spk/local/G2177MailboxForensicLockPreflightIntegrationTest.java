package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.77 pre-lock path safety. No reward grant or admission. */
public final class G2177MailboxForensicLockPreflightIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2177-lock-preflight-");
        Path real=Files.createDirectory(root.resolve("real"));
        Path alias=root.resolve("alias");
        Path ordinary=Files.createDirectory(root.resolve("ordinary"));
        boolean ancestorDenied=false;
        boolean redirectedLockNotCreated=false;
        boolean sentinelUnchanged=false;
        boolean symlinkLeafDenied=false;
        boolean standardWitnessWorks=false;
        boolean missingAccountStable=false;
        boolean oldBlockingPathUnchanged=false;
        boolean leasesClean=false;
        boolean noUnintendedMarker=false;
        try{
            Path sentinel=real.resolve("sentinel.txt");
            byte[] reference="DO_NOT_MODIFY_G2177".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII);
            Files.write(sentinel,reference);
            Files.createSymbolicLink(alias,real.getFileName());
            FilePlayerRepository redirect=new FilePlayerRepository(
                a->alias.resolve(a+".properties"));
            Path redirectedAccount=alias.resolve("g2177-alias.properties");
            Path redirectedLock=MailboxAccountPublicationCoordinator
                .lockPath(redirectedAccount);
            try{
                redirect.captureRestartContinuityTokenReadOnly(
                    "g2177-alias");
            }catch(IOException refused){
                ancestorDenied=refused.getMessage().contains(
                    "G21.77 RECOVERY_LOCK_ANCESTRY_UNSAFE_NO_GRANT");
            }
            redirectedLockNotCreated=
                !Files.exists(real.resolve(
                    redirectedLock.getFileName()),
                    LinkOption.NOFOLLOW_LINKS);

            Path file=ordinary.resolve("g2177-leaf.properties");
            Path lock=MailboxAccountPublicationCoordinator.lockPath(file);
            Files.createSymbolicLink(lock,sentinel);
            FilePlayerRepository repository=new FilePlayerRepository(
                a->ordinary.resolve(a+".properties"));
            try{
                repository.captureRestartContinuityTokenReadOnly(
                    "g2177-leaf");
            }catch(IOException refused){
                symlinkLeafDenied=refused.getMessage().contains(
                    "G21.77 RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT");
            }
            sentinelUnchanged=java.util.Arrays.equals(reference,
                Files.readAllBytes(sentinel));
            Files.delete(lock);

            String token=repository.captureRestartContinuityTokenReadOnly(
                "g2177-leaf");
            standardWitnessWorks=token.startsWith(
                "G2185|g2177-leaf|MISSING_ACCOUNT_NO_REPLAY|");
            FilePlayerRepository fresh=new FilePlayerRepository(
                a->ordinary.resolve(a+".properties"));
            missingAccountStable=fresh
                .compareRestartContinuityReadOnly(
                    "g2177-leaf",token).state==
                FilePlayerRepository.RestartContinuityComparison.State
                    .UNCHANGED_FORENSICS_NO_GRANT;
            AtomicBoolean executed=new AtomicBoolean();
            MailboxAccountPublicationCoordinator.withExclusivePublication(
                file,()->{
                    executed.set(true);
                    return null;
                });
            oldBlockingPathUnchanged=executed.get();
            noUnintendedMarker=!Files.exists(
                ordinary.resolve(
                    "g2177-leaf.properties.g2148-strict-write-intent"))&&
                !Files.exists(
                    ordinary.resolve(
                    "g2177-leaf.properties.g2132-mailbox-review"));
            leasesClean=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
        }finally{
            try(Stream<Path> entries=Files.walk(root)){
                for(Path p:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println("G2177_LOCK_PREFLIGHT_DIAGNOSTICS"+
            " ancestorDenied="+ancestorDenied+
            " redirectedLockNotCreated="+redirectedLockNotCreated+
            " symlinkLeafDenied="+symlinkLeafDenied+
            " sentinelUnchanged="+sentinelUnchanged+
            " standardWitnessWorks="+standardWitnessWorks+
            " missingAccountStable="+missingAccountStable+
            " oldBlockingPathUnchanged="+oldBlockingPathUnchanged+
            " noUnintendedMarker="+noUnintendedMarker+
            " leasesClean="+leasesClean);
        if(!(ancestorDenied&&redirectedLockNotCreated&&
             symlinkLeafDenied&&sentinelUnchanged&&
             standardWitnessWorks&&missingAccountStable&&
             oldBlockingPathUnchanged&&noUnintendedMarker&&leasesClean))
            throw new AssertionError("G21.77 forensic lock preflight NO_GRANT");
        System.out.println("G2177_LOCK_PREFLIGHT_PASS"+
            " redirectedLockFileCreated=false"+
            " symlinkLeafOpened=false grant=false replay=false"+
            " admission=false release=false");
    }
    private G2177MailboxForensicLockPreflightIntegrationTest(){}
}
