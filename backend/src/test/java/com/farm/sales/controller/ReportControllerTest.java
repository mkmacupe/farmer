package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.farm.sales.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ReportControllerTest {
  private ReportService reportService;
  private ReportController reportController;

  @BeforeEach
  void setUp() {
    reportService = org.mockito.Mockito.mock(ReportService.class);
    reportController = new ReportController(reportService);
  }

  @Test
  void ordersReportReturnsBadRequestWhenFromAfterTo() {
    LocalDate from = LocalDate.of(2026, 2, 1);
    LocalDate to = LocalDate.of(2026, 1, 31);

    assertThatThrownBy(() -> reportController.ordersReport(from, to, "DELIVERED"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });

    verifyNoInteractions(reportService);
  }

  @Test
  void ordersReportReturnsWorkbookPayload() {
    byte[] expected = new byte[] {1, 2, 3, 4};
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 1, 31);
    when(reportService.buildOrdersReport(any(Instant.class), any(Instant.class), eq("DELIVERED")))
        .thenReturn(expected);

    ResponseEntity<byte[]> response = reportController.ordersReport(from, to, "DELIVERED");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .isEqualTo("attachment; filename=otchet-zakazy.xlsx");
    assertThat(response.getBody()).containsExactly(expected);

    ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(reportService).buildOrdersReport(fromCaptor.capture(), toCaptor.capture(), eq("DELIVERED"));
    assertThat(fromCaptor.getValue()).isNotNull();
    assertThat(toCaptor.getValue()).isNotNull();
    assertThat(toCaptor.getValue()).isAfterOrEqualTo(fromCaptor.getValue());
  }

  @Test
  void ordersReportSupportsOpenDateRange() {
    byte[] expected = new byte[] {9, 8};
    when(reportService.buildOrdersReport(null, null, null)).thenReturn(expected);

    ResponseEntity<byte[]> response = reportController.ordersReport(null, null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactly(expected);
    assertThat(response.getHeaders().getContentType())
        .hasToString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    verify(reportService).buildOrdersReport(null, null, null);
  }

  @Test
  void ordersReportSupportsOnlyUpperBoundDate() {
    byte[] expected = new byte[] {5};
    LocalDate to = LocalDate.of(2026, 2, 28);
    when(reportService.buildOrdersReport(null, Instant.parse("2026-02-28T23:59:59.999Z"), "APPROVED"))
        .thenReturn(expected);

    ResponseEntity<byte[]> response = reportController.ordersReport(null, to, "APPROVED");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactly(expected);
    verify(reportService).buildOrdersReport(null, Instant.parse("2026-02-28T23:59:59.999Z"), "APPROVED");
  }

  @Test
  void ordersReportSupportsOnlyLowerBoundDate() {
    byte[] expected = new byte[] {7};
    LocalDate from = LocalDate.of(2026, 3, 1);
    when(reportService.buildOrdersReport(Instant.parse("2026-03-01T00:00:00Z"), null, null))
        .thenReturn(expected);

    ResponseEntity<byte[]> response = reportController.ordersReport(from, null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactly(expected);
    verify(reportService).buildOrdersReport(Instant.parse("2026-03-01T00:00:00Z"), null, null);
  }
}
