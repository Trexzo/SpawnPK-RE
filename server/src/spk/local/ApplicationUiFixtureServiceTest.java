package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ApplicationUiFixtureServiceTest {
    private static final int[] SEED={3,5,7,11};
    private static final String[] FIXTURES={
        "mailbox","itemlist","makex","eventtask","events","shop","raid","confirm",
        "infobox","boss","metrics","selection","toast","attention","chapter","effects","progress"
    };
    private static final String UNCHANGED_FIXTURES_SHA256="CAPTURE_FROM_WINDOWS_CI";

    public static void main(String[] args)throws Exception{
        MessageDigest unchanged=MessageDigest.getInstance("SHA-256");
        int total=0,unchangedBytes=0;
        for(String name:FIXTURES){
            FixtureRun run=run(name);
            if(!run.result.contains("LOCAL_DEV_FIXTURE"))throw new AssertionError(name+" result="+run.result);
            if(run.wire.length==0)throw new AssertionError(name+" no packets");
            total+=run.wire.length;
            if(!name.equals("makex")){
                digestField(unchanged,name.getBytes(StandardCharsets.UTF_8));
                digestField(unchanged,run.result.getBytes(StandardCharsets.UTF_8));
                digestField(unchanged,run.wire);
                unchangedBytes+=run.wire.length;
            }
        }

        String unchangedSha=hex(unchanged.digest());
        if(unchangedBytes!=1667)throw new AssertionError("non-Make-X fixture bytes changed: "+unchangedBytes);
        if(!UNCHANGED_FIXTURES_SHA256.startsWith("CAPTURE_")&&!UNCHANGED_FIXTURES_SHA256.equals(unchangedSha))
            throw new AssertionError("non-Make-X fixture snapshot changed expected="+UNCHANGED_FIXTURES_SHA256+" actual="+unchangedSha);
        if(total!=1783)throw new AssertionError("fixture aggregate bytes changed: "+total);

        verifyMakeXFixtureFraming();
        verifyMakeXTypedEncoderFraming();

        System.out.println("V5185_APPLICATION_UI_FIXTURES_PASS fixtures="+FIXTURES.length
            +" bytes="+total+" unchangedBytes="+unchangedBytes+" unchangedSha256="+unchangedSha
            +" makeXNativeRoot="+ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT
            +" makeXFrames=6 typedOperationFrames=7 exactPayloads=true asyncResourceExcluded=true"
            +" productionDataInvented=false");
    }

    private static void verifyMakeXFixtureFraming()throws Exception{
        int[] expectedRoots={55290,55291,55292,55293,55333};
        if(!Arrays.equals(expectedRoots,ApplicationUiFixtureService.MAKE_X_NATIVE_ROOTS))
            throw new AssertionError("Make-X native root authority drift: "+Arrays.toString(ApplicationUiFixtureService.MAKE_X_NATIVE_ROOTS));
        if(ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT!=55290)
            throw new AssertionError("Make-X LOCAL fixture root drift: "+ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT);
        if(!ApplicationUiFixtureService.isKnownMakeXRoot(ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT))
            throw new AssertionError("Make-X fixture root is outside exact-current native root family");
        if(ApplicationUiFixtureService.isKnownMakeXRoot(55300))
            throw new AssertionError("regression: invalid historical Make-X root 55300 accepted");

        FixtureRun run=run("makex");
        if(!run.result.contains("layoutAuthority=CUSTOM_LOCALLAB"))
            throw new AssertionError("authority label lost: "+run.result);

        List<Frame> frames=decodeMakeXFixture(run.wire);
        if(frames.size()!=6)throw new AssertionError("Make-X emitted unexpected frame count: "+frames.size());

        expect(frames,0,97,u16(55290));
        expect(frames,1,250,app(2,bytes(0)));
        expect(frames,2,250,app(2,join(bytes(1),nl("LocalLab Make-X fixture"),bytes(1),nl("No production recipe/cost authority"))));
        expect(frames,3,250,app(0,join(bytes(0),i32(4151))));
        expect(frames,4,250,app(3,join(bytes(0),nl("Local fixture row"))));
        expect(frames,5,250,app(1,bytes(2)));

        for(int i=1;i<frames.size();i++){
            if((frames.get(i).body[2]&255)==4)
                throw new AssertionError("live Make-X fixture must not trigger async resource operation 4");
        }
    }

    private static void verifyMakeXTypedEncoderFraming()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));
        ApplicationUiService.makeXPreview(w,4,0x10203040);
        ApplicationUiService.makeXPreset(w,4);
        ApplicationUiService.makeXReset(w);
        ApplicationUiService.makeXLayout(w,"Title",null);
        ApplicationUiService.makeXLayout(w,"Title","Subtitle");
        ApplicationUiService.makeXRowAction(w,3,"Action");
        ApplicationUiService.makeXRowResource(w,4,"npc_1");

        List<Frame> frames=decodeApplicationFrames(out.toByteArray());
        if(frames.size()!=7)throw new AssertionError("typed Make-X encoder frame count="+frames.size());
        expect(frames,0,250,app(0,join(bytes(4),i32(0x10203040))));
        expect(frames,1,250,app(1,bytes(4)));
        expect(frames,2,250,app(2,bytes(0)));
        expect(frames,3,250,app(2,join(bytes(1),nl("Title"),bytes(0))));
        expect(frames,4,250,app(2,join(bytes(1),nl("Title"),bytes(1),nl("Subtitle"))));
        expect(frames,5,250,app(3,join(bytes(3),nl("Action"))));
        expect(frames,6,250,app(4,join(bytes(4),nl("npc_1"))));
    }

    private static FixtureRun run(String name)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));
        String result=ApplicationUiFixtureService.run(name,w);
        return new FixtureRun(result,out.toByteArray());
    }

    private static List<Frame> decodeMakeXFixture(byte[] wire){
        ArrayList<Frame> frames=new ArrayList<>();
        IsaacCipher decoder=new IsaacCipher(SEED.clone());
        int p=0;
        if(wire.length<3)throw new AssertionError("truncated Make-X fixture");
        int opcode=((wire[p++]&255)-decoder.nextInt())&255;
        if(opcode!=97)throw new AssertionError("first Make-X opcode expected 97 got "+opcode);
        frames.add(new Frame(opcode,Arrays.copyOfRange(wire,p,p+2)));
        p+=2;
        p=decodeApplicationFrames(wire,p,decoder,frames);
        if(p!=wire.length)throw new AssertionError("trailing Make-X bytes="+(wire.length-p));
        return frames;
    }

    private static List<Frame> decodeApplicationFrames(byte[] wire){
        ArrayList<Frame> frames=new ArrayList<>();
        int end=decodeApplicationFrames(wire,0,new IsaacCipher(SEED.clone()),frames);
        if(end!=wire.length)throw new AssertionError("trailing application bytes="+(wire.length-end));
        return frames;
    }

    private static int decodeApplicationFrames(byte[] wire,int start,IsaacCipher decoder,List<Frame> frames){
        int p=start;
        while(p<wire.length){
            int opcode=((wire[p++]&255)-decoder.nextInt())&255;
            if(opcode!=ApplicationPacket250Writer.OPCODE)
                throw new AssertionError("expected opcode250 got "+opcode+" at frame "+frames.size());
            if(p>=wire.length)throw new AssertionError("truncated opcode250 length");
            int len=wire[p++]&255;
            if(len<3||p+len>wire.length)
                throw new AssertionError("bad opcode250 len="+len+" remaining="+(wire.length-p));
            byte[] body=Arrays.copyOfRange(wire,p,p+len);
            int subtype=((body[0]&255)<<8)|(body[1]&255);
            if(subtype!=35)throw new AssertionError("Make-X subtype="+subtype);
            frames.add(new Frame(opcode,body));
            p+=len;
        }
        return p;
    }

    private static void expect(List<Frame> frames,int index,int opcode,byte[] body){
        Frame actual=frames.get(index);
        if(actual.opcode!=opcode||!Arrays.equals(actual.body,body))
            throw new AssertionError("frame "+index+" expected opcode="+opcode+" body="+hex(body)
                +" actual opcode="+actual.opcode+" body="+hex(actual.body));
    }

    private static byte[] app(int operation,byte[] payload){
        return join(u16(35),bytes(operation),payload);
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)out[i]=(byte)values[i];
        return out;
    }

    private static byte[] u16(int value){
        return bytes(value>>>8,value);
    }

    private static byte[] i32(int value){
        return bytes(value>>>24,value>>>16,value>>>8,value);
    }

    private static byte[] nl(String value){
        byte[] text=value.getBytes(StandardCharsets.ISO_8859_1);
        return join(text,bytes(10));
    }

    private static byte[] join(byte[]... parts){
        int length=0;for(byte[] part:parts)length+=part.length;
        byte[] out=new byte[length];int p=0;
        for(byte[] part:parts){System.arraycopy(part,0,out,p,part.length);p+=part.length;}
        return out;
    }

    private static void digestField(MessageDigest digest,byte[] value){
        digest.update(i32(value.length));
        digest.update(value);
    }

    private static String hex(byte[] value){
        char[] digits="0123456789abcdef".toCharArray();
        char[] out=new char[value.length*2];
        for(int i=0;i<value.length;i++){int v=value[i]&255;out[i*2]=digits[v>>>4];out[i*2+1]=digits[v&15];}
        return new String(out);
    }

    private static final class FixtureRun{
        final String result;final byte[] wire;
        FixtureRun(String result,byte[] wire){this.result=result;this.wire=wire;}
    }

    private static final class Frame{
        final int opcode;final byte[] body;
        Frame(int opcode,byte[] body){this.opcode=opcode;this.body=body;}
    }
}
