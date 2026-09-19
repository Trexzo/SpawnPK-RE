package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Exact S2C250 transport foundation: VAR_BYTE -> u16_be subtype -> typed application payload. */
final class ApplicationPacket250Writer {
    static final int OPCODE=250;
    static final class Payload {
        private final ByteArrayOutputStream out=new ByteArrayOutputStream();
        Payload u8(int v){out.write(v&255);return this;}
        Payload u16(int v){out.write((v>>>8)&255);out.write(v&255);return this;}
        Payload i32(int v){out.write((v>>>24)&255);out.write((v>>>16)&255);out.write((v>>>8)&255);out.write(v&255);return this;}
        Payload i64(long v){for(int s=56;s>=0;s-=8)out.write((int)(v>>>s)&255);return this;}
        Payload stringNl(String s){byte[]b=(s==null?"":s).getBytes(StandardCharsets.ISO_8859_1);out.write(b,0,b.length);out.write(10);return this;}
        byte[] bytes(){return out.toByteArray();}
    }
    static Payload payload(){return new Payload();}
    static byte[] encode(int subtype,byte[] body){if(subtype<0||subtype>65535)throw new IllegalArgumentException("subtype");if(body==null)body=new byte[0];if(body.length+2>255)throw new IllegalArgumentException("S2C250 VAR_BYTE payload exceeds 255 bytes");byte[]p=new byte[body.length+2];p[0]=(byte)(subtype>>>8);p[1]=(byte)subtype;System.arraycopy(body,0,p,2,body.length);return p;}
    static void send(ServerPacketWriter w,int subtype,Payload body)throws IOException{w.varByte(OPCODE,encode(subtype,body==null?null:body.bytes()));}
    static void send(ServerPacketWriter w,int subtype,byte[] body)throws IOException{w.varByte(OPCODE,encode(subtype,body));}
    private ApplicationPacket250Writer(){}
}
