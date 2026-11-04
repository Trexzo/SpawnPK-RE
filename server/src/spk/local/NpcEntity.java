package spk.local;

/** Minimal server-owned NPC entity used by the v5.3 synchronization foundation. */
final class NpcEntity {
    final int sceneIndex;
    final int definitionId;
    int x,y;
    final boolean pet;
    final int petItemId;
    final int ownerPlayerIndex;
    private EntityId canonicalId;
    // Opt-in R6 localhost playability fixture only; -1 means an ordinary NPC.
    // This never changes canonical HOME NPCs or historical max-hit dummies.
    private int localLabMortalHp=-1;
    private int localLabMortalMaxHp=-1;
    boolean localLabMortalFixture(){return localLabMortalHp>=0;}
    int localLabMortalHp(){return localLabMortalHp;}
    int localLabMortalMaxHp(){return localLabMortalMaxHp;}
    void enableLocalLabMortalFixture(){
        enableLocalLabMortalFixture(LocalLabMortalNpcFixture.maxHp());
    }
    void enableLocalLabMortalFixture(int maxHp){
        if(localLabMortalHp>=0)
            throw new IllegalStateException("mortal fixture already enabled");
        if(maxHp<1||maxHp>255)
            throw new IllegalArgumentException("fixture maxHP must be 1..255");
        localLabMortalHp=maxHp;
        localLabMortalMaxHp=maxHp;
    }
    int applyLocalLabMortalDamage(int amount){
        if(localLabMortalHp<0||amount<=0)
            throw new IllegalStateException("not positive fixture damage");
        localLabMortalHp=Math.max(0,localLabMortalHp-amount);
        return localLabMortalHp;
    }


    NpcEntity(int sceneIndex,int definitionId,int x,int y){ this(sceneIndex,definitionId,x,y,false,-1,-1); }
    NpcEntity(int sceneIndex,int definitionId,int x,int y,boolean pet,int petItemId,int ownerPlayerIndex){
        if(sceneIndex<0 || sceneIndex>=16383) throw new IllegalArgumentException("sceneIndex");
        if(definitionId<0 || definitionId>=16384) throw new IllegalArgumentException("definitionId");
        this.sceneIndex=sceneIndex; this.definitionId=definitionId; this.x=x; this.y=y;
        this.pet=pet; this.petItemId=petItemId; this.ownerPlayerIndex=ownerPlayerIndex;
    }

    EntityId canonicalId(){return canonicalId;}

    void bindCanonicalId(EntityId id){
        if(id==null)throw new NullPointerException("canonicalId");
        if(canonicalId!=null&&!canonicalId.equals(id))
            throw new IllegalStateException(
                "NPC projection already bound canonical="+canonicalId+
                " requested="+id+
                " scene="+sceneIndex
            );
        canonicalId=id;
    }
    @Override public String toString(){return "NpcEntity{idx="+sceneIndex+",def="+definitionId+",xy="+x+","+y+",pet="+pet+",item="+petItemId+",owner="+ownerPlayerIndex+"}";}
}
