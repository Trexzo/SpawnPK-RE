package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact-current i.bin policy evidence imported from the offline authority corpus. */
final class ItemPolicyRepository {
    private static final Map<Integer,Boolean> EXPLICIT_TRADEABLE = loadTradeable();
    private ItemPolicyRepository(){}

    static Boolean explicitTradeable(int itemId){ return EXPLICIT_TRADEABLE.get(itemId); }
    static boolean explicitlyUntradeable(int itemId){ return Boolean.FALSE.equals(EXPLICIT_TRADEABLE.get(itemId)); }

    private static Map<Integer,Boolean> loadTradeable(){
        HashMap<Integer,Boolean> out=new HashMap<>();
        try(InputStream in=ItemPolicyRepository.class.getResourceAsStream("/spk/local/data/item_policies.tsv")){
            if(in==null)throw new IllegalStateException("missing item_policies.tsv resource");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String header=br.readLine(); if(header==null)throw new IllegalStateException("empty item_policies.tsv");
                String[] h=header.split("\\t",-1); int idCol=index(h,"item_id"),tradeCol=index(h,"tradeable_explicit");
                String line; while((line=br.readLine())!=null){
                    String[] p=line.split("\\t",-1); if(p.length<=Math.max(idCol,tradeCol))continue;
                    String v=p[tradeCol].trim(); if(v.isEmpty())continue;
                    try{out.put(Integer.parseInt(p[idCol].trim()),Boolean.valueOf(v));}catch(NumberFormatException ignored){}
                }
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return Collections.unmodifiableMap(out);
    }
    private static int index(String[] h,String name){for(int i=0;i<h.length;i++)if(name.equals(h[i]))return i;throw new IllegalStateException("missing TSV column "+name);}
}
