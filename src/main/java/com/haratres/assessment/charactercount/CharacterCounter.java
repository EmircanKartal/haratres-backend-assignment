package com.haratres.assessment.charactercount;

import java.util.Locale;

/**
 * Counts occurrences of a single Unicode code point in a text.
 *
 * <p>Case-insensitive mode uses Turkish rules: dotless {@code I}/{@code ı}
 * share a bucket and dotted {@code İ}/{@code i} share a separate bucket.
 * The default JVM locale is not consulted.
 *
 * <p>Sentence length and character identity are measured in Unicode code
 * points, not UTF-16 code units. Combining sequences and multi-code-point
 * emoji are not treated as single characters.
 */
public final class CharacterCounter {
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    /**
     * Returns the number of times {@code searchCodePoint} appears in {@code text}.
     *
     * @param text           the text to search; must not be null or empty
     * @param searchCodePoint a Unicode scalar value to search for
     * @param caseSensitive  when {@code false} the comparison uses Turkish folding
     * @throws IllegalArgumentException if text is null or empty, or searchCodePoint
     *                                  is not a Unicode scalar value
     */
    public long countOccurrences(String text, int searchCodePoint, boolean caseSensitive) {
        if (text == null) {
            throw new IllegalArgumentException("Metin boş olamaz.");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Metin boş olamaz.");
        }
        if (!isUnicodeScalar(searchCodePoint)) {
            throw new IllegalArgumentException("Geçerli bir Unicode karakter değil.");
        }

        int target = caseSensitive ? searchCodePoint : fold(searchCodePoint);
        return text.codePoints()
                .map(cp -> caseSensitive ? cp : fold(cp))
                .filter(cp -> cp == target)
                .count();
    }

    /**
     * Returns the number of Unicode code points in {@code text}.
     * Used to measure sentence length against the user-supplied maximum.
     */
    public static int codePointLength(String text) {
        if (text == null) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }

    /**
     * Turkish lowercase of a single code point.
     * {@code Character.toLowerCase(int)} ignores locale and maps {@code I} to {@code i}.
     * If the lowercase form expands to more than one code point the original is returned,
     * so one input code point always maps to exactly one output code point.
     *
     * <p>Package-accessible here; {@code ProductCatalog} re-exports this via its own
     * {@code foldName} method to avoid a cross-package static reference.
     */
    public static int fold(int codePoint) {
        String lowered = new String(Character.toChars(codePoint)).toLowerCase(TURKISH);
        if (lowered.codePointCount(0, lowered.length()) == 1) {
            return lowered.codePointAt(0);
        }
        return codePoint;
    }

    private static boolean isUnicodeScalar(int codePoint) {
        return Character.isValidCodePoint(codePoint)
                && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE);
    }
}
