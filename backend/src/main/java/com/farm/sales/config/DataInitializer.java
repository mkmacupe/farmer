package com.farm.sales.config;

import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private static final int DEMO_PRODUCT_TARGET_COUNT = 200;
  private static final Set<String> CORE_PRODUCT_IMAGES = Set.of(
      "milk.webp",
      "kefir.webp",
      "kefir-05l.webp",
      "yogurt.webp",
      "cottage-cheese.webp",
      "sour-cream.webp",
      "butter.webp",
      "cheese.webp",
      "egg.webp",
      "chicken.webp",
      "potato.webp",
      "carrot.webp",
      "onion.webp",
      "cucumber.webp",
      "tomato.webp",
      "apple.webp",
      "honey.webp",
      "rye-bread.webp",
      "baguette.webp",
      "buckwheat.webp"
  );
  private static final List<Path> PRODUCT_IMAGE_DIR_CANDIDATES = List.of(
      Path.of("frontend", "public", "images", "products"),
      Path.of("..", "frontend", "public", "images", "products")
  );
  private static final Map<String, String> SEEDED_USER_PASSWORDS = Map.of(
      "berezka", "BrzK8r2pQ1",
      "kvartal", "KvtT4n7xR2",
      "yantar", "YntP6m9sL3",
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
    ensureDemoDataSeeded(true);
  }

  @Transactional
  public void seedDemoData() {
    ensureDemoDataSeeded(true);
  }

  @Transactional
  public void seedDemoDataWithoutAddresses() {
    ensureDemoDataSeeded(false);
  }

  private void ensureDemoDataSeeded(boolean includeDefaultAddresses) {
    if (demoSeeded) return;
    synchronized (seedLock) {
      if (demoSeeded) return;
      
      User mDirector = createUserIfMissing("berezka", "Ирина Соколова", "+375291948265", "Магазин \"Берёзка\"", Role.DIRECTOR, seededPassword("berezka"));
      User lDirector = createUserIfMissing("kvartal", "Павел Лаврентьев", "+375336521874", "Магазин \"Квартал\"", Role.DIRECTOR, seededPassword("kvartal"));
      User bDirector = createUserIfMissing("yantar", "Наталья Гринько", "+375447318502", "Магазин \"Янтарь\"", Role.DIRECTOR, seededPassword("yantar"));
      
      createUserIfMissing("manager", "Менеджер", "+375290000002", null, Role.MANAGER, seededPassword("manager"));
      createUserIfMissing("logistician", "Логист", "+375290000003", null, Role.LOGISTICIAN, seededPassword("logistician"));
      createUserIfMissing("driver1", "Водитель 1", "+375290000005", null, Role.DRIVER, seededPassword("driver1"));
      createUserIfMissing("driver2", "Водитель 2", "+375290000006", null, Role.DRIVER, seededPassword("driver2"));
      createUserIfMissing("driver3", "Водитель 3", "+375290000007", null, Role.DRIVER, seededPassword("driver3"));

      if (includeDefaultAddresses) {
        createAddressIfMissing(mDirector, "Берёзка • Центральный", "Могилёв, ул. Челюскинцев 105", "53.8654", "30.2905");
        createAddressIfMissing(lDirector, "Квартал • Проспект Мира", "Могилёв, пр-т Мира 42", "53.8948", "30.3312");
        createAddressIfMissing(bDirector, "Янтарь • Павлова", "Могилёв, ул. Академика Павлова 3", "53.9342", "30.2941");
      }

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
      seedCatalogProducts();

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

  private void seedCatalogProducts() {
    long existingCount = productRepository.count();
    if (existingCount >= DEMO_PRODUCT_TARGET_COUNT) {
      return;
    }

    Path productImagesDirectory = resolveProductImagesDirectory();
    if (productImagesDirectory == null) {
      log.warn(
          "Demo product image directory was not found; keeping only {} core demo products.",
          existingCount
      );
      return;
    }

    List<String> availableImages = loadSupplementalProductImages(productImagesDirectory);
    int remaining = (int) Math.max(0, DEMO_PRODUCT_TARGET_COUNT - existingCount);
    int seededSupplemental = 0;

    for (String imageName : availableImages) {
      if (seededSupplemental >= remaining) {
        break;
      }
      int catalogIndex = (int) existingCount + seededSupplemental + 1;
      seedProduct(
          buildCatalogProductName(imageName, catalogIndex),
          resolveCatalogCategory(imageName),
          resolveCatalogPrice(imageName),
          resolveCatalogStock(imageName),
          imageName
      );
      seededSupplemental++;
    }

    if (seededSupplemental < remaining) {
      log.warn(
          "Seeded {} supplemental demo products out of requested {}. Available unique images: {}.",
          seededSupplemental,
          remaining,
          availableImages.size()
      );
      return;
    }

    log.info("Seeded {} supplemental demo products to restore the full demo catalog.", seededSupplemental);
  }

  private Path resolveProductImagesDirectory() {
    for (Path candidate : PRODUCT_IMAGE_DIR_CANDIDATES) {
      Path normalizedCandidate = candidate.toAbsolutePath().normalize();
      if (Files.isDirectory(normalizedCandidate)) {
        return normalizedCandidate;
      }
    }
    return null;
  }

  private List<String> loadSupplementalProductImages(Path productImagesDirectory) {
    try (Stream<Path> paths = Files.list(productImagesDirectory)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(this::isWebpImage)
          .filter(imageName -> !CORE_PRODUCT_IMAGES.contains(imageName))
          .sorted(Comparator.naturalOrder())
          .collect(Collectors.toList());
    } catch (IOException ex) {
      log.warn("Failed to load demo product images from {}", productImagesDirectory, ex);
      return List.of();
    }
  }

  private boolean isWebpImage(String imageName) {
    return imageName.toLowerCase(Locale.ROOT).endsWith(".webp");
  }

  private String buildCatalogProductName(String imageName, int catalogIndex) {
    String baseName = imageName.substring(0, imageName.length() - ".webp".length());
    if (baseName.startsWith("mogilev-product-")) {
      return "Каталожный товар " + baseName.substring("mogilev-product-".length());
    }
    if (!baseName.isEmpty() && Character.isDigit(baseName.charAt(0))) {
      return String.format(Locale.ROOT, "Каталожный товар %03d", catalogIndex);
    }

    String readableName = Arrays.stream(baseName.split("-"))
        .filter(token -> !token.isBlank())
        .map(this::formatCatalogToken)
        .collect(Collectors.joining(" "));

    if (readableName.isBlank()) {
      return String.format(Locale.ROOT, "Каталожный товар %03d", catalogIndex);
    }
    return readableName;
  }

  private String formatCatalogToken(String token) {
    if (token.chars().allMatch(Character::isDigit)) {
      return token;
    }
    if (token.length() == 1) {
      return token.toUpperCase(Locale.ROOT);
    }
    return Character.toUpperCase(token.charAt(0)) + token.substring(1);
  }

  private String resolveCatalogCategory(String imageName) {
    String normalized = imageName.toLowerCase(Locale.ROOT);
    if (containsAny(normalized, "milk", "kefir", "yogurt", "cream", "butter", "cheese", "curd", "bryndza", "ghee")) {
      return "Молочная продукция";
    }
    if (containsAny(normalized, "beef", "pork", "chicken", "turkey", "rabbit", "duck", "mince", "liver")) {
      return "Мясо и птица";
    }
    if (containsAny(normalized, "egg")) {
      return "Птица и яйца";
    }
    if (containsAny(normalized, "potato", "carrot", "onion", "cucumber", "tomato", "cabbage", "broccoli",
        "beet", "pumpkin", "radish", "zucchini", "garlic", "pepper", "cauliflower", "spinach", "dill", "parsley")) {
      return "Овощи и зелень";
    }
    if (containsAny(normalized, "apple", "pear", "plum")) {
      return "Фрукты";
    }
    if (containsAny(normalized, "strawberry", "raspberry", "blueberry", "currant", "cranberry", "lingonberry",
        "buckthorn", "cherry", "blackcurrant", "redcurrant")) {
      return "Ягоды";
    }
    if (containsAny(normalized, "honey")) {
      return "Пчеловодство";
    }
    if (containsAny(normalized, "bread", "baguette", "flour")) {
      return "Хлеб и выпечка";
    }
    if (containsAny(normalized, "rice", "millet", "buckwheat", "lentils", "beans")) {
      return "Крупы и бобовые";
    }
    if (containsAny(normalized, "juice", "water")) {
      return "Напитки";
    }
    if (containsAny(normalized, "oil")) {
      return "Масла";
    }
    return "Каталог";
  }

  private boolean containsAny(String value, String... fragments) {
    for (String fragment : fragments) {
      if (value.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private String resolveCatalogPrice(String imageName) {
    int hash = Math.floorMod(imageName.hashCode(), 1600);
    BigDecimal price = BigDecimal.valueOf(2.50 + (hash / 100.0))
        .setScale(2, RoundingMode.HALF_UP);
    return price.toPlainString();
  }

  private int resolveCatalogStock(String imageName) {
    return 30 + Math.floorMod(imageName.hashCode(), 121);
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
