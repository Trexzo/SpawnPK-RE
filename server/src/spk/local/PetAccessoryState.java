package spk.local;

/** Persisted semantic pet-accessory selection. 0 means none. */
final class PetAccessoryState {
    private int activeItem;

    int activeItem(){
        return activeItem;
    }

    boolean active(){
        return activeItem!=0;
    }

    void setActiveItem(int itemId){
        activeItem=itemId;
    }

    void clear(){
        activeItem=0;
    }

    String summary(){
        return activeItem==0
            ?"NONE"
            :activeItem+"/"+PetAccessoryAuthority.name(activeItem);
    }
}
