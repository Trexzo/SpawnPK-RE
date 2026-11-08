package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * R28: real v308 wire-format grammar, strict inner-block boundary tests.
 * Offline only. No original client, GUI, network or account mutation.
 *
 * v308 client bytecode: outer login revision 317, login types 16/18,
 * nine CRC ints, inner marker 10, four ISAAC seeds, two config ints
 * and four newline strings. Build marker 307/308 is config2, not
 * the outer revision.
 */
public final class R28V308LoginFrameBoundaryTest {
    private static final int CONFIG_1=748878668;
    private static final int[] SEEDS={
        0x01020304, 0x11223344, 0x55667788, 0x99aabbcc
    };
    private static final String USER="SYNTHETIC_USER_NEVER_REAL";
    private static final String PASS="SYNTHETIC_PASSWORD_NEVER_REAL";
    private static final String DEVICE="SYNTHETIC_DEVICE_NEVER_REAL";
    private static final String CLIENT="SYNTHETIC_TRAILING_ID_NEVER_REAL";
    private static int assertions;

    public static void main(String[] args)throws Exception{
        for(int build:new int[]{307,308}){
            for(int type:new int[]{16,18}){
                byte[] bytes=frame(build);
                LoginFrame f=LoginFrame.parse(type,bytes);
                check(f.revision==317,"outer protocol revision 317");
                check(f.configValue1==CONFIG_1,"config1");
                check(f.configValue2==build,"v308 build marker");
                check(f.loginType==type,"login type");
                check(f.username.equals(USER)&&f.password.equals(PASS)
                      &&f.deviceId.equals(DEVICE)
                      &&f.trailingClientId.equals(CLIENT),"all wire strings");
                check(Arrays.equals(f.isaacSeeds,SEEDS),"four seeds");
                String log=f.toString();
                for(String secret:new String[]{USER,PASS,DEVICE,CLIENT,
                                                String.valueOf(SEEDS[0])}){
                    check(!log.contains(secret),"no sensitive login data logged");
                }
                check(log.contains("password=<redacted>"),"password redacted");
            }
        }

        byte[] good=frame(308);
        requireReject("bad login type",15,good);
        byte[] shortFrame=Arrays.copyOf(good,69);
        requireReject("missing fixed minimum",16,shortFrame);
        byte[] badMarker=good.clone();
        badMarker[0]=0;
        requireReject("missing 255 marker",16,badMarker);
        byte[] innerMarker=good.clone();
        innerMarker[41]=11;
        requireReject("missing marker 10",16,innerMarker);
        byte[] smaller=good.clone();
        smaller[40]--;
        requireReject("inner shorter than outer",16,smaller);
        byte[] larger=good.clone();
        larger[40]++;
        requireReject("inner greater than outer",16,larger);
        byte[] tooSmall=good.clone();
        tooSmall[40]=28;
        requireReject("inner block <29",16,tooSmall);
        byte[] trailingOutside=Arrays.copyOf(good,good.length+1);
        trailingOutside[trailingOutside.length-1]=0x44;
        requireReject("trailing outside declared inner block",16,trailingOutside);
        byte[] trailingInside=Arrays.copyOf(good,good.length+1);
        trailingInside[trailingInside.length-1]=0x44;
        trailingInside[40]++;
        requireReject("trailing after fourth field inside inner",16,trailingInside);
        byte[] finalTerminatorMissing=Arrays.copyOf(good,good.length-1);
        finalTerminatorMissing[40]--;
        requireReject("fourth field missing newline",16,finalTerminatorMissing);

        // A precisely minimal 70-byte login (four empty newline fields)
        // must still parse at transport layer; account validation is a
        // separate downstream concern.
        byte[] minimal=frameWithFields(308,"","","","");
        check(minimal.length==70,"minimum legal v308 transport size");
        LoginFrame min=LoginFrame.parse(16,minimal);
        check(min.username.isEmpty()&&min.password.isEmpty(),
              "minimum transport shape accepted without account mutation");

        System.out.println("R28_V308_LOGIN_INNER_BLOCK_BOUNDARY_PASS"
             +" assertions="+assertions
             +" builds=307,308 types=16,18"
             +" outerProtocol=317 exactInnerLength=true"
             +" fourNLLimit=true noTrailingBytes=true noIdentifyingLogs=true");
    }

    private static void check(boolean result,String message){
        assertions++;
        if(!result)throw new AssertionError("R28 "+message);
    }

    private static void requireReject(String label,int type,byte[] payload)throws Exception{
        assertions++;
        try{
            LoginFrame.parse(type,payload);
        }catch(IOException expected){
            return;
        }catch(IndexOutOfBoundsException crash){
            throw new AssertionError("R28 unbounded read instead of IOException: "+label,crash);
        }
        throw new AssertionError("R28 malformed frame accepted: "+label);
    }

    private static byte[] frame(int build)throws IOException{
        return frameWithFields(build,USER,PASS,DEVICE,CLIENT);
    }

    private static byte[] frameWithFields(int build,String user,String pass,
                                          String device,String trailing)throws IOException{
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        inner.write(10);
        for(int seed:SEEDS)put32(inner,seed);
        put32(inner,CONFIG_1);
        put32(inner,build);
        nl(inner,user);nl(inner,pass);nl(inner,device);nl(inner,trailing);
        byte[] body=inner.toByteArray();
        if(body.length>255)throw new AssertionError("synthetic inner length");
        ByteArrayOutputStream packet=new ByteArrayOutputStream();
        packet.write(255);
        packet.write(317>>>8);
        packet.write(317);
        packet.write(0);
        for(int i=0;i<9;i++)put32(packet,0);
        packet.write(body.length);
        packet.write(body);
        return packet.toByteArray();
    }

    private static void put32(OutputStream out,int v)throws IOException{
        out.write(v>>>24);out.write(v>>>16);out.write(v>>>8);out.write(v);
    }

    private static void nl(OutputStream out,String text)throws IOException{
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));out.write(10);
    }

    private R28V308LoginFrameBoundaryTest(){}
}
