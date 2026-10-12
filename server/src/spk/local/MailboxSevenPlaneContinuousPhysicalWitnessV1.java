package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
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

    /**
     * Negative-only physical identity observation. A raw writer may
     * spoof or restore timestamps or edit bytes in place; a matching
     * metadata pair does NOT establish raw-writer exclusion.
     */
    static final class PhysicalIdentity {
        final String sha256;
        final Object fileKey;
        final long size;
        final FileTime created, modified;
        private PhysicalIdentity(String digest,BasicFileAttributes attrs){
            sha256=digest;
            fileKey=attrs.fileKey();
            size=attrs.size();
            created=attrs.creationTime();
            modified=attrs.lastModifiedTime();
        }
        boolean sameMetadata(PhysicalIdentity other){
            return other!=null&&
                size==other.size&&
                Objects.equals(fileKey,other.fileKey)&&
                created.equals(other.created)&&
                modified.equals(other.modified);
        }
    }

    static final class Observation {
        final MailboxWriterEvidencePortableArchiveV1.Snapshot v1;
        final MailboxWriterKindEvidenceV2.Snapshot v2;
        final MailboxFourBoundResultLeavesArchiveV1.Snapshot v3;
        final String accountSha,journalSha,commitSha;
        final PhysicalIdentity accountIdentity,journalIdentity,commitIdentity;
        final Map<String,PhysicalIdentity> evidenceLeafIdentities;
        private Observation(
            MailboxWriterEvidencePortableArchiveV1.Snapshot a,
            MailboxWriterKindEvidenceV2.Snapshot b,
            MailboxFourBoundResultLeavesArchiveV1.Snapshot c,
            PhysicalIdentity account,PhysicalIdentity journal,
            PhysicalIdentity commit,
            Map<String,PhysicalIdentity> evidenceLeaves
        ){
            v1=a;v2=b;v3=c;
            accountIdentity=account;
            journalIdentity=journal;
            commitIdentity=commit;
            evidenceLeafIdentities=Collections.unmodifiableMap(
                new TreeMap<>(evidenceLeaves));
            accountSha=account.sha256;
            journalSha=journal.sha256;
            commitSha=commit.sha256;
        }
    }
    static final class Report {
        final Observation before,after;
        final boolean sevenPhysicalPinsMatched;
        final boolean threePhysicalIdentitiesStableAcrossCaptures;
        final boolean evidenceLeafMetadataStableAcrossCaptures;
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
            threePhysicalIdentitiesStableAcrossCaptures=true;
            evidenceLeafMetadataStableAcrossCaptures=true;
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
    private static PhysicalIdentity physicalDigest(Path file,int maximum)
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
           !before.lastModifiedTime().equals(after.lastModifiedTime())||
           !before.creationTime().equals(after.creationTime()))
            throw veto("PHYSICAL_READ_IDENTITY_DRIFT");
        byte[] raw=new byte[content.position()];
        System.arraycopy(content.array(),0,raw,0,raw.length);
        return new PhysicalIdentity(digest(raw),before);
    }


    /**
     * G21.131: bounded PHYSICAL scan of already-captured immutable
     * evidence leaves under the caller-owned G21.100 lifecycle lease.
     * No extra lease, no mutation, no change to v1/v2/v3 format.
     */
    private static Map<String,PhysicalIdentity> evidenceLeafMetadata(
        Path selected,int v1Count,int v2Count,int v3Count
    )throws IOException{
        String basename=selected.getFileName().toString();
        String[] globs={
            basename+".g21103-writer-attempt-*",
            basename+".g21114-writer-kind-*",
            basename+".g2112*"
        };
        int[] expected={v1Count,v2Count,v3Count};
        int[] maximum={32,32,4};
        Map<String,PhysicalIdentity> leaves=new TreeMap<>();
        Path parent=selected.getParent();
        if(parent==null)throw veto("EVIDENCE_PARENT_UNAVAILABLE");
        for(int group=0;group<globs.length;group++){
            int found=0;
            try(DirectoryStream<Path> children=
                    Files.newDirectoryStream(parent,globs[group])){
                for(Path path:children){
                    if(++found>maximum[group])
                        throw veto("EVIDENCE_LEAF_CAPACITY_DRIFT");
                    String name=path.getFileName().toString();
                    if(leaves.putIfAbsent(name,
                            physicalDigest(path,512))!=null)
                        throw veto("DUPLICATE_EVIDENCE_LEAF");
                }
            }catch(IOException error){
                if(String.valueOf(error.getMessage()).contains(
                        "CONTINUOUS_PHYSICAL_SEVEN_PLANE_UNSAFE"))
                    throw error;
                throw veto("EVIDENCE_LEAF_SCAN_FAILED");
            }
            if(found!=expected[group])
                throw veto("EVIDENCE_LEAF_COUNT_DRIFT");
        }
        return leaves;
    }

    private static boolean sameEvidenceMetadata(
        Map<String,PhysicalIdentity> before,
        Map<String,PhysicalIdentity> after
    ){
        if(!before.keySet().equals(after.keySet()))return false;
        for(Map.Entry<String,PhysicalIdentity> leaf:before.entrySet())
            if(!leaf.getValue().sameMetadata(after.get(leaf.getKey())))
                return false;
        return true;
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
        PhysicalIdentity currentAccount=physicalDigest(
            selected,MAX_ACCOUNT);
        PhysicalIdentity journal=physicalDigest(
            new MailboxDurableIdempotencyIntentJournal(exact)
                .journalPath(account),MAX_SIDECAR);
        PhysicalIdentity commit=physicalDigest(
            new MailboxGuardedDiskCommitRecord(exact)
                .recordPath(account),MAX_SIDECAR);
        if(!accountExpected.equals(currentAccount.sha256)||
           !preparedExpected.equals(journal.sha256)||
           !commitExpected.equals(commit.sha256))
            throw veto("ACCOUNT_OR_SIDECAR_PIN_DIVERGED");
        Map<String,PhysicalIdentity> leaves=evidenceLeafMetadata(
            selected,v1.verified.records,
            v2.report.typedBegins+v2.report.typedCompletes,
            v3.resultLeaves);
        return new Observation(
            v1,v2,v3,currentAccount,journal,commit,leaves);
    }


    /**
     * G21.134 opt-in: observer receives each pinned seven-plane
     * sample while the ORIGINAL G21.100 lease is still held.
     * Implementations must not attempt to re-acquire G21.100.
     */
    @FunctionalInterface
    interface InsideLeaseObserver {
        void inspect(Path selected,String account,Observation sample)
            throws IOException;
    }

    // G21.136: stable sentinel keeps legacy G21.129 method EXACTLY
    // two physical scans; only new opt-in observer pays tail rescan.
    private static final InsideLeaseObserver LEGACY_NOOP_OBSERVER=
        (selected,account,sample)->{};

    static Report compareWithContinuousLease(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedResultV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback
    )throws IOException{
        return compareWithContinuousLease(
            resolver,account,pinnedResultV3,v1Portable,pinnedV1,
            pinnedAccount,pinnedJournal,pinnedCommit,pinnedV2,
            acquisitionMillis,callback,LEGACY_NOOP_OBSERVER);
    }

    static Report compareWithContinuousLease(
        FilePlayerRepository.PathResolver resolver,String account,
        String pinnedResultV3,String v1Portable,String pinnedV1,
        String pinnedAccount,String pinnedJournal,String pinnedCommit,
        String pinnedV2,long acquisitionMillis,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses callback,
        InsideLeaseObserver observer
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(callback,"callback");
        Objects.requireNonNull(observer,"observer");
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
                    observer.inspect(selected,name,before);
                    callback.run();
                    Observation after=scanInside(
                        selected,name,pinnedResultV3,v1Portable,pinnedV1,
                        pinnedAccount,pinnedJournal,pinnedCommit,pinnedV2);
                    observer.inspect(selected,name,after);
                    // G21.131: physical evidence-leaf metadata may
                    // drift even when the portable archive hashes
                    // remain identical. RAW writers can still restore
                    // identity/timestamps, so this is veto-only.
                    if(!sameEvidenceMetadata(
                           before.evidenceLeafIdentities,
                           after.evidenceLeafIdentities))
                        throw veto(
                            "BETWEEN_CAPTURE_EVIDENCE_LEAF_METADATA_CHANGED");
                    // G21.130: the old digest-only check missed
                    // same-content RAW rewrites with observable mtime
                    // or file-key drift. This remains a negative test,
                    // NOT protection from timestamp restoration/spoofing.
                    if(!before.accountIdentity.sameMetadata(
                           after.accountIdentity)||
                       !before.journalIdentity.sameMetadata(
                           after.journalIdentity)||
                       !before.commitIdentity.sameMetadata(
                           after.commitIdentity))
                        throw veto("BETWEEN_CAPTURE_FILE_METADATA_CHANGED");
                    if(!before.v1.portable.equals(after.v1.portable)||
                       !before.v2.portable.equals(after.v2.portable)||
                       !before.v3.portable.equals(after.v3.portable)||
                       !before.accountSha.equals(after.accountSha)||
                       !before.journalSha.equals(after.journalSha)||
                       !before.commitSha.equals(after.commitSha))
                        throw veto("BETWEEN_CAPTURE_BYTES_CHANGED");

                    // G21.136: second observer executes AFTER the
                    // physical 'after' scan. Without a tail scan it
                    // could alter a raw or evidence leaf, then return
                    // a stale apparent success. Opt-in only; old
                    // G21.129 two-capture signature stays unchanged.
                    if(observer!=LEGACY_NOOP_OBSERVER){
                        Observation finalInside=scanInside(
                            selected,name,pinnedResultV3,v1Portable,
                            pinnedV1,pinnedAccount,pinnedJournal,
                            pinnedCommit,pinnedV2);
                        if(!sameEvidenceMetadata(
                               after.evidenceLeafIdentities,
                               finalInside.evidenceLeafIdentities)||
                           !after.accountIdentity.sameMetadata(
                               finalInside.accountIdentity)||
                           !after.journalIdentity.sameMetadata(
                               finalInside.journalIdentity)||
                           !after.commitIdentity.sameMetadata(
                               finalInside.commitIdentity))
                            throw veto(
                                "AFTER_FINAL_OBSERVER_METADATA_CHANGED");
                        if(!after.v1.portable.equals(
                               finalInside.v1.portable)||
                           !after.v2.portable.equals(
                               finalInside.v2.portable)||
                           !after.v3.portable.equals(
                               finalInside.v3.portable)||
                           !after.accountSha.equals(
                               finalInside.accountSha)||
                           !after.journalSha.equals(
                               finalInside.journalSha)||
                           !after.commitSha.equals(
                               finalInside.commitSha))
                            throw veto(
                                "AFTER_FINAL_OBSERVER_BYTES_CHANGED");
                    }
                    return new Report(before,after);
                });
    }
    private MailboxSevenPlaneContinuousPhysicalWitnessV1(){}
}
