package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.collect.ImmutableList;
import java.util.*;

public class OneToManyMap<K, V> extends HashMap<K, List<V>> {

    Set<V> values = new HashSet<>();

    public void append(K key, V value) {
        compute(
                key,
                (k, v) -> {
                    if (v == null) {
                        v = new ArrayList<>();
                    }
                    v.add(value);
                    return v;
                });
        values.add(value);
    }

    @Override
    public List<V> get(Object key) {
        List<V> result = super.get(key);
        if (result == null) {
            return ImmutableList.of();
        }
        return result;
    }
}
