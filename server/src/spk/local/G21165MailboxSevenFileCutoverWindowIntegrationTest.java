package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.165: real raw writes which ignore G21.39's advisory publication
 * lock must invalidate the seven-file cutover observation window.
 * These are negative observations, not writer exclusion or admission.
 */
public final class G21165MailboxSevenFileCutoverWindowIntegrationTest {
    private static void check(boolean pass,String reason) {
        if(!pass)throw new AssertionError("G21.165 "+reason);
    }

    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    private static void veto(Checked op,String expected)throws Exception {
        try {
            op.run();
        }catch(IOException denied) {
            check(String.valueOf(denied.getMessage()).contains(expected),
                "unexpected checked veto: "+denied.getMessage()+
                " expected "+expected);
            return;
        }
        throw new AssertionError("G21.165 missed veto "+expected);
    }

    private static void verifyUnchangedNoAuthority(
        FilePlayerRepository repo,String account,byte[] candidate
    )throws Exception {
        veto(()->repo.requireFreshCutoverUnderPublicationLock(
            account,candidate),
            "NO_POSITIVE_CUTOVER_STATE_LEGACY_NOT_MAILBOX_TRANSACTION");
    }

    private static void run()throws Exception {
        Path root=Files.createTempDirectory("g21165-raw-cutover-");
        try {
            FilePlayerRepository.PathResolver resolver=
                who->root.resolve(who+".properties");
            FilePlayerRepository repo=new FilePlayerRepository(resolver);
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String account="g21165-legacy";
            byte[] candidate=new byte[]{1,2};
            WorldPlayer owner=new WorldPlayer();
            owner.markRegistered(account);
            writer.saveStrict(PlayerSnapshotCodec.capture(account,owner));
            Path file=resolver.resolve(account);
            byte[] original=Files.readAllBytes(file);
            verifyUnchangedNoAuthority(repo,account,candidate);

            final String drift=
                "CUTOVER_WINDOW_UNSTABLE_UNSAFE_NO_AUTHORITY";
            final byte[] appended="# raw writer bypassed advisory lock\n"
                .getBytes(StandardCharsets.US_ASCII);
            // This byte rewrite keeps the LEGACY semantic classification:
            // an enum-only compare would miss it. We do NOT call the
            // coordinating lock or production writer to perform it.
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,candidate,lockedAccount->{
                    check(account.equals(lockedAccount),
                        "raw account callback changed principal");
                    Files.write(file,concat(original,appended));
                }),drift);
            check(Arrays.equals(concat(original,appended),
                Files.readAllBytes(file)),
                "raw account edit silently rolled back or cleaned up");
            Files.write(file,original);
            verifyUnchangedNoAuthority(repo,account,candidate);

            // A newly created review marker flips negative state inside
            // the same held advisory lock and MUST never be waved through
            // as the earlier LEGACY observation.
            Path marker=root.resolve(
                account+".properties.g2132-mailbox-review");
            byte[] markerBody="G21165_EXTERNAL_HOLD\n"
                .getBytes(StandardCharsets.US_ASCII);
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,candidate,lockedAccount->
                    Files.write(marker,markerBody)),drift);
            check(Arrays.equals(markerBody,Files.readAllBytes(marker)),
                "raw review marker overwritten or removed");
            // Initial marker remains negative. Rewriting marker content
            // *without* changing marker presence/state must be detected
            // by the physical SHA-256 witness.
            byte[] markerChanged="G21165_EXTERNAL_CHANGED\n"
                .getBytes(StandardCharsets.US_ASCII);
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,candidate,lockedAccount->
                    Files.write(marker,markerChanged)),drift);
            check(Arrays.equals(markerChanged,Files.readAllBytes(marker)),
                "in-place marker mutation was not preserved");
            // Test-fixture cleanup only, never invoked by admission.
            Files.delete(marker);
            verifyUnchangedNoAuthority(repo,account,candidate);

            // Optional PREPARED evidence is one of the actual G21.87
            // seven physical leaves. Corrupt raw insertion must be
            // a checked veto, not accepted as signed/committed state.
            Path prepared=root.resolve(
                account+".properties"+
                    MailboxDurableIdempotencyIntentJournal.SUFFIX);
            byte[] preparedBody="G21165_RAW_PREPARED_NOT_AUTHENTIC\n"
                .getBytes(StandardCharsets.US_ASCII);
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,candidate,lockedAccount->
                    Files.write(prepared,preparedBody)),drift);
            check(Arrays.equals(preparedBody,Files.readAllBytes(prepared)),
                "raw PREPARED sidecar removed or rewritten");
            Files.delete(prepared);
            verifyUnchangedNoAuthority(repo,account,candidate);

            // COMMIT evidence is also observed, not trusted. Its
            // unexpected creation cannot authorize recovery either.
            Path commit=root.resolve(
                account+".properties"+MailboxGuardedDiskCommitRecord.SUFFIX);
            byte[] commitBody="G21165_RAW_COMMIT_NOT_AUTHENTIC\n"
                .getBytes(StandardCharsets.US_ASCII);
            veto(()->repo.requireFreshCutoverUnderPublicationLock(
                account,candidate,lockedAccount->
                    Files.write(commit,commitBody)),drift);
            check(Arrays.equals(commitBody,Files.readAllBytes(commit)),
                "raw COMMIT sidecar removed or rewritten");
            Files.delete(commit);
            verifyUnchangedNoAuthority(repo,account,candidate);

            check(Arrays.equals(original,Files.readAllBytes(file)),
                "repository mutated the original account");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM lock/watch leak");
            System.out.println("G21165_SEVEN_FILE_CUTOVER_DIAGNOSTICS"+
                " realRawAccountRewriteVetoes=1"+
                " realMarkerCreationVetoes=1"+
                " realMarkerSameStateByteRewriteVetoes=1"+
                " realPreparedInsertionVetoes=1"+
                " realCommitInsertionVetoes=1"+
                " healthyLegacyDenials=5"+
                " rawWritesIgnoredCooperatingLock=true"+
                " originalAccountUnchanged=true"+
                " zeroJvmLeases="+noLeaks);
            System.out.println("G21165_SEVEN_FILE_CUTOVER_PASS"+
                " sevenLeafWitnessSameCriticalSection=true"+
                " negativeOnlyDriftDetection=true"+
                " historicalAuthentication=false"+
                " rawWriterExclusion=false"+
                " atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally {
            try(Stream<Path> files=Files.walk(root)) {
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    private static byte[] concat(byte[] a,byte[] b) {
        byte[] joined=Arrays.copyOf(a,a.length+b.length);
        System.arraycopy(b,0,joined,a.length,b.length);
        return joined;
    }

    public static void main(String[] args)throws Exception {
        check(args.length==0,"unexpected args");
        run();
    }
}
