package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R54: test actual default-off RAW writer paths with a HEAD created
 * during serialization (deny BEFORE account move), or after the
 * account move (UNKNOWN instead of a normal save/strict Receipt).
 *
 * The R50 HEAD is a retained negative even if malformed. No real
 * account migration, in-game action or crash recovery is performed.
 */
public final class G21382LateNegativeHeadRawPublicationRegression {
    private G21382LateNegativeHeadRawPublicationRegression(){}
    @FunctionalInterface interface Save {void write()throws IOException;}
    private static void check(boolean okay,String reason){
        if(!okay)throw new AssertionError("R54_HEAD_RACE "+reason);
    }
    private static void createMalformedHead(Path file)throws IOException{
        Path head=LocalNegativeAccountHeadPrototype.headPath(file);
        Files.write(head,
            "R54_PERMANENT_NEGATIVE_DURING_RAW_PUBLICATION".getBytes(
                StandardCharsets.US_ASCII),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
    }
    private static boolean rejects(Save operation,String code)
        throws IOException{
        try{
            operation.write();
            return false;
        }catch(IOException expected){
            for(Throwable cause=expected;cause!=null;cause=cause.getCause())
                if(String.valueOf(cause.getMessage()).contains(code))
                    return true;
            return false;
        }
    }
    private static void noLegacyLeaks(Path file)throws IOException{
        check(!Files.exists(file.resolveSibling(
                file.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            "legacy temp stranded "+file);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "reservation leaked");
    }
    private static void caseFor(
        String label,Path root,SaveFactory factory,
        java.util.function.Function<String,Path> accountPath
    )throws Exception{
        // Distinct path for each timing, because an R50/R54 negative
        // record is permanent and never retired, even in a test.
        for(boolean postmove:new boolean[]{false,true}){
            String name="r54-"+label+(postmove?"-post":"-pre");
            Path file=accountPath.apply(name);
            Save save=factory.create(name);
            save.write();
            byte[] baseline=Files.readAllBytes(file);
            check(baseline.length>0,"baseline empty "+name);
            AtomicBoolean callbackInvoked=new AtomicBoolean();
            LocalNegativeAccountHeadPrototype.RawPublicationProbe p=actual->{
                check(file.equals(actual),"wrong RAW account in callback");
                createMalformedHead(actual);
                callbackInvoked.set(true);
            };
            try{
                if(postmove)
                    LocalNegativeAccountHeadPrototype
                        .setAfterRawMoveForTest(p);
                else LocalNegativeAccountHeadPrototype
                        .setBeforeRawMoveForTest(p);
                final String failure=postmove
                    ?"R54_NEGATIVE_HEAD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW"
                    :"R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY";
                check(rejects(save,failure),
                    name+" returned success despite negative HEAD");
                check(callbackInvoked.get(),name+" did not exercise seam");
            }finally{
                LocalNegativeAccountHeadPrototype
                    .setAfterRawMoveForTest(null);
                LocalNegativeAccountHeadPrototype
                    .setBeforeRawMoveForTest(null);
            }
            if(!postmove)
                check(Arrays.equals(baseline,Files.readAllBytes(file)),
                    name+" rejected pre-move but changed original bytes");
            else
                check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
                    name+" postmove lost real account postimage");
            noLegacyLeaks(file);
            Path head=LocalNegativeAccountHeadPrototype.headPath(file);
            check(Files.isRegularFile(head,LinkOption.NOFOLLOW_LINKS),
                name+" lost HEAD after uncertain publication");
            byte[] headBytes=Files.readAllBytes(head);
            check(rejects(save,
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY"),
                name+" allowed retry through retained negative HEAD");
            check(Arrays.equals(headBytes,Files.readAllBytes(head)),
                name+" retry changed permanent negative HEAD");
            noLegacyLeaks(file);
        }
    }
    @FunctionalInterface interface SaveFactory{
        Save create(String name)throws IOException;
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r54-late-head-");
        String[] props={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            "spk.local.accountFile"
        };
        String[] previous=new String[props.length];
        for(int i=0;i<props.length;i++){
            previous[i]=System.getProperty(props[i]);
            System.clearProperty(props[i]);
        }
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                username->root.resolve(username+".properties"));
            caseFor("repository",root,name->()->
                repository.save(PlayerSnapshotCodec.capture(
                    name,new WorldPlayer())),
                name->root.resolve(name+".properties"));
            WorldPlayer player=new WorldPlayer();
            System.setProperty("spk.local.accountFile",
                root.resolve("opensrc.properties").toString());
            caseFor("named",root,name->()->
                LocalAccountProfiles.save(name,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()),
                LocalAccountProfiles::accountFile);
            caseFor("primary",root,name->{
                // AccountStore is a single canonical username/file:
                // remap its file path per distinct timing fixture.
                System.setProperty("spk.local.accountFile",
                    root.resolve(name+".properties").toString());
                return ()->{
                    System.setProperty("spk.local.accountFile",
                        root.resolve(name+".properties").toString());
                    AccountStore.save(
                        player.bank(),player.equipment(),player.movement(),
                        player.petState(),player.playerState());
                };
            },name->root.resolve(name+".properties"));
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(
                    username->root.resolve(username+".properties"));
            caseFor("strict",root,name->()->
                strict.saveStrict(PlayerSnapshotCodec.capture(
                    name,new WorldPlayer())),
                name->root.resolve(name+".properties"));
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "leaked World token");
            System.out.println("R54_LATE_NEGATIVE_HEAD_RAW_PUBLICATION_PASS"+
                " writerPaths=repository,named,primary,strict"+
                " premovePreservedPreimage=4"+
                " postmoveNormalReceiptsDenied=4"+
                " permanentNegativeHeadRetained=8"+
                " independentHealthyBaselinePerCase=true"+
                " defaultOffLegacyRaw=true"+
                " automatedRecovery=false"+
                " concurrentCrossJvmCAS=false"+
                " runtimeV308Acceptance=false");
        }finally{
            LocalNegativeAccountHeadPrototype.setBeforeRawMoveForTest(null);
            LocalNegativeAccountHeadPrototype.setAfterRawMoveForTest(null);
            for(int i=0;i<props.length;i++){
                if(previous[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],previous[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
