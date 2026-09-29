package spk.dev;

import java.lang.reflect.Field;

/**
 * LocalLab-only read-only observer for exact-v308 ordinary scene Walk-here.
 */
public final class WalkRouteTrace {
    private static final Field LOCAL_PLAYER;
    private static final Field ACTOR_PATH_X;
    private static final Field ACTOR_PATH_Y;
    private static final Field ALTERNATE_ROUTE;
    private static final Field RESOLVED_X;
    private static final Field RESOLVED_Y;

    static {
        try {
            Class<?> client=Class.forName("rs.Client");
            Class<?> actor=Class.forName("rs.a.k");

            LOCAL_PLAYER=client.getDeclaredField("eR");
            LOCAL_PLAYER.setAccessible(true);
            ACTOR_PATH_X=actor.getDeclaredField("l");
            ACTOR_PATH_X.setAccessible(true);
            ACTOR_PATH_Y=actor.getDeclaredField("k");
            ACTOR_PATH_Y.setAccessible(true);
            ALTERNATE_ROUTE=client.getDeclaredField("oB");
            ALTERNATE_ROUTE.setAccessible(true);
            RESOLVED_X=client.getDeclaredField("gd");
            RESOLVED_X.setAccessible(true);
            RESOLVED_Y=client.getDeclaredField("ge");
            RESOLVED_Y.setAccessible(true);
        }
        catch(Exception error){
            throw new ExceptionInInitializerError(error);
        }
    }

    private WalkRouteTrace(){}

    public static void observe(
        Object client,
        int pickedX,
        int pickedY,
        boolean result
    ){
        try {
            int startX=-1;
            int startY=-1;
            Object local=LOCAL_PLAYER.get(null);

            if(local!=null){
                int[] xs=(int[])ACTOR_PATH_X.get(local);
                int[] ys=(int[])ACTOR_PATH_Y.get(local);

                if(xs!=null&&xs.length>0) startX=xs[0];
                if(ys!=null&&ys.length>0) startY=ys[0];
            }

            int alternate=ALTERNATE_ROUTE.getInt(client);
            int resolvedX=result?RESOLVED_X.getInt(client):-1;
            int resolvedY=result?RESOLVED_Y.getInt(client):-1;

            System.out.println(
                "LOCALLAB_WALK_ROUTE_TRACE "+
                "picked="+pickedX+","+pickedY+" "+
                "start="+startX+","+startY+" "+
                "fallback=true "+
                "result="+result+" "+
                "alternate="+(alternate!=0)+" "+
                "resolved="+resolvedX+","+resolvedY
            );
        }
        catch(Throwable error){
            System.out.println(
                "LOCALLAB_WALK_ROUTE_TRACE_ERROR "+
                error.getClass().getName()+":"+
                String.valueOf(error.getMessage())
            );
        }
    }
}
