package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * G21.125: bounded, canonical, IN-MEMORY capture of four actual v3
 * result leaf names and raw bytes, with independently supplied
 * SHA-256 for read-only comparison to G21.124 four-result cohort.
 *
 * No archive write, deletion, mutation, writer enrollment, historical
 * proof, independent signer authentication or safe archival epoch.
 */
final class MailboxFourBoundResultLeavesArchiveV1 {
    private static final String FORMAT="SPK-G21125-FOUR-V3-LEAVES-V1";
    private static final long LOCK_MS=1500L;
    private static final int MAX_LEAVES=4;
    private static final int MAX_LEAF_BYTES=512;
    private static final String UUID=
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"+
        "[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String[] PREFIX={
        ".g21120-commit-result-v3-",
        ".g21121-prepared-result-v3-",
        ".g21122-strict-prepared-result-v3-",
        ".g21123-world-terminal-result-v3-"
    };

    static final class Snapshot {
        final String portable;
        final String sha256;
        final int resultLeaves;
        final boolean independentProvenanceAuthenticated=false;
        final boolean historicalWriterCensusProved=false;
        final boolean rawAndDefaultWritersExcluded=false;
        final boolean futureWriterFenceProved=false;
        final boolean atomicMultiFileEpochProved=false;
        final boolean hardwarePowerLossProved=false;
        final boolean archiveRolloverAuthorized=false;
        final boolean recordPruningAuthorized=false;
        final boolean cleanupAuthorized=false;
        final boolean unlinkAuthorized=false;
        final boolean grantAuthorized=false;
        final boolean replayAuthorized=false;
        final boolean sessionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean clientAckAuthorized=false;
        private Snapshot(String content,String digest){
            portable=content;
            sha256=digest;
            resultLeaves=4;
        }
    }

    static final class Comparison {
        final Snapshot before,after;
        final MailboxFourBoundWriterCohortAuditV1.TwoObservations
            fourWriterCohort;
        final boolean samePinnedResultLeavesObserved;
        final boolean allIntervalsFenced=false;
        final boolean independentSourceAuthenticationProved=false;
        final boolean safeArchiveEpochProved=false;
        final boolean cleanupAuthorized=false;
        final boolean rolloverAuthorized=false;
        final boolean grantAuthorized=false;
        private Comparison(
            Snapshot a,Snapshot b,
            MailboxFourBoundWriterCohortAuditV1.TwoObservations c
        ){
            before=a;after=b;fourWriterCohort=c;
            samePinnedResultLeavesObserved=a.sha256.equals(b.sha256);
        }
    }

    private static IOException veto(String problem){
        return new IOException(
            "G21.125 FOUR_RESULT_LEAF_WITNESS_UNSAFE_NO_AUTHORITY "+
            problem+" epoch=false cleanup=false grant=false");
    }
    private static String checked(String name)throws IOException{
        if(name==null||!name.matches("[a-z0-9_-]{1,64}"))
            throw veto("NONCANONICAL_ACCOUNT");
        return name;
    }
    private static Path selected(Path file){
        return Objects.requireNonNull(file,"file").toAbsolutePath()
            .normalize();
    }
    private static String digest(byte[] bytes){
        try{
            byte[] result=MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder out=new StringBuilder(64);
            for(byte b:result)
                out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IllegalStateException(impossible);
        }
    }
    private static byte[] safeRead(Path file)throws IOException{
        final BasicFileAttributes first;
        try{
            first=Files.readAttributes(file,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(IOException missing){
            throw veto("RESULT_LEAF_UNREADABLE");
        }
        if(!first.isRegularFile()||first.isSymbolicLink()||
           first.fileKey()==null||first.size()<1||
           first.size()>MAX_LEAF_BYTES)
            throw veto("UNSAFE_RESULT_LEAF_TYPE_OR_SIZE");
        ByteBuffer bytes=ByteBuffer.allocate(MAX_LEAF_BYTES+1);
        try(FileChannel channel=FileChannel.open(file,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(bytes.hasRemaining()){
                int n=channel.read(bytes);
                if(n<0)break;
                if(n==0)throw veto("STALLED_RESULT_READ");
            }
        }
        BasicFileAttributes last=Files.readAttributes(file,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes.position()!=first.size()||
           first.size()!=last.size()||
           !Objects.equals(first.fileKey(),last.fileKey())||
           !first.lastModifiedTime().equals(last.lastModifiedTime()))
            throw veto("RESULT_PHYSICAL_IDENTITY_DRIFT");
        byte[] read=new byte[bytes.position()];
        System.arraycopy(bytes.array(),0,read,0,read.length);
        return read;
    }

    // G21.129: may also be used ONLY inside an already-held G21.100
    // OS advisory lease; no nested FileLock acquisition.
    static Snapshot captureInside(Path file,String account)
        throws IOException{
        String basename=file.getFileName().toString();
        boolean[] observed=new boolean[4];
        List<String> entries=new ArrayList<>(4);
        // G21.125 scan only this account's G21.12x result namespace;
        // unknown filenames/versions in that namespace veto.
        try(DirectoryStream<Path> listing=Files.newDirectoryStream(
                file.getParent(),basename+".g2112*")){
            for(Path child:listing){
                String leaf=child.getFileName().toString();
                int kind=-1;
                for(int i=0;i<PREFIX.length;i++){
                    if(leaf.startsWith(basename+PREFIX[i])){
                        kind=i;
                        break;
                    }
                }
                if(kind<0)
                    throw veto("UNKNOWN_G2112_RESULT_LEAF");
                if(observed[kind])
                    throw veto("DUPLICATE_RESULT_KIND");
                observed[kind]=true;
                String end=leaf.substring(
                    (basename+PREFIX[kind]).length());
                if(!end.matches(UUID+"\\.result"))
                    throw veto("UNKNOWN_RESULT_NAME_OR_VERSION");
                byte[] bytes=safeRead(child);
                entries.add("KIND="+kind+"\nLEAF="+leaf+
                    "\nBYTES_BASE64="+Base64.getEncoder()
                        .encodeToString(bytes)+"\n");
                if(entries.size()>MAX_LEAVES)
                    throw veto("OVER_FOUR_RESULT_LEAVES");
            }
        }
        if(entries.size()!=4)
            throw veto("MISSING_BOUND_RESULT_LEAF");
        for(boolean b:observed)
            if(!b)throw veto("MISSING_RESULT_KIND");
        Collections.sort(entries);
        StringBuilder portable=new StringBuilder(
            FORMAT+"\nACCOUNT="+account+"\nCOUNT=4\n");
        for(String entry:entries)portable.append(entry);
        String content=portable.toString();
        if(content.length()>4000)
            throw veto("PORTABLE_RESULT_INVENTORY_TOO_LARGE");
        return new Snapshot(content,
            digest(content.getBytes(StandardCharsets.US_ASCII)));
    }

    static Snapshot capture(
        FilePlayerRepository.PathResolver resolver,String account
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        String name=checked(account);
        Path file=selected(Objects.requireNonNull(
            resolver.resolve(name),"account file"));
        return MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                MailboxPublicationWriterLifecycle.domain(file),
                LOCK_MS,()->captureInside(file,name));
    }

    private static void requirePinned(
        Snapshot s,String expectedDigest
    )throws IOException{
        if(expectedDigest==null||
           !expectedDigest.matches("[0-9a-f]{64}")||
           !expectedDigest.equals(s.sha256))
            throw veto("INDEPENDENT_RESULT_LEAF_PIN_MISMATCH");
    }

    /**
     * Separate G21.125 v3-leaf inventory pinned externally plus
     * existing original G21.124 two-pass typed cohort and v1/v2/
     * account/PREPARED/COMMIT independently pinned content.
     *
     * These acquisitions are successive observations, NOT a
     * synchronized multi-file epoch. Callback is UNLOCKED.
     */
    static Comparison comparePinnedFourResultLeaves(
        FilePlayerRepository.PathResolver resolver,String account,
        String resultLeavesSha256,String v1Portable,String v1Sha,
        String accountSha,String preparedSha,String commitSha,
        String v2Sha,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        String name=checked(account);
        Path file=selected(Objects.requireNonNull(
            resolver.resolve(name),"account file"));
        FilePlayerRepository.PathResolver exact=who->{
            if(!name.equals(who))
                throw new IllegalArgumentException(
                    "G21.125 ACCOUNT_PATH_CHANGED");
            return file;
        };
        Snapshot before=capture(exact,name);
        requirePinned(before,resultLeavesSha256);
        MailboxFourBoundWriterCohortAuditV1.TwoObservations cohort=
            MailboxFourBoundWriterCohortAuditV1
                .comparePinnedFourBoundResults(
                    exact,name,v1Portable,v1Sha,accountSha,
                    preparedSha,commitSha,v2Sha,between);
        Snapshot after=capture(exact,name);
        requirePinned(after,resultLeavesSha256);
        if(!cohort.observedAgreement||
           !before.portable.equals(after.portable))
            throw veto("BETWEEN_OBSERVATIONS_RESULT_DRIFT");
        return new Comparison(before,after,cohort);
    }

    private MailboxFourBoundResultLeavesArchiveV1(){}
}
