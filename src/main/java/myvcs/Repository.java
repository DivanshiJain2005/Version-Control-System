package myvcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Repository {
    static final String VCS_DIR = ".myvcs";
    private static final String OBJECTS_DIR = "objects";
    private static final String REFS_DIR = "refs";
    private static final String HEADS_DIR = "heads";
    private static final String HEAD_FILE = "HEAD";
    private static final String INDEX_FILE = "index";

    private final Path root;
    private final Path vcsDir;
    private final ObjectStore objectStore;
    private final Index index;

    private Repository(Path root) {
        this.root = root;
        this.vcsDir = root.resolve(VCS_DIR);
        this.objectStore = new ObjectStore(vcsDir.resolve(OBJECTS_DIR));
        this.index = new Index(vcsDir.resolve(INDEX_FILE));
    }

    public static Repository open() {
        return new Repository(repoRoot());
    }

    public static void initRepo() throws IOException {
        Path root = repoRoot();
        Path vcsDir = root.resolve(VCS_DIR);
        Files.createDirectories(vcsDir.resolve(OBJECTS_DIR));
        Files.createDirectories(vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR));
        Files.writeString(vcsDir.resolve(HEAD_FILE), "refs/heads/main\n", StandardCharsets.UTF_8);
        Files.writeString(vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve("main"), "", StandardCharsets.UTF_8);
        Files.writeString(vcsDir.resolve(INDEX_FILE), "", StandardCharsets.UTF_8);
        System.out.println("Initialized empty repository in " + vcsDir.toAbsolutePath());
    }

    public void add(String target) throws IOException {
        ensureRepo();
        Path absoluteTarget = root.resolve(target).normalize();
        Map<String, String> entries = index.read();

        if (!Files.exists(absoluteTarget)) {
            throw new IllegalArgumentException("Path does not exist: " + target);
        }

        List<Path> files = new ArrayList<>();
        WorkingTree.collectFiles(root, absoluteTarget, files);

        for (Path file : files) {
            byte[] data = Files.readAllBytes(file);
            String blobHash = objectStore.writeObject("blob", data);
            String rel = root.relativize(file).toString().replace("\\", "/");
            entries.put(rel, blobHash);
        }

        index.write(entries);
        System.out.println("Added " + files.size() + " file(s) to index");
    }

    public void commit(String message) throws IOException {
        ensureRepo();
        Map<String, String> entries = index.read();
        if (entries.isEmpty()) {
            System.out.println("Nothing to commit (index is empty)");
            return;
        }

        String treeHash = writeTree(entries);
        String parent = readHeadCommit();
        String author = System.getProperty("user.name", "unknown");
        String time = String.valueOf(Instant.now().toEpochMilli());

        StringBuilder body = new StringBuilder();
        body.append("tree ").append(treeHash).append("\n");
        if (parent != null && !parent.isBlank()) {
            body.append("parent ").append(parent).append("\n");
        }
        body.append("author ").append(author).append("\n");
        body.append("time ").append(time).append("\n");
        body.append("message ").append(message).append("\n");

        String commitHash = objectStore.writeObject("commit", body.toString().getBytes(StandardCharsets.UTF_8));
        updateHeadCommit(commitHash);
        System.out.println("Committed as " + commitHash);
    }

    public void status() throws IOException {
        ensureRepo();
        Map<String, String> entries = index.read();
        Map<String, String> headTree = readHeadTree();
        Map<String, String> working = WorkingTree.readWorkingTreeHashes(root, objectStore);

        List<String> staged = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> untracked = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (Map.Entry<String, String> entry : working.entrySet()) {
            String path = entry.getKey();
            String workingHash = entry.getValue();
            String indexHash = entries.get(path);
            if (indexHash == null) {
                untracked.add(path);
            } else if (!Objects.equals(indexHash, workingHash)) {
                modified.add(path);
            }
        }

        for (String path : entries.keySet()) {
            if (!working.containsKey(path)) {
                deleted.add(path);
            }
        }

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String path = entry.getKey();
            String indexHash = entry.getValue();
            String headHash = headTree.get(path);
            if (!Objects.equals(indexHash, headHash)) {
                staged.add(path);
            }
        }

        if (staged.isEmpty() && modified.isEmpty() && untracked.isEmpty() && deleted.isEmpty()) {
            System.out.println("Working tree clean");
            return;
        }

        printList("Staged changes", staged);
        printList("Modified (not staged)", modified);
        printList("Deleted (not staged)", deleted);
        printList("Untracked files", untracked);
    }

    public void log() throws IOException {
        ensureRepo();
        String commitHash = readHeadCommit();
        if (commitHash == null || commitHash.isBlank()) {
            System.out.println("No commits yet");
            return;
        }
        while (commitHash != null && !commitHash.isBlank()) {
            ObjectStore.ObjectData obj = objectStore.readObject(commitHash);
            String body = new String(obj.body(), StandardCharsets.UTF_8);
            String message = findLine(body, "message");
            String time = findLine(body, "time");
            String author = findLine(body, "author");
            System.out.println("commit " + commitHash);
            System.out.println("Author: " + author);
            System.out.println("Time: " + time);
            System.out.println("    " + message);
            System.out.println();
            commitHash = findLine(body, "parent");
        }
    }

    public void checkout(String nameOrHash) throws IOException {
        ensureRepo();
        if (!isWorkingTreeClean()) {
            System.out.println("Cannot checkout: working tree has uncommitted changes");
            return;
        }

        String commitHash = resolveCommit(nameOrHash);
        if (commitHash == null || commitHash.isBlank()) {
            throw new IllegalArgumentException("Unknown branch or commit: " + nameOrHash);
        }

        Map<String, String> targetTree = readCommitTree(commitHash);
        Map<String, String> currentTree = readHeadTree();

        for (Map.Entry<String, String> entry : targetTree.entrySet()) {
            String rel = entry.getKey();
            String blobHash = entry.getValue();
            ObjectStore.ObjectData blob = objectStore.readObject(blobHash);
            if (!"blob".equals(blob.type())) {
                throw new IOException("Expected blob object for " + rel);
            }
            Path filePath = root.resolve(rel);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, blob.body());
        }

        for (String path : currentTree.keySet()) {
            if (!targetTree.containsKey(path)) {
                Path filePath = root.resolve(path);
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    Files.delete(filePath);
                }
            }
        }

        index.write(targetTree);
        updateHeadForCheckout(nameOrHash, commitHash);
        System.out.println("Checked out " + nameOrHash);
    }

    public void checkoutCreateBranch(String branch) throws IOException {
        ensureRepo();
        if (!isWorkingTreeClean()) {
            System.out.println("Cannot checkout: working tree has uncommitted changes");
            return;
        }
        String headCommit = readHeadCommit();
        if (headCommit == null || headCommit.isBlank()) {
            System.out.println("No commits yet");
            return;
        }
        createBranch(branch);
        checkout(branch);
    }

    public void listBranches() throws IOException {
        ensureRepo();
        String current = currentBranch();
        Path heads = vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR);
        if (!Files.exists(heads)) {
            System.out.println("No branches");
            return;
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(heads)) {
            for (Path child : stream) {
                if (Files.isRegularFile(child)) {
                    names.add(child.getFileName().toString());
                }
            }
        }
        Collections.sort(names);
        for (String name : names) {
            if (name.equals(current)) {
                System.out.println("* " + name);
            } else {
                System.out.println("  " + name);
            }
        }
    }

    public void createBranch(String name) throws IOException {
        ensureRepo();
        String headCommit = readHeadCommit();
        if (headCommit == null || headCommit.isBlank()) {
            System.out.println("No commits yet");
            return;
        }
        Path branchPath = vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(name);
        if (Files.exists(branchPath)) {
            System.out.println("Branch already exists: " + name);
            return;
        }
        Files.createDirectories(branchPath.getParent());
        Files.writeString(branchPath, headCommit + "\n", StandardCharsets.UTF_8);
        System.out.println("Created branch " + name);
    }

    public void diff(boolean staged) throws IOException {
        ensureRepo();
        Map<String, String> left;
        Map<String, String> right;
        if (staged) {
            left = readHeadTree();
            right = index.read();
        } else {
            left = index.read();
            right = WorkingTree.readWorkingTreeHashes(root, objectStore);
        }

        List<String> paths = new ArrayList<>();
        for (String p : left.keySet()) {
            if (!paths.contains(p)) {
                paths.add(p);
            }
        }
        for (String p : right.keySet()) {
            if (!paths.contains(p)) {
                paths.add(p);
            }
        }
        Collections.sort(paths);

        boolean any = false;
        for (String path : paths) {
            String leftHash = left.get(path);
            String rightHash = right.get(path);
            if (Objects.equals(leftHash, rightHash)) {
                continue;
            }
            any = true;
            System.out.println("diff -- " + path);
            if (leftHash == null) {
                System.out.println("added");
                DiffUtil.printContentDiff(null, contentForPath(path, rightHash, false));
            } else if (rightHash == null) {
                System.out.println("deleted");
                DiffUtil.printContentDiff(contentForPath(path, leftHash, true), null);
            } else {
                DiffUtil.printContentDiff(
                    contentForPath(path, leftHash, true),
                    contentForPath(path, rightHash, false)
                );
            }
            System.out.println();
        }

        if (!any) {
            System.out.println("No differences");
        }
    }

    private static Path repoRoot() {
        return Paths.get("").toAbsolutePath();
    }

    private void ensureRepo() {
        if (!Files.exists(vcsDir)) {
            throw new IllegalStateException("Not a repository. Run `init` first.");
        }
    }

    private String writeTree(Map<String, String> entries) throws IOException {
        List<String> keys = new ArrayList<>(entries.keySet());
        Collections.sort(keys);
        StringBuilder body = new StringBuilder();
        for (String key : keys) {
            body.append(key).append("\t").append(entries.get(key)).append("\n");
        }
        return objectStore.writeObject("tree", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> readTree(String hash) throws IOException {
        Map<String, String> map = new HashMap<>();
        if (hash == null || hash.isBlank()) {
            return map;
        }
        ObjectStore.ObjectData obj = objectStore.readObject(hash);
        String body = new String(obj.body(), StandardCharsets.UTF_8);
        for (String line : body.split("\n")) {
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

    private Map<String, String> readHeadTree() throws IOException {
        String commitHash = readHeadCommit();
        if (commitHash == null || commitHash.isBlank()) {
            return new HashMap<>();
        }
        ObjectStore.ObjectData commit = objectStore.readObject(commitHash);
        String body = new String(commit.body(), StandardCharsets.UTF_8);
        String treeHash = findLine(body, "tree");
        return readTree(treeHash);
    }

    private String readHeadCommit() throws IOException {
        Path headPath = vcsDir.resolve(HEAD_FILE);
        if (!Files.exists(headPath)) {
            return null;
        }
        String ref = Files.readString(headPath, StandardCharsets.UTF_8).trim();
        if (ref.isBlank()) {
            return null;
        }
        if (ref.startsWith("refs/")) {
            Path refPath = vcsDir.resolve(ref);
            if (!Files.exists(refPath)) {
                return null;
            }
            String commitHash = Files.readString(refPath, StandardCharsets.UTF_8).trim();
            return commitHash.isBlank() ? null : commitHash;
        }
        return ref;
    }

    private void updateHeadCommit(String commitHash) throws IOException {
        Path headPath = vcsDir.resolve(HEAD_FILE);
        String ref = Files.readString(headPath, StandardCharsets.UTF_8).trim();
        if (ref.startsWith("refs/")) {
            Path refPath = vcsDir.resolve(ref);
            Files.writeString(refPath, commitHash + "\n", StandardCharsets.UTF_8);
        } else {
            Files.writeString(headPath, commitHash + "\n", StandardCharsets.UTF_8);
        }
    }

    private String resolveCommit(String nameOrHash) throws IOException {
        Path branchPath = vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(nameOrHash);
        if (Files.exists(branchPath)) {
            String commitHash = Files.readString(branchPath, StandardCharsets.UTF_8).trim();
            return commitHash.isBlank() ? null : commitHash;
        }
        Path objectPath = objectStore.objectPath(nameOrHash);
        if (Files.exists(objectPath)) {
            return nameOrHash;
        }
        return null;
    }

    private Map<String, String> readCommitTree(String commitHash) throws IOException {
        ObjectStore.ObjectData commit = objectStore.readObject(commitHash);
        if (!"commit".equals(commit.type())) {
            throw new IOException("Not a commit object: " + commitHash);
        }
        String body = new String(commit.body(), StandardCharsets.UTF_8);
        String treeHash = findLine(body, "tree");
        return readTree(treeHash);
    }

    private void updateHeadForCheckout(String nameOrHash, String commitHash) throws IOException {
        Path headPath = vcsDir.resolve(HEAD_FILE);
        Path branchPath = vcsDir.resolve(REFS_DIR).resolve(HEADS_DIR).resolve(nameOrHash);
        if (Files.exists(branchPath)) {
            Files.writeString(headPath, "refs/heads/" + nameOrHash + "\n", StandardCharsets.UTF_8);
        } else {
            Files.writeString(headPath, commitHash + "\n", StandardCharsets.UTF_8);
        }
    }

    private String currentBranch() throws IOException {
        Path headPath = vcsDir.resolve(HEAD_FILE);
        if (!Files.exists(headPath)) {
            return null;
        }
        String ref = Files.readString(headPath, StandardCharsets.UTF_8).trim();
        if (ref.startsWith("refs/heads/")) {
            return ref.substring("refs/heads/".length());
        }
        return null;
    }

    private boolean isWorkingTreeClean() throws IOException {
        Map<String, String> entries = index.read();
        Map<String, String> headTree = readHeadTree();
        Map<String, String> working = WorkingTree.readWorkingTreeHashes(root, objectStore);

        if (!entries.equals(headTree)) {
            return false;
        }

        if (working.size() != entries.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : working.entrySet()) {
            if (!Objects.equals(entries.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private byte[] contentForPath(String path, String hash, boolean fromObject) throws IOException {
        if (hash == null) {
            return null;
        }
        if (fromObject) {
            ObjectStore.ObjectData obj = objectStore.readObject(hash);
            return obj.body();
        }
        Path filePath = root.resolve(path);
        if (!Files.exists(filePath)) {
            return null;
        }
        return Files.readAllBytes(filePath);
    }

    private static String findLine(String body, String key) {
        for (String line : body.split("\n")) {
            if (line.startsWith(key + " ")) {
                return line.substring((key + " ").length()).trim();
            }
        }
        return "";
    }

    private static void printList(String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        Collections.sort(items);
        System.out.println(title + ":");
        for (String item : items) {
            System.out.println("  " + item);
        }
        System.out.println();
    }
}
