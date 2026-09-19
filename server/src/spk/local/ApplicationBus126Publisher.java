package spk.local;
import java.io.IOException;
/** Exact generic S2C126 string-payload + target-key publisher recovered from the current client. */
final class ApplicationBus126Publisher {
    static void send(ServerPacketWriter w,int targetKey,String payload)throws IOException{
        if(targetKey<0||targetKey>65535)throw new IllegalArgumentException("targetKey");
        w.varShort(126,BootstrapPackets.widgetText126(targetKey,payload==null?"":payload));
    }
    private ApplicationBus126Publisher(){}
}
