package me.trae.foundation.utilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilStringTest {

    @ParameterizedTest
    @CsvSource({"'hello world', 'Hello World'", "'  TWO   words  ', 'Two Words'", "'Mixed_case text', 'Mixed Case Text'"})
    void cleansWordsAndWhitespace(final String input, final String expected) {
        assertEquals(expected, UtilString.clean(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void treatsNullAndBlankAsEmpty(final String input) {
        assertTrue(UtilString.isEmpty(input));
        assertNull(UtilString.clean(input));
        assertNull(UtilString.slice(input));
        assertNull(UtilString.unSlice(input));
    }

    @Test
    void slicesSeparatorsAndUnSlicesCamelCase() {
        assertEquals("HelloWorld", UtilString.slice("Hello_World."));
        assertEquals("hello World", UtilString.unSlice("hello_World"));
        assertEquals("HTTP Server", UtilString.unSlice("HTTPServer"));
        assertEquals("Already Spaced", UtilString.unSlice("Already Spaced"));
    }

    @ParameterizedTest
    @CsvSource({"'hour', 'an '", "'university', 'a '", "'honest person', 'an '", "'European', 'a '", "'apple', 'an '", "'banana', 'a '"})
    void selectsAnArticleByPronunciationRules(final String input, final String expected) {
        assertEquals(expected, UtilString.getIndefiniteArticlePrefix(input));
        assertEquals(expected + input, UtilString.withIndefiniteArticle(input));
    }

    @Test
    void articleFormattingPreservesEmptyInput() {
        assertEquals("", UtilString.getIndefiniteArticlePrefix(" "));
        assertEquals(" ", UtilString.withIndefiniteArticle(" "));
        assertEquals("$1,234", UtilString.formatToDollarByInteger(1234));
        assertEquals("$1,234.50", UtilString.formatToDollarByDouble(1234.5));
        assertEquals("name: value", UtilString.pair("name", "value"));
    }
}
