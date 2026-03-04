package com.farm.sales.service;

import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
  private static final int REPORT_ROW_WINDOW = 200;
  private static final int MAX_REPORT_ROWS = 20_000;
  private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
      .withLocale(Locale.US)
      .withZone(ZoneOffset.UTC);
  private final OrderRepository orderRepository;

  public ReportService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional(readOnly = true)
  public byte[] buildOrdersReport(Instant from, Instant to, String statusValue) {
    OrderStatus status = parseStatus(statusValue);

    List<OrderRepository.ReportRow> orders = orderRepository.findReportRows(from, to, status, PageRequest.of(0, MAX_REPORT_ROWS + 1));
    if (orders.size() > MAX_REPORT_ROWS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Слишком большой отчёт. Сузьте период или фильтр статуса"
      );
    }

    try (SXSSFWorkbook workbook = new SXSSFWorkbook(REPORT_ROW_WINDOW);
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = workbook.createSheet("Заказы");
      createHeader(sheet);
      setColumnWidths(sheet);

      int rowIndex = 1;
      for (OrderRepository.ReportRow order : orders) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(order.getOrderId() == null ? 0 : order.getOrderId());
        row.createCell(1).setCellValue(order.getStoreName() == null ? "-" : order.getStoreName());
        row.createCell(2).setCellValue(order.getStatus() == null ? "-" : order.getStatus().name());
        row.createCell(3).setCellValue(order.getCreatedAt() == null ? "-" : REPORT_DATE_FORMATTER.format(order.getCreatedAt()));
        row.createCell(4).setCellValue(order.getTotalAmount() == null ? 0d : order.getTotalAmount().doubleValue());
        row.createCell(5).setCellValue(order.getItemCount() == null ? 0 : order.getItemCount());
        row.createCell(6).setCellValue(order.getDeliveryAddressText() == null ? "-" : order.getDeliveryAddressText());
        row.createCell(7).setCellValue(order.getDriverName() == null ? "-" : order.getDriverName());
      }

      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сформировать отчёт");
    }
  }

  private void createHeader(Sheet sheet) {
    Row header = sheet.createRow(0);
    Cell cell0 = header.createCell(0);
    cell0.setCellValue("ID заказа");
    header.createCell(1).setCellValue("Магазин");
    header.createCell(2).setCellValue("Статус");
    header.createCell(3).setCellValue("Создан");
    header.createCell(4).setCellValue("Сумма");
    header.createCell(5).setCellValue("Количество позиций");
    header.createCell(6).setCellValue("Адрес доставки");
    header.createCell(7).setCellValue("Водитель");
  }

  private void setColumnWidths(Sheet sheet) {
    sheet.setColumnWidth(0, 12 * 256);
    sheet.setColumnWidth(1, 30 * 256);
    sheet.setColumnWidth(2, 14 * 256);
    sheet.setColumnWidth(3, 20 * 256);
    sheet.setColumnWidth(4, 14 * 256);
    sheet.setColumnWidth(5, 20 * 256);
    sheet.setColumnWidth(6, 48 * 256);
    sheet.setColumnWidth(7, 24 * 256);
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
