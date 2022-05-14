package jadx.core.utils.files;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.utils.exceptions.JadxRuntimeException;

public class FileUtils {
	private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);

	public static final int READ_BUFFER_SIZE = 8 * 1024;
	private static final int MAX_FILENAME_LENGTH = 128;

	public static final String JADX_TMP_INSTANCE_PREFIX = "jadx-instance-";
	public static final String JADX_TMP_PREFIX = "jadx-tmp-";

	private FileUtils() {
	}

	public static List<Path> expandDirs(List<Path> paths) {
		List<Path> files = new ArrayList<>(paths.size());
		for (Path path : paths) {
			if (Files.isDirectory(path)) {
				expandDir(path, files);
			} else {
				files.add(path);
			}
		}
		return files;
	}

	private static void expandDir(Path dir, List<Path> files) {
		try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
			walk.filter(Files::isRegularFile).forEach(files::add);
		} catch (Exception e) {
			LOG.error("Failed to list files in directory: {}", dir, e);
		}
	}

	public static void addFileToJar(JarOutputStream jar, File source, String entryName) throws IOException {
		try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
			JarEntry entry = new JarEntry(entryName);
			entry.setTime(source.lastModified());
			jar.putNextEntry(entry);

			copyStream(in, jar);
			jar.closeEntry();
		}
	}

	public static void makeDirsForFile(Path path) {
		if (path != null) {
			makeDirs(path.toAbsolutePath().getParent().toFile());
		}
	}

	public static void makeDirsForFile(File file) {
		if (file != null) {
			makeDirs(file.getParentFile());
		}
	}

	private static final Object MKDIR_SYNC = new Object();

	public static void makeDirs(@Nullable File dir) {
		if (dir != null) {
			synchronized (MKDIR_SYNC) {
				if (!dir.mkdirs() && !dir.isDirectory()) {
					throw new JadxRuntimeException("Can't create directory " + dir);
				}
			}
		}
	}

	public static void makeDirs(@Nullable Path dir) {
		if (dir != null) {
			makeDirs(dir.toFile());
		}
	}

	public static boolean deleteDir(File dir) {
		File[] content = dir.listFiles();
		if (content != null) {
			for (File file : content) {
				deleteDir(file);
			}
		}
		return dir.delete();
	}

	public static void deleteDirIfExists(Path dir) {
		if (Files.exists(dir)) {
			deleteDir(dir);
		}
	}

	public static void deleteDir(Path dir) {
		try (Stream<Path> pathStream = Files.walk(dir)) {
			pathStream.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(file -> {
						if (!file.delete()) {
							LOG.warn("Failed to remove file: {}", file.getAbsolutePath());
						}
					});
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to delete directory " + dir, e);
		}
	}

	private static final Path TEMP_ROOT_DIR = createTempRootDir();

	private static Path createTempRootDir() {
		try {
			String jadxTmpDir = System.getenv("JADX_TMP_DIR");
			Path dir;
			if (jadxTmpDir != null) {
				dir = Files.createTempDirectory(Paths.get(jadxTmpDir), "jadx-instance-");
			} else {
				dir = Files.createTempDirectory(JADX_TMP_INSTANCE_PREFIX);
			}
			dir.toFile().deleteOnExit();
			return dir;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp root directory", e);
		}
	}

	public static void deleteTempRootDir() {
		deleteDir(TEMP_ROOT_DIR);
	}

	public static void clearTempRootDir() {
		deleteDir(TEMP_ROOT_DIR);
		makeDirs(TEMP_ROOT_DIR);
	}

	public static Path createTempDir(String prefix) {
		try {
			Path dir = Files.createTempDirectory(TEMP_ROOT_DIR, prefix);
			dir.toFile().deleteOnExit();
			return dir;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp directory with suffix: " + prefix, e);
		}
	}

	public static Path createTempFile(String suffix) {
		try {
			Path path = Files.createTempFile(TEMP_ROOT_DIR, JADX_TMP_PREFIX, suffix);
			path.toFile().deleteOnExit();
			return path;
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp file with suffix: " + suffix, e);
		}
	}

	public static Path createTempFileNoDelete(String suffix) {
		try {
			return Files.createTempFile(Files.createTempDirectory("jadx-persist"), "jadx-", suffix);
		} catch (Exception e) {
			throw new JadxRuntimeException("Failed to create temp file with suffix: " + suffix, e);
		}
	}

	public static void copyStream(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[READ_BUFFER_SIZE];
		while (true) {
			int count = input.read(buffer);
			if (count == -1) {
				break;
			}
			output.write(buffer, 0, count);
		}
	}

	public static byte[] streamToByteArray(InputStream input) throws IOException {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			copyStream(input, out);
			return out.toByteArray();
		}
	}

	public static void close(Closeable c) {
		if (c == null) {
			return;
		}
		try {
			c.close();
		} catch (IOException e) {
			LOG.error("Close exception for {}", c, e);
		}
	}

	@NotNull
	public static File prepareFile(File file) {
		File saveFile = cutFileName(file);
		makeDirsForFile(saveFile);
		return saveFile;
	}

	private static File cutFileName(File file) {
		String name = file.getName();
		if (name.length() <= MAX_FILENAME_LENGTH) {
			return file;
		}
		int dotIndex = name.indexOf('.');
		int cutAt = MAX_FILENAME_LENGTH - name.length() + dotIndex - 1;
		if (cutAt <= 0) {
			name = name.substring(0, MAX_FILENAME_LENGTH - 1);
		} else {
			name = name.substring(0, cutAt) + name.substring(dotIndex);
		}
		return new File(file.getParentFile(), name);
	}

	private static String bytesToHex(byte[] bytes) {
		char[] hexArray = "0123456789abcdef".toCharArray();
		if (bytes == null || bytes.length <= 0) {
			return null;
		}
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static boolean isZipFile(File file) {
		try (InputStream is = new FileInputStream(file)) {
			byte[] headers = new byte[4];
			int read = is.read(headers, 0, 4);
			if (read == headers.length) {
				String headerString = bytesToHex(headers);
				if (Objects.equals(headerString, "504b0304")) {
					return true;
				}
			}
		} catch (Exception e) {
			LOG.error("Failed read zip file: {}", file.getAbsolutePath(), e);
		}
		return false;
	}

	public static String getPathBaseName(Path file) {
		String fileName = file.getFileName().toString();
		int extEndIndex = fileName.lastIndexOf('.');
		if (extEndIndex == -1) {
			return fileName;
		}
		return fileName.substring(0, extEndIndex);
	}

	public static File toFile(String path) {
		if (path == null) {
			return null;
		}
		return new File(path);
	}

	public static List<Path> toPaths(List<File> files) {
		return files.stream().map(File::toPath).collect(Collectors.toList());
	}

	public static List<Path> toPaths(File[] files) {
		return Stream.of(files).map(File::toPath).collect(Collectors.toList());
	}

	public static List<Path> fileNamesToPaths(List<String> fileNames) {
		return fileNames.stream().map(Paths::get).collect(Collectors.toList());
	}

	public static List<File> toFiles(List<Path> paths) {
		return paths.stream().map(Path::toFile).collect(Collectors.toList());
	}
}
