package ink.ziip.championshipscore.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.configuration.config.CCConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

/** Ordering deliberately exposes configuration leaks between methods and nested containers. */
@ExtendWith(ConfigurationStateExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class ConfigurationStateExtensionTest {
    @BeforeAll
    static void classDefaults() {
        CCConfig.MODE = "class-default";
    }

    @Test
    @Order(1)
    void methodMayReplaceConfiguration() {
        assertEquals("class-default", CCConfig.MODE);
        CCConfig.MODE = "method-override";
    }

    @Test
    @Order(2)
    void nextMethodReceivesClassDefaults() {
        assertEquals("class-default", CCConfig.MODE);
    }

    @Nested
    @Order(1)
    class NestedOverrides {
        @BeforeAll
        static void nestedDefaults() {
            CCConfig.MODE = "nested-default";
        }

        @Test
        void nestedMethodMayReplaceConfiguration() {
            assertEquals("nested-default", CCConfig.MODE);
            CCConfig.MODE = "nested-method-override";
        }

        @AfterAll
        static void nestedDefaultsSurviveMethodCleanup() {
            assertEquals("nested-default", CCConfig.MODE);
        }
    }

    @Nested
    @Order(2)
    class SiblingContainer {
        @Test
        void nestedOverridesDoNotLeakToSiblings() {
            assertEquals("class-default", CCConfig.MODE);
        }
    }

    @AfterAll
    static void classDefaultsSurviveNestedCleanup() {
        assertEquals("class-default", CCConfig.MODE);
    }
}
