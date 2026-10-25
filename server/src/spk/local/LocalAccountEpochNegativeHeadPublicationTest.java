package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** R154 standalone negative-only HEAD publication, no owner authority. */
public final class LocalAccountEpochNegativeHeadPublicationTest {
    private static int denied;
    @FunctionalInterface interface Op{void run()throws Exception;}
    private static void check(boolean pass,String message){
        if(!pass)throw new AssertionError("R154 "+message);
    }
    private static void refuse(String caseName,Op op)throws Exception{
        try{op.run();}
        catch(IOException expected){denied++;return;}
        throw new AssertionError("R154 accepted "+caseName);
    }
    private static LocalAccountEpochHeadCodec.Record record(
        String name,byte[] content,LocalAccountEpochHeadCodec.State state)
        throws IOException{
        return new LocalAccountEpochHeadCodec.Record(name,1,1,2,state,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(content));
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"no CLI args");
        Path root=Files.createTempDirectory("r154-negative-head-");
        byte[] data="immutable negative-only world snapshot".getBytes(
            StandardCharsets.US_ASCII);
        try{
            Path account=root.resolve("alice.properties");
            LocalAccountEpochHeadCodec.Record active=record(
                "alice",data,LocalAccountEpochHeadCodec.State.ACTIVE);
            LocalAccountEpochNegativeHeadPublication.Observation created=
                LocalAccountEpochNegativeHeadPublication.publish(
                    account,active,data);
            check(created.untrusted.state==
                    LocalAccountEpochHeadCodec.State.ACTIVE&&
                !created.admissionAuthorized&&!created.releaseAuthorized&&
                !created.recoveryAuthorized&&
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "negative head incorrectly granted account authority");
            check(LocalAccountEpochNegativeHeadPublication
                .inspectNegative(account).fileKey.equals(created.fileKey),
                "negative HEAD readback identity");
            refuse("duplicate active HEAD",()->
                LocalAccountEpochNegativeHeadPublication.publish(
                    account,active,data));
            Path committed=root.resolve("committed.properties");
            refuse("positive COMMITTED",()->
                LocalAccountEpochNegativeHeadPublication.publish(
                    committed,record("committed",data,
                        LocalAccountEpochHeadCodec.State.COMMITTED),data));
            refuse("positive FINAL_COMMITTED",()->
                LocalAccountEpochNegativeHeadPublication.publish(
                    committed,record("committed",data,
                        LocalAccountEpochHeadCodec.State.FINAL_COMMITTED),data));
            check(!Files.exists(
                LocalAccountEpochNegativeHeadPublication.headPath(committed),
                LinkOption.NOFOLLOW_LINKS),
                "positive head published");
            refuse("mismatched content before IO",()->
                LocalAccountEpochNegativeHeadPublication.publish(
                    committed,record("committed",data,
                        LocalAccountEpochHeadCodec.State.ACTIVE),
                    "different bytes".getBytes(StandardCharsets.US_ASCII)));
            refuse("wrong account name",()->
                LocalAccountEpochNegativeHeadPublication.publish(
                    committed,active,data));

            for(LocalAccountEpochNegativeHeadPublication.Stage stage:
                    LocalAccountEpochNegativeHeadPublication.Stage.values()){
                String username="fault"+stage.ordinal();
                Path current=root.resolve(username+".properties");
                LocalAccountEpochHeadCodec.Record marker=record(username,data,
                    LocalAccountEpochHeadCodec.State.REVIEW);
                refuse("cutpoint "+stage,()->
                    LocalAccountEpochNegativeHeadPublication.publish(
                        current,marker,data,at->{
                            if(at==stage)
                                throw new IOException("R154_TEST_CUTPOINT_"+stage);
                        }));
                Path object=LocalAccountEpochSnapshotObjects.objectPath(
                    current,marker.snapshotSha256);
                check(Files.isRegularFile(object,LinkOption.NOFOLLOW_LINKS),
                    "uncertain immutable snapshot was deleted");
                Path head=LocalAccountEpochNegativeHeadPublication
                    .headPath(current);
                check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==
                    (stage!=LocalAccountEpochNegativeHeadPublication.Stage
                        .OBJECT_FORCED_BEFORE_HEAD),
                    "unexpected negative HEAD cutpoint artifact");
                refuse("retry uncertain cutpoint "+stage,()->
                    LocalAccountEpochNegativeHeadPublication.publish(
                        current,marker,data));
                check(Files.exists(object,LinkOption.NOFOLLOW_LINKS),
                    "retry cleaned uncertain object");
            }
            Path tampered=root.resolve("tampered.properties");
            LocalAccountEpochNegativeHeadPublication.publish(
                tampered,record("tampered",data,
                    LocalAccountEpochHeadCodec.State.REVIEW),data);
            Path corrupt=LocalAccountEpochNegativeHeadPublication
                .headPath(tampered);
            Files.write(corrupt,"tampered".getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.TRUNCATE_EXISTING);
            refuse("malformed HEAD",()->
                LocalAccountEpochNegativeHeadPublication
                    .inspectNegative(tampered));

            Path newAccount=root.resolve("new.properties");
            LocalAccountEpochNegativeHeadPublication.publish(
                newAccount,record("new",data,
                    LocalAccountEpochHeadCodec.State.ACTIVE),data);
            Path newObject=LocalAccountEpochSnapshotObjects.objectPath(
                newAccount,LocalAccountEpochHeadCodec.digestSnapshot(data));
            Files.write(newObject,"tampered".getBytes(
                StandardCharsets.US_ASCII),StandardOpenOption.TRUNCATE_EXISTING);
            refuse("tampered linked immutable snapshot",()->
                LocalAccountEpochNegativeHeadPublication
                    .inspectNegative(newAccount));
            check(denied==15,"negative case count "+denied);
            System.out.println(
                "R154_NONADMITTING_NEGATIVE_HEAD_PUBLICATION_PASS"+
                " refusedCases="+denied+
                " states=ACTIVE_REVIEW_ONLY"+
                " immutableObjectBeforeHead=true"+
                " fileAndDirectoryForce=true"+
                " fourFailureArtifactsPreserved=true"+
                " noPositiveHeadOrAccountWrite=true"+
                " noAdmissionReleaseRecoveryOrFinalACK=true"+
                " noProcessKillOrPowerLossProof=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path item:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
    }
}
