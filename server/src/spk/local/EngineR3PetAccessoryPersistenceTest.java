package spk.local;

import java.util.*;

public final class EngineR3PetAccessoryPersistenceTest{
    public static void main(String[]args){
        Properties p=new Properties();p.setProperty("movement.worldX","3104");p.setProperty("inventory.slot.0","4151");
        PetAccessoryPersistence.merge(p,21068);eq(21068,PetAccessoryPersistence.read(p),"restore");
        eq("3104",p.getProperty("movement.worldX"),"unrelated movement retained");eq("4151",p.getProperty("inventory.slot.0"),"unrelated inventory retained");
        PetAccessoryPersistence.merge(p,0);eq(0,PetAccessoryPersistence.read(p),"detach");eq("3104",p.getProperty("movement.worldX"),"movement retained after detach");
        System.out.println("V5130_ENGINE_R3_PET_ACCESSORY_PERSISTENCE_PASS semanticField=true unrelatedPropertiesRetained=true detachNone=true");
    }
    static void eq(int e,int a,String m){if(e!=a)throw new AssertionError(m+" expected="+e+" actual="+a);}
    static void eq(String e,String a,String m){if(!e.equals(a))throw new AssertionError(m+" expected="+e+" actual="+a);}
}
