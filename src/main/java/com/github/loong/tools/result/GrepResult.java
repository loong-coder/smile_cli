package com.github.loong.tools.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "GrepResult", description = "Grep 文本搜索的单条匹配结果（包含上下文预览）")
public record GrepResult(
    @Schema(description = "匹配到的文件绝对路径")
    String path,
    
    @Schema(description = "核心匹配行的行号（从 1 开始）")
    int lineNumber,
    
    @Schema(description = "核心匹配行的文本内容")
    String content,
    
    @Schema(description = "匹配行附近的上下文代码片段（包含前后若干行，用于协助 AI 理解语境）")
    List<String> contextLines
) {}