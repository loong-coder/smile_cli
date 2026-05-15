package com.github.loong.ui;

import junit.framework.TestCase;

import java.util.List;

public class ConsoleUITest extends TestCase {

    public void testVisibleWidthIgnoresAnsi() {
        assertEquals(5, TerminalManager.visibleWidth("\033[31mhello\033[0m"));
    }

    public void testVisibleWidthCountsChineseAsWide() {
        assertEquals(9, TerminalManager.visibleWidth("伊特hello"));
    }

    public void testPadVisibleRightHandlesWideCharacters() {
        String padded = TerminalManager.padVisibleRight("伊特", 6);
        assertEquals(6, TerminalManager.visibleWidth(padded));
        assertTrue(padded.endsWith("  "));
    }

    public void testWelcomeIncludesMascotAndModel() {
        List<String> lines = TerminalManager.renderWelcomeLines("deepseek v4 pro", "smile_cli", 74);
        String output = String.join("\n", lines);
        assertTrue(output.contains("SMILE CLI"));
        assertTrue(output.contains("▀▄▀"));
        assertTrue(output.contains("deepseek v4 pro"));
        assertTrue(output.contains("Workspace - smile_cli"));
    }

    public void testSetupErrorIncludesApiKeyGuidance() {
        List<String> lines = TerminalManager.renderSetupErrorLines(74);
        String output = String.join("\n", lines);
        assertTrue(output.contains("Yite [ready]"));
        assertTrue(output.contains("DEEPSEEK_API_KEY"));
        assertTrue(output.contains("PowerShell"));
    }

    public void testWelcomeCommandsOnlyShowsHelp() {
        String output = String.join("\n", TerminalManager.renderWelcomeLines("deepseek v4 pro", "smile_cli", 100));
        assertTrue(output.contains("/help   show commands"));
        assertFalse(output.contains("/exit   exit"));
        assertFalse(output.contains("/quit   exit"));
    }

    public void testRenderedLinesKeepSameVisibleWidth() {
        assertLinesHaveWidth(TerminalManager.renderWelcomeLines("deepseek v4 pro", "D:/code/java/smile_cli", 74), 74);
        assertLinesHaveWidth(TerminalManager.renderWelcomeLines("deepseek v4 pro", "D:/code/java/smile_cli", 140), 140);
        assertLinesHaveWidth(TerminalManager.renderSetupErrorLines(74), 74);
    }

    private void assertLinesHaveWidth(List<String> lines, int width) {
        for (String line : lines) {
            assertEquals(line, width, TerminalManager.visibleWidth(line));
        }
    }
}
