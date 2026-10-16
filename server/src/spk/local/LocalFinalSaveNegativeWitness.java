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
                try(FileChannel file=FileChannel.open(output,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)){
                    ByteBuffer data=ByteBuffer.wrap(bytes);
                    while(data.hasRemaining())file.write(data);
                    file.force(true);
                }
                try(FileChannel directory=FileChannel.open(
                        selected.getParent(),StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)){
                    directory.force(true);
                }
                BasicFileAttributes attrs=Files.readAttributes(output,
                    BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
                   attrs.fileKey()==null||attrs.size()!=bytes.length)
                    throw new IOException(
                        "G21.298 NEGATIVE_FINAL_WITNESS_POSTWRITE_UNPROVEN");
                LocalAccountForensicEvidence.Evidence after=
                    LocalAccountForensicEvidence.inspect(selected);
                if(!before.accountSha256.equals(after.accountSha256)||
                   !markerBefore.equals(after
                       .negativeSidecarContentWitnesses.get("activeReview")))
                    throw new IOException(
                        "G21.298 NEGATIVE_FINAL_WITNESS_ACCOUNT_OR_INTENT_DRIFT");
                LocalDurableAccountReviewIntentPrimitive
                    .requireReviewMarker(selected);
                return output;
            });
    }
}
