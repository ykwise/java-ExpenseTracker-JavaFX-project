package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class MainAppTest {

    @Test
    void userMessage_unwrapsFxmlHandlerWrappingToOurMessage() {
        Throwable wrapped = new RuntimeException(new InvocationTargetException(
                new DataAccessException("Could not save the expense", new java.sql.SQLException("locked"))));
        assertEquals("Could not save the expense", MainApp.userMessage(wrapped));
    }

    @Test
    void startupErrorMessage_explainsTheCauseAndReassures() {
        String message = MainApp.startupErrorMessage(new DataAccessException("Could not initialise the database",
                new java.sql.SQLException("database is locked")));
        assertTrue(message.startsWith("Could not initialise the database."), message);
        assertTrue(message.contains("already open"), message);
        assertTrue(message.contains("has not been changed"), message);
    }

    @Test
    void userMessage_fallsBackToRootCause() {
        assertEquals("disk full", MainApp.userMessage(new RuntimeException(new java.io.IOException("disk full"))));
    }
}
