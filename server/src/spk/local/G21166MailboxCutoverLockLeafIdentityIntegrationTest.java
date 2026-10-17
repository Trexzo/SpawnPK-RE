package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.166: detect a raw lock-leaf replacement that ignores the held
 * cooperating OS advisory lock. Nothing here authorizes recovery.
 */
public final class G21166MailboxCutoverLockLeafIdentityIntegrationTest {
    private static void check(boolean pass,String reason){
        if(!pass)throw new AssertionError("G21.166 "+reason);
    }

    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    private static void veto(Checked operation,String expected)
        throws Exception {
        try{
            operation.run();
        }catch(IOException refused){
            check(String.valueOf(refused.getMessage()).contains(expected),
                "unexpected refusal "+refused+" expected "+expected);
            return;
        }
        throw new AssertionError("G21.166 returned authority for "+expected);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21166-lock-leaf-");
        try{
            FilePlayerRepository.PathResolver resolver=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=new FilePlayerRepository(resolver);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String account="g21166-legacy";
            WorldPlayer player=new WorldPlayer();
            player.markRegistered(account);
            writer.saveStrict(PlayerSnapshotCodec.capture(account,player));
            Path accountFile=resolver.resolve(account);
            Path lock=MailboxAccountPublicationCoordinator.lockPath(
                accountFile);
            byte[] original=Files.readAllBytes(accountFile);
            byte[] candidate=new byte[]{1,2,3};

            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                account,candidate),
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION");
            check(Files.isRegularFile(lock),
                "original cooperating lock leaf missing");

            AtomicBoolean replaced=new AtomicBoolean(false);
            AtomicBoolean deletionBlockedByOs=new AtomicBoolean(false);
            byte[] replacement="G21166_EXTERNAL_LOCK_REPLACEMENT\n"
                .getBytes(StandardCharsets.US_ASCII);
            try{
                repository.requireFreshCutoverUnderPublicationLock(
                    account,candidate,heldAccount->{
                        check(account.equals(heldAccount),
                            "cutover lock account drift");
                        // Deliberately bypass G21.39, while this thread
                        // holds its original live FileLock. POSIX allows
                        // unlink of the open locked inode; Windows may
                        // deny it. Do not forge a replacement in that case.
                        try{
                            Files.delete(lock);
                        }catch(AccessDeniedException unavailable){
                            deletionBlockedByOs.set(true);
                            return;
                        }
                        Files.write(lock,replacement,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        replaced.set(true);
                    });
                throw new AssertionError(
                    "G21.166 cutover unexpectedly returned successfully");
            }catch(IOException refused){
                String expected=replaced.get()
                    ?"CUTOVER_LOCK_LEAF_CHANGED_UNSAFE_NO_AUTHORITY"
                    :"NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION";
                check(String.valueOf(refused.getMessage()).contains(expected),
                    "incorrect in-lock raw-replacement denial "+refused+
                    " expected "+expected);
            }
            check(replaced.get()||deletionBlockedByOs.get(),
                "neither real unlink+recreate nor documented OS refusal");
            if(replaced.get()){
                check(Arrays.equals(replacement,Files.readAllBytes(lock)),
                    "raw replacement evidence was removed");
            }
            check(Arrays.equals(original,Files.readAllBytes(accountFile)),
                "cutover modified original account");
            // Even after an attacked observation, future optional
            // checks remain denials. Replaced lock path is a fresh
            // filesystem leaf and never an approval credential.
            veto(()->repository.requireFreshCutoverUnderPublicationLock(
                account,candidate),
                "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM publication lease or watch leaked");
            System.out.println("G21166_LOCK_LEAF_DIAGNOSTICS"+
                " rawReplacementDetected="+replaced.get()+
                " osProhibitedUnlink="+deletionBlockedByOs.get()+
                " healthyLegacyDenials=2"+
                " originalAccountUnchanged=true"+
                " rawReplacementEvidencePreserved="+replaced.get()+
                " zeroJvmLeases="+noLeaks);
            System.out.println("G21166_LOCK_LEAF_PASS"+
                " realRawLockPathMutationConditional=true"+
                " postDenialLockIdentityChecked=true"+
                " pathObjectNotOpenHandleProof=true"+
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
