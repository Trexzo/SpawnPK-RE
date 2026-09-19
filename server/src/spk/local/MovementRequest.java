package spk.local;

import java.util.*;

/** Immutable decoded client walking request. */
final class MovementRequest {
    final int opcode;
    final boolean run;
    final int[] x;
    final int[] y;
    final byte[] telemetry;

    MovementRequest(int opcode, boolean run, int[] x, int[] y, byte[] telemetry) {
        this.opcode = opcode;
        this.run = run;
        this.x = x;
        this.y = y;
        this.telemetry = telemetry == null ? new byte[0] : telemetry.clone();
        if (x.length != y.length || x.length == 0) throw new IllegalArgumentException("waypoints");
    }

    int waypointCount() { return x.length; }
    int finalX() { return x[x.length - 1]; }
    int finalY() { return y[y.length - 1]; }

    @Override public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("MovementRequest{opcode=").append(opcode)
         .append(", run=").append(run)
         .append(", waypoints=[");
        for (int i = 0; i < x.length; i++) {
            if (i != 0) b.append(';');
            b.append(x[i]).append(',').append(y[i]);
        }
        return b.append("]}").toString();
    }
}
