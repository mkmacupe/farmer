package com.farm.sales.service;

import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
  private static final Logger log = LoggerFactory.getLogger(ReportService.class);
  private static final int REPORT_ROW_WINDOW = 200;
  private static final int MAX_REPORT_ROWS = 20_000;
  private static final int TITLE_ROW_INDEX = 0;
  private static final int FILTER_ROW_INDEX = 1;
  private static final int HEADER_ROW_INDEX = 3;
  private static final int DATA_START_ROW_INDEX = 4;
  private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      .withLocale(Locale.US)
      .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter FILTER_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
      .withLocale(Locale.forLanguageTag("ru-RU"))
      .withZone(ZoneOffset.UTC);
  private final OrderRepository orderRepository;

  private record ReportStyles(
      CellStyle title,
      CellStyle meta,
      CellStyle header,
      CellStyle body,
      CellStyle bodyAlt,
      CellStyle date,
      CellStyle dateAlt,
      CellStyle currency,
      CellStyle currencyAlt,
      CellStyle number,
      CellStyle numberAlt
  ) {
  }

  public ReportService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional(readOnly = true)
  public byte[] buildOrdersReport(Instant from, Instant to, String statusValue) {
    OrderStatus status = parseStatus(statusValue);
    boolean applyFromFilter = from != null;
    boolean applyToFilter = to != null;
    boolean applyStatusFilter = status != null;

    List<OrderRepository.ReportRow> orders = orderRepository.findReportRows(
        from,
        to,
        status,
        applyFromFilter,
        applyToFilter,
        applyStatusFilter,
        PageRequest.of(0, MAX_REPORT_ROWS + 1)
    );
    if (orders.size() > MAX_REPORT_ROWS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Слишком большой отчёт. Сузьте период или фильтр статуса"
      );
    }
    BigDecimal totalAmount = orders.stream()
        .map(OrderRepository.ReportRow::getTotalAmount)
        .filter(value -> value != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    try (SXSSFWorkbook workbook = new SXSSFWorkbook(REPORT_ROW_WINDOW);
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Заказы");
      ReportStyles styles = createStyles(workbook);
      sheet.setDisplayGridlines(false);
      sheet.setZoom(120);
      createHeader(sheet, styles);
      createReportMeta(sheet, styles, from, to, status, orders.size(), totalAmount);
      setColumnWidths(sheet);
      sheet.createFreezePane(0, DATA_START_ROW_INDEX);
      sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW_INDEX, HEADER_ROW_INDEX, 0, 7));

      int rowIndex = DATA_START_ROW_INDEX;
      for (OrderRepository.ReportRow order : orders) {
        createOrderRow(sheet.createRow(rowIndex), rowIndex, order, styles);
        rowIndex++;
      }

      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (Exception ex) {
      log.error("Ошибка при формировании отчета", ex);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сформировать отчёт");
    }
  }

  private void createHeader(Sheet sheet, ReportStyles styles) {
    Row titleRow = sheet.createRow(TITLE_ROW_INDEX);
    titleRow.setHeightInPoints(24);
    Cell titleCell = titleRow.createCell(0);
    titleCell.setCellValue("Отчёт по заказам");
    titleCell.setCellStyle(styles.title());
    sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW_INDEX, TITLE_ROW_INDEX, 0, 7));

    Row spacerRow = sheet.createRow(2);
    spacerRow.setHeightInPoints(8);

    Row headerRow = sheet.createRow(HEADER_ROW_INDEX);
    createHeaderCell(headerRow, 0, "ID заказа", styles.header());
    createHeaderCell(headerRow, 1, "Магазин", styles.header());
    createHeaderCell(headerRow, 2, "Статус", styles.header());
    createHeaderCell(headerRow, 3, "Создан", styles.header());
    createHeaderCell(headerRow, 4, "Сумма", styles.header());
    createHeaderCell(headerRow, 5, "Количество позиций", styles.header());
    createHeaderCell(headerRow, 6, "Адрес доставки", styles.header());
    createHeaderCell(headerRow, 7, "Водитель", styles.header());
  }

  private void createHeaderCell(Row row, int index, String value, CellStyle style) {
    Cell cell = row.createCell(index);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private void createReportMeta(Sheet sheet,
                                ReportStyles styles,
                                Instant from,
                                Instant to,
                                OrderStatus status,
                                int rowsCount,
                                BigDecimal totalAmount) {
    Row metaRow = sheet.createRow(FILTER_ROW_INDEX);
    metaRow.setHeightInPoints(34);
    Cell metaCell = metaRow.createCell(0);
    metaCell.setCellValue(buildFilterSummary(from, to, status, rowsCount, totalAmount));
    metaCell.setCellStyle(styles.meta());
    sheet.addMergedRegion(new CellRangeAddress(FILTER_ROW_INDEX, FILTER_ROW_INDEX, 0, 7));
  }

  private String buildFilterSummary(Instant from,
                                    Instant to,
                                    OrderStatus status,
                                    int rowsCount,
                                    BigDecimal totalAmount) {
    String fromLabel = from == null ? "с начала" : FILTER_DATE_FORMATTER.format(from);
    String toLabel = to == null ? "сейчас" : FILTER_DATE_FORMATTER.format(to);
    String statusLabel = status == null ? "все статусы" : localizeStatus(status);
    String totalAmountLabel = formatCurrencyValue(totalAmount);
    String generatedAtLabel = REPORT_DATE_FORMATTER.format(Instant.now());
    return "Период: " + fromLabel + " - " + toLabel
        + " | Статус: " + statusLabel
        + " | Строк: " + rowsCount
        + " | Сумма: " + totalAmountLabel
        + " | Сформирован: " + generatedAtLabel + " UTC";
  }

  private void createOrderRow(Row row, int rowIndex, OrderRepository.ReportRow order, ReportStyles styles) {
    boolean alternate = (rowIndex - DATA_START_ROW_INDEX) % 2 != 0;
    CellStyle bodyStyle = alternate ? styles.bodyAlt() : styles.body();
    CellStyle dateStyle = alternate ? styles.dateAlt() : styles.date();
    CellStyle currencyStyle = alternate ? styles.currencyAlt() : styles.currency();
    CellStyle numberStyle = alternate ? styles.numberAlt() : styles.number();

    row.setHeightInPoints(22);

    Cell idCell = row.createCell(0);
    idCell.setCellValue(order.getOrderId() == null ? 0 : order.getOrderId());
    idCell.setCellStyle(numberStyle);

    Cell storeCell = row.createCell(1);
    storeCell.setCellValue(order.getStoreName() == null ? "-" : order.getStoreName());
    storeCell.setCellStyle(bodyStyle);

    Cell statusCell = row.createCell(2);
    statusCell.setCellValue(order.getStatus() == null ? "-" : localizeStatus(order.getStatus()));
    statusCell.setCellStyle(bodyStyle);

    Cell createdCell = row.createCell(3);
    createdCell.setCellValue(order.getCreatedAt() == null ? "-" : REPORT_DATE_FORMATTER.format(order.getCreatedAt()));
    createdCell.setCellStyle(dateStyle);

    Cell amountCell = row.createCell(4);
    amountCell.setCellValue(order.getTotalAmount() == null ? 0d : order.getTotalAmount().doubleValue());
    amountCell.setCellStyle(currencyStyle);

    Cell itemCountCell = row.createCell(5);
    itemCountCell.setCellValue(order.getItemCount() == null ? 0d : order.getItemCount().doubleValue());
    itemCountCell.setCellStyle(numberStyle);

    Cell addressCell = row.createCell(6);
    addressCell.setCellValue(order.getDeliveryAddressText() == null ? "-" : order.getDeliveryAddressText());
    addressCell.setCellStyle(bodyStyle);

    Cell driverCell = row.createCell(7);
    driverCell.setCellValue(order.getDriverName() == null ? "-" : order.getDriverName());
    driverCell.setCellStyle(bodyStyle);
  }

  private ReportStyles createStyles(SXSSFWorkbook workbook) {
    DataFormat dataFormat = workbook.createDataFormat();

    Font titleFont = workbook.createFont();
    titleFont.setBold(true);
    titleFont.setFontHeightInPoints((short) 18);
    titleFont.setColor(IndexedColors.WHITE.getIndex());

    Font metaFont = workbook.createFont();
    metaFont.setFontHeightInPoints((short) 10);
    metaFont.setBold(true);
    metaFont.setColor(IndexedColors.DARK_GREEN.getIndex());

    Font headerFont = workbook.createFont();
    headerFont.setBold(true);
    headerFont.setColor(IndexedColors.WHITE.getIndex());

    CellStyle titleStyle = workbook.createCellStyle();
    titleStyle.setFont(titleFont);
    titleStyle.setAlignment(HorizontalAlignment.LEFT);
    titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    titleStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
    titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

    CellStyle metaStyle = workbook.createCellStyle();
    metaStyle.setFont(metaFont);
    metaStyle.setWrapText(true);
    metaStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    metaStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
    metaStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

    CellStyle headerStyle = workbook.createCellStyle();
    headerStyle.setFont(headerFont);
    headerStyle.setAlignment(HorizontalAlignment.CENTER);
    headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    applyBorders(headerStyle);

    CellStyle bodyStyle = workbook.createCellStyle();
    bodyStyle.setWrapText(true);
    bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
    applyBorders(bodyStyle);

    CellStyle bodyAltStyle = workbook.createCellStyle();
    bodyAltStyle.cloneStyleFrom(bodyStyle);
    bodyAltStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
    bodyAltStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

    CellStyle dateStyle = workbook.createCellStyle();
    dateStyle.cloneStyleFrom(bodyStyle);
    dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));

    CellStyle dateAltStyle = workbook.createCellStyle();
    dateAltStyle.cloneStyleFrom(bodyAltStyle);
    dateAltStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));

    CellStyle currencyStyle = workbook.createCellStyle();
    currencyStyle.cloneStyleFrom(bodyStyle);
    currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
    currencyStyle.setDataFormat(dataFormat.getFormat("#,##0.00\" BYN\""));

    CellStyle currencyAltStyle = workbook.createCellStyle();
    currencyAltStyle.cloneStyleFrom(bodyAltStyle);
    currencyAltStyle.setAlignment(HorizontalAlignment.RIGHT);
    currencyAltStyle.setDataFormat(dataFormat.getFormat("#,##0.00\" BYN\""));

    CellStyle numberStyle = workbook.createCellStyle();
    numberStyle.cloneStyleFrom(bodyStyle);
    numberStyle.setAlignment(HorizontalAlignment.CENTER);
    numberStyle.setDataFormat(dataFormat.getFormat("0"));

    CellStyle numberAltStyle = workbook.createCellStyle();
    numberAltStyle.cloneStyleFrom(bodyAltStyle);
    numberAltStyle.setAlignment(HorizontalAlignment.CENTER);
    numberAltStyle.setDataFormat(dataFormat.getFormat("0"));

    return new ReportStyles(
        titleStyle,
        metaStyle,
        headerStyle,
        bodyStyle,
        bodyAltStyle,
        dateStyle,
        dateAltStyle,
        currencyStyle,
        currencyAltStyle,
        numberStyle,
        numberAltStyle
    );
  }

  private void applyBorders(CellStyle style) {
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
  }

  private void setColumnWidths(Sheet sheet) {
    sheet.setColumnWidth(0, 12 * 256);
    sheet.setColumnWidth(1, 32 * 256);
    sheet.setColumnWidth(2, 18 * 256);
    sheet.setColumnWidth(3, 20 * 256);
    sheet.setColumnWidth(4, 16 * 256);
    sheet.setColumnWidth(5, 18 * 256);
    sheet.setColumnWidth(6, 56 * 256);
    sheet.setColumnWidth(7, 24 * 256);
  }

  private String localizeStatus(OrderStatus status) {
    if (status == null) {
      return "-";
    }
    return switch (status) {
      case CREATED -> "Создан";
      case APPROVED -> "Одобрен";
      case ASSIGNED -> "Назначен";
      case DELIVERED -> "Доставлен";
    };
  }

  private String formatCurrencyValue(BigDecimal amount) {
    BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount;
    return normalizedAmount.stripTrailingZeros().scale() > 0
        ? normalizedAmount.toPlainString() + " BYN"
        : normalizedAmount.setScale(2).toPlainString() + " BYN";
  }

  private OrderStatus parseStatus(String statusValue) {
    if (statusValue == null || statusValue.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(statusValue.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный статус: " + statusValue);
    }
  }
}
