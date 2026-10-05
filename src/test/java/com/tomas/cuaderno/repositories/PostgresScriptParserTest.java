package com.tomas.cuaderno.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresScriptParserTest {
    @Test void splitsOnlyAtTopLevelSemicolons() {
        var sql = "select ';' as value; select $$a;b$$; /* outer; /* nested; */ done */ select 3";
        assertEquals(List.of("select ';' as value", "select $$a;b$$", "/* outer; /* nested; */ done */ select 3"), PostgresScriptParser.split(sql));
    }
    @Test void ignoresSemicolonsInsideDollarQuotedProcedureBodies() {
        assertEquals(2, PostgresScriptParser.split("select 1; select $body$ begin perform 2; end $body$").size());
    }
    @Test void rejectsTransactionControlEvenAfterComments() {
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("/* comment */ COMMIT"));
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("begin; select 1"));
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("call public.some_procedure()"));
    }
    @Test void rejectsUnclosedSyntaxAndEmptyScripts() {
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("select 'unfinished"));
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("-- comment only"));
        assertThrows(IllegalArgumentException.class, () -> PostgresScriptParser.split("select 1;".repeat(51)));
    }
}
