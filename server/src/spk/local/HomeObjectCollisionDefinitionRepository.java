package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exact V9.06 current-client object-definition fields that control collision for HOME dynamic additions. */
final class HomeObjectCollisionDefinitionRepository {
    static final class Def {
        final int sceneObjectId,sizeX,sizeY,definitionAnimationId;
        final String displayName,provenance;
        final boolean movementClipFlagAj,projectileClipFlagAb,floorDecorationQualifierAr;
        Def(int id,String n,int sx,int sy,boolean aj,boolean ab,boolean ar,int anim,String p){sceneObjectId=id;displayName=n;sizeX=sx;sizeY=sy;movementClipFlagAj=aj;projectileClipFlagAb=ab;floorDecorationQualifierAr=ar;definitionAnimationId=anim;provenance=p;}
    }
    private static final Map<Integer,Def> BY_ID;
    static { try { BY_ID=Collections.unmodifiableMap(load(resolveData("home_object_collision_defs_v906.tsv"))); } catch(IOException e){ throw new ExceptionInInitializerError(e); } }
    private HomeObjectCollisionDefinitionRepository(){}
    static Def get(int sceneObjectId){ return BY_ID.get(sceneObjectId); }
    static Collection<Def> all(){ return BY_ID.values(); }
    static int count(){ return BY_ID.size(); }

    private static LinkedHashMap<Integer,Def> load(Path p)throws IOException{
        LinkedHashMap<Integer,Def> out=new LinkedHashMap<>();
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String h=r.readLine(); if(h==null||!h.startsWith("sceneObjectId\t"))throw new IOException("bad collision defs header");
            String line; while((line=r.readLine())!=null){ if(line.trim().isEmpty())continue; String[] a=line.split("\\t",-1); if(a.length<9)throw new IOException("bad collision def row "+line);
                Def d=new Def(pi(a[0]),a[1],pi(a[2]),pi(a[3]),pb(a[4]),pb(a[5]),pb(a[6]),pi(a[7]),a[8]);
                if(out.put(d.sceneObjectId,d)!=null)throw new IOException("duplicate object collision def "+d.sceneObjectId);
            }
        } return out;
    }
    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name); if(Files.isRegularFile(p))return p;
        p=Paths.get("data",name); if(Files.isRegularFile(p))return p;
        InputStream in=HomeObjectCollisionDefinitionRepository.class.getResourceAsStream("/spk/local/"+name);
        if(in!=null){Path t=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");Files.copy(in,t,StandardCopyOption.REPLACE_EXISTING);in.close();t.toFile().deleteOnExit();return t;}
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){return Integer.parseInt(s.trim());} private static boolean pb(String s){return Boolean.parseBoolean(s.trim());}
}
