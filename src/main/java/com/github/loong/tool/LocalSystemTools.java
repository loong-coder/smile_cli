package com.github.loong.tool;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 提供限制在当前工作区内的文件和命令工具。
 */
public class LocalSystemTools {

    private static final int DEFAULT_MAX_BYTES = 64 * 1024;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int MAX_ENTRIES = 1000;
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    private static final long MAX_TIMEOUT_MILLIS = 120_000L;
    private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    private final Path workspaceRoot;

    public LocalSystemTools(Path workspaceRoot) throws IOException {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot cannot be null");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize().toRealPath();
    }

    @Schema(name = "read_file", title = "Read File", description = "读取工作区内文本文件内容")
    public ReadFileResult readFile(
            @Schema(name = "path", description = "工作区内文件路径", requiredMode = Schema.RequiredMode.REQUIRED) String path,
            @Schema(name = "maxBytes", description = "最大读取字节数，默认 65536，上限 1048576") Integer maxBytes,
            @Schema(name = "encoding", description = "字符编码，默认 UTF-8") String encoding) throws IOException {
        Path file = resolveExistingPath(requireText(path, "path"));
        if (Files.isDirectory(file)) {
            throw new IOException("path is a directory: " + path);
        }

        int byteLimit = boundedInt(maxBytes, DEFAULT_MAX_BYTES, MAX_BYTES, "maxBytes");
        Charset charset = charsetOrDefault(encoding);
        byte[] bytes;
        boolean truncated;
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] raw = inputStream.readNBytes(byteLimit + 1);
            truncated = raw.length > byteLimit;
            bytes = truncated ? Arrays.copyOf(raw, byteLimit) : raw;
        }

        return new ReadFileResult(relativePath(file), new String(bytes, charset), charset.name(), bytes.length, truncated);
    }

    @Schema(name = "write_file", title = "Write File", description = "写入工作区内文本文件内容")
    public WriteFileResult writeFile(
            @Schema(name = "path", description = "工作区内文件路径", requiredMode = Schema.RequiredMode.REQUIRED) String path,
            @Schema(name = "content", description = "要写入的文本内容", requiredMode = Schema.RequiredMode.REQUIRED) String content,
            @Schema(name = "encoding", description = "字符编码，默认 UTF-8") String encoding,
            @Schema(name = "createParents", description = "父目录不存在时是否创建，默认 false") Boolean createParents) throws IOException {
        Objects.requireNonNull(content, "content cannot be null");
        Path requested = normalizeWorkspacePath(requireText(path, "path"));
        boolean created = !Files.exists(requested, LinkOption.NOFOLLOW_LINKS);
        Path file = resolveWritableFile(requested, Boolean.TRUE.equals(createParents));
        if (Files.isDirectory(file)) {
            throw new IOException("path is a directory: " + path);
        }

        Charset charset = charsetOrDefault(encoding);
        byte[] bytes = content.getBytes(charset);
        OpenOption[] options = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        Files.write(file, bytes, options);
        return new WriteFileResult(relativePath(file), bytes.length, created, charset.name());
    }

    @Schema(name = "list_directory", title = "List Directory", description = "列出工作区内目录条目")
    public ListDirectoryResult listDirectory(
            @Schema(name = "path", description = "工作区内目录路径，默认工作区根目录") String path,
            @Schema(name = "recursive", description = "是否递归列出子目录，默认 false") Boolean recursive,
            @Schema(name = "maxEntries", description = "最大条目数，默认 200，上限 1000") Integer maxEntries) throws IOException {
        Path directory = resolveExistingPath(hasText(path) ? path : ".");
        if (!Files.isDirectory(directory)) {
            throw new IOException("path is not a directory: " + path);
        }

        int entryLimit = boundedInt(maxEntries, DEFAULT_MAX_ENTRIES, MAX_ENTRIES, "maxEntries");
        boolean recursiveList = Boolean.TRUE.equals(recursive);
        List<DirectoryEntry> entries = new ArrayList<>();
        boolean truncated;
        try (Stream<Path> stream = recursiveList ? Files.walk(directory) : Files.list(directory)) {
            var iterator = stream.filter(item -> !item.equals(directory)).iterator();
            truncated = collectDirectoryEntries(iterator, entries, entryLimit);
        }

        return new ListDirectoryResult(relativePath(directory), entries, truncated);
    }

    @Schema(name = "execute_command", title = "Execute Command", description = "在工作区内执行非 shell 命令数组")
    public CommandResult executeCommand(
            @Schema(name = "command", description = "命令数组，例如 [\"git\", \"status\"]", requiredMode = Schema.RequiredMode.REQUIRED) List<String> command,
            @Schema(name = "workingDirectory", description = "工作目录，默认工作区根目录") String workingDirectory,
            @Schema(name = "timeoutMillis", description = "超时时间毫秒，默认 30000，上限 120000") Long timeoutMillis,
            @Schema(name = "maxOutputBytes", description = "stdout/stderr 最大保留字节数，默认 65536，上限 1048576") Integer maxOutputBytes) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        List<String> commandLine = validateCommand(command);
        Path directory = resolveExistingPath(hasText(workingDirectory) ? workingDirectory : ".");
        if (!Files.isDirectory(directory)) {
            throw new IOException("workingDirectory is not a directory: " + workingDirectory);
        }

        long timeout = boundedLong(timeoutMillis, DEFAULT_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS, "timeoutMillis");
        int outputLimit = boundedInt(maxOutputBytes, DEFAULT_MAX_BYTES, MAX_BYTES, "maxOutputBytes");
        Process process = new ProcessBuilder(commandLine).directory(directory.toFile()).start();
        ExecutorService outputReaders = Executors.newFixedThreadPool(2);
        long startedAt = System.nanoTime();
        try {
            Future<OutputCapture> stdout = outputReaders.submit(readOutput(process.getInputStream(), outputLimit));
            Future<OutputCapture> stderr = outputReaders.submit(readOutput(process.getErrorStream(), outputLimit));
            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            boolean timedOut = !finished;
            if (timedOut) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor();
                }
            }

            OutputCapture stdoutCapture = stdout.get(5, TimeUnit.SECONDS);
            OutputCapture stderrCapture = stderr.get(5, TimeUnit.SECONDS);
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new CommandResult(commandLine, relativePath(directory), exitCode,
                    stdoutCapture.content(), stderrCapture.content(), timedOut, durationMillis,
                    stdoutCapture.truncated(), stderrCapture.truncated());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            outputReaders.shutdownNow();
        }
    }

    private Path resolveExistingPath(String path) throws IOException {
        Path normalized = normalizeWorkspacePath(path);
        if (!Files.exists(normalized)) {
            throw new NoSuchFileException(path);
        }
        Path realPath = normalized.toRealPath();
        ensureInsideWorkspace(realPath);
        return realPath;
    }

    private Path resolveWritableFile(Path requested, boolean createParents) throws IOException {
        ensureInsideWorkspace(requested);
        if (Files.isSymbolicLink(requested) && !Files.exists(requested)) {
            throw new IOException("path is a broken symbolic link: " + requested);
        }
        if (Files.exists(requested)) {
            Path realPath = requested.toRealPath();
            ensureInsideWorkspace(realPath);
            return realPath;
        }

        Path parent = requested.getParent();
        if (parent == null) {
            parent = workspaceRoot;
        }
        if (!Files.exists(parent)) {
            ensureExistingAncestorInsideWorkspace(parent);
            if (!createParents) {
                throw new NoSuchFileException(parent.toString());
            }
            Files.createDirectories(parent);
        }
        Path realParent = parent.toRealPath();
        ensureInsideWorkspace(realParent);
        return realParent.resolve(requested.getFileName()).normalize();
    }

    private void ensureExistingAncestorInsideWorkspace(Path path) throws IOException {
        Path current = path;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new NoSuchFileException(path.toString());
        }
        ensureInsideWorkspace(current.toRealPath());
    }

    private Path normalizeWorkspacePath(String path) throws IOException {
        Path rawPath = Path.of(path);
        Path normalized = rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : workspaceRoot.resolve(rawPath).normalize().toAbsolutePath();
        ensureInsideWorkspace(normalized);
        return normalized;
    }

    private void ensureInsideWorkspace(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot)) {
            throw new IOException("path is outside workspace: " + path);
        }
    }

    private boolean collectDirectoryEntries(java.util.Iterator<Path> iterator, List<DirectoryEntry> entries, int entryLimit) throws IOException {
        boolean truncated = false;
        while (iterator.hasNext()) {
            Path entry = iterator.next();
            if (entries.size() >= entryLimit) {
                truncated = true;
                break;
            }
            entries.add(toDirectoryEntry(entry));
        }
        return truncated;
    }

    private DirectoryEntry toDirectoryEntry(Path entry) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Long size = attributes.isRegularFile() ? attributes.size() : null;
        return new DirectoryEntry(entry.getFileName().toString(), relativePath(entry), attributes.isDirectory(), size,
                attributes.lastModifiedTime().toString());
    }

    private Callable<OutputCapture> readOutput(InputStream inputStream, int maxBytes) {
        return () -> {
            try (InputStream stream = inputStream; ByteArrayOutputStream retained = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
                byte[] buffer = new byte[8192];
                long totalBytes = 0;
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (totalBytes < maxBytes) {
                        int retainBytes = (int) Math.min(read, maxBytes - totalBytes);
                        retained.write(buffer, 0, retainBytes);
                    }
                    totalBytes += read;
                }
                return new OutputCapture(retained.toString(StandardCharsets.UTF_8), totalBytes > maxBytes);
            }
        };
    }

    private List<String> validateCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command cannot be empty");
        }
        List<String> commandLine = new ArrayList<>(command.size());
        for (int i = 0; i < command.size(); i++) {
            String part = command.get(i);
            if (part == null) {
                throw new IllegalArgumentException("command part cannot be null");
            }
            if (i == 0 && part.isBlank()) {
                throw new IllegalArgumentException("command name cannot be blank");
            }
            commandLine.add(part);
        }
        return List.copyOf(commandLine);
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Charset charsetOrDefault(String encoding) {
        return Charset.forName(hasText(encoding) ? encoding : DEFAULT_ENCODING);
    }

    private int boundedInt(Integer value, int defaultValue, int maxValue, String name) {
        int resolved = value == null ? defaultValue : value;
        if (resolved < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return Math.min(resolved, maxValue);
    }

    private long boundedLong(Long value, long defaultValue, long maxValue, String name) {
        long resolved = value == null ? defaultValue : value;
        if (resolved < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return Math.min(resolved, maxValue);
    }

    private String relativePath(Path path) {
        Path relative = workspaceRoot.relativize(path.toAbsolutePath().normalize());
        String value = relative.toString().replace('\\', '/');
        return value.isBlank() ? "." : value;
    }

    private record OutputCapture(String content, boolean truncated) {
    }

    public record ReadFileResult(String path, String content, String encoding, int bytesRead, boolean truncated) {
    }

    public record WriteFileResult(String path, int bytesWritten, boolean created, String encoding) {
    }

    public record ListDirectoryResult(String path, List<DirectoryEntry> entries, boolean truncated) {
    }

    public record DirectoryEntry(String name, String path, boolean directory, Long size, String lastModified) {
    }

    public record CommandResult(List<String> command, String workingDirectory, int exitCode, String stdout, String stderr,
                                boolean timedOut, long durationMillis, boolean stdoutTruncated,
                                boolean stderrTruncated) {
    }
}