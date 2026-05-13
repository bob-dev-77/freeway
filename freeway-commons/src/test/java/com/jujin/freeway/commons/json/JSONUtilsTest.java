package com.jujin.freeway.commons.json;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JSONUtilsTest {

    // ========================================================================
    // toJson — compact serialization
    // ========================================================================

    @Nested
    class ToJson {

        @Test
        void nullValue() {
            assertEquals("null", JSONUtils.toJson(null));
        }

        @Test
        void string() {
            assertEquals("\"hello\"", JSONUtils.toJson("hello"));
        }

        @Test
        void stringWithSpecialChars() {
            assertEquals("\"tab\\there\"", JSONUtils.toJson("tab\there"));
            assertEquals("\"line\\nbreak\"", JSONUtils.toJson("line\nbreak"));
            assertEquals("\"quo\\\"te\"", JSONUtils.toJson("quo\"te"));
            assertEquals("\"back\\\\slash\"", JSONUtils.toJson("back\\slash"));
        }

        @Test
        void stringWithUnicodeEscape() {
            // Characters in range 0x0080-0x009f
            assertEquals("\"\\u0080\"", JSONUtils.toJson("\u0080"));
            assertEquals("\"\\u009f\"", JSONUtils.toJson("\u009f"));
            // Characters in range 0x2000-0x20ff
            assertEquals("\"\\u2000\"", JSONUtils.toJson("\u2000"));
            assertEquals("\"\\u20ff\"", JSONUtils.toJson("\u20ff"));
        }

        @Test
        void htmlSlashEscaping() {
            // '/' after '<' should be escaped as "\\/"
            assertEquals("\"<\\/\"", JSONUtils.toJson("</"));
        }

        @Test
        void integer() {
            assertEquals("42", JSONUtils.toJson(42));
        }

        @Test
        void longValue() {
            assertEquals("9223372036854775807", JSONUtils.toJson(9223372036854775807L));
        }

        @Test
        void doubleValue() {
            assertEquals("3.14", JSONUtils.toJson(3.14));
        }

        @Test
        void negativeZero() {
            // toJson uses buf.append(value) for Number, Double.toString(-0d) = "-0.0"
            assertEquals("-0.0", JSONUtils.toJson(-0d));
        }

        @Test
        void booleanValue() {
            assertEquals("true", JSONUtils.toJson(true));
            assertEquals("false", JSONUtils.toJson(false));
        }

        @Test
        void jsonObject() {
            JSONObject obj = new JSONObject();
            obj.put("name", "test");
            obj.put("count", 1);
            // JSONObject's toCompactString sorts by insertion order
            String json = JSONUtils.toJson(obj);
            assertTrue(json.contains("\"name\":\"test\""));
            assertTrue(json.contains("\"count\":1"));
            assertTrue(json.startsWith("{") && json.endsWith("}"));
        }

        @Test
        void jsonArray() {
            JSONArray arr = new JSONArray();
            arr.add(1);
            arr.add("two");
            arr.add(true);
            assertEquals("[1,\"two\",true]", JSONUtils.toJson(arr));
        }

        @Test
        void map() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("x", 10);
            map.put("y", "hello");
            assertEquals("{\"x\":10,\"y\":\"hello\"}", JSONUtils.toJson(map));
        }

        @Test
        void nestedMap() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("k", "v");
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("inner", inner);
            assertEquals("{\"inner\":{\"k\":\"v\"}}", JSONUtils.toJson(outer));
        }

        @Test
        void list() {
            List<Object> list = List.of(1, "two", false);
            assertEquals("[1,\"two\",false]", JSONUtils.toJson(list));
        }

        @Test
        void nestedList() {
            List<Object> inner = List.of("a", "b");
            List<Object> outer = List.of(inner);
            assertEquals("[[\"a\",\"b\"]]", JSONUtils.toJson(outer));
        }

        @Test
        void primitiveArray() {
            int[] ints = { 1, 2, 3 };
            assertEquals("[1,2,3]", JSONUtils.toJson(ints));
            String[] strs = { "a", "b" };
            assertEquals("[\"a\",\"b\"]", JSONUtils.toJson(strs));
            boolean[] bools = { true, false };
            assertEquals("[true,false]", JSONUtils.toJson(bools));
        }

        @Test
        void bean() {
            Bean bean = new Bean("Alice", 30);
            String json = JSONUtils.toJson(bean);
            assertTrue(json.contains("\"name\":\"Alice\""));
            assertTrue(json.contains("\"age\":30"));
        }

        @Test
        void beanWithIsPrefix() {
            ActiveBean bean = new ActiveBean(true, "active");
            String json = JSONUtils.toJson(bean);
            assertTrue(json.contains("\"enabled\":true"));
            assertTrue(json.contains("\"status\":\"active\""));
        }

        @Test
        void recordTest() {
            Point point = new Point(3, 4);
            assertEquals("{\"x\":3,\"y\":4}", JSONUtils.toJson(point));
        }

        @Test
        void enumTest() {
            assertEquals("\"RED\"", JSONUtils.toJson(Color.RED));
            assertEquals("\"BLUE\"", JSONUtils.toJson(Color.BLUE));
        }

        @Test
        void nullWithinCollection() {
            List<Object> list = new ArrayList<>();
            list.add(null);
            list.add("x");
            assertEquals("[null,\"x\"]", JSONUtils.toJson(list));
        }

        @Test
        void emptyCollection() {
            assertEquals("[]", JSONUtils.toJson(List.of()));
            assertEquals("{}", JSONUtils.toJson(Map.of()));
        }
    }

    // ========================================================================
    // toJsonPretty — pretty-print serialization
    // ========================================================================

    @Nested
    class ToJsonPretty {

        @Test
        void nullValue() {
            assertEquals("null", JSONUtils.toJsonPretty(null));
        }

        @Test
        void primitive() {
            assertEquals("42", JSONUtils.toJsonPretty(42));
            assertEquals("\"str\"", JSONUtils.toJsonPretty("str"));
            assertEquals("true", JSONUtils.toJsonPretty(true));
        }

        @Test
        void prettyObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", 1);
            map.put("b", 2);
            String pretty = JSONUtils.toJsonPretty(map);
            // Should have newlines and indentation
            assertTrue(pretty.contains("\n"));
            assertTrue(pretty.contains("  "));
            assertEquals("{\n  \"a\": 1,\n  \"b\": 2\n}", pretty);
        }

        @Test
        void prettyArray() {
            List<Object> list = List.of(1, 2, 3);
            String pretty = JSONUtils.toJsonPretty(list);
            assertEquals("[\n  1,\n  2,\n  3\n]", pretty);
        }

        @Test
        void prettyNested() {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("k", "v");
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("inner", inner);
            String pretty = JSONUtils.toJsonPretty(outer);
            assertEquals("{\n  \"inner\": {\n    \"k\": \"v\"\n  }\n}", pretty);
        }

        @Test
        void prettyEmpty() {
            assertEquals("{}", JSONUtils.toJsonPretty(Map.of()));
            assertEquals("[]", JSONUtils.toJsonPretty(List.of()));
        }
    }

    // ========================================================================
    // fromJson — parse to raw JSON types
    // ========================================================================

    @Nested
    class FromJson {

        @Test
        void nullLiteral() {
            assertNull(JSONUtils.fromJson("null"));
        }

        @Test
        void booleanLiteral() {
            assertEquals(Boolean.TRUE, JSONUtils.fromJson("true"));
            assertEquals(Boolean.FALSE, JSONUtils.fromJson("false"));
        }

        @Test
        void integer() {
            assertEquals(123, JSONUtils.fromJson("123"));
        }

        @Test
        void negativeInteger() {
            assertEquals(-42, JSONUtils.fromJson("-42"));
        }

        @Test
        void longValue() {
            assertEquals(9223372036854775807L, JSONUtils.fromJson("9223372036854775807"));
        }

        @Test
        void doubleValue() {
            assertEquals(3.14, JSONUtils.fromJson("3.14"));
        }

        @Test
        void scientificNotation() {
            assertEquals(1.0e10, JSONUtils.fromJson("1.0e10"));
        }

        @Test
        void string() {
            assertEquals("hello", JSONUtils.fromJson("\"hello\""));
        }

        @Test
        void stringWithEscapedChars() {
            assertEquals("line\nbreak", JSONUtils.fromJson("\"line\\nbreak\""));
            assertEquals("tab\there", JSONUtils.fromJson("\"tab\\there\""));
            assertEquals("quo\"te", JSONUtils.fromJson("\"quo\\\"te\""));
            assertEquals("back\\slash", JSONUtils.fromJson("\"back\\\\slash\""));
        }

        @Test
        void unicodeEscape() {
            assertEquals("\u00e9", JSONUtils.fromJson("\"\\u00e9\""));
        }

        @Test
        void jsonObject() {
            Object result = JSONUtils.fromJson("{\"a\":1,\"b\":\"x\"}");
            assertInstanceOf(JSONObject.class, result);
            JSONObject obj = (JSONObject) result;
            assertEquals(1, obj.get("a"));
            assertEquals("x", obj.get("b"));
        }

        @Test
        void jsonArray() {
            Object result = JSONUtils.fromJson("[1,\"two\",true]");
            assertInstanceOf(JSONArray.class, result);
            JSONArray arr = (JSONArray) result;
            assertEquals(1, arr.get(0));
            assertEquals("two", arr.get(1));
            assertEquals(true, arr.get(2));
        }

        @Test
        void nestedObject() {
            Object result = JSONUtils.fromJson("{\"outer\":{\"inner\":42}}");
            assertInstanceOf(JSONObject.class, result);
            JSONObject outer = (JSONObject) result;
            JSONObject inner = (JSONObject) outer.get("outer");
            assertEquals(42, inner.get("inner"));
        }

        @Test
        void nestedArray() {
            Object result = JSONUtils.fromJson("[[1,2],[3,4]]");
            assertInstanceOf(JSONArray.class, result);
            JSONArray outer = (JSONArray) result;
            JSONArray inner = (JSONArray) outer.get(0);
            assertEquals(1, inner.get(0));
            assertEquals(2, inner.get(1));
        }

        @Test
        void mixedNested() {
            Object result = JSONUtils.fromJson("{\"items\":[1,2,{\"k\":\"v\"}]}");
            assertInstanceOf(JSONObject.class, result);
            JSONObject obj = (JSONObject) result;
            JSONArray arr = (JSONArray) obj.get("items");
            assertEquals(1, arr.get(0));
            JSONObject inner = (JSONObject) arr.get(2);
            assertEquals("v", inner.get("k"));
        }

        @Test
        void emptyObject() {
            JSONObject obj = (JSONObject) JSONUtils.fromJson("{}");
            assertTrue(obj.isEmpty());
        }

        @Test
        void emptyArray() {
            JSONArray arr = (JSONArray) JSONUtils.fromJson("[]");
            assertTrue(arr.isEmpty());
        }
    }

    // ========================================================================
    // fromJson(String, Class<T>) — parse and convert to target type
    // ========================================================================

    @Nested
    class FromJsonTyped {

        @Test
        void nullLiteral() {
            assertNull(JSONUtils.fromJson("null", String.class));
        }

        @Test
        void toInteger() {
            assertEquals(Integer.valueOf(42), JSONUtils.fromJson("42", Integer.class));
        }

        @Test
        void toLong() {
            assertEquals(Long.valueOf(123L), JSONUtils.fromJson("123", Long.class));
        }

        @Test
        void toDouble() {
            assertEquals(Double.valueOf(3.14), JSONUtils.fromJson("3.14", Double.class));
        }

        @Test
        void toBoolean() {
            assertEquals(Boolean.TRUE, JSONUtils.fromJson("true", Boolean.class));
        }

        @Test
        void toString_() {
            assertEquals("hello", JSONUtils.fromJson("\"hello\"", String.class));
        }

        @Test
        void toIntPrimitive() {
            assertEquals(42, (int) JSONUtils.fromJson("42", int.class));
        }

        @Test
        void toBean() {
            String json = "{\"name\":\"Bob\",\"age\":25}";
            Bean bean = JSONUtils.fromJson(json, Bean.class);
            assertNotNull(bean);
            assertEquals("Bob", bean.getName());
            assertEquals(25, bean.getAge());
        }

        @Test
        void toRecord() {
            String json = "{\"x\":10,\"y\":20}";
            Point point = JSONUtils.fromJson(json, Point.class);
            assertNotNull(point);
            assertEquals(10, point.x());
            assertEquals(20, point.y());
        }

        @Test
        void toMap() {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSONUtils.fromJson("{\"a\":1,\"b\":\"x\"}", Map.class);
            assertNotNull(map);
            assertEquals(1, map.get("a"));
            assertEquals("x", map.get("b"));
        }

        @Test
        void toList() {
            @SuppressWarnings("unchecked")
            List<Object> list = JSONUtils.fromJson("[1,2,3]", List.class);
            assertNotNull(list);
            assertEquals(List.of(1, 2, 3), list);
        }

        @Test
        void toArray() {
            int[] arr = JSONUtils.fromJson("[1,2,3]", int[].class);
            assertNotNull(arr);
            assertArrayEquals(new int[]{ 1, 2, 3 }, arr);
        }

        @Test
        void toStringArray() {
            String[] arr = JSONUtils.fromJson("[\"a\",\"b\",\"c\"]", String[].class);
            assertNotNull(arr);
            assertArrayEquals(new String[]{ "a", "b", "c" }, arr);
        }

        @Test
        void beanWithNestedBean() {
            String json = "{\"name\":\"Parent\",\"child\":{\"name\":\"Child\",\"age\":5}}";
            Parent parent = JSONUtils.fromJson(json, Parent.class);
            assertNotNull(parent);
            assertEquals("Parent", parent.getName());
            assertNotNull(parent.getChild());
            assertEquals("Child", parent.getChild().getName());
            assertEquals(5, parent.getChild().getAge());
        }

        @Test
        void toBeanWithExtraFields() {
            // Extra fields in JSON should be ignored by the bean
            String json = "{\"name\":\"Test\",\"age\":10,\"extra\":\"ignored\"}";
            Bean bean = JSONUtils.fromJson(json, Bean.class);
            assertNotNull(bean);
            assertEquals("Test", bean.getName());
            assertEquals(10, bean.getAge());
        }

        @Test
        void toEnum() {
            Color color = JSONUtils.fromJson("\"RED\"", Color.class);
            assertEquals(Color.RED, color);
        }
    }

    // ========================================================================
    // numberToString
    // ========================================================================

    @Nested
    class NumberToString {

        @Test
        void integer() {
            assertEquals("42", JSONUtils.numberToString(42));
        }

        @Test
        void longValue() {
            assertEquals("9223372036854775807", JSONUtils.numberToString(9223372036854775807L));
        }

        @Test
        void doubleValue() {
            assertEquals("3.14", JSONUtils.numberToString(3.14));
        }

        @Test
        void negativeZero() {
            assertEquals("-0", JSONUtils.numberToString(-0d));
        }

        @Test
        void zero() {
            assertEquals("0", JSONUtils.numberToString(0));
            assertEquals("0", JSONUtils.numberToString(0d));
        }

        @Test
        void negativeNumber() {
            assertEquals("-7", JSONUtils.numberToString(-7));
        }

        @Test
        void smallFraction() {
            assertEquals("0.5", JSONUtils.numberToString(0.5));
        }

        @Test
        void nullThrows() {
            assertThrows(RuntimeException.class, () -> JSONUtils.numberToString(null));
        }

        @Test
        void nanThrows() {
            assertThrows(RuntimeException.class, () -> JSONUtils.numberToString(Double.NaN));
        }

        @Test
        void infinityThrows() {
            assertThrows(RuntimeException.class, () -> JSONUtils.numberToString(Double.POSITIVE_INFINITY));
            assertThrows(RuntimeException.class, () -> JSONUtils.numberToString(Double.NEGATIVE_INFINITY));
        }

        @Test
        void floatValue() {
            assertEquals("3.14", JSONUtils.numberToString(3.14f));
        }

        @Test
        void integerDouble() {
            // A double that is a whole number should render without decimal
            assertEquals("5", JSONUtils.numberToString(5.0d));
        }

        @Test
        void negativeIntDouble() {
            assertEquals("-10", JSONUtils.numberToString(-10.0d));
        }
    }

    // ========================================================================
    // quote
    // ========================================================================

    @Nested
    class Quote {

        @Test
        void nullInput() {
            assertEquals("\"\"", JSONUtils.quote(null));
        }

        @Test
        void emptyString() {
            assertEquals("\"\"", JSONUtils.quote(""));
        }

        @Test
        void plainText() {
            assertEquals("\"hello\"", JSONUtils.quote("hello"));
        }

        @Test
        void doubleQuote() {
            assertEquals("\"she said \\\"hi\\\"\"", JSONUtils.quote("she said \"hi\""));
        }

        @Test
        void backslash() {
            assertEquals("\"path\\\\to\\\\file\"", JSONUtils.quote("path\\to\\file"));
        }

        @Test
        void tab() {
            assertEquals("\"tab\\there\"", JSONUtils.quote("tab\there"));
        }

        @Test
        void newline() {
            assertEquals("\"line1\\nline2\"", JSONUtils.quote("line1\nline2"));
        }

        @Test
        void carriageReturn() {
            assertEquals("\"line1\\rline2\"", JSONUtils.quote("line1\rline2"));
        }

        @Test
        void backspace() {
            assertEquals("\"a\\bb\"", JSONUtils.quote("a\bb"));
        }

        @Test
        void formFeed() {
            assertEquals("\"a\\fb\"", JSONUtils.quote("a\fb"));
        }

        @Test
        void controlCharacter() {
            // 0x1F (Unit Separator) should be unicode-escaped
            assertEquals("\"\\u001f\"", JSONUtils.quote("\u001f"));
        }

        @Test
        void unicodeRange0080() {
            // Character at boundary 0x0080 should be escaped
            assertEquals("\"\\u0080\"", JSONUtils.quote("\u0080"));
        }

        @Test
        void unicodeRange2000() {
            // Character at boundary 0x2000 should be escaped
            assertEquals("\"\\u2000\"", JSONUtils.quote("\u2000"));
        }

        @Test
        void asciiPrintableNotEscaped() {
            // Normal ASCII printable should not be escaped
            String input = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            // Only " and \ are escaped within this range
            String expected = " !\\\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~";
            assertEquals("\"" + expected + "\"", JSONUtils.quote(input));
        }
    }

    // ========================================================================
    // Round-trip tests: toJson + fromJson should be inverse
    // ========================================================================

    @Nested
    class RoundTrip {

        @Test
        void string() {
            String original = "hello world";
            assertEquals(original, JSONUtils.fromJson(JSONUtils.toJson(original), String.class));
        }

        @Test
        void integer() {
            assertEquals(42, JSONUtils.fromJson(JSONUtils.toJson(42)));
        }

        @Test
        void booleanValue() {
            assertEquals(true, JSONUtils.fromJson(JSONUtils.toJson(true)));
            assertEquals(false, JSONUtils.fromJson(JSONUtils.toJson(false)));
        }

        @Test
        void mapToJsonObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", 1);
            map.put("b", "hello");
            map.put("c", true);
            String json = JSONUtils.toJson(map);
            JSONObject parsed = (JSONObject) JSONUtils.fromJson(json);
            assertEquals(1, parsed.get("a"));
            assertEquals("hello", parsed.get("b"));
            assertEquals(true, parsed.get("c"));
        }

        @Test
        void listToJsonArray() {
            List<Object> list = List.of(1, "two", false);
            String json = JSONUtils.toJson(list);
            JSONArray parsed = (JSONArray) JSONUtils.fromJson(json);
            assertEquals(1, parsed.get(0));
            assertEquals("two", parsed.get(1));
            assertEquals(false, parsed.get(2));
        }
    }

    // ========================================================================
    // Test data types
    // ========================================================================

    public record Point(int x, int y) {}

    public enum Color {
        RED, GREEN, BLUE
    }

    public static class Bean {
        private String name;
        private int age;

        public Bean() {}

        public Bean(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class ActiveBean {
        private boolean enabled;
        private String status;

        public ActiveBean() {}

        public ActiveBean(boolean enabled, String status) {
            this.enabled = enabled;
            this.status = status;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class Parent {
        private String name;
        private Bean child;

        public Parent() {}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Bean getChild() {
            return child;
        }

        public void setChild(Bean child) {
            this.child = child;
        }
    }
}
