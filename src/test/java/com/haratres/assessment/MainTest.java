package com.haratres.assessment;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test void helpSucceedsAndShowsBothCommands() {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int status = Main.run(new String[]{"--help"}, new PrintStream(output), new PrintStream(errors));
        assertEquals(0, status);
        assertTrue(output.toString().contains("count|shop"));
        assertEquals("", errors.toString());
    }

    @Test void invalidCommandFailsWithUsageGuidance() {
        var errors = new ByteArrayOutputStream();
        int status = Main.run(new String[]{"invalid"}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertEquals(2, status);
        assertTrue(errors.toString().contains("--help"));
    }

    @Test void countCommandRunsCharacterCounterWithSuppliedReader() {
        // "50\nMerhaba Dünya!\nHayır\na\n" → 3 occurrences of 'a' (case-insensitive)
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int status = Main.run(new String[]{"count"},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                new StringReader("50\nMerhaba Dünya!\nHayır\na\n"));
        assertEquals(0, status);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("'a' harfi toplamda 3 defa geçmektedir."));
    }

    @Test void shopCommandRunsWithSuppliedReader() {
        // 2 products, sort by name asc, decline cart
        String input = "2\nKalem\n1.50\n100\n4.5\nDefter\n3.00\n50\n4.7\nname\nartan\nHayır\n";
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int status = Main.run(new String[]{"shop"},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                new StringReader(input));
        assertEquals(0, status);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Sıralanmış Ürünler"));
    }
}
