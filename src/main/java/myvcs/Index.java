package myvcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Index {
    private final Path indexPath;

    public Index(Path indexPath) {
        this.indexPath = indexPath;
    }

    public Map<String, String> read() throws IOException {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(indexPath)) {
            return map;
        }
        List<String> lines = Files.readAllLines(indexPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }

    public void write(Map<String, String> index) throws IOException {
        List<String> lines = new ArrayList<>();
        List<String> keys = new ArrayList<>(index.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            lines.add(key + "\t" + index.get(key));
        }
        Files.write(indexPath, lines, StandardCharsets.UTF_8);
    }
}
