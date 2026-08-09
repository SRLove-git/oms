package com.oms.common.web.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出工具：UTF-8 with BOM，Excel 可直接打开；字段自动转义。
 */
public final class CsvExportUtil {

    private CsvExportUtil() {
    }

    public static void write(
            HttpServletResponse response,
            String fileName,
            List<String> headers,
            List<List<Object>> rows)
            throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + fileName + ".csv\"");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        writer.write('\ufeff');
        writeLine(writer, headers);
        for (List<Object> row : rows) {
            writeLine(writer, row.stream().map(String::valueOf).toList());
        }
        writer.flush();
    }

    private static void writeLine(Writer writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            String cell = cells.get(i);
            if (cell != null && (cell.contains(",") || cell.contains("\"") || cell.contains("\n"))) {
                writer.write('"');
                writer.write(cell.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(cell == null ? "" : cell);
            }
        }
        writer.write("\r\n");
    }
}
