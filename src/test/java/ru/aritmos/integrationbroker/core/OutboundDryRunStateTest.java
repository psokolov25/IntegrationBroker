package ru.aritmos.integrationbroker.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboundDryRunStateTest {

    @Test
    void shouldToggleAndResetOverride() {
        OutboundDryRunState state = new OutboundDryRunState(false, null);

        assertFalse(state.effective(false));
        assertNull(state.overrideValue());

        state.setOverride(true);
        assertTrue(state.effective(false));
        assertEquals(Boolean.TRUE, state.overrideValue());

        state.setOverride(false);
        assertFalse(state.effective(true));

        state.resetOverride();
        assertNull(state.overrideValue());
        assertFalse(state.effective(false));
    }
}
