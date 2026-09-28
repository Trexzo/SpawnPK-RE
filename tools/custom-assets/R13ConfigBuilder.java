import java.nio.file.*;
import java.util.*;
import org.msgpack.jackson.dataformat.MessagePackMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public final class R13ConfigBuilder {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: base-i.bin output-i.bin");
        }

        MessagePackMapper mapper = new MessagePackMapper();
        TypeReference<Map<Integer, Map<String, Object>>> type =
            (TypeReference<Map<Integer, Map<String, Object>>>) (TypeReference<?>)
                Class.forName("rs.t.b").getConstructor().newInstance();

        Map<Integer, Map<String, Object>> root =
            mapper.readValue(Files.readAllBytes(Path.of(args[0])), type);

        if (root.containsKey(29999)) {
            throw new IllegalStateException("EXACT_BASE_COLLISION item=29999");
        }

        TreeMap<String, Object> record = new TreeMap<>();
        record.put("actions", Arrays.asList(null, null, null, null, "Drop"));
        record.put("modelId", 79999);
        record.put("name", "LocalLab Model Probe");
        record.put("offsets", Arrays.asList(0, 0));
        record.put("rotations", Arrays.asList(0, 0));
        record.put("tradeable", false);
        record.put("zoom", 1000);

        TreeMap<Integer, Map<String, Object>> output = new TreeMap<>(root);
        output.put(29999, record);

        byte[] blob = mapper.writeValueAsBytes(output);
        Files.write(Path.of(args[1]), blob);

        Map<Integer, Map<String, Object>> check = mapper.readValue(blob, type);
        Object model = check.get(29999).get("modelId");

        if (!(model instanceof Number) || ((Number) model).intValue() != 79999) {
            throw new AssertionError("model mismatch");
        }

        System.out.println(
            "R13_CONFIG_BUILD_PASS base=" + root.size() +
            " output=" + check.size() +
            " item=29999 model=79999 bytes=" + blob.length
        );
    }

    private R13ConfigBuilder() {}
}
