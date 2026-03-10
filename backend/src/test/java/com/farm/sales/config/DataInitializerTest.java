package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataInitializerTest {
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
        passwordEncoder
    );

    when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> {
      String rawPassword = invocation.getArgument(0, String.class);
      return "encoded::" + rawPassword;
    });
    when(productRepository.findByPhotoUrlIgnoreCase(any())).thenReturn(Optional.empty());
    when(productRepository.findAll()).thenReturn(List.of());
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
    verify(userRepository, times(8)).save(userCaptor.capture());
    
    ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    verify(productRepository, atLeast(200)).save(productCaptor.capture());
    verify(storeAddressRepository, times(3)).save(any(StoreAddress.class));

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

    assertThat(legacyCatalogProduct.getName()).isNotEqualTo("Каталожный товар 101");
    assertThat(legacyCatalogProduct.getCategory()).isNotEqualTo("Каталог");
    assertThat(legacyCatalogProduct.getPhotoUrl()).isEqualTo("/images/products/mogilev-product-101.webp");
    verify(productRepository, atLeast(1)).save(any(Product.class));
  }
}
