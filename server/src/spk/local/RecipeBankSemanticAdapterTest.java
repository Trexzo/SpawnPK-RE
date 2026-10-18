package spk.local;
import java.util.*;

/** Source-only bank adapter test; no network, packet, XP or live recipes. */
public final class RecipeBankSemanticAdapterTest {
    static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    static void reject(Runnable run){boolean rejected=false;try{run.run();}catch(IllegalStateException expected){rejected=true;}ok(rejected,"stale snapshot committed");}
    public static void main(String[] args){
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()],qty=new int[ids.length];
        Arrays.fill(ids,-1);ids[0]=100;qty[0]=5;ids[1]=200;qty[1]=7;
        bank.replaceInventorySemantic(ids,qty);
        Set<Integer> known=new HashSet<>(Arrays.asList(100,200,300));
        RecipeBankSemanticAdapter.Prepared ready=RecipeBankSemanticAdapter.prepare(
            bank,Collections.singletonMap(100,2),300,3,2,known);
        ok(bank.inventoryAt(0).qty==5&&bank.inventoryAt(2)==null,
            "prepare mutated bank");
        RecipeBankSemanticAdapter.commit(bank,ready);
        ok(bank.inventoryAt(0).qty==1&&bank.inventoryAt(2).itemId==300&&
            bank.inventoryAt(2).qty==6,"commit postimage incorrect");
        RecipeBankSemanticAdapter.Prepared stale=RecipeBankSemanticAdapter.prepare(
            bank,Collections.singletonMap(100,1),300,1,1,known);
        int[] changedIds=new int[ids.length],changedQty=new int[qty.length];
        for(int i=0;i<ids.length;i++){
            BankState.Stack value=bank.inventoryAt(i);
            changedIds[i]=value==null?-1:value.itemId;
            changedQty[i]=value==null?0:value.qty;
        }
        changedQty[1]=6;
        bank.replaceInventorySemantic(changedIds,changedQty);
        reject(()->RecipeBankSemanticAdapter.commit(bank,stale));
        ok(bank.inventoryAt(0).qty==1&&bank.inventoryAt(1).qty==6,
            "stale rejection modified bank");
        System.out.println("RECIPE_BANK_SEMANTIC_ADAPTER_PASS"+
            " preparedReadOnly=true validCommit=true staleDenied=true"+
            " liveRecipeRegistered=false");
    }
    private RecipeBankSemanticAdapterTest(){}
}
