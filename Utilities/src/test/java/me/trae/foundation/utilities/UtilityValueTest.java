package me.trae.foundation.utilities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilityValueTest {

    @Test
    void base64RoundTripsUtf8AndRejectsMalformedInput() {
        final String input = "héllo 🌍";
        final String encoded = UtilBase64.encodeToString(input);

        assertEquals(input, UtilBase64.decodeToString(encoded));
        assertArrayEquals(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), UtilBase64.decodeToBytes(UtilBase64.encodeToBytes(input)));
        assertThrows(IllegalArgumentException.class, () -> UtilBase64.decodeToString("%%%"));
    }

    @Test
    void hashesAndHmacsUseExpectedEncodingAndVerifyContent() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UtilHash.hashToString("SHA-256", "abc"));
        final String hash = UtilHash.hashToString("SHA-256", "password");
        assertTrue(UtilHash.verify("SHA-256", "password", hash));
        assertFalse(UtilHash.verify("SHA-256", "other", hash));
        assertEquals("00ff", UtilHash.toHex(new byte[]{0, (byte) 0xff}));
        assertArrayEquals(new byte[]{0, (byte) 0xff}, UtilHash.fromHex("00ff"));
        assertNotEquals(UtilHash.hmac("HmacSHA256", "key-one", "message"), UtilHash.hmac("HmacSHA256", "key-two", "message"));
        assertThrows(IllegalArgumentException.class, () -> UtilHash.fromHex("xyz"));
    }

    @Test
    void parsesInputSafelyAndAppliesNumericBounds() {
        assertEquals(Optional.of(42), UtilInput.getInput(Integer.class, "42"));
        assertEquals(Optional.of("hello"), UtilInput.getInput(String.class, "hello"));
        assertEquals(Optional.of(true), UtilInput.getInput(Boolean.class, "TRUE"));
        assertTrue(UtilInput.getInput(Boolean.class, "yes").isEmpty());
        assertTrue(UtilInput.getInput(Double.class, "NaN").isEmpty());
        assertTrue(UtilInput.getInput(Integer.class, "not a number").isEmpty());
        assertEquals(Optional.of(5), UtilInput.getNumber(Integer.class, 1, 10, "5"));
        assertTrue(UtilInput.getNumber(Integer.class, 1, 10, "11").isEmpty());
        assertTrue(UtilInput.getInput(UtilityValueTest.class, "unsupported").isEmpty());
    }

    @Test
    void typeChecksHandleUnicodeNumbersAndNonFiniteValues() {
        assertTrue(UtilType.isAlphabetic("café"));
        assertFalse(UtilType.isAlphabetic("abc1"));
        assertTrue(UtilType.isNumeric("１２３"));
        assertFalse(UtilType.isNumeric("12.3"));
        assertTrue(UtilType.isInteger("-12"));
        assertFalse(UtilType.isInteger("2147483648"));
        assertTrue(UtilType.isDouble("1.25"));
        assertFalse(UtilType.isDouble("Infinity"));
        assertTrue(UtilType.isFloat("1.25"));
        assertFalse(UtilType.isFloat("NaN"));
        assertTrue(UtilType.isLong("9223372036854775807"));
        assertFalse(UtilType.isLong("9223372036854775808"));
        assertFalse(UtilType.isAllMatch("", Character::isLetter));
        assertFalse(UtilType.isAnyMatch(null, Character::isLetter));
    }

    @Test
    void numberFormattingClampingAndRandomRangeWorkAtBoundaries() {
        assertEquals("1,234.57", UtilNumber.format("#,##0.00", 1234.567));
        assertEquals(0, UtilNumber.clamp(0, 10, -1));
        assertEquals(10, UtilNumber.clamp(0, 10, 11));
        assertEquals(5, UtilNumber.clamp(0, 10, 5));
        assertEquals(7, UtilNumber.getRandomNumber(Integer.class, 7, 7));
        for (int i = 0; i < 100; i++) {
            final int value = UtilNumber.getRandomNumber(Integer.class, -2, 3);
            assertTrue(value >= -2 && value < 3);
        }
        assertThrows(IllegalArgumentException.class, () -> UtilNumber.getRandomNumber(BigDecimal.class, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void generatedCodesHaveRequestedLengthAndAlphabet() {
        assertEquals("", UtilCode.generate(0, new char[]{'x'}));
        assertThrows(NegativeArraySizeException.class, () -> UtilCode.generate(-1, new char[]{'x'}));
        assertThrows(IllegalArgumentException.class, () -> UtilCode.generate(1, new char[0]));
        final String custom = UtilCode.generate(128, new char[]{'x', 'y'});
        assertEquals(128, custom.length());
        assertTrue(custom.chars().allMatch(value -> value == 'x' || value == 'y'));
        assertTrue(UtilCode.generateUpperCase(64).matches("[A-Z0-9]{64}"));
        assertTrue(UtilCode.generateRandom(64).matches("[A-Za-z0-9]{64}"));
    }

    @Test
    void reflectionHelpersSupportPrivateMembersAndBoxedPrimitives() throws Exception {
        final Fixture fixture = new Fixture();
        final Field field = Fixture.class.getDeclaredField("count");
        UtilField.set(fixture, field, 4);
        assertEquals(4, UtilField.get(Integer.class, fixture, field));
        assertThrows(IllegalStateException.class, () -> UtilField.get(String.class, fixture, field));

        final Method method = Fixture.class.getDeclaredMethod("add", int.class);
        UtilMethod.invoke(fixture, method, 3);
        assertEquals(7, fixture.count);

        assertEquals("value", UtilClass.create(Constructed.class, "value", 3).value);
        assertEquals(0, UtilClass.create(Constructed.class).number);
        assertThrows(NoSuchMethodException.class, () -> UtilClass.create(Constructed.class, true));
        assertEquals("UtilityValueTest$Fixture", UtilClass.formatName("me.trae.foundation.utilities", Fixture.class));
        assertEquals(Fixture.class.getName(), UtilClass.formatName("wrong.package", Fixture.class));
        assertSame(fixture, UtilJava.cast(Fixture.class, fixture));
        assertNull(UtilJava.cast(String.class, fixture));
        assertNull(UtilJava.cast(Fixture.class, null));
    }

    @Test
    void collectionMapAndFileHelpersMutateAndCacheExpectedValues(@org.junit.jupiter.api.io.TempDir final Path directory) throws IOException {
        final List<String> list = UtilJava.createCollection(new ArrayList<>(), values -> values.add("one"));
        UtilJava.updateCollection(list, values -> values.add("two"));
        assertEquals(List.of("one", "two"), list);

        final Map<String, Integer> map = UtilJava.createMap(new HashMap<>(), values -> values.put("one", 1));
        UtilJava.updateMap(map, values -> values.put("two", 2));
        assertEquals(Map.of("one", 1, "two", 2), map);

        final Path file = directory.resolve("lines.txt");
        Files.writeString(file, "one\ntwo\n");
        assertEquals(List.of("one", "two"), UtilFile.read(file));
        assertEquals(List.of("one", "two"), UtilFile.read(file.toFile()));
        assertEquals(List.of("one", "two"), UtilFile.read(file.toString()));
        Files.writeString(file, "changed content\n");
        assertEquals(List.of("changed content"), UtilFile.read(file));
        assertThrows(IllegalArgumentException.class, () -> UtilFile.read((Path) null));
        assertThrows(java.io.UncheckedIOException.class, () -> UtilFile.read(directory.resolve("missing")));
    }

    @Test
    void genericTypesResolveThroughInheritanceAndInterfaces() {
        assertEquals(String.class, UtilGeneric.getGenericParameter(StringList.class, List.class, 0));
        assertEquals(String.class, UtilGeneric.getGenericParameter(StringChild.class, GenericContract.class, 0));
        assertEquals(Integer.class, UtilGeneric.getGenericParameter(Pair.class, TwoValues.class, 1));
    }

    private static final class Fixture {

        private int count;

        private void add(final int value) {
            this.count += value;
        }
    }

    private static final class Constructed {

        private final String value;
        private final int number;

        private Constructed() {
            this(null, 0);
        }

        private Constructed(final String value, final int number) {
            this.value = value;
            this.number = number;
        }
    }

    private static final class StringList extends ArrayList<String> {
    }

    private interface GenericContract<T> {
    }

    private static class GenericBase<T> implements GenericContract<T> {
    }

    private static final class StringChild extends GenericBase<String> {
    }

    private interface TwoValues<A, B> {
    }

    private static final class Pair implements TwoValues<String, Integer> {
    }
}