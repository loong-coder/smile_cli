package com.github.loong.memory;

import junit.framework.TestCase;

import java.nio.file.Path;

/**
 * 验证工作区 ID 稳定且适合放入 Qdrant collection 名称。
 */
public class WorkspaceIdTest extends TestCase {

    public void testWorkspaceIdIsStableForSamePath() {
        String first = WorkspaceId.fromPath(Path.of("D:/code/java/smile_cli"));
        String second = WorkspaceId.fromPath(Path.of("D:/code/java/smile_cli"));

        assertEquals(first, second);
    }

    public void testWorkspaceIdUsesSafeCharacters() {
        String id = WorkspaceId.fromPath(Path.of("D:/code/java/smile_cli"));

        assertTrue(id.matches("[a-f0-9]{16}"));
    }

    public void testCollectionNameCombinesPrefixAndWorkspace() {
        String id = "abcdef1234567890";

        assertEquals("smile_cli_memory_abcdef1234567890", WorkspaceId.collectionName("smile_cli_memory", id));
    }
}
