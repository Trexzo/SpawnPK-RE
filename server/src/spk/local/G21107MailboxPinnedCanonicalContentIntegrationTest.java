package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** G21.107: negative-only canonical bytes pinned separately from v1 archive. */
public final class G21107MailboxPinnedCanonicalContentIntegrationTest {
    private static final String ACCOUNT="g21107-account";

    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.107 "+message);
    }

    private static String hash(byte[] bytes)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest)
            hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }

    private static void denied(RunnableIO operation,String reason)
        throws Exception{
        boolean rejected=false;
        try{
            operation.run();
        }catch(IOException expected){
            rejected=String.valueOf(expected.getMessage()).contains(reason);
        }
        check(rejected,"expected fail-closed "+reason);
    }

    @FunctionalInterface
    private interface RunnableIO { void run()throws IOException; }

    private static void mutateSamePhysical(
        Path account,byte[] bytes,FileTime original
    )throws Exception{
        byte[] change=Arrays.copyOf(bytes,bytes.length);
        change[0]=(byte)(change[0]^1);
        Files.write(account,change);
        Files.setLastModifiedTime(account,original);
        check(Files.size(account)==bytes.length,
            "same-size fixture changed length");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21107-pinned-bytes-");
        boolean valid=false,changedDenied=false,interpassDenied=false;
        boolean wrongHashDenied=false,oversizedDenied=false;
        boolean oldApiStillUnpinned=false,identityUnchanged=false;
        boolean noAuthority=false,noLeases=false;
        try{
            FilePlayerRepository.PathResolver paths=
                name->root.resolve(name+".properties");
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(paths);
            writer.saveStrict(PlayerSnapshotCodec.capture(
                ACCOUNT,new WorldPlayer()));
            MailboxWriterAttemptEvidenceV1.runRecorded(
                paths,ACCOUNT,()->true);
            MailboxWriterEvidencePortableArchiveV1.Snapshot archive=
                MailboxWriterEvidencePortableArchiveV1.capture(
                    paths,ACCOUNT);
            Path account=paths.resolve(ACCOUNT);
            byte[] original=Files.readAllBytes(account);
            FileTime timestamp=Files.getLastModifiedTime(
                account,LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes physical=Files.readAttributes(
                account,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            String pinned=hash(original);
            MailboxWriterArchiveLiveReconciliationV1.Report good=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedAccount(paths,ACCOUNT,
                        archive.portable,archive.sha256,pinned);
            valid=good.pinnedCanonicalBytesMatched&&
                good.matchedRecords==2;
            noAuthority=!good.authoritativeAccountContentProved&&
                !good.cleanupAuthorized&&!good.unlinkAuthorized&&
                !good.rolloverAuthorized&&!good.grantAuthorized&&
                !good.replayAuthorized&&!good.sessionAdmissionAuthorized&&
                !good.clientAckAuthorized;
            denied(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                    archive.sha256,"f".repeat(64)),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");
            wrongHashDenied=true;
            denied(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                    archive.sha256,"not-a-sha256"),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");

            mutateSamePhysical(account,original,timestamp);
            BasicFileAttributes after=Files.readAttributes(
                account,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            identityUnchanged=physical.fileKey().equals(
                after.fileKey())&&physical.size()==after.size()&&
                physical.lastModifiedTime().equals(
                    after.lastModifiedTime());
            check(identityUnchanged,
                "fixture did not preserve inode, size and mtime");
            // The old G21.106 API deliberately has no canonical digest.
            MailboxWriterArchiveLiveReconciliationV1.Report old=
                MailboxWriterArchiveLiveReconciliationV1.compare(
                    paths,ACCOUNT,archive.portable,archive.sha256);
            oldApiStillUnpinned=!old.pinnedCanonicalBytesMatched&&
                !old.authoritativeAccountContentProved;
            denied(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                    archive.sha256,pinned),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");
            changedDenied=true;
            Files.write(account,original);
            Files.setLastModifiedTime(account,timestamp);

            denied(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                    archive.sha256,pinned,()->{
                        try{
                            mutateSamePhysical(account,original,timestamp);
                        }catch(Exception e){
                            throw new IOException(e);
                        }
                    }),"G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");
            interpassDenied=true;
            Files.write(account,original);
            Files.setLastModifiedTime(account,timestamp);

            // Deliberately exceeds the explicitly bounded 1 MiB reader.
            Files.write(account,new byte[1024*1024+1]);
            denied(()->MailboxWriterArchiveLiveReconciliationV1
                .comparePinnedAccount(paths,ACCOUNT,archive.portable,
                    archive.sha256,pinned),
                "G21.107 PINNED_CANONICAL_CONTENT_MISMATCH");
            oversizedDenied=true;
            Files.write(account,original);
            Files.setLastModifiedTime(account,timestamp);
            noLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            check(Arrays.equals(original,Files.readAllBytes(account)),
                "original restored");
            check(valid&&wrongHashDenied&&identityUnchanged&&
                changedDenied&&interpassDenied&&oversizedDenied&&
                oldApiStillUnpinned&&noAuthority&&noLeases,
                "failed pinned content contract");
            System.out.println("G21107_PINNED_CANONICAL_DIAGNOSTICS"+
                " independentlyPinnedValid="+valid+
                " sameInodeSameSizeMtimeTamperDenied="+changedDenied+
                " interpassChangedBytesDenied="+interpassDenied+
                " malformedAndWrongDigestDenied="+wrongHashDenied+
                " oversizedCanonicalDenied="+oversizedDenied+
                " legacyG21106StillUnpinned="+oldApiStillUnpinned+
                " originalCanonicalRestored=true"+
                " jvmLeasesZero="+noLeases);
            System.out.println("G21107_PINNED_CANONICAL_CONTENT_PASS"+
                " records=2 canonicalSha256Independent=true"+
                " cleanup=false unlink=false rollover=false"+
                " grant=false replay=false session=false ack=false");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
