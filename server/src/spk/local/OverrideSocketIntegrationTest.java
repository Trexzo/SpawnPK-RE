package spk.local;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class OverrideSocketIntegrationTest {
    private static void req(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    private static void putLeA(OutputStream o,int v)throws IOException{o.write(((v&255)+128)&255);o.write((v>>>8)&255);}
    private static void putLe(OutputStream o,int v)throws IOException{o.write(v&255);o.write((v>>>8)&255);}
    private static void putBeA(OutputStream o,int v)throws IOException{o.write((v>>>8)&255);o.write(((v&255)+128)&255);}
    private static void send75(EngineR7DevPanelSocketTest.Client c,int widget,int slot,int item)throws Exception{
        OutputStream o=c.s.getOutputStream();o.write((75+c.cipher.nextInt())&255);putLeA(o,widget);putLe(o,slot);putBeA(o,item);o.flush();
    }
    private static void send16(EngineR7DevPanelSocketTest.Client c,int widget,int slot,int item)throws Exception{
        OutputStream o=c.s.getOutputStream();o.write((16+c.cipher.nextInt())&255);putBeA(o,item);putLeA(o,slot);putLeA(o,widget);o.flush();
    }
    private static Properties props(Path p)throws Exception{Properties x=new Properties();if(Files.exists(p))try(InputStream in=Files.newInputStream(p)){x.load(in);}return x;}
    private static boolean containsValue(Properties p,String needle){for(Object v:p.values())if(String.valueOf(v).contains(needle))return true;return false;}

    public static void main(String[]args)throws Exception{
        Path tmp=Files.createTempDirectory("spk-v51842-override-");Path account=tmp.resolve("opensrc.properties");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",account.toString());World world=World.isolatedForTest(80L);InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,1,loop)){ExecutorService ex=Executors.newFixedThreadPool(2);Future<?> accept=ex.submit(()->{try{Socket s=ss.accept();new LocalSession(s,true,true,world).run();}catch(IOException e){throw new RuntimeException(e);}});EngineR7DevPanelSocketTest.Client c=null;try{
            c=EngineR7DevPanelSocketTest.login(loop,ss.getLocalPort(),"opensrc");EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==1,3000,"login");EngineR7DevPanelSocketTest.waitFor(()->world.tickTargetsSnapshot().size()==1,3000,"session ready");EngineR7DevPanelSocketTest.drainUntilQuiet(c.s,1500,100);WorldPlayer player=world.players().snapshot().get(0);
            EngineR7DevPanelSocketTest.sendCommand(c,"tabitem 21560 1");send75(c,3214,0,21560);EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"LocalLab Dev Control Center | v5.18.5\n");
            synchronized(player.mutationLock()){req(player.playerState().cosmetic().itemId()==21560,"Kellatha Override did not apply");req(player.bank().inventoryCount(21560)==0,"Kellatha Override did not consume source item");}
            EngineR7DevPanelSocketTest.sendCommand(c,"tabitem 22132 1");send16(c,3214,0,22132);EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"LocalLab Dev Control Center | v5.18.5\n");
            synchronized(player.mutationLock()){req(player.playerState().cosmetic().itemId()==22132,"partyhat Override did not apply");req(player.bank().inventoryCount(21560)>0,"old Kellatha not returned to inventory");}
            send16(c,3214,0,21560);EngineR7DevPanelSocketTest.sendCommand(c,"devpanel");EngineR7DevPanelSocketTest.drainUntil(c.s,2500,"LocalLab Dev Control Center | v5.18.5\n");
            synchronized(player.mutationLock()){req(player.playerState().cosmetic().itemId()==22132,"Defuse changed cosmetic");req(player.bank().inventoryCount(21560)>0,"Defuse consumed Kellatha");}
            c.close();c=null;EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);
            Properties persisted=props(account);req("22132".equals(persisted.getProperty("cosmetic.itemId")),"swap not persisted");req(containsValue(persisted,"21560"),"returned Kellatha not persisted");
            System.out.println("V51842_OVERRIDE_SOCKET_PASS kellatha75Override=true ownerPartyhat16Override=true swapPersisted=true kellatha16DefuseFailClosed=true inventoryRetained=true sessionAligned=true");
        }finally{if(c!=null)c.close();EngineR7DevPanelSocketTest.waitFor(()->world.players().size()==0,3000,"logout");accept.get(3,TimeUnit.SECONDS);ex.shutdownNow();}}
        finally{world.close();if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);try(java.util.stream.Stream<Path>st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    }
}
