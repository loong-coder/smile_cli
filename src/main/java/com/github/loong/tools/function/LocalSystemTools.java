package com.github.loong.tools.function;

import com.github.loong.tools.result.GrepResult;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 提供系统级文件操作和命令执行工具，支持绝对路径与跨目录操作。
 */
public class LocalSystemTools {

    private static final int DEFAULT_MAX_BYTES = 64 * 1024;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int MAX_ENTRIES = 1000;
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    private static final long MAX_TIMEOUT_MILLIS = 120_000L;
    private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();

    // 默认基准目录，当输入相对路径时，基于此目录解析（通常为用户工作目录）
    private final Path baseDir;

    public LocalSystemTools() {
        this.baseDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public LocalSystemTools(Path baseDir) throws IOException {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir cannot be null");
        }
        this.baseDir = baseDir.toAbsolutePath().normalize().toRealPath();
    }

    @Schema(name = "read_file", title = "Read File", description = "读取系统中的文本文件内容，支持绝对路径或相对路径")
    public ReadFileResult readFile(
            @Schema(name = "path", description = "文件路径（可以是绝对路径，或相对于基准目录的相对路径）", requiredMode = Schema.RequiredMode.REQUIRED) String path,
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

        return new ReadFileResult(toStandardPathString(file), new String(bytes, charset), charset.name(), bytes.length, truncated);
    }

    @Schema(name = "write_file", title = "Write File", description = "写入文本文件内容到指定路径，支持跨目录写入")
    public WriteFileResult writeFile(
            @Schema(name = "path", description = "目标文件路径（支持绝对路径或相对路径）", requiredMode = Schema.RequiredMode.REQUIRED) String path,
            @Schema(name = "content", description = "要写入的文本内容", requiredMode = Schema.RequiredMode.REQUIRED) String content,
            @Schema(name = "encoding", description = "字符编码，默认 UTF-8") String encoding,
            @Schema(name = "createParents", description = "父级目录不存在时是否自动创建，默认 false") Boolean createParents) throws IOException {
        Objects.requireNonNull(content, "content cannot be null");
        Path requested = normalizePath(requireText(path, "path"));
        boolean created = !Files.exists(requested, LinkOption.NOFOLLOW_LINKS);
        Path file = resolveWritableFile(requested, Boolean.TRUE.equals(createParents));
        if (Files.isDirectory(file)) {
            throw new IOException("path is a directory: " + path);
        }

        Charset charset = charsetOrDefault(encoding);
        byte[] bytes = content.getBytes(charset);
        OpenOption[] options = new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        Files.write(file, bytes, options);
        return new WriteFileResult(toStandardPathString(file), bytes.length, created, charset.name());
    }

    @Schema(name = "list_directory", title = "List Directory", description = "列出指定目录下的文件和子目录")
    public ListDirectoryResult listDirectory(
            @Schema(name = "path", description = "目录路径（绝对路径或相对路径），留空则默认列出当前基准目录") String path,
            @Schema(name = "recursive", description = "是否递归列出所有子目录深度的条目，默认 false") Boolean recursive,
            @Schema(name = "maxEntries", description = "最大返回条目数，默认 200，上限 1000") Integer maxEntries) throws IOException {
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

        return new ListDirectoryResult(toStandardPathString(directory), entries, truncated);
    }

    @Schema(name = "grep_search", title = "Grep Search", description = "在指定目录的文件中搜索匹配的文本行（grep 功能），返回匹配行及上下文")
    public List<GrepResult> grepSearch(
            @Schema(name = "path", description = "搜索的根目录路径") String path,
            @Schema(name = "pattern", description = "要搜索的文本或正则表达式", requiredMode = Schema.RequiredMode.REQUIRED) String pattern,
            @Schema(name = "isRegex", description = "是否启用正则表达式匹配，默认 false") Boolean isRegex) throws IOException {

        String searchPattern = requireText(pattern, "pattern");
        Path startDir = resolveExistingPath(hasText(path) ? path : ".");
        // 上下文行数：匹配行前后各取几行，帮助 AI 理解语境
        int contextCount = 2;

        // 提前预编译正则表达式，避免在并行流中重复编译
        Pattern compiledRegex = null;
        if (Boolean.TRUE.equals(isRegex)) {
            try {
                compiledRegex = Pattern.compile(searchPattern, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("无效的正则表达式: " + searchPattern, e);
            }
        }
        final Pattern regex = compiledRegex;

        List<GrepResult> results = new ArrayList<>();
        // 使用并行流（Parallel Stream）利用多核 CPU 加速文件扫描
        try (Stream<Path> stream = Files.walk(startDir)) {
            stream.parallel()
                    .filter(Files::isRegularFile)
                    .filter(this::isTextFile)
                    .forEach(file -> {
                        try {
                            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                            for (int i = 0; i < lines.size(); i++) {
                                String line = lines.get(i);
                                if (matches(line, searchPattern, regex)) {
                                    List<String> contextLines = extractContextLines(lines, i, contextCount);
                                    synchronized (results) {
                                        results.add(new GrepResult(
                                                toStandardPathString(file),
                                                i + 1,
                                                line.trim(),
                                                contextLines));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // 忽略个别文件读取失败，继续搜索其他文件
                        }
                    });
        }
        return results;
    }

    /**
     * 校验文件是否为文本文件。
     * 采用双重检查：1. 常见二进制文件后缀黑名单； 2. 读取前1024字节探测是否包含控制字符（NUL）。
     */
    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        // 1. 快筛：过滤掉常见的已知二进制文件后缀
        List<String> binaryExtensions = List.of(
                ".class", ".jar", ".war", ".zip", ".tar", ".gz", ".7z", ".rar",
                ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg",
                ".mp3", ".mp4", ".avi", ".mov", ".pdf", ".doc", ".docx", ".xls", ".xlsx"
        );
        if (binaryExtensions.stream().anyMatch(fileName::endsWith)) {
            return false;
        }

        // 2. 深检：读取文件前 1024 字节，检查是否包含二进制特征码（如 NUL 字节 '\0'）
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024];
            int read = is.read(buffer);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) { // 包含 NUL 字节，基本可以判定为二进制文件
                    return false;
                }
            }
        } catch (IOException e) {
            return false; // 读取失败的文件直接跳过
        }

        return true;
    }

    /**
     * 核心匹配逻辑：支持普通字符串忽略大小写包含，或预编译的正则表达式匹配。
     *
     * @param line           当前行文本
     * @param pattern        搜索关键词
     * @param compiledRegex  预编译的正则表达式，为 null 时退化为字符串包含匹配
     */
    private boolean matches(String line, String pattern, Pattern compiledRegex) {
        if (line == null || pattern == null) {
            return false;
        }
        if (compiledRegex != null) {
            return compiledRegex.matcher(line).find();
        }
        return line.toLowerCase().contains(pattern.toLowerCase());
    }

    /**
     * 提取匹配行附近的上下文代码片段（前后各 contextCount 行），用于协助 AI 理解匹配结果的语境。
     */
    private List<String> extractContextLines(List<String> allLines, int matchIndex, int contextCount) {
        int start = Math.max(0, matchIndex - contextCount);
        int end = Math.min(allLines.size(), matchIndex + contextCount + 1);
        List<String> contextLines = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            contextLines.add(allLines.get(i));
        }
        return contextLines;
    }

    @Schema(name = "execute_command", title = "Execute Command", description = "在系统的指定工作目录下执行任意非 shell 命令数组")
    public CommandResult executeCommand(
            @Schema(name = "command", description = "命令及其参数数组，例如 [\"mvn\", \"clean\", \"package\"] 或 [\"git\", \"status\"]", requiredMode = Schema.RequiredMode.REQUIRED) List<String> command,
            @Schema(name = "workingDirectory", description = "命令执行的物理工作目录（绝对路径或相对路径），默认使用基准目录") String workingDirectory,
            @Schema(name = "timeoutMillis", description = "超时时间毫秒，默认 30000，上限 120000") Long timeoutMillis,
            @Schema(name = "maxOutputBytes", description = "stdout/stderr 最大保留的标准输出字节数，默认 65536，上限 1048576") Integer maxOutputBytes) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        List<String> commandLine = validateCommand(command);
        Path directory = resolveExistingPath(hasText(workingDirectory) ? workingDirectory : ".");
        if (!Files.isDirectory(directory)) {
            throw new IOException("workingDirectory is not a directory: " + workingDirectory);
        }

        long timeout = boundedLong(timeoutMillis, DEFAULT_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS, "timeoutMillis");
        int outputLimit = boundedInt(maxOutputBytes, DEFAULT_MAX_BYTES, MAX_BYTES, "maxOutputBytes");
        Process process = new ProcessBuilder(commandLine).directory(directory.toFile()).start();

        long startedAt = System.nanoTime();
        try(ExecutorService outputReaders = Executors.newFixedThreadPool(2)){
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
            return new CommandResult(commandLine, toStandardPathString(directory), exitCode,
                    stdoutCapture.content(), stderrCapture.content(), timedOut, durationMillis,
                    stdoutCapture.truncated(), stderrCapture.truncated());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private Path resolveExistingPath(String path) throws IOException {
        Path normalized = normalizePath(path);
        if (!Files.exists(normalized)) {
            throw new NoSuchFileException(path);
        }
        return normalized.toRealPath();
    }

    private Path resolveWritableFile(Path requested, boolean createParents) throws IOException {
        if (Files.isSymbolicLink(requested) && !Files.exists(requested)) {
            throw new IOException("path is a broken symbolic link: " + requested);
        }
        if (Files.exists(requested)) {
            return requested.toRealPath();
        }

        Path parent = requested.getParent();
        if (parent == null) {
            parent = baseDir;
        }
        if (!Files.exists(parent)) {
            if (!createParents) {
                throw new NoSuchFileException(parent.toString());
            }
            Files.createDirectories(parent);
        }
        Path realParent = parent.toRealPath();
        return realParent.resolve(requested.getFileName()).normalize();
    }

    private Path normalizePath(String path) {
        Path rawPath = Path.of(path);
        return rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : baseDir.resolve(rawPath).normalize().toAbsolutePath();
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
        return new DirectoryEntry(entry.getFileName().toString(), toStandardPathString(entry), attributes.isDirectory(), size,
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

    /**
     * 将路径转换为标准的、跨平台一致的绝对路径字符串表现形式（统一使用斜杠 /）。
     */
    private String toStandardPathString(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
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