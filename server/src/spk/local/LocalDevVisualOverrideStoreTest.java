package spk.local;

import java.nio.file.*;
import java.util.*;

public final class LocalDevVisualOverrideStoreTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-v5124-visual-"); Path file=dir.resolve("dev_pet_visual.properties");
        String old=System.getProperty("spk.local.devVisualFile");
        try{
            System.setProperty("spk.local.devVisualFile",file.toString());
            LocalDevVisualOverrideStore.clear();
            LocalDevVisualOverrideStore.set("alpha","150");
            LocalDevVisualOverrideStore.set("ai","319770");
            LocalDevVisualOverrideStore.set("tint","rgb:66ccff");
            LocalDevVisualOverrideStore.set("bodycycle","33");
            Properties p=new Properties(); try(java.io.InputStream in=Files.newInputStream(file)){p.load(in);}
            if(!"true".equals(p.getProperty("enabled")))throw new AssertionError("enabled");
            if(!"150".equals(p.getProperty("alpha"))||!"319770".equals(p.getProperty("ai"))||!"rgb:66ccff".equals(p.getProperty("tint"))||!"33".equals(p.getProperty("bodycycle")))throw new AssertionError(p.toString());
            LocalDevVisualOverrideStore.set("ai",null); p.clear(); try(java.io.InputStream in=Files.newInputStream(file)){p.load(in);} if(p.containsKey("ai"))throw new AssertionError("ai auto did not clear");
            LocalDevVisualOverrideStore.clear(); if(Files.exists(file))throw new AssertionError("clear");
            System.out.println("V5124_LOCAL_DEV_VISUAL_OVERRIDE_STORE_PASS alpha=true ai=true tint=true bodycycle=true autoReset=true");
        }finally{
            if(old==null)System.clearProperty("spk.local.devVisualFile"); else System.setProperty("spk.local.devVisualFile",old);
            try(java.util.stream.Stream<Path> st=Files.walk(dir)){st.sorted(Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}
        }
    }
}
