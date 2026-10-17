package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.298 real socket World admission, strict forced final World
 * publication, and default-off negative witness. No positive login.
 */
public final class G21298ActualFinalNegativeReviewWitnessTest {
    private G21298ActualFinalNegativeReviewWitnessTest(){}
    private static void check(boolean b,String why){
        if(!b)throw new AssertionError("G21.298 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21298-negative-final-");
        String[] flags={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalReviewIntentAdmissionVeto.PROPERTY,
            LocalOneShotActiveReviewOnAdmission.PROPERTY,
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY,
            LocalFinalSaveNegativeWitness.PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<flags.length;i++)System.setProperty(flags[i],"true");
        World world=null;
        final String user="g21298-real-final";
        try{
            Path account=LocalAccountProfiles.accountFile(user);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(account);
            check(!Files.exists(witness,LinkOption.NOFOLLOW_LINKS),
                "negative witness preexisting");
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(50L,repository);
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(java.net.Socket socket=server.login(user)){
                    long until=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(8);
                    while(world.players().byName(user)==null&&
                          System.nanoTime()<until)
                        Thread.sleep(10L);
                    check(world.players().byName(user)!=null,
                        "real World login not admitted");
                }
                server.waitCompleted();
            }
            check(world.persistence().failedCount()==0L,
                "forced world final save/witness reported failure");
            check(repository.forcedWorldPostimageCountForTest()>0,
                "strict forced account postimage not exercised");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
                "real final World save omitted witness");
            check(Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS),
                "ACTIVE review intent illegally retired");
            LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                account);
            String content=Files.readString(witness,
                StandardCharsets.US_ASCII);
            LocalAccountForensicEvidence.Evidence accountEvidence=
                LocalAccountForensicEvidence.inspect(account);
            check(content.startsWith("G21298_NEGATIVE_FINAL_REVIEW_V1\n")&&
                content.contains("account="+user+"\n")&&
                content.contains("accountSha256="+
                    accountEvidence.accountSha256+"\n")&&
                content.contains("activeMarkerWitness="+
                    accountEvidence.negativeSidecarContentWitnesses
                        .get("activeReview")+"\n")&&
                content.contains("recoveryAuthorized=false")&&
                content.contains("ownerEpochProven=false")&&
                content.contains("finalSaveAck=false"),
                "untrusted witness fields missing or inconsistent");
            boolean refused=false;
            try{
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
            }catch(java.io.IOException expected){
                refused=true;
            }
            check(refused,
                "negative final witness falsely authorized next login");
            System.out.println(
                "G21298_REAL_FINAL_NEGATIVE_REVIEW_WITNESS_PASS"+
                " realTcpAdmissionAndLogout=true"+
                " forcedWorldPostimage=true"+
                " finalWorkerWitnessForced=true"+
                " ACTIVEIntentRetained=true"+
                " rereadAccountAndMarkerFingerprints=true"+
                " automaticRecovery=false"+
                " finalSaveAck=false");
        }finally{
            if(world!=null)world.close();
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
