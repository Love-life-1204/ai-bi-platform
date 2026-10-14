package com.aibi.platform.ingestion.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.ingestion.dto.IngestionDtos.ColumnPreview;
import com.aibi.platform.ingestion.dto.IngestionDtos.ErrorRow;
import com.aibi.platform.ingestion.dto.IngestionDtos.FilePreviewData;
import com.aibi.platform.ingestion.entity.FileEntity;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileImportManager {

    private static final int MAX_ROWS = 1_000_000;
    private static final int MAX_COLUMNS = 512;
    private static final int MAX_CELL_CHARS = 32_767;
    private static final List<String> DATE_PATTERNS = List.of(
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss");
    private final IngestionProperties properties;

    public FileImportManager(IngestionProperties properties) {
        this.properties = properties;
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(50L * 1024 * 1024);
    }

    public StoredUpload store(long tenantId, long fileId, MultipartFile upload) {
        validateMetadata(upload);
        Path tenantDirectory = properties.getStorageDir().resolve(Long.toString(tenantId)).normalize();
        Path storageRoot = properties.getStorageDir().toAbsolutePath().normalize();
        Path destination = tenantDirectory.resolve(fileId + extension(upload.getOriginalFilename())).normalize();
        if (!destination.toAbsolutePath().startsWith(storageRoot)) {
            throw new BadRequestException("上传路径不合法");
        }
        try {
            Files.createDirectories(tenantDirectory);
            try (InputStream input = upload.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            validateContent(destination, upload.getOriginalFilename());
            return new StoredUpload(destination, sha256(destination), Files.size(destination),
                    normalizedContentType(upload.getOriginalFilename()));
        } catch (IOException exception) {
            deleteQuietly(destination);
            throw new BadRequestException("文件上传或解析失败");
        } catch (RuntimeException exception) {
            deleteQuietly(destination);
            throw exception;
        }
    }

    /** Only removes this operation's file under the configured tenant storage directory. */
    public void discardStoredUpload(Path path, long tenantId, long fileId) {
        Path root = properties.getStorageDir().toAbsolutePath().normalize();
        Path checked = path.toAbsolutePath().normalize();
        Path tenant = root.resolve(Long.toString(tenantId));
        if (!checked.getParent().equals(tenant)
                || !checked.getFileName().toString().matches(fileId + "\\.(csv|xlsx)")) {
            throw new BadRequestException("上传路径不合法");
        }
        deleteQuietly(checked);
    }

    public ParsedTable parse(FileEntity file) {
        String extension = extension(file.fileName());
        if (".csv".equals(extension)) {
            return parseCsv(file.storagePath());
        }
        return parseXlsx(file.storagePath());
    }

    public FilePreviewData preview(FileEntity file) {
        ParsedTable table = parse(file);
        int limit = Math.min(properties.getPreviewRows(), table.rows().size());
        List<Map<String, String>> samples = table.rows().subList(0, limit);
        List<ColumnPreview> columns = new ArrayList<>();
        for (String header : table.headers()) {
            List<String> values = samples.stream().map(row -> row.getOrDefault(header, ""))
                    .filter(value -> !value.isBlank()).limit(5).toList();
            columns.add(new ColumnPreview(header, inferType(values), hasBlank(samples, header),
                    matchingDatePatterns(values), values));
        }
        return new FilePreviewData(file.id(), table.sheetName(), limit, columns,
                List.copyOf(samples), table.rows().size() > limit);
    }

    public StoredUpload writeErrorReport(long tenantId, long fileId, List<ErrorRow> errors) {
        Path tenantDirectory = properties.getStorageDir().resolve(Long.toString(tenantId)).normalize();
        Path destination = tenantDirectory.resolve(fileId + "-errors.csv").normalize();
        try {
            Files.createDirectories(tenantDirectory);
            List<String> lines = new ArrayList<>();
            lines.add("row_number,errors,row_data");
            for (ErrorRow error : errors) {
                lines.add(error.rowNumber() + "," + csvSafe(String.join("; ", error.errors()))
                        + "," + csvSafe(error.values().toString()));
            }
            Files.write(destination, lines, StandardCharsets.UTF_8);
            return new StoredUpload(destination, sha256(destination), Files.size(destination), "text/csv");
        } catch (IOException exception) {
            throw new IllegalStateException("异常行报告写入失败", exception);
        }
    }

    private void validateMetadata(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BadRequestException("上传文件不能为空");
        }
        String name = upload.getOriginalFilename();
        String extension = extension(name);
        if (!Set.of(".csv", ".xlsx").contains(extension)) {
            throw new BadRequestException("仅支持 CSV 和 XLSX 文件");
        }
        if (upload.getSize() > properties.getMaxFileBytes()) {
            throw new BadRequestException("文件大小不能超过 100MB");
        }
    }

    private void validateContent(Path path, String name) throws IOException {
        if (".csv".equals(extension(name))) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(path),
                    StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    for (int index = 0; index < read; index++) {
                        if (buffer[index] == '\0') {
                            throw new BadRequestException("CSV 包含非法二进制内容");
                        }
                    }
                }
            } catch (CharacterCodingException exception) {
                throw new BadRequestException("CSV 必须使用 UTF-8 编码");
            }
            parseCsv(path);
            return;
        }
        byte[] signature;
        try (InputStream input = Files.newInputStream(path)) {
            signature = input.readNBytes(4);
        }
        if (signature.length < 4 || signature[0] != 'P' || signature[1] != 'K') {
            throw new BadRequestException("XLSX 文件签名不正确");
        }
        inspectZip(path);
        parseXlsx(path);
    }

    private void inspectZip(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            long expanded = 0;
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                if (lower.contains("vbaproject") || lower.contains("/embeddings/")
                        || lower.contains("externallinks")) {
                    throw new BadRequestException("XLSX 包含宏、嵌入对象或外部链接");
                }
                if (entry.getSize() > 0) {
                    expanded += entry.getSize();
                }
                if (expanded > properties.getMaxFileBytes() * 4) {
                    throw new BadRequestException("XLSX 解压后内容过大");
                }
            }
        }
    }

    private ParsedTable parseCsv(Path path) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).get();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                CSVParser parser = format.parse(reader)) {
            List<String> headers = validateHeaders(new ArrayList<>(parser.getHeaderNames()));
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) {
                    throw new BadRequestException("文件行数不能超过 100 万行");
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), safeCell(index < record.size() ? record.get(index) : ""));
                }
                rows.add(Map.copyOf(row));
            }
            return new ParsedTable("CSV", headers, rows);
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof BadRequestException badRequest) {
                throw badRequest;
            }
            throw new BadRequestException("CSV 结构无法安全解析");
        }
    }

    private ParsedTable parseXlsx(Path path) {
        try (InputStream input = Files.newInputStream(path);
                Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("XLSX 不包含工作表");
            }
            rejectFormulaCells(workbook);
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw new BadRequestException("XLSX 缺少表头");
            }
            List<String> headers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                headers.add(safeCell(formatter.formatCellValue(headerRow.getCell(index))));
            }
            headers = validateHeaders(headers);
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (rows.size() >= MAX_ROWS) {
                    throw new BadRequestException("文件行数不能超过 100 万行");
                }
                Row source = sheet.getRow(rowIndex);
                if (source == null) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                boolean nonEmpty = false;
                for (int index = 0; index < headers.size(); index++) {
                    Cell cell = source.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = safeCell(cell == null ? "" : formatter.formatCellValue(cell));
                    nonEmpty |= !value.isBlank();
                    row.put(headers.get(index), value);
                }
                if (nonEmpty) {
                    rows.add(Map.copyOf(row));
                }
            }
            return new ParsedTable(sheet.getSheetName(), headers, rows);
        } catch (IOException exception) {
            throw new BadRequestException("XLSX 结构无法安全解析");
        } catch (RuntimeException exception) {
            if (exception instanceof BadRequestException badRequest) {
                throw badRequest;
            }
            throw new BadRequestException("XLSX 结构无法安全解析");
        }
    }

    private void rejectFormulaCells(Workbook workbook) {
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.FORMULA) {
                        throw new BadRequestException("XLSX 不允许公式单元格");
                    }
                }
            }
        }
    }

    private List<String> validateHeaders(List<String> headers) {
        if (headers.isEmpty() || headers.size() > MAX_COLUMNS) {
            throw new BadRequestException("字段数必须在 1 到 512 之间");
        }
        Set<String> unique = new HashSet<>();
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                throw new BadRequestException("字段名不能为空");
            }
            if (!unique.add(header)) {
                throw new BadRequestException("字段名不能重复: " + header);
            }
        }
        return List.copyOf(headers);
    }

    private String inferType(List<String> values) {
        if (values.isEmpty()) {
            return "STRING";
        }
        if (values.stream().allMatch(this::isLong)) {
            return "LONG";
        }
        if (values.stream().allMatch(this::isDecimal)) {
            return "DECIMAL";
        }
        if (values.stream().allMatch(this::isBoolean)) {
            return "BOOLEAN";
        }
        if (!matchingDatePatterns(values).isEmpty()) {
            return values.stream().anyMatch(value -> value.contains(":")) ? "DATETIME" : "DATE";
        }
        return "STRING";
    }

    private List<String> matchingDatePatterns(List<String> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        return DATE_PATTERNS.stream().filter(pattern -> values.stream().allMatch(value ->
                parseDate(value, pattern))).toList();
    }

    private boolean parseDate(String value, String pattern) {
        try {
            if (pattern.contains("H")) {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } else {
                LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            }
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean hasBlank(List<Map<String, String>> rows, String field) {
        return rows.stream().anyMatch(row -> row.getOrDefault(field, "").isBlank());
    }

    private boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isDecimal(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isBoolean(String value) {
        return Set.of("true", "false", "0", "1", "是", "否").contains(value.toLowerCase(Locale.ROOT));
    }

    private String safeCell(String value) {
        if (value != null && value.length() > MAX_CELL_CHARS) {
            throw new BadRequestException("单元格内容超过 32767 字符");
        }
        return value == null ? "" : value;
    }

    private String csvSafe(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        String lower = Path.of(name).getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot < 0 ? "" : lower.substring(dot);
    }

    private String normalizedContentType(String name) {
        return ".csv".equals(extension(name)) ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 隔离目录清理由运维任务兜底。
        }
    }

    public record StoredUpload(Path path, String sha256, long sizeBytes, String contentType) {
    }

    public record ParsedTable(String sheetName, List<String> headers,
            List<Map<String, String>> rows) {
        public ParsedTable {
            headers = List.copyOf(headers);
            rows = List.copyOf(rows);
        }
    }
}
