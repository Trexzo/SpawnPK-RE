package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.118: explicit per-operation, cross-JVM-readable durable writer
 * ATTEMPT diagnostic. Reuses the EXISTING G21.103/104 32-leaf
 * forced v1 BEGIN/COMPLETE and G21.114 typed v2 companion protocol.
 *
 * Not enabled on ordinary/default writers and not a business
 * publication kind. A successful callback is not a World commit,
 * grant, historical writer census, crash epoch, archival rollover
 * or replay/release/ACK authorization.
 */
final class MailboxCrossJvmDurableWriterDiagnosticV1 {
    /**
     * Resolves the account exactly once. Lifecycle lease is acquired
     * exactly once before any evidence creation. G21.103/114 handles
     * capacity/unknown/crashed-record preflight, forced BEGIN and
     * COMPLETE, and leaves unmatched BEGIN on error/Runtime.halt.
     *
     * The callback is caller-supplied, never dispatched by ordinary
     * World autosave, strict default, journal or disk COMMIT paths.
     */
    static <T> T runRecorded(
        FilePlayerRepository.PathResolver resolver,
        String account,
        MailboxAccountPublicationCoordinator.Operation<T> operation
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(operation,"operation");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.118 NONCANONICAL_ACCOUNT_NO_AUTHORITY");
        final Path file=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        return MailboxPublicationWriterLifecycle.withWriter(file,()->
            MailboxWriterAttemptEvidenceV1.runRecordedInsideWriter(
                file,account,
                MailboxWriterKindEvidenceV2.Kind.COOPERATING_DIAGNOSTIC,
                operation));
    }

    private MailboxCrossJvmDurableWriterDiagnosticV1(){}
}
