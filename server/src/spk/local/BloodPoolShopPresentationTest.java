package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class BloodPoolShopPresentationTest {
    private static final int[] SEED={81,82,83,84};

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(SEED.clone())
            );

        BloodPoolShopPresentation presentation=
            new BloodPoolShopPresentation(
                writer
            );

        presentation.resetSlots();
        presentation.appendOpaqueSlotRecord(
            "opaque-slot-record"
        );

        byte[] wire=out.toByteArray();
        IsaacCipher decode=
            new IsaacCipher(SEED.clone());

        int offset=0;
        offset=assertPacket(
            wire,
            offset,
            decode,
            "RESET_BLOOD_POOL_SHOP_SLOTS",
            1
        );
        offset=assertPacket(
            wire,
            offset,
            decode,
            "opaque-slot-record",
            37
        );

        if(offset!=wire.length)
            throw new AssertionError(
                "trailing bytes="+
                (wire.length-offset)
            );

        if(!"EXACT_CURRENT_CLIENT".equals(
                BloodPoolShopPresentation
                    .PRESENTATION_AUTHORITY))
            throw new AssertionError(
                "presentation authority"
            );

        if(!"UNKNOWN_CLIENT_PRESENTATION_DETAIL".equals(
                BloodPoolShopPresentation
                    .SLOT_FIELD_AUTHORITY))
            throw new AssertionError(
                "opaque field authority"
            );

        protocolOnlyBoundary();

        System.out.println(
            "BLOOD_POOL_SHOP_PRESENTATION_PASS "+
            "resetTarget=1 "+
            "resetToken=RESET_BLOOD_POOL_SHOP_SLOTS "+
            "appendTarget=37 "+
            "opaqueFields=true "+
            "economicsOwned=false "+
            "destinationRootOwned=false "+
            "authority=EXACT_CURRENT_CLIENT"
        );
    }

    private static void protocolOnlyBoundary(){
        for(Field field:
                BloodPoolShopPresentation.class
                    .getDeclaredFields()){
            assertNoDomainLeak(
                field.getName(),
                "field"
            );
        }

        for(Method method:
                BloodPoolShopPresentation.class
                    .getDeclaredMethods()){
            assertNoDomainLeak(
                method.getName(),
                "method"
            );

            for(Class<?> type:
                    method.getParameterTypes())
                if(type==BloodPoolStoreService.class||
                   type==ShopService.class)
                    throw new AssertionError(
                        "domain service leaked into presentation method "+
                        method.getName()
                    );
        }
    }

    private static void assertNoDomainLeak(
        String value,
        String kind
    ){
        String lower=
            value.toLowerCase(
                Locale.ROOT
            );

        for(String forbidden:new String[]{
                "price",
                "currency",
                "stock",
                "purchase",
                "eligibility",
                "destinationroot",
                "shopid"
            })
            if(lower.contains(forbidden))
                throw new AssertionError(
                    "unproven Blood Pool semantic in "+
                    kind+" "+
                    value
                );
    }

    private static int assertPacket(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "opcode="+opcode
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        byte[] expected=
            exactBody(
                payload,
                target
            );

        if(length!=expected.length)
            throw new AssertionError(
                "length="+length+
                " expected="+
                expected.length
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+length
            );

        if(!Arrays.equals(
                expected,
                actual))
            throw new AssertionError(
                "packet body mismatch"
            );

        return offset+length;
    }

    private static byte[] exactBody(
        String payload,
        int target
    ){
        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );
        byte[] out=
            Arrays.copyOf(
                text,
                text.length+3
            );
        out[text.length]=10;
        out[text.length+1]=
            (byte)(target>>>8);
        out[text.length+2]=
            (byte)((target+128)&255);
        return out;
    }

    private BloodPoolShopPresentationTest(){}
}
