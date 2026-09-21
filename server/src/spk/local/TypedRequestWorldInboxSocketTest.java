package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class TypedRequestWorldInboxSocketTest {
    public static void main(String[] args)throws Exception{
        InetAddress loop=
            InetAddress.getByName("127.0.0.1");
        World world=
            World.isolatedForTest(50L);
        ExecutorService sessions=
            Executors.newFixedThreadPool(2);

        try(ServerSocket server=
                new ServerSocket(0,1,loop)){
            Future<?> accept=
                sessions.submit(()->{
                    try{
                        Socket socket=server.accept();
                        new LocalSession(
                            socket,
                            true,
                            true,
                            world
                        ).run();
                    }catch(IOException e){
                        throw new RuntimeException(e);
                    }
                });

            Client client=
                login(
                    loop,
                    server.getLocalPort(),
                    "issue17-inbox-boundary"
                );

            try{
                waitFor(
                    ()->world.players().size()==1,
                    3_000L,
                    "membership 1"
                );

                WorldPlayer player=
                    world.players().byName(
                        "issue17-inbox-boundary"
                    );
                if(player==null)
                    throw new AssertionError(
                        "synthetic player missing"
                    );

                if(player.movement().persistentRun())
                    throw new AssertionError(
                        "nonpersistent synthetic account "+
                        "started with run enabled"
                    );

                long before=
                    commandsProcessed(world);

                client.sendWidget(152);

                waitFor(
                    ()->player.movement().persistentRun(),
                    3_000L,
                    "first typed widget run toggle"
                );

                // The inbox future completes inside WorldCommandInbox.drain().
                // WorldPulse increments commandsProcessed immediately after drain()
                // returns, so state mutation can become observable a few instructions
                // before the diagnostic counter advances. Require the counter
                // boundedly rather than racing that bookkeeping edge.
                waitFor(
                    ()->commandsProcessed(world)>before,
                    3_000L,
                    "first typed request WorldCommandInbox execution"
                );

                long afterFirst=
                    commandsProcessed(world);

                if(afterFirst<=before)
                    throw new AssertionError(
                        "typed request mutated state "+
                        "without WorldCommandInbox execution "+
                        "before="+before+
                        " afterFirst="+afterFirst+
                        " metrics="+world.pulse().metrics()
                    );

                if(world.commands().size()!=0)
                    throw new AssertionError(
                        "WorldCommandInbox not drained after first request "+
                        "queued="+world.commands().size()
                    );

                client.sendWidget(152);

                waitFor(
                    ()->!player.movement().persistentRun(),
                    3_000L,
                    "second typed widget run toggle"
                );

                waitFor(
                    ()->commandsProcessed(world)>afterFirst,
                    3_000L,
                    "second typed request WorldCommandInbox execution"
                );

                long afterSecond=
                    commandsProcessed(world);

                if(afterSecond<=afterFirst)
                    throw new AssertionError(
                        "duplicate typed request did not execute "+
                        "through WorldCommandInbox "+
                        "afterFirst="+afterFirst+
                        " afterSecond="+afterSecond+
                        " metrics="+world.pulse().metrics()
                    );

                if(world.commands().size()!=0)
                    throw new AssertionError(
                        "WorldCommandInbox not drained after second request "+
                        "queued="+world.commands().size()
                    );

                System.out.println(
                    "TYPED_REQUEST_WORLD_INBOX_SOCKET_PASS "+
                    "opcode185Widget152=true "+
                    "state=false_true_false "+
                    "commandsProcessed="+
                    before+"_"+afterFirst+"_"+afterSecond+
                    " duplicateDelivery=true "+
                    "worldPulseThread="+
                    world.pulse().thread().getName()
                );
            }finally{
                client.close();
            }

            waitFor(
                ()->world.players().size()==0,
                3_000L,
                "membership 0"
            );

            accept.get(2,TimeUnit.SECONDS);
        }finally{
            sessions.shutdownNow();
            world.close();
        }
    }

    private static long commandsProcessed(
        World world
    ){
        String metrics=world.pulse().metrics();
        String key="commandsProcessed=";
        int start=metrics.indexOf(key);
        if(start<0)
            throw new AssertionError(
                "commandsProcessed missing from metrics: "+
                metrics
            );

        start+=key.length();
        int end=metrics.indexOf(',',start);
        if(end<0)
            throw new AssertionError(
                "commandsProcessed terminator missing: "+
                metrics
            );

        return Long.parseLong(
            metrics.substring(start,end)
        );
    }

    private static Client login(
        InetAddress host,
        int port,
        String user
    )throws Exception{
        Socket socket=new Socket(host,port);
        socket.setSoTimeout(5_000);

        InputStream in=socket.getInputStream();
        OutputStream out=socket.getOutputStream();

        out.write(14);
        out.write(7);
        out.flush();

        byte[] pre=Binary.readExactly(in,9);
        if((pre[8]&255)!=0)
            throw new AssertionError(
                "prelogin response="+(pre[8]&255)
            );

        long seed=
            Binary.i64(
                Binary.readExactly(in,8),
                0
            );

        int[] seeds={
            0x01020304,
            0x11223344,
            (int)(seed>>>32),
            (int)seed
        };

        byte[] login=
            loginPayload(seeds,user);

        out.write(16);
        out.write(login.length);
        out.write(login);
        out.flush();

        byte[] ok=Binary.readExactly(in,3);
        if((ok[0]&255)!=2)
            throw new AssertionError(
                "login response="+(ok[0]&255)
            );

        IsaacCipher outbound=
            new IsaacCipher(seeds.clone());

        sendWidget(out,outbound,912);

        return new Client(
            socket,
            out,
            outbound
        );
    }

    private static byte[] loginPayload(
        int[] seeds,
        String user
    )throws IOException{
        ByteArrayOutputStream rsa=
            new ByteArrayOutputStream();

        rsa.write(10);
        for(int seed:seeds)
            put32(rsa,seed);

        put32(rsa,748878668);
        put32(rsa,307);
        newline(rsa,user);
        newline(rsa,"localpass");
        newline(rsa,"LOCAL-DEVICE");
        newline(rsa,"LOCAL-CLIENT");

        byte[] rsaBody=rsa.toByteArray();

        ByteArrayOutputStream payload=
            new ByteArrayOutputStream();

        payload.write(255);
        payload.write(1);
        payload.write(317);
        payload.write(0);

        for(int i=0;i<9;i++)
            put32(payload,0);

        payload.write(rsaBody.length);
        payload.write(rsaBody);

        return payload.toByteArray();
    }

    private static void sendWidget(
        OutputStream out,
        IsaacCipher cipher,
        int widget
    )throws IOException{
        out.write(
            (185+cipher.nextInt())&255
        );
        out.write((widget>>>8)&255);
        out.write(widget&255);
        out.flush();
    }

    private static void put32(
        OutputStream out,
        int value
    )throws IOException{
        out.write(value>>>24);
        out.write(value>>>16);
        out.write(value>>>8);
        out.write(value);
    }

    private static void newline(
        OutputStream out,
        String value
    )throws IOException{
        out.write(
            value.getBytes(
                StandardCharsets.ISO_8859_1
            )
        );
        out.write(10);
    }

    private interface Check{
        boolean ok();
    }

    private static void waitFor(
        Check check,
        long timeout,
        String label
    )throws Exception{
        long end=
            System.currentTimeMillis()+timeout;

        while(System.currentTimeMillis()<end){
            if(check.ok())
                return;
            Thread.sleep(10L);
        }

        throw new AssertionError(
            "timeout "+label
        );
    }

    private static final class Client
        implements AutoCloseable {

        private final Socket socket;
        private final OutputStream out;
        private final IsaacCipher outbound;

        Client(
            Socket socket,
            OutputStream out,
            IsaacCipher outbound
        ){
            this.socket=socket;
            this.out=out;
            this.outbound=outbound;
        }

        void sendWidget(int widget)
            throws IOException{
            TypedRequestWorldInboxSocketTest
                .sendWidget(
                    out,
                    outbound,
                    widget
                );
        }

        @Override public void close()
            throws IOException{
            socket.close();
        }
    }
}
