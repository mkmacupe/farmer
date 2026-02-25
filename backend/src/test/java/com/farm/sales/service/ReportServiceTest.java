package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReportServiceTest {
  private OrderRepository orderRepository;
  private ReportService service;

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    service = new ReportService(orderRepository);
  }

  @Test
  void buildOrdersReportParsesStatusAndBuildsWorkbookPayload() throws Exception {
    Instant createdAt = Instant.parse("2026-02-01T09:30:00Z");
    OrderRepository.ReportRow row = reportRow(
        1001L,
        "ОАО Могилевхимволокно",
        OrderStatus.DELIVERED,
        createdAt,
        new BigDecimal("120.50"),
        3L,
        "Могилёв, ул. Челюскинцев 105",
        "Водитель 1"
    );
    when(orderRepository.findReportRows(
        Instant.parse("2026-02-01T00:00:00Z"),
        Instant.parse("2026-02-10T00:00:00Z"),
        OrderStatus.DELIVERED
    )).thenReturn(List.of(row));

    byte[] payload = service.buildOrdersReport(
        Instant.parse("2026-02-01T00:00:00Z"),
        Instant.parse("2026-02-10T00:00:00Z"),
        " delivered "
    );

    assertThat(payload).isNotEmpty();
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(payload))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID заказа");
      assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1001d);
      assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("ОАО Могилевхимволокно");
      assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("DELIVERED");
      assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(120.50d);
      assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(3d);
      assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("Водитель 1");
    }
  }

  @Test
  void buildOrdersReportUsesNullStatusAndFallbackCellValues() throws Exception {
    OrderRepository.ReportRow row = reportRow(
        null,
        "Точка",
        OrderStatus.CREATED,
        Instant.parse("2026-02-11T10:00:00Z"),
        new BigDecimal("33.90"),
        null,
        null,
        null
    );
    when(orderRepository.findReportRows(null, null, null)).thenReturn(List.of(row));

    byte[] payload = service.buildOrdersReport(null, null, "   ");

    verify(orderRepository).findReportRows(null, null, null);
    try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(payload))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(0d);
      assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(0d);
      assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("-");
      assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("-");
    }
  }

  @Test
  void buildOrdersReportRejectsUnknownStatus() {
    assertThatThrownBy(() -> service.buildOrdersReport(null, null, "INVALID"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("Неизвестный статус");
        });
  }

  @Test
  void buildOrdersReportConvertsWorkbookIoErrorsToHttp500() throws Exception {
    when(orderRepository.findReportRows(null, null, null)).thenReturn(List.of());

    try (MockedConstruction<XSSFWorkbook> mocked = mockConstruction(XSSFWorkbook.class, (workbook, context) -> {
      XSSFSheet sheet = mock(XSSFSheet.class);
      XSSFRow row = mock(XSSFRow.class);
      XSSFCell cell = mock(XSSFCell.class);
      when(workbook.createSheet(anyString())).thenReturn(sheet);
      when(sheet.createRow(anyInt())).thenReturn(row);
      when(row.createCell(anyInt())).thenReturn(cell);
      doThrow(new IOException("boom")).when(workbook).write(any(ByteArrayOutputStream.class));
    })) {
      assertThatThrownBy(() -> service.buildOrdersReport(null, null, null))
          .isInstanceOf(ResponseStatusException.class)
          .satisfies(error -> {
            ResponseStatusException ex = (ResponseStatusException) error;
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(ex.getReason()).contains("Не удалось сформировать отчёт");
          });
    }
  }

  private OrderRepository.ReportRow reportRow(Long orderId,
                                              String storeName,
                                              OrderStatus status,
                                              Instant createdAt,
                                              BigDecimal totalAmount,
                                              Long itemCount,
                                              String deliveryAddress,
                                              String driverName) {
    OrderRepository.ReportRow row = mock(OrderRepository.ReportRow.class);
    when(row.getOrderId()).thenReturn(orderId);
    when(row.getStoreName()).thenReturn(storeName);
    when(row.getStatus()).thenReturn(status);
    when(row.getCreatedAt()).thenReturn(createdAt);
    when(row.getTotalAmount()).thenReturn(totalAmount);
    when(row.getItemCount()).thenReturn(itemCount);
    when(row.getDeliveryAddressText()).thenReturn(deliveryAddress);
    when(row.getDriverName()).thenReturn(driverName);
    return row;
  }
}
