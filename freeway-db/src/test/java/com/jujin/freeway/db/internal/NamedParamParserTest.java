package com.jujin.freeway.db.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NamedParamParserTest {

    @Test
    void shouldIgnoreParamsInStringLiterals() {
        var result = NamedParamParser.parse("SELECT * FROM users WHERE name = ':name'");
        assertTrue(result.names().isEmpty());
        assertEquals("SELECT * FROM users WHERE name = ':name'", result.jdbcSql());
    }

    @Test
    void shouldIgnoreParamsInComments() {
        var result = NamedParamParser.parse("SELECT * FROM users -- :name comment");
        assertTrue(result.names().isEmpty());
        assertEquals("SELECT * FROM users -- :name comment", result.jdbcSql());
    }

    @Test
    void shouldParseValidNamedParams() {
        var result = NamedParamParser.parse("SELECT * FROM users WHERE id = :id AND name = #name");
        assertEquals(List.of("id", "name"), result.names());
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", result.jdbcSql());
    }

    @Test
    void shouldHandlePostgresCast() {
        var result = NamedParamParser.parse("SELECT CAST(id AS VARCHAR) FROM users WHERE name = :name");
        assertEquals(List.of("name"), result.names());
        assertEquals("SELECT CAST(id AS VARCHAR) FROM users WHERE name = ?", result.jdbcSql());
    }

    @Test
    void shouldHandleEscapedQuotes() {
        var result = NamedParamParser.parse("SELECT * FROM users WHERE name = 'it''s :name'");
        assertTrue(result.names().isEmpty());
    }
}
