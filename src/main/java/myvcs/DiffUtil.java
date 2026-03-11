package myvcs;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class DiffUtil {
    private DiffUtil() {}

    public static void printContentDiff(byte[] left, byte[] right) {
        if (left != null && isBinary(left) || right != null && isBinary(right)) {
            System.out.println("Binary files differ");
            return;
        }
        String leftText = left == null ? "" : new String(left, StandardCharsets.UTF_8);
        String rightText = right == null ? "" : new String(right, StandardCharsets.UTF_8);
        String[] leftLines = leftText.split("\n", -1);
        String[] rightLines = rightText.split("\n", -1);
        int max = Math.max(leftLines.length, rightLines.length);
        for (int i = 0; i < max; i++) {
            String l = i < leftLines.length ? leftLines[i] : null;
            String r = i < rightLines.length ? rightLines[i] : null;
            if (Objects.equals(l, r)) {
                if (l != null && !l.isEmpty()) {
                    System.out.println(" " + l);
                }
                continue;
            }
            if (l != null) {
                System.out.println("-" + l);
            }
            if (r != null) {
                System.out.println("+" + r);
            }
        }
    }

    private static boolean isBinary(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
