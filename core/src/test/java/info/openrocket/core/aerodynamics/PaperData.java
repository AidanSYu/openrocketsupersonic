package info.openrocket.core.aerodynamics;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the repository's {@code paper/data} tree for benchmark tests that publish CSV or
 * Markdown artifacts.
 * <p>
 * These tests must not build their output paths from the current working directory. Gradle
 * runs each module's tests with the working directory set to that module, so a bare
 * {@code Path.of("paper", "data", "csv")} resolves to {@code core/paper/data/csv} -- a
 * second, stray copy of the results that shadows the real one and gets committed by
 * accident. Resolving from the worktree root instead makes the output location the same
 * whether the tests are launched from the repository root, from {@code core/}, or from an
 * IDE.
 */
public final class PaperData {

	/** Maximum directories to climb before giving up on finding the worktree root. */
	private static final int MAX_DEPTH = 6;

	private PaperData() {
	}

	/**
	 * @return the worktree root, i.e. the nearest enclosing directory that contains
	 *         {@code paper/data}; the working directory if none is found
	 */
	public static Path root() {
		Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		for (int i = 0; i < MAX_DEPTH && current != null; i++) {
			if (current.resolve("paper").resolve("data").toFile().isDirectory()) {
				return current;
			}
			current = current.getParent();
		}
		return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
	}

	/**
	 * @param parts path elements below {@code paper/data}, e.g. {@code "csv", "foo.csv"}
	 * @return absolute path to that artifact
	 */
	public static Path resolve(String... parts) {
		Path p = root().resolve("paper").resolve("data");
		for (String part : parts) {
			p = p.resolve(part);
		}
		return p;
	}

	/** @return absolute path to a file in {@code paper/data/csv} */
	public static Path csv(String fileName) {
		return resolve("csv", fileName);
	}

	/** @return absolute path to the {@code paper/data/csv} directory */
	public static Path csvDir() {
		return resolve("csv");
	}
}
