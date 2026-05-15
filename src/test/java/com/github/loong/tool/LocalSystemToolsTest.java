package com.github.loong.tool;

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

        assertEquals("hello.txt", result.path());
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

        assertEquals("nested/file.txt", result.path());
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

        assertEquals(".", result.path());
        assertEquals(2, result.entries().size());
        assertTrue(containsEntry(result.entries(), "a.txt", false));
        assertTrue(containsEntry(result.entries(), "dir", true));
        assertFalse(result.truncated());
    }

    public void testRejectsPathOutsideWorkspace() throws Exception {
        try {
            tools.readFile("../outside.txt", null, null);
            fail("outside path should fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("outside workspace"));
        }
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
