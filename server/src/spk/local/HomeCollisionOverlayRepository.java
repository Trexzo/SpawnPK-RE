package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Collision consequences/provenance aligned one-for-one with the 53 V9.06 HOME scene mutations. */
final class HomeCollisionOverlayRepository {
    static final class Entry {
        final int seq,worldX,worldY,layer,shape,rotation,wireObjectId,sceneObjectId,sizeX,sizeY,effectiveWidth,effectiveHeight;
        final String operation,layerName,collisionClass,relation,certainty,provenance;
        final Boolean movementClip,projectileClip;
        Entry(String[] a){seq=pi(a[0]);operation=a[1];worldX=pi(a[2]);worldY=pi(a[3]);layer=pi(a[4]);layerName=a[5];shape=pi(a[6]);rotation=pi(a[7]);wireObjectId=pi(a[8]);sceneObjectId=pi(a[9]);collisionClass=a[10];movementClip=pbn(a[11]);projectileClip=pbn(a[12]);sizeX=pi(a[13]);sizeY=pi(a[14]);effectiveWidth=pi(a[15]);effectiveHeight=pi(a[16]);relation=a[17];certainty=a[18];provenance=a[19];}
        boolean isAdd(){return "ADD_OR_REPLACE".equals(operation);} boolean isRemove(){return "REMOVE".equals(operation);} boolean removeOnly(){return "REMOVE_ONLY_CLEAR".equals(relation);}
    }
    private static final List<Entry> ALL;
    static { try { ALL=Collections.unmodifiableList(load(resolveData("home_collision_overlay_plan_v906.tsv"))); } catch(IOException e){ throw new ExceptionInInitializerError(e); } }
    private HomeCollisionOverlayRepository(){}
    static List<Entry> all(){return ALL;} static int count(){return ALL.size();}
    static List<Entry> at(int x,int y){ArrayList<Entry> o=new ArrayList<>();for(Entry e:ALL)if(e.worldX==x&&e.worldY==y)o.add(e);return o;}
    static List<ClientCollisionDeltaCodec.Delta> addDeltas(Entry e){
        if(!e.isAdd())throw new IllegalArgumentException("not add"); if(!Boolean.TRUE.equals(e.movementClip))return Collections.emptyList();
        boolean p=Boolean.TRUE.equals(e.projectileClip);
        if("RECTANGLE".equals(e.collisionClass))return ClientCollisionDeltaCodec.rectangle(ClientCollisionDeltaCodec.Kind.ADD,e.worldX,e.worldY,e.sizeX,e.sizeY,e.rotation,p);
        if("WALL".equals(e.collisionClass)&&e.shape==0)return ClientCollisionDeltaCodec.straightWall(ClientCollisionDeltaCodec.Kind.ADD,e.worldX,e.worldY,e.rotation,p);
        throw new IllegalStateException("unsupported HOME add collision class "+e.collisionClass+" shape="+e.shape+" seq="+e.seq);
    }
    private static ArrayList<Entry> load(Path p)throws IOException{ArrayList<Entry> o=new ArrayList<>();try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){String h=r.readLine();if(h==null||!h.startsWith("seq\t"))throw new IOException("bad collision plan header");String l;int n=1;while((l=r.readLine())!=null){if(l.trim().isEmpty())continue;String[] a=l.split("\\t",-1);if(a.length<20)throw new IOException("bad collision plan row "+l);Entry e=new Entry(a);if(e.seq!=n++)throw new IOException("collision seq gap "+e.seq);o.add(e);}}return o;}
    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name); if(Files.isRegularFile(p))return p;
        p=Paths.get("data",name); if(Files.isRegularFile(p))return p;
        InputStream in=HomeCollisionOverlayRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){Path t=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");Files.copy(in,t,StandardCopyOption.REPLACE_EXISTING);in.close();t.toFile().deleteOnExit();return t;}
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){return Integer.parseInt(s.trim());} private static Boolean pbn(String s){s=s.trim();return "unknown".equals(s)?null:Boolean.valueOf(s);}
}
