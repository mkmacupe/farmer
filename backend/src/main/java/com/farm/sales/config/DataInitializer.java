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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {
  private static final String PRODUCT_IMAGE_BASE = "/images/products/";

  private static String image(String filename) {
    return PRODUCT_IMAGE_BASE + filename;
  }

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final PasswordEncoder passwordEncoder;
  private final String demoPassword;

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
    String normalizedDemoPassword = validateDemoPassword();
    archiveLegacyDirectorUser();

    User mogilevkhimDirector = createUserIfMissing(
        "mogilevkhim",
        "Олег Курилин",
        "+375291948265",
        "ОАО \"Могилевхимволокно\"",
        Role.DIRECTOR,
        normalizedDemoPassword
    );
    User mogilevliftDirector = createUserIfMissing(
        "mogilevlift",
        "Руслан Страхар",
        "+375336521874",
        "ОАО \"Могилевлифтмаш\"",
        Role.DIRECTOR,
        normalizedDemoPassword
    );
    User babushkinaDirector = createUserIfMissing(
        "babushkina",
        "Эдуард Орешко",
        "+375447318502",
        "ОАО \"Бабушкина крынка\"",
        Role.DIRECTOR,
        normalizedDemoPassword
    );
    createUserIfMissing("manager", "Менеджер отдела сбыта", "+375290000002", null, Role.MANAGER, normalizedDemoPassword);
    createUserIfMissing("logistician", "Логист", "+375290000003", null, Role.LOGISTICIAN, normalizedDemoPassword);
    createUserIfMissing("driver1", "Водитель 1", "+375290000005", null, Role.DRIVER, normalizedDemoPassword);
    createUserIfMissing("driver2", "Водитель 2", "+375290000006", null, Role.DRIVER, normalizedDemoPassword);
    createUserIfMissing("driver3", "Водитель 3", "+375290000007", null, Role.DRIVER, normalizedDemoPassword);

    resetDemoAddress(mogilevkhimDirector, "Основной склад", "Могилёв, ул. Челюскинцев 105", "53.8654", "30.2905");
    resetDemoAddress(mogilevliftDirector, "Точка отгрузки", "Могилёв, пр-т Мира 42", "53.8948", "30.3312");
    resetDemoAddress(babushkinaDirector, "Центральный магазин", "Могилёв, ул. Академика Павлова 3", "53.9342", "30.2941");

    createOrUpdateProduct(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее коровье молоко с фермы",
        image("milk.webp"),
        "2.98",
        120
    );
    createOrUpdateProduct(
        "Сыр 0.5 кг",
        "Молочная продукция",
        "Фермерский сыр ручной работы",
        image("cheese.webp"),
        "13.42",
        60
    );
    createOrUpdateProduct(
        "Мёд 0.5 кг",
        "Мёд",
        "Натуральный акациевый мёд",
        image("honey.webp"),
        "27.43",
        40
    );
    createOrUpdateProduct(
        "Томаты 1 кг",
        "Овощи",
        "Сезонные тепличные томаты",
        image("tomato.webp"),
        "11.19",
        85
    );
    createOrUpdateProduct(
        "Кефир 1 л",
        "Молочная продукция",
        "Кефир 2,5% жирности",
        image("kefir.webp"),
        "3.06",
        90
    );
    createOrUpdateProduct(
        "Йогурт натуральный 0.5 л",
        "Молочная продукция",
        "Натуральный йогурт без сахара",
        image("yogurt.webp"),
        "5.56",
        70
    );
    createOrUpdateProduct(
        "Творог 0.5 кг",
        "Молочная продукция",
        "Домашний творог",
        image("cottage-cheese.webp"),
        "22.31",
        55
    );
    createOrUpdateProduct(
        "Сметана 0.4 л",
        "Молочная продукция",
        "Сметана 20% жирности",
        image("sour-cream.webp"),
        "4.13",
        60
    );
    createOrUpdateProduct(
        "Сливочное масло 0.2 кг",
        "Молочная продукция",
        "Масло 82,5%",
        image("butter.webp"),
        "6.15",
        50
    );
    createOrUpdateProduct(
        "Яйца 10 шт",
        "Птица",
        "Домашние яйца",
        image("egg.webp"),
        "3.45",
        200
    );
    createOrUpdateProduct(
        "Курица охлаждённая 1 кг",
        "Мясо",
        "Фермерская курица",
        image("chicken.webp"),
        "7.59",
        45
    );
    createOrUpdateProduct(
        "Говядина 1 кг",
        "Мясо",
        "Мякоть для тушения",
        image("beef.webp"),
        "27.88",
        30
    );
    createOrUpdateProduct(
        "Свинина 1 кг",
        "Мясо",
        "Фермерская свинина",
        image("pork.webp"),
        "15.99",
        40
    );
    createOrUpdateProduct(
        "Картофель 5 кг",
        "Овощи",
        "Отборный картофель",
        image("potato.webp"),
        "12.15",
        120
    );
    createOrUpdateProduct(
        "Морковь 2 кг",
        "Овощи",
        "Свежая морковь",
        image("carrot.webp"),
        "2.20",
        110
    );
    createOrUpdateProduct(
        "Лук репчатый 2 кг",
        "Овощи",
        "Жёлтый лук",
        image("onion.webp"),
        "3.56",
        100
    );
    createOrUpdateProduct(
        "Огурцы 1 кг",
        "Овощи",
        "Хрустящие огурцы",
        image("cucumber.webp"),
        "11.19",
        80
    );
    createOrUpdateProduct(
        "Яблоки 1 кг",
        "Фрукты",
        "Сезонные яблоки",
        image("apple.webp"),
        "4.55",
        95
    );
    createOrUpdateProduct(
        "Груши 1 кг",
        "Фрукты",
        "Сладкие груши",
        image("pear.webp"),
        "7.62",
        70
    );
    createOrUpdateProduct(
        "Клубника 0.5 кг",
        "Фрукты",
        "Свежая клубника",
        image("strawberry.webp"),
        "5.15",
        40
    );
    createOrUpdateProduct(
        "Мёд липовый 0.5 кг",
        "Мёд",
        "Липовый мёд",
        image("honey-linden.webp"),
        "27.43",
        35
    );
    createOrUpdateProduct(
        "Хлеб ржаной 0.6 кг",
        "Хлеб",
        "Ржаной хлеб",
        image("rye-bread.webp"),
        "2.08",
        90
    );
    createOrUpdateProduct(
        "Батон 0.4 кг",
        "Хлеб",
        "Пшеничный батон",
        image("baguette.webp"),
        "1.45",
        100
    );
    createOrUpdateProduct(
        "Крупа гречневая 1 кг",
        "Крупы",
        "Гречка ядрица",
        image("buckwheat.webp"),
        "2.59",
        85
    );
    createOrUpdateProduct(
        "Рис 1 кг",
        "Крупы",
        "Рис шлифованный",
        image("rice.webp"),
        "2.59",
        90
    );
    createOrUpdateProduct(
        "Пшено 1 кг",
        "Крупы",
        "Пшено шлифованное",
        image("millet.webp"),
        "2.59",
        75
    );
    createOrUpdateProduct(
        "Сок яблочный 1 л",
        "Напитки",
        "Натуральный сок из фермерских яблок",
        image("apple-juice.webp"),
        "2.98",
        80
    );
    createOrUpdateProduct(
        "Вода питьевая артезианская 1.5 л",
        "Напитки",
        "Питьевая вода из артезианской скважины фермы",
        image("water.webp"),
        "4.37",
        150
    );
    createOrUpdateProduct(
        "Молоко 2 л",
        "Молочная продукция",
        "Молоко пастеризованное 3,2%",
        image("milk-2l.webp"),
        "5.76",
        90
    );
    createOrUpdateProduct(
        "Кефир 0.5 л",
        "Молочная продукция",
        "Кефир 2,5% жирности",
        image("kefir-05l.webp"),
        "1.63",
        140
    );
    createOrUpdateProduct(
        "Йогурт с фруктами 0.5 л",
        "Молочная продукция",
        "Йогурт с фруктовыми кусочками",
        image("yogurt-fruit.webp"),
        "5.15",
        110
    );
    createOrUpdateProduct(
        "Мёд гречишный 0.5 кг",
        "Мёд",
        "Ароматный гречишный мёд",
        image("honey-buckwheat.webp"),
        "27.43",
        25
    );
    createOrUpdateProduct(
        "Сыр твёрдый 1 кг",
        "Молочная продукция",
        "Выдержанный твёрдый сыр",
        image("cheese-hard.webp"),
        "26.64",
        35
    );
    createOrUpdateProduct(
        "Томаты черри 0.5 кг",
        "Овощи",
        "Сладкие томаты черри",
        image("tomato-cherry.webp"),
        "9.55",
        70
    );
  }

  private User createUserIfMissing(String username,
                                   String fullName,
                                   String phone,
                                   String legalEntityName,
                                   Role role,
                                   String password) {
    User existing = userRepository.findByUsername(username).orElse(null);
    if (existing == null) {
      return userRepository.save(new User(
          username,
          passwordEncoder.encode(password),
          fullName,
          phone,
          legalEntityName,
          role
      ));
    }

    boolean changed = false;
    if (!passwordEncoder.matches(password, existing.getPasswordHash())) {
      existing.setPasswordHash(passwordEncoder.encode(password));
      changed = true;
    }
    if (!fullName.equals(existing.getFullName())) {
      existing.setFullName(fullName);
      changed = true;
    }
    if (!equalsNullable(phone, existing.getPhone())) {
      existing.setPhone(phone);
      changed = true;
    }
    if (!equalsNullable(legalEntityName, existing.getLegalEntityName())) {
      existing.setLegalEntityName(legalEntityName);
      changed = true;
    }
    if (existing.getRole() != role) {
      existing.setRole(role);
      changed = true;
    }

    return changed ? userRepository.save(existing) : existing;
  }

  private void createOrUpdateProduct(String name,
                                     String category,
                                     String description,
                                     String photoUrl,
                                     String price,
                                     int stockQuantity) {
    Product existing = productRepository.findByNameIgnoreCase(name).orElse(null);
    if (existing == null) {
      productRepository.save(new Product(
          name,
          category,
          description,
          photoUrl,
          new BigDecimal(price),
          stockQuantity
      ));
      return;
    }

    boolean changed = false;
    if (!category.equals(existing.getCategory())) {
      existing.setCategory(category);
      changed = true;
    }
    if (!equalsNullable(description, existing.getDescription())) {
      existing.setDescription(description);
      changed = true;
    }
    if (!equalsNullable(photoUrl, existing.getPhotoUrl())) {
      existing.setPhotoUrl(photoUrl);
      changed = true;
    }
    BigDecimal newPrice = new BigDecimal(price);
    if (existing.getPrice() == null || existing.getPrice().compareTo(newPrice) != 0) {
      existing.setPrice(newPrice);
      changed = true;
    }
    if (existing.getStockQuantity() == null || existing.getStockQuantity() != stockQuantity) {
      existing.setStockQuantity(stockQuantity);
      changed = true;
    }

    if (changed) {
      productRepository.save(existing);
    }
  }

  private void createAddressIfMissing(User user, String label, String addressLine, String latitude, String longitude) {
    StoreAddress address = storeAddressRepository.findByUserIdAndLabelIgnoreCase(user.getId(), label)
        .orElseGet(() -> {
          StoreAddress created = new StoreAddress();
          created.setUser(user);
          created.setLabel(label);
          created.setCreatedAt(Instant.now());
          return created;
        });

    address.setAddressLine(addressLine);
    address.setLatitude(new BigDecimal(latitude));
    address.setLongitude(new BigDecimal(longitude));
    address.setUpdatedAt(Instant.now());
    storeAddressRepository.save(address);
  }

  private void resetDemoAddress(User user, String label, String addressLine, String latitude, String longitude) {
    var addresses = storeAddressRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    if (addresses.isEmpty()) {
      createAddressIfMissing(user, label, addressLine, latitude, longitude);
      return;
    }

    BigDecimal targetLatitude = new BigDecimal(latitude);
    BigDecimal targetLongitude = new BigDecimal(longitude);
    Instant now = Instant.now();

    for (StoreAddress address : addresses) {
      address.setLabel(label);
      address.setAddressLine(addressLine);
      address.setLatitude(targetLatitude);
      address.setLongitude(targetLongitude);
      address.setUpdatedAt(now);
      storeAddressRepository.save(address);
    }
  }

  private String validateDemoPassword() {
    String normalized = demoPassword == null ? "" : demoPassword.trim();
    if (normalized.isEmpty() || normalized.toLowerCase().startsWith("replace-with-")) {
      throw new IllegalStateException("Необходимо задать app.demo.password");
    }
    return normalized;
  }

  private boolean equalsNullable(String left, String right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }

  private void archiveLegacyDirectorUser() {
    User legacyDirector = userRepository.findByUsername("director").orElse(null);
    if (legacyDirector == null) {
      return;
    }

    String archivedUsername = "legacy-director-" + legacyDirector.getId();
    if (userRepository.existsByUsername(archivedUsername)) {
      archivedUsername = archivedUsername + "-" + Instant.now().getEpochSecond();
    }

    legacyDirector.setUsername(archivedUsername);
    legacyDirector.setFullName("Архивный пользователь");
    legacyDirector.setPhone(null);
    legacyDirector.setLegalEntityName(null);
    legacyDirector.setRole(Role.MANAGER);
    userRepository.save(legacyDirector);
  }

}
