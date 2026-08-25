package ai.alaser.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotConfigurationTest {
    @Test
    fun rejectsUnknownUsersByDefault() {
        val config = TelegramBotConfiguration("id", "bot", "secret", "workspace", emptySet(), emptySet())
        assertFalse(config.accepts(42, 100))
    }

    @Test
    fun acceptsAllowedUsersInAllowedChats() {
        val config = TelegramBotConfiguration("id", "bot", "secret", "workspace", setOf(42), setOf(100))
        assertTrue(config.accepts(42, 100))
        assertFalse(config.accepts(42, 200))
        assertFalse(config.accepts(99, 100))
        assertFalse(config.accepts(null, 100))
    }
}
