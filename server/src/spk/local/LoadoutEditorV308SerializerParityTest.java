package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class LoadoutEditorV308SerializerParityTest {
    private static final String EXACT_V308_SHA=
        "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6";

    public static void main(String[] args)
        throws Exception{
        Class<?> widgetClass=
            Class.forName("rs.n.e");
        Class<?> builderClass=
            Class.forName("rs.n.c.al");
        Class<?> clientClass=
            Class.forName("rs.Client");

        Object widgets=
            Array.newInstance(
                widgetClass,
                33020
            );
        Object inventory=
            widgetClass
                .getConstructor()
                .newInstance();
        Object equipment=
            widgetClass
                .getConstructor()
                .newInstance();

        int[] inventoryAmounts=
            new int[28];
        int[] inventoryStoredIds=
            new int[28];

        for(int i=0;i<28;i++){
            inventoryStoredIds[i]=1001+i;
            inventoryAmounts[i]=10+i;
        }

        int[] equipmentAmounts=
            new int[15];
        int[] equipmentStoredIds=
            new int[15];

        for(int i=0;i<15;i++){
            equipmentStoredIds[i]=2001+i;
            equipmentAmounts[i]=20+i;
        }

        widgetClass
            .getField("az")
            .set(
                inventory,
                inventoryStoredIds
            );
        widgetClass
            .getField("ax")
            .set(
                inventory,
                inventoryAmounts
            );
        widgetClass
            .getField("az")
            .set(
                equipment,
                equipmentStoredIds
            );
        widgetClass
            .getField("ax")
            .set(
                equipment,
                equipmentAmounts
            );

        Array.set(
            widgets,
            33002,
            inventory
        );
        Array.set(
            widgets,
            33003,
            equipment
        );

        Field allWidgets=
            widgetClass.getField("H");
        Object oldWidgets=
            allWidgets.get(null);

        Field cld1Field=
            clientClass.getField("ap");
        Field cld2Field=
            clientClass.getField("ao");
        Object oldCld1=cld1Field.get(null);
        Object oldCld2=cld2Field.get(null);

        try{
            allWidgets.set(null,widgets);

            builderClass
                .getMethod("h")
                .invoke(null);

            String cld1=
                String.valueOf(
                    cld1Field.get(null)
                );
            String cld2=
                String.valueOf(
                    cld2Field.get(null)
                );

            String expectedInventory=
                expectedInventory();
            String expectedEquipment=
                expectedEquipment();

            require(
                cld1.equals(
                    "::cld1 "+
                    expectedInventory
                ),
                "exact cld1 inventory serialization="+
                cld1
            );
            require(
                cld2.equals(
                    "::cld2 "+
                    expectedEquipment
                ),
                "exact cld2 equipment serialization="+
                cld2
            );

            LoadoutEditorCompatibilityPairer pairer=
                new LoadoutEditorCompatibilityPairer();

            LoadoutEditorCompatibilityPairer.Outcome
                first=
                    pairer.accept(
                        cld1.substring(2),
                        1L
                    );
            LoadoutEditorCompatibilityPairer.Outcome
                second=
                    pairer.accept(
                        cld2.substring(2),
                        2L
                    );

            require(
                first.kind==
                    LoadoutEditorCompatibilityPairer
                        .Kind.INVENTORY_STAGED,
                "exact cld1 stage"
            );
            require(
                second.kind==
                    LoadoutEditorCompatibilityPairer
                        .Kind.COMPLETE,
                "exact cld2 pair completion"
            );

            LoadoutEditorSaveClientRequest request=
                second.request;

            require(
                request.inventory().size()==28&&
                request.equipment().size()==14,
                "exact pair sizes"
            );

            System.out.println(
                "LOADOUT_EDITOR_V308_SERIALIZER_PARITY_PASS "+
                "clientSha256="+EXACT_V308_SHA+" "+
                "root=33000 "+
                "inventoryWidget=33002 "+
                "equipmentWidget=33003 "+
                "cld1=inventory28 "+
                "cld2=equipment14 "+
                "equipmentOrder=1,3,4,6,7,8,placeholder,10,placeholder,12,13,placeholder,14,5 "+
                "consecutivePair=true"
            );
        }finally{
            allWidgets.set(null,oldWidgets);
            cld1Field.set(null,oldCld1);
            cld2Field.set(null,oldCld2);
        }
    }

    private static String expectedInventory(){
        ArrayList<String> entries=
            new ArrayList<>();
        for(int i=0;i<28;i++)
            entries.add(
                (1000+i)+","+(10+i)
            );
        return String.join(" ",entries);
    }

    private static String expectedEquipment(){
        ArrayList<String> entries=
            new ArrayList<>();
        for(int source:
                LoadoutEditorCompatibilityPairer
                    .equipmentSourceIndices()){
            entries.add(
                source<0
                ?"0,0"
                :(2000+source)+","+(20+source)
            );
        }
        return String.join(" ",entries);
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LoadoutEditorV308SerializerParityTest(){}
}
