package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads a U Canterbury mapping file from section id to patch id.
 * Section id is for crustal, Puysegur, or Hikurangi rupture set.
 * An index offset can be used to move this into the combined rupture set id space.
 */
public class UCMappingsFile {

    static Map<Integer, List<Integer>> read(String fileName, int sectionOffset) throws FileNotFoundException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName));

        Gson gson = new Gson();
        Map<String, Object> json = (Map<String, Object>) gson.fromJson(bufferedReader, Object.class);

        Map<Integer, List<Integer>> result = new HashMap<>();

        json.keySet().forEach(k -> {
            int key = Integer.parseInt(k);
            List<Double> ps = (List<Double>) json.get(k);
            List<Integer> patches = ps.stream().map(Double::intValue).collect(Collectors.toList());
            result.put(key + sectionOffset, patches);
        });
        return result;
    }
}
