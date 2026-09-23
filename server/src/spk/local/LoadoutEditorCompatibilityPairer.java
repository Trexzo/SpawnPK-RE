package spk.local;

import java.util.*;

/**
 * Session-local exact-v308 compatibility pairer for the in-game loadout editor.
 *
 * Exact client order is cld1(inventory) immediately followed by cld2(equipment).
 * Only one inventory half may be staged and any intervening packet expires it.
 */
final class LoadoutEditorCompatibilityPairer {
    static final int INVENTORY_ENTRY_COUNT=28;
    static final int EQUIPMENT_ENTRY_COUNT=14;

    /*
     * Exact rs.n.c.al.h() compatibility projection from widget 33003.
     * -1 marks a literal client-emitted 0,0 placeholder rather than a source
     * equipment-array index.
     */
    static final int[] EQUIPMENT_SOURCE_INDICES={
        1,3,4,6,7,8,-1,10,-1,12,13,-1,14,5
    };

    enum Kind {
        UNRECOGNIZED,
        INVENTORY_STAGED,
        COMPLETE,
        REJECTED
    }

    static final class Outcome {
        final Kind kind;
        final LoadoutEditorSaveClientRequest request;
        final String reason;

        Outcome(
            Kind kind,
            LoadoutEditorSaveClientRequest request,
            String reason
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.request=request;
            this.reason=reason;
        }

        boolean recognized(){
            return kind!=Kind.UNRECOGNIZED;
        }
    }

    private static final class PendingInventory {
        final List<LoadoutEditorSaveClientRequest.WireEntry> entries;
        final long packetSequence;

        PendingInventory(
            List<LoadoutEditorSaveClientRequest.WireEntry> entries,
            long packetSequence
        ){
            this.entries=entries;
            this.packetSequence=packetSequence;
        }
    }

    private PendingInventory pending;

    Outcome accept(String text,long packetSequence){
        if(text==null)
            return unrecognized(null);

        boolean inventory=
            text.equals("cld1")||
            text.startsWith("cld1 ");
        boolean equipment=
            text.equals("cld2")||
            text.startsWith("cld2 ");

        if(!inventory&&!equipment){
            String expired=
                expireForInterveningPacket(packetSequence);
            return unrecognized(expired);
        }

        if(packetSequence<1L)
            throw new IllegalArgumentException(
                "packetSequence="+packetSequence
            );

        if(inventory){
            try{
                List<LoadoutEditorSaveClientRequest.WireEntry> parsed=
                    parseEntries(
                        payload(text,"cld1"),
                        INVENTORY_ENTRY_COUNT,
                        false
                    );

                String reason=
                    pending==null
                    ?"INVENTORY_HALF_STAGED"
                    :"PREVIOUS_INVENTORY_HALF_REPLACED";

                pending=
                    new PendingInventory(
                        parsed,
                        packetSequence
                    );

                return new Outcome(
                    Kind.INVENTORY_STAGED,
                    null,
                    reason
                );
            }catch(IllegalArgumentException invalid){
                pending=null;
                return new Outcome(
                    Kind.REJECTED,
                    null,
                    "INVALID_CLD1_"+
                    sanitize(invalid.getMessage())
                );
            }
        }

        List<LoadoutEditorSaveClientRequest.WireEntry> parsedEquipment;
        try{
            parsedEquipment=
                parseEntries(
                    payload(text,"cld2"),
                    EQUIPMENT_ENTRY_COUNT,
                    true
                );
        }catch(IllegalArgumentException invalid){
            pending=null;
            return new Outcome(
                Kind.REJECTED,
                null,
                "INVALID_CLD2_"+
                sanitize(invalid.getMessage())
            );
        }

        if(pending==null)
            return new Outcome(
                Kind.REJECTED,
                null,
                "ORPHAN_CLD2_NO_INVENTORY_HALF"
            );

        PendingInventory inventoryHalf=pending;
        pending=null;

        if(packetSequence!=
                inventoryHalf.packetSequence+1L)
            return new Outcome(
                Kind.REJECTED,
                null,
                "NON_CONSECUTIVE_PAIR inventorySeq="+
                inventoryHalf.packetSequence+
                " equipmentSeq="+packetSequence
            );

        return new Outcome(
            Kind.COMPLETE,
            new LoadoutEditorSaveClientRequest(
                inventoryHalf.entries,
                parsedEquipment,
                inventoryHalf.packetSequence,
                packetSequence
            ),
            "PAIR_COMPLETE"
        );
    }

    String expireForInterveningPacket(
        long packetSequence
    ){
        if(pending==null)
            return null;

        if(packetSequence<=pending.packetSequence)
            return null;

        long staged=pending.packetSequence;
        pending=null;

        return "INVENTORY_HALF_EXPIRED_INTERVENING_PACKET stagedSeq="+
            staged+
            " currentSeq="+packetSequence;
    }

    boolean hasPendingInventory(){
        return pending!=null;
    }

    private static String payload(
        String text,
        String command
    ){
        String prefix=command+" ";
        if(!text.startsWith(prefix)||
           text.length()==prefix.length())
            throw new IllegalArgumentException(
                "MISSING_PAYLOAD"
            );
        return text.substring(prefix.length());
    }

    static List<LoadoutEditorSaveClientRequest.WireEntry>
        parseEntries(
            String payload,
            int expectedCount,
            boolean equipment
        ){
        String[] tokens=
            payload.split(" ",-1);

        if(tokens.length!=expectedCount)
            throw new IllegalArgumentException(
                "ENTRY_COUNT_"+tokens.length+
                "_EXPECTED_"+expectedCount
            );

        ArrayList<LoadoutEditorSaveClientRequest.WireEntry> out=
            new ArrayList<>(expectedCount);

        for(int i=0;i<tokens.length;i++){
            String token=tokens[i];
            int comma=token.indexOf(',');

            if(comma<=0||
               comma!=token.lastIndexOf(',')||
               comma==token.length()-1)
                throw new IllegalArgumentException(
                    "BAD_ENTRY_"+i
                );

            final int itemId;
            final int amount;
            try{
                itemId=
                    Integer.parseInt(
                        token.substring(0,comma)
                    );
                amount=
                    Integer.parseInt(
                        token.substring(comma+1)
                    );
            }catch(NumberFormatException invalid){
                throw new IllegalArgumentException(
                    "BAD_INTEGER_"+i,
                    invalid
                );
            }

            if(equipment&&
               EQUIPMENT_SOURCE_INDICES[i]<0&&
               (itemId!=0||amount!=0))
                throw new IllegalArgumentException(
                    "PLACEHOLDER_"+i+
                    "_MUST_BE_0_0"
                );

            out.add(
                new LoadoutEditorSaveClientRequest.WireEntry(
                    itemId,
                    amount
                )
            );
        }

        return Collections.unmodifiableList(out);
    }

    private static Outcome unrecognized(String reason){
        return new Outcome(
            Kind.UNRECOGNIZED,
            null,
            reason
        );
    }

    private static String sanitize(String value){
        if(value==null||value.isEmpty())
            return "INVALID";
        return value.replace(' ','_');
    }
}
