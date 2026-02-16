package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.model.Product;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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

    when(passwordEncoder.encode(DEMO_PASSWORD)).thenReturn("encoded-password");
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

    verify(userRepository, times(8)).save(any(User.class));
    verify(productRepository, times(34)).save(any(Product.class));
    verify(storeAddressRepository, times(3)).save(any(StoreAddress.class));
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
}
