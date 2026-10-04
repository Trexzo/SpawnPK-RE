package spk.local;

/** Per-session pet proc/charge state. Combat modifiers remain separate from the M2 fixture formula. */
final class PetEffectState {
    private int itemId=-1,npcId=-1;
    private int accumulatedDamage;
    private int charge;
    private long lastDamageAtMs;

    void onPetChanged(int item,int npc){
        itemId=item; npcId=npc; accumulatedDamage=0; charge=0; lastDamageAtMs=0L;
    }
    void clear(){ onPetChanged(-1,-1); }

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

    static final class PreparedTimeoutReset {
        final int itemId;
        final int npcId;
        final int accumulatedDamage;
        final int charge;
        final long lastDamageAtMs;

        PreparedTimeoutReset(
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

    PreparedTimeoutReset prepareTimeoutReset(
        long now
    ){
        if(charge<=0||lastDamageAtMs<=0)
            return null;

        long timeout=resetMs();

        if(timeout<=0||
           now-lastDamageAtMs<timeout)
            return null;

        return new PreparedTimeoutReset(
            itemId,
            npcId,
            accumulatedDamage,
            charge,
            lastDamageAtMs
        );
    }

    boolean canCommitTimeoutReset(
        PreparedTimeoutReset prepared
    ){
        return prepared!=null&&
            itemId==prepared.itemId&&
            npcId==prepared.npcId&&
            accumulatedDamage==
                prepared.accumulatedDamage&&
            charge==prepared.charge&&
            lastDamageAtMs==
                prepared.lastDamageAtMs;
    }

    boolean commitTimeoutReset(
        PreparedTimeoutReset prepared
    ){
        if(!canCommitTimeoutReset(prepared))
            return false;

        accumulatedDamage=0;
        charge=0;
        lastDamageAtMs=0L;
        return true;
    }

    /** Legacy direct seam retained for non-world-tick callers. */
    boolean tick(long now){
        PreparedTimeoutReset prepared=
            prepareTimeoutReset(now);

        return prepared!=null&&
            commitTimeoutReset(prepared);
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
