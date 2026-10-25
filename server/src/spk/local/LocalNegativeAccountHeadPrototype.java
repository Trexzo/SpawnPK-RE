package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * R50: UNWIRED, NON-ADMITTING account-head publication experiment.
 *
 * A forced immutable snapshot object is published before a forced
 * CREATE_NEW negative HEAD which references its exact SHA-256. Both
 * leaves are permanent in this prototype: no overwrite, unlock,
 * retirement, final save, World integration or recovery API exists.
 *
 * An inspected HEAD remains UNTRUSTED and DENIES admission regardless
 * of byte integrity. This code cannot grant an owner epoch or prove
 * crash/power-loss durability, especially against external writers.
 */
final class LocalNegativeAccountHeadPrototype {
    static final int MAX_SNAPSHOT_BYTES=8*1024*1024;
    private static final int MAX_HEAD_BYTES=4096;
    private static final String MAGIC="G21378_NEGATIVE_ACCOUNT_HEAD_V1";
    private static final String HEAD_SUFFIX=".g21378-negative-head";
    private static final String OBJECT_PREFIX=".g21378-object-";
    private static final String OBJECT_SUFFIX=".snapshot";

    enum Stage {
        OBJECT_FILE_FORCED_BEFORE_DIRECTORY,
        OBJECT_DIRECTORY_FORCED_BEFORE_HEAD,
        HEAD_FILE_FORCED_BEFORE_DIRECTORY,
        HEAD_DIRECTORY_FORCED_BEFORE_READBACK
    }
    @FunctionalInterface interface Cutpoint {
        void reached(Stage stage)throws IOException;
    }

    static final class NegativeObservation {
        final Path account;
        final Path immutableObject;
        final String username;
        final long untrustedWorldGeneration;
        final String untrustedEpoch;
        final String snapshotSha256;
        final int snapshotLength;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        private NegativeObservation(Path account,Path object,
            String username,long generation,String epoch,String sha,int length){
            this.account=account;
            this.immutableObject=object;
            this.username=username;
            this.untrustedWorldGeneration=generation;
            this.untrustedEpoch=epoch;
            this.snapshotSha256=sha;
            this.snapshotLength=length;
        }
    }

    private LocalNegativeAccountHeadPrototype(){}

    static Path headPath(Path account)throws IOException {
        Path exact=canonical(account);
        return exact.resolveSibling(exact.getFileName().toString()+HEAD_SUFFIX);
    }

    static Path snapshotPath(Path account,String digest)throws IOException{
        Path exact=canonical(account);
        if(digest==null||!digest.matches("[0-9a-f]{64}"))
            throw new IOException("R50_OBJECT_DIGEST_INVALID");
        return exact.resolveSibling(exact.getFileName().toString()+
            OBJECT_PREFIX+digest+OBJECT_SUFFIX);
    }

    private static Path canonical(Path path)throws IOException{
        if(path==null)throw new IOException("R50_NULL_ACCOUNT_PATH");
        Path exact=path.toAbsolutePath().normalize();
        if(exact.getParent()==null||exact.getFileName()==null)
            throw new IOException("R50_ACCOUNT_PATH_INVALID");
        return exact;
    }

    /**
     * R53: read-only denial for ANY negative HEAD leaf, whether
     * canonical, malformed, symlinked or partially written. A
     * missing leaf retains existing legacy behavior; it is not a
     * positive account-epoch receipt.
     */
    static void requireNoPublishedHead(Path account)throws IOException{
        Path exact=canonical(account);
        // Read-only negative leaf presence does NOT supersede the
        // existing writer-specific ancestry/error classification.
        // Those opt-in checks remain responsible for preflight.
        Path[] negativeHeads={
            headPath(exact),
            // R160: the independent R154-G21.377 negative HEAD is also
            // untrusted evidence. Presence blocks legacy admission and
            // writes even if its codec parses as a complete HEAD.
            exact.resolveSibling(exact.getFileName().toString()+
                LocalAccountEpochNegativeHeadPublication.SUFFIX)
        };
        for(Path head:negativeHeads){
            try{
                Files.readAttributes(head,BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            }catch(NoSuchFileException absent){
                continue;
            }
            throw new IOException(
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY"+
                " leaf="+head.getFileName());
        }
    }

    // R54: narrow package-only deterministic fault seams. In
    // production these callbacks are null; they confer no authority,
    // and can only precede a negative check. Test data are disposable.
    @FunctionalInterface interface RawPublicationProbe {
        void run(Path account)throws IOException;
    }
    private static volatile RawPublicationProbe beforeRawMoveForTest;
    private static volatile RawPublicationProbe afterRawMoveForTest;
    static void setBeforeRawMoveForTest(RawPublicationProbe probe){
        beforeRawMoveForTest=probe;
    }
    static void setAfterRawMoveForTest(RawPublicationProbe probe){
        afterRawMoveForTest=probe;
    }

    static void requireNoHeadBeforeRawMove(Path account)
        throws IOException{
        RawPublicationProbe callback=beforeRawMoveForTest;
        if(callback!=null)callback.run(account);
        requireNoPublishedHead(account);
        LocalReusableLabEvidenceNegativeGate
            .requireNoLegacyCollision(account);
    }

    static void requireNoHeadAfterRawMove(Path account)
        throws IOException{
        try{
            RawPublicationProbe callback=afterRawMoveForTest;
            if(callback!=null)callback.run(account);
            requireNoPublishedHead(account);
            LocalReusableLabEvidenceNegativeGate
                .requireNoLegacyCollision(account);
        }catch(IOException uncertainty){
            // The move may already have changed the account.
            // Do not claim rollback, a successful save or Receipt.
            throw new IOException(
                "R54_NEGATIVE_HEAD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW",
                uncertainty);
        }
    }

    private static void safeParent(Path exact)throws IOException{
        LocalCrossJvmAccountSessionLock
            .requireNoSymlinkAncestors(exact.getParent());
        if(!Files.isDirectory(exact.getParent(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R50_ACCOUNT_PARENT_UNSAFE");
    }

    private static void missing(Path leaf,String code)throws IOException{
        try{
            Files.readAttributes(leaf,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return;
        }
        throw new IOException(code);
    }

    private static void forceDirectory(Path parent)throws IOException{
        try(FileChannel directory=FileChannel.open(parent,
            StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            directory.force(true);
        }
    }

    private static void createForced(Path leaf,byte[] bytes,int max)
        throws IOException{
        if(bytes==null||bytes.length==0||bytes.length>max)
            throw new IOException("R50_FORCED_OBJECT_BYTES_OUT_OF_BOUNDS");
        // Direct CREATE_NEW is intentional: any torn/incomplete HEAD
        // stays a negative leaf, never promoted or overwritten.
        try(FileChannel channel=FileChannel.open(leaf,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer b=ByteBuffer.wrap(bytes);
            while(b.hasRemaining())channel.write(b);
            channel.force(true);
        }
    }

    private static byte[] boundedRead(Path leaf,int maximum)
        throws IOException{
        final BasicFileAttributes before=Files.readAttributes(
            leaf,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.isSymbolicLink()||
                before.fileKey()==null||before.size()<=0||
                before.size()>maximum)
            throw new IOException("R50_RECORD_NONREGULAR_OR_UNBOUNDED");
        ByteBuffer value=ByteBuffer.allocate(maximum+1);
        try(FileChannel channel=FileChannel.open(leaf,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(value.hasRemaining()){
                int n=channel.read(value);
                if(n<0)break;
                if(n==0)throw new IOException("R50_RECORD_READ_STALLED");
            }
        }
        if(value.position()==0||value.position()>maximum)
            throw new IOException("R50_RECORD_BOUNDED_READ_FAILED");
        final BasicFileAttributes after=Files.readAttributes(
            leaf,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!after.isRegularFile()||after.isSymbolicLink()||
                !Objects.equals(before.fileKey(),after.fileKey())||
                !Objects.equals(before.creationTime(),after.creationTime())||
                before.size()!=after.size()||before.size()!=value.position())
            throw new IOException("R50_RECORD_OBJECT_CHANGED_DURING_READ");
        return Arrays.copyOf(value.array(),value.position());
    }

    static NegativeObservation publishNegative(
        Path account,String username,long worldGeneration,byte[] snapshot
    )throws IOException{
        return publishNegative(account,username,worldGeneration,
            snapshot,stage->{});
    }

    static NegativeObservation publishNegative(
        Path account,String username,long worldGeneration,
        byte[] snapshot,Cutpoint cutpoint
    )throws IOException{
        if(username==null||!username.matches("[a-z0-9_-]{1,64}")||
                worldGeneration<=0||cutpoint==null||snapshot==null||
                snapshot.length==0||snapshot.length>MAX_SNAPSHOT_BYTES)
            throw new IOException("R50_NEGATIVE_HEAD_ARGUMENTS_INVALID");
        final Path exact=canonical(account);
        safeParent(exact);
        final Path head=headPath(exact);
        missing(head,"R50_HEAD_ALREADY_PRESENT_NO_OVERWRITE");

        final String digest=sha(snapshot);
        final Path object=snapshotPath(exact,digest);
        // A preexisting object, even byte-identical, does not confer
        // publication authority or permit implicit reuse.
        missing(object,"R50_OBJECT_ALREADY_PRESENT_NO_REUSE");
        createForced(object,snapshot,MAX_SNAPSHOT_BYTES);
        cutpoint.reached(Stage.OBJECT_FILE_FORCED_BEFORE_DIRECTORY);
        forceDirectory(exact.getParent());
        cutpoint.reached(Stage.OBJECT_DIRECTORY_FORCED_BEFORE_HEAD);
        safeParent(exact);
        if(!MessageDigest.isEqual(snapshot,boundedRead(object,
                MAX_SNAPSHOT_BYTES)))
            throw new IOException("R50_STAGED_OBJECT_CHANGED_NO_HEAD");
        missing(head,"R50_HEAD_ALREADY_PRESENT_NO_OVERWRITE");

        final String epoch=UUID.randomUUID().toString();
        final String pathDigest=sha(exact.toString().getBytes(
            StandardCharsets.UTF_8));
        final String body=MAGIC+"\n"+
            "accountPathSha256="+pathDigest+"\n"+
            "username="+username+"\n"+
            "untrustedEpoch="+epoch+"\n"+
            "untrustedWorldGeneration="+worldGeneration+"\n"+
            "snapshotSha256="+digest+"\n"+
            "snapshotLength="+snapshot.length+"\n"+
            "state=ACTIVE_NO_ADMISSION_NO_RELEASE\n"+
            "recoveryAuthorized=false\n";
        createForced(head,body.getBytes(StandardCharsets.US_ASCII),
            MAX_HEAD_BYTES);
        cutpoint.reached(Stage.HEAD_FILE_FORCED_BEFORE_DIRECTORY);
        forceDirectory(exact.getParent());
        cutpoint.reached(Stage.HEAD_DIRECTORY_FORCED_BEFORE_READBACK);
        NegativeObservation observed=inspectNegative(exact);
        if(!username.equals(observed.username)||
                worldGeneration!=observed.untrustedWorldGeneration||
                !epoch.equals(observed.untrustedEpoch)||
                !digest.equals(observed.snapshotSha256))
            throw new IOException("R50_NEGATIVE_HEAD_READBACK_CHANGED");
        return observed;
    }

    static NegativeObservation inspectNegative(Path account)
        throws IOException{
        final Path exact=canonical(account);
        safeParent(exact);
        final Path head=headPath(exact);
        final byte[] before;
        try{
            before=boundedRead(head,MAX_HEAD_BYTES);
        }catch(NoSuchFileException absent){
            throw new IOException("R50_HEAD_MISSING_NEGATIVE_NO_ADMISSION",
                absent);
        }
        final String[] lines=new String(before,StandardCharsets.US_ASCII)
            .split("\n",-1);
        if(lines.length!=10||!MAGIC.equals(lines[0])||
                !lines[1].startsWith("accountPathSha256=")||
                !lines[2].startsWith("username=")||
                !lines[3].startsWith("untrustedEpoch=")||
                !lines[4].startsWith("untrustedWorldGeneration=")||
                !lines[5].startsWith("snapshotSha256=")||
                !lines[6].startsWith("snapshotLength=")||
                !"state=ACTIVE_NO_ADMISSION_NO_RELEASE".equals(lines[7])||
                !"recoveryAuthorized=false".equals(lines[8])||
                !lines[9].isEmpty())
            throw new IOException("R50_MALFORMED_HEAD_NEGATIVE");
        String pathHash=lines[1].substring("accountPathSha256=".length());
        if(!sha(exact.toString().getBytes(StandardCharsets.UTF_8))
                .equals(pathHash))
            throw new IOException("R50_FOREIGN_ACCOUNT_HEAD_NEGATIVE");
        String username=lines[2].substring("username=".length());
        String epoch=lines[3].substring("untrustedEpoch=".length());
        if(!username.matches("[a-z0-9_-]{1,64}"))
            throw new IOException("R50_HEAD_USERNAME_INVALID_NEGATIVE");
        try{
            if(!UUID.fromString(epoch).toString().equals(epoch))
                throw new IllegalArgumentException("noncanonical epoch");
        }catch(IllegalArgumentException error){
            throw new IOException("R50_HEAD_EPOCH_INVALID_NEGATIVE",error);
        }
        final long generation;
        final int length;
        try{
            generation=Long.parseLong(lines[4].substring(
                "untrustedWorldGeneration=".length()));
            length=Integer.parseInt(lines[6].substring(
                "snapshotLength=".length()));
        }catch(NumberFormatException malformed){
            throw new IOException("R50_HEAD_NUMERIC_INVALID_NEGATIVE",
                malformed);
        }
        if(generation<=0||length<=0||length>MAX_SNAPSHOT_BYTES)
            throw new IOException("R50_HEAD_BOUNDS_INVALID_NEGATIVE");
        String digest=lines[5].substring("snapshotSha256=".length());
        Path object=snapshotPath(exact,digest);
        final byte[] objectBytes;
        try{
            objectBytes=boundedRead(object,MAX_SNAPSHOT_BYTES);
        }catch(IOException missingOrUnsafe){
            throw new IOException("R50_REFERENCED_OBJECT_UNSAFE_NEGATIVE",
                missingOrUnsafe);
        }
        if(objectBytes.length!=length||!sha(objectBytes).equals(digest))
            throw new IOException("R50_REFERENCED_OBJECT_DIGEST_NEGATIVE");
        // Repeat both reads: metadata alone does not detect an external
        // in-place byte mutation that restores the original mtime.
        if(!Arrays.equals(before,boundedRead(head,MAX_HEAD_BYTES))||
                !Arrays.equals(objectBytes,boundedRead(
                    object,MAX_SNAPSHOT_BYTES)))
            throw new IOException("R50_POSTREAD_BYTES_CHANGED_NEGATIVE");
        safeParent(exact);
        return new NegativeObservation(exact,object,
            username,generation,epoch,digest,length);
    }

    private static String sha(byte[] bytes)throws IOException{
        try{
            byte[] raw=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result=new StringBuilder(raw.length*2);
            for(byte n:raw){
                String part=Integer.toHexString(n&255);
                if(part.length()==1)result.append('0');
                result.append(part);
            }
            return result.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("R50_SHA256_UNAVAILABLE",unavailable);
        }
    }
}
