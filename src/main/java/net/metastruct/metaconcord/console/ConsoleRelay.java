package net.metastruct.metaconcord.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import net.metastruct.metaconcord.payload.Payloads;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Captures the server log for the website console. Every line lands in a
 * replay ring (sent on subscribe) and, while subscribed, in a queue flushed
 * as one ConsolePayload per {@link #flush()} call. The appender is called
 * from arbitrary threads and must never block or log itself.
 */
public final class ConsoleRelay extends AbstractAppender {
	private static final int QUEUE_CAP = 1000;
	private static final int REPLAY_CAP = 300;
	private static final int FLUSH_CAP = 200;
	private static final String PATTERN = "[%d{HH:mm:ss}] [%t/%level] [%logger{1}]: %msg%n%throwable";

	private final Object lock = new Object();
	private final ArrayDeque<Line> queue = new ArrayDeque<>();
	private final ArrayDeque<Line> replay = new ArrayDeque<>();
	private volatile boolean subscribed = false;
	private Consumer<String> sink = s -> {};

	public record Line(String level, String text) {}

	public ConsoleRelay() {
		super("metaconcord-console", null,
			PatternLayout.newBuilder().withPattern(PATTERN).withCharset(StandardCharsets.UTF_8).build(),
			true, Property.EMPTY_ARRAY);
		start();
	}

	public void attach(Consumer<String> sink) {
		this.sink = sink;
		((Logger) LogManager.getRootLogger()).addAppender(this);
	}

	public void detach() {
		((Logger) LogManager.getRootLogger()).removeAppender(this);
		stop();
	}

	public void setSubscribed(boolean subscribed) {
		this.subscribed = subscribed;
		if (!subscribed) {
			synchronized (lock) {
				queue.clear();
			}
		}
	}

	public boolean isSubscribed() {
		return subscribed;
	}

	@Override
	public void append(LogEvent event) {
		String text = new String(getLayout().toByteArray(event), StandardCharsets.UTF_8);
		if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
		if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
		Line line = new Line(event.getLevel().name(), text);
		synchronized (lock) {
			replay.addLast(line);
			if (replay.size() > REPLAY_CAP) replay.removeFirst();
			if (subscribed) {
				queue.addLast(line);
				if (queue.size() > QUEUE_CAP) queue.removeFirst();
			}
		}
	}

	/** Sends the replay ring as one payload, for a session that just subscribed. */
	public void sendReplay() {
		List<Line> lines;
		synchronized (lock) {
			lines = new ArrayList<>(replay);
		}
		sink.accept(Payloads.console(lines, true));
	}

	/** Sends queued live lines, called periodically from the socket scheduler. */
	public void flush() {
		if (!subscribed) return;
		List<Line> lines = new ArrayList<>();
		synchronized (lock) {
			while (!queue.isEmpty() && lines.size() < FLUSH_CAP) lines.add(queue.pollFirst());
		}
		if (!lines.isEmpty()) sink.accept(Payloads.console(lines, false));
	}
}
