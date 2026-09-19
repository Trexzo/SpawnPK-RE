package spk.local;
import java.io.*;
public final class ApplicationUiFixtureServiceTest{
 public static void main(String[]a)throws Exception{
  String[] names={"mailbox","itemlist","makex","eventtask","events","shop","raid","confirm","infobox","boss","metrics","selection","toast","attention","chapter","effects","progress"};
  int total=0;for(String n:names){ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{3,5,7,11}));String r=ApplicationUiFixtureService.run(n,w);if(!r.contains("LOCAL_DEV_FIXTURE"))throw new AssertionError(n+" result="+r);if(out.size()==0)throw new AssertionError(n+" no packets");total+=out.size();}
  System.out.println("V5185_APPLICATION_UI_FIXTURES_PASS fixtures="+names.length+" bytes="+total+" productionDataInvented=false");
 }
}
