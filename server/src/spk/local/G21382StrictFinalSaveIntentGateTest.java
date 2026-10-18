package spk.local;

import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.382 exact strict final-save pre-reservation guard negative tests.
 * Does not reserve, publish, recover or ACK an account snapshot.
 */
public final class G21382StrictFinalSaveIntentGateTest {
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.382 "+why);
    }
    private static boolean refused(Runnable test,String code){
        try{test.run();return false;}
        catch(IllegalStateException e){
            return String.valueOf(e.getMessage()).contains(code);
        }
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g21382-strict-save-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        World world=null;
        LocalJvmAccountSessionLease.Token token=null;
        try {
            for(int i=0;i<keys.length;i++){
                old[i]=System.getProperty(keys[i]);
                System.setProperty(keys[i],"true");
            }
            FilePlayerRepository repo=new FilePlayerRepository(
                name->root.resolve(name+".properties"));
            world=World.isolatedForTest(60000L,repo);
            token=LocalJvmAccountSessionLease.acquire(
                world.persistence(),"guarded-user");
            check(token!=null,"no owned token");
            final LocalJvmAccountSessionLease.Token owned=token;
            check(refused(()->LocalStrictFinalSaveIntentGate
                .requireBeforeReservation(true,null),
                "G21.382 FINAL_SAVE_MISSING_LOCKED_SESSION_OWNER"),
                "strict mode allowed ownerless save");
            check(refused(()->LocalStrictFinalSaveIntentGate
                .requireBeforeReservation(true,owned),
                "G21.382 FINAL_SAVE_ACTIVE_INTENT_NOT_VERIFIED"),
                "strict mode allowed missing ACTIVE");
            Path account=root.resolve("guarded-user.properties");
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            Files.write(marker,new byte[]{7,7,7},
                StandardOpenOption.CREATE_NEW);
            check(refused(()->LocalStrictFinalSaveIntentGate
                .requireBeforeReservation(true,owned),
                "G21.382 FINAL_SAVE_ACTIVE_INTENT_NOT_VERIFIED"),
                "strict mode accepted malformed ACTIVE");
            check(Files.size(marker)==3L,
                "negative evidence changed during validation");
            // Test-only reset of an intentionally malformed fixture,
            // never a production account recovery/marker retirement.
            Files.delete(marker);
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                account);
            LocalStrictFinalSaveIntentGate.requireBeforeReservation(
                true,owned);
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "validated ACTIVE was cleared by pre-reservation gate");
            LocalStrictFinalSaveIntentGate.requireBeforeReservation(
                false,owned);
            LocalStrictFinalSaveIntentGate.requireBeforeReservation(
                false,null);
            check(world.players().size()==0&&
                !Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "pre-reservation test published state");
            System.out.println("G21382_STRICT_FINAL_SAVE_PRE_RESERVATION_PASS"+
                " absentOwnerDenied=true absentMarkerDenied=true"+
                " malformedMarkerDenied=true validForcedMarkerAccepted=true defaultModeUnchanged=true"+
                " noAccountPublication=true crashDurabilityProven=false");
        }finally{
            if(token!=null)token.releaseAfterInitializationFailure();
            if(world!=null)world.close();
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    private G21382StrictFinalSaveIntentGateTest(){}
}
