package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

public final class DualProfileSocketIntegrationTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-v5123-dual-"); String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",dir.resolve("opensrc.properties").toString());
        InetAddress loop=InetAddress.getByName("127.0.0.1"); World world=World.isolatedForTest(50L);
        try(ServerSocket server=new ServerSocket(0,2,loop)){
            ExecutorService sessions=Executors.newFixedThreadPool(3);
            Future<?> accept=sessions.submit(()->{try{for(int i=0;i<2;i++){Socket s=server.accept();sessions.submit(()->new LocalSession(s,true,true,world).run());}}catch(IOException e){throw new RuntimeException(e);}});
            Client a=login(loop,server.getLocalPort(),"opensrc");
            waitFor(()->world.players().byName("opensrc")!=null,3000,"primary login");
            Client b=login(loop,server.getLocalPort(),"opensrc");
            waitFor(()->world.players().size()==2,3000,"dual membership");
            if(world.players().byName("opensrc")==null||world.players().byName("src")==null)throw new AssertionError("expected opensrc+src registry names");
            a.close();b.close();waitFor(()->world.players().size()==0,3000,"logout");accept.get(2,TimeUnit.SECONDS);
            sessions.shutdown(); if(!sessions.awaitTermination(3,TimeUnit.SECONDS)){sessions.shutdownNow();sessions.awaitTermination(1,TimeUnit.SECONDS);}
            if(!Files.isRegularFile(dir.resolve("opensrc.properties"))||!Files.isRegularFile(dir.resolve("src.properties")))throw new AssertionError("both persistent profile files were not created");
            System.out.println("V5123_DUAL_PROFILE_SOCKET_PASS sameClientLoginAlias=opensrc profiles=opensrc,src members=2 persistenceFiles=2");
        }finally{
            world.close(); if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);
            try(java.util.stream.Stream<Path> st=Files.walk(dir)){st.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}
        }
    }
    private static Client login(InetAddress host,int port,String user)throws Exception{Socket s=new Socket(host,port);s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();out.write(14);out.write(7);out.flush();byte[] pre=Binary.readExactly(in,9);if((pre[8]&255)!=0)throw new AssertionError("prelogin");long seed=Binary.i64(Binary.readExactly(in,8),0);int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};byte[] login=loginPayload(seeds,user);out.write(16);out.write(login.length);out.write(login);out.flush();byte[] ok=Binary.readExactly(in,3);if((ok[0]&255)!=2)throw new AssertionError("login response="+(ok[0]&255));IsaacCipher outbound=new IsaacCipher(seeds.clone());out.write((185+outbound.nextInt())&255);out.write(912>>>8);out.write(912);out.flush();return new Client(s);}
    private static byte[] loginPayload(int[] seeds,String user)throws IOException{ByteArrayOutputStream rsa=new ByteArrayOutputStream();rsa.write(10);for(int x:seeds)put32(rsa,x);put32(rsa,748878668);put32(rsa,307);nl(rsa,user);nl(rsa,"localpass");nl(rsa,"LOCAL-DEVICE");nl(rsa,"LOCAL-CLIENT");byte[] r=rsa.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(1);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(r.length);p.write(r);return p.toByteArray();}
    private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}private interface Check{boolean ok();}private static void waitFor(Check c,long timeout,String label)throws Exception{long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(c.ok())return;Thread.sleep(10);}throw new AssertionError("timeout "+label);}private static final class Client implements AutoCloseable{final Socket socket;Client(Socket s){socket=s;}public void close()throws IOException{socket.close();}}
}
