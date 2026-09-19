package spk.dev;

import java.io.InputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/**
 * LocalLab-only keyboard bridge for server-owned classic choice dialogs.
 * Numeric keys 1..9 select the corresponding ordered widget published by the
 * local server.  The original client's key handling still runs unchanged.
 */
public final class DialogNumberKeys {
    private static volatile Properties props=new Properties();
    private static volatile long seenMtime=Long.MIN_VALUE;
    private static volatile long nextCheck;
    private static volatile Path file;
    private static volatile int lastKey=-1;
    private static volatile long lastSend;

    private static final Field CLIENT_WRITER;
    private static final Method WRITE_OPCODE;
    private static final Method WRITE_WIDGET;

    static {
        try {
            Class<?> client=Class.forName("rs.Client");
            Field f=client.getDeclaredField("fv"); f.setAccessible(true); CLIENT_WRITER=f;
            Class<?> writer=Class.forName("rs.x.e");
            Method a=writer.getDeclaredMethod("a",int.class); a.setAccessible(true); WRITE_OPCODE=a;
            Method d=writer.getDeclaredMethod("d",int.class); d.setAccessible(true); WRITE_WIDGET=d;
        } catch(Exception e){ throw new ExceptionInInitializerError(e); }
    }

    private DialogNumberKeys(){}

    /** Called once for each key code returned by the exact client's w(-796). */
    public static void observe(int key){
        try{
            if(key<49||key>57)return; // exact client key stream uses ASCII for digits
            Properties p=current();
            if(!"true".equalsIgnoreCase(p.getProperty("active","false")))return;
            String raw=p.getProperty("widgets","").trim(); if(raw.isEmpty())return;
            String[] parts=raw.split(","); int idx=key-49; if(idx<0||idx>=parts.length)return;
            int widget=Integer.parseInt(parts[idx].trim()); if(widget<=0)return;
            long now=System.currentTimeMillis();
            if(key==lastKey && now-lastSend<160L)return; // suppress key-repeat duplicate packet
            Object writer=CLIENT_WRITER.get(null); if(writer==null)return;
            WRITE_OPCODE.invoke(writer,Integer.valueOf(185));
            WRITE_WIDGET.invoke(writer,Integer.valueOf(widget));
            lastKey=key; lastSend=now;
            System.out.println("LOCALLAB_DIALOG_NUMBER_KEY key="+(char)key+" rank="+(idx+1)+" widget="+widget+" opcode=185");
        }catch(Throwable ignored){}
    }

    private static Properties current(){
        long now=System.currentTimeMillis(); if(now<nextCheck)return props; nextCheck=now+35L;
        try{
            Path f=resolveFile(); long mt=Files.isRegularFile(f)?Files.getLastModifiedTime(f).toMillis():-1L;
            if(mt!=seenMtime){Properties n=new Properties();if(mt>=0)try(InputStream in=Files.newInputStream(f)){n.load(in);}props=n;seenMtime=mt;}
        }catch(Throwable ignored){}
        return props;
    }

    private static Path resolveFile() throws Exception{
        Path f=file;if(f!=null)return f;
        URI uri=DialogNumberKeys.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path code=Paths.get(uri).toAbsolutePath().normalize();
        Path parent=Files.isDirectory(code)?code:code.getParent();
        Path lab=parent!=null?parent.getParent():null;
        if(lab!=null)f=lab.resolve("server").resolve("data").resolve("locallab_dialog_keys.properties");
        else f=Paths.get("server","data","locallab_dialog_keys.properties").toAbsolutePath();
        file=f.normalize();return file;
    }
}
