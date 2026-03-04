package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
  private ApiExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ApiExceptionHandler();
  }

  @Test
  void handleStatusExceptionUsesReasonOrDefault() {
    ResponseEntity<ErrorResponse> withReason = handler.handleStatusException(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден")
    );
    assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(withReason.getBody().message()).isEqualTo("Товар не найден");

    ResponseEntity<ErrorResponse> withoutReason = handler.handleStatusException(
        new ResponseStatusException(HttpStatus.BAD_REQUEST)
    );
    assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(withoutReason.getBody().message()).isEqualTo("Запрос не выполнен");
  }

  @Test
  void handleValidationBuildsDetailsForFieldAndGlobalErrors() {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "name", "обязательно"));
    bindingResult.addError(new ObjectError("request", "глобальная ошибка"));
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);

    ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().message()).isEqualTo("Ошибка валидации");
    assertThat(response.getBody().details()).containsExactly("name: обязательно", "глобальная ошибка");
  }

  @Test
  void handleConstraintViolationBuildsDetailsList() {
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
    Path path = mock(Path.class);
    when(path.toString()).thenReturn("createOrder.items[0].quantity");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("должно быть положительным");
    ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

    ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().details()).containsExactly("createOrder.items[0].quantity: должно быть положительным");
  }

  @Test
  void handleUnreadableAndUnexpectedReturnExpectedPayload() {
    ResponseEntity<ErrorResponse> unreadable = handler.handleUnreadableBody(
        new HttpMessageNotReadableException("bad-body", mock(HttpInputMessage.class))
    );
    assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(unreadable.getBody().message()).isEqualTo("Некорректное тело запроса");
    assertThat(unreadable.getBody().details()).isEmpty();

    ResponseEntity<ErrorResponse> unexpected = handler.handleUnexpected(new IllegalStateException("boom"));
    assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(unexpected.getBody().message()).isEqualTo("Непредвиденная ошибка сервера");
    assertThat(unexpected.getBody().details()).isEmpty();
  }

  @Test
  void handleMethodNotSupportedReturns405() {
    ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
        new HttpRequestMethodNotSupportedException("GET", List.of("POST"))
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody().message()).isEqualTo("Метод запроса не поддерживается");
    assertThat(response.getBody().details()).containsExactly("POST");
  }

  @Test
  void handleResourceNotFoundReturns404() {
    ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(
        new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/dashboard/categories")
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().message()).isEqualTo("Ресурс не найден");
    assertThat(response.getBody().details()).isEmpty();
  }
}
