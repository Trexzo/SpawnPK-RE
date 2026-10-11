package spk.local;

import java.io.IOException;
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
            MailboxWriterEvidencePortableArchiveV1.Verified v
        ){
            archive=v;
            matchedRecords=v.records;
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
    static Report compare(
        FilePlayerRepository.PathResolver resolver,
        String account,String externallyPinnedPortable,
        String trustedSha256,BetweenPasses between
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
            externallyPinnedPortable,trustedSha256);
        between.run();
        Pass second=observe(exact,selected,account,
            externallyPinnedPortable,trustedSha256);
        if(!first.sha256.equals(second.sha256)||
           !first.portable.equals(second.portable)||
           !same(first.witness,second.witness))
            throw mismatch("interpass canonical or temp physical drift");
        return new Report(trusted);
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
        String account,String expected,String trustedDigest
    )throws IOException{
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(selected),
                1500L,()->{
                    MailboxOrphanPublicationCleanupAudit auditor=
                        new MailboxOrphanPublicationCleanupAudit(exact);
                    MailboxOrphanPublicationCleanupAudit.Result before=
                        auditor.inspect(account);
                    MailboxWriterEvidencePortableArchiveV1.Snapshot live=
                        MailboxWriterEvidencePortableArchiveV1
                            .captureInside(selected,account);
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

    private MailboxWriterArchiveLiveReconciliationV1(){}
}
