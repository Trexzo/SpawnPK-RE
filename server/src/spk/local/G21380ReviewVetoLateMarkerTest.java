package spk.local;

import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.380 late negative review-intent publication after first scan.
 * Characterizes a bounded opt-in recheck, NOT an atomic durable epoch.
 */
public final class G21380ReviewVetoLateMarkerTest {
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.380 "+why);
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g21380-late-marker-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] prior=new String[keys.length];
        World world=null;
        LocalJvmAccountSessionLease.Token token=null;
        try{
            for(int i=0;i<keys.length;i++){
                prior[i]=System.getProperty(keys[i]);
                System.setProperty(keys[i],"true");
            }
            FilePlayerRepository repo=new FilePlayerRepository(
                user->dir.resolve(user+".properties"));
            world=World.isolatedForTest(60000L,repo);
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),"late-review");
            check(token!=null,"missing cooperating lease");
            final Path account=dir.resolve("late-review.properties");
            final Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            final boolean[] injected={false};
            LocalReviewIntentAdmissionVeto.setAfterNegativeChecksForTest(
                selected->{
                    check(selected.equals(account.toAbsolutePath().normalize()),
                        "selected account mismatch");
                    try{
                        // Hostile/untrusted marker bytes are NEGATIVE by existence.
                        Files.write(marker,new byte[]{1,2,3,4},
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                        injected[0]=true;
                    }catch(java.io.IOException err){
                        throw new IllegalStateException(err);
                    }
                });
            boolean rejected=false;
            try{
                LocalReviewIntentAdmissionVeto.requireAdmissible(
                    true,true,token);
            }catch(IllegalStateException fail){
                rejected=String.valueOf(fail.getMessage()).contains(
                    "G21.189 REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
            }
            check(injected[0]&&rejected,
                "late negative marker was not rejected");
            check(Files.size(marker)==4L&&world.players().size()==0&&
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "negative denial changed account/member/marker");
            System.out.println("G21380_REVIEW_VETO_LATE_MARKER_PASS"+
                " lateIntentDenied=true markerPreserved=true"+
                " noWorldAdmission=true durableEpoch=false");
        }finally{
            LocalReviewIntentAdmissionVeto.setAfterNegativeChecksForTest(null);
            if(token!=null)token.releaseAfterInitializationFailure();
            if(world!=null)world.close();
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(dir)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    private G21380ReviewVetoLateMarkerTest(){}
}
