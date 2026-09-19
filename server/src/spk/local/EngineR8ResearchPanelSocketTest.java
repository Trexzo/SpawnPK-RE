package spk.local;

import java.net.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;

public final class EngineR8ResearchPanelSocketTest {
 public static void main(String[]args)throws Exception{
  Path tmp=Files.createTempDirectory("spk-v5180-research-panel-");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
  try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(Exception e){throw new RuntimeException(e);}});EngineR7DevPanelSocketTest.Client c=null;try{
   c=EngineR7DevPanelSocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==1,3000,"login");Thread.sleep(500);EngineR7DevPanelSocketTest.drain(c.s,300);
   EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drain(c.s,700);EngineR7DevPanelSocketTest.sendWidget(c,2485);EngineR7DevPanelSocketTest.drain(c.s,700);EngineR7DevPanelSocketTest.sendWidget(c,2485);byte[] diag=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(diag,"Diagnostics | ");
   EngineR7DevPanelSocketTest.sendWidget(c,2483);byte[] auth=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(auth,"Research closure...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2484);byte[] research=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(research,"Research closure | ");EngineR7DevPanelSocketTest.has(research,"Equipment/static stats...\n");EngineR7DevPanelSocketTest.has(research,"Pet proc/presentation...\n");EngineR7DevPanelSocketTest.has(research,"World transitions...\n");EngineR7DevPanelSocketTest.has(research,"Client discovery...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);byte[] equip=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(equip,"Equipment research | ");EngineR7DevPanelSocketTest.has(equip,"Browse item ID...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);EngineR7DevPanelSocketTest.drain(c.s,400);EngineR7DevPanelSocketTest.sendAmount(c,12419);byte[] rel=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(rel,"Equipment research | ");
   EngineR7DevPanelSocketTest.sendWidget(c,2485);EngineR7DevPanelSocketTest.drain(c.s,600);EngineR7DevPanelSocketTest.sendWidget(c,2484);byte[] worldp=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(worldp,"World transitions | ");EngineR7DevPanelSocketTest.sendWidget(c,2482);EngineR7DevPanelSocketTest.drain(c.s,400);EngineR7DevPanelSocketTest.sendAmount(c,28580);byte[] arax=EngineR7DevPanelSocketTest.drain(c.s,900);EngineR7DevPanelSocketTest.has(arax,"World transitions | ");
   System.out.println("V5180_ENGINE_R8_RESEARCH_PANEL_SOCKET_PASS native2480=true numeric208=true equipmentRelationBrowse=true teleportBrowse=true researchSystemUI=true");
  }finally{if(c!=null)c.close();EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdown();if(!ex.awaitTermination(3,TimeUnit.SECONDS))ex.shutdownNow();}}
  finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
}
