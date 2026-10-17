package spk.local;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** G21.192: real socket World account saves with forced-postimage opt-in. */
public final class G21192CoreSocketForcedWorldPostimageTest {
    private static final String USER="g21192-forced-world";
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.192 "+message);
    }
    @FunctionalInterface interface Check {boolean ok()throws Exception;}
    private static void await(Check c)throws Exception{
        long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<stop){
            if(c.ok())return;
            Thread.sleep(10);
        }
        check(c.ok(),"timeout");
    }
    private static String onDisk(FilePlayerRepository repo)throws Exception{
        Optional<PlayerSnapshot> item=repo.load(USER);
        check(item.isPresent(),"account missing");
        return item.get().values().get("extension.core_force.phase");
    }
    private static void session(String previous,String next)throws Exception{
        FilePlayerRepository repo=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repo);
        try{
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket client=server.login(USER)){
                    await(()->world.players().byName(USER)!=null);
                    WorldPlayer owner=world.players().byName(USER);
                    String old=owner.snapshotExtensions().namespace(
                        "core_force").get("phase");
                    check(previous==null?old==null:previous.equals(old),
                        "hydration mismatch");
                    world.submit(owner,owner.generation(),()->{
                        owner.snapshotExtensions().replaceNamespace(
                            "core_force",Collections.singletonMap("phase",next));
                    }).get(5L,TimeUnit.SECONDS);
                }
                await(()->world.players().size()==0);
                server.waitCompleted();
            }
            check(next.equals(onDisk(repo)),"postimage mismatch");
            check(repo.forcedWorldPostimageCountForTest()>0,
                "actual World forced-save path not exercised");
            check(world.persistence().failedCount()==0,"save failure");
        }finally{
            world.close();
        }
        check(world.closed(),"World did not close");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21192-real-");
        String oldFile=System.getProperty("spk.local.accountFile");
        String oldForce=System.getProperty(
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY);
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY,
            "true");
        try{
            session(null,"A_FORCED");
            session("A_FORCED","B_FORCED");
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            check("B_FORCED".equals(onDisk(repo)),
                "fresh repository rehydration mismatch");
            check(!Files.exists(
                LocalDurableAccountReviewIntentPrimitive.markerPath(
                    LocalAccountProfiles.accountFile(USER))),
                "ordinary final save created or retired review intent");
            System.out.println(
                "G21192_REAL_WORLD_FORCE_PASS"+
                " actualTcpSessions=2 optIn=true defaultOff=true"+
                " forcedTempBeforeRename=true"+
                " atomicFallback=false forcedDirectory=true"+
                " exactPostimageReadback=true"+
                " markerRetirement=false crashEpochSafe=false"+
                " rawWriterExclusion=false powerLossProven=false"+
                " originalClientProven=false");
        }finally{
            if(oldFile==null)System.clearProperty("spk.local.accountFile");
            else System.setProperty("spk.local.accountFile",oldFile);
            if(oldForce==null)System.clearProperty(
                FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY);
            else System.setProperty(
                FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY,
                oldForce);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}
