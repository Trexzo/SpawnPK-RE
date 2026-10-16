package spk.local;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Genuine LocalSession TCP denial, not just repository load policy. */
public final class G21306CoreSocketRetainedFinalWitnessRestartVetoTest {
    private G21306CoreSocketRetainedFinalWitnessRestartVetoTest(){}
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.306 "+why);
    }
    private static int loginResponse(int port,String username)throws Exception{
        try(Socket sock=new Socket(InetAddress.getByName("127.0.0.1"),port)){
            sock.setSoTimeout(5000);
            InputStream in=sock.getInputStream();
            OutputStream out=sock.getOutputStream();
            out.write(14);out.write(7);out.flush();
            byte[] header=Binary.readExactly(in,9);
            require((header[8]&255)==0,"prelogin wrong");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            require(seed==LocalLoginTransport.SERVER_SEED,"seed wrong");
            int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};
            byte[] frame=G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .loginFrame(seeds,username);
            out.write(16);out.write(frame.length);out.write(frame);
            out.flush();
            return in.read();
        }
    }
    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21306-socket-restart-");
        String key="spk.local.accountFile";
        String prior=System.getProperty(key);
        String flag=LocalFinalSaveNegativeWitness.PROPERTY;
        String oldFlag=System.getProperty(flag);
        System.setProperty(key,root.resolve("opensrc.properties").toString());
        System.setProperty(flag,"false");
        World world=null;
        try{
            String user="g21306-socket";
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(PlayerSnapshotCodec.capture(user,new WorldPlayer()));
            Path file=LocalAccountProfiles.accountFile(user);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(file);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                file,user,1L,1L);
            byte[] account=Files.readAllBytes(file);
            byte[] negative=Files.readAllBytes(witness);
            Files.delete(active); // simulate uncooperative ACTIVE deletion
            require(!Files.exists(active,LinkOption.NOFOLLOW_LINKS),
                "ACTIVE still present");
            world=World.isolatedForTest(50L,repo);
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
                require(loginResponse(
                    server.listener.getLocalPort(),user)!=2,
                    "TCP successfully admitted retained negative witness");
                server.waitCompleted();
            }
            require(world.players().size()==0,"denied World has member");
            require(Arrays.equals(account,Files.readAllBytes(file))&&
                Arrays.equals(negative,Files.readAllBytes(witness)),
                "TCP veto mutated saved bytes or negative witness");
            require(repo.load(user).isPresent(),
                "forensic-only raw account inspection unavailable");
            System.out.println(
                "G21306_REAL_TCP_RETAINED_FINAL_WITNESS_VETO_PASS"+
                " actualLocalSessionLogin=true"+
                " missingACTIVE=true"+
                " disabledWitnessWriter=true"+
                " noWorldAdmission=true"+
                " accountAndWitnessUnmodified=true"+
                " safeRestartRecovery=false");
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
