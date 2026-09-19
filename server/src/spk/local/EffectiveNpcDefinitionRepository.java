package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact-client NPC action definition corpus; production-effective override layer can be added later. */
final class EffectiveNpcDefinitionRepository {
    static final class Def {
        final int npcId; final String name; final String[] actions=new String[5]; final String[] normalized=new String[5];
        Def(int id,String n){npcId=id;name=n;}
        String action(int option){return option>=1&&option<=5?actions[option-1]:null;}
        String normalized(int option){return option>=1&&option<=5?normalized[option-1]:null;}
    }
    private static final Map<Integer,Def> BY_ID=load();
    static Def get(int id){return BY_ID.get(id);}
    static int count(){return BY_ID.size();}
    private static Map<Integer,Def> load(){
        LinkedHashMap<Integer,Def> out=new LinkedHashMap<>();
        InputStream in=EffectiveNpcDefinitionRepository.class.getResourceAsStream("/spk/local/npc_client_interaction_resolver.tsv");
        if(in==null) return out;
        try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String header=br.readLine(); if(header==null)return out; String[] h=header.split("\\t",-1); Map<String,Integer> ix=new HashMap<>();
            for(int i=0;i<h.length;i++)ix.put(h[i],i);
            String line; while((line=br.readLine())!=null){
                String[] c=line.split("\\t",-1); int id=parse(cell(c,ix.get("npcId")),-1); if(id<0)continue;
                Def d=new Def(id,cell(c,ix.get("name"))); for(int o=1;o<=5;o++){
                    d.actions[o-1]=blankToNull(cell(c,ix.get("action"+o)));
                    d.normalized[o-1]=blankToNull(cell(c,ix.get("action"+o+"Normalized")));
                } out.put(id,d);
            }
        }catch(Exception e){throw new ExceptionInInitializerError(e);} return out;
    }
    private static String cell(String[] c,Integer i){return i==null||i<0||i>=c.length?"":c[i];}
    private static String blankToNull(String s){return s==null||s.trim().isEmpty()?null:s.trim();}
    private static int parse(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
}
