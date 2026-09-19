package spk.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Localhost-only bridge for render-time client experiments. Values in this file
 * are NOT SpawnPK server packet authority. The patched exact client reads them
 * only for the local player's owned pet and applies them immediately before draw.
 */
final class LocalDevVisualOverrideStore {
    private static final String FILE_NAME="dev_pet_visual.properties";
    private LocalDevVisualOverrideStore(){}

    static Path file(){
        String explicit=System.getProperty("spk.local.devVisualFile","").trim();
        if(!explicit.isEmpty()) return Paths.get(explicit).toAbsolutePath().normalize();
        Path serverData=Paths.get("server","data");
        Path base=Files.isDirectory(serverData)?serverData:Paths.get("data");
        return base.resolve(FILE_NAME).toAbsolutePath().normalize();
    }

    static synchronized String set(String key,String value){
        try{
            Properties p=load();
            if(value==null||value.equalsIgnoreCase("auto"))p.remove(key); else p.setProperty(key,value);
            p.setProperty("enabled","true");
            write(p);
            return "LOCAL_DEV_EXPERIMENT_SET key="+key+" value="+(value==null?"AUTO":value)+" file="+file();
        }catch(IOException e){return "LOCAL_DEV_EXPERIMENT_IO_ERROR key="+key+" error="+e;}
    }

    static synchronized String clear(){
        try{
            Files.deleteIfExists(file());
            return "LOCAL_DEV_EXPERIMENT_CLEARED file="+file();
        }catch(IOException e){return "LOCAL_DEV_EXPERIMENT_IO_ERROR clear error="+e;}
    }

    static synchronized String summary(){
        try{
            Properties p=load();
            TreeMap<String,String> m=new TreeMap<>();
            for(String k:p.stringPropertyNames())m.put(k,p.getProperty(k));
            return "LOCAL_DEV_EXPERIMENT_OVERRIDES "+m+" file="+file();
        }catch(IOException e){return "LOCAL_DEV_EXPERIMENT_IO_ERROR info error="+e;}
    }

    private static Properties load() throws IOException{
        Properties p=new Properties(); Path f=file();
        if(Files.isRegularFile(f))try(InputStream in=Files.newInputStream(f)){p.load(in);} return p;
    }
    private static void write(Properties p) throws IOException{
        Path f=file(), parent=f.getParent(); if(parent!=null)Files.createDirectories(parent);
        Path tmp=f.resolveSibling(f.getFileName()+".tmp");
        try(OutputStream out=Files.newOutputStream(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){p.store(out,"SpawnPK LocalLab LOCAL_DEV_EXPERIMENT render overrides");}
        try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}
    }
}
