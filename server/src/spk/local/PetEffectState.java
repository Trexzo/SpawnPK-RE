package spk.local;

/** Per-session pet proc/charge state. Combat modifiers remain separate from the M2 fixture formula. */
final class PetEffectState {
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

    static final class PreparedDamage {
        final int itemId;
        final int npcId;
        final int beforeAccumulatedDamage;
        final int beforeCharge;
        final long beforeLastDamageAtMs;
        final int afterAccumulatedDamage;
        final int afterCharge;
        final long afterLastDamageAtMs;

        PreparedDamage(
            int itemId,
            int npcId,
            int beforeAccumulatedDamage,
            int beforeCharge,
            long beforeLastDamageAtMs,
            int afterAccumulatedDamage,
            int afterCharge,
            long afterLastDamageAtMs
        ){
            this.itemId=itemId;
            this.npcId=npcId;
            this.beforeAccumulatedDamage=beforeAccumulatedDamage;
            this.beforeCharge=beforeCharge;
            this.beforeLastDamageAtMs=beforeLastDamageAtMs;
            this.afterAccumulatedDamage=afterAccumulatedDamage;
            this.afterCharge=afterCharge;
            this.afterLastDamageAtMs=afterLastDamageAtMs;
        }

        boolean chargeChanged(){
            return beforeCharge!=afterCharge;
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

    int charge(){ return charge; }
    int accumulatedDamage(){ return accumulatedDamage; }
    long lastDamageAtMs(){ return lastDamageAtMs; }
    boolean chargePet(){ return PetPresentationProfile.isChargePet(itemId,npcId); }
    int threshold(){ return PetPresentationProfile.damagePerCharge(itemId,npcId); }
    long resetMs(){ return PetPresentationProfile.chargeResetMs(itemId,npcId); }

    PreparedDamage prepareDamage(
        int damage,
        long now
    ){
        if(damage<=0 || !chargePet())
            return null;

        int nextAccumulated=
            accumulatedDamage;
        int nextCharge=
            charge;

        long timeout=resetMs();
        if(lastDamageAtMs>0&&
           timeout>0&&
           now-lastDamageAtMs>=timeout){
            nextAccumulated=0;
            nextCharge=0;
        }

        long next=
            (long)nextAccumulated+
            damage;
        nextAccumulated=
            (int)Math.min(
                Integer.MAX_VALUE,
                next
            );

        int t=threshold();
        nextCharge=
            t<=0
                ?0
                :Math.min(
                    3,
                    nextAccumulated/t
                );

        return new PreparedDamage(
            itemId,
            npcId,
            accumulatedDamage,
            charge,
            lastDamageAtMs,
            nextAccumulated,
            nextCharge,
            now
        );
    }

    void commitPreparedDamage(
        PreparedDamage prepared
    ){
        if(prepared==null)
            throw new NullPointerException("prepared");

        if(itemId!=prepared.itemId||
           npcId!=prepared.npcId||
           accumulatedDamage!=
                prepared.beforeAccumulatedDamage||
           charge!=prepared.beforeCharge||
           lastDamageAtMs!=
                prepared.beforeLastDamageAtMs)
            throw new IllegalStateException(
                "pet effect state changed before damage commit"
            );

        accumulatedDamage=
            prepared.afterAccumulatedDamage;
        charge=
            prepared.afterCharge;
        lastDamageAtMs=
            prepared.afterLastDamageAtMs;
    }

    /** Returns true if the externally rendered charge changed. */
    boolean recordDamage(int damage,long now){
        PreparedDamage prepared=
            prepareDamage(
                damage,
                now
            );

        if(prepared==null)
            return false;

        commitPreparedDamage(
            prepared
        );

        return prepared.chargeChanged();
    }

    PreparedTimeoutReset prepareTimeoutReset(long now){
        if(charge<=0 || lastDamageAtMs<=0) return null;
        long timeout=resetMs();
        if(timeout<=0 || now-lastDamageAtMs<timeout) return null;
        return new PreparedTimeoutReset(
            itemId,
            npcId,
            accumulatedDamage,
            charge,
            lastDamageAtMs
        );
    }

    void commitTimeoutReset(
        PreparedTimeoutReset prepared
    ){
        if(prepared==null)
            throw new NullPointerException("prepared");
        if(itemId!=prepared.itemId||
           npcId!=prepared.npcId||
           accumulatedDamage!=prepared.accumulatedDamage||
           charge!=prepared.charge||
           lastDamageAtMs!=prepared.lastDamageAtMs)
            throw new IllegalStateException(
                "pet effect state changed before timeout reset commit"
            );
        accumulatedDamage=0;
        charge=0;
        lastDamageAtMs=0L;
    }

    /** Compatibility helper for standalone state-only tests. */
    boolean tick(long now){
        PreparedTimeoutReset prepared=
            prepareTimeoutReset(now);
        if(prepared==null) return false;
        commitTimeoutReset(prepared);
        return true;
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
