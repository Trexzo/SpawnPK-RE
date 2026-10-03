package spk.local;

/** Session-only state for the custom Voidglass prototype.  It is intentionally
 * not account-persisted: the underlying ordinary Vasa pet remains the canonical
 * persisted item/NPC identity until a real client/cache content definition is made. */
final class VoidglassPetState {
    private boolean active;
    private Integer previousParticleSelector;
    private int selectedParticle=VoidglassPetProfile.DEFAULT_PARTICLE_SELECTOR;
    private int procCount;

    boolean active(){return active;}
    int selectedParticle(){return selectedParticle;}
    int procCount(){return procCount;}
    Integer previousParticleSelector(){return previousParticleSelector;}

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
