package com.orabank.backend.export;

import com.orabank.backend.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExcelExporter {

    private static final int MAX_ROWS = 100_000;
    private static final int ROWS_IN_MEMORY = 500;
    private static final int CHARACTER_WIDTH = 256;

    private final Clock clock;

    public ExportedFile write(String subject, List<String> headers, List<List<Object>> rows) {
        if (rows.size() > MAX_ROWS) {
            throw ApiException.businessRule("EXPORT_TOO_LARGE",
                    "L'export dépasse %d lignes (%d trouvées) : affinez les filtres avant de réessayer."
                            .formatted(MAX_ROWS, rows.size()));
        }

        // classeur écrit en flux : seules les dernières lignes restent en mémoire ;
        // close() supprime aussi ses fichiers temporaires
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(subject);
            CellStyle dateTimeStyle = dateTimeStyle(workbook);
            CellStyle durationStyle = durationStyle(workbook);

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = headerStyle(workbook);
            for (int column = 0; column < headers.size(); column++) {
                Cell cell = headerRow.createCell(column);
                cell.setCellValue(headers.get(column));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, columnWidth(headers.get(column)));
            }

            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                List<Object> values = rows.get(index);
                for (int column = 0; column < values.size(); column++) {
                    fill(row.createCell(column), values.get(column), dateTimeStyle, durationStyle);
                }
            }

            sheet.createFreezePane(0, 1);
            if (!headers.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, headers.size() - 1));
            }

            workbook.write(output);
            return new ExportedFile("%s-%s.xlsx".formatted(subject, LocalDate.now(clock)), output.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("Generation du classeur impossible", exception);
        }
    }

    private void fill(Cell cell, Object value, CellStyle dateTimeStyle, CellStyle durationStyle) {
        switch (value) {
            case null -> cell.setBlank();
            case String text -> cell.setCellValue(text);
            case Number number -> cell.setCellValue(number.doubleValue());
            case Instant instant -> {
                cell.setCellValue(LocalDateTime.ofInstant(instant, clock.getZone()));
                cell.setCellStyle(dateTimeStyle);
            }
            case Duration duration -> {
                cell.setCellValue(duration.toMinutes() / (24.0 * 60));
                cell.setCellStyle(durationStyle);
            }
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private int columnWidth(String header) {
        return Math.clamp(header.length() + 6, 14, 40) * CHARACTER_WIDTH;
    }

    private CellStyle dateTimeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
        return style;
    }

    private CellStyle durationStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("[h]\" h \"mm\" min\""));
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
