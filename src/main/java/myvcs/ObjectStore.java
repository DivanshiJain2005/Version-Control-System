package myvcs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ObjectStore {
    private final Path objectsDir;

    public ObjectStore(Path objectsDir) {
        this.objectsDir = objectsDir;
    }

    public String writeObject(String type, byte[] body) throws IOException {
        String hash = hashObject(type, body);
        Path objectPath = objectPath(hash);
        if (Files.exists(objectPath)) {
            return hash;
        }
        Files.createDirectories(objectPath.getParent());
        byte[] header = (type + " " + body.length + "\n").getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(objectPath))) {
            out.write(header);
            out.write(body);
        }
        return hash;
    }

    public ObjectData readObject(String hash) throws IOException {
        Path objectPath = objectPath(hash);
        if (!Files.exists(objectPath)) {
            throw new IOException("Object not found: " + hash);
        }
        byte[] all;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(objectPath))) {
            all = in.readAllBytes();
        }
        int split = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == (byte) '\n') {
                split = i;
                break;
            }
        }
        if (split < 0) {
            throw new IOException("Invalid object format: " + hash);
        }
        String header = new String(all, 0, split, StandardCharsets.UTF_8);
        String[] parts = header.split(" ", 2);
        String type = parts[0];
        byte[] body = new byte[all.length - split - 1];
        System.arraycopy(all, split + 1, body, 0, body.length);
        return new ObjectData(type, body);
    }

    public String hashObject(String type, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = (type + " " + body.length + "\n").getBytes(StandardCharsets.UTF_8);
            digest.update(header);
            digest.update(body);
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Path objectPath(String hash) {
        String dir = hash.substring(0, 2);
        String file = hash.substring(2);
        return objectsDir.resolve(dir).resolve(file);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record ObjectData(String type, byte[] body) {}
}
