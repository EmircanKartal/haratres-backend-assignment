package com.haratres.assessment.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;

/**
 * Reads console lines.
 *
 * <p>Every line is NFC-normalized before being returned so that characters
 * like 'Ç' arrive as a single precomposed code point (U+00C7) regardless of
 * whether the terminal or clipboard delivered the NFD decomposed form
 * (U+0043 + U+0327). This makes code-point counting and character comparison
 * consistent across platforms and input methods.
 *
 * <p>Does not prompt, validate, or close the reader.
 */
public final class ConsoleInput {
    private final BufferedReader reader;

    public ConsoleInput(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader must not be null");
        }
        this.reader = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);
    }

    /**
     * @return the next NFC-normalized line without its line terminator,
     *         or {@code null} at end of input
     */
    public String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        return Normalizer.normalize(line, Normalizer.Form.NFC);
    }
}
