package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact-current item semantic authority assembled from the recovered 30k item
 * census plus Equipment Stats Static Recovery R2. Numerical 14-field equipment
 * profiles are intentionally absent: the exact client proves those are
 * server-fed through equipstr/key24 and no complete static profile corpus was
 * recovered.
 */
final class ItemAuthorityRepository {
    static final class Entry {
        final int itemId;
        final String name;
        final boolean wield;
        final boolean wear;
        final String effectText;
        final String inventoryActions;
        final String groundActions;
        final String policySummary;
        final String relationSummary;
        final String mechanicsSummary;
        final String nameProvenance;
        final String effectProvenance;
        Entry(int itemId,String name,boolean wield,boolean wear,String effectText,String inventoryActions,String groundActions,
              String policySummary,String relationSummary,String mechanicsSummary,String nameProvenance,String effectProvenance){
            this.itemId=itemId;this.name=name;this.wield=wield;this.wear=wear;this.effectText=effectText;
            this.inventoryActions=inventoryActions;this.groundActions=groundActions;this.policySummary=policySummary;
            this.relationSummary=relationSummary;this.mechanicsSummary=mechanicsSummary;
            this.nameProvenance=nameProvenance;this.effectProvenance=effectProvenance;
        }
        boolean equippable(){return wield||wear;}
        public String toString(){return itemId+":"+name+" wield="+wield+" wear="+wear;}
    }

    private static final LinkedHashMap<Integer,Entry> BY_ID=load();
    private static final HashMap<String,Entry> BY_NAME=buildNames();

    static int count(){return BY_ID.size();}
    static Entry get(int id){return BY_ID.get(id);}
    static Collection<Entry> all(){return Collections.unmodifiableCollection(BY_ID.values());}
    static Entry byExactName(String name){if(name==null)return null;return BY_NAME.get(normalizeName(name));}
    static int withEffectText(){int n=0;for(Entry e:BY_ID.values())if(!blank(e.effectText))n++;return n;}
    static int withStaticMechanics(){int n=0;for(Entry e:BY_ID.values())if(!blank(e.mechanicsSummary))n++;return n;}
    static int equippableCount(){int n=0;for(Entry e:BY_ID.values())if(e.equippable())n++;return n;}

    private static LinkedHashMap<Integer,Entry> load(){
        LinkedHashMap<Integer,Entry> out=new LinkedHashMap<>();
        try(InputStream in=ItemAuthorityRepository.class.getResourceAsStream("/spk/local/data/item_authority_r5.tsv")){
            if(in==null)throw new IllegalStateException("missing item_authority_r5.tsv");
            try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                String header=br.readLine(); if(header==null||!header.startsWith("item_id\t"))throw new IllegalStateException("bad item authority header");
                for(String line;(line=br.readLine())!=null;){
                    String[] p=line.split("\t",-1); if(p.length<12)throw new IllegalStateException("bad item authority row fields="+p.length);
                    int id=Integer.parseInt(p[0]);
                    Entry e=new Entry(id,unesc(p[1]),"1".equals(p[2]),"1".equals(p[3]),unesc(p[4]),unesc(p[5]),unesc(p[6]),
                        unesc(p[7]),unesc(p[8]),unesc(p[9]),unesc(p[10]),unesc(p[11]));
                    if(out.put(id,e)!=null)throw new IllegalStateException("duplicate item authority id="+id);
                }
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return out;
    }
    private static HashMap<String,Entry> buildNames(){
        HashMap<String,Entry> out=new HashMap<>();
        for(Entry e:BY_ID.values()){
            String k=normalizeName(e.name); if(!k.isEmpty())out.putIfAbsent(k,e);
            String stripped=normalizeName(stripTags(e.name)); if(!stripped.isEmpty())out.putIfAbsent(stripped,e);
        }
        return out;
    }
    private static String normalizeName(String s){return stripTags(s).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    static String stripTags(String s){
        if(s==null)return "";
        String x=s.replaceAll("@[A-Za-z0-9]{3}@","");
        x=x.replaceAll("<[^>]+>","");
        return x;
    }
    private static boolean blank(String s){return s==null||s.trim().isEmpty();}
    private static String unesc(String s){
        StringBuilder b=new StringBuilder(s.length()); boolean slash=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(slash){if(c=='n')b.append('\n');else if(c=='t')b.append('\t');else b.append(c);slash=false;}
            else if(c=='\\')slash=true;else b.append(c);
        }
        if(slash)b.append('\\'); return b.toString();
    }
    private ItemAuthorityRepository(){}
}
