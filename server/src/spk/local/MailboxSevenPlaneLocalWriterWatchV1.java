package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * G21.126: an opt-in, negative-only SAME-JVM cooperating attempt
 * watch spanning G21.125's ENTIRE seven-plane comparison, including
 * gaps between the individually held lifecycle observations.
 *
 * G21.116's registry is process-local. This does not detect foreign
 * JVM operations, raw/uncooperative writers, completed attempts from
 * before arming, future writers, or a durable multi-file epoch.
 */
final class MailboxSevenPlaneLocalWriterWatchV1 {
    private static IOException veto(String reason){
        return new IOException(
            "G21.126 SEVEN_PLANE_LOCAL_ATTEMPT_UNSAFE_NO_AUTHORITY "+
            reason+" historicalFence=false crossJvmFence=false "+
            "rollover=false cleanup=false grant=false");
    }

    static MailboxFourBoundResultLeavesArchiveV1.Comparison
        comparePinnedWithLocalAttemptWatch(
        FilePlayerRepository.PathResolver resolver,
        String account,String resultLeavesSha256,
        String v1Portable,String v1Sha,String accountSha,
        String preparedSha,String commitSha,String v2Sha,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        // Resolve once, before arming. The downstream v3, four
        // writer, v1/v2 and sidecar observations reuse this selection.
        final Path file=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        FilePlayerRepository.PathResolver exact=who->{
            if(!account.equals(who))
                throw new IllegalArgumentException(
                    "G21.126 RESOLVER_CHANGED_NO_AUTHORITY");
            return file;
        };
        try(MailboxLocalWriterAttemptWatchV1.Scope watch=
                MailboxLocalWriterAttemptWatchV1.arm(file)){
            MailboxFourBoundResultLeavesArchiveV1.Comparison result=
                MailboxFourBoundResultLeavesArchiveV1
                    .comparePinnedFourResultLeaves(
                        exact,account,resultLeavesSha256,
                        v1Portable,v1Sha,accountSha,
                        preparedSha,commitSha,v2Sha,between);
            // Fail closed even if the writer callback failed, changed
            // no bytes, or wrote the same bytes back. A successful
            // result NEVER means another JVM/raw writer was absent.
            if(watch.sawAttempt())
                throw veto("COOPERATING_LOCAL_WRITER_ATTEMPT_SEEN");
            return result;
        }
    }

    private MailboxSevenPlaneLocalWriterWatchV1(){}
}
