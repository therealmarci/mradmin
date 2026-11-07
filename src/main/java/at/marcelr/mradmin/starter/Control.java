package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * {@code mrserver stop|restart|cancel|console}: asks MRadmin for a countdown (file {@code request}, MRadmin answers
 * in {@code request.answer}). If MRadmin does not answer (not installed, still starting, hanging), stop and restart
 * happen right away over the console.
 */
final class Control {

    /** How long to wait for MRadmin's answer. */
    private static final long ANSWER_MS = 6000L;

    private Control() {
    }

    static int request(StarterFiles files, String kind, List<String> args) throws IOException, InterruptedException {
        // [time] [reason ...] - without a time, MRadmin's countdown applies and everything is the reason.
        String time = "";
        List<String> rest = args;
        if (!args.isEmpty() && args.getFirst().toLowerCase(Locale.ROOT).matches("now|jetzt|[0-9]+|([0-9]+[smhd])+")) {
            time = args.getFirst().toLowerCase(Locale.ROOT);
            rest = args.subList(1, args.size());
        }
        String reason = String.join(" ", rest).replaceAll("[\\r\\n]+", " ").strip();
        boolean running = Main.supervisorRunning(files);
        if (!running) {
            if (kind.equals("restart")) {
                System.out.println("The server is not running - starting it.");
                return Service.start(files);
            }
            System.out.println("The server is not running.");
            return kind.equals("stop") ? 0 : 1;
        }
        var answer = files.request().resolveSibling("request.answer");
        Files.deleteIfExists(answer);
        StarterFiles.writeText(files.request(), kind + " " + (time.isEmpty() ? "-" : time) + " " + System.currentTimeMillis()
                + (reason.isEmpty() || kind.equals("cancel") ? "" : " " + reason));
        long until = System.currentTimeMillis() + ANSWER_MS;
        while (System.currentTimeMillis() < until) {
            Thread.sleep(200L);
            String text = StarterFiles.readText(answer);
            if (text != null) {
                Files.deleteIfExists(answer);
                System.out.println(text);
                return text.startsWith("OK") ? 0 : 1;
            }
        }
        Files.deleteIfExists(files.request());
        if (kind.equals("cancel")) {
            System.out.println("MRadmin did not answer - nothing to cancel.");
            return 1;
        }
        System.out.println("MRadmin did not answer - " + kind + " without a countdown.");
        StarterFiles.writeText(files.action(), kind.equals("restart") ? StarterFiles.ACTION_RESTART : StarterFiles.ACTION_STOP);
        StarterFiles.appendLine(files.consoleIn(), "stop");
        return 0;
    }

    static int console(StarterFiles files, List<String> args) throws IOException {
        String command = String.join(" ", args).trim();
        if (command.isEmpty()) {
            System.out.println("No command given, e.g.: mrserver console \"say Hello\"");
            return 2;
        }
        if (!Main.supervisorRunning(files)) {
            System.out.println("The server is not running.");
            return 1;
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        StarterFiles.appendLine(files.consoleIn(), command);
        return 0;
    }
}
