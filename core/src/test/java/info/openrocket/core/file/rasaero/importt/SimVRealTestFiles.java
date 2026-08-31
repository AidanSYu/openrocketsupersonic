package info.openrocket.core.file.rasaero.importt;

import java.io.File;

/**
 * Locates SimVReal validation assets (CDX1 rocket definitions, .eng motor files)
 * regardless of the working directory a test happens to run in.
 * <p>
 * Gradle executes the core test suite with the working directory set to the
 * {@code core/} module, while the assets live in {@code simvreal/} at the
 * repository root. Call sites used to hard-code a two-entry candidate list of
 * {@code "simvreal/..."} plus an absolute {@code "c:/Code/OpenRocket Plus/..."}
 * developer path, so they resolved on exactly one machine and failed everywhere
 * else. This helper walks up from the working directory looking for the
 * repository root instead.
 */
public final class SimVRealTestFiles {

	/** Maximum number of parent directories to examine when hunting for the repo root. */
	private static final int MAX_DEPTH = 6;

	private SimVRealTestFiles() {
		// utility
	}

	/**
	 * Resolve a path expressed relative to the repository root.
	 *
	 * @param relativePath e.g. {@code "simvreal/RasAero Sims/Torrent.CDX1"}
	 * @return the existing file, or {@code null} if it cannot be found
	 */
	public static File find(String relativePath) {
		File dir = new File(System.getProperty("user.dir"));
		for (int i = 0; i <= MAX_DEPTH && dir != null; i++, dir = dir.getParentFile()) {
			File candidate = new File(dir, relativePath);
			if (candidate.exists()) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Resolve a CDX1 rocket definition by bare filename.
	 *
	 * @param filename e.g. {@code "Torrent.CDX1"}
	 * @return the existing file, or {@code null} if it cannot be found
	 */
	public static File findCdx1(String filename) {
		return find("simvreal/RasAero Sims/" + filename);
	}

	/**
	 * Absolute path for a repo-root-relative asset, or {@code null} when absent.
	 * Convenience for call sites that thread a {@code String} path around.
	 */
	public static String findPath(String relativePath) {
		File f = find(relativePath);
		return f == null ? null : f.getAbsolutePath();
	}
}
