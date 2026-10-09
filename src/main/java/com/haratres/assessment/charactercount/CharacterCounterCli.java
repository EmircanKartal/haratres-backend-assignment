package com.haratres.assessment.charactercount;

import com.haratres.assessment.console.ConsoleInput;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Console adapter for the character-counting command.
 *
 * <p>Runs the following interaction:
 * <ol>
 *   <li>Ask for a positive maximum character count (once).
 *   <li>Ask for a sentence within that limit (once).
 *   <li>Ask Evet/Hayır for case sensitivity (once).
 *   <li>Ask for exactly one character; print the occurrence count; loop back
 *       to step 4 indefinitely until the user presses Ctrl+D (EOF on
 *       macOS/Linux) or Ctrl+Z then Enter (EOF on Windows).
 * </ol>
 * On EOF or I/O failure at any step the command exits cleanly with status 0
 * if at least one character was analysed, or status 1 if EOF arrived before
 * the first result.
 */
public final class CharacterCounterCli {
    private final CharacterCounter counter;

    public CharacterCounterCli(CharacterCounter counter) {
        if (counter == null) {
            throw new IllegalArgumentException("counter must not be null");
        }
        this.counter = counter;
    }

    public int run(ConsoleInput input, PrintStream out, PrintStream err) {
        // --- Step 1: maximum character count ---
        int maxLength = readPositiveInt(input, out, err, "Maksimum karakter sayısı belirleyin: ");
        if (maxLength < 0) {
            return 1;
        }

        // --- Step 2: sentence within the limit ---
        String sentence = readSentence(input, out, err, maxLength);
        if (sentence == null) {
            return 1;
        }

        // --- Step 3: case sensitivity ---
        Boolean caseSensitive = readYesNo(input, out, err,
                "Büyük/küçük harf duyarlılığı aktif olsun mu? (Evet/Hayır): ");
        if (caseSensitive == null) {
            return 1;
        }

        // --- Step 4: character search loop (runs until EOF / Ctrl+D / Ctrl+Z) ---
        out.println("Çıkmak için Ctrl+D tuşlayın (Windows'ta Ctrl+Z ardından Enter).");
        boolean analysedAtLeastOne = false;
        while (true) {
            int searchCodePoint = readSingleCodePointOrEof(input, out, err, "Analiz etmek için bir harf girin: ");
            if (searchCodePoint == -1) {
                // EOF — exit cleanly
                break;
            }
            long count = counter.countOccurrences(sentence, searchCodePoint, caseSensitive);
            String charDisplay = new String(Character.toChars(searchCodePoint));
            out.println("Girilen cümlede '" + charDisplay + "' harfi toplamda " + count + " defa geçmektedir.");
            analysedAtLeastOne = true;
        }

        return analysedAtLeastOne ? 0 : 1;
    }

    // Returns the parsed positive integer, or -1 on EOF/error.
    private static int readPositiveInt(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return -1;
            }
            try {
                int value = Integer.parseInt(line.trim());
                if (value > 0) {
                    return value;
                }
                err.println("Lütfen sıfırdan büyük bir sayı giriniz.");
            } catch (NumberFormatException e) {
                err.println("Lütfen geçerli bir sayı giriniz.");
            }
        }
    }

    // Returns the sentence string, or null on EOF/error.
    private static String readSentence(ConsoleInput input, PrintStream out, PrintStream err, int maxLength) {
        while (true) {
            out.print("Lütfen bir cümle girin: ");
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                err.println("Cümle boş olamaz.");
                continue;
            }
            int length = CharacterCounter.codePointLength(line);
            if (length > maxLength) {
                err.println("Girilen cümle karakter limitini aşıyor (" + length + " > " + maxLength + "). Lütfen tekrar giriniz.");
                continue;
            }
            return line;
        }
    }

    // Returns Boolean.TRUE for Evet, Boolean.FALSE for Hayır, null on EOF/error.
    private static Boolean readYesNo(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return null;
            }
            String trimmed = line.trim();
            if ("Evet".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("Hayır".equalsIgnoreCase(trimmed) || "Hayir".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
            err.println("Lütfen geçerli bir cevap giriniz.");
        }
    }

    // Returns the code point, or -1 on EOF/error.
    private static int readSingleCodePoint(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return -1;
            }
            if (line.isEmpty()) {
                err.println("Geçerli bir karakter giriniz.");
                continue;
            }
            int count = line.codePointCount(0, line.length());
            if (count != 1) {
                err.println("Geçerli bir karakter giriniz.");
                continue;
            }
            return line.codePointAt(0);
        }
    }

    /**
     * Like {@link #readSingleCodePoint} but returns {@code -1} silently on EOF
     * (no error message printed) so the caller can exit the analysis loop cleanly.
     * Invalid input (empty or multi-code-point) still prints an error and retries.
     */
    private static int readSingleCodePointOrEof(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line;
            try {
                line = input.readLine();
            } catch (IOException e) {
                err.println("Okuma hatası: " + e.getMessage());
                return -1;
            }
            if (line == null) {
                // EOF — clean exit, no error
                out.println();
                return -1;
            }
            if (line.isEmpty()) {
                err.println("Geçerli bir karakter giriniz.");
                continue;
            }
            int cpCount = line.codePointCount(0, line.length());
            if (cpCount != 1) {
                err.println("Geçerli bir karakter giriniz.");
                continue;
            }
            return line.codePointAt(0);
        }
    }

    // Reads a line. Prints an error and returns null on EOF or IOException.
    private static String readLineOrNull(ConsoleInput input, PrintStream err) {
        try {
            String line = input.readLine();
            if (line == null) {
                err.println("Giriş sona erdi.");
            }
            return line;
        } catch (IOException e) {
            err.println("Okuma hatası: " + e.getMessage());
            return null;
        }
    }
}
