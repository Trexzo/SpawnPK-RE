package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;

/**
 * R154 NON-ADMITTING, CREATE_NEW-only negative HEAD prototype.
 * It can publish only ACTIVE or REVIEW for a disposable first epoch;
 * no COMMITTED/FINAL_COMMITTED, reissue, overwrite, unlink, clearance,
 * admission, session lease release or World final-save ACK is possible.
 * This is still not a trusted owner or proven crash-durable protocol.
 */
final class LocalAccountEpochNegativeHeadPublication {
    static final String SUFFIX=".g21377-negative-head";
    enum Stage {
        OBJECT_FORCED_BEFORE_HEAD,
        HEAD_WRITTEN_BEFORE_FILE_FORCE,
        HEAD_FILE_FORCED_BEFORE_DIRECTORY_FORCE,
        HEAD_DIRECTORY_FORCED_BEFORE_READBACK
    }
    @FunctionalInterface interface Probe {
        void at(Stage stage)throws IOException;
    }
    static final class Observation {
        final Path path;
        final LocalAccountEpochHeadCodec.Record untrusted;
        final Object fileKey;
        final boolean admissionAuthorized=false;
        final boolean releaseAuthorized=false;
        final boolean recoveryAuthorized=false;
        private Observation(Path path,LocalAccountEpochHeadCodec.Record parsed,
                            Object fileKey){
            this.path=path;
            this.untrusted=parsed;
            this.fileKey=fileKey;
        }
    }
    private LocalAccountEpochNegativeHeadPublication(){}

    static Path headPath(Path account)throws IOException{
        String digest=LocalAccountEpochHeadCodec.ZERO_SHA;
        Path object=LocalAccountEpochSnapshotObjects.objectPath(account,digest);
        Path selected=account.toAbsolutePath().normalize();
        return object.resolveSibling(selected.getFileName().toString()+SUFFIX);
    }

    private static void requireAbsent(Path leaf)throws IOException{
        try{
            Files.readAttributes(leaf,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException missing){
            return;
        }
        throw new IOException("R154_HEAD_ALREADY_PRESENT_NO_REISSUE");
    }
    private static void oneLink(Path leaf)throws IOException{
        Object count;
        try{
            count=Files.getAttribute(leaf,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|IllegalArgumentException bad){
            throw new IOException("R154_HEAD_LINK_COUNT_UNSUPPORTED",bad);
        }
        if(!(count instanceof Number)||((Number)count).longValue()!=1L)
            throw new IOException("R154_HEAD_HARDLINK_UNSAFE");
    }
    private static void safeAncestors(Path parent)throws IOException{
        for(Path p=parent;p!=null;p=p.getParent()){
            BasicFileAttributes attrs=Files.readAttributes(p,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory()||attrs.isSymbolicLink())
                throw new IOException("R154_UNSAFE_HEAD_DIRECTORY");
        }
    }
    private static void forceDirectory(Path parent)throws IOException{
        try(FileChannel dir=FileChannel.open(parent,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            dir.force(true);
        }
    }
    private static byte[] readBounded(Path leaf)throws IOException{
        ByteBuffer data=ByteBuffer.allocate(
            LocalAccountEpochHeadCodec.MAX_HEAD_BYTES+1);
        try(FileChannel in=FileChannel.open(leaf,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            while(data.hasRemaining()){
                int n=in.read(data);
                if(n<0)break;
                if(n==0)throw new IOException("R154_HEAD_READ_STALLED");
            }
        }
        if(data.position()==0||
           data.position()>LocalAccountEpochHeadCodec.MAX_HEAD_BYTES)
            throw new IOException("R154_HEAD_READ_SIZE_UNSAFE");
        return Arrays.copyOf(data.array(),data.position());
    }
    private static BasicFileAttributes regular(Path leaf)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(leaf,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null||attrs.size()<1||
           attrs.size()>LocalAccountEpochHeadCodec.MAX_HEAD_BYTES)
            throw new IOException("R154_HEAD_IDENTITY_UNSAFE");
        return attrs;
    }
    /** Purely negative forensic observation. Even a valid record denies. */
    static Observation inspectNegative(Path account)throws IOException{
        Path leaf=headPath(account);
        safeAncestors(leaf.getParent());
        BasicFileAttributes before=regular(leaf);
        oneLink(leaf);
        byte[] bytes=readBounded(leaf);
        BasicFileAttributes after=regular(leaf);
        oneLink(leaf);
        if(!Objects.equals(before.fileKey(),after.fileKey())||
           before.size()!=after.size()||
           !Objects.equals(before.lastModifiedTime(),
                           after.lastModifiedTime()))
            throw new IOException("R154_HEAD_CHANGED_DURING_READ");
        LocalAccountEpochHeadCodec.Record parsed=
            LocalAccountEpochHeadCodec.decode(bytes);
        if(parsed.state!=LocalAccountEpochHeadCodec.State.ACTIVE &&
           parsed.state!=LocalAccountEpochHeadCodec.State.REVIEW)
            throw new IOException("R154_FORGED_POSITIVE_HEAD_REFUSED");
        if(parsed.epoch!=1L||parsed.sequence!=1L||
           !LocalAccountEpochHeadCodec.ZERO_SHA.equals(
               parsed.previousHeadSha256))
            throw new IOException("R154_UNTRUSTED_HEAD_EPOCH_SEQUENCE");
        if(!parsed.account.equals(
            account.toAbsolutePath().normalize()
                .getFileName().toString().replace(".properties","")))
            throw new IOException("R154_HEAD_ACCOUNT_MISMATCH");
        LocalAccountEpochSnapshotObjects.verify(
            account,parsed.snapshotSha256,null);
        return new Observation(leaf,parsed,after.fileKey());
    }

    static Observation publish(Path account,
        LocalAccountEpochHeadCodec.Record record,byte[] snapshot)
        throws IOException{
        return publish(account,record,snapshot,null);
    }
    static Observation publish(Path account,
        LocalAccountEpochHeadCodec.Record record,byte[] snapshot,Probe probe)
        throws IOException{
        if(record==null || snapshot==null ||
           (record.state!=LocalAccountEpochHeadCodec.State.ACTIVE &&
            record.state!=LocalAccountEpochHeadCodec.State.REVIEW)||
           record.epoch!=1L||record.sequence!=1L||
           !LocalAccountEpochHeadCodec.ZERO_SHA.equals(
               record.previousHeadSha256))
            throw new IOException("R154_ONLY_FIRST_NEGATIVE_HEAD_ALLOWED");
        Path head=headPath(account);
        safeAncestors(head.getParent());
        requireAbsent(head);
        byte[] sealed=LocalAccountEpochHeadCodec.encode(record);
        String observed=LocalAccountEpochHeadCodec.digestSnapshot(snapshot);
        if(!observed.equals(record.snapshotSha256))
            throw new IOException("R154_SNAPSHOT_DIGEST_MISMATCH_BEFORE_IO");
        String leaf=account.toAbsolutePath().normalize()
            .getFileName().toString();
        if(!leaf.endsWith(".properties")||
           !record.account.equals(
               leaf.substring(0,leaf.length()-".properties".length())))
            throw new IOException("R154_ACCOUNT_NAME_MISMATCH_BEFORE_IO");
        LocalAccountEpochSnapshotObjects.Observation object=
            LocalAccountEpochSnapshotObjects.publish(account,snapshot);
        if(probe!=null)probe.at(Stage.OBJECT_FORCED_BEFORE_HEAD);
        try(FileChannel writer=FileChannel.open(head,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer body=ByteBuffer.wrap(sealed);
            while(body.hasRemaining())writer.write(body);
            if(probe!=null)probe.at(Stage.HEAD_WRITTEN_BEFORE_FILE_FORCE);
            writer.force(true);
        }
        if(probe!=null)
            probe.at(Stage.HEAD_FILE_FORCED_BEFORE_DIRECTORY_FORCE);
        forceDirectory(head.getParent());
        if(probe!=null)
            probe.at(Stage.HEAD_DIRECTORY_FORCED_BEFORE_READBACK);
        Observation result=inspectNegative(account);
        if(!Arrays.equals(sealed,readBounded(head))||
           !object.fileKey.equals(
               LocalAccountEpochSnapshotObjects.verify(
                   account,object.digest,object.fileKey).fileKey))
            throw new IOException("R154_HEAD_POSTPUBLICATION_DRIFT");
        return result;
    }
}
