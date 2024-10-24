package nz.cri.gns.NZSHM22.opensha.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentCounter {
    ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    protected AtomicInteger getCounter() {
        Long id = Thread.currentThread().getId();
        AtomicInteger counter = counters.get(id);
        if (counter != null) {
            return counter;
        }
        counter = new AtomicInteger(0);
        AtomicInteger newCounter = counters.putIfAbsent(id, counter);
        return newCounter == null ? counter : newCounter;
    }

    public void inc(){
        AtomicInteger counter = getCounter();
        counter.incrementAndGet();
    }

    public int get() {
        return counters.values().stream().mapToInt(AtomicInteger::get).sum();
    }
}
