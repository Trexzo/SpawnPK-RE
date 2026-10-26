package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R156: deterministic foreign HEAD inode substitution and aliasing at
 * publication cutpoints. This is source-only evidence, not an OS-lock,
 * power-loss, World-admission or positive epoch authorization test.
 */
public final class LocalAccountEpochNegativeHeadIdentityRaceTest {
    private static final byte[] SNAPSHOT=
        "R156 negative-only foreign replacement witness".getBytes(
            StandardCharsets.US_ASCII);
    private static int cases;
    private LocalAccountEpochNegativeHeadIdentityRaceTest(){}

    private enum Attack { SAME_BYTES_NEW_INODE, FOREIGN_HARDLINK }

    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError("R156 "+what);
    }

    private static void runCase(Path root,boolean existing,
        LocalAccountEpochNegativeHeadPublication.Stage stage,Attack attack)
        throws Exception{
        String name="r156"+cases;
        Path account=root.resolve(name+".properties");
        byte[] previous=("old-R156-"+name).getBytes(
            StandardCharsets.US_ASCII);
        if(existing)Files.write(account,previous);
        LocalAccountEpochHeadCodec.Record marker=
            new LocalAccountEpochHeadCodec.Record(
                name,1,1,11,LocalAccountEpochHeadCodec.State.REVIEW,
                LocalAccountEpochHeadCodec.ZERO_SHA,
                LocalAccountEpochHeadCodec.digestSnapshot(SNAPSHOT));
        byte[] expected=LocalAccountEpochHeadCodec.encode(marker);
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        Path foreign=head.resolveSibling(head.getFileName()+".foreign");
        check(!Files.exists(head,LinkOption.NOFOLLOW_LINKS),
            "HEAD existed before publication");
        final boolean[] attacked={false};
        try{
            LocalAccountEpochNegativeHeadPublication.publish(
                account,marker,SNAPSHOT,at->{
                    if(at!=stage)return;
                    attacked[0]=true;
                    if(attack==Attack.SAME_BYTES_NEW_INODE){
                        // Retain the original inode and its bytes so that
                        // replacement is provably different, not a mutation
                        // of the same open file.
                        Files.move(head,foreign);
                        Files.write(head,expected,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    }else{
                        Files.createLink(foreign,head);
                    }
                });
            throw new AssertionError("R156 publication accepted a foreign "+
                attack+" at "+stage);
        }catch(IOException denied){
            check(attacked[0],"denied before selected injection: "+denied);
        }
        check(Arrays.equals(expected,Files.readAllBytes(head)),
            "negative HEAD bytes or presence changed");
        check(Files.isRegularFile(foreign,LinkOption.NOFOLLOW_LINKS),
            "foreign witness was deleted");
        if(attack==Attack.SAME_BYTES_NEW_INODE){
            check(!Files.isSameFile(head,foreign),
                "HEAD retained original inode after replacement");
            LocalAccountEpochNegativeHeadPublication.Observation view=
                LocalAccountEpochNegativeHeadPublication.inspectNegative(
                    account);
            check(!view.admissionAuthorized&&!view.releaseAuthorized&&
                !view.recoveryAuthorized,
                "separately inspected foreign HEAD gained authority");
        }else{
            check(Files.isSameFile(head,foreign),
                "hardlink alias no longer points to HEAD");
            try{
                LocalAccountEpochNegativeHeadPublication.inspectNegative(
                    account);
                throw new AssertionError("R156 accepted hardlinked HEAD");
            }catch(IOException expectedDenial){
                // nlink != 1 must fail closed, including after force.
            }
        }
        LocalAccountEpochSnapshotObjects.verify(
            account,marker.snapshotSha256,null);
        check(existing?
            Arrays.equals(previous,Files.readAllBytes(account)):
            !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "legacy account bytes or existence changed");
        try{
            LocalAccountEpochNegativeHeadPublication.publish(
                account,marker,SNAPSHOT);
            throw new AssertionError("R156 reused uncertain HEAD");
        }catch(IOException expectedDenial){
            // No retry, rewrite, cleanup or clearance.
        }
        check(Files.isRegularFile(foreign,LinkOption.NOFOLLOW_LINKS)&&
            Arrays.equals(expected,Files.readAllBytes(head)),
            "retry modified/removed foreign negative evidence");
        cases++;
        System.out.println("R156_NEGATIVE_HEAD_IDENTITY_DRIFT_DENIED_PASS"+
            " case="+cases+" stage="+stage+" attack="+attack+
            " existingAccount="+existing+
            " byteIdenticalHead="+
            (attack==Attack.SAME_BYTES_NEW_INODE)+
            " foreignEvidenceRetained=true"+
            " noAdmissionOrFinalAck=true");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r156-head-identity-");
        try{
            for(boolean existing:new boolean[]{false,true}){
                for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                    new LocalAccountEpochNegativeHeadPublication.Stage[]{
                        LocalAccountEpochNegativeHeadPublication.Stage
                            .HEAD_WRITTEN_BEFORE_FILE_FORCE,
                        LocalAccountEpochNegativeHeadPublication.Stage
                            .HEAD_FILE_FORCED_BEFORE_DIRECTORY_FORCE,
                        LocalAccountEpochNegativeHeadPublication.Stage
                            .HEAD_DIRECTORY_FORCED_BEFORE_READBACK}){
                    for(Attack attack:Attack.values())
                        runCase(root,existing,stage,attack);
                }
            }
            check(cases==12,"expected twelve injected scenarios");
            System.out.println("R156_NEGATIVE_HEAD_IDENTITY_MATRIX_PASS"+
                " cases=12 sameBytesInodeSwap=6 hardlinkAlias=6"+
                " noPositivePublication=true noWorldLogin=true"+
                " noPowerLossProof=true");
        }finally{
            try(Stream<Path> entries=Files.walk(root)){
                for(Path item:entries.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}
