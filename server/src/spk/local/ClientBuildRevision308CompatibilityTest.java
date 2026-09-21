package spk.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class ClientBuildRevision308CompatibilityTest {
    private static final int CONFIG_VALUE_1=748878668;
    private static final int OUTER_PROTOCOL_REVISION=317;

    public static void main(String[] args)throws Exception{
        assertAccepted(307);
        assertAccepted(308);
        assertOuterProtocolStillStrict();

        System.out.println(
            "CLIENT_BUILD_REVISION_308_COMPAT_PASS "+
            "outerProtocol=317 "+
            "configValue2Accepted=307,308 "+
            "configValue2Preserved=true "+
            "outer318Rejected=true"
        );
    }

    private static void assertAccepted(
        int clientBuild
    )throws Exception{
        InetAddress loop=
            InetAddress.getByName(
                "127.0.0.1"
            );

        try(ServerSocket serverSocket=
                new ServerSocket(
                    0,
                    1,
                    loop
                )){
            ExecutorService executor=
                Executors.newSingleThreadExecutor();

            try{
                Future<LoginFrame> server=
                    executor.submit(
                        ()->{
                            try(Socket socket=
                                    serverSocket.accept()){
                                return LocalLoginTransport
                                    .readLogin(
                                        socket,
                                        socket.getInputStream(),
                                        socket.getOutputStream(),
                                        "[v308-test] "
                                    );
                            }
                        }
                    );

                try(Socket client=
                        new Socket(
                            loop,
                            serverSocket.getLocalPort()
                        )){
                    sendPreloginAndLogin(
                        client,
                        OUTER_PROTOCOL_REVISION,
                        clientBuild
                    );
                }

                LoginFrame frame=
                    server.get(
                        5,
                        TimeUnit.SECONDS
                    );

                if(frame.revision!=
                        OUTER_PROTOCOL_REVISION)
                    throw new AssertionError(
                        "outer revision="+
                        frame.revision
                    );

                if(frame.configValue1!=
                        CONFIG_VALUE_1)
                    throw new AssertionError(
                        "configValue1="+
                        frame.configValue1
                    );

                if(frame.configValue2!=
                        clientBuild)
                    throw new AssertionError(
                        "expected configValue2="+
                        clientBuild+
                        " actual="+
                        frame.configValue2
                    );
            }finally{
                executor.shutdownNow();
                executor.awaitTermination(
                    3,
                    TimeUnit.SECONDS
                );
            }
        }
    }

    private static void assertOuterProtocolStillStrict()
        throws Exception{
        InetAddress loop=
            InetAddress.getByName(
                "127.0.0.1"
            );

        try(ServerSocket serverSocket=
                new ServerSocket(
                    0,
                    1,
                    loop
                )){
            ExecutorService executor=
                Executors.newSingleThreadExecutor();

            try{
                Future<Boolean> server=
                    executor.submit(
                        ()->{
                            try(Socket socket=
                                    serverSocket.accept()){
                                try{
                                    LocalLoginTransport
                                        .readLogin(
                                            socket,
                                            socket.getInputStream(),
                                            socket.getOutputStream(),
                                            "[v308-test] "
                                        );
                                    return false;
                                }catch(IOException expected){
                                    return expected
                                        .getMessage()
                                        .contains(
                                            "expected protocol revision 317"
                                        );
                                }
                            }
                        }
                    );

                try(Socket client=
                        new Socket(
                            loop,
                            serverSocket.getLocalPort()
                        )){
                    sendPreloginAndLogin(
                        client,
                        318,
                        308
                    );
                }

                if(!server.get(
                        5,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "outer revision 318 was not rejected"
                    );
            }finally{
                executor.shutdownNow();
                executor.awaitTermination(
                    3,
                    TimeUnit.SECONDS
                );
            }
        }
    }

    private static void sendPreloginAndLogin(
        Socket socket,
        int outerRevision,
        int clientBuild
    )throws IOException{
        socket.setSoTimeout(5000);

        InputStream in=
            socket.getInputStream();

        OutputStream out=
            socket.getOutputStream();

        out.write(
            LocalLoginTransport.PRELOGIN_REQUEST
        );
        out.write(7);
        out.flush();

        byte[] prefix=
            Binary.readExactly(
                in,
                9
            );

        if((prefix[8]&255)!=0)
            throw new AssertionError(
                "prelogin status="+
                (prefix[8]&255)
            );

        long serverSeed=
            Binary.i64(
                Binary.readExactly(
                    in,
                    8
                ),
                0
            );

        if(serverSeed!=
                LocalLoginTransport.SERVER_SEED)
            throw new AssertionError(
                "server seed="+
                serverSeed
            );

        int[] seeds=
            new int[]{
                0x01020304,
                0x11223344,
                (int)(serverSeed>>>32),
                (int)serverSeed
            };

        byte[] payload=
            loginPayload(
                seeds,
                outerRevision,
                clientBuild
            );

        out.write(16);
        out.write(payload.length);
        out.write(payload);
        out.flush();
    }

    private static byte[] loginPayload(
        int[] seeds,
        int outerRevision,
        int clientBuild
    )throws IOException{
        ByteArrayOutputStream inner=
            new ByteArrayOutputStream();

        inner.write(10);

        for(int seed:seeds)
            put32(
                inner,
                seed
            );

        put32(
            inner,
            CONFIG_VALUE_1
        );

        put32(
            inner,
            clientBuild
        );

        nl(inner,"local");
        nl(inner,"localpass");
        nl(inner,"LOCAL-DEVICE");
        nl(inner,"LOCAL-CLIENT");

        byte[] raw=
            inner.toByteArray();

        ByteArrayOutputStream payload=
            new ByteArrayOutputStream();

        payload.write(255);
        payload.write(
            outerRevision>>>8
        );
        payload.write(
            outerRevision
        );
        payload.write(0);

        for(int i=0;i<9;i++)
            put32(
                payload,
                0
            );

        payload.write(raw.length);
        payload.write(raw);

        return payload.toByteArray();
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

    private static void nl(
        OutputStream out,
        String text
    )throws IOException{
        out.write(
            text.getBytes(
                StandardCharsets.ISO_8859_1
            )
        );
        out.write(10);
    }
}
