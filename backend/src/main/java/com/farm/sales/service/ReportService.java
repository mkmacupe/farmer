package com.farm.sales.service;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportService {
  private final OrderRepository orderRepository;

  public ReportService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional(readOnly = true)
  public byte[] buildOrdersReport(Instant from, Instant to, String statusValue) {
    OrderStatus status = parseStatus(statusValue);

    List<Order> orders = orderRepository.findForReport(from, to, status);

    try (Workbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Заказы");
      createHeader(sheet);

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
          .withLocale(Locale.US)
          .withZone(ZoneOffset.UTC);

      int rowIndex = 1;
      for (Order order : orders) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(order.getId());
        row.createCell(1).setCellValue(order.getCustomer().getLegalEntityName() == null
            ? order.getCustomer().getFullName()
            : order.getCustomer().getLegalEntityName());
        row.createCell(2).setCellValue(order.getStatus().name());
        row.createCell(3).setCellValue(formatter.format(order.getCreatedAt()));
        row.createCell(4).setCellValue(order.getTotalAmount().doubleValue());
        row.createCell(5).setCellValue(order.getItems().size());
        row.createCell(6).setCellValue(order.getDeliveryAddressText() == null ? "-" : order.getDeliveryAddressText());
        row.createCell(7).setCellValue(order.getAssignedDriver() == null ? "-" : order.getAssignedDriver().getFullName());
      }

      for (int i = 0; i < 8; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException ex) {
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
