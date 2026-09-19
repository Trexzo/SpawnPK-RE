package spk.local;
import java.util.*;
public final class EngineR84Packet250WriterTest{
 public static void main(String[]a){
  byte[] body=ApplicationPacket250Writer.payload().u8(7).u16(0x1234).i32(0x10203040).i64(0x0102030405060708L).stringNl("abc").bytes();
  byte[] p=ApplicationPacket250Writer.encode(31,body);
  if(p.length!=2+1+2+4+8+4)throw new AssertionError("len="+p.length);
  if((p[0]&255)!=0||(p[1]&255)!=31||(p[2]&255)!=7||(p[3]&255)!=0x12||(p[4]&255)!=0x34)throw new AssertionError(Arrays.toString(p));
  if((p[p.length-4]&255)!='a'||(p[p.length-3]&255)!='b'||(p[p.length-2]&255)!='c'||(p[p.length-1]&255)!=10)throw new AssertionError("string_nl");
  boolean over=false;try{ApplicationPacket250Writer.encode(1,new byte[254]);}catch(IllegalArgumentException e){over=true;}if(!over)throw new AssertionError("varbyte guard");
  System.out.println("V5184_ENGINE_R84_PACKET250_WRITER_PASS opcode=250 framing=VAR_BYTE subtype=u16_be primitives=u8,u16,i32,i64,string_nl maxPayload=255");
 }
}
