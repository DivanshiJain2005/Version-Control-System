package myvcs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkingTree {
    private WorkingTree() {}

    public static void collectFiles(Path root, Path target, List<Path> files) throws IOException {
        if (target.getFileName() != null && target.getFileName().toString().equals(Repository.VCS_DIR)) {
            return;
        }
        if (Files.isDirectory(target)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(target)) {
                for (Path child : stream) {
                    collectFiles(root, child, files);
                }
            }
        } else if (Files.isRegularFile(target)) {
            files.add(target);
        }
    }

    public static Map<String, String> readWorkingTreeHashes(Path root, ObjectStore objectStore) throws IOException {
        Map<String, String> working = new HashMap<>();
        List<Path> files = new ArrayList<>();
        collectFiles(root, root, files);
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace("\\", "/");
            String blobHash = objectStore.hashObject("blob", Files.readAllBytes(file));
            working.put(rel, blobHash);
        }
        return working;
    }
}
