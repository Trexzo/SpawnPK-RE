package spk.local;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Foreign untrusted witness must not globally veto real clean TCP login. */
public final class G21307CoreSocketForeignFinalWitnessIsolationTest {
    private G21307CoreSocketForeignFinalWitnessIsolationTest(){}
    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.307 "+why);
    }
    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21307-socket-foreign-");
        String key="spk.local.accountFile",prior=System.getProperty(key);
        String flag=LocalFinalSaveNegativeWitness.PROPERTY;
        String oldFlag=System.getProperty(flag);
        System.setProperty(key,root.resolve("opensrc.properties").toString());
        System.setProperty(flag,"false");
        World world=null;
        try{
            String selected="g21307-clean",foreign="g21307-foreign";
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(selected,new WorldPlayer()));
            repo.save(PlayerSnapshotCodec.capture(foreign,new WorldPlayer()));
            Path other=LocalAccountProfiles.accountFile(foreign);
            byte[] otherBytes=Files.readAllBytes(other);
            Path negative=LocalFinalSaveNegativeWitness.witnessPath(other);
            Files.createDirectory(negative); // malformed foreign negative
            world=World.isolatedForTest(50L,repo);
            final World live=world;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
                try(Socket client=server.login(selected)){
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
                    while(live.players().byName(selected)==null&&
                          System.nanoTime()<deadline)Thread.sleep(10L);
                    require(live.players().byName(selected)!=null,
                        "foreign negative falsely denied clean TCP player");
                    require(live.players().byName(foreign)==null,
                        "foreign player incorrectly registered");
                }
                server.waitCompleted();
            }
            require(world.players().size()==0,"logout did not unregister");
            require(Files.isDirectory(negative,LinkOption.NOFOLLOW_LINKS)&&
                Arrays.equals(otherBytes,Files.readAllBytes(other))&&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(
                    LocalAccountProfiles.accountFile(selected)),
                    LinkOption.NOFOLLOW_LINKS),
                "foreign negative mutated or selected created witness");
            System.out.println(
                "G21307_REAL_TCP_FOREIGN_FINAL_WITNESS_CONTROL_PASS"+
                " actualVersion317Login=true"+
                " cleanWorldAdmissionAndLogout=true"+
                " foreignNonregularWitnessUnchanged=true"+
                " exactAccountIsolation=true"+
                " noForgedGrantAuthority=true");
        }finally{
            if(world!=null)world.close();
            if(prior==null)System.clearProperty(key);
            else System.setProperty(key,prior);
            if(oldFlag==null)System.clearProperty(flag);
            else System.setProperty(flag,oldFlag);
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}
