package spk.local;

/** Per-session pet proc/charge state. Combat modifiers remain separate from the M2 fixture formula. */
final class PetEffectState {
    static final class Snapshot {
        final int itemId;
        final int npcId;
        final int accumulatedDamage;
        final int charge;
        final long lastDamageAtMs;

        Snapshot(
            int itemId,
            int npcId,
            int accumulatedDamage,
            int charge,
            long lastDamageAtMs
        ){
            this.itemId=itemId;
            this.npcId=npcId;
            this.accumulatedDamage=accumulatedDamage;
            this.charge=charge;
            this.lastDamageAtMs=lastDamageAtMs;
        }
    }

    private int itemId=-1,npcId=-1;
    private int accumulatedDamage;
    private int charge;
    private long lastDamageAtMs;

    void onPetChanged(int item,int npc){
        itemId=item; npcId=npc; accumulatedDamage=0; charge=0; lastDamageAtMs=0L;
    }
    void clear(){ onPetChanged(-1,-1); }

    Snapshot snapshot(){
        return new Snapshot(
            itemId,
            npcId,
            accumulatedDamage,
            charge,
            lastDamageAtMs
        );
    }

    void restore(Snapshot snapshot){
        if(snapshot==null)
            throw new NullPointerException("snapshot");
        itemId=snapshot.itemId;
        npcId=snapshot.npcId;
        accumulatedDamage=snapshot.accumulatedDamage;
        charge=snapshot.charge;
        lastDamageAtMs=snapshot.lastDamageAtMs;
    }
    int charge(){ return charge; }
    int accumulatedDamage(){ return accumulatedDamage; }
    long lastDamageAtMs(){ return lastDamageAtMs; }
    boolean chargePet(){ return PetPresentationProfile.isChargePet(itemId,npcId); }
    int threshold(){ return PetPresentationProfile.damagePerCharge(itemId,npcId); }
    long resetMs(){ return PetPresentationProfile.chargeResetMs(itemId,npcId); }

    /** Returns true if the externally rendered charge changed. */
    boolean recordDamage(int damage,long now){
        if(damage<=0 || !chargePet()) return false;
        int before=charge;
        long timeout=resetMs();
        if(lastDamageAtMs>0 && timeout>0 && now-lastDamageAtMs>=timeout){ accumulatedDamage=0; charge=0; }
        lastDamageAtMs=now;
        long next=(long)accumulatedDamage+damage;
        accumulatedDamage=(int)Math.min(Integer.MAX_VALUE,next);
        int t=threshold();
        charge=t<=0?0:Math.min(3,accumulatedDamage/t);
        return charge!=before;
    }

    /** Returns true if a timeout reset occurred. */
    boolean tick(long now){
        if(charge<=0 || lastDamageAtMs<=0) return false;
        long timeout=resetMs();
        if(timeout>0 && now-lastDamageAtMs>=timeout){ accumulatedDamage=0; charge=0; lastDamageAtMs=0L; return true; }
        return false;
    }

    void forceCharge(int value,long now){
        if(value<0||value>3) throw new IllegalArgumentException("charge");
        charge=value;
        int t=threshold();
        accumulatedDamage=t<=0?0:value*t;
        lastDamageAtMs=value==0?0L:now;
    }

    String summary(){
        return "item="+itemId+" npc="+npcId+" charge="+charge+" damage="+accumulatedDamage+
            " threshold="+threshold()+" resetMs="+resetMs()+" mechanics="+
            PetPresentationProfile.chargeMechanicsSummary(itemId,npcId,charge);
    }
}
