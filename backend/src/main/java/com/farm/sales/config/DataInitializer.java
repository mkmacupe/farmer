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
import org.springframework.dao.DataIntegrityViolationException;
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

  private static String image(String filename) {
    return PRODUCT_IMAGE_BASE + filename;
  }

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final PasswordEncoder passwordEncoder;
  private final String demoPassword;
  private final Object seedLock = new Object();
  private volatile boolean demoSeeded;

  public DataInitializer(UserRepository userRepository,
                         ProductRepository productRepository,
                         StoreAddressRepository storeAddressRepository,
                         PasswordEncoder passwordEncoder,
                         @Value("${app.demo.password:}") String demoPassword) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.passwordEncoder = passwordEncoder;
    this.demoPassword = demoPassword;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (demoSeeded) {
      return;
    }
    synchronized (seedLock) {
      if (demoSeeded) {
        return;
      }
    validateDemoPassword();
    archiveLegacyDirectorUser();

    User mogilevkhimDirector = createUserIfMissing(
        "mogilevkhim",
        "Олег Курилин",
        "+375291948265",
        "ОАО \"Могилевхимволокно\"",
        Role.DIRECTOR,
        seededPassword("mogilevkhim")
    );
    User mogilevliftDirector = createUserIfMissing(
        "mogilevlift",
        "Руслан Страхар",
        "+375336521874",
        "ОАО \"Могилевлифтмаш\"",
        Role.DIRECTOR,
        seededPassword("mogilevlift")
    );
    User babushkinaDirector = createUserIfMissing(
        "babushkina",
        "Эдуард Орешко",
        "+375447318502",
        "ОАО \"Бабушкина крынка\"",
        Role.DIRECTOR,
        seededPassword("babushkina")
    );
    createUserIfMissing(
        "manager",
        "Менеджер отдела сбыта",
        "+375290000002",
        null,
        Role.MANAGER,
        seededPassword("manager")
    );
    createUserIfMissing(
        "logistician",
        "Логист",
        "+375290000003",
        null,
        Role.LOGISTICIAN,
        seededPassword("logistician")
    );
    createUserIfMissing(
        "driver1",
        "Водитель 1",
        "+375290000005",
        null,
        Role.DRIVER,
        seededPassword("driver1")
    );
    createUserIfMissing(
        "driver2",
        "Водитель 2",
        "+375290000006",
        null,
        Role.DRIVER,
        seededPassword("driver2")
    );
    createUserIfMissing(
        "driver3",
        "Водитель 3",
        "+375290000007",
        null,
        Role.DRIVER,
        seededPassword("driver3")
    );

    createAddressIfMissing(mogilevkhimDirector, "МХВ Точка 01", "Могилёв, ул. Челюскинцев 105", "53.8654", "30.2905");
    createAddressIfMissing(mogilevliftDirector, "МЛМ Точка 01", "Могилёв, пр-т Мира 42", "53.8948", "30.3312");
    createAddressIfMissing(babushkinaDirector, "БК Точка 01", "Могилёв, ул. Академика Павлова 3", "53.9342", "30.2941");

    createOrUpdateProduct("Молоко фермерское 1 л", "Молочная продукция", "Пастеризованное коровье молоко", image("milk.webp"), "3.20", 120);
    createOrUpdateProduct("Кефир домашний 1 л", "Молочная продукция", "Кефир 2,5% на живой закваске", image("kefir.webp"), "3.40", 95);
    createOrUpdateProduct("Ряженка 500 мл", "Молочная продукция", "Томлёная ряженка", image("kefir-05l.webp"), "2.90", 85);
    createOrUpdateProduct("Йогурт натуральный 500 мл", "Молочная продукция", "Йогурт без сахара", image("yogurt.webp"), "5.30", 75);
    createOrUpdateProduct("Йогурт с клубникой 500 мл", "Молочная продукция", "Йогурт с ягодным пюре", image("yogurt-fruit.webp"), "5.80", 70);
    createOrUpdateProduct("Творог рассыпчатый 500 г", "Молочная продукция", "Домашний творог", image("cottage-cheese.webp"), "4.90", 80);
    createOrUpdateProduct("Сметана 20% 400 г", "Молочная продукция", "Густая сметана", image("sour-cream.webp"), "4.30", 78);
    createOrUpdateProduct("Масло сливочное 82.5% 200 г", "Молочная продукция", "Натуральное масло", image("butter.webp"), "6.40", 65);
    createOrUpdateProduct("Сыр полутвёрдый 500 г", "Молочная продукция", "Полутвёрдый сыр", image("cheese.webp"), "13.90", 55);
    createOrUpdateProduct("Сыр выдержанный 700 г", "Молочная продукция", "Выдержанный фермерский сыр", image("cheese-hard.webp"), "18.60", 45);
    createOrUpdateProduct("Яйца куриные С1 10 шт", "Птица и яйца", "Яйца от кур свободного выгула", image("egg.webp"), "3.70", 180);
    createOrUpdateProduct("Курица фермерская охлаждённая 1 кг", "Мясо и птица", "Охлаждённая курица", image("chicken.webp"), "8.10", 50);
    createOrUpdateProduct("Говядина лопатка 1 кг", "Мясо и птица", "Говяжья лопатка", image("beef.webp"), "16.80", 38);
    createOrUpdateProduct("Свинина окорок 1 кг", "Мясо и птица", "Нежирный свиной окорок", image("pork.webp"), "12.40", 46);
    createOrUpdateProduct("Картофель молодой 2 кг", "Овощи", "Свежий молодой картофель", image("potato.webp"), "5.40", 140);
    createOrUpdateProduct("Морковь сладкая 1 кг", "Овощи", "Сочная морковь", image("carrot.webp"), "2.30", 120);
    createOrUpdateProduct("Лук репчатый 1 кг", "Овощи", "Лук с плотной луковицей", image("onion.webp"), "2.10", 115);
    createOrUpdateProduct("Огурцы грунтовые 1 кг", "Овощи", "Хрустящие огурцы", image("cucumber.webp"), "4.90", 90);
    createOrUpdateProduct("Томаты розовые 1 кг", "Овощи", "Мясистые розовые томаты", image("tomato.webp"), "5.60", 85);
    createOrUpdateProduct("Яблоки садовые 1 кг", "Фрукты", "Сладко-кислые яблоки", image("apple.webp"), "3.20", 110);
    createOrUpdateProduct("Мёд цветочный 500 г", "Пчеловодство", "Цветочный мёд", image("honey.webp"), "11.90", 42);
    createOrUpdateProduct("Хлеб ржаной на закваске 600 г", "Хлеб и выпечка", "Ржаной хлеб", image("rye-bread.webp"), "2.70", 95);
    createOrUpdateProduct("Батон деревенский 400 г", "Хлеб и выпечка", "Мягкий пшеничный батон", image("baguette.webp"), "2.10", 100);
    createOrUpdateProduct("Гречка ядрица 1 кг", "Крупы", "Отборная гречневая крупа", image("buckwheat.webp"), "3.00", 90);
    createOrUpdateProduct("Рис бурый 1 кг", "Крупы", "Цельнозерновой бурый рис", image("rice.webp"), "3.40", 82);
    createOrUpdateProduct("Сок яблочный прямого отжима 1 л", "Напитки", "Нефильтрованный сок", image("apple-juice.webp"), "3.60", 88);

    demoSeeded = true;
    }
  }

  public void seedDemoData() {
    run();
  }

  private User createUserIfMissing(String username, String fullName, String phone, String legalEntityName, Role role, String password) {
    User existing = userRepository.findByUsername(username).orElse(null);
    if (existing == null) {
      return userRepository.save(new User(username, passwordEncoder.encode(password), fullName, phone, legalEntityName, role));
    }
    return existing;
  }

  private void createOrUpdateProduct(String name, String category, String description, String photoUrl, String price, int stockQuantity) {
    Product existing = productRepository.findByNameIgnoreCase(name).orElse(null);
    double weight = parseWeight(name);
    double volume = parseVolume(name);
    
    if (existing == null) {
      Product product = new Product(name, category, description, photoUrl, new BigDecimal(price), stockQuantity, weight, volume);
      productRepository.save(product);
      return;
    }

    boolean changed = false;
    if (existing.getWeightKg() == null || Math.abs(existing.getWeightKg() - weight) > 0.001) {
      existing.setWeightKg(weight);
      changed = true;
    }
    if (existing.getVolumeM3() == null || Math.abs(existing.getVolumeM3() - volume) > 0.001) {
      existing.setVolumeM3(volume);
      changed = true;
    }
    if (changed) {
      productRepository.save(existing);
    }
  }

  private double parseWeight(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("1 л") || lower.contains("1 кг")) return 1.0;
    if (lower.contains("2 л") || lower.contains("2 кг")) return 2.0;
    if (lower.contains("500 мл") || lower.contains("500 г")) return 0.5;
    if (lower.contains("400 г")) return 0.4;
    if (lower.contains("200 г") || lower.contains("250 г")) return 0.25;
    if (lower.contains("10 шт")) return 0.6;
    if (lower.contains("600 г") || lower.contains("700 г")) return 0.7;
    return 1.0;
  }

  private double parseVolume(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.contains("1 л") || lower.contains("1 кг")) return 0.0012;
    if (lower.contains("2 л") || lower.contains("2 кг")) return 0.0025;
    if (lower.contains("500 мл") || lower.contains("500 г")) return 0.0006;
    return 0.001;
  }

  private void createAddressIfMissing(User user, String label, String addressLine, String latitude, String longitude) {
    if (storeAddressRepository.existsByUserIdAndLabelIgnoreCase(user.getId(), label)) return;
    StoreAddress created = new StoreAddress();
    created.setUser(user);
    created.setLabel(label);
    created.setAddressLine(addressLine);
    created.setLatitude(new BigDecimal(latitude));
    created.setLongitude(new BigDecimal(longitude));
    created.setCreatedAt(Instant.now());
    created.setUpdatedAt(Instant.now());
    storeAddressRepository.save(created);
  }

  private void validateDemoPassword() {
    if (demoPassword == null || demoPassword.trim().isEmpty()) {
      throw new IllegalStateException("app.demo.password is required");
    }
  }

  private String seededPassword(String username) {
    return SEEDED_USER_PASSWORDS.get(username);
  }

  private void archiveLegacyDirectorUser() {}
  private boolean equalsNullable(Object a, Object b) { return (a == null && b == null) || (a != null && a.equals(b)); }
}
