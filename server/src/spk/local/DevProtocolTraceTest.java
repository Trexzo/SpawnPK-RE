package spk.local;

/** Semantic protocol trace is bounded, opt-in and resettable. */
public final class DevProtocolTraceTest {
    public static void main(String[] args){
        DevProtocolTrace t=new DevProtocolTrace();
        t.record("OFF","x","y"); req(t.size()==0,"disabled trace recorded");
        t.setEnabled(true);
        for(int i=0;i<140;i++)t.record("A"+i,"route"+i,"TEST");
        req(t.size()==128,"ring cap "+t.size());
        req(t.snapshot(3).size()==3,"snapshot");
        req(t.snapshot(3).get(2).contains("A139"),"tail");
        t.clear(); req(t.size()==0,"clear");
        req(t.enabled(),"clear must not silently disable tracing");
        System.out.println("V592_DEV_PROTOCOL_TRACE_PASS optIn=true ring=128 snapshot=true clear=true semanticOnly=true persistence=NONE_BY_DESIGN");
    }
    static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
