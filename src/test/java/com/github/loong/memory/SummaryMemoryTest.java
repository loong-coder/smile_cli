package com.github.loong.memory;

import junit.framework.TestCase;

/**
 * 验证滚动摘要只接受有效内容并可清空。
 */
public class SummaryMemoryTest extends TestCase {

    public void testInitialSummaryIsEmpty() {
        SummaryMemory summaryMemory = new SummaryMemory();

        assertFalse(summaryMemory.hasSummary());
        assertEquals("", summaryMemory.content());
    }

    public void testUpdateStoresNonBlankSummary() {
        SummaryMemory summaryMemory = new SummaryMemory();

        summaryMemory.update("用户要求使用 Qdrant。");

        assertTrue(summaryMemory.hasSummary());
        assertEquals("用户要求使用 Qdrant。", summaryMemory.content());
    }

    public void testBlankSummaryDoesNotOverwriteExistingSummary() {
        SummaryMemory summaryMemory = new SummaryMemory();
        summaryMemory.update("已有摘要");

        summaryMemory.update("  ");

        assertEquals("已有摘要", summaryMemory.content());
    }

    public void testClearRemovesSummary() {
        SummaryMemory summaryMemory = new SummaryMemory();
        summaryMemory.update("已有摘要");

        summaryMemory.clear();

        assertFalse(summaryMemory.hasSummary());
    }
}
