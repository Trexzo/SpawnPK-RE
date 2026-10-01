package spk.local;

import java.io.*;

/** Direct v4 state proof: fixed inventory holes, non-stackables, placeholders and X. */
public final class BankV4StateTest {
    public static void main(String[] args) throws Exception {
        ServerPacketWriter w = new ServerPacketWriter(new ByteArrayOutputStream(), new IsaacCipher(new int[]{1,2,3,4}));
        BankState b = new BankState();

        boolean closedOpenFailure=false;
        try {
            b.open(failingWriter());
        } catch (IOException expected) {
            closedOpenFailure=true;
        }
        if(!closedOpenFailure||b.isOpen())
            throw new AssertionError("failed closed->open committed BankState open="+b.isOpen());

        b.open(w);
        if (b.inventoryCapacity()!=28 || b.bankCapacity()!=352) throw new AssertionError("capacity");
        if (!b.togglePlaceholders(w).startsWith("PLACEHOLDERS_ENABLED")) throw new AssertionError("placeholder toggle");

        // 20 rocktails are non-stackable: Withdraw All must occupy 20 distinct fixed slots.
        String r = b.apply(new ItemContainerAction(129,5382,3,15272,0,"WITHDRAW_ALL"), w);
        if (!r.startsWith("WITHDRAW_OK amount=20")) throw new AssertionError(r);
        for (int i=0;i<20;i++) {
            BankState.Stack s=b.inventoryAt(i);
            if (s==null || s.itemId!=15272 || s.qty!=1) throw new AssertionError("food slot "+i+"="+s);
        }
        BankState.Stack ph=b.bankAt(3);
        if (ph==null || ph.itemId!=15272 || ph.qty!=0) throw new AssertionError("placeholder="+ph);

        // Store one from the middle. Slots on either side must not compact or move.
        BankState.Stack left=b.inventoryAt(4), right=b.inventoryAt(6);
        r=b.apply(new ItemContainerAction(145,5064,5,15272,0,"STORE_1"),w);
        if(!r.startsWith("STORE_OK amount=1"))throw new AssertionError(r);
        if(b.inventoryAt(5)!=null)throw new AssertionError("stored slot not cleared");
        if(b.inventoryAt(4)!=left || b.inventoryAt(6)!=right)throw new AssertionError("inventory compacted");
        if(b.bankAt(3)==null || b.bankAt(3).qty!=1)throw new AssertionError("placeholder not refilled");

        // X cycle on stackable coins: prompt -> amount -> prompt store -> amount.
        r=b.apply(new ItemContainerAction(135,5382,0,995,0,"WITHDRAW_X"),w);
        if(!r.startsWith("WITHDRAW_X_PROMPT_SENT"))throw new AssertionError(r);

        boolean reopenFailure=false;
        try {
            b.open(failingWriter());
        } catch (IOException expected) {
            reopenFailure=true;
        }
        if(!reopenFailure||!b.isOpen())
            throw new AssertionError("failed reopen changed BankState open="+b.isOpen());
        r=b.applyAmount(123,w);
        if(!r.startsWith("WITHDRAW_X_OK amount=123"))
            throw new AssertionError("failed reopen cleared pending-X: "+r);
        int coinSlot=-1; for(int i=0;i<28;i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==995){coinSlot=i;break;}}
        if(coinSlot<0 || b.inventoryAt(coinSlot).qty!=123)throw new AssertionError("coin x withdraw");
        r=b.apply(new ItemContainerAction(135,5064,coinSlot,995,0,"STORE_X"),w);
        if(!r.startsWith("STORE_X_PROMPT_SENT"))throw new AssertionError(r);
        r=b.applyAmount(23,w); if(!r.startsWith("STORE_X_OK amount=23"))throw new AssertionError(r);
        if(b.inventoryAt(coinSlot)==null || b.inventoryAt(coinSlot).qty!=100)throw new AssertionError("coin x store");

        // Exact drag route must preserve fixed capacity and not lose item state.
        r=b.applyDrag(new ContainerDrag(5064,0,0,27),w);
        if(!r.startsWith("DRAG_OK"))throw new AssertionError(r);
        if(b.inventoryCapacity()!=28)throw new AssertionError();

        System.out.println("V4_BANK_STATE_PASS fixedInventory=28 fixedBank=352 nonStackableFood=20DistinctSlots noCompaction=true placeholders=true withdrawX=123 storeX=23 drag214=true targetOpenFailureAtomic=true pendingXPreservedOnFailedReopen=true");
    }

    private static ServerPacketWriter failingWriter() {
        return new ServerPacketWriter(
            new OutputStream() {
                @Override public void write(int value) throws IOException {
                    throw new IOException("EXPECTED_BANK_OPEN_WRITE_FAILURE");
                }
            },
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }
}
