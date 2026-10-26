package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loading a syntactically valid snapshot returned from a custom or
 * in-memory repository MUST NOT hydrate a different selected account.
 * This exercises both the direct repository and real World FIFO loader,
 * and preserves case-insensitive legitimate profile names.
 *
 * This is a source-level load/admission fence, not a disk file-object
 * lock or durable original-client acceptance.
 */
public final class AccountSnapshotLoadIdentityRegression {
    private AccountSnapshotLoadIdentityRegression(){}

    private static final class ReturningRepository
        implements PlayerRepository
    {
        volatile PlayerSnapshot response;
        final AtomicInteger loadCount=new AtomicInteger();
        final AtomicInteger writeCount=new AtomicInteger();
        volatile String lastLoad;

        @Override public Optional<PlayerSnapshot> load(String username)
            throws IOException{
            lastLoad=username;
            loadCount.incrementAndGet();
            return Optional.ofNullable(response);
        }

        @Override public void save(PlayerSnapshot snapshot)
            throws IOException{
            writeCount.incrementAndGet();
        }
    }

    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(
            "ACCOUNT_SNAPSHOT_LOAD_IDENTITY "+label);
    }

    private static PlayerSnapshot snapshot(
        String username,int runEnergy
    ){
        WorldPlayer staged=new WorldPlayer();
        staged.movement().setRunEnergy(runEnergy);
        return PlayerSnapshotCodec.capture(username,staged,0);
    }

    private static LocalAccountLifecycle.Selection selected(String username){
        return new LocalAccountLifecycle.Selection(username,true);
    }

    private static void assertForeignDenied(
        ReturningRepository repo,WorldPlayer player,
        boolean useWorldPersistence
    )throws Exception{
        player.movement().setRunEnergy(81);
        repo.response=snapshot("other-account",12);
        LocalAccountLifecycle.Selection selection=
            selected("  MixedCase.Owner  ");
        LocalAccountLifecycle.LoadResult result;
        if(useWorldPersistence){
            World world=World.isolatedForTest(60_000L,repo);
            try{
                world.start();
                result=LocalAccountLifecycle.load(
                    selection,player,world.persistence(),
                    item->true,"[load-identity-world] ");
            }finally{
                world.close();
            }
        }else{
            result=LocalAccountLifecycle.load(
                selection,player,repo,
                item->true,"[load-identity-direct] ");
        }
        check(result.failed&&!result.loaded&&!result.missing,
            "cross-account load did not reject");
        check(player.movement().runEnergy()==81,
            "foreign snapshot altered live player before rejection");
        check(player.bank().inventoryCount(995)==0,
            "foreign snapshot granted account items");
        check(repo.loadCount.get()==1&&
            "  MixedCase.Owner  ".equals(repo.lastLoad),
            "source route changed selected profile before repository read");
        check(repo.writeCount.get()==0,
            "rejected account load wrote to repository");
    }

    private static void assertValidOwner(
        ReturningRepository repo,WorldPlayer player,
        boolean useWorldPersistence
    )throws Exception{
        repo.response=snapshot("mixedcase.owner",64);
        LocalAccountLifecycle.LoadResult result;
        if(useWorldPersistence){
            World world=World.isolatedForTest(60_000L,repo);
            try{
                world.start();
                result=LocalAccountLifecycle.load(
                    selected("MIXEDCASE.OWNER"),player,
                    world.persistence(),item->true,
                    "[load-identity-world] ");
            }finally{
                world.close();
            }
        }else{
            result=LocalAccountLifecycle.load(
                selected("MIXEDCASE.OWNER"),player,
                repo,item->true,
                "[load-identity-direct] ");
        }
        check(result.loaded&&!result.failed&&
            result.loadedSnapshotSha256!=null&&
            result.loadedSnapshotSha256.length()==64,
            "valid canonical account snapshot not accepted");
        check(player.movement().runEnergy()==64,
            "valid same-account snapshot not hydrated");
        check(repo.writeCount.get()==0,
            "load unexpectedly persisted an account");
    }

    private static void assertMissing(
        ReturningRepository repo,WorldPlayer player
    ){
        repo.response=null;
        player.movement().setRunEnergy(77);
        LocalAccountLifecycle.LoadResult missing=LocalAccountLifecycle.load(
            selected("mixedcase.owner"),player,repo,
            item->true,"[load-identity-missing] ");
        check(missing.missing&&!missing.failed&&
            player.movement().runEnergy()==77,
            "missing account did not keep defaults");
    }

    public static void main(String[] args)throws Exception{
        ReturningRepository direct=new ReturningRepository();
        assertForeignDenied(direct,new WorldPlayer(),false);
        assertValidOwner(direct,new WorldPlayer(),false);
        assertMissing(direct,new WorldPlayer());

        ReturningRepository world=new ReturningRepository();
        assertForeignDenied(world,new WorldPlayer(),true);
        assertValidOwner(world,new WorldPlayer(),true);

        check(direct.loadCount.get()==3&&
            world.loadCount.get()==2,
            "unexpected repository load count");

        System.out.println(
            "ACCOUNT_SNAPSHOT_LOAD_IDENTITY_PASS"+
            " foreignDirectRepositoryDenied=true"+
            " foreignWorldFifoLoadDenied=true"+
            " beforeAnyHydration=true"+
            " caseInsensitiveCanonicalOwnerAccepted=true"+
            " missingLoadPreservesDefaults=true"+
            " rejectedNoWrite=true"+
            " sourceOnlyNotDurableFileEpoch=true");
    }
}
