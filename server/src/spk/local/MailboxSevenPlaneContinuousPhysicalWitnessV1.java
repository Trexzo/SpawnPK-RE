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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * G21.129: independent read-only PHYSICAL seven-plane comparison
 * under ONE continuous G21.100 OS advisory lifecycle lease.
 *
 * Does NOT call G21.119/124 semantic checks while holding the
 * lease: those APIs acquire their OWN G21.100 FileLock and would
 * overlap/deadlock. The physical check does NOT assert a semantic
 * cohort, authenticated external source, historical writer census,
 * raw writer fence, archive epoch, cleanup or reward authority.
 */
final class MailboxSevenPlaneContinuousPhysicalWitnessV1 {
    private static final long MAX_WAIT_MS=1500L;
    private static final int MAX_ACCOUNT=1048576;
    private static final int MAX_SIDECAR=1024;

    static final class Observation {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String accountSha,journalSha,commitSha;
        private Observation(
            MailboxWriterEvidencePortableArchiveV1.Snapshot a,
            MailboxWriterKindEvidenceV2.Snapshot b,
            MailboxFourBoundResultLeavesArchiveV1.Snapshot c,
            String account,String journal,String commit
        ){
            v1=a;v2=b;v3=c;
            accountSha=account;journalSha=journal;commitSha=commit;
        }
    }
    static final class Report {
        final Observation before,after;
        final boolean sevenPhysicalPinsMatched;
        final boolean cooperatingCrossJvmWritesExcludedDuringLease;
        final boolean richFourWriterSemanticCohortProved=false;
        final boolean independentPinAuthenticationProved=false;
        final boolean rawWriterExcluded=false;
        final boolean historicalAndFutureWritersExcluded=false;
        final boolean atomicRecoveryEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean pruningAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(Observation a,Observation b){
            before=a;after=b;
            sevenPhysicalPinsMatched=true;
            cooperatingCrossJvmWritesExcludedDuringLease=true;
        }
    }
    private static IOException veto(String reason){
        return new IOException(
            "G21.129 CONTINUOUS_PHYSICAL_SEVEN_PLANE_UNSAFE_NO_AUTHORITY "+
            reason+" epoch=false cleanup=false grant=false");
    }
    private static String checked(String account)throws IOException{
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw veto("INVALID_ACCOUNT");
        return account;
    }
    private static String pin(String expected)throws IOException{
        if(expected==null||!expected.matches("[0-9a-f]{64}"))
            throw veto("INVALID_INDEPENDENT_PIN");
        return expected;
    }
    private static String digest(byte[] bytes){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex=new StringBuilder(64);
            for(byte b:hash)
                hex.append(String.format(java.util.Locale.ROOT,
                    "%02x",b&255));
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static String physicalDigest(Path file,int maximum)
        throws IOException{
        BasicFileAttributes before;
        try{
            before=Files.readAttributes(file,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException problem){
            throw veto("MISSING_OR_UNREADABLE_PHYSICAL_PLANE");
        }
        if(!before.isRegularFile()||before.isSymbolicLink()||
           before.fileKey()==null||before.size()<1||
           before.size()>maximum)
            throw veto("UNSAFE_OR_OVERSIZED_PHYSICAL_PLANE");
        ByteBuffer content=ByteBuffer.allocate(maximum+1);
        try(FileChannel reader=FileChannel.open(file,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(content.hasRemaining()){
                int amount=reader.read(content);
                if(amount<0)break;
                if(amount==0)throw veto("STALLED_PHYSICAL_READ");
            }
        }catch(IOException source){
            throw veto("PHYSICAL_READ_FAILED");
        }
        BasicFileAttributes after;
        try{
            after=Files.readAttributes(file,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException changed){
            throw veto("PHYSICAL_SOURCE_DISAPPEARED");
        }
        if(before.size()!=content.position()||
           before.size()!=after.size()||
           !Objects.equals(before.fileKey(),after.fileKey())||
           !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw veto("PHYSICAL_READ_IDENTITY_DRIFT");
        byte[] raw=new byte[content.position()];
        System.arraycopy(content.array(),0,raw,0,raw.length);
        return digest(raw);
    }

    /** Caller ALREADY holds G21.100 stable OS lease. No nested
     * lifecycle acquisition; all physical captures use prior safe
     * inside variants, plus G21.39-independent physical NOFOLLOW
     * reads of the exact bounded canonical and sidecar files.
     */
    private static Observation scanInside(
        Path selected,String account,String v3Expected,
        String v1Portable,String v1Expected,String accountExpected,
        String preparedExpected,String commitExpected,
        String v2Expected
    )throws IOException{
        MailboxWriterEvidencePortableArchiveV1.Snapshot v1=
            MailboxWriterEvidencePortableArchiveV1
                .captureInside(selected,account);
        MailboxWriterKindEvidenceV2.Snapshot v2=
            MailboxWriterKindEvidenceV2
                .captureInside(selected,account);
        MailboxFourBoundResultLeavesArchiveV1.Snapshot v3=
            MailboxFourBoundResultLeavesArchiveV1
                .captureInside(selected,account);
        if(!v1Expected.equals(v1.sha256)||
           !v1Portable.equals(v1.portable)||
           !v2Expected.equals(v2.sha256)||
           !v3Expected.equals(v3.sha256))
            throw veto("IMMUTABLE_EVIDENCE_PIN_DIVERGED");
        FilePlayerRepository.PathResolver exact=name->{
            if(!account.equals(name))
                throw new IllegalArgumentException(
                    "G21.129 account resolver changed");
            return selected;
        };
        String currentAccount=physicalDigest(selected,MAX_ACCOUNT);
        String journal=physicalDigest(
            new MailboxDurableIdempotencyIntentJournal(exact)
                .journalPath(account),MAX_SIDECAR);
        String commit=physicalDigest(
            new MailboxGuardedDiskCommitRecord(exact)
                .recordPath(account),MAX_SIDECAR);
        if(!accountExpected.equals(currentAccount)||
           !preparedExpected.equals(journal)||
           !commitExpected.equals(commit))
            throw veto("ACCOUNT_OR_SIDECAR_PIN_DIVERGED");
        return new Observation(v1,v2,v3,currentAccount,journal,commit);
    }

    static Report compareWithContinuousLease(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedResultV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(callback,"callback");
        String name=checked(account);
        pin(pinnedResultV3);pin(pinnedV1);pin(pinnedV2);
        pin(pinnedAccount);pin(pinnedJournal);pin(pinnedCommit);
        if(v1Portable==null||v1Portable.isEmpty())
            throw veto("MISSING_ORIGINAL_PORTABLE_V1");
        if(acquisitionMillis<1L||acquisitionMillis>MAX_WAIT_MS)
            throw new IllegalArgumentException(
                "G21.129 invalid bounded continuous lease timeout");
        Path selected=Objects.requireNonNull(
            resolver.resolve(name),"account file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(selected),
                acquisitionMillis,()->{
                    Observation before=scanInside(
                        selected,name,pinnedResultV3,v1Portable,pinnedV1,
                        pinnedAccount,pinnedJournal,pinnedCommit,pinnedV2);
                    callback.run();
                    Observation after=scanInside(
                        selected,name,pinnedResultV3,v1Portable,pinnedV1,
                        pinnedAccount,pinnedJournal,pinnedCommit,pinnedV2);
                    if(!before.v1.portable.equals(after.v1.portable)||
                       !before.v2.portable.equals(after.v2.portable)||
                       !before.v3.portable.equals(after.v3.portable)||
                       !before.accountSha.equals(after.accountSha)||
                       !before.journalSha.equals(after.journalSha)||
                       !before.commitSha.equals(after.commitSha))
                        throw veto("BETWEEN_CAPTURE_BYTES_CHANGED");
                    return new Report(before,after);
                });
    }
    private MailboxSevenPlaneContinuousPhysicalWitnessV1(){}
}
