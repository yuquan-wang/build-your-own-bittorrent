import org.junit.Test;

import java.util.*;

import static junit.framework.TestCase.assertEquals;

public class BEncodeTest {
    private BEncode bEncode = new BEncode();

    @Test
    public void testDecodeValidPositiveInteger() throws Exception {
        assertEquals(3, bEncode.decode("i3e"));
    }

    @Test
    public void testDecodeValidNegativeInteger() throws Exception {
        assertEquals(-3, bEncode.decode("i-3e"));
    }

    @Test
    public void testDecodeValidZero() throws Exception {
        assertEquals(0, bEncode.decode("i0e"));
    }

    @Test(expected = Exception.class)
    public void testDecodeInvalidZero() throws Exception {
        bEncode.decode("i-0e");
    }

    @Test(expected = Exception.class)
    public void testDecodeInvalidLeadingZero() throws Exception {
        bEncode.decode("i03e");
    }

    @Test
    public void testDecodeString() throws Exception {
        assertEquals("spam", bEncode.decode("4:spam"));
    }

    @Test(expected = Exception.class)
    public void testDecodeInvalidString() throws Exception {
        bEncode.decode("4:spamm");
    }

    @Test
    public void testDecodeList() throws Exception {
        List expected = new ArrayList();
        expected.add("spam");
        expected.add(42);
        assertEquals(expected, bEncode.decode("l4:spami42ee"));

        expected.clear();

        assertEquals(expected, bEncode.decode("le"));
    }

    @Test
    public void testDecodeDictionary() throws Exception {
        Map expected = new HashMap();
        expected.put("bar", "spam");
        expected.put("foo", 42);
        assertEquals(expected, bEncode.decode("d3:bar4:spam3:fooi42ee"));

        expected.clear();
        expected.put("spam", Arrays.asList("a", "b"));
        assertEquals(expected, bEncode.decode("d4:spaml1:a1:bee"));

        expected.clear();
        assertEquals(expected, bEncode.decode("de"));
    }

    @Test
    public void testEncodeInteger() throws Exception {
        assertEquals("i3e", bEncode.encode(3));
    }

    @Test
    public void testEncodeString() throws Exception {
        assertEquals("4:spam", bEncode.encode("spam"));
    }

    @Test
    public void testEncodeList() throws Exception {
        List l = new ArrayList();
        l.add("spam");
        l.add(3);
        assertEquals("l4:spami3ee", bEncode.encode(l));
    }

    @Test
    public void testEncodeMap() throws Exception {
        List l = new ArrayList();
        l.add("spam");
        l.add(3);

        Map m = new HashMap();
        m.put("spam", l);
        m.put("key1", 1);
        m.put("key2", "2");
        assertEquals("d4:key1i1e4:key21:24:spaml4:spami3eee", bEncode.encode(m));
    }

}
