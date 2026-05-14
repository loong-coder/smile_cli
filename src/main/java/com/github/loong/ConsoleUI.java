package com.github.loong;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public class ConsoleUI implements AutoCloseable {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private final Terminal terminal;
    private final LineReader reader;
    private final ConfigManager config;

    public ConsoleUI(ConfigManager config) throws IOException {
        this.config = config;
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build();
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    public void showWelcome() {
        String banner =
                ANSI_CYAN + ANSI_BOLD +
                "  ╔════════════════════════════════════════════╗\n" +
                "  ║                                            ║\n" +
                "  ║         " + ANSI_YELLOW + "S M I L E   C L I" + ANSI_CYAN + "                  ║\n" +
                "  ║                                            ║\n" +
                "  ╠════════════════════════════════════════════╣\n" +
                "  ║  Model: " + ANSI_RESET + ANSI_GREEN + padRight(config.getDisplayVersion(), 28) + ANSI_CYAN + "       ║\n" +
                "  ║  Type /help for commands                   ║\n" +
                "  ╚════════════════════════════════════════════╝\n" +
                ANSI_RESET;
        terminal.writer().println(banner);
        terminal.writer().flush();
    }

    public String readInput(String prompt) {
        try {
            String line = reader.readLine(ANSI_GREEN + prompt + ANSI_RESET);
            return line;
        } catch (EndOfFileException e) {
            return null; // Ctrl+D
        } catch (UserInterruptException e) {
            return ""; // Ctrl+C on empty line
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

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static String padRight(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n - 1) + "…";
        }
        if (s.length() == n) return s;
        return s + " ".repeat(n - s.length());
    }
}
