package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/** R153: Linux filesystem characterization; not power-loss evidence. */
public final class LocalAccountEpochSnapshotObjectsTest {
    private static int refused;
    @FunctionalInterface interface Operation{void run()throws Exception;}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R153 "+reason);
    }
    private static void deny(String reason,Operation action)throws Exception{
        try{action.run();}
        catch(IOException rejected){refused++;return;}
        throw new AssertionError("R153 wrongly accepted "+reason);
    }
    private static Object key(Path file)throws IOException{
        return Files.readAttributes(file,BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS).fileKey();
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r153-detached-object-");
        try{
            byte[] source="immutable untrusted World bytes".getBytes(
                StandardCharsets.US_ASCII);
            String sha=LocalAccountEpochHeadCodec.digestSnapshot(source);
            Path account=root.resolve("profile.properties");
            LocalAccountEpochSnapshotObjects.Observation first=
                LocalAccountEpochSnapshotObjects.publish(account,source);
            check(first.digest.equals(sha)&&
                first.path.equals(LocalAccountEpochSnapshotObjects
                    .objectPath(account,sha))&&
                key(first.path).equals(first.fileKey)&&
                Arrays.equals(source,Files.readAllBytes(first.path))&&
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "initial forced postimage mismatch");
            LocalAccountEpochSnapshotObjects.verify(
                account,sha,first.fileKey);
            deny("duplicate object",()->
                LocalAccountEpochSnapshotObjects.publish(account,source));
            check(key(first.path).equals(first.fileKey),
                "duplicate changed object");

            Path early=root.resolve("early.properties");
            deny("pre-force exception",()->
                LocalAccountEpochSnapshotObjects.publish(
                    early,source,
                    path->{throw new IOException("R153_INJECT_PRE_FORCE");},
                    null));
            check(Files.isRegularFile(
                LocalAccountEpochSnapshotObjects.objectPath(early,sha),
                LinkOption.NOFOLLOW_LINKS),"pre-force orphan missing");
            deny("reuse pre-force orphan",()->
                LocalAccountEpochSnapshotObjects.publish(early,source));
            Path late=root.resolve("late.properties");
            deny("post-directory-force exception",()->
                LocalAccountEpochSnapshotObjects.publish(
                    late,source,null,
                    path->{throw new IOException("R153_INJECT_POST_DIR_FORCE");}));
            Path lateObject=LocalAccountEpochSnapshotObjects.objectPath(late,sha);
            check(Files.isRegularFile(lateObject,LinkOption.NOFOLLOW_LINKS),
                "postforce orphan missing");
            deny("reuse postforce orphan",()->
                LocalAccountEpochSnapshotObjects.publish(late,source));

            Path alias=root.resolve("foreign-hardlink");
            Files.createLink(alias,first.path);
            deny("hardlink alias",()->
                LocalAccountEpochSnapshotObjects.verify(account,sha,
                    first.fileKey));
            Files.delete(alias);
            LocalAccountEpochSnapshotObjects.verify(account,sha,first.fileKey);

            Path newInode=root.resolve("foreign-object");
            Files.write(newInode,source,StandardOpenOption.CREATE_NEW);
            Files.move(newInode,first.path,StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
            deny("same-byte foreign inode",()->
                LocalAccountEpochSnapshotObjects.verify(
                    account,sha,first.fileKey));
            check(Arrays.equals(source,Files.readAllBytes(first.path)),
                "test replaced wrong bytes");
            Files.write(first.path,"modified".getBytes(
                StandardCharsets.US_ASCII),StandardOpenOption.TRUNCATE_EXISTING);
            deny("altered object hash",()->
                LocalAccountEpochSnapshotObjects.verify(account,sha,null));

            Path symlinkAccount=root.resolve("symlink.properties");
            Files.createSymbolicLink(
                LocalAccountEpochSnapshotObjects.objectPath(
                    symlinkAccount,sha),lateObject);
            deny("leaf symlink",()->
                LocalAccountEpochSnapshotObjects.publish(
                    symlinkAccount,source));
            Path redirected=root.resolve("redirected");
            Files.createSymbolicLink(redirected,root);
            deny("parent symlink",()->
                LocalAccountEpochSnapshotObjects.publish(
                    redirected.resolve("other.properties"),source));
            deny("unsafe account leaf",()->
                LocalAccountEpochSnapshotObjects.objectPath(
                    root.resolve("Bad.properties"),sha));
            deny("malformed digest",()->
                LocalAccountEpochSnapshotObjects.objectPath(account,"../"));
            deny("null snapshot",()->
                LocalAccountEpochSnapshotObjects.publish(account,null));
            deny("oversized snapshot",()->
                LocalAccountEpochSnapshotObjects.publish(
                    account,new byte[
                        LocalAccountEpochHeadCodec.MAX_SNAPSHOT_BYTES+1]));
            check(refused==14,"expected 14 refusals; observed "+refused);
            System.out.println(
                "R153_NONADMITTING_SNAPSHOT_OBJECT_PASS"+
                " negativeCases="+refused+
                " createNewNoOverwrite=true fileForceAndDirForce=true"+
                " boundedNofollowReadback=true observedFileKeyChecked=true"+
                " failedObjectsRetained=true hardlinkAndForeignInodeRefused=true"+
                " noAccountOrHeadPublication=true noRecoveryOrFinalACK=true"+
                " noPowerLossProof=true");
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
