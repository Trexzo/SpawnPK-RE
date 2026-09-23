package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Canonical transformed payload writer for exact-current SpawnPK wire layouts. */
final class PacketPayloadWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    PacketPayloadWriter putU8(int v){ range(v,0xff,"u8"); out.write(v); return this; }
    PacketPayloadWriter putU8Add128(int v){ range(v,0xff,"u8"); out.write((v+128)&0xff); return this; }
    PacketPayloadWriter putU8Sub128(int v){ range(v,0xff,"u8"); out.write((v-128)&0xff); return this; }
    PacketPayloadWriter putU8Neg(int v){ range(v,0xff,"u8"); out.write((-v)&0xff); return this; }
    PacketPayloadWriter putU8_128Minus(int v){ range(v,0xff,"u8"); out.write((128-v)&0xff); return this; }
    PacketPayloadWriter putI8(int v){ range(v,-128,127,"i8"); out.write(v&0xff); return this; }
    PacketPayloadWriter putI8Neg(int v){ range(v,-128,127,"i8"); out.write((-v)&0xff); return this; }
    PacketPayloadWriter putI8_128Minus(int v){ range(v,-128,127,"i8"); out.write((128-v)&0xff); return this; }

    PacketPayloadWriter putU16BE(int v){ range(v,0xffff,"u16"); out.write(v>>>8); out.write(v); return this; }
    PacketPayloadWriter putU16LE(int v){ range(v,0xffff,"u16"); out.write(v); out.write(v>>>8); return this; }
    PacketPayloadWriter putU16BELowAdd128(int v){ range(v,0xffff,"u16"); out.write(v>>>8); out.write((v+128)&0xff); return this; }
    PacketPayloadWriter putU16LELowAdd128(int v){ range(v,0xffff,"u16"); out.write((v+128)&0xff); out.write(v>>>8); return this; }
    PacketPayloadWriter putU16BELowSub128(int v){ range(v,0xffff,"u16"); out.write(v>>>8); out.write((v-128)&0xff); return this; }
    PacketPayloadWriter putU16LELowSub128(int v){ range(v,0xffff,"u16"); out.write((v-128)&0xff); out.write(v>>>8); return this; }
    PacketPayloadWriter putI16BE(int v){ range(v,-32768,32767,"i16"); out.write((v>>>8)&0xff); out.write(v&0xff); return this; }
    PacketPayloadWriter putI16LE(int v){ range(v,-32768,32767,"i16"); out.write(v&0xff); out.write((v>>>8)&0xff); return this; }
    PacketPayloadWriter putI16LELowAdd128(int v){ range(v,-32768,32767,"i16"); out.write((v+128)&0xff); out.write((v>>>8)&0xff); return this; }

    PacketPayloadWriter putI32BE(int v){ out.write(v>>>24); out.write(v>>>16); out.write(v>>>8); out.write(v); return this; }
    PacketPayloadWriter putI32X(int v){ out.write(v>>>8); out.write(v); out.write(v>>>24); out.write(v>>>16); return this; }
    PacketPayloadWriter putI32Y(int v){ out.write(v>>>16); out.write(v>>>24); out.write(v); out.write(v>>>8); return this; }
    PacketPayloadWriter putI64BE(long v){ for(int s=56;s>=0;s-=8) out.write((int)(v>>>s)); return this; }
    PacketPayloadWriter putStringNl(String s){ if(s==null)s=""; byte[] b=s.getBytes(StandardCharsets.ISO_8859_1); out.write(b,0,b.length); out.write(10); return this; }
    PacketPayloadWriter putSmartU(int v){ if(v<0||v>32767)throw new IllegalArgumentException("smart_u="+v); if(v<128)putU8(v); else putU16BE(v+32768); return this; }
    byte[] toByteArray(){ return out.toByteArray(); }
    int size(){ return out.size(); }

    private static void range(int v,int max,String n){ if(v<0||v>max)throw new IllegalArgumentException(n+"="+v); }
    private static void range(int v,int min,int max,String n){ if(v<min||v>max)throw new IllegalArgumentException(n+"="+v); }
}
