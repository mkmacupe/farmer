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
  private static final int DEMO_PRODUCTS_LIMIT = 20;

  private static String image(String filename) {
    return PRODUCT_IMAGE_BASE + filename;
  }

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final PasswordEncoder passwordEncoder;
  private final String demoPassword;
  private int seededProductsCount;

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
    seededProductsCount = 0;

    createOrUpdateProduct(
        "Молоко фермерское 1 л",
        "Молочная продукция",
        "Пастеризованное коровье молоко от локальной фермы",
        image("milk.webp"),
        "3.20",
        120
    );
    createOrUpdateProduct(
        "Кефир домашний 1 л",
        "Молочная продукция",
        "Кефир 2,5% на живой закваске",
        image("kefir.webp"),
        "3.40",
        95
    );
    createOrUpdateProduct(
        "Ряженка 500 мл",
        "Молочная продукция",
        "Томлёная ряженка из цельного молока",
        image("kefir-05l.webp"),
        "2.90",
        85
    );
    createOrUpdateProduct(
        "Йогурт натуральный 500 мл",
        "Молочная продукция",
        "Йогурт без сахара и добавок",
        image("yogurt.webp"),
        "5.30",
        75
    );
    createOrUpdateProduct(
        "Йогурт с клубникой 500 мл",
        "Молочная продукция",
        "Йогурт с ягодным пюре фермерской клубники",
        image("yogurt-fruit.webp"),
        "5.80",
        70
    );
    createOrUpdateProduct(
        "Творог рассыпчатый 500 г",
        "Молочная продукция",
        "Домашний творог средней жирности",
        image("cottage-cheese.webp"),
        "4.90",
        80
    );
    createOrUpdateProduct(
        "Сметана 20% 400 г",
        "Молочная продукция",
        "Густая сметана из фермерских сливок",
        image("sour-cream.webp"),
        "4.30",
        78
    );
    createOrUpdateProduct(
        "Масло сливочное 82.5% 200 г",
        "Молочная продукция",
        "Натуральное сливочное масло без добавок",
        image("butter.webp"),
        "6.40",
        65
    );
    createOrUpdateProduct(
        "Сыр полутвёрдый 500 г",
        "Молочная продукция",
        "Полутвёрдый сыр ручной выдержки",
        image("cheese.webp"),
        "13.90",
        55
    );
    createOrUpdateProduct(
        "Сыр выдержанный 700 г",
        "Молочная продукция",
        "Выдержанный фермерский сыр с ореховыми нотами",
        image("cheese-hard.webp"),
        "18.60",
        45
    );
    createOrUpdateProduct(
        "Яйца куриные С1 10 шт",
        "Птица и яйца",
        "Яйца от кур свободного выгула",
        image("egg.webp"),
        "3.70",
        180
    );
    createOrUpdateProduct(
        "Курица фермерская охлаждённая 1 кг",
        "Мясо и птица",
        "Охлаждённая курица без инъекций",
        image("chicken.webp"),
        "8.10",
        50
    );
    createOrUpdateProduct(
        "Говядина лопатка 1 кг",
        "Мясо и птица",
        "Говяжья лопатка для тушения и запекания",
        image("beef.webp"),
        "16.80",
        38
    );
    createOrUpdateProduct(
        "Свинина окорок 1 кг",
        "Мясо и птица",
        "Нежирный свиной окорок фермерского откорма",
        image("pork.webp"),
        "12.40",
        46
    );
    createOrUpdateProduct(
        "Картофель молодой 2 кг",
        "Овощи",
        "Свежий молодой картофель из хозяйства",
        image("potato.webp"),
        "5.40",
        140
    );
    createOrUpdateProduct(
        "Морковь сладкая 1 кг",
        "Овощи",
        "Сочная морковь нового урожая",
        image("carrot.webp"),
        "2.30",
        120
    );
    createOrUpdateProduct(
        "Лук репчатый 1 кг",
        "Овощи",
        "Лук с плотной луковицей и мягкой остротой",
        image("onion.webp"),
        "2.10",
        115
    );
    createOrUpdateProduct(
        "Огурцы грунтовые 1 кг",
        "Овощи",
        "Хрустящие огурцы с открытого грунта",
        image("cucumber.webp"),
        "4.90",
        90
    );
    createOrUpdateProduct(
        "Томаты розовые 1 кг",
        "Овощи",
        "Мясистые розовые томаты",
        image("tomato.webp"),
        "5.60",
        85
    );
    createOrUpdateProduct(
        "Томаты черри 500 г",
        "Овощи",
        "Сладкие черри в кистях",
        image("tomato-cherry.webp"),
        "4.70",
        76
    );
    createOrUpdateProduct(
        "Яблоки садовые 1 кг",
        "Фрукты",
        "Сладко-кислые яблоки местных садов",
        image("apple.webp"),
        "3.20",
        110
    );
    createOrUpdateProduct(
        "Груши десертные 1 кг",
        "Фрукты",
        "Ароматные груши мягкой спелости",
        image("pear.webp"),
        "4.10",
        84
    );
    createOrUpdateProduct(
        "Клубника свежая 500 г",
        "Ягоды",
        "Сезонная клубника утреннего сбора",
        image("strawberry.webp"),
        "6.40",
        60
    );
    createOrUpdateProduct(
        "Мёд цветочный 500 г",
        "Пчеловодство",
        "Цветочный мёд с летних лугов",
        image("honey.webp"),
        "11.90",
        42
    );
    createOrUpdateProduct(
        "Пыльца цветочная 100 г",
        "Пчеловодство",
        "Высушенная пыльца с пасеки",
        image("honey-linden.webp"),
        "7.20",
        36
    );
    createOrUpdateProduct(
        "Прополис натуральный 50 г",
        "Пчеловодство",
        "Натуральный прополис в гранулах",
        image("honey-buckwheat.webp"),
        "8.50",
        34
    );
    createOrUpdateProduct(
        "Хлеб ржаной на закваске 600 г",
        "Хлеб и выпечка",
        "Ржаной хлеб длительной ферментации",
        image("rye-bread.webp"),
        "2.70",
        95
    );
    createOrUpdateProduct(
        "Батон деревенский 400 г",
        "Хлеб и выпечка",
        "Мягкий пшеничный батон на молочной сыворотке",
        image("baguette.webp"),
        "2.10",
        100
    );
    createOrUpdateProduct(
        "Гречка ядрица 1 кг",
        "Крупы",
        "Отборная гречневая крупа",
        image("buckwheat.webp"),
        "3.00",
        90
    );
    createOrUpdateProduct(
        "Рис бурый 1 кг",
        "Крупы",
        "Цельнозерновой бурый рис",
        image("rice.webp"),
        "3.40",
        82
    );
    createOrUpdateProduct(
        "Пшено шлифованное 1 кг",
        "Крупы",
        "Пшено для каш и гарниров",
        image("millet.webp"),
        "2.80",
        78
    );
    createOrUpdateProduct(
        "Сок яблочный прямого отжима 1 л",
        "Напитки",
        "Нефильтрованный сок из садовых яблок",
        image("apple-juice.webp"),
        "3.60",
        88
    );
    createOrUpdateProduct(
        "Квас хлебный фермерский 1 л",
        "Напитки",
        "Натуральный квас на ржаном солоде",
        image("water.webp"),
        "2.90",
        92
    );
    createOrUpdateProduct(
        "Айран фермерский 1 л",
        "Напитки",
        "Освежающий кисломолочный напиток",
        image("milk-2l.webp"),
        "3.30",
        80
    );
    createOrUpdateProduct(
        "Брынза козья 300 г",
        "Молочная продукция",
        "Мягкая брынза из козьего молока",
        image("goat-bryndza.webp"),
        "7.90",
        48
    );
    createOrUpdateProduct(
        "Сыр мягкий с травами 250 г",
        "Молочная продукция",
        "Сливочный сыр с укропом и петрушкой",
        image("herb-soft-cheese.webp"),
        "6.20",
        52
    );
    createOrUpdateProduct(
        "Топлёное масло гхи 200 г",
        "Молочная продукция",
        "Очищенное масло для жарки и выпечки",
        image("ghee.webp"),
        "7.10",
        44
    );
    createOrUpdateProduct(
        "Индейка филе 1 кг",
        "Мясо и птица",
        "Нежное филе индейки без кожи",
        image("turkey-fillet.webp"),
        "12.90",
        40
    );
    createOrUpdateProduct(
        "Утка домашняя 1 кг",
        "Мясо и птица",
        "Домашняя утка для запекания",
        image("duck.webp"),
        "13.40",
        28
    );
    createOrUpdateProduct(
        "Кролик фермерский 1 кг",
        "Мясо и птица",
        "Диетическое мясо молодого кролика",
        image("rabbit.webp"),
        "15.20",
        24
    );
    createOrUpdateProduct(
        "Перепелиные яйца 20 шт",
        "Птица и яйца",
        "Набор свежих перепелиных яиц",
        image("quail-eggs.webp"),
        "4.60",
        70
    );
    createOrUpdateProduct(
        "Свекла столовая 1 кг",
        "Овощи",
        "Сладкая свекла с плотной мякотью",
        image("beet.webp"),
        "2.00",
        105
    );
    createOrUpdateProduct(
        "Капуста белокочанная 1 кг",
        "Овощи",
        "Хрустящая белокочанная капуста",
        image("cabbage.webp"),
        "1.80",
        110
    );
    createOrUpdateProduct(
        "Капуста цветная 1 шт",
        "Овощи",
        "Плотный кочан цветной капусты",
        image("cauliflower.webp"),
        "3.90",
        58
    );
    createOrUpdateProduct(
        "Брокколи свежая 500 г",
        "Овощи",
        "Нежные соцветия брокколи",
        image("broccoli.webp"),
        "4.30",
        54
    );
    createOrUpdateProduct(
        "Кабачки молодые 1 кг",
        "Овощи",
        "Молодые кабачки с тонкой кожицей",
        image("zucchini.webp"),
        "3.00",
        86
    );
    createOrUpdateProduct(
        "Баклажаны 1 кг",
        "Овощи",
        "Баклажаны тепличного выращивания",
        image("eggplant.webp"),
        "4.80",
        72
    );
    createOrUpdateProduct(
        "Перец сладкий 1 кг",
        "Овощи",
        "Мясистый сладкий перец разных цветов",
        image("bell-pepper.webp"),
        "5.50",
        68
    );
    createOrUpdateProduct(
        "Тыква мускатная 1 кг",
        "Овощи",
        "Сладкая мускатная тыква для каш и запекания",
        image("pumpkin.webp"),
        "2.70",
        75
    );
    createOrUpdateProduct(
        "Чеснок молодой 300 г",
        "Овощи",
        "Ароматный молодой чеснок",
        image("garlic.webp"),
        "3.20",
        64
    );
    createOrUpdateProduct(
        "Редис 500 г",
        "Овощи",
        "Сочный редис с лёгкой остротой",
        image("radish.webp"),
        "2.40",
        82
    );
    createOrUpdateProduct(
        "Салат ромэн 1 шт",
        "Зелень",
        "Хрустящий салат ромэн",
        image("romaine.webp"),
        "2.60",
        66
    );
    createOrUpdateProduct(
        "Шпинат свежий 200 г",
        "Зелень",
        "Молодые листья шпината",
        image("spinach.webp"),
        "2.80",
        62
    );
    createOrUpdateProduct(
        "Укроп свежий 100 г",
        "Зелень",
        "Пучок ароматного укропа",
        image("dill.webp"),
        "1.20",
        96
    );
    createOrUpdateProduct(
        "Петрушка свежая 100 г",
        "Зелень",
        "Свежая листовая петрушка",
        image("parsley.webp"),
        "1.20",
        94
    );
    createOrUpdateProduct(
        "Вишня 500 г",
        "Ягоды",
        "Спелая кисло-сладкая вишня",
        image("cherry.webp"),
        "5.90",
        50
    );
    createOrUpdateProduct(
        "Черешня 500 г",
        "Ягоды",
        "Сладкая красная черешня",
        image("sweet-cherry.webp"),
        "6.70",
        46
    );
    createOrUpdateProduct(
        "Слива синяя 1 кг",
        "Фрукты",
        "Синяя слива с сочной мякотью",
        image("plum.webp"),
        "4.60",
        70
    );
    createOrUpdateProduct(
        "Малина 300 г",
        "Ягоды",
        "Малина деликатного сбора",
        image("raspberry.webp"),
        "6.30",
        38
    );
    createOrUpdateProduct(
        "Черника 300 г",
        "Ягоды",
        "Лесная черника от фермерского кооператива",
        image("blueberry.webp"),
        "6.80",
        36
    );
    createOrUpdateProduct(
        "Смородина чёрная 300 г",
        "Ягоды",
        "Чёрная смородина насыщенного вкуса",
        image("blackcurrant.webp"),
        "5.40",
        44
    );
    createOrUpdateProduct(
        "Облепиха 300 г",
        "Ягоды",
        "Облепиха для морсов и чаёв",
        image("sea-buckthorn.webp"),
        "5.20",
        40
    );
    createOrUpdateProduct(
        "Фасоль красная 800 г",
        "Бобовые",
        "Сухая красная фасоль нового урожая",
        image("red-beans.webp"),
        "3.90",
        74
    );
    createOrUpdateProduct(
        "Чечевица зелёная 800 г",
        "Бобовые",
        "Зелёная чечевица для супов и салатов",
        image("green-lentils.webp"),
        "4.10",
        72
    );
    createOrUpdateProduct(
        "Масло подсолнечное холодного отжима 750 мл",
        "Масла",
        "Нерафинированное масло из местных семян",
        image("sunflower-oil.webp"),
        "6.50",
        58
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
    if (seededProductsCount >= DEMO_PRODUCTS_LIMIT) {
      // Do not delete existing products when demo seed exceeds the limit.
      // Legacy rows can already be referenced by stock movements/orders.
      return;
    }
    seededProductsCount++;

    Product existing = productRepository.findByNameIgnoreCase(name).orElse(null);
    String resolvedPhotoUrl = resolveDemoPhotoUrl(name, photoUrl, existing == null ? null : existing.getId());
    if (existing == null) {
      saveNewProductWithPhotoFallback(
          name,
          category,
          description,
          resolvedPhotoUrl,
          price,
          stockQuantity
      );
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
    if (!equalsNullable(resolvedPhotoUrl, existing.getPhotoUrl())) {
      existing.setPhotoUrl(resolvedPhotoUrl);
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
      try {
        productRepository.save(existing);
      } catch (DataIntegrityViolationException ex) {
        if (existing.getPhotoUrl() == null) {
          throw ex;
        }
        log.warn(
            "Duplicate product photo URL '{}' detected during demo seed for '{}'; clearing photo to continue startup.",
            existing.getPhotoUrl(),
            name
        );
        existing.setPhotoUrl(null);
        productRepository.save(existing);
      }
    }
  }

  private void saveNewProductWithPhotoFallback(String name,
                                               String category,
                                               String description,
                                               String photoUrl,
                                               String price,
                                               int stockQuantity) {
    Product product = new Product(
        name,
        category,
        description,
        photoUrl,
        new BigDecimal(price),
        stockQuantity
    );

    try {
      productRepository.save(product);
    } catch (DataIntegrityViolationException ex) {
      if (photoUrl == null) {
        throw ex;
      }
      log.warn(
          "Duplicate product photo URL '{}' detected while creating demo product '{}'; continuing with null photo.",
          photoUrl,
          name
      );
      product.setPhotoUrl(null);
      productRepository.save(product);
    }
  }

  private String resolveDemoPhotoUrl(String productName, String photoUrl, Long currentProductId) {
    if (photoUrl == null || photoUrl.isBlank()) {
      return null;
    }

    boolean alreadyUsed = currentProductId == null
        ? productRepository.existsByPhotoUrlIgnoreCase(photoUrl)
        : productRepository.existsByPhotoUrlIgnoreCaseAndIdNot(photoUrl, currentProductId);

    if (!alreadyUsed) {
      return photoUrl;
    }

    log.warn(
        "Product photo URL '{}' is already used by another product; demo seed for '{}' will continue without photo.",
        photoUrl,
        productName
    );
    return null;
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
