package myvcs;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return;
        }

        Repository repo = Repository.open();
        String command = args[0];
        switch (command) {
            case "init":
                Repository.initRepo();
                break;
            case "add":
                requireArgs(args, 2);
                repo.add(args[1]);
                break;
            case "commit":
                requireArgs(args, 3);
                if (!"-m".equals(args[1])) {
                    throw new IllegalArgumentException("Usage: commit -m \"message\"");
                }
                repo.commit(args[2]);
                break;
            case "status":
                repo.status();
                break;
            case "log":
                repo.log();
                break;
            case "checkout":
                if (args.length >= 3 && "-b".equals(args[1])) {
                    repo.checkoutCreateBranch(args[2]);
                } else {
                    requireArgs(args, 2);
                    repo.checkout(args[1]);
                }
                break;
            case "branch":
                if (args.length == 1) {
                    repo.listBranches();
                } else {
                    repo.createBranch(args[1]);
                }
                break;
            case "diff":
                if (args.length >= 2 && "--staged".equals(args[1])) {
                    repo.diff(true);
                } else {
                    repo.diff(false);
                }
                break;
            default:
                printHelp();
                break;
        }
    }

    private static void printHelp() {
        System.out.println("myvcs (minimal VCS) commands:");
        System.out.println("  init");
        System.out.println("  add <path>");
        System.out.println("  commit -m \"message\"");
        System.out.println("  status");
        System.out.println("  log");
        System.out.println("  checkout <branch|commit>");
        System.out.println("  checkout -b <branch>");
        System.out.println("  branch [name]");
        System.out.println("  diff [--staged]");
    }

    private static void requireArgs(String[] args, int count) {
        if (args.length < count) {
            throw new IllegalArgumentException("Not enough arguments");
        }
    }
}
