package spk.local;

/** Persisted main-pet and configured mini-pet state. Mini selection survives main-pet pickup. */
final class PetState {
    static final class Snapshot {
        final int itemId;
        final int npcId;
        final int miniItemId;

        Snapshot(
            int itemId,
            int npcId,
            int miniItemId
        ){
            this.itemId=itemId;
            this.npcId=npcId;
            this.miniItemId=miniItemId;
        }
    }

    private int itemId=-1;
    private int npcId=-1;
    private int miniItemId=-1;

    boolean active(){return itemId>=0&&npcId>=0;}
    int itemId(){return itemId;}
    int npcId(){return npcId;}
    boolean miniConfigured(){return miniItemId>=0;}
    int miniItemId(){return miniItemId;}

    void activate(PetDefinitionRepository.Def d){
        if(d==null)throw new IllegalArgumentException("pet definition");
        itemId=d.itemId; npcId=d.npcId;
    }
    /** Clear only the active main pet. Configured mini-pet is independent and persists. */
    void clear(){itemId=-1;npcId=-1;}
    void configureMini(int item){
        if(MiniPetDefinitionRepository.get(item)==null)throw new IllegalArgumentException("unknown mini pet "+item);
        miniItemId=item;
    }
    void clearMini(){miniItemId=-1;}

    Snapshot snapshot(){
        return new Snapshot(
            itemId,
            npcId,
            miniItemId
        );
    }

    void restore(Snapshot snapshot){
        if(snapshot==null)
            throw new NullPointerException("snapshot");
        itemId=snapshot.itemId;
        npcId=snapshot.npcId;
        miniItemId=snapshot.miniItemId;
    }



}
