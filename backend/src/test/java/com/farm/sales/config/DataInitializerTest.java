package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.model.Order;
import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DataInitializerTest {
  private UserRepository userRepository;
  private OrderRepository orderRepository;
  private ProductRepository productRepository;
  private StoreAddressRepository storeAddressRepository;
  private PasswordEncoder passwordEncoder;
  private DataInitializer dataInitializer;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    orderRepository = mock(OrderRepository.class);
    productRepository = mock(ProductRepository.class);
    storeAddressRepository = mock(StoreAddressRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    dataInitializer = new DataInitializer(
        userRepository,
        orderRepository,
        productRepository,
        storeAddressRepository,
        passwordEncoder
    );

    when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> {
      String rawPassword = invocation.getArgument(0, String.class);
      return "encoded::" + rawPassword;
    });
    when(productRepository.findByPhotoUrlIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.findAll()).thenReturn(List.of());
    when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
    when(orderRepository.findByDeliveryAddressId(any())).thenReturn(List.of());
    when(orderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void runCreatesDemoDataWhenEntitiesMissing() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

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
    verify(userRepository, times(expectedSeededUsernames().size())).save(userCaptor.capture());
    assertThat(userCaptor.getAllValues())
        .extracting(User::getUsername)
        .containsExactlyInAnyOrderElementsOf(expectedSeededUsernames());
    
    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository, atLeast(200)).save(productCaptor.capture());
    ArgumentCaptor<StoreAddress> addressCaptor = ArgumentCaptor.forClass(StoreAddress.class);
    verify(storeAddressRepository, times(3)).save(addressCaptor.capture());
    assertThat(addressCaptor.getAllValues())
        .extracting(StoreAddress::getLabel)
        .containsExactly(
            "Лавка Полесья • Центральный",
            "Сезонный Двор • Проспект Мира",
            "Усадьба Урожая • Павлова"
        );

    Product firstProduct = productCaptor.getAllValues().get(0);
    assertThat(firstProduct.getWeightKg()).isNotNull();
    assertThat(firstProduct.getVolumeM3()).isNotNull();
  }

  @Test
  void seedDemoDataCallsRun() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User u = invocation.getArgument(0);
      u.setId(1L);
      return u;
    });

    dataInitializer.seedDemoData();

    verify(userRepository, atLeast(1)).findByUsername(anyString());
  }

  @Test
  void seedDemoDataWithoutAddressesDoesNotCreateStoreAddresses() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);

    AtomicLong userIds = new AtomicLong(100);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      return user;
    });
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.seedDemoDataWithoutAddresses();

    verify(storeAddressRepository, times(0)).save(any(StoreAddress.class));
  }

  @Test
  void runUsesConfiguredProductImagesDirectoryOverride() throws Exception {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(199L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

    Path tempDir = Files.createTempDirectory("farm-sales-demo-images");
    Files.writeString(tempDir.resolve("custom-fast.webp"), "placeholder");
    ReflectionTestUtils.setField(dataInitializer, "productImagesDir", tempDir.toString());

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

    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository, atLeast(21)).save(productCaptor.capture());
    assertThat(productCaptor.getAllValues())
        .extracting(Product::getPhotoUrl)
        .contains("/images/products/custom-fast.webp");
  }

  @Test
  void runNormalizesExistingCatalogProductFoundByPhoto() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

    Product legacyCatalogProduct = new Product(
        "Каталожный товар 101",
        "Каталог",
        "Каталожный товар 101",
        "/images/products/mogilev-product-101.webp",
        new BigDecimal("4.10"),
        24
    );
    legacyCatalogProduct.setId(501L);
    legacyCatalogProduct.setWeightKg(1.0);
    legacyCatalogProduct.setVolumeM3(0.001);
    when(productRepository.findByPhotoUrlIgnoreCase(any())).thenAnswer(invocation -> {
      String photoUrl = invocation.getArgument(0, String.class);
      if ("/images/products/mogilev-product-101.webp".equals(photoUrl)) {
        return Optional.of(legacyCatalogProduct);
      }
      return Optional.empty();
    });
    when(productRepository.findAll()).thenReturn(List.of(legacyCatalogProduct));

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

    assertThat(legacyCatalogProduct.getName()).isEqualTo("Молоко пастеризованное 3.2% 1 л");
    assertThat(legacyCatalogProduct.getCategory()).isEqualTo("Молочная продукция");
    assertThat(legacyCatalogProduct.getPhotoUrl()).isEqualTo("/images/products/mogilev-product-101.webp");
    verify(productRepository, atLeast(1)).save(any(Product.class));
  }

  @Test
  void runNormalizesOpaqueCatalogProductFoundByPhoto() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

    Product legacyCatalogProduct = new Product(
        "Каталожный товар 301",
        "Каталог",
        "Каталожный товар 301",
        "/images/products/82-5-200-1jt74k3.webp",
        new BigDecimal("6.40"),
        18
    );
    legacyCatalogProduct.setId(777L);
    legacyCatalogProduct.setWeightKg(1.0);
    legacyCatalogProduct.setVolumeM3(0.001);
    when(productRepository.findByPhotoUrlIgnoreCase(any())).thenAnswer(invocation -> {
      String photoUrl = invocation.getArgument(0, String.class);
      if ("/images/products/82-5-200-1jt74k3.webp".equals(photoUrl)) {
        return Optional.of(legacyCatalogProduct);
      }
      return Optional.empty();
    });
    when(productRepository.findAll()).thenReturn(List.of(legacyCatalogProduct));

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

    assertThat(legacyCatalogProduct.getName()).isEqualTo("Масло крестьянское 82.5% 200 г");
    assertThat(legacyCatalogProduct.getCategory()).isEqualTo("Молочная продукция");
    assertThat(legacyCatalogProduct.getPhotoUrl()).isEqualTo("/images/products/82-5-200-1jt74k3.webp");
    verify(productRepository, atLeast(1)).save(any(Product.class));
  }

  @Test
  void runKeepsSeparateProductsForDifferentImagesWithSameReadableName() {
    when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

    Map<String, Product> productsByPhoto = new HashMap<>();
    when(productRepository.findByNameIgnoreCase(any())).thenAnswer(invocation -> {
      String name = invocation.getArgument(0, String.class);
      return productsByPhoto.values().stream()
          .filter(product -> name.equalsIgnoreCase(product.getName()))
          .findFirst();
    });
    when(productRepository.findByPhotoUrlIgnoreCase(any())).thenAnswer(invocation ->
        Optional.ofNullable(productsByPhoto.get(invocation.getArgument(0, String.class))));
    when(productRepository.findAll()).thenReturn(List.of());

    AtomicLong userIds = new AtomicLong(100);
    AtomicLong productIds = new AtomicLong(500);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      return user;
    });
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
      Product product = invocation.getArgument(0);
      if (product.getId() == null) {
        product.setId(productIds.incrementAndGet());
      }
      if (product.getPhotoUrl() != null) {
        productsByPhoto.put(product.getPhotoUrl(), product);
      }
      return product;
    });
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.run();

    Product honeyLinden = productsByPhoto.get("/images/products/honey-linden.webp");
    Product lindenHoney = productsByPhoto.get("/images/products/linden-honey.webp");
    assertThat(honeyLinden).isNotNull();
    assertThat(lindenHoney).isNotNull();
    assertThat(honeyLinden.getId()).isNotEqualTo(lindenHoney.getId());
    assertThat(honeyLinden.getName()).isEqualTo("Мёд липовый 500 г");
    assertThat(lindenHoney.getName()).isEqualTo("Мёд липовый 500 г");
  }

  @Test
  void runRenamesLegacyDirectorInsteadOfCreatingDuplicateSeededDirector() {
    Map<String, User> usersByUsername = new HashMap<>();
    User legacyDirector = new User(
        "director04",
        "encoded::Dir04Farm2026",
        "Директор магазина 04",
        "+375291000004",
        "Магазин \"Демо 04\"",
        Role.DIRECTOR
    );
    legacyDirector.setId(404L);
    usersByUsername.put("director04", legacyDirector);

    when(userRepository.findByUsername(any())).thenAnswer(invocation -> Optional.ofNullable(
        usersByUsername.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT))
    ));
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);

    AtomicLong userIds = new AtomicLong(2000);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      usersByUsername.entrySet().removeIf(entry ->
          Objects.equals(entry.getValue().getId(), user.getId())
              && !entry.getKey().equalsIgnoreCase(user.getUsername()));
      usersByUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user);
      return user;
    });
    doAnswer(invocation -> {
      User user = invocation.getArgument(0);
      usersByUsername.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getId(), user.getId()));
      return null;
    }).when(userRepository).delete(any(User.class));
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.run();

    assertThat(usersByUsername).doesNotContainKey("director04");
    assertThat(usersByUsername).containsKey("dirgromova");
    User renamedDirector = usersByUsername.get("dirgromova");
    assertThat(renamedDirector.getId()).isEqualTo(404L);
    assertThat(renamedDirector.getFullName()).isEqualTo("Елена Громова");
    assertThat(renamedDirector.getLegalEntityName()).isEqualTo("ООО \"Зелёная Полка\"");
  }

  @Test
  void runRenamesLegacyPrimaryAddressLabelInsteadOfCreatingDuplicateAddress() {
    Map<String, User> usersByUsername = new HashMap<>();
    User seededDirector = new User(
        "diralekseev",
        "encoded::AlekseevFarm26",
        "Андрей Алексеев",
        "+375291000001",
        "ООО \"Лавка Полесья\"",
        Role.DIRECTOR
    );
    seededDirector.setId(101L);
    usersByUsername.put("diralekseev", seededDirector);

    when(userRepository.findByUsername(any())).thenAnswer(invocation -> Optional.ofNullable(
        usersByUsername.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT))
    ));
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);

    Map<String, StoreAddress> addressesByKey = new HashMap<>();
    StoreAddress legacyAddress = new StoreAddress();
    legacyAddress.setId(501L);
    legacyAddress.setUser(seededDirector);
    legacyAddress.setLabel("Демо 01 • Центральный");
    legacyAddress.setAddressLine("Могилёв, ул. Челюскинцев 105");
    legacyAddress.setLatitude(new BigDecimal("53.8654"));
    legacyAddress.setLongitude(new BigDecimal("30.2905"));
    legacyAddress.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    legacyAddress.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    addressesByKey.put(addressKey(101L, "Демо 01 • Центральный"), legacyAddress);

    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenAnswer(invocation ->
        addressesByKey.containsKey(addressKey(
            invocation.getArgument(0, Long.class),
            invocation.getArgument(1, String.class)
        )));
    when(storeAddressRepository.findByUserIdAndLabelIgnoreCase(any(), any())).thenAnswer(invocation ->
        Optional.ofNullable(addressesByKey.get(addressKey(
            invocation.getArgument(0, Long.class),
            invocation.getArgument(1, String.class)
        ))));

    AtomicLong userIds = new AtomicLong(2000);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      usersByUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user);
      return user;
    });
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AtomicLong addressIds = new AtomicLong(800);
    List<StoreAddress> savedAddresses = new ArrayList<>();
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
      StoreAddress address = invocation.getArgument(0);
      if (address.getId() == null) {
        address.setId(addressIds.incrementAndGet());
      }
      addressesByKey.entrySet().removeIf(entry ->
          Objects.equals(entry.getValue().getId(), address.getId())
              && !entry.getKey().equalsIgnoreCase(addressKey(address.getUser().getId(), address.getLabel())));
      addressesByKey.put(addressKey(address.getUser().getId(), address.getLabel()), address);
      savedAddresses.add(address);
      return address;
    });

    dataInitializer.run();

    assertThat(addressesByKey).doesNotContainKey(addressKey(101L, "Демо 01 • Центральный"));
    assertThat(addressesByKey).containsKey(addressKey(101L, "Лавка Полесья • Центральный"));
    StoreAddress normalizedAddress = addressesByKey.get(addressKey(101L, "Лавка Полесья • Центральный"));
    assertThat(normalizedAddress.getId()).isEqualTo(501L);
    assertThat(savedAddresses)
        .filteredOn(address -> Objects.equals(address.getUser().getId(), 101L))
        .extracting(StoreAddress::getLabel)
        .contains("Лавка Полесья • Центральный")
        .doesNotContain("Демо 01 • Центральный");
  }

  @Test
  void runRemovesLegacyDirectorWhenNewSeededDirectorAlreadyExists() {
    Map<String, User> usersByUsername = new HashMap<>();
    User currentDirector = new User(
        "dirgromova",
        "encoded::GromovaFarm26",
        "Елена Громова",
        "+375291000004",
        "ООО \"Зелёная Полка\"",
        Role.DIRECTOR
    );
    currentDirector.setId(1004L);
    usersByUsername.put("dirgromova", currentDirector);

    User legacyDirector = new User(
        "director04",
        "encoded::Dir04Farm2026",
        "Директор магазина 04",
        "+375291000004",
        "Магазин \"Демо 04\"",
        Role.DIRECTOR
    );
    legacyDirector.setId(404L);
    usersByUsername.put("director04", legacyDirector);

    when(userRepository.findByUsername(any())).thenAnswer(invocation -> Optional.ofNullable(
        usersByUsername.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT))
    ));
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);
    when(storeAddressRepository.findByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(Optional.empty());

    AtomicLong userIds = new AtomicLong(1000);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      usersByUsername.entrySet().removeIf(entry ->
          Objects.equals(entry.getValue().getId(), user.getId())
              && !entry.getKey().equalsIgnoreCase(user.getUsername()));
      usersByUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user);
      return user;
    });
    doAnswer(invocation -> {
      User user = invocation.getArgument(0);
      usersByUsername.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getId(), user.getId()));
      return null;
    }).when(userRepository).delete(any(User.class));
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.run();

    assertThat(usersByUsername).doesNotContainKey("director04");
    verify(userRepository).delete(argThat(user -> Objects.equals(user.getId(), 404L)));
  }

  @Test
  void runMergesMultipleLegacyDirectorAliasesWhenCurrentSeededUserIsMissing() {
    Map<String, User> usersByUsername = new HashMap<>();
    User normalizedLegacyDirector = new User(
        "director01",
        "encoded::Dir01Farm2026",
        "Директор магазина 01",
        "+375291000001",
        "Магазин \"Демо 01\"",
        Role.DIRECTOR
    );
    normalizedLegacyDirector.setId(101L);
    usersByUsername.put("director01", normalizedLegacyDirector);

    User preNormalizedLegacyDirector = new User(
        "berezka",
        "encoded::OldBerezka",
        "Ирина Соколова",
        "+375291000001",
        "Магазин \"Берёзка\"",
        Role.DIRECTOR
    );
    preNormalizedLegacyDirector.setId(201L);
    usersByUsername.put("berezka", preNormalizedLegacyDirector);

    when(userRepository.findByUsername(any())).thenAnswer(invocation -> Optional.ofNullable(
        usersByUsername.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT))
    ));
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(false);
    when(storeAddressRepository.findByUserIdAndLabelIgnoreCase(any(), any())).thenReturn(Optional.empty());

    AtomicLong userIds = new AtomicLong(2000);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      usersByUsername.entrySet().removeIf(entry ->
          Objects.equals(entry.getValue().getId(), user.getId())
              && !entry.getKey().equalsIgnoreCase(user.getUsername()));
      usersByUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user);
      return user;
    });
    doAnswer(invocation -> {
      User user = invocation.getArgument(0);
      usersByUsername.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getId(), user.getId()));
      return null;
    }).when(userRepository).delete(any(User.class));
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    dataInitializer.run();

    assertThat(usersByUsername).containsKey("diralekseev");
    assertThat(usersByUsername).doesNotContainKeys("director01", "berezka");
    verify(userRepository).delete(argThat(user -> Objects.equals(user.getId(), 201L)));
  }

  @Test
  void runRemovesLegacyPrimaryAddressWhenNormalizedAddressAlreadyExists() {
    Map<String, User> usersByUsername = new HashMap<>();
    User seededDirector = new User(
        "diralekseev",
        "encoded::AlekseevFarm26",
        "Андрей Алексеев",
        "+375291000001",
        "ООО \"Лавка Полесья\"",
        Role.DIRECTOR
    );
    seededDirector.setId(101L);
    usersByUsername.put("diralekseev", seededDirector);

    when(userRepository.findByUsername(any())).thenAnswer(invocation -> Optional.ofNullable(
        usersByUsername.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT))
    ));
    when(productRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.count()).thenReturn(20L);

    Map<String, StoreAddress> addressesByKey = new HashMap<>();
    StoreAddress normalizedAddress = new StoreAddress();
    normalizedAddress.setId(701L);
    normalizedAddress.setUser(seededDirector);
    normalizedAddress.setLabel("Лавка Полесья • Центральный");
    normalizedAddress.setAddressLine("Могилёв, ул. Челюскинцев 105");
    normalizedAddress.setLatitude(new BigDecimal("53.8654"));
    normalizedAddress.setLongitude(new BigDecimal("30.2905"));
    normalizedAddress.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    normalizedAddress.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    addressesByKey.put(addressKey(101L, "Лавка Полесья • Центральный"), normalizedAddress);

    StoreAddress legacyAddress = new StoreAddress();
    legacyAddress.setId(702L);
    legacyAddress.setUser(seededDirector);
    legacyAddress.setLabel("Демо 01 • Центральный");
    legacyAddress.setAddressLine("Могилёв, ул. Челюскинцев 105");
    legacyAddress.setLatitude(new BigDecimal("53.8654"));
    legacyAddress.setLongitude(new BigDecimal("30.2905"));
    legacyAddress.setCreatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    legacyAddress.setUpdatedAt(Instant.parse("2026-03-01T00:00:00Z"));
    addressesByKey.put(addressKey(101L, "Демо 01 • Центральный"), legacyAddress);

    Order order = new Order();
    order.setId(901L);
    order.setCustomer(seededDirector);
    order.setDeliveryAddress(legacyAddress);
    order.setDeliveryAddressText(legacyAddress.getAddressLine());
    order.setDeliveryLatitude(legacyAddress.getLatitude());
    order.setDeliveryLongitude(legacyAddress.getLongitude());
    when(orderRepository.findByDeliveryAddressId(702L)).thenReturn(List.of(order));

    when(storeAddressRepository.existsByUserIdAndLabelIgnoreCase(any(), any())).thenAnswer(invocation ->
        addressesByKey.containsKey(addressKey(
            invocation.getArgument(0, Long.class),
            invocation.getArgument(1, String.class)
        )));
    when(storeAddressRepository.findByUserIdAndLabelIgnoreCase(any(), any())).thenAnswer(invocation ->
        Optional.ofNullable(addressesByKey.get(addressKey(
            invocation.getArgument(0, Long.class),
            invocation.getArgument(1, String.class)
        ))));

    AtomicLong userIds = new AtomicLong(1000);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      if (user.getId() == null) {
        user.setId(userIds.incrementAndGet());
      }
      usersByUsername.put(user.getUsername().toLowerCase(Locale.ROOT), user);
      return user;
    });
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AtomicLong addressIds = new AtomicLong(800);
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
      StoreAddress address = invocation.getArgument(0);
      if (address.getId() == null) {
        address.setId(addressIds.incrementAndGet());
      }
      addressesByKey.entrySet().removeIf(entry ->
          Objects.equals(entry.getValue().getId(), address.getId())
              && !entry.getKey().equalsIgnoreCase(addressKey(address.getUser().getId(), address.getLabel())));
      addressesByKey.put(addressKey(address.getUser().getId(), address.getLabel()), address);
      return address;
    });
    doAnswer(invocation -> {
      StoreAddress address = invocation.getArgument(0);
      addressesByKey.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getId(), address.getId()));
      return null;
    }).when(storeAddressRepository).delete(any(StoreAddress.class));

    dataInitializer.run();

    assertThat(addressesByKey).containsKey(addressKey(101L, "Лавка Полесья • Центральный"));
    assertThat(addressesByKey).doesNotContainKey(addressKey(101L, "Демо 01 • Центральный"));
    assertThat(order.getDeliveryAddress()).isSameAs(normalizedAddress);
    assertThat(order.getDeliveryAddressText()).isEqualTo("Могилёв, ул. Челюскинцев 105");
    assertThat(order.getDeliveryLatitude()).isEqualByComparingTo("53.8654");
    assertThat(order.getDeliveryLongitude()).isEqualByComparingTo("30.2905");
  }

  private Set<String> expectedSeededUsernames() {
    Set<String> usernames = new LinkedHashSet<>(List.of(
        "diralekseev",
        "dirbaranova",
        "dirvasilevsky",
        "dirgromova",
        "dirdrozdov",
        "dirermakova",
        "dirzhuravlev",
        "dirzimina",
        "dirivashkevich",
        "dirkovaleva",
        "dirlavrinenko",
        "dirmelnik",
        "dirnovik",
        "dirosipova",
        "dirparkhomenko",
        "dirrudenko",
        "dirsavchuk",
        "dirtarasova",
        "dirulyanov",
        "dirfedorova",
        "dirharitonov",
        "dirsokolova",
        "dirchernov",
        "dirshevtsova",
        "diryashin",
        "dirabramova",
        "dirbelyaev",
        "dirvoronova",
        "dirgrishin",
        "dirdanilova"
    ));
    usernames.add("manager");
    usernames.add("logistician");
    usernames.add("driver1");
    usernames.add("driver2");
    usernames.add("driver3");
    return usernames;
  }

  private String addressKey(Long userId, String label) {
    return userId + "|" + label.toLowerCase(Locale.ROOT);
  }
}
