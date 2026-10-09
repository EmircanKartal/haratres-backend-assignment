package com.haratres.assessment.charactercount;

import com.haratres.assessment.console.ConsoleInput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterCounterCliTest {
    private final CharacterCounterCli cli = new CharacterCounterCli(new CharacterCounter());

    // --- happy paths ---

    @Test void assignmentExample_caseInsensitive_a_in_MerhaDunya() {
        // Assignment example: max=50, "Merhaba Dünya!", Hayır (insensitive), 'a' → 3, then EOF
        Capture c = run("50\nMerhaba Dünya!\nHayır\na\n");
        assertEquals(0, c.status, c.errors);
        assertEquals("", c.errors);
        assertTrue(c.output.contains("'a' harfi toplamda 3 defa geçmektedir."), c.output);
    }

    @Test void multipleCharacterQueriesOnSameSentence() {
        // After setup, search 'a' then 'M' then EOF — both results printed
        Capture c = run("50\nMerhaba\nEvet\na\nM\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("'a' harfi toplamda 2 defa geçmektedir."), c.output);
        assertTrue(c.output.contains("'M' harfi toplamda 1 defa geçmektedir."), c.output);
    }

    @Test void caseSensitiveFindsExactCaseOnly() {
        Capture c = run("50\nMerhaba\nEvet\nM\n");
        assertEquals(0, c.status);
        assertTrue(c.output.contains("'M' harfi toplamda 1 defa geçmektedir."), c.output);
    }

    @Test void caseSensitiveReturnsZeroForWrongCase() {
        Capture c = run("50\nMerhaba\nEvet\nm\n");
        assertEquals(0, c.status);
        assertTrue(c.output.contains("'m' harfi toplamda 0 defa geçmektedir."), c.output);
    }

    // --- Turkish character counting ---

    @Test void caseInsensitiveTurkishÇ_findsUpperAndLower() {
        // "Çiçek Çok" has Ç at pos 0, ç at pos 2, Ç at pos 5 → 3 insensitive
        Capture c = run("20\nÇiçek Çok\nHayır\nÇ\n");
        assertEquals(0, c.status);
        assertTrue(c.output.contains("'Ç' harfi toplamda 3 defa geçmektedir."), c.output);
    }

    @Test void caseSensitiveTurkishÇ_findsUpperOnly() {
        Capture c = run("20\nÇiçek Çok\nEvet\nÇ\n");
        assertEquals(0, c.status);
        assertTrue(c.output.contains("'Ç' harfi toplamda 2 defa geçmektedir."), c.output);
    }

    @Test void caseInsensitiveDotlessI_IAndSmallDotlessIAreSameBucket() {
        // "Iıb": I (folds→ı) + ı + b → insensitive search 'I' finds both → 2 matches
        Capture c = run("10\nIıb\nHayır\nI\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("'I' harfi toplamda 2 defa geçmektedir."), c.output);
    }

    @Test void caseInsensitiveDottedI_İAndSmallIAreSameBucket() {
        // "İi" insensitive search 'İ' → 2
        Capture c = run("10\nİstanbul\nHayır\nİ\n");
        assertEquals(0, c.status);
        // İstanbul: İ(fold→i),s,t,a,n,b,u,l — case-insensitive 'İ' matches both İ and i → 1 i
        assertTrue(c.output.contains("harfi toplamda 1 defa geçmektedir."), c.output);
    }

    // --- NFD/NFC normalization (the Ç bug) ---

    @Test void nfdDecomposedSearchCharIsAccepted() {
        // NFD 'Ç' = C + combining-cedilla (2 code units); ConsoleInput must NFC-normalize it to 1
        String nfdÇ = Normalizer.normalize("Ç", Normalizer.Form.NFD);
        assertEquals(2, nfdÇ.codePointCount(0, nfdÇ.length()), "pre-condition: NFD has 2 code points");

        // Sentence is NFC "Çiçek"; search char is NFD "Ç" — both must normalize to NFC for matching
        Capture c = run("10\nÇiçek\nEvet\n" + nfdÇ + "\n");
        assertEquals(0, c.status, c.errors);
        // must NOT reject the search char as invalid; must count correctly
        assertFalse(c.errors.contains("Geçerli bir karakter"), c.errors);
        assertTrue(c.output.contains("harfi toplamda 1 defa geçmektedir."), c.output);
    }

    @Test void nfdDecomposedSentenceCharsAreFoundByNfcSearchChar() {
        // Sentence delivered as NFD "Ç"; search as NFC 'Ç' — count must be 1, not 0
        String nfdSentence = Normalizer.normalize("Çay", Normalizer.Form.NFD);
        Capture c = run("10\n" + nfdSentence + "\nEvet\nÇ\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("harfi toplamda 1 defa geçmektedir."), c.output);
    }

    // --- sentence length validation ---

    @Test void sentenceTooLongIsRejectedAndReprompted() {
        // max=5, first sentence is 10 chars → rejected, then "Kısa" (4) → accepted
        Capture c = run("5\nOnHarfliDir\nKısa\nHayır\na\n");
        assertEquals(0, c.status);
        assertTrue(c.errors.contains("limitini aşıyor"), c.errors);
        assertTrue(c.output.contains("harfi toplamda"), c.output);
    }

    @Test void sentenceLengthMeasuredInCodePointsNotChars() {
        // "😀" is 2 UTF-16 chars but 1 code point; max=1 must accept it
        Capture c = run("1\n\uD83D\uDE00\nEvet\n\uD83D\uDE00\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("harfi toplamda 1 defa geçmektedir."), c.output);
    }

    // --- case-sensitivity prompt validation ---

    @Test void invalidCaseSensitivityIsRejectedAndReprompted() {
        Capture c = run("50\nMerhaba\nbelki\nEvet\na\n");
        assertEquals(0, c.status);
        assertTrue(c.errors.contains("Lütfen geçerli bir cevap giriniz."), c.errors);
    }

    @Test void hayirAcceptedWithAsciiI() {
        // "Hayir" (ASCII i instead of dotless ı) should still be accepted
        Capture c = run("10\ntest\nHayir\na\n");
        assertEquals(0, c.status, c.errors);
    }

    @Test void hayirAcceptedAllCaps() {
        Capture c = run("10\ntest\nHAYIR\na\n");
        assertEquals(0, c.status, c.errors);
    }

    // --- search character validation ---

    @Test void emptySearchCharRetried() {
        Capture c = run("50\nMerhaba\nEvet\n\na\n");
        assertEquals(0, c.status);
        assertTrue(c.errors.contains("Geçerli bir karakter giriniz."), c.errors);
    }

    @Test void multiCodePointSearchCharRetried() {
        Capture c = run("50\nMerhaba\nEvet\nab\na\n");
        assertEquals(0, c.status);
        assertTrue(c.errors.contains("Geçerli bir karakter giriniz."), c.errors);
        assertTrue(c.output.contains("'a'"), c.output);
    }

    @Test void spaceIsAValidSearchChar() {
        // "a b" has 1 space
        Capture c = run("10\na b\nEvet\n \n");
        assertEquals(0, c.status);
        assertTrue(c.output.contains("' ' harfi toplamda 1 defa geçmektedir."), c.output);
    }

    @Test void supplementaryCodePointSearchChar() {
        String emoji = "\uD83D\uDE00"; // 😀 — 1 code point
        Capture c = run("10\n" + emoji + emoji + "\nEvet\n" + emoji + "\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("harfi toplamda 2 defa geçmektedir."), c.output);
    }

    // --- EOF handling at every step ---

    @Test void eofAtMaxLengthStepExitsWithStatusOne() {
        Capture c = run("");
        assertEquals(1, c.status);
        assertFalse(c.errors.isEmpty());
    }

    @Test void eofAtSentenceStepExitsWithStatusOne() {
        Capture c = run("10\n");
        assertEquals(1, c.status);
    }

    @Test void eofAtCaseSensitivityStepExitsWithStatusOne() {
        Capture c = run("10\nMerhaba\n");
        assertEquals(1, c.status);
    }

    @Test void eofAtSearchCharStepBeforeAnyResultExitsWithStatusOne() {
        // EOF immediately at the character prompt (no character ever searched) → status 1
        Capture c = run("10\nMerhaba\nEvet\n");
        assertEquals(1, c.status);
    }

    @Test void eofAfterAtLeastOneResultExitsWithStatusZero() {
        // One character searched successfully, then EOF → status 0 (clean exit)
        Capture c = run("10\nMerhaba\nEvet\na\n");
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("harfi toplamda"), c.output);
    }

    // --- I/O failure ---

    @Test void ioFailureExitsWithStatusOne() {
        Reader broken = new Reader() {
            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("simulated");
            }
            @Override public void close() { }
        };
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = cli.run(new ConsoleInput(broken), utf8(out), utf8(err));
        assertEquals(1, status);
        assertFalse(err.toString(StandardCharsets.UTF_8).isEmpty());
    }

    // --- helpers ---

    private Capture run(String input) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = cli.run(new ConsoleInput(new StringReader(input)), utf8(out), utf8(err));
        return new Capture(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static PrintStream utf8(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    private record Capture(int status, String output, String errors) { }
}
