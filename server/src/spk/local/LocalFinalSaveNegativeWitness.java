package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Arrays;

/**
 * G21.298 EXPERIMENTAL, DEFAULT-OFF: one-shot, negative-only,
 * forced operator witness AFTER the strict opt-in World final save.
 *
 * A successful write is NOT an authenticated final-save ACK. The
 * ACTIVE review marker is never retired, and every subsequent login
 * remains denied. A crash at any cutpoint retains negative evidence
 * and authorizes NO recovery, replay, grant or owner handoff.
 */
final class LocalFinalSaveNegativeWitness {
    static final String PROPERTY=
        "spk.local.experimentalNegativeFinalSaveReviewWitness";
    static final String SUFFIX=".g21298-final-review-witness";

    // G21.300/301 package-only deterministic crash/fault seams.
    // Ordinary production never configures either probe. They run
    // under the cooperating account publication lock and cannot
    // relax any marker, source-file or forced-directory validation.
    @FunctionalInterface interface Probe {
        void run(Path witness)throws IOException;
    }
    private static volatile Probe afterWriteBeforeForceForTest;
    private static volatile Probe afterDirectoryForceForTest;
    static void setAfterWriteBeforeForceForTest(Probe p){
        afterWriteBeforeForceForTest=p;
    }
    static void setAfterDirectoryForceForTest(Probe p){
        afterDirectoryForceForTest=p;
    }
    private LocalFinalSaveNegativeWitness(){}

    static boolean enabled(){
        return Boolean.getBoolean(PROPERTY);
    }

    static void requireAdmissionConfiguration(boolean persistentAccount){
        if(!enabled()||!persistentAccount)return;
        if(!Boolean.getBoolean(LocalJvmAccountSessionLease.PROPERTY)||
           !Boolean.getBoolean(
               LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY)||
           !Boolean.getBoolean(LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
               LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY)||
           !Boolean.getBoolean(LocalReviewIntentAdmissionVeto.PROPERTY)||
           !Boolean.getBoolean(LocalOneShotActiveReviewOnAdmission.PROPERTY)||
           !Boolean.getBoolean(
               FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY))
            throw new IllegalStateException(
                "G21.298 NEGATIVE_FINAL_WITNESS_REQUIRES_ALL_EXPERIMENTAL_GATES");
    }

    /**
     * G21.302: a previously created (or forged/corrupted) final
     * review witness is ALWAYS a negative admission sidecar.
     * Never parse its body or use its presence as positive proof.
     * Check regardless of the current writer opt-in flag: a JVM
     * restart must not erase this conservative witness by toggling
     * optional instrumentation off. Missing is NOT proof of safety.
     */
    static void requireNoNegativeWitness(Path account)throws IOException{
        Path witness=witnessPath(account);
        try{
            Files.readAttributes(witness,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(java.nio.file.NoSuchFileException absent){
            return;
        }
        throw new IOException(
            "G21.302 NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
    }

    static Path witnessPath(Path account)throws IOException{
        Path selected=Objects.requireNonNull(account,"account")
            .toAbsolutePath().normalize();
        if(selected.getFileName()==null||selected.getParent()==null)
            throw new IOException("G21.298 INVALID_FINAL_ACCOUNT_PATH");
        return selected.resolveSibling(
            selected.getFileName().toString()+SUFFIX);
    }

    /**
     * Requires a completed strict opt-in REAL final World save and
     * exact original captured JVM lease at the worker call site.
     * This routine independently rechecks selected account and marker
     * evidence inside the existing cooperating publication lock.
     * A failed/partial CREATE_NEW is left as negative operator
     * evidence and can never be cleaned up automatically.
     */
    private static BasicFileAttributes requireSourceIdentity(
        Path selected,String code
    )throws IOException{
        BasicFileAttributes a=Files.readAttributes(selected,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null)
            throw new IOException(code);
        return a;
    }

    static Path publishOnce(
        Path account,String username,long generation,long sequence
    )throws IOException{
        Objects.requireNonNull(username,"username");
        if(!username.matches("[a-z0-9_-]{1,64}")||
           generation<=0L||sequence<=0L)
            throw new IOException(
                "G21.298 FINAL_WITNESS_OWNER_SEQUENCE_INVALID");
        final Path selected=account.toAbsolutePath().normalize();
        final Path output=witnessPath(selected);
        return MailboxAccountPublicationCoordinator.withExclusivePublication(
            selected,()->{
                if(Files.exists(output,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "G21.298 FINAL_WITNESS_ALREADY_PRESENT_NEGATIVE");
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(selected);
                LocalAccountForensicEvidence.Evidence before=
                    LocalAccountForensicEvidence.inspect(selected);
                final String markerBefore=before
                    .negativeSidecarContentWitnesses.get("activeReview");
                // G21.313/314: hashes alone cannot distinguish
                // byte-identical selected account or ACTIVE marker
                // pathname replacement during witness publication.
                final Path active=LocalDurableAccountReviewIntentPrimitive
                    .markerPath(selected);
                final BasicFileAttributes accountBeforeIdentity=
                    requireSourceIdentity(selected,
                        "G21.313 FINAL_WITNESS_ACCOUNT_IDENTITY_UNPROVEN");
                final BasicFileAttributes markerBeforeIdentity=
                    requireSourceIdentity(active,
                        "G21.314 FINAL_WITNESS_ACTIVE_IDENTITY_UNPROVEN");
                if(!"PRESENT_REGULAR".equals(before.accountState)||
                   !"1".equals(before.accountObservedLinkCount)||
                   !"PRESENT_REGULAR_NEGATIVE".equals(
                        before.negativeSidecars.get("activeReview"))||
                   markerBefore==null||
                   !markerBefore.matches("SHA256:[0-9a-f]{64}"))
                    throw new IOException(
                        "G21.298 FINAL_WITNESS_PHYSICAL_NEGATIVE_EVIDENCE_UNSAFE");
                String body=
                    "G21298_NEGATIVE_FINAL_REVIEW_V1\n"+
                    "account="+username+"\n"+
                    "worldGeneration="+generation+"\n"+
                    "worldSaveSequence="+sequence+"\n"+
                    "accountSha256="+before.accountSha256+"\n"+
                    "activeMarkerWitness="+markerBefore+"\n"+
                    "state=UNTRUSTED_OPERATOR_REVIEW_ONLY\n"+
                    "recoveryAuthorized=false\n"+
                    "ownerEpochProven=false\n"+
                    "finalSaveAck=false\n";
                byte[] bytes=body.getBytes(StandardCharsets.US_ASCII);
                // Capture original name-to-inode evidence before
                // either deterministic postwrite fault seam. A
                // substituted object of identical length/bytes is
                // still untrusted and must never be reported stable.
                final Object createdFileKey;
                try(FileChannel file=FileChannel.open(output,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer data=ByteBuffer.wrap(bytes);
                    while(data.hasRemaining())file.write(data);
                    BasicFileAttributes created=Files.readAttributes(
                        output,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    if(!created.isRegularFile()||
                       created.isSymbolicLink()||
                       created.fileKey()==null)
                        throw new IOException(
                            "G21.305 NEGATIVE_FINAL_WITNESS_INITIAL_IDENTITY_UNPROVEN");
                    createdFileKey=created.fileKey();
                    Probe beforeForce=afterWriteBeforeForceForTest;
                    if(beforeForce!=null)beforeForce.run(output);
                    file.force(true);
                }
                try(FileChannel directory=FileChannel.open(
                        selected.getParent(),StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)){
                    directory.force(true);
                }
                Probe afterForce=afterDirectoryForceForTest;
                if(afterForce!=null)afterForce.run(output);
                BasicFileAttributes attrs=Files.readAttributes(output,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
                   attrs.fileKey()==null||attrs.size()!=bytes.length)
                    throw new IOException(
                        "G21.298 NEGATIVE_FINAL_WITNESS_POSTWRITE_UNPROVEN");
                if(!createdFileKey.equals(attrs.fileKey()))
                    throw new IOException(
                        "G21.305 NEGATIVE_FINAL_WITNESS_FILE_OBJECT_CHANGED");
                // G21.304: stat length alone cannot catch a
                // same-inode, same-length rewrite after file and
                // parent-directory force. Verify the EXACT written
                // bytes through a second bounded, NOFOLLOW read.
                // This is untrusted observation, NOT a MAC.
                ByteBuffer reread=ByteBuffer.allocate(bytes.length+1);
                try(FileChannel verify=FileChannel.open(output,
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)){
                    while(reread.hasRemaining()){
                        int read=verify.read(reread);
                        if(read<0)break;
                        if(read==0)
                            throw new IOException(
                                "G21.304 NEGATIVE_FINAL_WITNESS_READ_NO_PROGRESS");
                    }
                }
                if(reread.position()!=bytes.length)
                    throw new IOException(
                        "G21.304 NEGATIVE_FINAL_WITNESS_POSTFORCE_BYTES_CHANGED");
                byte[] checked=Arrays.copyOf(reread.array(),
                    reread.position());
                if(!Arrays.equals(bytes,checked))
                    throw new IOException(
                        "G21.304 NEGATIVE_FINAL_WITNESS_POSTFORCE_BYTES_CHANGED");
                BasicFileAttributes finalIdentity=Files.readAttributes(
                    output,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if(!finalIdentity.isRegularFile()||
                   finalIdentity.isSymbolicLink()||
                   !createdFileKey.equals(finalIdentity.fileKey())||
                   finalIdentity.size()!=bytes.length)
                    throw new IOException(
                        "G21.305 NEGATIVE_FINAL_WITNESS_FILE_OBJECT_CHANGED");
                LocalAccountForensicEvidence.Evidence after=
                    LocalAccountForensicEvidence.inspect(selected);
                if(!before.accountSha256.equals(after.accountSha256)||
                   !markerBefore.equals(after
                       .negativeSidecarContentWitnesses.get("activeReview")))
                    throw new IOException(
                        "G21.298 NEGATIVE_FINAL_WITNESS_ACCOUNT_OR_INTENT_DRIFT");
                // G21.313: an uncooperative actor can replace the
                // selected account with a different SAME-BYTES inode,
                // or add a new hardlink after the initial single-link
                // forensic read. Neither changes its SHA256.
                BasicFileAttributes accountAfterIdentity=
                    requireSourceIdentity(selected,
                        "G21.313 FINAL_WITNESS_ACCOUNT_IDENTITY_UNPROVEN");
                if(!accountBeforeIdentity.fileKey().equals(
                        accountAfterIdentity.fileKey())||
                   !"1".equals(after.accountObservedLinkCount))
                    throw new IOException(
                        "G21.313 FINAL_WITNESS_ACCOUNT_OBJECT_OR_LINK_DRIFT_NEGATIVE");
                // G21.314: identical marker bytes / canonical content
                // do not prove the selected ACTIVE pathname retained
                // its original filesystem object.
                BasicFileAttributes markerAfterIdentity=
                    requireSourceIdentity(active,
                        "G21.314 FINAL_WITNESS_ACTIVE_IDENTITY_UNPROVEN");
                if(!markerBeforeIdentity.fileKey().equals(
                        markerAfterIdentity.fileKey()))
                    throw new IOException(
                        "G21.314 FINAL_WITNESS_ACTIVE_OBJECT_DRIFT_NEGATIVE");
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(selected);
                return output;
            });
    }
}
