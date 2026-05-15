package com.github.loong.tools;

import com.github.loong.tools.function.LocalSystemTools;
import com.github.loong.tools.result.GrepResult;
import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 验证本地文件和命令工具被限制在工作区内执行。
 */
public class LocalSystemToolsTest extends TestCase {

    private Path workspace;
    private LocalSystemTools tools;

    @Override
    protected void setUp() throws Exception {
        workspace = Files.createTempDirectory("smile-cli-tools-").toRealPath();
        tools = new LocalSystemTools(workspace);
    }

    public void testReadFileReturnsContent() throws Exception {
        Files.writeString(workspace.resolve("hello.txt"), "你好，工具", StandardCharsets.UTF_8);

        LocalSystemTools.ReadFileResult result = tools.readFile("hello.txt", null, null);

        // toStandardPathString 返回绝对路径，验证以文件名结尾即可
        assertTrue(result.path().endsWith("/hello.txt"));
        assertEquals("你好，工具", result.content());
        assertEquals("UTF-8", result.encoding());
        assertFalse(result.truncated());
    }

    public void testReadFileTruncatesByMaxBytes() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "abcdef", StandardCharsets.UTF_8);

        LocalSystemTools.ReadFileResult result = tools.readFile("large.txt", 3, "UTF-8");

        assertEquals("abc", result.content());
        assertEquals(3, result.bytesRead());
        assertTrue(result.truncated());
    }

    public void testWriteFileCreatesParentsWhenRequested() throws Exception {
        LocalSystemTools.WriteFileResult result = tools.writeFile("nested/file.txt", "content", null, true);

        // toStandardPathString 返回绝对路径
        assertTrue(result.path().endsWith("/nested/file.txt"));
        assertEquals("content".getBytes(StandardCharsets.UTF_8).length, result.bytesWritten());
        assertTrue(result.created());
        assertEquals("content", Files.readString(workspace.resolve("nested/file.txt"), StandardCharsets.UTF_8));
    }

    public void testWriteFileWithoutParentCreationFails() throws Exception {
        try {
            tools.writeFile("missing/file.txt", "content", null, false);
            fail("missing parent should fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    public void testListDirectoryReturnsEntries() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "a", StandardCharsets.UTF_8);
        Files.createDirectory(workspace.resolve("dir"));

        LocalSystemTools.ListDirectoryResult result = tools.listDirectory(".", false, null);

        // toStandardPathString 返回绝对路径
        assertEquals(workspace.toString(), result.path());
        assertEquals(2, result.entries().size());
        assertTrue(containsEntry(result.entries(), "a.txt", false));
        assertTrue(containsEntry(result.entries(), "dir", true));
        assertFalse(result.truncated());
    }

    /**
     * 验证 grepSearch 能搜索文本内容并返回带上下文的匹配结果。
     */
    public void testGrepSearchFindsMatchingLinesWithContext() throws Exception {
        Files.writeString(workspace.resolve("search.txt"),
                "line1: hello\nline2: world\nline3: hello again\nline4: foo\nline5: hello world\n",
                StandardCharsets.UTF_8);

        List<GrepResult> results = tools.grepSearch(".", "hello", false);

        // 应匹配到 3 行包含 "hello" 的内容
        assertEquals(3, results.size());

        GrepResult first = results.get(0);
        assertEquals(1, first.lineNumber());
        assertTrue(first.content().contains("hello"));
        // 上下文：第1行前面没有行，后面应有第2、3行
        assertTrue(first.contextLines().size() <= 4);

        GrepResult last = results.get(2);
        assertEquals(5, last.lineNumber());
        assertTrue(last.content().contains("hello world"));
    }

    public void testExecuteCommandReturnsStdoutAndExitCode() throws Exception {
        LocalSystemTools.CommandResult result = tools.executeCommand(
                List.of("java", "-version"), ".", 10_000L, null);

        assertEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("version") || result.stdout().contains("version"));
        assertFalse(result.timedOut());
    }

    public void testExecuteCommandPreservesNonZeroExitCode() throws Exception {
        LocalSystemTools.CommandResult result = tools.executeCommand(
                List.of("java", "--bad-smile-cli-option"), ".", 10_000L, null);

        assertTrue(result.exitCode() != 0);
        assertFalse(result.timedOut());
    }

    public void testExecuteCommandTimesOut() throws Exception {
        LocalSystemTools.CommandResult result = tools.executeCommand(
                List.of("java", "-cp", System.getProperty("java.class.path"), SleepCommand.class.getName()), ".", 200L, null);

        assertTrue(result.timedOut());
    }

    public void testToolRegistryRegistersLocalTools() throws Exception {
        ToolRegistry registry = new ToolRegistry();

        registry.register(tools);

        List<String> names = registry.definitions().stream().map(ToolDefinition::name).toList();
        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("write_file"));
        assertTrue(names.contains("list_directory"));
        assertTrue(names.contains("grep_search"));
        assertTrue(names.contains("execute_command"));
    }

    private boolean containsEntry(List<LocalSystemTools.DirectoryEntry> entries, String name, boolean directory) {
        for (LocalSystemTools.DirectoryEntry entry : entries) {
            if (entry.name().equals(name) && entry.directory() == directory) {
                return true;
            }
        }
        return false;
    }

    public static class SleepCommand {
        public static void main(String[] args) throws Exception {
            Thread.sleep(5_000L);
        }
    }
}
