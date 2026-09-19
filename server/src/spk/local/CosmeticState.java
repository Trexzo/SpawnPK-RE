package spk.local;

final class CosmeticState {
    private int itemId=-1;
    int itemId(){return itemId;}
    boolean active(){return itemId>=0;}
    int set(int id){int old=itemId;itemId=id;return old;}
    int clear(){return set(-1);}
    void load(int id){itemId=id>=0?id:-1;}
}
