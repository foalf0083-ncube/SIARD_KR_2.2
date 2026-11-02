// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.cmd;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.tika.Tika;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MimeDetectionCacheTest {
    @AfterEach void reset() { System.clearProperty(MimeDetectionCache.PROPERTY_BYTES); }

    @Test void completeInputsKeepRealTikaResultsIncludingBoundariesAndBinaryHeaders() throws Exception {
        Tika tika = new Tika(); MimeDetectionCache cache = new MimeDetectionCache(tika);
        List<byte[]> inputs = new ArrayList<>();
        for (String s : new String[]{"", "한글 법률 😀", "<?xml version=\"1.0\"?><law/>",
                "<html><body>고시</body></html>", "%PDF-1.7\n", "PK\003\004", "{\"a\":1}"})
            inputs.add(s.getBytes(StandardCharsets.UTF_8));
        for (int length : new int[]{8191,8192,8193,65535,65536,65537}) {
            byte[] bytes = new byte[length]; new Random(length).nextBytes(bytes); inputs.add(bytes);
        }
        for (byte[] value : inputs) {
            String expected = tika.detect(new ByteArrayInputStream(value));
            assertEquals(expected, cache.detect(new ByteArrayInputStream(value)));
            assertEquals(expected, cache.detect(new ByteArrayInputStream(value)));
            assertEquals(tika.detect(value), cache.detect(value));
        }
        assertTrue(cache.hits() > 0);
    }

    @Test void collisionsMutationAndSamePrefixesNeverReuseDifferentContent() throws Exception {
        Tika tika = new Tika(); MimeDetectionCache cache = new MimeDetectionCache(tika);
        byte[] first = {0,31}, collision = {1,0};
        assertEquals(Arrays.hashCode(first), Arrays.hashCode(collision));
        cache.detect(first); cache.detect(collision); assertEquals(0, cache.hits());
        Arrays.fill(first, (byte)'a'); cache.detect(first); assertEquals(0, cache.hits());
        cache.detect(new byte[]{0,31}); assertEquals(1, cache.hits());
        byte[] longValue = new byte[8193]; Arrays.fill(longValue,(byte)'x');
        cache.detect(Arrays.copyOf(longValue,8192)); long before=cache.hits();
        assertEquals(tika.detect(longValue),cache.detect(new ByteArrayInputStream(longValue)));
        assertEquals(before,cache.hits());
    }

    @Test void cacheEvictsAndDoesNotPeekUnknownOrStatefulStreams() throws Exception {
        System.setProperty(MimeDetectionCache.PROPERTY_BYTES,"512");
        MimeDetectionCache cache = new MimeDetectionCache(new Tika());
        for (int i=0;i<30;i++) cache.detect(("sample-"+i).getBytes(StandardCharsets.US_ASCII));
        assertTrue(cache.retainedBytes()<=512); assertTrue(cache.size()<30);
        InputStream untrusted = new ByteArrayInputStream("abc".getBytes()) {
            @Override public int available() { throw new AssertionError("must not use available on subclasses"); }
        };
        assertEquals(-1,MimeDetectionCache.memoryLength(untrusted));
        int[] calls={0};
        Tika stateful = new Tika() {
            @Override public String detect(InputStream input) { return "type/"+(++calls[0]); }
        };
        MimeDetectionCache custom = new MimeDetectionCache(stateful);
        assertEquals("type/1",custom.detect(new ByteArrayInputStream(new byte[0])));
        assertEquals("type/2",custom.detect(new ByteArrayInputStream(new byte[0])));
        assertEquals(0,custom.hits());
    }
}
