package com.github.loong.ui;

import com.github.loong.config.LlmConfig;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.Widget;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.WCWidth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TerminalManager implements AutoCloseable {

    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_CYAN = "\033[36m";
    private static final String ANSI_GREEN = "\033[32m";
    private static final String ANSI_RED = "\033[31m";
    private static final String ANSI_BOLD = "\033[1m";
    private static final String ANSI_YELLOW = "\033[33m";
    private static final Pattern ANSI_PATTERN = Pattern.compile("\033\\[[;\\d]*[ -/]*[@-~]");

    static final String MASCOT_READY = "Yite [ready]";
    private static final List<String> ROBOT_MASCOT = List.of(
            " ▄▄▄▄▄▄▄▄▄ ",
            "██ ▄   ▄ ██",
            "██  ▀▄▀  ██",
            "▀███████▀ "
    );

    private final Terminal terminal;
    private final LineReader reader;
    private final LlmConfig config;

    public TerminalManager(LlmConfig config) throws IOException {
        this.config = config;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new StringsCompleter(List.of("/exit", "/quit", "/help", "/tools", "/clear")))
                .option(LineReader.Option.AUTO_LIST, true)
                .option(LineReader.Option.AUTO_MENU, true)
                .option(LineReader.Option.MENU_COMPLETE, true)
                .build();

        Widget slashComplete = new Widget() {
            @Override
            public boolean apply() {
                if (reader.getBuffer().cursor() == 0) {
                    reader.getBuffer().write('/');
                    reader.callWidget(LineReader.COMPLETE_WORD);
                } else {
                    reader.getBuffer().write('/');
                }
                return true;
            }
        };
        reader.getKeyMaps().get(LineReader.EMACS).bind(slashComplete, "/");
    }

    public void showWelcome() {
        printDecoratedLines(renderWelcomeLines(config.getDisplayVersion(), workspacePath(), frameWidth()), ANSI_CYAN + ANSI_BOLD);
    }

    public void printSetupError() {
        printDecoratedLines(renderSetupErrorLines(frameWidth()), ANSI_YELLOW + ANSI_BOLD);
    }

    public String readInput(String prompt) {
        try {
            String line = reader.readLine(ANSI_GREEN + prompt + ANSI_RESET);
            return line;
        } catch (EndOfFileException e) {
            return null;
        } catch (UserInterruptException e) {
            return "";
        }
    }

    public void printToken(String token) {
        terminal.writer().print(token);
        terminal.writer().flush();
    }

    public void printInfo(String msg) {
        terminal.writer().println(ANSI_CYAN + msg + ANSI_RESET);
        terminal.writer().flush();
    }

    public void printError(String msg) {
        terminal.writer().println(ANSI_RED + ANSI_BOLD + "  Error: " + msg + ANSI_RESET);
        terminal.writer().flush();
    }

    public void printWarning(String msg) {
        terminal.writer().println(ANSI_YELLOW + msg + ANSI_RESET);
        terminal.writer().flush();
    }

    public void println() {
        terminal.writer().println();
        terminal.writer().flush();
    }

    public void clearScreen() {
        // 清屏后把光标移回左上角，兼容常见 ANSI 终端。
        terminal.writer().print("\033[H\033[2J");
        terminal.writer().flush();
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private void printDecoratedLines(List<String> lines, String style) {
        for (String line : lines) {
            terminal.writer().println(style + line + ANSI_RESET);
        }
        terminal.writer().flush();
    }

    private int frameWidth() {
        int terminalWidth = terminal.getWidth();
        if (terminalWidth <= 0) {
            return 128;
        }
        return Math.clamp(terminalWidth - 1, 72, 213);
    }

    private static String workspacePath() {
        return System.getProperty("user.dir");
    }

    static List<String> renderWelcomeLines(String displayVersion, int width) {
        return renderWelcomeLines(displayVersion, workspacePath(), width);
    }

    static List<String> renderWelcomeLines(String displayVersion, String workspace, int width) {
        int frameWidth = normalizeWidth(width);
        int leftWidth = leftPanelWidth(frameWidth);
        List<String> lines = new ArrayList<>();
        lines.add(titledTop("SMILE CLI", frameWidth));
        lines.add(panelLine("", "What's new", frameWidth));
        lines.add(panelLine(center("Welcome back!", leftWidth), "Claude-style boxed welcome layout", frameWidth));
        lines.add(panelLine(center(ROBOT_MASCOT.get(0), leftWidth), "Block mascot: flat smiling robot", frameWidth));
        lines.add(panelLine(center(ROBOT_MASCOT.get(1), leftWidth), "Setup errors render as guided cards", frameWidth));
        lines.add(panelLine(center(ROBOT_MASCOT.get(2), leftWidth), "/help for commands, /exit to leave", frameWidth));
        lines.add(panelLine(center(ROBOT_MASCOT.get(3), leftWidth), "Ready when you are.", frameWidth));
        lines.add(panelLine("  Model - " + displayVersion, "", frameWidth));
        lines.add(panelLine("  Workspace - " + workspace, "", frameWidth));
        lines.add(panelDivider(frameWidth));
        lines.add(panelLine("  Commands", "Tips", frameWidth));
        lines.add(panelLine("  /help   show commands", "Use /clear to clear console output", frameWidth));
        lines.add(bottom(frameWidth));
        return lines;
    }

    static List<String> renderSetupErrorLines(int width) {
        int frameWidth = normalizeWidth(width);
        List<String> lines = new ArrayList<>();
        lines.add(titledTop("Setup needed", frameWidth));
        lines.add(boxLine(MASCOT_READY + "  One more step before we start.", frameWidth));
        lines.add(boxLine("", frameWidth));
        lines.add(boxLine("Missing: DEEPSEEK_API_KEY", frameWidth));
        lines.add(boxLine("Git Bash/macOS/Linux: export DEEPSEEK_API_KEY=your-key", frameWidth));
        lines.add(boxLine("PowerShell: $env:DEEPSEEK_API_KEY=\"your-key\"", frameWidth));
        lines.add(boxLine("", frameWidth));
        lines.add(boxLine("Set the variable, then run smile_cli again.", frameWidth));
        lines.add(bottom(frameWidth));
        return lines;
    }

    static String padVisibleRight(String text, int width) {
        String value = truncateVisible(text, width);
        int visibleWidth = visibleWidth(value);
        if (visibleWidth >= width) {
            return value;
        }
        return value + " ".repeat(width - visibleWidth);
    }

    static int visibleWidth(String text) {
        String stripped = stripAnsi(text);
        int width = 0;
        for (int i = 0; i < stripped.length(); ) {
            int codePoint = stripped.codePointAt(i);
            width += codePointWidth(codePoint);
            i += Character.charCount(codePoint);
        }
        return width;
    }

    static String stripAnsi(String text) {
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    private static String titledTop(String title, int width) {
        int innerWidth = width - 2;
        String label = "─ " + title + " ";
        return "╭" + label + "─".repeat(innerWidth - visibleWidth(label)) + "╮";
    }

    private static String panelDivider(int width) {
        int leftWidth = leftPanelWidth(width);
        int rightWidth = rightPanelWidth(width);
        return "├" + "─".repeat(leftWidth) + "┼" + "─".repeat(rightWidth) + "┤";
    }

    private static String bottom(int width) {
        return "╰" + "─".repeat(width - 2) + "╯";
    }

    private static String panelLine(String left, String right, int width) {
        int leftWidth = leftPanelWidth(width);
        int rightWidth = rightPanelWidth(width);
        return "│" + padVisibleRight(left, leftWidth) + "│" + padVisibleRight(" " + right, rightWidth) + "│";
    }

    private static String boxLine(String text, int width) {
        return "│" + padVisibleRight("  " + text, width - 2) + "│";
    }

    private static String center(String text, int width) {
        int textWidth = visibleWidth(text);
        if (textWidth >= width) {
            return truncateVisible(text, width);
        }
        int leftPadding = (width - textWidth) / 2;
        return " ".repeat(leftPadding) + text;
    }

    private static String truncateVisible(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (visibleWidth(text) <= width) {
            return text;
        }
        if (width == 1) {
            return "…";
        }

        String stripped = stripAnsi(text);
        StringBuilder result = new StringBuilder();
        int used = 0;
        int target = width - 1;
        for (int i = 0; i < stripped.length(); ) {
            int codePoint = stripped.codePointAt(i);
            int charWidth = codePointWidth(codePoint);
            if (used + charWidth > target) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += charWidth;
            i += Character.charCount(codePoint);
        }
        result.append('…');
        return result.toString();
    }

    private static int normalizeWidth(int width) {
        return Math.max(72, width);
    }

    private static int leftPanelWidth(int width) {
        int contentWidth = width - 3;
        return Math.clamp(contentWidth * 2L / 5, 34, 64);
    }

    private static int rightPanelWidth(int width) {
        return width - 3 - leftPanelWidth(width);
    }

    private static int codePointWidth(int codePoint) {
        int width = WCWidth.wcwidth(codePoint);
        return Math.max(width, 0);
    }
}
