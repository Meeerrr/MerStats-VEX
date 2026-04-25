package com.merstats.vex.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EloEngineTest {

    @Test
    public void testEngineInitialization() {
        // Instantiate your ACTUAL class from the main folder
        EloEngine engine = new EloEngine();

        // A basic check to ensure the engine builds successfully without crashing
        assertNotNull(engine, "The EloEngine failed to initialize!");

        System.out.println("✅ EloEngine linked and tested successfully!");
    }
}