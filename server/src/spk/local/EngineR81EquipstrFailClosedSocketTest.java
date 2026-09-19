package spk.local;
import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public final class EngineR81EquipstrFailClosedSocketTest{
 public static void main(String[]args)throws Exception{
  Path tmp=Files.createTempDirectory("spk-v5181-equipstr-");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
  try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(Exception e){throw new RuntimeException(e);}});EngineR5ItemLibrarySocketTest.Client c=null;try{
   c=EngineR5ItemLibrarySocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR5ItemLibrarySocketTest.waitFor(()->world.players().size()==1,3000,"login");Thread.sleep(500);EngineR5ItemLibrarySocketTest.drain(c.s,300);
   EngineR5ItemLibrarySocketTest.sendCommand(c,"equipstr 28860");byte[] r=EngineR5ItemLibrarySocketTest.drain(c.s,900);EngineR5ItemLibrarySocketTest.has(r,"RESET_HOVER_EQUIPMENT\n");
   System.out.println("V5181_ENGINE_R81_EQUIPSTR_FAILCLOSED_SOCKET_PASS c2s103=true s2c126Key1ResetToken=true unresolvedNumericProfileNotFabricated=true loadingSpinnerReleased=true");
  }finally{if(c!=null)c.close();EngineR5ItemLibrarySocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdown();if(!ex.awaitTermination(3,TimeUnit.SECONDS))ex.shutdownNow();}}
  finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
}
