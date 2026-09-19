package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named high-value HOME landmarks from the V9.06 final scene / native menus. */
final class HomeLandmarkRepository {
    static final class Landmark {
        final String key,name,layer,actions,source;
        final int wireObjectId,sceneObjectId,plane,worldX,worldY,shape,rotation,definitionAnimationId;
        Landmark(String key,int wireObjectId,int sceneObjectId,String name,int plane,int worldX,int worldY,String layer,
                 int shape,int rotation,int definitionAnimationId,String actions,String source){
            this.key=key;this.wireObjectId=wireObjectId;this.sceneObjectId=sceneObjectId;this.name=name;
            this.plane=plane;this.worldX=worldX;this.worldY=worldY;this.layer=layer;this.shape=shape;this.rotation=rotation;
            this.definitionAnimationId=definitionAnimationId;this.actions=actions;this.source=source;
        }
    }
    private static final List<Landmark> ALL;
    private static final Map<String,Landmark> BY_KEY;
    static{
        try{
            ArrayList<Landmark>a=load(resolveData("home_landmarks_v906.tsv"));ALL=Collections.unmodifiableList(a);
            LinkedHashMap<String,Landmark>m=new LinkedHashMap<>();for(Landmark x:a)if(m.put(x.key,x)!=null)throw new IOException("duplicate landmark "+x.key);
            BY_KEY=Collections.unmodifiableMap(m);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private HomeLandmarkRepository(){}
    static List<Landmark> all(){return ALL;}
    static Landmark get(String key){return BY_KEY.get(key);}
    static int count(){return ALL.size();}
    private static ArrayList<Landmark>load(Path p)throws IOException{
        ArrayList<Landmark>o=new ArrayList<>();
        try(BufferedReader r=Files.newBufferedReader(p,StandardCharsets.UTF_8)){
            String h=r.readLine();if(h==null||!h.startsWith("key\t"))throw new IOException("bad landmark header");
            String s;while((s=r.readLine())!=null){if(s.trim().isEmpty())continue;String[]a=s.split("\\t",-1);if(a.length<13)throw new IOException("bad landmark row "+s);
                o.add(new Landmark(a[0],pi(a[1]),pi(a[2]),a[3],pi(a[4]),pi(a[5]),pi(a[6]),a[7],pi(a[8]),pi(a[9]),pi(a[10]),a[11],a[12]));}
        }return o;
    }
    private static Path resolveData(String name)throws IOException{
        Path p=Paths.get("server","data",name);if(Files.isRegularFile(p))return p;p=Paths.get("data",name);if(Files.isRegularFile(p))return p;
        InputStream in=HomeLandmarkRepository.class.getResourceAsStream("/spk/local/"+name);if(in!=null){Path t=Files.createTempFile("spk-"+name.replace('.','-'),".tmp");Files.copy(in,t,StandardCopyOption.REPLACE_EXISTING);in.close();t.toFile().deleteOnExit();return t;}
        throw new FileNotFoundException(name);
    }
    private static int pi(String s){return Integer.parseInt(s.trim());}
}
