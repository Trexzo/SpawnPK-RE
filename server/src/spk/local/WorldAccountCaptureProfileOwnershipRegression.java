package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * World generation ownership alone cannot authorize capture into a second
 * account identity. Reject wrong-profile ordinary/final saves before they
 * allocate a save sequence, request a file-lease witness, suppress any
 * checkpoint or enqueue a repository write.
 *
 * This is a source-owned capture fence, not a durable account write receipt.
 */
public final class WorldAccountCaptureProfileOwnershipRegression {
    private WorldAccountCaptureProfileOwnershipRegression(){}

    private static final class RecordingRepository
        implements PlayerRepository
    {
        final List<String> accounts=
            java.util.Collections.synchronizedList(new ArrayList<>());
        @Override public Optional<PlayerSnapshot> load(String username)
            throws IOException{
            return Optional.empty();
        }
        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            accounts.add(snapshot.username());
        }
    }

    private static void check(boolean good,String label){
        if(!good)throw new AssertionError(
            "WORLD_CAPTURE_PROFILE_OWNERSHIP "+label);
    }

    private static void reject(
        WorldPlayerPersistence persistence,
        WorldPlayer owner,long generation,
        String requested,boolean finalCapture,String label
    ){
        boolean refused=false;
        try{
            if(finalCapture)
                persistence.captureDeferredFinalSave(
                    requested,owner,generation,0,"[account-owner] ",label);
            else
                persistence.captureDeferredSave(
                    requested,owner,generation,0,"[account-owner] ",label);
        }catch(IllegalStateException expected){
            refused="ACCOUNT_CAPTURE_PROFILE_OWNER_MISMATCH".equals(
                expected.getMessage());
        }
        check(refused,
            "foreign profile capture admitted "+label);
    }

    public static void main(String[] args)throws Exception{
        RecordingRepository repository=new RecordingRepository();
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer alpha=new WorldPlayer();
        WorldPlayer beta=new WorldPlayer();
        try{
            long alphaGeneration=world.registerPlayer(
                alpha,"MixedCase.Owner");
            long betaGeneration=world.registerPlayer(
                beta,"separate-owner");
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> first=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket> second=
                new AtomicReference<>();

            world.submitAndWait(alpha,alphaGeneration,()->{
                WorldPlayerPersistence persistence=world.persistence();
                reject(persistence,alpha,alphaGeneration,
                    "separate-owner",false,"other registered profile");
                reject(persistence,alpha,alphaGeneration,
                    "separate-owner",true,"other registered final profile");
                reject(persistence,alpha,alphaGeneration,
                    "opensrc",false,"another existing local profile");
                reject(persistence,alpha,alphaGeneration,
                    "",true,"blank final account");
                reject(persistence,alpha,alphaGeneration,
                    null,false,"null account");
                check(repository.accounts.isEmpty(),
                    "rejected captures already wrote repository");
                first.set(persistence.captureAndSave(
                    "mixedcase.owner",alpha,alphaGeneration,0,
                    "[account-owner] ","VALID_CASE_ALIAS"));
            },5_000L);

            check(first.get()!=null&&first.get().sequence==1L,
                "bad owner captures consumed save sequence");
            first.get().completion.get(5,TimeUnit.SECONDS);
            check(repository.accounts.size()==1&&
                "mixedcase.owner".equals(repository.accounts.get(0)),
                "canonical owner save did not reach correct repository");

            world.submitAndWait(beta,betaGeneration,()->{
                WorldPlayerPersistence persistence=world.persistence();
                reject(persistence,beta,betaGeneration,
                    "MixedCase.Owner",false,
                    "separate owner reverse cross-profile attempt");
                reject(persistence,beta,betaGeneration,
                    "MixedCase.Owner",true,
                    "separate owner reverse final capture");
                second.set(persistence.captureAndSave(
                    "separate-owner",beta,betaGeneration,0,
                    "[account-owner] ","VALID_SECOND_OWNER"));
            },5_000L);

            check(second.get()!=null&&second.get().sequence==2L,
                "foreign captures consumed second save sequence");
            second.get().completion.get(5,TimeUnit.SECONDS);
            check(repository.accounts.size()==2&&
                "separate-owner".equals(repository.accounts.get(1)),
                "second exact owner save was misrouted");

            world.submitAndWait(alpha,alphaGeneration,()->{
                reject(world.persistence(),alpha,alphaGeneration,
                    "separate-owner",false,
                    "late cross-profile ordinary capture");
            },5_000L);
            check(repository.accounts.size()==2,
                "late cross-profile request wrote repository");

            System.out.println(
                "WORLD_CAPTURE_PROFILE_OWNERSHIP_PASS"+
                " foreignNormalDenied=true foreignFinalDenied=true"+
                " nullAndBlankDenied=true"+
                " canonicalCaseAliasAccepted=true"+
                " twoDistinctWorldOwnersIsolated=true"+
                " rejectedCapturesDoNotConsumeSequence=true"+
                " acceptedRepositoryWrites=2"+
                " crashDurableReceiptClaim=false");
        }finally{
            world.close();
        }
    }
}
