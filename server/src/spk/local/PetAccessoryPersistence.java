package spk.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Adds one semantic persistence field without changing the certified R2 profile serializer. */
final class PetAccessoryPersistence {
    private static final String KEY="pet.accessoryItem";
    private PetAccessoryPersistence(){}

    static int load(String username) throws IOException {
        Path p=LocalAccountProfiles.accountFile(username);
        if(!Files.isRegularFile(p))return 0;
        Properties props=new Properties();
        try(InputStream in=Files.newInputStream(p)){props.load(in);}
        return read(props);
    }

    static void save(String username,int itemId) throws IOException {
        Path p=LocalAccountProfiles.accountFile(username);
        if(!Files.isRegularFile(p))return;
        Properties props=new Properties();
        try(InputStream in=Files.newInputStream(p)){props.load(in);}
        merge(props,itemId);
        AtomicAccountPublisher.publish(p, props, "SpawnPK LocalLab account");
    }

        static int read(Properties props){
        String raw=props==null?"0":props.getProperty(KEY,"0").trim();
        try{return Math.max(0,Integer.parseInt(raw));}catch(NumberFormatException e){return 0;}
    }
    static void merge(Properties props,int itemId){
        if(props==null)throw new NullPointerException("props");
        if(itemId>0)props.setProperty(KEY,Integer.toString(itemId));else props.remove(KEY);
    }
}
