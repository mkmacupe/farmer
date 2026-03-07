package com.farm.sales.config;

import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
  private static final String PRODUCT_IMAGE_BASE = "/images/products/";
  private static final Map<String, String> SEEDED_USER_PASSWORDS = Map.of(
      "mogilevkhim", "MhvK8r2pQ1",
      "mogilevlift", "MlvT4n7xR2",
      "babushkina", "BbkP6m9sL3",
      "manager", "MgrD5v8cN4",
      "logistician", "LogS7q1wE5",
      "driver1", "Drv1A9k2Z6",
      "driver2", "Drv2B8m3Y7",
      "driver3", "Drv3C7n4X8"
  );

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final PasswordEncoder passwordEncoder;
  private final Object seedLock = new Object();
  @Value("${app.demo.seed-on-startup:true}")
  private boolean seedOnStartup = true;
  private volatile boolean demoSeeded;

  public DataInitializer(UserRepository userRepository,
                         ProductRepository productRepository,
                         StoreAddressRepository storeAddressRepository,
                         PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!seedOnStartup) {
      log.info("Startup demo seed skipped: app.demo.seed-on-startup=false");
      return;
    }
    ensureDemoDataSeeded();
  }

  @Transactional
  public void seedDemoData() {
    ensureDemoDataSeeded();
  }

  private void ensureDemoDataSeeded() {
    if (demoSeeded) return;
    synchronized (seedLock) {
      if (demoSeeded) return;
      
      User mDirector = createUserIfMissing("mogilevkhim", "Олег Курилин", "+375291948265", "ОАО \"Могилевхимволокно\"", Role.DIRECTOR, seededPassword("mogilevkhim"));
      User lDirector = createUserIfMissing("mogilevlift", "Руслан Страхар", "+375336521874", "ОАО \"Могилевлифтмаш\"", Role.DIRECTOR, seededPassword("mogilevlift"));
      User bDirector = createUserIfMissing("babushkina", "Эдуард Орешко", "+375447318502", "ОАО \"Бабушкина крынка\"", Role.DIRECTOR, seededPassword("babushkina"));
      
      createUserIfMissing("manager", "Менеджер", "+375290000002", null, Role.MANAGER, seededPassword("manager"));
      createUserIfMissing("logistician", "Логист", "+375290000003", null, Role.LOGISTICIAN, seededPassword("logistician"));
      createUserIfMissing("driver1", "Водитель 1", "+375290000005", null, Role.DRIVER, seededPassword("driver1"));
      createUserIfMissing("driver2", "Водитель 2", "+375290000006", null, Role.DRIVER, seededPassword("driver2"));
      createUserIfMissing("driver3", "Водитель 3", "+375290000007", null, Role.DRIVER, seededPassword("driver3"));

      createAddressIfMissing(mDirector, "МХВ Точка 01", "Могилёв, ул. Челюскинцев 105", "53.8654", "30.2905");
      createAddressIfMissing(lDirector, "МЛМ Точка 01", "Могилёв, пр-т Мира 42", "53.8948", "30.3312");
      createAddressIfMissing(bDirector, "БК Точка 01", "Могилёв, ул. Академика Павлова 3", "53.9342", "30.2941");

      seedProduct("Молоко фермерское 1 л", "Молочная продукция", "3.20", 120, "milk.webp");
      seedProduct("Кефир домашний 1 л", "Молочная продукция", "3.40", 95, "kefir.webp");
      seedProduct("Ряженка 500 мл", "Молочная продукция", "2.90", 85, "kefir-05l.webp");
      seedProduct("Йогурт натуральный 500 мл", "Молочная продукция", "5.30", 75, "yogurt.webp");
      seedProduct("Творог рассыпчатый 500 г", "Молочная продукция", "4.90", 80, "cottage-cheese.webp");
      seedProduct("Сметана 20% 400 г", "Молочная продукция", "4.30", 78, "sour-cream.webp");
      seedProduct("Масло сливочное 82.5% 200 г", "Молочная продукция", "6.40", 65, "butter.webp");
      seedProduct("Сыр полутвёрдый 500 г", "Молочная продукция", "13.90", 55, "cheese.webp");
      seedProduct("Яйца куриные С1 10 шт", "Птица и яйца", "3.70", 180, "egg.webp");
      seedProduct("Курица фермерская 1 кг", "Мясо и птица", "8.10", 50, "chicken.webp");
      seedProduct("Картофель молодой 2 кг", "Овощи", "5.40", 140, "potato.webp");
      seedProduct("Морковь сладкая 1 кг", "Овощи", "2.30", 120, "carrot.webp");
      seedProduct("Лук репчатый 1 кг", "Овощи", "2.10", 115, "onion.webp");
      seedProduct("Огурцы грунтовые 1 кг", "Овощи", "4.90", 90, "cucumber.webp");
      seedProduct("Томаты розовые 1 кг", "Овощи", "5.60", 85, "tomato.webp");
      seedProduct("Яблоки садовые 1 кг", "Фрукты", "3.20", 110, "apple.webp");
      seedProduct("Мёд цветочный 500 г", "Пчеловодство", "11.90", 42, "honey.webp");
      seedProduct("Хлеб ржаной 600 г", "Хлеб и выпечка", "2.70", 95, "rye-bread.webp");
      seedProduct("Батон деревенский 400 г", "Хлеб и выпечка", "2.10", 100, "baguette.webp");
      seedProduct("Гречка ядрица 1 кг", "Крупы", "3.00", 90, "buckwheat.webp");

      demoSeeded = true;
    }
  }

  public void resetDemoSeedState() {
    synchronized (seedLock) {
      demoSeeded = false;
    }
  }

  private void seedProduct(String name, String cat, String price, int stock, String img) {
    Product existing = productRepository.findByNameIgnoreCase(name).orElse(null);
    double weight = parseWeight(name);
    double volume = parseVolume(name);
    String photoUrl = PRODUCT_IMAGE_BASE + img;
    boolean photoTakenByAnotherProduct = photoUrl != null && productRepository.existsByPhotoUrlIgnoreCase(photoUrl);
    String normalizedPhotoUrl = photoTakenByAnotherProduct ? null : photoUrl;

    if (existing == null) {
      Product p = new Product(name, cat, name, normalizedPhotoUrl, new BigDecimal(price), stock, weight, volume);
      productRepository.save(p);
    } else {
      boolean changed = false;
      if (existing.getPhotoUrl() == null && normalizedPhotoUrl != null) {
        existing.setPhotoUrl(normalizedPhotoUrl);
        changed = true;
      }
      if (existing.getWeightKg() == null || Math.abs(existing.getWeightKg() - weight) > 0.001) {
        existing.setWeightKg(weight);
        changed = true;
      }
      if (existing.getVolumeM3() == null || Math.abs(existing.getVolumeM3() - volume) > 0.001) {
        existing.setVolumeM3(volume);
        changed = true;
      }
      if (changed) productRepository.save(existing);
    }
  }

  private User createUserIfMissing(String username, String fullName, String phone, String legal, Role role, String password) {
    User existing = userRepository.findByUsername(username).orElse(null);
    if (existing == null) {
      return userRepository.save(new User(username, passwordEncoder.encode(password), fullName, phone, legal, role));
    }
    return existing;
  }

  private void createAddressIfMissing(User user, String label, String addressLine, String lat, String lon) {
    if (storeAddressRepository.existsByUserIdAndLabelIgnoreCase(user.getId(), label)) return;
    StoreAddress a = new StoreAddress();
    a.setUser(user);
    a.setLabel(label);
    a.setAddressLine(addressLine);
    a.setLatitude(new BigDecimal(lat));
    a.setLongitude(new BigDecimal(lon));
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    storeAddressRepository.save(a);
  }

  private double parseWeight(String name) {
    String l = name.toLowerCase(Locale.ROOT);
    if (l.contains("1 л") || l.contains("1 кг")) return 1.0;
    if (l.contains("2 л") || l.contains("2 кг")) return 2.0;
    if (l.contains("500 мл") || l.contains("500 г")) return 0.5;
    if (l.contains("400 г")) return 0.4;
    if (l.contains("200 г") || l.contains("250 г")) return 0.25;
    if (l.contains("10 шт")) return 0.6;
    if (l.contains("600 г") || l.contains("700 г")) return 0.7;
    return 1.0;
  }

  private double parseVolume(String name) {
    String l = name.toLowerCase(Locale.ROOT);
    if (l.contains("1 л") || l.contains("1 кг")) return 0.0012;
    if (l.contains("2 л") || l.contains("2 кг")) return 0.0025;
    if (l.contains("500 мл") || l.contains("500 г")) return 0.0006;
    return 0.001;
  }

  private String seededPassword(String username) {
    return SEEDED_USER_PASSWORDS.get(username);
  }
}
