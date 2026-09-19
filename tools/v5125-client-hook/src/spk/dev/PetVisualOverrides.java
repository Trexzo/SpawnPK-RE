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

    private static final Class<?> NPC;
    private static final Class<?> MODEL;
    private static final Field NPC_TARGET;
    private static final Field NPC_DEF;
    private static final Field DEF_ID;
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
            NPC_DEF=NPC.getField("aG");
            DEF_ID=Class.forName("rs.d.d").getField("x");
            CLIENT_LOCAL_INDEX=Class.forName("rs.Client").getField("di");
            MODEL_FACE_COUNT=MODEL.getField("ah");
            MODEL_ALPHA=MODEL.getField("aq");
            MODEL_AI=MODEL.getField("aI");
            MODEL_TINT=MODEL.getField("H");
            RGB_TO_TINT=Class.forName("rs.l.f").getMethod("a",int.class);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private PetVisualOverrides(){}

    /**
     * Called immediately before model draw. LocalLab parity defaults are applied
     * even when no developer override file exists:
     *   - Yoshiganger 1334: green body tint #00FF00 (live-certified by user)
     *   - Wondrous Doppel 8210: retain native client-driven blue/violet cycle
     *
     * Explicit dev values then override those defaults for live experiments.
     */
    public static void beforeDraw(Object actor,Object model){
        try {
            if(!NPC.isInstance(actor)||!MODEL.isInstance(model))return;
            int target=NPC_TARGET.getInt(actor), local=CLIENT_LOCAL_INDEX.getInt(null);
            if(target<32768 || target-32768!=local)return;
            long def=definitionId(actor);
            Properties p=current();
            State st=new State(model); boolean touched=false;

            // Live LocalLab certification: this is the intended Yoshiganger body,
            // distinct from the accessory/particle layer suppressed separately.
            if(def==1334L){
                MODEL_TINT.setInt(null,toTint(0x00ff00));
                touched=true;
            }

            boolean explicit="true".equalsIgnoreCase(p.getProperty("enabled","false"));
            if(explicit){
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
            }
            if(touched)STATE.set(st);
        } catch (Throwable ignored) {}
    }

    /**
     * Gate for the renderer's built-in selector-3 injection. LocalLab's corrected
     * default suppresses the accessory-like layer for Yoshiganger and Wondrous
     * Doppel while leaving explicit server particle selectors untouched.
     *
     * ::devpet visual intrinsicfx on/off/auto can override this. "auto" means the
     * LocalLab per-pet default below; "on" restores the stock client intrinsic layer.
     */
    public static boolean allowIntrinsicSpecialFx(Object actor){
        try{
            long def=definitionId(actor);
            Properties p=current();
            String v=p.getProperty("intrinsicfx");
            if(v!=null){
                if("on".equalsIgnoreCase(v)||"true".equalsIgnoreCase(v)||"native".equalsIgnoreCase(v))return true;
                if("off".equalsIgnoreCase(v)||"false".equalsIgnoreCase(v))return false;
            }
            return def!=1334L && def!=8210L;
        }catch(Throwable ignored){return true;}
    }

    public static int defaultBodyTintRgb(long definitionId){
        return definitionId==1334L?0x00ff00:-1;
    }

    public static boolean defaultIntrinsicFxEnabled(long definitionId){
        return definitionId!=1334L && definitionId!=8210L;
    }

    private static long definitionId(Object actor) throws Exception{
        Object def=NPC_DEF.get(actor);
        return def==null?-1L:DEF_ID.getLong(def);
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
