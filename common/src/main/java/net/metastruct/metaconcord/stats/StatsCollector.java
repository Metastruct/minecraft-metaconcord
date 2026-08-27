package net.metastruct.metaconcord.stats;

import com.sun.management.OperatingSystemMXBean;
import net.minecraft.server.MinecraftServer;

import net.metastruct.metaconcord.payload.Payloads;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Process stats for the website's graphs: cpu as % of one core, jvm heap,
 * host network throughput and the tick rate. Safe to call off the server
 * thread, everything read here is either jvm-level or a racy-but-harmless read.
 */
public final class StatsCollector {
	private static final Path PROC_NET_DEV = Path.of("/proc/net/dev");

	private final MinecraftServer server;
	private final OperatingSystemMXBean os;
	private final int cores = Runtime.getRuntime().availableProcessors();

	private long lastNetAt;
	private long lastRx = -1;
	private long lastTx = -1;

	public StatsCollector(MinecraftServer server) {
		this.server = server;
		java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
		this.os = bean instanceof OperatingSystemMXBean sun ? sun : null;
	}

	public String frame() {
		double load = os == null ? 0 : os.getProcessCpuLoad();
		double cpu = load < 0 ? 0 : load * 100 * cores;

		Runtime runtime = Runtime.getRuntime();
		long memUsed = runtime.totalMemory() - runtime.freeMemory();
		long memMax = runtime.maxMemory();

		double[] net = networkRates();

		long avgNanos = server.getAverageTickTimeNanos();
		double mspt = avgNanos / 1_000_000.0;
		double tps = avgNanos <= 0 ? 20 : Math.min(20, 1_000_000_000.0 / avgNanos);

		return Payloads.stats(cpu, memUsed, memMax, net[0], net[1], tps, mspt, server.getPlayerCount());
	}

	/** Bytes/s received and sent across every interface but loopback, 0 when /proc is unavailable. */
	private double[] networkRates() {
		long rx = 0;
		long tx = 0;
		try {
			List<String> lines = Files.readAllLines(PROC_NET_DEV);
			for (int i = 2; i < lines.size(); i++) {
				String line = lines.get(i).trim();
				int colon = line.indexOf(':');
				if (colon < 0) continue;
				String iface = line.substring(0, colon).trim();
				if (iface.equals("lo")) continue;
				String[] cols = line.substring(colon + 1).trim().split("\\s+");
				if (cols.length < 9) continue;
				rx += Long.parseLong(cols[0]);
				tx += Long.parseLong(cols[8]);
			}
		} catch (IOException | RuntimeException e) {
			return new double[] {0, 0};
		}

		long now = System.nanoTime();
		double[] rates = {0, 0};
		if (lastRx >= 0 && now > lastNetAt) {
			double seconds = (now - lastNetAt) / 1_000_000_000.0;
			rates[0] = Math.max(0, (rx - lastRx) / seconds);
			rates[1] = Math.max(0, (tx - lastTx) / seconds);
		}
		lastNetAt = now;
		lastRx = rx;
		lastTx = tx;
		return rates;
	}
}
