package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.378: force a lock-name change after review sidecar observations.
 * Negative-only; no account publication, reward, or durable epoch.
 */
public final class G21378ReviewVetoLateLockContinuityTest {
    private static void check(boolean ok,String msg) {
        if(!ok)throw new AssertionError("G21.378 "+msg);
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g21378-review-lock-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++){
            previous[i]=System.getProperty(keys[i]);
            System.setProperty(keys[i],"true");
        }
        World world=null;
        LocalJvmAccountSessionLease.Token token=null;
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            world=World.isolatedForTest(60000L,repo);
            boolean unsupported=false;
            try{
                token=LocalJvmAccountSessionLease.acquire(
                    world.persistence(),"same-user");
            }catch(IllegalStateException e){
                // Some Windows providers cannot expose lock fileKey.
                unsupported=String.valueOf(e.getMessage()).contains(
                    "G21.185 CROSS_JVM_ACCOUNT_SESSION_DENIED") &&
                    e.getCause()!=null &&
                    String.valueOf(e.getCause().getMessage()).contains(
                    "G21.221 INITIAL_LOCK_FILE_IDENTITY_UNSAFE");
                if(!unsupported)throw e;
            }
            if(unsupported){
                check(world.players().size()==0,
                    "unsupported provider registered a World member");
                System.out.println("G21378_REVIEW_VETO_LATE_LOCK_PASS"+
                    " providerUnsupported=true failClosed=true");
                return;
            }
            check(token!=null,"lease missing");
            Path lock=root.resolve("same-user.properties.session.lck");
            check(Files.isRegularFile(lock,LinkOption.NOFOLLOW_LINKS),
                "original lock missing");
            // Windows does not reliably allow renaming an open lock leaf.
            if(System.getProperty("os.name").toLowerCase().contains("win")){
                System.out.println("G21378_REVIEW_VETO_LATE_LOCK_PASS"+
                    " windowsRenameUnsupported=true failClosed=true");
                return;
            }
            final boolean[] injected={false};
            LocalReviewIntentAdmissionVeto.setAfterNegativeChecksForTest(
                selected->{
                    try {
                        Files.move(lock,root.resolve("displaced.lock"),
                            StandardCopyOption.ATOMIC_MOVE);
                        injected[0]=true;
                    }catch(java.io.IOException e){
                        throw new IllegalStateException(
                            "G21.378 TEST_LOCK_RENAME_FAILED",e);
                    }
                });
            boolean denied=false;
            try{
                LocalReviewIntentAdmissionVeto.requireAdmissible(
                    true,true,token);
            }catch(IllegalStateException e){
                denied=String.valueOf(e.getMessage()).contains(
                    "G21.221 SESSION_LOCK_OBJECT_CHANGED_NO_PUBLICATION");
            }
            check(injected[0]&&denied,
                "late lock displacement did not deny review admission");
            check(world.players().size()==0,
                "refused review admission registered World member");
            check(!Files.exists(root.resolve("same-user.properties")),
                "refused review admission wrote an account");
            System.out.println("G21378_REVIEW_VETO_LATE_LOCK_PASS"+
                " injected=true deniedBeforeRegistration=true"+
                " noAccountPublication=true durableEpoch=false");
        }finally{
            LocalReviewIntentAdmissionVeto.setAfterNegativeChecksForTest(null);
            if(token!=null)token.releaseAfterInitializationFailure();
            if(world!=null)world.close();
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    private G21378ReviewVetoLateLockContinuityTest(){}
}
