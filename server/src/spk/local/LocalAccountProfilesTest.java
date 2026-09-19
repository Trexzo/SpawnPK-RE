package spk.local;

import java.nio.file.*;
import java.util.*;

public final class LocalAccountProfilesTest {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("spk-v5123-accounts-");
        String old=System.getProperty("spk.local.accountFile");
        try{
            Path primary=dir.resolve("opensrc.properties"); System.setProperty("spk.local.accountFile",primary.toString());
            BankState b=new BankState(); EquipmentState e=new EquipmentState(); MovementState m=new MovementState(); PetState p=new PetState(); PlayerState ps=new PlayerState();
            e.setWeapon(21566);m.setPersistentRun(true);m.setRunEnergy(73);
            String saved=LocalAccountProfiles.save("src",b,e,m,p,ps); Path src=dir.resolve("src.properties");
            if(!Files.isRegularFile(src)||Files.exists(primary))throw new AssertionError("wrong profile path saved="+saved);
            Properties props=new Properties();try(java.io.InputStream in=Files.newInputStream(src)){props.load(in);}if(!"src".equals(props.getProperty("username")))throw new AssertionError("username property");
            BankState b2=new BankState(); EquipmentState e2=new EquipmentState(); MovementState m2=new MovementState(); PetState p2=new PetState(); PlayerState ps2=new PlayerState();
            String loaded=LocalAccountProfiles.load("src",b2,e2,m2,p2,ps2);
            if(e2.weapon()!=21566||!m2.persistentRun()||m2.runEnergy()!=73)throw new AssertionError("round trip failed "+loaded);
            if(!LocalAccountProfiles.accountFile("src").equals(src.toAbsolutePath().normalize()))throw new AssertionError("src path");
            System.out.println("V5123_LOCAL_ACCOUNT_PROFILES_PASS separateSrcFile=true atomicSchemaCompatible=true stateRoundTrip=true");
        }finally{
            if(old==null)System.clearProperty("spk.local.accountFile");else System.setProperty("spk.local.accountFile",old);
            try(java.util.stream.Stream<Path> st=Files.walk(dir)){st.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}
        }
    }
}
