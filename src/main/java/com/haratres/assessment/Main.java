package com.haratres.assessment;

import com.haratres.assessment.charactercount.CharacterCounter;
import com.haratres.assessment.charactercount.CharacterCounterCli;
import com.haratres.assessment.console.ConsoleInput;
import com.haratres.assessment.shop.CartPricer;
import com.haratres.assessment.shop.ShopCli;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Entry point. Selects a command and wires its collaborators.
 *
 * <p>All three standard streams are opened as explicit UTF-8 so that Turkish
 * prompts and results display correctly on Windows (where the JVM default
 * encoding is often windows-1252 or CP437) as well as on macOS and Linux.
 * On Windows, the terminal or console must also support UTF-8:
 * run {@code chcp 65001} in CMD, or use Windows Terminal / PowerShell 7+.
 */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        var err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Reader in) {
        if (args.length == 0 || (args.length == 1 && "--help".equals(args[0]))) {
            out.println("Usage: java -jar assessment.jar <count|shop>");
            return 0;
        }
        if (args.length != 1 || !("count".equals(args[0]) || "shop".equals(args[0]))) {
            err.println("Unknown command. Use --help for usage.");
            return 2;
        }
        ConsoleInput input = new ConsoleInput(in);
        if ("count".equals(args[0])) {
            return new CharacterCounterCli(new CharacterCounter()).run(input, out, err);
        }
        return new ShopCli(new CartPricer()).run(input, out, err);
    }
}
