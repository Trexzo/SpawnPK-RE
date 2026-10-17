package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * G21.102: TWO negative read-only identity observations, each separately
 * acquiring G21.100 lifecycle then G21.99 publication. A change BETWEEN
 * two passes is reported fail-closed; a successful check does NOT fence
 * old/nonparticipating writers after either pass.
 */
final class MailboxCrossVersionPublicationEvidenceAudit {
    interface BetweenPasses {
        void afterFirst()throws IOException;
    }

    static final class Result {
        final MailboxPublicationWriterLifecycle.Report first;
        final MailboxPublicationWriterLifecycle.Report second;
        final boolean sameObservedPhysicalIdentities=true;
        final boolean participatingWritersQuietDuringPasses=true;
        final boolean legacyWritersExcluded=false;
        final boolean futureWritersExcluded=false;
        final boolean allWriterGenerationsCovered=false;
        final boolean crashEpochPersistentlySealed=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Result(
            MailboxPublicationWriterLifecycle.Report a,
            MailboxPublicationWriterLifecycle.Report b
        ){
            first=a;
            second=b;
        }
    }

    private static boolean same(
        MailboxOrphanPublicationCleanupAudit.Result a,
        MailboxOrphanPublicationCleanupAudit.Result b
    ){
        if(!a.account.equals(b.account)||
           !a.canonicalWitnesses.equals(b.canonicalWitnesses))
            return false;
        List<MailboxOrphanPublicationCleanupAudit.Candidate> ac=a.candidates;
        List<MailboxOrphanPublicationCleanupAudit.Candidate> bc=b.candidates;
        if(ac.size()!=bc.size())return false;
        for(int i=0;i<ac.size();i++){
            MailboxOrphanPublicationCleanupAudit.Candidate x=ac.get(i);
            MailboxOrphanPublicationCleanupAudit.Candidate y=bc.get(i);
            if(!x.path.equals(y.path)||x.kind!=y.kind||
               x.disposition!=y.disposition||
               !Objects.equals(x.fileKey,y.fileKey)||
               x.size!=y.size||x.modifiedMillis!=y.modifiedMillis)
                return false;
        }
        return true;
    }

    static Result inspectTwice(
        FilePlayerRepository.PathResolver resolver,
        String account,long lockTimeoutMillis,
        BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.102 noncanonical account");
        // One and only one external resolution; both read-only passes
        // operate on this EXACT selected canonical account path.
        Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        FilePlayerRepository.PathResolver pinned=requested->{
            if(!account.equals(requested))
                throw new IllegalArgumentException(
                    "G21.102 foreign account resolver call");
            return selected;
        };
        MailboxPublicationWriterLifecycle.Report first=
            MailboxPublicationWriterLifecycle.inspect(
                pinned,account,lockTimeoutMillis);
        // No lifecycle or publication lock is held over this callback.
        // Real competing legacy writers can publish at this boundary.
        between.afterFirst();
        MailboxPublicationWriterLifecycle.Report second=
            MailboxPublicationWriterLifecycle.inspect(
                pinned,account,lockTimeoutMillis);
        if(!same(first.tempAudit,second.tempAudit))
            throw new IOException(
                "G21.102 CROSS_VERSION_DISK_WITNESS_DRIFT_NO_UNLINK"+
                " account="+account+" grant=false");
        return new Result(first,second);
    }

    static Result inspectTwice(
        FilePlayerRepository.PathResolver resolver,String account,
        long timeoutMillis
    )throws IOException{
        return inspectTwice(resolver,account,timeoutMillis,()->{});
    }
}
