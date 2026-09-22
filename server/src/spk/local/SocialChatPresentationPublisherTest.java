package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;

public final class SocialChatPresentationPublisherTest {
    private static final int[] SEED={1,2,3,4};

    @FunctionalInterface
    private interface Send {
        void run(SocialChatPresentationPublisher publisher)throws Exception;
    }

    public static void main(String[] args)throws Exception{
        assertFixed(50,bytes(
            0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x0a
        ),p->p.friendPresence(0x0102030405060708L,10));

        assertVarByte(104,concat(
            bytes(0xfd,0x80),
            asciiNl("Trade with")
        ),p->p.playerInteractionOption(3,true,"Trade with"));

        assertVarByte(104,concat(
            bytes(0xfe,0x81),
            asciiNl("null")
        ),p->p.playerInteractionOption(2,false,null));

        assertVarByte(196,concat(
            bytes(
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
                0x11,0x22,0x33,0x44,
                0x55,0x66,0x77,0x88
            ),
            asciiNl("hello")
        ),p->p.privateMessage(
            0x0102030405060708L,
            0x11223344,
            0x55667788,
            "hello"
        ));

        assertFixed(206,bytes(0x03,0x02,0x01),
            p->p.chatModes(3,2,1));

        assertVarShort(214,new byte[0],
            p->p.fullIgnoreList(Collections.emptyList()));

        assertVarShort(214,bytes(
            0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
            0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18
        ),p->p.fullIgnoreList(Arrays.asList(
            0x0102030405060708L,
            0x1112131415161718L
        )));

        assertFixed(221,bytes(0x02),
            p->p.friendServerStatus(2));

        assertVarByte(253,asciiNl("Welcome"),
            p->p.serverMessage("Welcome"));

        assertVarByte(253,asciiNl("Alice:tradereq:"),
            p->p.tradeRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:duelreq:"),
            p->p.duelRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:cwarreq:"),
            p->p.clanWarRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:whipddsreq:"),
            p->p.whipDdsRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:whipduelreq:"),
            p->p.whipDuelRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:gambreq:"),
            p->p.gambleRequestMessage("Alice"));
        assertVarByte(253,asciiNl("Alice:chalreq:"),
            p->p.challengeRequestMessage("Alice"));

        boolean badFriend=false;
        try{
            freshPublisher().friendPresence(1L,1);
        }catch(IllegalArgumentException expected){
            badFriend=true;
        }
        if(!badFriend)throw new AssertionError("unknown friend status accepted");

        boolean badChatMode=false;
        try{
            freshPublisher().chatModes(4,0,0);
        }catch(IllegalArgumentException expected){
            badChatMode=true;
        }
        if(!badChatMode)throw new AssertionError("unknown public mode accepted");

        if(Modifier.isPublic(SocialChatPresentationPublisher.class.getModifiers()))
            throw new AssertionError("social/chat protocol facade leaked into public API");

        System.out.println(
            "SOCIAL_CHAT_PRESENTATION_PUBLISHER_PASS "+
            "families=7 friendPresence=true playerOption=true privateMessage=true "+
            "chatModeOrder=public_private_trade ignoreFrames=true friendServer=true "+
            "legacyRequestSuffixes=true publicApiLeak=false"
        );
    }

    private static SocialChatPresentationPublisher freshPublisher(){
        return new SocialChatPresentationPublisher(
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(SEED.clone())
            )
        );
    }

    private static void assertFixed(
        int opcode,
        byte[] body,
        Send send
    )throws Exception{
        byte[] actual=capture(send);
        byte[] expected=new byte[1+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        System.arraycopy(body,0,expected,1,body.length);
        assertBytes("fixed opcode="+opcode,expected,actual);
    }

    private static void assertVarByte(
        int opcode,
        byte[] body,
        Send send
    )throws Exception{
        byte[] actual=capture(send);
        byte[] expected=new byte[2+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        expected[1]=(byte)body.length;
        System.arraycopy(body,0,expected,2,body.length);
        assertBytes("varByte opcode="+opcode,expected,actual);
    }

    private static void assertVarShort(
        int opcode,
        byte[] body,
        Send send
    )throws Exception{
        byte[] actual=capture(send);
        byte[] expected=new byte[3+body.length];
        expected[0]=(byte)encryptedOpcode(opcode);
        expected[1]=(byte)(body.length>>>8);
        expected[2]=(byte)body.length;
        System.arraycopy(body,0,expected,3,body.length);
        assertBytes("varShort opcode="+opcode,expected,actual);
    }

    private static byte[] capture(Send send)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        SocialChatPresentationPublisher publisher=
            new SocialChatPresentationPublisher(
                new ServerPacketWriter(
                    out,
                    new IsaacCipher(SEED.clone())
                )
            );
        send.run(publisher);
        return out.toByteArray();
    }

    private static int encryptedOpcode(int opcode){
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        return (opcode+cipher.nextInt())&255;
    }

    private static byte[] bytes(int... values){
        byte[] out=new byte[values.length];
        for(int i=0;i<values.length;i++)
            out[i]=(byte)values[i];
        return out;
    }

    private static byte[] asciiNl(String text){
        byte[] chars=text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] out=Arrays.copyOf(chars,chars.length+1);
        out[out.length-1]=10;
        return out;
    }

    private static byte[] concat(byte[] a,byte[] b){
        byte[] out=Arrays.copyOf(a,a.length+b.length);
        System.arraycopy(b,0,out,a.length,b.length);
        return out;
    }

    private static void assertBytes(
        String name,
        byte[] expected,
        byte[] actual
    ){
        if(!Arrays.equals(expected,actual))
            throw new AssertionError(
                name+
                " expected="+Arrays.toString(expected)+
                " actual="+Arrays.toString(actual)
            );
    }
}
