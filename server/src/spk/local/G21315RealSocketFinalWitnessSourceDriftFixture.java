package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.315/316 actual TCP final-World-save identity drift fixture. */
final class G21315RealSocketFinalWitnessSourceDriftFixture {
    private G21315RealSocketFinalWitnessSourceDriftFixture(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.315/316 "+why);
    }
    static void run(boolean markerVariation)throws Exception{
        final String code=markerVariation?"g21316":"g21315";
        Path root=Files.createTempDirectory(code+"-tcp-final-drift-");
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
        String[] old=new String[flags.length];
        for(int i=0;i<flags.length;i++)old[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<flags.length;i++)System.setProperty(flags[i],"true");
        final String username=code+"-real-final";
        final Path selected=LocalAccountProfiles.accountFile(username);
        final Path active=LocalDurableAccountReviewIntentPrimitive
            .markerPath(selected);
        final Path witness=LocalFinalSaveNegativeWitness
            .witnessPath(selected);
        final Path oldSource=root.resolve(
            markerVariation?"displaced-ACTIVE":"displaced-account");
        AtomicBoolean mutated=new AtomicBoolean();
        World world=null;
        try{
            LocalFinalSaveNegativeWitness.setAfterDirectoryForceForTest(
                leaf->{
                    check(leaf.equals(witness),"wrong selected witness leaf");
                    Path victim=markerVariation?active:selected;
                    BasicFileAttributes before=Files.readAttributes(
                        victim,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    byte[] bytes=Files.readAllBytes(victim);
                    // The original file is preserved separately; a
                    // distinct inode with identical bytes is created
                    // at the exact original selected pathname.
                    Files.move(victim,oldSource);
                    Files.write(victim,bytes);
                    BasicFileAttributes after=Files.readAttributes(
                        victim,BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                    check(!before.fileKey().equals(after.fileKey())&&
                        before.size()==after.size()&&
                        Arrays.equals(bytes,Files.readAllBytes(victim)),
                        "genuine same-content inode replacement absent");
                    mutated.set(true);
                });
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(50L,repository);
            final World live=world;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(java.net.Socket socket=server.login(username)){
                    long until=System.nanoTime()+
                        TimeUnit.SECONDS.toNanos(8);
                    while(live.players().byName(username)==null&&
                          System.nanoTime()<until)
                        Thread.sleep(10L);
                    check(live.players().byName(username)!=null,
                        "real TCP World login not admitted before final failure");
                }
                server.waitCompleted();
            }
            check(mutated.get()&&world.players().size()==0,
                "final World save fault injection or logout not reached");
            check(world.persistence().failedCount()>0L,
                "postforce selected identity drift incorrectly confirmed final save");
            check(repository.forcedWorldPostimageCountForTest()>0L,
                "real forced account postimage path was not reached");
            check(Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(oldSource,LinkOption.NOFOLLOW_LINKS),
                "uncertain forced source evidence was removed");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(selected);
            boolean refused=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(
                    selected);
            }catch(IOException expected){refused=true;}
            check(refused,"failed source identity check authorized restart");
            Path now=markerVariation?active:selected;
            check(Arrays.equals(Files.readAllBytes(oldSource),
                    Files.readAllBytes(now)),
                "identical source bytes not preserved");
            System.out.println((markerVariation?
                "G21316_REAL_TCP_FINAL_ACTIVE_INODE_DRIFT_FAIL_CLOSED_PASS":
                "G21315_REAL_TCP_FINAL_ACCOUNT_INODE_DRIFT_FAIL_CLOSED_PASS")+
                " realTcpSessionAndLogout=true"+
                " forcedWorldPostimageReached=true"+
                " negativeWitnessSourceSwapObserved=true"+
                " persistenceWorkerMarkedFailure=true"+
                " noAutoCleanupOrRestart=true"+
                " authenticatedFinalAck=false");
        }finally{
            LocalFinalSaveNegativeWitness
                .setAfterDirectoryForceForTest(null);
            if(world!=null)world.close();
            for(int i=0;i<flags.length;i++){
                if(old[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
