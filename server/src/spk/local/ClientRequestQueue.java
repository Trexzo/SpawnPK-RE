package spk.local;

import java.util.ArrayDeque;

/**
 * Bounded FIFO between exact packet decoding and semantic World-thread routing.
 *
 * The decoder fails closed on overflow; requests are never silently dropped or
 * allowed to grow an unbounded session-local backlog.
 */
final class ClientRequestQueue {
    static final int DEFAULT_CAPACITY=64;

    private final int capacity;
    private final ArrayDeque<ClientRequest>
        queue=new ArrayDeque<>();

    ClientRequestQueue(){
        this(DEFAULT_CAPACITY);
    }

    ClientRequestQueue(int capacity){
        if(capacity<1)
            throw new IllegalArgumentException(
                "capacity"
            );
        this.capacity=capacity;
    }

    synchronized boolean offer(
        ClientRequest request
    ){
        if(request==null)
            throw new NullPointerException(
                "request"
            );

        if(queue.size()>=capacity)
            return false;

        queue.addLast(request);
        return true;
    }

    synchronized ClientRequest poll(){
        return queue.pollFirst();
    }

    synchronized int size(){
        return queue.size();
    }

    int capacity(){
        return capacity;
    }
}
