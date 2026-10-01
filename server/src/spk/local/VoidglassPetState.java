package spk.local;

/** Session-only state for the custom Voidglass prototype.  It is intentionally
 * not account-persisted: the underlying ordinary Vasa pet remains the canonical
 * persisted item/NPC identity until a real client/cache content definition is made. */
final class VoidglassPetState {
    static final class Snapshot {
        final boolean active;
        final Integer previousParticleSelector;
        final int selectedParticle;
        final int procCount;

        Snapshot(
            boolean active,
            Integer previousParticleSelector,
            int selectedParticle,
            int procCount
        ){
            this.active=active;
            this.previousParticleSelector=previousParticleSelector;
            this.selectedParticle=selectedParticle;
            this.procCount=procCount;
        }
    }

    private boolean active;
    private Integer previousParticleSelector;
    private int selectedParticle=VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR;
    private int procCount;

    boolean active(){return active;}
    int selectedParticle(){return selectedParticle;}
    int procCount(){return procCount;}
    Integer previousParticleSelector(){return previousParticleSelector;}

    Snapshot snapshot(){
        return new Snapshot(
            active,
            previousParticleSelector,
            selectedParticle,
            procCount
        );
    }

    void restore(Snapshot snapshot){
        if(snapshot==null)
            throw new NullPointerException("snapshot");
        active=snapshot.active;
        previousParticleSelector=snapshot.previousParticleSelector;
        selectedParticle=snapshot.selectedParticle;
        procCount=snapshot.procCount;
    }

    Integer selectorAfterClear(){
        return previousParticleSelector;
    }

    void activate(Integer previous){
        active=true;
        previousParticleSelector=previous;
        selectedParticle=VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR;
        procCount=0;
    }

    void selectParticle(int selector){
        if(!VoidglassPetProfile.allowedSelector(selector))
            throw new IllegalArgumentException("Voidglass selector must be 6 or 8");
        selectedParticle=selector;
    }

    void recordProc(){
        if(!active) throw new IllegalStateException("Voidglass is not active");
        procCount++;
    }

    /** Returns the selector that was active before Voidglass took ownership. */
    Integer clearAndRestoreSelector(){
        Integer previous=previousParticleSelector;
        active=false;
        previousParticleSelector=null;
        selectedParticle=VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR;
        procCount=0;
        return previous;
    }

    String summary(Integer actualSelector){
        return "active="+active+
            " base="+VoidglassPetProfile.BASE_ITEM_ID+"->"+VoidglassPetProfile.BASE_NPC_ID+
            " selectedFx="+selectedParticle+
            " actualFx="+(actualSelector==null?"AUTO":actualSelector)+
            " previousFx="+(previousParticleSelector==null?"AUTO":previousParticleSelector)+
            " procCount="+procCount+
            " persistence=SESSION_ONLY"+
            " combatModifier=NOT_IMPLEMENTED_PRESENTATION_PROTOTYPE";
    }
}
