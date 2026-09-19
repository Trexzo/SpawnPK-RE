package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Ordered production HOME object overlay recovered from V9.06 decoded
 * post-scene semantics. This repository deliberately does NOT encode packets
 * 101/151; V9.06 did not persist raw production payload bytes, so the exact
 * client byte transforms remain a separate certification task.
 */
final class HomeObjectOverlayRepository {
    enum Operation { REMOVE, ADD_OR_REPLACE }

    static final class Mutation {
        final int seq;
        final long timeMs;
        final int opcode,wireObjectId,sceneObjectId,plane,worldX,worldY,localX,localY,layer,shape,rotation,definitionAnimationId;
        final Operation operation;
        final String displayName,layerName,actions,provenance;

        Mutation(int seq,long timeMs,int opcode,Operation operation,int wireObjectId,int sceneObjectId,String displayName,
                 int plane,int worldX,int worldY,int localX,int localY,int layer,String layerName,int shape,int rotation,
                 int definitionAnimationId,String actions,String provenance){
            this.seq=seq;this.timeMs=timeMs;this.opcode=opcode;this.operation=operation;
            this.wireObjectId=wireObjectId;this.sceneObjectId=sceneObjectId;this.displayName=displayName;
            this.plane=plane;this.worldX=worldX;this.worldY=worldY;this.localX=localX;this.localY=localY;
            this.layer=layer;this.layerName=layerName;this.shape=shape;this.rotation=rotation;
            this.definitionAnimationId=definitionAnimationId;this.actions=actions;this.provenance=provenance;
        }
        boolean isRemove(){return operation==Operation.REMOVE;}
        boolean isAdd(){return operation==Operation.ADD_OR_REPLACE;}
        @Override public String toString(){
            return "HomeObjectMutation{#"+seq+" op="+opcode+" "+operation+" wire="+wireObjectId+" scene="+sceneObjectId+
                " "+displayName+" @"+worldX+","+worldY+" layer="+layerName+" shape="+shape+" rot="+rotation+"}";
        }
    }

    private static final List<Mutation> ALL;
    static {
        try { ALL=Collections.unmodifiableList(load(resolveData("home_object_overlay_v906.tsv"))); }
        catch(IOException e){ throw new ExceptionInInitializerError(e); }
    }
    private HomeObjectOverlayRepository(){}

    static List<Mutation> all(){ return ALL; }
    static int count(){return ALL.size();}
    static int addCount(){int n=0;for(Mutation m:ALL)if(m.isAdd())n++;return n;}
    static int removeCount(){int n=0;for(Mutation m:ALL)if(m.isRemove())n++;return n;}
    static List<Mutation> at(int x,int y){
        ArrayList<Mutation> out=new ArrayList<>();
        for(Mutation m:ALL)if(m.worldX==x&&m.worldY==y)out.add(m);
        return out;
    }
    static boolean hasRemoveOnly(int x,int y){
        List<Mutation> a=at(x,y); if(a.isEmpty())return false;
        boolean remove=false,add=false;for(Mutation m:a){remove|=m.isRemove();add|=m.isAdd();}
        return remove&&!add;
    }

    private static ArrayList<Mutation> load(Path p)throws IOException{
        ArrayList<Mutation> out=new ArrayList<>();
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String header=r.readLine();
            if(header==null || !header.startsWith("seq\t")) throw new IOException("bad HOME object header");
            String line; int expected=1;
            while((line=r.readLine())!=null){
                if(line.trim().isEmpty())continue;
                String[] a=line.split("\\t",-1);
                if(a.length<19)throw new IOException("bad HOME object row: "+line);
                int seq=pi(a[0]); if(seq!=expected++)throw new IOException("non-contiguous object overlay seq "+seq);
                Operation op=Operation.valueOf(a[3]);
                int opcode=pi(a[2]);
                if(opcode!=101 && opcode!=151)throw new IOException("unexpected scene opcode "+opcode);
                Mutation m=new Mutation(seq,Long.parseLong(a[1]),opcode,op,pi(a[4]),pi(a[5]),a[6],
                    pi(a[7]),pi(a[8]),pi(a[9]),pi(a[10]),pi(a[11]),pi(a[12]),a[13],pi(a[14]),pi(a[15]),pi(a[16]),a[17],a[18]);
                if(m.isRemove() && (m.wireObjectId!=-1 || m.sceneObjectId!=-1)) throw new IOException("remove IDs must be -1 at seq "+seq);
                if(m.isAdd() && m.sceneObjectId!=(m.wireObjectId&0x7fff)) throw new IOException("15-bit alias mismatch seq "+seq);
                out.add(m);
            }
        }
        return out;
    }
    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name); if(Files.isRegularFile(p))return p;
        p=Paths.get("data",name); if(Files.isRegularFile(p))return p;
        InputStream in=HomeObjectOverlayRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){Path t=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");Files.copy(in,t,StandardCopyOption.REPLACE_EXISTING);in.close();t.toFile().deleteOnExit();return t;}
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){return Integer.parseInt(s.trim());}
}
