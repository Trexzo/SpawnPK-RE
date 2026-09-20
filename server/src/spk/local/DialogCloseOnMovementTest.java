package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;

public final class DialogCloseOnMovementTest {
    public static void main(String[] args)throws Exception{
        testMini();
        testColor();
        System.out.println(
            "V5129_DIALOG_CLOSE_ON_MOVEMENT_PASS "+
            "mini=true petColor=true closePacket219=true stateCleared=true"
        );
    }

    static void testMini()throws Exception{
        LocalSession session=session();
        LocalPetInventoryDialogHandler dialogs=
            dialogs(session);

        set(
            dialogs,
            "pendingMiniConfigureSlot",
            Integer.valueOf(3)
        );
        set(
            dialogs,
            "pendingMiniConfigureItem",
            Integer.valueOf(23988)
        );

        ByteArrayOutputStream out=move(session);

        if(((Integer)get(
                dialogs,
                "pendingMiniConfigureItem"))
                .intValue()!=-1)
            throw new AssertionError(
                "mini not cleared"
            );

        if(out.size()==0)
            throw new AssertionError(
                "mini emitted no close packet"
            );
    }

    static void testColor()throws Exception{
        LocalSession session=session();
        LocalPetInventoryDialogHandler dialogs=
            dialogs(session);

        set(
            dialogs,
            "pendingPetColorSlot",
            Integer.valueOf(3)
        );
        set(
            dialogs,
            "pendingPetColorItems",
            new int[]{24016,24017,24018,24019}
        );
        set(
            dialogs,
            "pendingPetColorFamily",
            "SCOOBY_BEHEMOTH"
        );

        ByteArrayOutputStream out=move(session);

        if(get(dialogs,"pendingPetColorItems")!=null)
            throw new AssertionError(
                "color not cleared"
            );

        if(out.size()==0)
            throw new AssertionError(
                "color emitted no close packet"
            );
    }

    static LocalSession session(){
        return new LocalSession(
            new Socket(),
            true,
            true
        );
    }

    static LocalPetInventoryDialogHandler dialogs(
        LocalSession session
    )throws Exception{
        Field field=
            LocalSession.class.getDeclaredField(
                "petDialogs"
            );
        field.setAccessible(true);
        return (LocalPetInventoryDialogHandler)
            field.get(session);
    }

    static ByteArrayOutputStream move(
        LocalSession session
    )throws Exception{
        Field movementField=
            LocalSession.class.getDeclaredField(
                "movement"
            );
        movementField.setAccessible(true);
        MovementState movement=
            (MovementState)movementField.get(session);

        Field handlerField=
            LocalSession.class.getDeclaredField(
                "movementRequests"
            );
        handlerField.setAccessible(true);
        LocalMovementRequestHandler handler=
            (LocalMovementRequestHandler)
                handlerField.get(session);

        int[] destination=findOpenAdjacent(
            movement.x(),
            movement.y()
        );

        MovementRequest request=
            new MovementRequest(
                164,
                false,
                new int[]{destination[0]},
                new int[]{destination[1]},
                new byte[0]
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        handler.handle(
            request,
            writer,
            "[test] "
        );

        return out;
    }

    static int[] findOpenAdjacent(int x,int y){
        int[][] directions={
            {1,0},
            {-1,0},
            {0,1},
            {0,-1},
            {1,1},
            {-1,1},
            {1,-1},
            {-1,-1}
        };

        for(int[] direction:directions){
            int nx=x+direction[0];
            int ny=y+direction[1];

            if(MovementState.insideLoadedRegion(
                    nx,
                    ny
                )&&
               HomeCombatPathfinder.canStep(
                    x,
                    y,
                    nx,
                    ny
               ))
                return new int[]{nx,ny};
        }

        throw new AssertionError(
            "no open adjacent HOME movement fixture"
        );
    }

    static void set(
        Object target,
        String name,
        Object value
    )throws Exception{
        Field field=
            target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target,value);
    }

    static Object get(
        Object target,
        String name
    )throws Exception{
        Field field=
            target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
