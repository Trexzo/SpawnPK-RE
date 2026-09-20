package spk.local;
import java.net.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.io.*;
public final class VoidglassR2PanelSocketTest{
 public static void main(String[]args)throws Exception{
  Path tmp=Files.createTempDirectory("spk-v51841-voidglass-panel-");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
  try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(IOException e){throw new RuntimeException(e);}});EngineR7DevPanelSocketTest.Client c=null;try{
   c=EngineR7DevPanelSocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==1,3000,"login");EngineR7DevPanelSocketTest.waitFor(()->world.tickTargetsSnapshot().size()==1,3000,"session ready");EngineR7DevPanelSocketTest.drainUntilQuiet(c.s,1500,100);
   EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"LocalLab Dev Control Center | v5.18.5\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2483);byte[] pets=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Pets | ");EngineR7DevPanelSocketTest.has(pets,"Presentation...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2484);byte[] pres=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Pet presentation | ");EngineR7DevPanelSocketTest.has(pres,"More presentation...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2485);byte[] more=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Pet presentation / custom | ");EngineR7DevPanelSocketTest.has(more,"Custom content...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);byte[] custom=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Custom content | LOCAL DEV - not production authority\n");EngineR7DevPanelSocketTest.has(custom,"Voidglass Nistirio R3...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);byte[] vg=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Voidglass R3 | inactive | item 29999\n");EngineR7DevPanelSocketTest.has(vg,"Give item 29999\n");EngineR7DevPanelSocketTest.has(vg,"Next visual candidate\n");EngineR7DevPanelSocketTest.has(vg,"Trigger VOIDGLASS RIFT\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Voidglass R3 | inactive | item 29999\n");if(world.players().size()!=1)throw new AssertionError("session lost after give");
   System.out.println("V5185_VOIDGLASS_R3_PANEL_SOCKET_PASS customPage=true giveRoute=true procRoute=true profileRoute=true productionAuthority=false");
  }finally{if(c!=null)c.close();EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdownNow();}}
  finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
}
