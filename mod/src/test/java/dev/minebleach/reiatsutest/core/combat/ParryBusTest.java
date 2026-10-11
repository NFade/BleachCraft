package dev.minebleach.reiatsutest.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParryBusTest {
	@BeforeEach
	@AfterEach
	void reset() {
		ParryBus.clear();
		ParryBus.setErrorHandler(null);
	}

	@Test
	void listenerReceivesTheEventAndCanBeRemoved() {
		List<ParryEvent> got = new ArrayList<>();
		Runnable off = ParryBus.register(got::add);
		ParryEvent e = new ParryEvent(UUID.randomUUID(), UUID.randomUUID(), 100, 6.0F);
		ParryBus.fire(e);
		assertEquals(List.of(e), got);
		off.run();
		ParryBus.fire(e);
		assertEquals(1, got.size());
		assertEquals(0, ParryBus.listenerCount());
	}

	@Test
	void failingListenerDoesNotStopTheOthers() {
		List<Throwable> errors = new ArrayList<>();
		ParryBus.setErrorHandler(errors::add);
		List<ParryEvent> got = new ArrayList<>();
		ParryBus.register(ev -> {
			throw new IllegalStateException("boom");
		});
		ParryBus.register(got::add);
		ParryBus.fire(new ParryEvent(UUID.randomUUID(), UUID.randomUUID(), 1, 1.0F));
		assertEquals(1, got.size());
		assertEquals(1, errors.size());
	}
}
