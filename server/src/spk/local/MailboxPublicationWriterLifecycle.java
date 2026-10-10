package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.100: cooperative TEMP-WRITER lifecycle lock, distinct from the
 * account publication lock. All participating writers hold this lock
 * BEFORE creating their publication temp and until they finish/cleanup.
 *
 * Lock order is ALWAYS lifecycle -> G21.39 account publication.
 * Neither lock proves that older/uncooperative writers are absent.
 * NO deletion, grant, session, repair, replay or ACK authority exists.
 */
final class MailboxPublicationWriterLifecycle {
    private static final long WRITER_TIMEOUT_MILLIS=60000L;
    private static final String DOMAIN=".g21100-temp-writer-lifecycle";

    static Path domain(Path accountFile){
        Path file=Objects.requireNonNull(accountFile,"account file")
            .toAbsolutePath().normalize();
        if(file.getParent()==null)
            throw new IllegalArgumentException(
                "G21.100 account file parent missing");
        return file.resolveSibling(file.getFileName().toString()+DOMAIN);
    }

    static <T> T withWriter(
        Path accountFile,
        MailboxAccountPublicationCoordinator.Operation<T> action
    )throws IOException{
        Objects.requireNonNull(action,"writer");
        Path account=Objects.requireNonNull(accountFile,"account file")
            .toAbsolutePath().normalize();
        // Existing strict save creates this directory before temp.
        // The new NOFOLLOW bounded lease is acquired *before* temp I/O.
        Files.createDirectories(account.getParent());
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                domain(account),WRITER_TIMEOUT_MILLIS,action);
    }

    static final class Report {
        final MailboxOrphanPublicationCleanupAudit.Result tempAudit;
        final boolean participatingWriterQuiescenceObserved=true;
        final boolean allWriterGenerationsCovered=false;
        final boolean uncooperativeWritersExcluded=false;
        final boolean unlinkAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean restartAdmissionAuthorized=false;
        final boolean clientAckAuthorized=false;

        private Report(
            MailboxOrphanPublicationCleanupAudit.Result snapshot
        ){
            tempAudit=Objects.requireNonNull(snapshot,"temp audit");
        }
    }

    /** Bounded, opt-in, NON-DESTRUCTIVE joint lifecycle+temp observation.
     * This is NOT a transferable cleanup token or a durable snapshot.
     */
    static Report inspect(
        FilePlayerRepository.PathResolver resolver,
        String account,long timeoutMillis
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.100 noncanonical account");
        final Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                domain(selected),timeoutMillis,()->
                    new Report(
                        new MailboxOrphanPublicationCleanupAudit(
                            requested->{
                                if(!account.equals(requested))
                                    throw new IllegalArgumentException(
                                        "G21.100 different account");
                                return selected;
                            }).inspect(account)
                    )
            );
    }
}
