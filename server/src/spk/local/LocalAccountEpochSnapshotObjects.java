package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;

/**
 * R153: independent, non-admitting, immutable snapshot-object experiment.
 * No authoritative HEAD, account writes, sessions, epochs or final ACK.
 * Force/readback is not evidence against arbitrary foreign writers.
 */
final class LocalAccountEpochSnapshotObjects {
    static final String PREFIX=".g21377-object-";
    @FunctionalInterface interface Probe {
        void reached(Path object)throws IOException;
    }
    static final class Observation {
        final Path path;
        final String digest;
        final Object fileKey;
        Observation(Path path,String digest,Object fileKey){
            this.path=path;
            this.digest=digest;
            this.fileKey=fileKey;
        }
    }
    private LocalAccountEpochSnapshotObjects(){}

    static Path objectPath(Path account,String digest)throws IOException{
        if(account==null||digest==null||
           !digest.matches("[0-9a-f]{64}"))
            throw new IOException("R153_OBJECT_ARGUMENT_INVALID");
        Path selected=account.toAbsolutePath().normalize();
        if(selected.getFileName()==null||selected.getParent()==null)
            throw new IOException("R153_ACCOUNT_PATH_INVALID");
        String leaf=selected.getFileName().toString();
        if(!leaf.matches("[a-z0-9_.-]{1,120}")||
           leaf.startsWith(".")||leaf.contains(".."))
            throw new IOException("R153_ACCOUNT_LEAF_UNSAFE");
        return selected.resolveSibling(leaf+PREFIX+digest+".bin");
    }
    private static void safeParents(Path dir)throws IOException{
        for(Path current=dir;current!=null;current=current.getParent()){
            BasicFileAttributes attrs=Files.readAttributes(current,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attrs.isDirectory()||attrs.isSymbolicLink())
                throw new IOException("R153_PARENT_SYMLINK_OR_NONDIR");
        }
    }
    private static void forceDirectory(Path parent)throws IOException{
        try(FileChannel dir=FileChannel.open(parent,
                StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            dir.force(true);
        }
    }
    static Observation publish(Path account,byte[] bytes)throws IOException{
        return publish(account,bytes,null,null);
    }
    /** Faults leave orphan objects; NEVER silently clean or reuse them. */
    static Observation publish(Path account,byte[] raw,
                               Probe beforeForce,Probe afterDirForce)
        throws IOException{
        if(raw==null)throw new IOException("R153_NULL_SNAPSHOT");
        byte[] bytes=raw.clone();
        String digest=LocalAccountEpochHeadCodec.digestSnapshot(bytes);
        Path object=objectPath(account,digest);
        safeParents(object.getParent());
        try(FileChannel out=FileChannel.open(object,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer data=ByteBuffer.wrap(bytes);
            while(data.hasRemaining())out.write(data);
            if(beforeForce!=null)beforeForce.reached(object);
            out.force(true);
        }
        forceDirectory(object.getParent());
        if(afterDirForce!=null)afterDirForce.reached(object);
        Observation checked=verify(account,digest,null);
        if(!Arrays.equals(bytes,readBounded(object)))
            throw new IOException("R153_OBJECT_POSTIMAGE_CHANGED");
        return checked;
    }
    private static BasicFileAttributes attrs(Path object)throws IOException{
        BasicFileAttributes a=Files.readAttributes(object,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!a.isRegularFile()||a.isSymbolicLink()||a.fileKey()==null||
           a.size()<1||a.size()>LocalAccountEpochHeadCodec.MAX_SNAPSHOT_BYTES)
            throw new IOException("R153_OBJECT_TYPE_OR_SIZE_UNSAFE");
        return a;
    }
    private static void oneLink(Path path)throws IOException{
        Object count;
        try{
            count=Files.getAttribute(path,"unix:nlink",
                LinkOption.NOFOLLOW_LINKS);
        }catch(UnsupportedOperationException|IllegalArgumentException e){
            throw new IOException("R153_LINK_PROVIDER_UNPROVEN",e);
        }
        if(!(count instanceof Number)||((Number)count).longValue()!=1)
            throw new IOException("R153_OBJECT_HARDLINK_UNSAFE");
    }
    private static byte[] readBounded(Path file)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream stream=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            byte[] chunk=new byte[8192];
            int n;
            while((n=stream.read(chunk))!=-1){
                if(n>LocalAccountEpochHeadCodec.MAX_SNAPSHOT_BYTES-
                        bytes.size())
                    throw new IOException("R153_OBJECT_READ_OVERSIZE");
                bytes.write(chunk,0,n);
            }
        }
        if(bytes.size()==0)throw new IOException("R153_OBJECT_EMPTY");
        return bytes.toByteArray();
    }
    /**
     * A valid digest and observed fileKey never establish durable owner,
     * epoch ancestry, trusted HEAD publication or permission to recover.
     */
    static Observation verify(Path account,String digest,Object expectedKey)
        throws IOException{
        Path path=objectPath(account,digest);
        safeParents(path.getParent());
        BasicFileAttributes first=attrs(path);
        oneLink(path);
        byte[] bytes=readBounded(path);
        BasicFileAttributes last=attrs(path);
        oneLink(path);
        if(!Objects.equals(first.fileKey(),last.fileKey())||
           first.size()!=last.size()||
           !Objects.equals(first.lastModifiedTime(),
                           last.lastModifiedTime())||
           (expectedKey!=null&&!expectedKey.equals(last.fileKey())))
            throw new IOException("R153_OBJECT_IDENTITY_DRIFT");
        if(!digest.equals(LocalAccountEpochHeadCodec.digestSnapshot(bytes)))
            throw new IOException("R153_OBJECT_DIGEST_MISMATCH");
        return new Observation(path,digest,last.fileKey());
    }
}
