package spk.local;
import java.net.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;
public final class EngineR84ApplicationPanelSocketTest{
 public static void main(String[]args)throws Exception{
  Path tmp=Files.createTempDirectory("spk-v5184-app-panel-");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
  try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(Exception e){throw new RuntimeException(e);}});EngineR7DevPanelSocketTest.Client c=null;try{
   c=EngineR7DevPanelSocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==1,3000,"login");EngineR7DevPanelSocketTest.waitFor(()->world.tickTargetsSnapshot().size()==1,3000,"session ready");EngineR7DevPanelSocketTest.drainUntilQuiet(c.s,1500,100);
   EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drain(c.s,400);
   EngineR7DevPanelSocketTest.sendWidget(c,2485);EngineR7DevPanelSocketTest.drain(c.s,300); // More
   EngineR7DevPanelSocketTest.sendWidget(c,2485);EngineR7DevPanelSocketTest.drain(c.s,300); // Diagnostics
   EngineR7DevPanelSocketTest.sendWidget(c,2483);EngineR7DevPanelSocketTest.drain(c.s,300); // Authority
   EngineR7DevPanelSocketTest.sendWidget(c,2484);EngineR7DevPanelSocketTest.drain(c.s,300); // Research
   EngineR7DevPanelSocketTest.sendWidget(c,2485);EngineR7DevPanelSocketTest.drain(c.s,300); // Discovery
   EngineR7DevPanelSocketTest.sendWidget(c,2485);byte[] asset=EngineR7DevPanelSocketTest.drain(c.s,400);EngineR7DevPanelSocketTest.has(asset,"Asset/model closure | ");EngineR7DevPanelSocketTest.has(asset,"Application protocols...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2484);byte[] app=EngineR7DevPanelSocketTest.drain(c.s,500);EngineR7DevPanelSocketTest.has(app,"Application protocols | ");EngineR7DevPanelSocketTest.has(app,"S2C250 application bus\n");EngineR7DevPanelSocketTest.has(app,"S2C126 / VM / settings\n");EngineR7DevPanelSocketTest.has(app,"C2S / world / events\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2485);byte[] back=EngineR7DevPanelSocketTest.drain(c.s,400);EngineR7DevPanelSocketTest.has(back,"Asset/model closure | ");
   System.out.println("V5184_ENGINE_R84_APPLICATION_PANEL_SOCKET_PASS assetEntry=true appPage=true back=true");
  }finally{if(c!=null)c.close();EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdown();if(!ex.awaitTermination(3,TimeUnit.SECONDS))ex.shutdownNow();}}
  finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
}
