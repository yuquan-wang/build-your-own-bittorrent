import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    Algorithm introduction: https://en.wikipedia.org/wiki/Bencode
 */
public class BEncode {
    private static final String INVALID = "Invalid input";

    private String data = "";
    private int index = 0;

    void setInput(String input) {
        data = input;
        index = 0;
    }

    public Object decode(String input) throws Exception {
        setInput(input);
        Object ret = internal_decode();
        if (index == data.length()) {
            return ret;
        } else {
            throw new Exception(INVALID);
        }
    }

    public String encode(Object input) throws Exception {
        if (input instanceof String) {
            return ((String) input).length() + ":" + input;
        } else if (input instanceof Integer) {
            return "i" + input + "e";
        } else if (input instanceof Iterable) {
            String output = "l";
            for (Object element : (Iterable) input) {
                output += encode(element);
            }
            output += "e";
            return output;
        } else if (input instanceof Map) {
            String output = "d";
            for (Object key : ((Map) input).keySet()) {
                Object value = ((Map) input).get(key);
                output += encode(key);
                output += encode(value);
            }
            output += "e";
            return output;
        } else {
            throw new Exception(INVALID);
        }
    }

    // decode data as an object recursively
    private Object internal_decode() throws Exception {
        char c = data.charAt(index);
        if (c == 'i') {
            //if it is type of integer
            index++; // skip ':'
            String s = "";
            while (data.charAt(index) != 'e') {
                s += data.charAt(index);
                index++;
            }
            index++;// skip 'e'
            int output = 0;
            try {
                output = Integer.valueOf(s);
            } catch (NumberFormatException e) {
                throw new Exception(INVALID);
            }
            if (isValidInteger(output, s)) {
                return output;
            } else {
                throw new Exception(INVALID);
            }
        } else if (c == 'l') {
            //if it is type of list
            List<Object> output = new ArrayList<>();
            index++; // skip 'l'
            while (data.charAt(index) != 'e') {
                output.add(internal_decode());
            }
            index++; // skip 'e'
            return output;
        } else if (c == 'd') {
            //if it is type of dictionary
            Map<Object, Object> output = new HashMap<>();
            index++; // skip 'd'
            while (data.charAt(index) != 'e') {
                Object key = internal_decode();
                Object value = internal_decode();
                output.put(key, value);
            }
            index++; // skip 'e'
            return output;
        } else {
            //it should be type of string
            //for performance we could use StringBuilder later
            String lenS = "";
            while (data.charAt(index) != ':') {
                lenS += data.charAt(index);
                index++;
            }
            int len = 0;
            try {
                len = Integer.valueOf(lenS); // when length is too large, better to user long
            } catch (NumberFormatException e) {
                throw new Exception(INVALID);
            }
            String output = "";
            index++; // ignore char ':'
            for (int i = 0; i < len; i++) {
                output += data.charAt(index++);
            }
            return output;
        }
    }

    private boolean isValidInteger(int n, String s) {
        if (n == 0) {
            if (s.equals("0")) { // only i0e is valid
                return true;
            } else {
                return false;
            }
        } else {
            if (String.valueOf(n).length() != s.length()) { //contains leading zero like i03e
                return false;
            } else {
                return true;
            }
        }
    }
}
