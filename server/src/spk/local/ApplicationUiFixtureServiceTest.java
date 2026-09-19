package spk.local;
import java.io.*;
public final class ApplicationUiFixtureServiceTest{
 private static final int[] SEED={3,5,7,11};
 public static void main(String[]a)throws Exception{
  String[] names={"mailbox","itemlist","makex","eventtask","events","shop","raid","confirm","infobox","boss","metrics","selection","toast","attention","chapter","effects","progress"};
  int total=0;for(String n:names){ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));String r=ApplicationUiFixtureService.run(n,w);if(!r.contains("LOCAL_DEV_FIXTURE"))throw new AssertionError(n+" result="+r);if(out.size()==0)throw new AssertionError(n+" no packets");total+=out.size();}
  verifyMakeXFraming();
  System.out.println("V5185_APPLICATION_UI_FIXTURES_PASS fixtures="+names.length+" bytes="+total+" makeXNativeRoot="+ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT+" makeXFraming=true productionDataInvented=false");
 }

 private static void verifyMakeXFraming()throws Exception{
  if(!ApplicationUiFixtureService.isKnownMakeXRoot(ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT))
   throw new AssertionError("Make-X fixture root is not in exact-current native root family: "+ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT);
  if(ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT==55300)
   throw new AssertionError("regression: invalid historical Make-X fixture root 55300");

  ByteArrayOutputStream out=new ByteArrayOutputStream();
  ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(SEED.clone()));
  String result=ApplicationUiFixtureService.run("makex",w);
  if(!result.contains("layoutAuthority=CUSTOM_LOCALLAB"))throw new AssertionError("authority label lost: "+result);

  byte[] wire=out.toByteArray();
  IsaacCipher decode=new IsaacCipher(SEED.clone());
  int p=0,frame=0,appFrames=0;
  while(p<wire.length){
   int opcode=((wire[p++]&255)-decode.nextInt())&255;
   if(frame==0){
    if(opcode!=97)throw new AssertionError("Make-X first opcode expected 97 got "+opcode);
    if(p+2>wire.length)throw new AssertionError("truncated packet97");
    int root=((wire[p]&255)<<8)|(wire[p+1]&255);p+=2;
    if(root!=ApplicationUiFixtureService.MAKE_X_FIXTURE_ROOT)throw new AssertionError("Make-X root="+root);
    if(!ApplicationUiFixtureService.isKnownMakeXRoot(root))throw new AssertionError("Make-X non-native root="+root);
   }else{
    if(opcode!=ApplicationPacket250Writer.OPCODE)throw new AssertionError("Make-X frame "+frame+" expected opcode250 got "+opcode);
    if(p>=wire.length)throw new AssertionError("truncated opcode250 length");
    int len=wire[p++]&255;
    if(len<3||p+len>wire.length)throw new AssertionError("bad opcode250 len="+len+" remaining="+(wire.length-p));
    int subtype=((wire[p]&255)<<8)|(wire[p+1]&255);
    int op=wire[p+2]&255;
    if(subtype!=35)throw new AssertionError("Make-X subtype="+subtype);
    if(op<0||op>4)throw new AssertionError("Make-X operation="+op);
    p+=len;appFrames++;
   }
   frame++;
  }
  if(frame!=7||appFrames!=6)throw new AssertionError("Make-X frame count total="+frame+" subtype35="+appFrames);
 }
}
