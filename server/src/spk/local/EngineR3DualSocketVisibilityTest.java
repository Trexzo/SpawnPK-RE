package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class EngineR3DualSocketVisibilityTest {
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("spk-v5130-r3-dual-");
        String old=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",tmp.resolve("opensrc.properties").toString());
        World world=World.isolatedForTest(50L);
        InetAddress loop=InetAddress.getByName("127.0.0.1");
        try(ServerSocket ss=new ServerSocket(0,2,loop)){
            ExecutorService ex=Executors.newFixedThreadPool(3);
            Future<?> accept=ex.submit(() -> {
                try{
                    for(int i=0;i<2;i++){
                        Socket s=ss.accept();
                        ex.submit(() -> new LocalSession(s,true,true,world).run());
                    }
                }catch(IOException e){throw new RuntimeException(e);}
            });
            AutoCloseable c1=null,c2=null;
            try{
                c1=login(loop,ss.getLocalPort(),"opensrc");
                waitFor(() -> world.players().size()==1,3000,"primary login");
                waitFor(() -> world.tickTargetsSnapshot().size()==1,3000,"primary session ready");
                Socket s1=socket(c1);drainUntilQuiet(s1,1500,100);

                c2=login(loop,ss.getLocalPort(),"opensrc");
                waitFor(() -> world.players().size()==2,3000,"dual membership");
                Socket s2=socket(c2);

                byte[] p1=awaitContains(
                    s1,
                    4000L,
                    "primary multiplayer payload",
                    "Attack\n",
                    "Follow\n",
                    "Trade with\n",
                    "src\n"
                );
                byte[] p2=awaitContains(
                    s2,
                    4000L,
                    "secondary multiplayer payload",
                    "Attack\n",
                    "Follow\n",
                    "Trade with\n",
                    "opensrc\n"
                );

                has(p1,"Attack\n","primary Attack option");
                has(p1,"Follow\n","primary Follow option");
                has(p1,"Trade with\n","primary Trade option");
                has(p2,"Attack\n","secondary Attack option");
                has(p2,"Follow\n","secondary Follow option");
                has(p2,"Trade with\n","secondary Trade option");
                has(p1,"src\n","primary remote appearance src");
                has(p2,"opensrc\n","secondary remote appearance opensrc");

                if(world.players().byName("opensrc")==null||world.players().byName("src")==null)
                    throw new AssertionError("expected opensrc+src in shared registry");
                System.out.println("V5130_ENGINE_R3_DUAL_SOCKET_VISIBILITY_PASS members=2 optionsBoth=true opensrcSeesSrc=true srcSeesOpensrc=true sharedWorld=true");
            }finally{
                if(c2!=null)try{c2.close();}catch(Exception ignored){}
                if(c1!=null)try{c1.close();}catch(Exception ignored){}
                waitFor(() -> world.players().size()==0,3000,"logout");
                accept.get(2,TimeUnit.SECONDS);
                ex.shutdown();if(!ex.awaitTermination(3,TimeUnit.SECONDS)){ex.shutdownNow();ex.awaitTermination(1,TimeUnit.SECONDS);}
            }
        }finally{
            world.close();
            if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);
            try(java.util.stream.Stream<Path> st=Files.walk(tmp)){st.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
    }

    private static AutoCloseable login(InetAddress a,int port,String user)throws Exception{
        Method m=DualProfileSocketIntegrationTest.class.getDeclaredMethod("login",InetAddress.class,int.class,String.class);
        m.setAccessible(true);return (AutoCloseable)m.invoke(null,a,port,user);
    }
    private static Socket socket(AutoCloseable c)throws Exception{
        Field f=c.getClass().getDeclaredField("socket");f.setAccessible(true);return (Socket)f.get(c);
    }
    private static byte[] drainUntilQuiet(Socket s,long timeout,long quietMillis)throws Exception{
        InputStream in=s.getInputStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        long end=System.currentTimeMillis()+timeout;
        long quietUntil=System.currentTimeMillis()+quietMillis;
        while(System.currentTimeMillis()<end){
            int n=in.available();
            if(n>0){
                byte[] b=new byte[Math.min(n,8192)];
                int r=in.read(b);
                if(r>0){
                    out.write(b,0,r);
                    quietUntil=System.currentTimeMillis()+quietMillis;
                }
            }else{
                if(System.currentTimeMillis()>=quietUntil)
                    return out.toByteArray();
                Thread.sleep(5);
            }
        }
        return out.toByteArray();
    }
    private static byte[] awaitContains(Socket s,long timeout,String label,String... needles)throws Exception{
        InputStream in=s.getInputStream();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        long end=System.currentTimeMillis()+timeout;
        byte[] bytes=new byte[0];
        while(System.currentTimeMillis()<end){
            int n=in.available();
            if(n>0){
                byte[] b=new byte[Math.min(n,8192)];
                int r=in.read(b);
                if(r>0){
                    out.write(b,0,r);
                    bytes=out.toByteArray();
                    if(hasAll(bytes,needles))return bytes;
                }
            }else Thread.sleep(5);
        }
        throw new AssertionError(label+" timeout bytes="+bytes.length+" missing="+missing(bytes,needles));
    }
    private static boolean hasAll(byte[] bytes,String... needles){
        for(String needle:needles)if(!contains(bytes,needle))return false;
        return true;
    }
    private static String missing(byte[] bytes,String... needles){
        ArrayList<String> missing=new ArrayList<>();
        for(String needle:needles)if(!contains(bytes,needle))missing.add(needle.replace("\n","\\n"));
        return missing.toString();
    }
    private static boolean contains(byte[] b,String needle){
        byte[] n=needle.getBytes(StandardCharsets.ISO_8859_1);
        outer:for(int i=0;i+n.length<=b.length;i++){
            for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;
            return true;
        }
        return false;
    }
    private static void has(byte[] b,String needle,String label){
        if(!contains(b,needle))throw new AssertionError(label+" missing bytes="+b.length);
    }
    private interface Check{boolean ok();}
    private static void waitFor(Check c,long ms,String label)throws Exception{long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(c.ok())return;Thread.sleep(10);}throw new AssertionError(label+" timeout");}
}
