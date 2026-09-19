package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Static explicit death-policy authority. Standard value/risk ordering remains server-owned and unresolved. */
final class DeathPolicyRepository {
    enum Kind { AUTO_KEEP_EXPLICIT, AUTO_LOSS_EXPLICIT, STANDARD_UNRESOLVED }
    static final class Policy { final int itemId; final String name; final Kind kind; Policy(int id,String name,Kind kind){this.itemId=id;this.name=name;this.kind=kind;} }
    private static final Map<Integer,Policy> MAP=load();
    private DeathPolicyRepository(){}
    static Policy get(int itemId){Policy p=MAP.get(itemId);return p==null?new Policy(itemId,"item_"+itemId,Kind.STANDARD_UNRESOLVED):p;}
    static int count(){return MAP.size();}
    static int count(Kind kind){int n=0;for(Policy p:MAP.values())if(p.kind==kind)n++;return n;}

    private static Map<Integer,Policy> load(){
        HashMap<Integer,Policy> out=new HashMap<>();
        try(InputStream in=DeathPolicyRepository.class.getResourceAsStream("/spk/local/data/death_policy_authority.tsv")){
            if(in==null)throw new IllegalStateException("missing death_policy_authority.tsv resource");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String header=br.readLine(); if(header==null)throw new IllegalStateException("empty death_policy_authority.tsv");
                String[] h=header.split("\\t",-1);int id=index(h,"item_id"),name=index(h,"name"),cls=index(h,"death_policy_class");
                String line;while((line=br.readLine())!=null){String[] p=line.split("\\t",-1);if(p.length<=Math.max(id,Math.max(name,cls)))continue;try{int item=Integer.parseInt(p[id]);String c=p[cls];Kind k="AUTO_KEEP_EXPLICIT".equals(c)?Kind.AUTO_KEEP_EXPLICIT:("AUTO_LOSS_EXPLICIT".equals(c)?Kind.AUTO_LOSS_EXPLICIT:Kind.STANDARD_UNRESOLVED);out.put(item,new Policy(item,p[name],k));}catch(NumberFormatException ignored){}}
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return Collections.unmodifiableMap(out);
    }
    private static int index(String[] h,String name){for(int i=0;i<h.length;i++)if(name.equals(h[i]))return i;throw new IllegalStateException("missing TSV column "+name);}
}
