package spk.local;

import java.util.*;

/**
 * Authoritative movement state. HOME behavior remains byte/semantic compatible
 * with the certified R5 baseline. Engine R6 adds an explicitly transient
 * localhost region window for cache-backed world exploration; it is never
 * persisted as an account spawn point.
 */
final class MovementState {
    static final int REGION_BASE_X=3032;
    static final int REGION_BASE_Y=3440;
    static final int REGION_SIZE=104;
    static final int INITIAL_X=3087;
    static final int INITIAL_Y=3495;
    static final int MAX_QUEUED_STEPS=128;

    private final ArrayDeque<Step> queue=new ArrayDeque<>();
    private int x=INITIAL_X,y=INITIAL_Y,plane=0;
    private boolean runByPacket,persistentRun;
    private int runEnergy=100;
    private long acceptedPaths,rejectedPaths;

    /** Current client packet-73 104x104 window. HOME by default. */
    private int loadedBaseX=REGION_BASE_X, loadedBaseY=REGION_BASE_Y;
    /** External region exploration is localhost-only and intentionally nonpersistent. */
    private boolean transientRegion;

    int x(){return x;} int y(){return y;} int plane(){return plane;}
    int queued(){return queue.size();}
    long acceptedPaths(){return acceptedPaths;} long rejectedPaths(){return rejectedPaths;}
    boolean persistentRun(){return persistentRun;}
    boolean togglePersistentRun(){persistentRun=!persistentRun;return persistentRun;}
    void setPersistentRun(boolean v){persistentRun=v;}
    int runEnergy(){return runEnergy;}
    void setRunEnergy(int v){runEnergy=Math.max(0,Math.min(100,v));}

    int loadedBaseX(){return loadedBaseX;} int loadedBaseY(){return loadedBaseY;}
    boolean transientRegion(){return transientRegion;}

    static final class Snapshot {
        final int x;
        final int y;
        final int plane;
        final ArrayDeque<Step> queue;
        final boolean runByPacket;
        final boolean persistentRun;
        final int runEnergy;
        final long acceptedPaths;
        final long rejectedPaths;
        final int loadedBaseX;
        final int loadedBaseY;
        final boolean transientRegion;

        private Snapshot(
            MovementState source
        ){
            x=source.x;
            y=source.y;
            plane=source.plane;
            queue=new ArrayDeque<>();
            for(Step step:source.queue)
                queue.addLast(
                    new Step(
                        step.x,
                        step.y,
                        step.dir
                    )
                );
            runByPacket=source.runByPacket;
            persistentRun=source.persistentRun;
            runEnergy=source.runEnergy;
            acceptedPaths=source.acceptedPaths;
            rejectedPaths=source.rejectedPaths;
            loadedBaseX=source.loadedBaseX;
            loadedBaseY=source.loadedBaseY;
            transientRegion=source.transientRegion;
        }
    }

    Snapshot snapshot(){
        return new Snapshot(this);
    }

    void restore(
        Snapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "movement snapshot"
            );

        x=snapshot.x;
        y=snapshot.y;
        plane=snapshot.plane;
        queue.clear();
        for(Step step:snapshot.queue)
            queue.addLast(
                new Step(
                    step.x,
                    step.y,
                    step.dir
                )
            );
        runByPacket=snapshot.runByPacket;
        persistentRun=snapshot.persistentRun;
        runEnergy=snapshot.runEnergy;
        acceptedPaths=snapshot.acceptedPaths;
        rejectedPaths=snapshot.rejectedPaths;
        loadedBaseX=snapshot.loadedBaseX;
        loadedBaseY=snapshot.loadedBaseY;
        transientRegion=snapshot.transientRegion;
    }

    static final class LoadedWindowSnapshot {
        final int baseX;
        final int baseY;
        final boolean transientRegion;

        private LoadedWindowSnapshot(
            int baseX,
            int baseY,
            boolean transientRegion
        ){
            this.baseX=baseX;
            this.baseY=baseY;
            this.transientRegion=transientRegion;
        }
    }

    LoadedWindowSnapshot snapshotLoadedWindow(){
        return new LoadedWindowSnapshot(
            loadedBaseX,
            loadedBaseY,
            transientRegion
        );
    }

    void restoreLoadedWindow(
        LoadedWindowSnapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "loaded window snapshot"
            );

        loadedBaseX=snapshot.baseX;
        loadedBaseY=snapshot.baseY;
        transientRegion=snapshot.transientRegion;
    }
    boolean inHomeWindow(){return loadedBaseX==REGION_BASE_X && loadedBaseY==REGION_BASE_Y && !transientRegion;}

    void restoreAccountState(
        boolean runEnabled,
        int energy,
        int worldX,
        int worldY,
        int restoredPlane
    ){
        persistentRun=runEnabled;
        setRunEnergy(energy);

        if(insideLoadedRegion(
                worldX,
                worldY
           )&&restoredPlane==0){
            x=worldX;
            y=worldY;
            plane=restoredPlane;
        }else{
            x=INITIAL_X;
            y=INITIAL_Y;
            plane=0;
        }

        loadedBaseX=REGION_BASE_X;
        loadedBaseY=REGION_BASE_Y;
        transientRegion=false;
        queue.clear();
        runByPacket=false;
    }

    void clearQueuedPath(){queue.clear();runByPacket=false;}

    /** Activate a cache-backed 104x104 packet-73 window. Local-dev only. */
    void enterTransientRegion(int worldX,int worldY,int newPlane,int baseX,int baseY){
        if(newPlane<0||newPlane>3)throw new IllegalArgumentException("plane 0..3");
        if(worldX<baseX||worldX>=baseX+REGION_SIZE||worldY<baseY||worldY>=baseY+REGION_SIZE)
            throw new IllegalArgumentException("target outside loaded window");
        x=worldX;y=worldY;plane=newPlane;loadedBaseX=baseX;loadedBaseY=baseY;transientRegion=true;
        clearQueuedPath();
    }
    void returnHome(){x=INITIAL_X;y=INITIAL_Y;plane=0;loadedBaseX=REGION_BASE_X;loadedBaseY=REGION_BASE_Y;transientRegion=false;clearQueuedPath();}

    /** R8.1 scene-streaming rebase: preserve authoritative world position and queued route. */
    void rebaseLoadedWindow(int baseX,int baseY,boolean transientFlag){
        if(x<baseX||x>=baseX+REGION_SIZE||y<baseY||y>=baseY+REGION_SIZE)
            throw new IllegalArgumentException("current position outside rebased window world="+x+","+y+" base="+baseX+","+baseY);
        loadedBaseX=baseX;loadedBaseY=baseY;transientRegion=transientFlag;
    }

    /** Reattach the current position to the certified HOME window without teleporting. */
    void restoreHomeWindowAtCurrentPosition(){
        if(!insideLoadedRegion(x,y))throw new IllegalStateException("current position is outside HOME window world="+x+","+y);
        loadedBaseX=REGION_BASE_X;loadedBaseY=REGION_BASE_Y;transientRegion=false;
    }

    /**
     * Presentation-only HOME window staging for a prepared respawn. The
     * canonical world position remains unchanged until PlayerLifecycleService
     * commits the exact prepared respawn after packet settlement.
     */
    void stageHomeWindowForPreparedRespawn(){
        loadedBaseX=REGION_BASE_X;
        loadedBaseY=REGION_BASE_Y;
        transientRegion=false;
    }

    boolean nearLoadedEdge(int margin){
        int lx=x-loadedBaseX,ly=y-loadedBaseY;
        return lx<margin||ly<margin||lx>=REGION_SIZE-margin||ly>=REGION_SIZE-margin;
    }

    boolean insideHomeInnerCore(int margin){
        return x>=REGION_BASE_X+margin&&x<REGION_BASE_X+REGION_SIZE-margin&&
               y>=REGION_BASE_Y+margin&&y<REGION_BASE_Y+REGION_SIZE-margin;
    }

    boolean insideCurrentLoadedRegion(int wx,int wy){
        return wx>=loadedBaseX&&wx<loadedBaseX+REGION_SIZE&&wy>=loadedBaseY&&wy<loadedBaseY+REGION_SIZE;
    }

    String accept(MovementRequest r){
        ArrayDeque<Step> proposed=new ArrayDeque<>();
        int cx=x,cy=y;
        for(int i=0;i<r.waypointCount();i++){
            int tx=r.x[i],ty=r.y[i];
            if(!insideCurrentLoadedRegion(tx,ty)){rejectedPaths++;return "REJECT_OUTSIDE_LOADED_REGION world="+tx+","+ty;}
            while(cx!=tx||cy!=ty){
                int nx=cx+Integer.signum(tx-cx),ny=cy+Integer.signum(ty-cy);
                int dir=direction(cx,cy,nx,ny);
                if(dir<0){rejectedPaths++;return "REJECT_NON_ADJACENT_STEP from="+cx+","+cy+" to="+nx+","+ny;}
                // Collision ownership is explicit even while HOME preserves the
                // certified client-submitted compatibility policy.
                CollisionStepAuthority.Policy collisionPolicy=
                    CollisionStepAuthority.movementPolicy(this);
                if(!CollisionStepAuthority.canStep(
                        collisionPolicy,
                        cx,cy,plane,nx,ny)){
                    rejectedPaths++;return "REJECT_STATIC_COLLISION from="+cx+","+cy+" to="+nx+","+ny+" plane="+plane;
                }
                proposed.addLast(new Step(nx,ny,dir));cx=nx;cy=ny;
                if(proposed.size()>MAX_QUEUED_STEPS){rejectedPaths++;return "REJECT_PATH_TOO_LONG max="+MAX_QUEUED_STEPS;}
            }
        }
        queue.clear();queue.addAll(proposed);runByPacket=r.run;acceptedPaths++;
        boolean effective=persistentRun||runByPacket;
        return "ACCEPTED queued="+queue.size()+" final="+r.finalX()+","+r.finalY()+" packetRun="+(runByPacket?1:0)+" persistentRun="+(persistentRun?1:0)+" effectiveRun="+(effective?1:0);
    }

    Tick advance(){
        if(queue.isEmpty())return null;
        int fx=x,fy=y;Step s1=queue.removeFirst();x=s1.x;y=s1.y;
        int dir2=-1,tiles=1;boolean running=false;
        if((persistentRun||runByPacket)&&!queue.isEmpty()){
            Step s2=queue.removeFirst();dir2=s2.dir;x=s2.x;y=s2.y;tiles=2;running=true;
        }
        return new Tick(fx,fy,x,y,s1.dir,dir2,tiles,running,queue.size());
    }

    /** Legacy HOME window authority retained for all certified HOME systems. */
    static boolean insideLoadedRegion(int wx,int wy){return wx>=3032&&wx<3136&&wy>=3440&&wy<3544;}

    static int direction(int x0,int y0,int x1,int y1){
        int dx=x1-x0,dy=y1-y0;
        if(dx==-1&&dy==1)return 0;
        if(dx==0&&dy==1)return 1;
        if(dx==1&&dy==1)return 2;
        if(dx==-1&&dy==0)return 3;
        if(dx==1&&dy==0)return 4;
        if(dx==-1&&dy==-1)return 5;
        if(dx==0&&dy==-1)return 6;
        if(dx==1&&dy==-1)return 7;
        return -1;
    }

    static final class Step{final int x,y,dir;Step(int x,int y,int dir){this.x=x;this.y=y;this.dir=dir;}}
    static final class Tick{
        final int fromX,fromY,toX,toY,dir1,dir2,tiles,remaining; final boolean running;
        Tick(int fx,int fy,int tx,int ty,int d1,int d2,int tiles,boolean running,int remaining){this.fromX=fx;this.fromY=fy;this.toX=tx;this.toY=ty;this.dir1=d1;this.dir2=d2;this.tiles=tiles;this.running=running;this.remaining=remaining;}
    }
}
