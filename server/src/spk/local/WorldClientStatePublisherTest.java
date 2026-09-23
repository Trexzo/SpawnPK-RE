package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class WorldClientStatePublisherTest {
    private static final int[] SEED={1,2,3,4};

    @FunctionalInterface
    private interface Send {
        void run(WorldClientStatePublisher publisher)throws Exception;
    }

    public static void main(String[] args)throws Exception{
        assertFixed(1,new byte[0],WorldClientStatePublisher::resetActorAnimations);
        assertFixed(35,bytes(0x02,0x11,0x22,0x33),
            p->p.cameraShake(2,0x11,0x22,0x33));
        assertFixed(61,bytes(0x01),p->p.multicombatState(1));
        assertFixed(68,new byte[0],WorldClientStatePublisher::resetVarpsToDefaults);
        assertFixed(74,bytes(0xff,0xff),p->p.musicTrack(-1));
        assertFixed(78,new byte[0],WorldClientStatePublisher::resetDestinationMarker);
        assertFixed(99,bytes(0x02),p->p.minimapState(2));
        assertFixed(107,new byte[0],WorldClientStatePublisher::resetCamera);
        assertFixed(114,bytes(0x34,0x12),p->p.systemUpdateSeconds(0x1234));
        assertFixed(121,bytes(0xb4,0x12,0x45,0xe7),
            p->p.queuedMusic(0x1234,0x4567));

        assertFixed(166,bytes(0x01,0x02,0x12,0x34,0x63,0x64),
            p->p.forcedCameraPosition(1,2,0x1234,99,100));

        assertFixed(176,bytes(
            0xfb,
            0x12,0xb4,
            0x06,
            0x22,0x11,0x44,0x33,
            0x55,0x66
        ),p->p.welcomeMetadata(
            5,0x1234,6,0x11223344,0x5566
        ));

        assertFixed(177,bytes(0x03,0x04,0x22,0x33,0x64,0x65),
            p->p.forcedCameraLookAt(3,4,0x2233,100,101));

        assertFixed(240,bytes(0x00,0x7b),p->p.weight(123));
        assertFixed(240,bytes(0xff,0x85),p->p.weight(-123));

        assertFixed(254,bytes(0x01,0x12,0x34,0x00,0x00,0x00),
            p->p.hintNpc(0x1234));
        assertFixed(254,bytes(0x04,0x12,0x34,0x45,0x67,0x08),
            p->p.hintLocation(4,0x1234,0x4567,8));
        assertFixed(254,bytes(0x0a,0x23,0x45,0x00,0x00,0x00),
            p->p.hintPlayer(0x2345));

        boolean badChannel=false;
        try{
            freshPublisher().cameraShake(5,0,0,0);
        }catch(IllegalArgumentException expected){
            badChannel=true;
        }
        if(!badChannel)throw new AssertionError("camera channel range guard missing");

        boolean badHint=false;
        try{
            freshPublisher().hintLocation(7,0,0,0);
        }catch(IllegalArgumentException expected){
            badHint=true;
        }
        if(!badHint)throw new AssertionError("location hint type range guard missing");

        if(Modifier.isPublic(WorldClientStatePublisher.class.getModifiers()))
            throw new AssertionError("world/client-state protocol facade leaked into public API");

        System.out.println(
            "WORLD_CLIENT_STATE_PUBLISHER_PASS "+
            "families=15 zeroBody=true musicSentinel=true updateTimer=true "+
            "cameraImmediateVectors=true welcomeMixedI32=true signedWeight=true "+
            "hintVariants=true fixedSixPadding=true publicApiLeak=false"
        );
    }

    private static WorldClientStatePublisher freshPublisher(){
        return new WorldClientStatePublisher(
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
        if(!Arrays.equals(expected,actual))
            throw new AssertionError(
                "opcode="+opcode+
                " expected="+Arrays.toString(expected)+
                " actual="+Arrays.toString(actual)
            );
    }

    private static byte[] capture(Send send)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        WorldClientStatePublisher publisher=
            new WorldClientStatePublisher(
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
        for(int i=0;i<values.length;i++)out[i]=(byte)values[i];
        return out;
    }
}
