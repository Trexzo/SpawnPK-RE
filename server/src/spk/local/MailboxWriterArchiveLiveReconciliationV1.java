package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * G21.106: compare a separately pinned, portable v1 archive against
 * CURRENT account-scoped disk evidence twice, without retaining locks
 * between passes. Neither matching evidence nor a hash gives fencing
 * or permission to remove/roll over any record.
 */
final class MailboxWriterArchiveLiveReconciliationV1 {
    interface BetweenPasses { void run()throws IOException; }

    static final class Report {
        final MailboxWriterEvidencePortableArchiveV1.Verified archive;
        final int matchedRecords;
        // True only for the separately pinned, opt-in G21.107 observation.
        // Never a transferable cleanup, session or reward capability.
        final boolean pinnedCanonicalBytesMatched;
        final boolean sourceMatchedAcrossTwoPasses=true;
        final boolean participatingWriterLeasesObservedQuiet=true;
        final boolean authoritativeAccountContentProved=false;
        final boolean allWriterVersionsFenced=false;
        final boolean futureLegacyWritersExcluded=false;
        final boolean ledgerLifetimeArchived=false;
        final boolean sourceFutureImmutabilityProved=false;
        final boolean rolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Report(
            MailboxWriterEvidencePortableArchiveV1.Verified v,
            boolean pinnedCanonical
        ){
            archive=v;
            matchedRecords=v.records;
            pinnedCanonicalBytesMatched=pinnedCanonical;
        }
    }

    private static IOException mismatch(String reason){
        return new IOException(
            "G21.106 LIVE_ARCHIVE_WITNESS_DRIFT_NO_ROLLOVER "+
            reason+" cleanup=false grant=false");
    }

    private static boolean same(
        MailboxOrphanPublicationCleanupAudit.Result a,
        MailboxOrphanPublicationCleanupAudit.Result b
    ){
        if(!a.account.equals(b.account)||
           !a.canonicalWitnesses.equals(b.canonicalWitnesses))
            return false;
        List<MailboxOrphanPublicationCleanupAudit.Candidate> left=
            a.candidates;
        List<MailboxOrphanPublicationCleanupAudit.Candidate> right=
            b.candidates;
        if(left.size()!=right.size())return false;
        for(int i=0;i<left.size();i++){
            MailboxOrphanPublicationCleanupAudit.Candidate x=left.get(i);
            MailboxOrphanPublicationCleanupAudit.Candidate y=right.get(i);
            if(!x.path.equals(y.path)||x.kind!=y.kind||
               x.disposition!=y.disposition||
               !Objects.equals(x.fileKey,y.fileKey)||
               x.size!=y.size||x.modifiedMillis!=y.modifiedMillis)
                return false;
        }
        return true;
    }

    private static final class Pass {
        final MailboxOrphanPublicationCleanupAudit.Result witness;
        final String portable;
        final String sha256;
        Pass(
            MailboxOrphanPublicationCleanupAudit.Result w,
            MailboxWriterEvidencePortableArchiveV1.Snapshot evidence
        ){
            witness=w;
            portable=evidence.portable;
            sha256=evidence.sha256;
        }
    }

    /**
     * Operation order is lifecycle -> publication audit; never reverse.
     * Caller-provided between-pass callback runs only after ALL locks
     * have been released and can model a legacy raw publisher.
     */
    // G21.107 is strictly opt-in: callers must independently pin BOTH
    // the portable writer ledger SHA-256 and the canonical account SHA-256.
    static Report comparePinnedAccount(
        FilePlayerRepository.PathResolver resolver,
        String account,String externallyPinnedPortable,
        String trustedArchiveSha256,String trustedCanonicalSha256,
        BetweenPasses between
    )throws IOException{
        if(trustedCanonicalSha256==null||
           !trustedCanonicalSha256.matches("[0-9a-f]{64}"))
            throw pinnedMismatch("missing independent canonical SHA-256");
        return compareInternal(resolver,account,externallyPinnedPortable,
            trustedArchiveSha256,trustedCanonicalSha256,between);
    }

    static Report comparePinnedAccount(
        FilePlayerRepository.PathResolver resolver,
        String account,String portable,String archiveSha256,
        String canonicalSha256
    )throws IOException{
        return comparePinnedAccount(resolver,account,portable,
            archiveSha256,canonicalSha256,()->{});
    }

    static Report compare(
        FilePlayerRepository.PathResolver resolver,
        String account,String externallyPinnedPortable,
        String trustedSha256,BetweenPasses between
    )throws IOException{
        return compareInternal(resolver,account,externallyPinnedPortable,
            trustedSha256,null,between);
    }

    private static Report compareInternal(
        FilePlayerRepository.PathResolver resolver,
        String account,String externallyPinnedPortable,
        String trustedSha256,String pinnedCanonicalSha256,
        BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        MailboxWriterEvidencePortableArchiveV1.Verified trusted=
            MailboxWriterEvidencePortableArchiveV1.verifyPortable(
                externallyPinnedPortable,trustedSha256);
        if(!trusted.account.equals(account))
            throw mismatch("foreign account archive");
        final Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account path")
            .toAbsolutePath().normalize();
        FilePlayerRepository.PathResolver exact=which->{
            if(!account.equals(which))
                throw new IllegalArgumentException(
                    "G21.106 selected account mismatch");
            return selected;
        };
        Pass first=observe(exact,selected,account,
            externallyPinnedPortable,trustedSha256,pinnedCanonicalSha256);
        between.run();
        Pass second=observe(exact,selected,account,
            externallyPinnedPortable,trustedSha256,pinnedCanonicalSha256);
        if(!first.sha256.equals(second.sha256)||
           !first.portable.equals(second.portable)||
           !same(first.witness,second.witness))
            throw mismatch("interpass canonical or temp physical drift");
        return new Report(trusted,pinnedCanonicalSha256!=null);
    }

    static Report compare(
        FilePlayerRepository.PathResolver resolver,
        String account,String portable,String trustedSha256
    )throws IOException{
        return compare(
            resolver,account,portable,trustedSha256,()->{});
    }

    private static Pass observe(
        FilePlayerRepository.PathResolver exact,Path selected,
        String account,String expected,String trustedDigest,
        String pinnedCanonicalSha256
    )throws IOException{
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(selected),
                1500L,()->{
                    MailboxOrphanPublicationCleanupAudit auditor=
                        new MailboxOrphanPublicationCleanupAudit(exact);
                    MailboxOrphanPublicationCleanupAudit.Result before=
                        auditor.inspect(account);
                    if(pinnedCanonicalSha256!=null)
                        checkPinnedCanonicalContent(
                            selected,pinnedCanonicalSha256);
                    MailboxWriterEvidencePortableArchiveV1.Snapshot live=
                        MailboxWriterEvidencePortableArchiveV1
                            .captureInside(selected,account);
                    if(pinnedCanonicalSha256!=null)
                        checkPinnedCanonicalContent(
                            selected,pinnedCanonicalSha256);
                    MailboxOrphanPublicationCleanupAudit.Result after=
                        auditor.inspect(account);
                    if(!same(before,after))
                        throw mismatch(
                            "canonical/temp changed within locked pass");
                    if(!trustedDigest.equals(live.sha256)||
                       !expected.equals(live.portable))
                        throw mismatch("current v1 record bytes differ");
                    return new Pass(before,live);
                });
    }

    // Bounded exact canonical byte capture inside a cooperating publication
    // lock, called under G21.100 lifecycle ownership. Other/raw writers
    // remain outside this contract; this is NOT a future-writer fence.
    private static final long MAX_CANONICAL_BYTES=1024L*1024L;
    private static IOException pinnedMismatch(String reason){
        return new IOException(
            "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH_NO_AUTHORITY "+
            reason+" cleanup=false grant=false");
    }
    private static void checkPinnedCanonicalContent(
        Path accountFile,String pinnedSha256
    )throws IOException{
        MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(accountFile,1500L,()->{
                BasicFileAttributes before=Files.readAttributes(
                    accountFile,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if(!before.isRegularFile()||before.isSymbolicLink()||
                   before.fileKey()==null||before.size()<1||
                   before.size()>MAX_CANONICAL_BYTES)
                    throw pinnedMismatch("unsafe/oversized canonical leaf");
                ByteBuffer bytes=ByteBuffer.allocate(
                    (int)before.size()+1);
                try(FileChannel file=FileChannel.open(accountFile,
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)){
                    while(bytes.hasRemaining()){
                        int n=file.read(bytes);
                        if(n<0)break;
                        if(n==0)throw pinnedMismatch("stalled byte read");
                    }
                }
                BasicFileAttributes after=Files.readAttributes(
                    accountFile,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
                if(!after.isRegularFile()||after.isSymbolicLink()||
                   !Objects.equals(before.fileKey(),after.fileKey())||
                   before.size()!=after.size()||
                   !Objects.equals(before.lastModifiedTime(),
                       after.lastModifiedTime())||
                   bytes.position()!=before.size())
                    throw pinnedMismatch("changed canonical leaf/read");
                byte[] exact=Arrays.copyOf(
                    bytes.array(),(int)before.size());
                final byte[] digest;
                try{
                    digest=MessageDigest.getInstance("SHA-256")
                        .digest(exact);
                }catch(NoSuchAlgorithmException impossible){
                    throw new IllegalStateException(impossible);
                }
                StringBuilder hex=new StringBuilder(64);
                for(byte b:digest)
                    hex.append(String.format(
                        Locale.ROOT,"%02x",b&255));
                if(!pinnedSha256.equals(hex.toString()))
                    throw pinnedMismatch("canonical bytes disagree");
                return null;
            });
    }

    private MailboxWriterArchiveLiveReconciliationV1(){}
}
