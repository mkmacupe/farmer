package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataInitializerTest {
  private static final String DEMO_PASSWORD = "StrongDemoPass123!";

  private UserRepository userRepository;
  private ProductRepository productRepository;
  private StoreAddressRepository storeAddressRepository;
  private PasswordEncoder passwordEncoder;
  private DataInitializer dataInitializer;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    productRepository = mock(ProductRepository.class);
    storeAddressRepository = mock(StoreAddressRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    dataInitializer = new DataInitializer(
        userRepository,
        productRepository,
        storeAddressRepository,
        passwordEncoder,
        DEMO_PASSWORD
    );

    when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> {
      String rawPassword = invocation.getArgument(0, String.class);
      return "encoded::" + rawPassword;
    });
  }

  @Test
  void runCreatesDemoDataWhenEntitiesMissing() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);
    when(storeAddressRepository.findByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(Optional.empty());

    AtomicLong userIds = new AtomicLong(100);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      return user;
    });
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.run();

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository, times(8)).save(userCaptor.capture());
    var userPasswordHashes = userCaptor.getAllValues().stream()
        .map(User::getPasswordHash)
        .toList();
    assertThat(userPasswordHashes).doesNotHaveDuplicates();

    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository, atLeast(20)).save(productCaptor.capture());
    verify(storeAddressRepository, times(3)).save(any(StoreAddress.class));

    var photoUrls = productCaptor.getAllValues().stream()
        .map(Product::getPhotoUrl)
        .toList();
    var nonNullPhotoUrls = photoUrls.stream().filter(Objects::nonNull).toList();
    assertThat(nonNullPhotoUrls).doesNotHaveDuplicates();
    assertThat(nonNullPhotoUrls).allMatch(url -> url.matches("^/images/products/[a-z0-9-]+\\.webp$"));
  }

  @Test
  void runFailsWhenDemoPasswordIsMissing() {
    DataInitializer insecureInitializer = new DataInitializer(
        userRepository,
        productRepository,
        storeAddressRepository,
        passwordEncoder,
        "   "
    );

    assertThatThrownBy(insecureInitializer::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.demo.password");
  }

  @Test
  void createUserIfMissingReturnsExistingWhenNoChangesNeeded() throws Exception {
    User existing = new User("manager", "stored", "Менеджер", "+375290000002", null, Role.MANAGER);
    existing.setId(1L);
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(existing));
    when(passwordEncoder.matches(DEMO_PASSWORD, "stored")).thenReturn(true);

    User result = (User) invoke(
        dataInitializer,
        "createUserIfMissing",
        new Class<?>[] {String.class, String.class, String.class, String.class, Role.class, String.class},
        "manager", "Менеджер", "+375290000002", null, Role.MANAGER, DEMO_PASSWORD
    );

    assertThat(result).isSameAs(existing);
    verify(userRepository, never()).save(existing);
  }

  @Test
  void createUserIfMissingUpdatesExistingWhenFieldsDiffer() throws Exception {
    User existing = new User("manager", "stored", "Старое имя", "+375299999999", "Old", Role.DRIVER);
    existing.setId(2L);
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(existing));
    when(passwordEncoder.matches(DEMO_PASSWORD, "stored")).thenReturn(false);
    when(passwordEncoder.encode(DEMO_PASSWORD)).thenReturn("new-hash");
    when(userRepository.save(existing)).thenReturn(existing);

    User result = (User) invoke(
        dataInitializer,
        "createUserIfMissing",
        new Class<?>[] {String.class, String.class, String.class, String.class, Role.class, String.class},
        "manager", "Менеджер", "+375290000002", null, Role.MANAGER, DEMO_PASSWORD
    );

    assertThat(result.getPasswordHash()).isEqualTo("new-hash");
    assertThat(result.getFullName()).isEqualTo("Менеджер");
    assertThat(result.getPhone()).isEqualTo("+375290000002");
    assertThat(result.getLegalEntityName()).isNull();
    assertThat(result.getRole()).isEqualTo(Role.MANAGER);
    verify(userRepository).save(existing);
  }

  @Test
  void createOrUpdateProductCoversCreateUpdateAndNoop() throws Exception {
    when(productRepository.findByNameIgnoreCase("Мёд")).thenReturn(Optional.empty());
    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Мёд", "Мёд", "Описание", "/images/products/honey.webp", "12.34", 10
    );
    verify(productRepository).save(any(Product.class));

    Product existing = new Product("Мёд", "Старая", "Старое", "/images/products/old.webp", new BigDecimal("1.00"), 1);
    when(productRepository.findByNameIgnoreCase("Мёд")).thenReturn(Optional.of(existing));
    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Мёд", "Новая", "Новое", "/images/products/new.webp", "2.00", 5
    );
    assertThat(existing.getCategory()).isEqualTo("Новая");
    assertThat(existing.getDescription()).isEqualTo("Новое");
    assertThat(existing.getPhotoUrl()).isEqualTo("/images/products/new.webp");
    assertThat(existing.getPrice()).isEqualByComparingTo("2.00");
    assertThat(existing.getStockQuantity()).isEqualTo(5);

    when(productRepository.findByNameIgnoreCase("Мёд")).thenReturn(Optional.of(existing));
    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Мёд", "Новая", "Новое", "/images/products/new.webp", "2.00", 5
    );
    verify(productRepository).save(existing);
    verify(productRepository, times(2)).save(any(Product.class));
  }

  @Test
  void createAddressIfMissingSkipsExistingAddressWithSameLabel() throws Exception {
    User user = new User();
    user.setId(5L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(5L, "Label")).thenReturn(true);

    invoke(
        dataInitializer,
        "createAddressIfMissing",
        new Class<?>[] {User.class, String.class, String.class, String.class, String.class},
        user, "Label", "Address", "53.1", "30.2"
    );

    verify(storeAddressRepository, never()).save(any(StoreAddress.class));
  }

  @Test
  void validateDemoPasswordRejectsPlaceholderPrefixAndEqualsNullableBranches() throws Exception {
    DataInitializer placeholderInitializer = new DataInitializer(
        userRepository, productRepository, storeAddressRepository, passwordEncoder, "replace-with-secret"
    );
    assertThatThrownBy(() -> invoke(placeholderInitializer, "validateDemoPassword", new Class<?>[] {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.demo.password");

    assertThat((Boolean) invoke(dataInitializer, "equalsNullable", new Class<?>[] {String.class, String.class}, null, null))
        .isTrue();
    assertThat((Boolean) invoke(dataInitializer, "equalsNullable", new Class<?>[] {String.class, String.class}, null, "x"))
        .isFalse();
    assertThat((Boolean) invoke(dataInitializer, "equalsNullable", new Class<?>[] {String.class, String.class}, "x", "x"))
        .isTrue();
    assertThat((Boolean) invoke(dataInitializer, "equalsNullable", new Class<?>[] {String.class, String.class}, "x", "y"))
        .isFalse();

    DataInitializer nullPasswordInitializer = new DataInitializer(
        userRepository, productRepository, storeAddressRepository, passwordEncoder, null
    );
    assertThatThrownBy(() -> invoke(nullPasswordInitializer, "validateDemoPassword", new Class<?>[] {}))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.demo.password");
  }

  @Test
  void archiveLegacyDirectorUserHandlesMissingAndCollision() throws Exception {
    when(userRepository.findByUsername("director")).thenReturn(Optional.empty());
    invoke(dataInitializer, "archiveLegacyDirectorUser", new Class<?>[] {});
    verify(userRepository, never()).save(any(User.class));

    User legacy = new User("director", "hash", "Директор", "+37529", "ООО", Role.DIRECTOR);
    legacy.setId(12L);
    when(userRepository.findByUsername("director")).thenReturn(Optional.of(legacy));
    when(userRepository.existsByUsername("legacy-director-12")).thenReturn(true);
    when(userRepository.save(legacy)).thenReturn(legacy);

    invoke(dataInitializer, "archiveLegacyDirectorUser", new Class<?>[] {});

    assertThat(legacy.getUsername()).startsWith("legacy-director-12-");
    assertThat(legacy.getFullName()).isEqualTo("Архивный пользователь");
    assertThat(legacy.getPhone()).isNull();
    assertThat(legacy.getLegalEntityName()).isNull();
    assertThat(legacy.getRole()).isEqualTo(Role.MANAGER);
    verify(userRepository).save(legacy);

    User legacyNoCollision = new User("director", "hash", "Директор", "+37529", "ООО", Role.DIRECTOR);
    legacyNoCollision.setId(13L);
    when(userRepository.findByUsername("director")).thenReturn(Optional.of(legacyNoCollision));
    when(userRepository.existsByUsername("legacy-director-13")).thenReturn(false);

    invoke(dataInitializer, "archiveLegacyDirectorUser", new Class<?>[] {});

    assertThat(legacyNoCollision.getUsername()).isEqualTo("legacy-director-13");
    verify(userRepository).save(legacyNoCollision);
  }

  @Test
  void createOrUpdateProductHandlesNullPriceAndStockOnExistingEntity() throws Exception {
    Product existing = new Product();
    existing.setName("Тест");
    existing.setCategory("Cat");
    existing.setDescription("Desc");
    existing.setPhotoUrl("/images/products/test.webp");
    existing.setPrice(null);
    existing.setStockQuantity(null);
    when(productRepository.findByNameIgnoreCase("Тест")).thenReturn(Optional.of(existing));

    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Тест", "Cat", "Desc", "/images/products/test.webp", "9.99", 3
    );

    assertThat(existing.getPrice()).isEqualByComparingTo("9.99");
    assertThat(existing.getStockQuantity()).isEqualTo(3);
    verify(productRepository).save(existing);
  }

  @Test
  void createOrUpdateProductGeneratesUniquePhotoWhenPhotoAlreadyTakenForNewProduct() throws Exception {
    when(productRepository.findByNameIgnoreCase("Тест")).thenReturn(Optional.empty());
    when(productRepository.existsByPhotoUrlIgnoreCase("/images/products/test.webp")).thenReturn(true);

    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Тест", "Cat", "Desc", "/images/products/test.webp", "9.99", 3
    );

    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository).save(productCaptor.capture());
    String generatedPhotoUrl = productCaptor.getValue().getPhotoUrl();
    assertThat(generatedPhotoUrl).isNotBlank();
    assertThat(generatedPhotoUrl).isNotEqualTo("/images/products/test.webp");
    assertThat(generatedPhotoUrl).matches("^/images/products/[a-z0-9-]+\\.webp$");
  }

  @Test
  void createOrUpdateProductGeneratesUniquePhotoWhenPhotoTakenByAnotherExistingProduct() throws Exception {
    Product existing = new Product("Тест", "Cat", "Desc", "/images/products/old.webp", new BigDecimal("1.00"), 1);
    existing.setId(7L);
    when(productRepository.findByNameIgnoreCase("Тест")).thenReturn(Optional.of(existing));
    when(productRepository.existsByPhotoUrlIgnoreCaseAndIdNot("/images/products/test.webp", 7L)).thenReturn(true);

    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Тест", "Cat", "Desc", "/images/products/test.webp", "9.99", 3
    );

    assertThat(existing.getPhotoUrl()).isNotBlank();
    assertThat(existing.getPhotoUrl()).isNotEqualTo("/images/products/test.webp");
    assertThat(existing.getPhotoUrl()).matches("^/images/products/[a-z0-9-]+\\.webp$");
    verify(productRepository).save(existing);
  }

  @Test
  void createOrUpdateProductPersistsAdditionalProductWithoutLegacyLimit() throws Exception {
    when(productRepository.findByNameIgnoreCase("Лишний товар")).thenReturn(Optional.empty());
    invoke(
        dataInitializer,
        "createOrUpdateProduct",
        new Class<?>[] {String.class, String.class, String.class, String.class, String.class, int.class},
        "Лишний товар", "Категория", "Описание", "/images/products/extra.webp", "1.00", 1
    );

    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository).save(productCaptor.capture());
    assertThat(productCaptor.getValue().getName()).isEqualTo("Лишний товар");
    assertThat(productCaptor.getValue().getStockQuantity()).isEqualTo(1);
    verify(productRepository, never()).delete(any(Product.class));
  }

  private Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new RuntimeException(cause);
    }
  }
}
