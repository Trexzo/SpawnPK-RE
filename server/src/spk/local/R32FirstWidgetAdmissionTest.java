package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

/** Read-only synthetic vector tests for the *first* original v308 ISAAC opcode185/912. */
public final class R32FirstWidgetAdmissionTest {
    private static int assertions;
    private static final int[] SEEDS={0x01020304,0x11223344,0x55667788,0x778899aa};

    public static void main(String[] ignored) throws Exception {
        check(ClientPacketProbe.LOGIN_BOOTSTRAP_WIDGET==912,
              "source-verified original v308 first widget constant");
        // Valid initial widget followed by another generic widget action
        // MUST retain full ISAAC alignment and later opcode185 support.
        IsaacCipher enc=new IsaacCipher(SEEDS.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        write185(wire,enc,912);
        write185(wire,enc,152);
        ClientPacketProbe ok=decode(wire.toByteArray());
        check(ok.readFirst185()==185,"correct first 185 / 912 admitted");
        check(ok.isAligned() && ok.decodedCount()==1,
              "startup ISAAC remains aligned");
        check(ok.readNextKnownPacket(),"later generic widget185 still decodable");
        ClientRequest action=ok.takeTypedRequest();
        check(action instanceof WidgetActionClientRequest,
              "later widget has typed action");
        check(((WidgetActionClientRequest)action).widgetId()==152,
              "later widget152 unchanged");
        check(ok.typedRequestCount()==0 && ok.decodedCount()==2 &&
              ok.isAligned(),"later widget pipeline aligned");

        for(int invalid:new int[]{0,1,152,911,913,65535}){
            IsaacCipher invalidEnc=new IsaacCipher(SEEDS.clone());
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            write185(raw,invalidEnc,invalid);
            ClientPacketProbe probe=decode(raw.toByteArray());
            expectRefusal(probe,"first login widget");
            check(!probe.isAligned(),
                  "invalid first widget marks probe alignment unavailable");
            check(probe.decodedCount()==1,
                  "invalid widget consumed precisely one ISAAC opcode");
            check(probe.typedRequestCount()==0,
                  "invalid widget cannot schedule a game request");
        }

        for(int other:new int[]{0,103,184,186,255}){
            IsaacCipher alternate=new IsaacCipher(SEEDS.clone());
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            raw.write((other+alternate.nextInt())&255);
            raw.write(0x03);raw.write(0x90);
            ClientPacketProbe probe=decode(raw.toByteArray());
            expectRefusal(probe,"first login opcode");
            check(!probe.isAligned() && probe.decodedCount()==1,
                  "bad first opcode stops decoder without startup");
        }

        // A truncated widget must fail closed rather than returning
        // opcode185 and entering LocalSession bootstrap.
        for(int length:new int[]{0,1}){
            IsaacCipher truncated=new IsaacCipher(SEEDS.clone());
            ByteArrayOutputStream raw=new ByteArrayOutputStream();
            raw.write((185+truncated.nextInt())&255);
            if(length==1)raw.write(3);
            ClientPacketProbe probe=decode(raw.toByteArray());
            try{
                probe.readFirst185();
                throw new AssertionError("R32 truncated first widget admitted");
            }catch(EOFException expected){
                assertions++;
            }
        }

        System.out.println("R32_V308_INITIAL_WIDGET_ADMISSION_PASS assertions="
            +assertions+" originalFirst=ISAAC185_WIDGET912"
            +" incorrectFirstWidgetsRejected=6"
            +" incorrectFirstOpcodesRejected=5"
            +" truncatedBodyRejected=2"
            +" laterGeneric185Widget152Unchanged=true"
            +" bootstrapNotStarted=true"
            +" originalClientExecuted=false");
    }

    private static ClientPacketProbe decode(byte[] bytes){
        return new ClientPacketProbe(new ByteArrayInputStream(bytes),
                                     new IsaacCipher(SEEDS.clone()),"[r32] ");
    }

    private static void write185(ByteArrayOutputStream out,IsaacCipher enc,int widget){
        out.write((185+enc.nextInt())&255);
        out.write((widget>>>8)&255);out.write(widget&255);
    }

    private static void expectRefusal(ClientPacketProbe probe,String message)
            throws Exception {
        try{
            probe.readFirst185();
            throw new AssertionError("R32 bad "+message+" was admitted");
        }catch(IOException expected){
            assertions++;
            check(expected.getMessage().contains(message),
                  "refusal describes original first "+message);
        }
    }

    private static void check(boolean result,String reason){
        assertions++;
        if(!result)throw new AssertionError("R32 "+reason);
    }
    private R32FirstWidgetAdmissionTest(){}
}
