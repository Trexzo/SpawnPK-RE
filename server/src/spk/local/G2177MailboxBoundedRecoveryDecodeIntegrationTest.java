package spk.local;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.77: reject giant forensic account/marker files before decoding,
 * and bound decoder + independently rehashed byte streams if a raw
 * writer grows the file after its initial NOFOLLOW metadata stat.
 */
public final class G2177MailboxBoundedRecoveryDecodeIntegrationTest {
    private static final long ACCOUNT_MAX=64L*1024L*1024L;
    private static final int MARKER_MAX=4096;

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2177-bounded-forensic-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        AtomicInteger enteredDecoder=new AtomicInteger();
        FilePlayerRepository instrumented=new FilePlayerRepository(
            paths,a->{},a->{},a->enteredDecoder.incrementAndGet(),
            a->{});
        FilePlayerRepository fresh=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);

        boolean accountSparseCreated=false;
        boolean accountOversizePreflight=false;
        boolean accountDecoderNotEntered=false;
        boolean accountUnchangedAfterVeto=false;
        boolean markerSparseCreated=false;
        boolean markerOversizePreflight=false;
        boolean markerDecoderNotEntered=false;
        boolean markerUnchangedAfterVeto=false;
        boolean markerAtLimitAccepted=false;
        boolean markerStillSessionVeto=false;
        boolean regularAccountStable=false;
        boolean decoderRejectsGrowth=false;
        boolean decoderExactBoundaryAccepted=false;
        boolean rehashRejectsGrowth=false;
        boolean rehashExactBoundaryAccepted=false;
        boolean previousLegacyReaderUnchanged=false;
        boolean unrelatedAccountStable=false;
        boolean missingAccountStable=false;
        boolean noPositiveAuthority=true;
        boolean noTempOrLeaseLeaks=false;
        try{
            String over="g2177-oversize";
            Path oversized=paths.resolve(over);
            sparse(oversized,ACCOUNT_MAX+1);
            accountSparseCreated=Files.size(oversized)==ACCOUNT_MAX+1;
            try{
                instrumented.captureRestartContinuityTokenReadOnly(over);
            }catch(IOException rejected){
                accountOversizePreflight=rejected.getMessage().contains(
                    "G21.77 RECOVERY_ACCOUNT_OVERSIZE_NO_GRANT");
            }
            accountDecoderNotEntered=enteredDecoder.get()==0;
            accountUnchangedAfterVeto=
                Files.size(oversized)==ACCOUNT_MAX+1&&
                Files.readAttributes(oversized,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isRegularFile();
            // The sparse file is never parsed. A tiny tail read rather
            // than a full read confirms that the sentinel is intact.
            Files.delete(oversized);

            String marked="g2177-marker";
            WorldPlayer ordinary=new WorldPlayer();
            ordinary.markRegistered(marked);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                marked,ordinary));
            Path marker=new MailboxStrictWriteIntentFence(paths)
                .fencePath(marked);
            sparse(marker,MARKER_MAX+1L);
            markerSparseCreated=Files.size(marker)==MARKER_MAX+1L;
            try{
                instrumented.captureRestartContinuityTokenReadOnly(marked);
            }catch(IOException rejected){
                markerOversizePreflight=rejected.getMessage().contains(
                    "G21.77 RECOVERY_MARKER_OVERSIZE_NO_GRANT");
            }
            markerDecoderNotEntered=enteredDecoder.get()==0;
            markerUnchangedAfterVeto=Files.size(marker)==MARKER_MAX+1L;
            Files.write(marker,new byte[MARKER_MAX],
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            String reviewedToken=instrumented
                .captureRestartContinuityTokenReadOnly(marked);
            markerAtLimitAccepted=unchanged(
                fresh.compareRestartContinuityReadOnly(
                    marked,reviewedToken));
            try{
                fresh.loadForWorldSession(marked);
            }catch(IOException markerDenied){
                markerStillSessionVeto=markerDenied.getMessage()
                    .contains("MAILBOX_DURABLE_REVIEW_FENCE");
            }

            String normal="g2177-normal";
            WorldPlayer independent=new WorldPlayer();
            independent.markRegistered(normal);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                normal,independent));
            Path ordinaryFile=paths.resolve(normal);
            // A valid Properties comment extends the raw file but
            // preserves the original legacy account's decode.
            byte[] comment=new byte[8192];
            Arrays.fill(comment,(byte)'q');
            comment[0]=(byte)'#';
            comment[comment.length-1]=(byte)'\n';
            Files.write(ordinaryFile,comment,StandardOpenOption.APPEND);
            String normalToken=fresh
                .captureRestartContinuityTokenReadOnly(normal);
            regularAccountStable=unchanged(
                new FilePlayerRepository(paths)
                    .compareRestartContinuityReadOnly(
                        normal,normalToken));

            // White-box, deterministic streaming-bound tests avoid a
            // timing-sensitive writer thread. In a forensic call, these
            // private overloads receive the 64MiB boundary. Here a
            // smaller test limit forces the overflow code path with a
            // valid account well below the normal advertised maximum.
            Method decoder=FilePlayerRepository.class.getDeclaredMethod(
                "loadExactFile",String.class,Path.class,boolean.class,
                MessageDigest.class,long.class);
            decoder.setAccessible(true);
            long exact=Files.size(ordinaryFile);
            decoderRejectsGrowth=rejects(decoder,fresh,
                new Object[]{normal,ordinaryFile,true,
                    MessageDigest.getInstance("SHA-256"),exact-1},
                "G21.77 RECOVERY_STREAM_OVERSIZE_NO_GRANT");
            Object admitted=decoder.invoke(fresh,
                normal,ordinaryFile,true,
                MessageDigest.getInstance("SHA-256"),exact);
            decoderExactBoundaryAccepted=
                admitted instanceof Optional&&
                ((Optional<?>)admitted).isPresent();

            Method digest=FilePlayerRepository.class.getDeclaredMethod(
                "digestAdmittedAccountFile",Path.class,long.class);
            digest.setAccessible(true);
            rehashRejectsGrowth=rejects(digest,null,
                new Object[]{ordinaryFile,exact-1},
                "G21.77 RECOVERY_STREAM_OVERSIZE_NO_GRANT");
            byte[] originalSha=(byte[])digest.invoke(
                null,ordinaryFile,exact);
            rehashExactBoundaryAccepted=MessageDigest.isEqual(
                originalSha,MessageDigest.getInstance("SHA-256").digest(
                    Files.readAllBytes(ordinaryFile)));

            previousLegacyReaderUnchanged=fresh.load(normal).isPresent();
            String independentName="g2177-unrelated";
            WorldPlayer unaffected=new WorldPlayer();
            unaffected.markRegistered(independentName);
            strict.saveStrict(PlayerSnapshotCodec.capture(
                independentName,unaffected));
            String unrelated=fresh.captureRestartContinuityTokenReadOnly(
                independentName);
            unrelatedAccountStable=unchanged(
                instrumented.compareRestartContinuityReadOnly(
                    independentName,unrelated));

            String missing="g2177-missing";
            String absent=fresh.captureRestartContinuityTokenReadOnly(
                missing);
            missingAccountStable=unchanged(
                instrumented.compareRestartContinuityReadOnly(
                    missing,absent));
            FilePlayerRepository.RestartContinuityComparison noGrant=
                fresh.compareRestartContinuityReadOnly(normal,normalToken);
            noPositiveAuthority=!noGrant.grantAuthorized&&
                !noGrant.replayAuthorized&&!noGrant.releaseAuthorized&&
                !noGrant.transactionCommitted&&
                !noGrant.restartAdmissionAuthorized&&
                !noGrant.clientAckAuthorized&&
                independent.bank().inventorySlots()==0;

            try(Stream<Path> files=Files.walk(root)){
                noTempOrLeaseLeaks=files.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("G2177_BOUNDED_FORENSICS_DIAGNOSTICS"+
            " accountSparseCreated="+accountSparseCreated+
            " accountOversizePreflight="+accountOversizePreflight+
            " accountDecoderNotEntered="+accountDecoderNotEntered+
            " accountUnchangedAfterVeto="+accountUnchangedAfterVeto+
            " markerSparseCreated="+markerSparseCreated+
            " markerOversizePreflight="+markerOversizePreflight+
            " markerDecoderNotEntered="+markerDecoderNotEntered+
            " markerUnchangedAfterVeto="+markerUnchangedAfterVeto+
            " markerAtLimitAccepted="+markerAtLimitAccepted+
            " markerStillSessionVeto="+markerStillSessionVeto+
            " regularAccountStable="+regularAccountStable+
            " decoderRejectsGrowth="+decoderRejectsGrowth+
            " decoderExactBoundaryAccepted="+
                decoderExactBoundaryAccepted+
            " rehashRejectsGrowth="+rehashRejectsGrowth+
            " rehashExactBoundaryAccepted="+
                rehashExactBoundaryAccepted+
            " previousLegacyReaderUnchanged="+
                previousLegacyReaderUnchanged+
            " unrelatedAccountStable="+unrelatedAccountStable+
            " missingAccountStable="+missingAccountStable+
            " noPositiveAuthority="+noPositiveAuthority+
            " noTempOrLeaseLeaks="+noTempOrLeaseLeaks);
        if(!(accountSparseCreated&&accountOversizePreflight&&
             accountDecoderNotEntered&&accountUnchangedAfterVeto&&
             markerSparseCreated&&markerOversizePreflight&&
             markerDecoderNotEntered&&markerUnchangedAfterVeto&&
             markerAtLimitAccepted&&markerStillSessionVeto&&
             regularAccountStable&&decoderRejectsGrowth&&
             decoderExactBoundaryAccepted&&rehashRejectsGrowth&&
             rehashExactBoundaryAccepted&&
             previousLegacyReaderUnchanged&&unrelatedAccountStable&&
             missingAccountStable&&noPositiveAuthority&&
             noTempOrLeaseLeaks))
            throw new AssertionError(
                "G21.77 unbounded forensic decoder regression");
        System.out.println("G2177_BOUNDED_FORENSICS_PASS"+
            " earlyPreflight=true boundedDecode=true boundedRehash=true"+
            " grant=false replay=false admission=false release=false");
    }

    private static void sparse(Path path,long size)throws IOException{
        try(FileChannel channel=FileChannel.open(path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)){
            channel.position(size-1L);
            channel.write(ByteBuffer.wrap(new byte[]{(byte)'Z'}));
        }
    }

    private static boolean rejects(
        Method method,Object receiver,Object[] args,String error
    )throws Exception{
        try{
            method.invoke(receiver,args);
            return false;
        }catch(InvocationTargetException reflected){
            return reflected.getCause() instanceof IOException&&
                reflected.getCause().getMessage().contains(error);
        }
    }

    private static boolean unchanged(
        FilePlayerRepository.RestartContinuityComparison result
    ){
        return result.state==
            FilePlayerRepository.RestartContinuityComparison.State
                .UNCHANGED_FORENSICS_NO_GRANT&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.releaseAuthorized&&!result.restartAdmissionAuthorized;
    }
    private G2177MailboxBoundedRecoveryDecodeIntegrationTest(){}
}
