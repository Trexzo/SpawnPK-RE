package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * R31 independent byte-for-byte model of exact pinned original v308
 * rs.Client.a(String,String,boolean) and rs/x/e buffer methods:
 *
 * - rs/x/e.a(String): getBytes(default charset), copy String.length()
 *   bytes (UTF-16 *units*, NOT encoded byte length), then byte 10.
 * - rs/x/e.M(): BigInteger(byte[]) -> toByteArray() with NO modPow,
 *   followed by one-byte signed byte-array length and raw bytes.
 * - Outer: type16 or 18, one-byte length, 255, short317, memory flag,
 *   nine CRC ints then that exact inner buffer.
 *
 * No original client JAR is loaded or executed. All names synthetic.
 */
public final class R31OriginalV308LoginWireCodecTest {
    private static final Charset WIN = Charset.forName("windows-1252");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        String originalSetting = System.getProperty("spk.local.login.decodeWindows1252");
        try {
            System.clearProperty("spk.local.login.decodeWindows1252");
            byte[] ascii = packet("local","SAFE_TEST_PASS","LOCAL-DEVICE",
                    "LOCAL-CLIENT",StandardCharsets.ISO_8859_1,308,16);
            check((ascii[0] & 255)==16,"outer type16");
            check((ascii[1] & 255)==ascii.length-2,"outer length exact");
            check((ascii[2] & 255)==255,"outer 255");
            check((ascii[3] & 255)==1 && (ascii[4] & 255)==61,
                  "short 317 byte order");
            check((ascii[42] & 255)==ascii.length-43,
                  "BigInteger inner length prefix");
            check((ascii[43] & 255)==10,"inner marker remains 10");
            LoginFrame defaultAscii = LoginFrame.parse(16,
                    Arrays.copyOfRange(ascii,2,ascii.length));
            eq("local",defaultAscii.username,"default ISO ASCII");
            eq("SAFE_TEST_PASS",defaultAscii.password,"default password");
            check(defaultAscii.revision==317 && defaultAscii.configValue2==308,
                  "original actual v308 outer revision317 and config308");

            byte[] reconnect = packet("localtest","SAFE_TEST_PASS","LOCAL-DEVICE",
                    "LOCAL-CLIENT",StandardCharsets.ISO_8859_1,307,18);
            LoginFrame reconnectParsed = LoginFrame.parse(18,
                Arrays.copyOfRange(reconnect,2,reconnect.length));
            check(reconnectParsed.loginType==18 &&
                  reconnectParsed.configValue2==307,"reconnect type18 build307");
            eq("localtest",reconnectParsed.username,"reconnect username");
            for(String probe:new String[]{"local","localtest","opensrc",
                                         "myau","test"}) {
                byte[] b=packet(probe,"SYNTHETIC","D","C",WIN,308,16);
                byte[] payload=Arrays.copyOfRange(b,2,b.length);
                LoginFrame f=LoginFrame.parse(16,payload);
                eq(probe,f.username,"ordinary ASCII works unchanged");
                check((b[1]&255)==payload.length &&
                        (payload[40]&255)==payload.length-41,
                        "outer and inner exact lengths");
            }

            String special="\u20acRune";
            byte[] windows=packet(special,"\u20acPASS","DEVICE-\u2013",
                                  "ID-\u201a",WIN,308,16);
            byte[] body=Arrays.copyOfRange(windows,2,windows.length);
            LoginFrame legacy=LoginFrame.parse(16,body);
            check(!special.equals(legacy.username),
                  "legacy ISO-8859-1 does not decode Cp1252 euro");
            eq("\u0080Rune",legacy.username,
                "legacy ISO mapping retained exactly");
            LoginFrame explicit=LoginFrame.parse(16,body,WIN);
            eq(special,explicit.username,
                "explicit Windows1252 correct username");
            eq("\u20acPASS",explicit.password,
                "explicit Windows1252 correct password bytes");
            eq("DEVICE-\u2013",explicit.deviceId,
                "explicit Windows1252 correct device text");
            eq("ID-\u201a",explicit.trailingClientId,
                "explicit Windows1252 correct trailing text");
            check(V308PreloginNameHash.hash5(special)==
                  V308PreloginNameHash.hash5(explicit.username),
                  "real username hash preserved for Cp1252 special char");

            System.setProperty("spk.local.login.decodeWindows1252","true");
            eq(special,LoginFrame.parse(16,body).username,
                "property-enabled Cp1252 matches exact client wire");
            System.setProperty("spk.local.login.decodeWindows1252","false");
            eq("\u0080Rune",LoginFrame.parse(16,body).username,
                "property-disabled legacy ISO preserved");
            expectReject(()->LoginFrame.parse(16,body,StandardCharsets.UTF_8),
                         "UTF-8 unsupported wire decoder");
            expectReject(()->LoginFrame.parse(16,body,null),
                         "null unsupported wire decoder");

            byte[] inner = inner("local","pass","dev","id",WIN,308);
            check(inner.length>0 && inner[0]==10,"original buffer 10 marker");
            byte[] bigint=bigIntegerLengthEncode(inner);
            check(bigint.length==inner.length+1,
                  "original M() exact prefix plus payload");
            check((bigint[0]&255)==inner.length,
                  "M() prefix equals original payload size");
            check(Arrays.equals(inner,Arrays.copyOfRange(
                  bigint,1,bigint.length)),
                  "M() has no RSA/modPow transformation");

            // Negative controls: a truncated body/changed inner length
            // must be rejected by existing strict R28 wire parser.
            byte[] tooShort=Arrays.copyOf(body,body.length-1);
            expectReject(()->LoginFrame.parse(16,tooShort),
                         "missing final payload byte");
            byte[] modified=body.clone();
            modified[40]--;
            expectReject(()->LoginFrame.parse(16,modified),
                         "invalid signed BigInteger length marker");
            // Verify non-ASCII, non-Cp1252 bytes still follow the
            // peculiar original getBytes()/String.length truncation.
            byte[] utf8raw="\u20acRune".getBytes(StandardCharsets.UTF_8);
            byte[] utf8copy=rawStringBytes("\u20acRune",StandardCharsets.UTF_8);
            check(utf8copy.length=="\u20acRune".length()+1,
                  "UTF-8 original copies character count, not bytes");
            check(utf8raw.length > utf8copy.length-1,
                  "truncated UTF-8 experiment is not a valid full string");
            System.out.println(
                "R31_ORIGINAL_V308_BIGINT_WIRE_CP1252_PARITY_PASS assertions="
                +assertions
                +" originalBigIntegerModPow=false"
                +" lengthPrefixExact=true"
                +" originalClientJdkCharset=Cp1252"
                +" defaultLegacyISO=true"
                +" optInWindows1252=true"
                +" noOriginalGuiExecuted=true");
        } finally {
            if(originalSetting==null)
                System.clearProperty("spk.local.login.decodeWindows1252");
            else
                System.setProperty("spk.local.login.decodeWindows1252",
                                   originalSetting);
        }
    }

    private static byte[] packet(String user,String pass,String device,
                                 String client,Charset charset,int build,
                                 int loginType)throws IOException {
        byte[] inner=inner(user,pass,device,client,charset,build);
        byte[] block=bigIntegerLengthEncode(inner);
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(buf);
        out.writeByte(loginType);
        int payloadLength=1+2+1+36+block.length;
        if(payloadLength>255)throw new AssertionError("outer too large");
        out.writeByte(payloadLength);
        out.writeByte(255);
        out.writeShort(317);
        out.writeByte(0);
        for(int i=0;i<9;i++)out.writeInt(0);
        out.write(block);
        out.flush();
        byte[] bytes=buf.toByteArray();
        check(payloadLength==bytes.length-2,
              "independent original outer packet length");
        return bytes;
    }

    private static byte[] inner(String user,String pass,String device,
                                String client,Charset charset,int build)
            throws IOException {
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(buf);
        out.writeByte(10);
        for(int seed:new int[]{0x01020304,0x11223344,0x55667788,0x778899aa})
            out.writeInt(seed);
        out.writeInt(748878668);
        out.writeInt(build);
        for(String value:new String[]{user,pass,device,client}) {
            out.write(rawStringBytes(value,charset));
        }
        out.flush();
        return buf.toByteArray();
    }

    private static byte[] rawStringBytes(String value,Charset charset)
            throws IOException {
        byte[] encoded=value.getBytes(charset);
        // Exact rs/x/e.a(String): copies String.length() bytes, not
        // encoded.length; multi-byte text can be truncated on UTF-8 JVMs.
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        buf.write(encoded,0,value.length());
        buf.write(10);
        return buf.toByteArray();
    }

    private static byte[] bigIntegerLengthEncode(byte[] original)
            throws IOException {
        // Exact original rs/x/e.M(): no modular exponentiation.
        byte[] signed=new BigInteger(original).toByteArray();
        if(signed.length>255)throw new AssertionError("inner too big");
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        buf.write(signed.length);
        buf.write(signed);
        return buf.toByteArray();
    }

    private interface TestAction { void run()throws Exception; }
    private static void expectReject(TestAction action,String reason)
            throws Exception {
        assertions++;
        try {
            action.run();
        } catch(IOException expected) {
            return;
        }
        throw new AssertionError("R31 malformed login accepted: "+reason);
    }
    private static void eq(String a,String b,String reason) {
        check(a.equals(b),reason);
    }
    private static void check(boolean predicate,String reason) {
        assertions++;
        if(!predicate)throw new AssertionError("R31 "+reason);
    }
    private R31OriginalV308LoginWireCodecTest(){}
}
