package spk.local;

import java.net.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;

public final class EngineR72RuntimeWeaponPanelSocketTest {
 private static final int ROOT_RESPONSE_TIMEOUT_MILLIS=5000;
 public static void main(String[]args)throws Exception{
  Path tmp=Files.createTempDirectory("spk-v5172-runtime-weapon-panel-");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
  try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(Exception e){throw new RuntimeException(e);}});EngineR7DevPanelSocketTest.Client c=null;try{
   c=EngineR7DevPanelSocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==1,3000,"login");EngineR7DevPanelSocketTest.waitFor(()->world.tickTargetsSnapshot().size()==1,3000,"session ready");EngineR7DevPanelSocketTest.drainUntilQuiet(c.s,1500,100);
   EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drainUntil(c.s,ROOT_RESPONSE_TIMEOUT_MILLIS,"LocalLab Dev Control Center | v5.18.5\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);byte[] combat=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"More combat...\n");EngineR7DevPanelSocketTest.has(combat,"More combat...\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2485);byte[] more=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Combat systems | runtime=");EngineR7DevPanelSocketTest.has(more,"Combat systems | runtime=");EngineR7DevPanelSocketTest.has(more,"Runtime weapon lab...\n");EngineR7DevPanelSocketTest.has(more,"Current authority summary\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2482);byte[] lab=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Runtime weapon lab | ");EngineR7DevPanelSocketTest.has(lab,"Runtime weapon lab | ");EngineR7DevPanelSocketTest.has(lab,"Browse runtime item ID...\n");EngineR7DevPanelSocketTest.has(lab,"Preview safe presentation\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2483);EngineR7DevPanelSocketTest.drainUntilQuiet(c.s,1500,100);EngineR7DevPanelSocketTest.sendAmount(c,25001);byte[] selected=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Runtime weapon lab | 25001 Blood rune c'bow\n");EngineR7DevPanelSocketTest.has(selected,"Runtime weapon lab | 25001 Blood rune c'bow\n");
   EngineR7DevPanelSocketTest.sendWidget(c,2484);byte[] preview=EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"Runtime weapon lab | 25001 Blood rune c'bow\n");EngineR7DevPanelSocketTest.has(preview,"Runtime weapon lab | 25001 Blood rune c'bow\n");
   System.out.println("V5172_ENGINE_R72_RUNTIME_WEAPON_PANEL_SOCKET_PASS native2480=true browseNumeric208=true profile25001=true previewPresentationOnly=true");
  }finally{if(c!=null)c.close();EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdown();if(!ex.awaitTermination(3,TimeUnit.SECONDS))ex.shutdownNow();}}
  finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 }
}
