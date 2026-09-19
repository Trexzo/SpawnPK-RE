package spk.local;

import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Exact-current item-definition exceptions where inventory option5 is NOT the default Drop. */
final class InventoryOption5Repository {
    private static final Map<Integer,String> ACTION=load();
    static String explicitAction(int itemId){return ACTION.get(itemId);}
    static int count(){return ACTION.size();}
    private static Map<Integer,String> load(){
        HashMap<Integer,String> m=new HashMap<>();InputStream in=InventoryOption5Repository.class.getResourceAsStream("/spk/local/opcode87_non_drop_items.csv");if(in==null)return m;
        try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line=br.readLine();while((line=br.readLine())!=null){
            // First three columns contain no quoted commas in this generated corpus: item_id,name,action_index_4,...
            int a=line.indexOf(','); if(a<0)continue; int b=line.indexOf(',',a+1); if(b<0)continue; int c=line.indexOf(',',b+1); if(c<0)c=line.length();
            try{int id=Integer.parseInt(line.substring(0,a).replace("\ufeff","").trim());String act=line.substring(b+1,c).replace("\"","").trim();if(!act.isEmpty())m.put(id,act);}catch(Exception ignored){}
        }}catch(IOException e){throw new ExceptionInInitializerError(e);}return m;
    }
}
