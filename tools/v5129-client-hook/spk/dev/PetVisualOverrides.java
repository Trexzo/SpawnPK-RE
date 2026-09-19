package spk.dev;

import java.io.InputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** LocalLab-only draw-time pet visual experiments. Not production server authority. */
public final class PetVisualOverrides {
    private static volatile Properties props=new Properties();
    private static volatile long seenMtime=Long.MIN_VALUE;
    private static volatile long nextCheck;
    private static volatile Path file;
    private static final ThreadLocal<State> STATE=new ThreadLocal<State>();
    private static volatile long nextCompatCheck;
    private static volatile boolean compat24019Logged;

    private static final Class<?> NPC;
    private static final Class<?> MODEL;
    private static final Field NPC_TARGET;
    private static final Field CLIENT_LOCAL_INDEX;
    private static final Field MODEL_FACE_COUNT;
    private static final Field MODEL_ALPHA;
    private static final Field MODEL_AI;
    private static final Field MODEL_TINT;
    private static final Method RGB_TO_TINT;

    static {
        try {
            NPC=Class.forName("rs.a.j");
            MODEL=Class.forName("rs.a.h");
            NPC_TARGET=NPC.getField("m");
            CLIENT_LOCAL_INDEX=Class.forName("rs.Client").getField("di");
            MODEL_FACE_COUNT=MODEL.getField("ah");
            MODEL_ALPHA=MODEL.getField("aq");
            MODEL_AI=MODEL.getField("aI");
            MODEL_TINT=MODEL.getField("H");
            RGB_TO_TINT=Class.forName("rs.l.f").getMethod("a",int.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private PetVisualOverrides(){}

    public static void beforeDraw(Object actor,Object model){
        try {
            ensureLocalLabCompatibilityFixes();
            if(!NPC.isInstance(actor)||!MODEL.isInstance(model))return;
            int target=NPC_TARGET.getInt(actor), local=CLIENT_LOCAL_INDEX.getInt(null);
            if(target<32768 || target-32768!=local)return;
            Properties p=current(); if(!"true".equalsIgnoreCase(p.getProperty("enabled","false")))return;
            State st=new State(model); boolean touched=false;

            String alpha=p.getProperty("alpha");
            if(alpha!=null){
                int[] arr=(int[])MODEL_ALPHA.get(model);
                if(arr!=null){int v=Integer.parseInt(alpha);if(v>=0&&v<=255){st.alpha=arr.clone();int n=Math.min(MODEL_FACE_COUNT.getInt(model),arr.length);for(int i=0;i<n;i++)arr[i]=v;touched=true;}}
            }
            String ai=p.getProperty("ai");
            if(ai!=null){MODEL_AI.setInt(model,Integer.parseInt(ai));touched=true;}

            String cycle=p.getProperty("bodycycle");
            String tint=p.getProperty("tint");
            if(cycle!=null){
                if("off".equalsIgnoreCase(cycle)){MODEL_TINT.setInt(null,1);touched=true;}
                else {int phase=Integer.parseInt(cycle);if(phase>=0&&phase<=66){MODEL_TINT.setInt(null,toTint(255+131072*phase));touched=true;}}
            }
            if(tint!=null){
                if("off".equalsIgnoreCase(tint)){MODEL_TINT.setInt(null,1);touched=true;}
                else if(tint.startsWith("raw:")){MODEL_TINT.setInt(null,Integer.parseInt(tint.substring(4)));touched=true;}
                else if(tint.startsWith("rgb:")){MODEL_TINT.setInt(null,toTint(Integer.parseInt(tint.substring(4),16)));touched=true;}
            }
            if(touched)STATE.set(st);
        } catch (Throwable ignored) {}
    }

    /**
     * LocalLab compatibility extension requested by the user: make the confirmed
     * green/black Cursed Behemoth item (24019) participate in the same inventory
     * Switch-color surface as 24016..24018. The exact current client definition
     * intentionally omits this action, so this is explicitly local-only and does
     * not alter the pinned evidence JAR.
     */
    private static void ensureLocalLabCompatibilityFixes(){
        long now=System.currentTimeMillis();
        if(now<nextCompatCheck)return;
        nextCompatCheck=now+1000L;
        try{
            Class<?> item=Class.forName("rs.d.k");
            Method lookup=item.getMethod("f",int.class);
            Field actionsField=item.getField("L");
            Object def=lookup.invoke(null,Integer.valueOf(24019));
            if(def==null)return;
            String[] actions=(String[])actionsField.get(def);
            if(actions==null || actions.length<5){
                String[] grown=new String[5];
                if(actions!=null)System.arraycopy(actions,0,grown,0,Math.min(actions.length,grown.length));
                actions=grown;
                actionsField.set(def,actions);
            }
            if(!"Switch-color".equals(actions[3]))actions[3]="Switch-color";
            if(!compat24019Logged){
                compat24019Logged=true;
                System.out.println("LOCALLAB_CLIENT_24019_SWITCH_COLOR_READY slot=4 item=24019 localCompatibilityExtension=true");
            }
        }catch(Throwable ignored){
            // Item repositories are initialized during client bootstrap. Keep retrying
            // once per second rather than turning a startup race into a client crash.
        }
    }

    public static void afterDraw(Object model){
        State st=STATE.get(); if(st==null)return; STATE.remove();
        if(model!=st.model)return;
        try {
            MODEL_AI.setInt(model,st.ai);
            MODEL_TINT.setInt(null,st.tint);
            if(st.alpha!=null){int[] arr=(int[])MODEL_ALPHA.get(model);if(arr!=null)System.arraycopy(st.alpha,0,arr,0,Math.min(st.alpha.length,arr.length));}
        } catch (Throwable ignored) {}
    }

    private static int toTint(int rgb) throws Exception { return ((Integer)RGB_TO_TINT.invoke(null,Integer.valueOf(rgb))).intValue(); }

    private static Properties current(){
        long now=System.currentTimeMillis(); if(now<nextCheck)return props; nextCheck=now+50L;
        try{
            Path f=resolveFile(); long mt=Files.isRegularFile(f)?Files.getLastModifiedTime(f).toMillis():-1L;
            if(mt!=seenMtime){Properties n=new Properties();if(mt>=0)try(InputStream in=Files.newInputStream(f)){n.load(in);}props=n;seenMtime=mt;}
        }catch(Throwable ignored){}
        return props;
    }

    private static Path resolveFile() throws Exception{
        Path f=file;if(f!=null)return f;
        URI uri=PetVisualOverrides.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path code=Paths.get(uri).toAbsolutePath().normalize();
        Path parent=Files.isDirectory(code)?code:code.getParent();
        Path lab=parent!=null?parent.getParent():null;
        if(lab!=null)f=lab.resolve("server").resolve("data").resolve("dev_pet_visual.properties");
        else f=Paths.get("server","data","dev_pet_visual.properties").toAbsolutePath();
        file=f.normalize();return file;
    }

    private static final class State{
        final Object model; final int ai; final int tint; int[] alpha;
        State(Object m) throws Exception {model=m;ai=MODEL_AI.getInt(m);tint=MODEL_TINT.getInt(null);}
    }
}
