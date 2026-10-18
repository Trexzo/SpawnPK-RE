package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.379: a preexisting uncertain review sidecar remains negative
 * after World recreation, including malformed sidecars.
 * Read-only admission: NO marker deletion, replay, grant or repair.
 */
public final class G21379ReviewMarkerRestartAdmissionTest {
    private static void check(boolean result,String why) {
        if(!result)throw new AssertionError("G21.379 "+why);
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g21379-restart-veto-");
        String[] names={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] original=new String[names.length];
        for(int i=0;i<names.length;i++){
            original[i]=System.getProperty(names[i]);
            System.setProperty(names[i],"true");
        }
        World first=null, restarted=null;
        LocalJvmAccountSessionLease.Token firstToken=null, secondToken=null;
        try{
            Path account=root.resolve("same-player.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive.markerPath(account);
            // Deliberately malformed: content must never grant admission.
            byte[] corrupt=new byte[]{0,1,2,3,4,5};
            Files.write(marker,corrupt);
            FilePlayerRepository repository=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            first=World.isolatedForTest(60000L,repository);
            firstToken=LocalJvmAccountSessionLease.acquire(
                first.persistence(),"same-player");
            check(firstToken!=null,"initial session token absent");
            boolean deniedFirst=false;
            try{
                LocalReviewIntentAdmissionVeto.requireAdmissible(
                    true,true,firstToken);
            }catch(IllegalStateException expected){
                deniedFirst=String.valueOf(expected.getMessage()).contains(
                    "G21.189 REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
            }
            check(deniedFirst,"malformed review marker was admitted");
            firstToken.releaseAfterInitializationFailure();
            firstToken=null;
            first.close();
            first=null;
            restarted=World.isolatedForTest(60000L,repository);
            secondToken=LocalJvmAccountSessionLease.acquire(
                restarted.persistence(),"same-player");
            check(secondToken!=null,"restart token absent");
            boolean deniedRestart=false;
            try{
                LocalReviewIntentAdmissionVeto.requireAdmissible(
                    true,true,secondToken);
            }catch(IllegalStateException expected){
                deniedRestart=String.valueOf(expected.getMessage()).contains(
                    "G21.189 REVIEW_INTENT_LOGIN_DENIED_NO_AUTO_RECOVERY");
            }
            check(deniedRestart,"new World ignored preexisting negative marker");
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS)&&
                  java.util.Arrays.equals(corrupt,Files.readAllBytes(marker)),
                  "admission rewrote or cleared negative marker");
            check(first==null&&restarted.players().size()==0&&
                  !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                  "negative admission published player/account");
            System.out.println("G21379_REVIEW_RESTART_NEGATIVE_PASS"+
                " malformedMarkerDenied=true worldRecreationDenied=true"+
                " markerUnchanged=true noRecovery=true"+
                " processCrashProven=false");
        }finally{
            if(secondToken!=null)secondToken.releaseAfterInitializationFailure();
            if(firstToken!=null)firstToken.releaseAfterInitializationFailure();
            if(restarted!=null)restarted.close();
            if(first!=null)first.close();
            for(int i=0;i<names.length;i++){
                if(original[i]==null)System.clearProperty(names[i]);
                else System.setProperty(names[i],original[i]);
            }
            try(Stream<Path> children=Files.walk(root)){
                for(Path path:children.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
    private G21379ReviewMarkerRestartAdmissionTest(){}
}
