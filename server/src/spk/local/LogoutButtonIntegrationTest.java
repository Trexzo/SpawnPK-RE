package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;

/** Widget 2458 must produce exact S2C109 logout and unregister/save cleanly. */
public final class LogoutButtonIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-v5124-logout-"); String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",dir.resolve("opensrc.properties").toString());
        InetAddress loop=InetAddress.getByName("127.0.0.1"); World world=World.isolatedForTest(50L);
        try(ServerSocket server=new ServerSocket(0,1,loop)){
            ExecutorService ex=Executors.newFixedThreadPool(2);
            Future<?> accept=ex.submit(()->{try{Socket s=server.accept();new LocalSession(s,false,false,world).run();}catch(IOException e){throw new RuntimeException(e);}});
            try(Socket s=new Socket(loop,server.getLocalPort())){
                s.setSoTimeout(5000); InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
                out.write(14);out.write(7);out.flush(); byte[] pre=Binary.readExactly(in,9); if((pre[8]&255)!=0)throw new AssertionError("prelogin"); long seed=Binary.i64(Binary.readExactly(in,8),0);
                int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed}; byte[] login=loginPayload(seeds,"opensrc"); out.write(16);out.write(login.length);out.write(login);out.flush();
                byte[] ok=Binary.readExactly(in,3); if((ok[0]&255)!=2)throw new AssertionError("login="+(ok[0]&255));
                IsaacCipher c2s=new IsaacCipher(seeds.clone());
                // mandatory first click/ack then normal logout button click, same stream/cipher.
                writeWidget(out,c2s,912); writeWidget(out,c2s,2458); out.flush();
                int[] s2cSeeds=seeds.clone(); for(int i=0;i<s2cSeeds.length;i++)s2cSeeds[i]+=50; IsaacCipher s2c=new IsaacCipher(s2cSeeds);
                int enc=in.read(); if(enc<0)throw new AssertionError("EOF before logout packet"); int opcode=(enc-s2c.nextInt())&255; if(opcode!=109)throw new AssertionError("expected S2C109 got "+opcode);
                waitFor(()->world.players().size()==0,3000,"world unregister");
            }
            accept.get(3,TimeUnit.SECONDS); ex.shutdown(); if(!ex.awaitTermination(3,TimeUnit.SECONDS))ex.shutdownNow();
            if(!Files.isRegularFile(dir.resolve("opensrc.properties")))throw new AssertionError("account not saved");
            System.out.println("V5124_LOGOUT_BUTTON_PASS widget2458=true s2c109=true save=true unregister=true");
        }finally{
            world.close(); if(old==null)System.clearProperty("spk.local.accountFile"); else System.setProperty("spk.local.accountFile",old);
            try(java.util.stream.Stream<Path> st=Files.walk(dir)){st.sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}
        }
    }
    private static void writeWidget(OutputStream out,IsaacCipher cipher,int widget)throws IOException{out.write((185+cipher.nextInt())&255);out.write((widget>>>8)&255);out.write(widget&255);}
    private static byte[] loginPayload(int[] seeds,String user)throws IOException{ByteArrayOutputStream rsa=new ByteArrayOutputStream();rsa.write(10);for(int x:seeds)put32(rsa,x);put32(rsa,748878668);put32(rsa,307);nl(rsa,user);nl(rsa,"localpass");nl(rsa,"LOCAL-DEVICE");nl(rsa,"LOCAL-CLIENT");byte[] r=rsa.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(1);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(r.length);p.write(r);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);} private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);} private interface Check{boolean ok();} private static void waitFor(Check c,long timeout,String label)throws Exception{long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(c.ok())return;Thread.sleep(10);}throw new AssertionError("timeout "+label);}
}
