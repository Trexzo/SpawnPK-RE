package spk.local;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone, read-only operator interface for UNWIRED NON-GRANTING
 * experimental NPC reservation evidence. No locks, creates, deletion,
 * callbacks, credentials, user IDs or server event authority.
 *
 * Usage: java -cp <server classpath> spk.local.LocalLabExperimentalDiskJournalInspector <existing-dir>
 * Exit: 0 parsed unauthenticated history, 2 directory absent,
 *       3 blocked/unsafe/uncertain, 64 invalid invocation.
 */
public final class LocalLabExperimentalDiskJournalInspector {
    static final int READABLE = 0;
    static final int ABSENT = 2;
    static final int BLOCKED = 3;
    static final int USAGE = 64;

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        if (args == null || args.length != 1 ||
            args[0] == null || args[0].isEmpty()) {
            stderr.println("SPK_JOURNAL_FORENSICS_USAGE expectedOneDirectory=true"
                + " readOnly=true grantAck=false replay=false");
            return USAGE;
        }
        final Path path;
        try {
            path = Paths.get(args[0]);
        } catch (RuntimeException invalid) {
            stderr.println("SPK_JOURNAL_FORENSICS_USAGE invalidDirectory=true"
                + " readOnly=true grantAck=false replay=false");
            return USAGE;
        }
        final LocalLabExperimentalDiskJournalForensics.Report report;
        try {
            report = LocalLabExperimentalDiskJournalForensics.inspectExisting(path);
        } catch (RuntimeException failure) {
            // Do not leak stack traces or supplied absolute paths to logs.
            stdout.println("SPK_JOURNAL_FORENSICS_V1 state=BLOCKED_UNCERTAIN_IO"
                + " reason=DISK_FORENSICS_UNCERTAIN_RUNTIME"
                + " events=0 reservations=0 readOnly=true unlocked=true"
                + " countsMayBePartial=true originalEventAuthority=false"
                + " ownerEpoch=false grantAck=false replay=false");
            return BLOCKED;
        }
        // Normalize any provider-specific error text into a safe one-token
        // diagnostic. Never print the operator's path, account, or source bytes.
        String code = report.reasonCode.matches("[A-Z0-9_]{1,120}")
            ? report.reasonCode : "DISK_FORENSICS_PROVIDER_IO";
        stdout.println("SPK_JOURNAL_FORENSICS_V1"
            + " state=" + report.state.name()
            + " reason=" + code
            + " events=" + report.observedEvents
            + " reservations=" + report.observedReservations
            + " readOnly=true unlocked=true countsMayBePartial=true"
            + " originalEventAuthority=false ownerEpoch=false"
            + " grantAck=false replay=false");
        switch (report.state) {
            case READABLE_UNAUTHENTICATED_HISTORY: return READABLE;
            case NO_EXISTING_JOURNAL: return ABSENT;
            default: return BLOCKED;
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    private LocalLabExperimentalDiskJournalInspector() {}
}
