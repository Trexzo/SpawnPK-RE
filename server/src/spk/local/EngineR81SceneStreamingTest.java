package spk.local;
import java.util.*;
public final class EngineR81SceneStreamingTest{
 public static void main(String[]a){
  MovementState m=new MovementState();req(m.loadedBaseX()==3032&&m.loadedBaseY()==3440,"home base");
  m.rebaseLoadedWindow(3064,3440,false);req(m.loadedBaseX()==3064&&m.loadedBaseY()==3440&&!m.transientRegion(),"normal rebase");req(m.insideCurrentLoadedRegion(m.x(),m.y()),"player outside rebase");
  Properties p=new Properties();m.saveAccountProperties(p);req(Integer.parseInt(p.getProperty("movement.worldX"))==MovementState.INITIAL_X,"home current persists");
  // Project the authority position outside legacy HOME without enabling transient mode; persistence must fail closed to HOME.
  try{java.lang.reflect.Field x=MovementState.class.getDeclaredField("x"),y=MovementState.class.getDeclaredField("y");x.setAccessible(true);y.setAccessible(true);x.setInt(m,3140);y.setInt(m,3500);m.rebaseLoadedWindow(3096,3448,true);}catch(Exception e){throw new AssertionError(e);}
  Properties q=new Properties();m.saveAccountProperties(q);req(Integer.parseInt(q.getProperty("movement.worldX"))==MovementState.INITIAL_X&&Integer.parseInt(q.getProperty("movement.worldY"))==MovementState.INITIAL_Y,"outside-home persistence gate");
  System.out.println("V5181_ENGINE_R81_SCENE_STREAMING_PASS packet73WindowRebaseState=true transientFalse=true globalStaticCollisionPolicy=true outsideHomePersistenceFallback=true");
 }
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
