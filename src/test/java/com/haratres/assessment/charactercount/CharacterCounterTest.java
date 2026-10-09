package com.haratres.assessment.charactercount;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterCounterTest {
    private final CharacterCounter counter = new CharacterCounter();

    // --- basic counting ---

    @Test void caseSensitiveCountsExactMatch() {
        assertEquals(3, counter.countOccurrences("Merhaba Dünya!", 'a', true));
    }

    @Test void caseSensitiveDoesNotCountUppercase() {
        assertEquals(0, counter.countOccurrences("AAA", 'a', true));
        assertEquals(3, counter.countOccurrences("AAA", 'A', true));
    }

    @Test void caseInsensitiveCountsBothCases() {
        assertEquals(4, counter.countOccurrences("AaAa", 'a', false));
        assertEquals(4, counter.countOccurrences("AaAa", 'A', false));
    }

    @Test void zeroMatchesReturnedCorrectly() {
        assertEquals(0, counter.countOccurrences("Merhaba", 'z', true));
        assertEquals(0, counter.countOccurrences("Merhaba", 'z', false));
    }

    @Test void singleCharacterText() {
        assertEquals(1, counter.countOccurrences("a", 'a', true));
        assertEquals(1, counter.countOccurrences("A", 'a', false));
    }

    // --- Turkish i pairs ---

    @Test void caseInsensitiveDotlessIPair() {
        // 'I' folds to 'ı'; searching for 'I' case-insensitively should find both
        assertEquals(2, counter.countOccurrences("Iı", 'I', false));
        assertEquals(2, counter.countOccurrences("Iı", 0x0131 /*ı*/, false));
    }

    @Test void caseInsensitiveDottedIPair() {
        // 'İ' folds to 'i'; searching for 'İ' case-insensitively should find both
        assertEquals(2, counter.countOccurrences("İi", 'İ', false));
        assertEquals(2, counter.countOccurrences("İi", 'i', false));
    }

    @Test void caseSensitiveDotlessAndDottedIAreDistinct() {
        assertEquals(1, counter.countOccurrences("Iı", 'I', true));
        assertEquals(1, counter.countOccurrences("Iı", 0x0131 /*ı*/, true));
        assertEquals(0, counter.countOccurrences("Iı", 'i', true));
    }

    @Test void caseInsensitiveTurkishFoldingIgnoresDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long inUs = counter.countOccurrences("Iı", 'I', false);
            Locale.setDefault(Locale.forLanguageTag("tr"));
            long inTr = counter.countOccurrences("Iı", 'I', false);
            assertEquals(inUs, inTr);
            assertEquals(2, inTr);
        } finally {
            Locale.setDefault(previous);
        }
    }

    // --- supplementary code points ---

    @Test void supplementaryCodePointCountsAsOne() {
        String emoji = "\uD83D\uDE00"; // 😀 — one code point, two chars
        assertEquals(2, emoji.length());
        assertEquals(2, counter.countOccurrences(emoji + emoji, emoji.codePointAt(0), true));
    }

    @Test void supplementaryCodePointCaseInsensitiveNoExpansion() {
        String emoji = "\uD83D\uDE00";
        assertEquals(1, counter.countOccurrences("a" + emoji, emoji.codePointAt(0), false));
    }

    // --- spaces and punctuation ---

    @Test void spacesAreCounted() {
        // "a b c" has two spaces: between a-b and b-c
        assertEquals(2, counter.countOccurrences("a b c", ' ', true));
        // leading and trailing spaces also count
        assertEquals(4, counter.countOccurrences(" a b c ", ' ', true));
    }

    // --- validation ---

    @Test void rejectsNullText() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> counter.countOccurrences(null, 'a', true));
        assertTrue(ex.getMessage().contains("boş olamaz"));
    }

    @Test void rejectsEmptyText() {
        assertThrows(IllegalArgumentException.class, () -> counter.countOccurrences("", 'a', true));
    }

    @Test void rejectsUnpairedSurrogateAsSearchChar() {
        assertThrows(IllegalArgumentException.class,
                () -> counter.countOccurrences("abc", Character.MIN_SURROGATE, true));
    }

    // --- codePointLength helper ---

    @Test void codePointLengthCountsScalarsNotCharUnits() {
        String emoji = "\uD83D\uDE00";
        assertEquals(1, CharacterCounter.codePointLength(emoji));
        assertEquals(2, CharacterCounter.codePointLength("ab"));
    }

    @Test void codePointLengthOfNullIsZero() {
        assertEquals(0, CharacterCounter.codePointLength(null));
    }

    // --- fold helper ---

    @Test void foldMapsCapitalIToDotlessI() {
        assertEquals(0x0131, CharacterCounter.fold('I'));
    }

    @Test void foldMapsDottedCapitalIToDottedI() {
        assertEquals('i', CharacterCounter.fold(0x0130 /*İ*/));
    }

    @Test void foldDoesNotExpandMultiCodePointResult() {
        // ß (U+00DF) stays ß — its uppercase 'ẞ' maps back to ß, no expansion
        assertEquals(0x00DF, CharacterCounter.fold(0x1E9E /*ẞ*/));
    }
}
