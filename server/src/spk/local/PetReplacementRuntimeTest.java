package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** Live-loopback proof that dropping a second supported pet replaces, rather than rejects, the active pet. */
public final class PetReplacementRuntimeTest {
    public static void main(String[] args)throws Exception{
        Path tmp=Files.createTempDirectory("spk-pet-replace-").resolve("opensrc.properties");String old=System.getProperty("spk.local.accountFile");System.setProperty("spk.local.accountFile",tmp.toString());
        try{
            InetAddress loop=InetAddress.getByName("127.0.0.1");
            try(ServerSocket ss=new ServerSocket(0,1,loop)){
                ExecutorService ex=
                    Executors.newSingleThreadExecutor(
                        r->{
                            Thread t=
                                new Thread(
                                    r,
                                    "pet-replacement-loopback"
                                );
                            t.setDaemon(true);
                            return t;
                        }
                    );
                Future<?> server=null;
                try{
                    server=ex.submit(
                        ()->{
                            try{
                                new LocalSession(
                                    ss.accept(),
                                    true,
                                    true
                                ).run();
                            }catch(IOException e){
                                throw new RuntimeException(e);
                            }
                        }
                    );
                    try(Socket s=new Socket(loop,ss.getLocalPort())){
                    s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();out.write(14);out.write(7);out.flush();byte[] pre=Binary.readExactly(in,9);long seed=Binary.i64(Binary.readExactly(in,8),0);int[] seeds={0x01020304,0x11223344,(int)(seed>>>32),(int)seed};byte[] lp=loginPayload(seeds);out.write(16);out.write(lp.length);out.write(lp);out.flush();Binary.readExactly(in,3);IsaacCipher c2s=new IsaacCipher(seeds.clone());int[] si=seeds.clone();for(int i=0;i<4;i++)si[i]+=50;IsaacCipher s2c=new IsaacCipher(si);
                    send185(out,c2s,912);expectFixed(in,s2c,249,3);expectFixed(in,s2c,73,4);expectVarShort(in,s2c,81);expectFixed(in,s2c,110,1);expectVarShort(in,s2c,126);V5BootstrapTestSupport.expectNativeSidebarInventoryAndFountain(in,s2c);
                    send103(out,c2s,"tabitem 22519 1");byte[] invA=expectEventually53(in,s2c,3214);int slotA=findItem(invA,22519);send103(out,c2s,"tabitem 23484 1");byte[] invB=expectEventually53(in,s2c,3214);int slotB=findItem(invB,23484);if(slotA<0||slotB<0)throw new AssertionError("spawn slots "+slotA+","+slotB);
                    send87(out,c2s,22519,3214,slotA);byte[] consumedA=expectEventually53(in,s2c,3214);if(findItem(consumedA,22519)>=0)throw new AssertionError("first pet retained");expectEventually65(in,s2c);
                    send87(out,c2s,23484,3214,slotB);
                    byte[] replacementInventory=
                        expectEventually53(
                            in,
                            s2c,
                            3214
                        );
                    if(findItem(
                            replacementInventory,
                            23484
                        )>=0)
                        throw new AssertionError(
                            "new pet retained"
                        );
                    int restoredSlot=
                        findItem(
                            replacementInventory,
                            22519
                        );
                    if(restoredSlot<0)
                        throw new AssertionError(
                            "old pet item not restored in atomic replacement inventory"
                        );
                    if(restoredSlot!=slotB)
                        throw new AssertionError(
                            "old pet restored to "+
                            restoredSlot+
                            " expected dropped-new slot "+
                            slotB
                        );

                    expectEventually65(
                        in,
                        s2c
                    ); // old follower remove
                    expectEventually65(
                        in,
                        s2c
                    ); // new follower add
                    }
                    try{
                        server.get(
                            2,
                            TimeUnit.SECONDS
                        );
                    }catch(Exception ignored){}
                }finally{
                    ex.shutdownNow();
                    try{
                        ex.awaitTermination(
                            2,
                            TimeUnit.SECONDS
                        );
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                    }
                }
            }
            PetState pet=new PetState();AccountStore.load(new BankState(),new EquipmentState(),new MovementState(),pet);if(!pet.active()||pet.itemId()!=23484||pet.npcId()!=3845)throw new AssertionError("persisted active="+pet.active()+" item="+pet.itemId()+" npc="+pet.npcId());
            System.out.println("V56_PET_REPLACEMENT_RUNTIME_PASS first22519ReturnedToDroppedNewSlot=true replacementInventoryAtomic53=true second23484Active=true oldFollowerRemoved=true newFollowerSpawned=true persisted=23484->3845");
        }finally{if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);}
    }
    private static int findItem(byte[] p,int wanted){int slots=Binary.u16(p,2),off=4;for(int i=0;i<slots;i++){int q=p[off++]&255;if(q==255)off+=4;int lo=((p[off++]&255)-128)&255,hi=p[off++]&255,id=((hi<<8)|lo)-1;if(id==wanted&&q>0)return i;}return -1;}
    private static void send185(OutputStream o,IsaacCipher c,int w)throws IOException{o.write((185+c.nextInt())&255);o.write(w>>>8);o.write(w);o.flush();}private static void send103(OutputStream o,IsaacCipher c,String cmd)throws IOException{byte[] b=(cmd+"\n").getBytes(StandardCharsets.ISO_8859_1);o.write((103+c.nextInt())&255);o.write(b.length);o.write(b);o.flush();}private static void send87(OutputStream o,IsaacCipher c,int item,int widget,int slot)throws IOException{o.write((87+c.nextInt())&255);putBEA(o,item);putBE(o,widget);putBEA(o,slot);o.flush();}private static void putBE(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write(v);}private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void expectFixed(InputStream in,IsaacCipher c,int op,int n)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);Binary.readExactly(in,n);}private static byte[] expectVarShort(InputStream in,IsaacCipher c,int op)throws IOException{int g=((in.read()&255)-c.nextInt())&255;if(g!=op)throw new AssertionError("op="+g+" expected="+op);int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}private static byte[] expectEventually53(InputStream in,IsaacCipher c,int widget)throws IOException{for(int i=0;i<60;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==53){int n=((in.read()&255)<<8)|(in.read()&255);byte[] p=Binary.readExactly(in,n);if(Binary.u16(p,0)==widget)return p;}else skip(in,op);}throw new AssertionError("53 widget="+widget);}private static byte[] expectEventually65(InputStream in,IsaacCipher c)throws IOException{for(int i=0;i<60;i++){int op=((in.read()&255)-c.nextInt())&255;if(op==65){int n=((in.read()&255)<<8)|(in.read()&255);return Binary.readExactly(in,n);}skip(in,op);}throw new AssertionError("65");}
    private static void skip(InputStream in,int op)throws IOException{switch(op){case 81:case 53:case 65:case 126:{int n=((in.read()&255)<<8)|(in.read()&255);Binary.readExactly(in,n);break;}case 36:case 71:Binary.readExactly(in,3);break;case 134:Binary.readExactly(in,6);break;case 110:case 106:Binary.readExactly(in,1);break;case 248:Binary.readExactly(in,4);break;case 97:Binary.readExactly(in,2);break;case 219:case 27:break;default:throw new AssertionError("unexpected s2c "+op);}}
    private static byte[] loginPayload(int[] seeds)throws IOException{ByteArrayOutputStream inner=new ByteArrayOutputStream();inner.write(10);for(int v:seeds)put32(inner,v);put32(inner,748878668);put32(inner,307);nl(inner,"opensrc");nl(inner,"localpass");nl(inner,"LOCAL-DEVICE");nl(inner,"LOCAL-CLIENT");byte[] raw=inner.toByteArray();ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(255);p.write(317>>>8);p.write(317);p.write(0);for(int i=0;i<9;i++)put32(p,0);p.write(raw.length);p.write(raw);return p.toByteArray();}private static void put32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}private static void nl(OutputStream o,String s)throws IOException{o.write(s.getBytes(StandardCharsets.ISO_8859_1));o.write(10);}
}
