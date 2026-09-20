package spk.local;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Exact R8.5 localhost login transport contract.
 *
 * This class owns only wire/session setup. It intentionally does not select
 * accounts, register WorldPlayer state, publish gameplay bootstrap packets, or
 * perform any domain mutation.
 */
final class LocalLoginTransport {
    static final long SERVER_SEED = 0x0123456789ABCDEFL;
    static final int PRELOGIN_REQUEST = 14;
    static final int PROTOCOL_REVISION = 317;
    static final int LOCAL_DEV_RANK = 205;

    static LoginFrame readLogin(Socket socket,InputStream in,OutputStream out,String tag)throws IOException{
        if(!socket.getInetAddress().isLoopbackAddress())
            throw new SecurityException("non-loopback peer refused");

        socket.setSoTimeout(30_000);

        byte[] pre=Binary.readExactly(in,2);
        int requestType=pre[0]&0xff;
        int userHash5=pre[1]&0xff;
        if(requestType!=PRELOGIN_REQUEST)
            throw new IOException("expected prelogin 14, got "+requestType);
        System.out.println(tag+"prelogin ok type=14 userHash5="+userHash5);

        out.write(new byte[8]);
        out.write(0);
        Binary.put64(out,SERVER_SEED);
        out.flush();

        int loginType=in.read();
        int outerLength=in.read();
        if(loginType<0||outerLength<0)throw new EOFException("login header EOF");

        byte[] payload=Binary.readExactly(in,outerLength);
        LoginFrame frame=LoginFrame.parse(loginType,payload);
        System.out.println(tag+frame);
        if(frame.revision!=PROTOCOL_REVISION)
            throw new IOException("expected protocol revision 317, got "+frame.revision);
        return frame;
    }

    static Ciphers ciphers(LoginFrame frame){
        int[] clientToServerSeeds=frame.isaacSeeds.clone();
        int[] serverToClientSeeds=frame.isaacSeeds.clone();
        for(int i=0;i<serverToClientSeeds.length;i++)serverToClientSeeds[i]+=50;
        return new Ciphers(
            new IsaacCipher(clientToServerSeeds),
            new IsaacCipher(serverToClientSeeds)
        );
    }

    static void writeLoginSuccess(OutputStream out)throws IOException{
        // Exact current LocalLab success triplet consumed by Client.cT + flag.
        out.write(2);
        out.write(LOCAL_DEV_RANK);
        out.write(0);
        out.flush();
    }

    static final class Ciphers{
        final IsaacCipher clientToServer;
        final IsaacCipher serverToClient;
        Ciphers(IsaacCipher clientToServer,IsaacCipher serverToClient){
            this.clientToServer=clientToServer;
            this.serverToClient=serverToClient;
        }
    }

    private LocalLoginTransport(){}
}
