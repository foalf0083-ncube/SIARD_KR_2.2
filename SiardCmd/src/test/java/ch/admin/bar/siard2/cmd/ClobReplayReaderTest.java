// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClobReplayReaderTest {
    private static Set<Path> spillFiles() throws IOException {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("siard_clob_prefix_"))
                    .collect(Collectors.toSet());
        }
    }

    @Test void replaysMemoryAndSpilledPrefixesWithoutLosingAnyUtf16CodeUnit() throws Exception {
        char[] chars = new char[1_100_000];
        for (int i=0; i<chars.length; i++) chars[i] = (char)i;
        String expected = new String(chars);
        Set<Path> before = spillFiles();
        for (int prefix : new int[]{0, 1, 1024, 65536, 65537, 1_000_000, chars.length}) {
            int[] closes = {0};
            StringReader source = new StringReader(expected) {
                @Override public void close() { closes[0]++; super.close(); }
            };
            try (ClobReplayReader reader = new ClobReplayReader(source, true)) {
                char[] scratch = new char[7777];
                int consumed = 0;
                while (consumed < prefix) consumed += reader.read(scratch, 3, Math.min(prefix-consumed, 7774));
                reader.replayPrefix();
                assertEquals(0, reader.read(scratch, 0, 0));
                StringWriter result = new StringWriter();
                reader.transferTo(result);
                assertEquals(expected, result.toString(), "prefix=" + prefix);
                assertEquals(-1, reader.read(scratch));
            }
            assertEquals(1, closes[0]);
            assertEquals(before, spillFiles());
        }
    }

    @Test void cleansSpillOnEarlyCloseAndOnSourceCloseFailure() throws Exception {
        for (boolean fail : new boolean[]{false, true}) {
            Set<Path> before = spillFiles();
            Reader source = new Reader() {
                @Override public int read(char[] chars, int offset, int length) {
                    Arrays.fill(chars, offset, offset + length, 'a'); return length;
                }
                @Override public void close() throws IOException { if (fail) throw new IOException("source close failed"); }
            };
            ClobReplayReader reader = new ClobReplayReader(source, true);
            reader.read(new char[100_000]);
            Set<Path> during = spillFiles();
            during.removeAll(before);
            assertEquals(1, during.size());
            if (fail) assertEquals("source close failed", assertThrows(IOException.class, reader::close).getMessage());
            else reader.close();
            assertEquals(before, spillFiles());
            reader.close();
            assertThrows(IOException.class, reader::read);
        }
    }

    @Test void passesThroughWhenNoCaptureIsNeeded() throws Exception {
        try (ClobReplayReader reader = new ClobReplayReader(new StringReader("한글 😀"), false)) {
            StringWriter result = new StringWriter();
            reader.transferTo(result);
            assertEquals("한글 😀", result.toString());
        }
    }
}
