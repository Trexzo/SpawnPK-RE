package spk.local;

/** Session-local state for the clickable in-game R7/R7.1 developer control center. */
final class DevControlCenter {
    enum Page {
        MAIN,MORE,COMBAT,COMBAT_ANIM,COMBAT_HIT,COMBAT_MORE,COMBAT_RUNTIME,PETS,PET_FX,PET_FOLLOW,PET_PRESENT,PET_PRESENT_MORE,CUSTOM_CONTENT,CUSTOM_VOIDGLASS,
        MAGIC_PRAYER,MAGIC,PRAYER,WORLD,ITEMS,PLAYER_NPC,PLAYER,PLAYER_PRESENT,NPC,
        DIAG,AUTHORITY,AUTH_MAGIC,AUTH_WORLD_ITEM,RESEARCH,RESEARCH_EQUIP,RESEARCH_PET,RESEARCH_WORLD,RESEARCH_SERVICE,RESEARCH_DISCOVERY,RESEARCH_ASSET,RESEARCH_PROTOCOL,ALIGNMENT,RESET_CONFIRM
    }
    enum PendingAmount {
        NONE,COMBAT_ANIM,HIT_DAMAGE,HIT_TYPE,PET_FX,PET_ANIM,PET_GFX,PET_NATIVE_STATE,
        REGION_ID,ITEM_LIBRARY_ID,PLAYER_MORPH,PLAYER_ANIM,PLAYER_GFX,NPC_SPAWN,
        PRAYER_WIDGET,PRAYER_ICON,AUTH_SPELL_WIDGET,AUTH_PRAYER_WIDGET,AUTH_ITEM_ID,AUTH_REGION_ID,RUNTIME_WEAPON_ITEM,RESEARCH_EQUIP_ITEM,RESEARCH_PET_ROW,RESEARCH_TELE_ITEM,RESEARCH_TRANSITION_REGION
    }
    static final class StateSnapshot {
        final boolean open;
        final Page page;
        final PendingAmount pending;
        final Page returnPage;

        StateSnapshot(
            boolean open,
            Page page,
            PendingAmount pending,
            Page returnPage
        ){
            this.open=open;
            this.page=page;
            this.pending=pending;
            this.returnPage=returnPage;
        }
    }

    private boolean open;
    private Page page=Page.MAIN;
    private PendingAmount pending=PendingAmount.NONE;
    private Page returnPage=Page.MAIN;
    private int selectedSpellWidget=-1,selectedPrayerWidget=-1,selectedItemId=-1,selectedRegionId=-1,selectedRuntimeWeaponItemId=-1,selectedResearchEquipItemId=-1,selectedResearchPetRow=-1,selectedResearchTeleportItemId=-1,selectedResearchTransitionRegion=-1;

    boolean isOpen(){return open;}
    Page page(){return page;}
    StateSnapshot snapshot(){
        return new StateSnapshot(
            open,
            page,
            pending,
            returnPage
        );
    }
    void restore(StateSnapshot snapshot){
        if(snapshot==null)
            throw new NullPointerException("snapshot");
        open=snapshot.open;
        page=snapshot.page;
        pending=snapshot.pending;
        returnPage=snapshot.returnPage;
    }
    void open(Page p){open=true;page=p==null?Page.MAIN:p;pending=PendingAmount.NONE;}
    void setPage(Page p){open=true;page=p;pending=PendingAmount.NONE;}
    void close(){open=false;pending=PendingAmount.NONE;}
    void prompt(PendingAmount p){returnPage=page;pending=p;open=false;}
    PendingAmount pending(){return pending;}
    boolean hasPending(){return pending!=PendingAmount.NONE;}
    Page finishPrompt(){Page p=returnPage;pending=PendingAmount.NONE;open=true;page=p;return p;}
    void cancelPending(){pending=PendingAmount.NONE;}

    int selectedSpellWidget(){return selectedSpellWidget;}
    int selectedPrayerWidget(){return selectedPrayerWidget;}
    int selectedItemId(){return selectedItemId;}
    int selectedRegionId(){return selectedRegionId;}
    int selectedRuntimeWeaponItemId(){return selectedRuntimeWeaponItemId;}
    int selectedResearchEquipItemId(){return selectedResearchEquipItemId;}
    int selectedResearchPetRow(){return selectedResearchPetRow;}
    int selectedResearchTeleportItemId(){return selectedResearchTeleportItemId;}
    int selectedResearchTransitionRegion(){return selectedResearchTransitionRegion;}
    void selectSpellWidget(int v){selectedSpellWidget=v;}
    void selectPrayerWidget(int v){selectedPrayerWidget=v;}
    void selectItemId(int v){selectedItemId=v;}
    void selectRegionId(int v){selectedRegionId=v;}
    void selectRuntimeWeaponItemId(int v){selectedRuntimeWeaponItemId=v;}
    void selectResearchEquipItemId(int v){selectedResearchEquipItemId=v;}
    void selectResearchPetRow(int v){selectedResearchPetRow=v;}
    void selectResearchTeleportItemId(int v){selectedResearchTeleportItemId=v;}
    void selectResearchTransitionRegion(int v){selectedResearchTransitionRegion=v;}
}
