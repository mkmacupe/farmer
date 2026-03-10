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
  private static final Map<String, CatalogDescriptor> CATALOG_PRODUCT_BY_BASENAME = Map.ofEntries(
      Map.entry("adyghe-cheese", new CatalogDescriptor("Сыр адыгейский 300 г", "Молочная продукция")),
      Map.entry("apple-antonovka", new CatalogDescriptor("Яблоки Антоновка 1 кг", "Фрукты")),
      Map.entry("apple-juice", new CatalogDescriptor("Сок яблочный 1 л", "Напитки")),
      Map.entry("apple-white", new CatalogDescriptor("Яблоки Белый налив 1 кг", "Фрукты")),
      Map.entry("baked-milk", new CatalogDescriptor("Молоко топлёное 1 л", "Молочная продукция")),
      Map.entry("beef", new CatalogDescriptor("Говядина охлаждённая 1 кг", "Мясо и птица")),
      Map.entry("beef-liver", new CatalogDescriptor("Печень говяжья 600 г", "Мясо и птица")),
      Map.entry("beet", new CatalogDescriptor("Свёкла столовая 1 кг", "Овощи и зелень")),
      Map.entry("bell-pepper", new CatalogDescriptor("Перец сладкий 1 кг", "Овощи и зелень")),
      Map.entry("blackcurrant", new CatalogDescriptor("Смородина чёрная 300 г", "Ягоды")),
      Map.entry("blueberry", new CatalogDescriptor("Голубика свежая 300 г", "Ягоды")),
      Map.entry("brined-cheese", new CatalogDescriptor("Сыр рассольный 300 г", "Молочная продукция")),
      Map.entry("broccoli", new CatalogDescriptor("Брокколи 400 г", "Овощи и зелень")),
      Map.entry("cabbage", new CatalogDescriptor("Капуста белокочанная 1 кг", "Овощи и зелень")),
      Map.entry("cauliflower", new CatalogDescriptor("Капуста цветная 1 кг", "Овощи и зелень")),
      Map.entry("cheese-hard", new CatalogDescriptor("Сыр твёрдый 300 г", "Молочная продукция")),
      Map.entry("cherry", new CatalogDescriptor("Вишня свежая 500 г", "Ягоды")),
      Map.entry("chicken-fillet", new CatalogDescriptor("Филе куриное 700 г", "Мясо и птица")),
      Map.entry("chicken-thigh", new CatalogDescriptor("Бедро куриное 1 кг", "Мясо и птица")),
      Map.entry("chinese-cabbage", new CatalogDescriptor("Капуста пекинская 1 кг", "Овощи и зелень")),
      Map.entry("cranberry", new CatalogDescriptor("Клюква 300 г", "Ягоды")),
      Map.entry("cream-20", new CatalogDescriptor("Сливки 20% 500 мл", "Молочная продукция")),
      Map.entry("curd-cheese", new CatalogDescriptor("Сыр творожный 200 г", "Молочная продукция")),
      Map.entry("curd-raisin", new CatalogDescriptor("Творожная масса с изюмом 250 г", "Молочная продукция")),
      Map.entry("dill", new CatalogDescriptor("Укроп свежий 100 г", "Овощи и зелень")),
      Map.entry("dill-dry", new CatalogDescriptor("Укроп сушёный 50 г", "Овощи и зелень")),
      Map.entry("duck", new CatalogDescriptor("Утка фермерская 1 кг", "Мясо и птица")),
      Map.entry("egg-c0", new CatalogDescriptor("Яйца куриные С0 10 шт", "Птица и яйца")),
      Map.entry("eggplant", new CatalogDescriptor("Баклажаны 1 кг", "Овощи и зелень")),
      Map.entry("garlic", new CatalogDescriptor("Чеснок молодой 250 г", "Овощи и зелень")),
      Map.entry("ghee", new CatalogDescriptor("Масло топлёное 250 г", "Молочная продукция")),
      Map.entry("goat-bryndza", new CatalogDescriptor("Брынза козья 250 г", "Молочная продукция")),
      Map.entry("green-lentils", new CatalogDescriptor("Чечевица зелёная 800 г", "Крупы и бобовые")),
      Map.entry("herb-soft-cheese", new CatalogDescriptor("Сыр мягкий с зеленью 200 г", "Молочная продукция")),
      Map.entry("honey-buckwheat", new CatalogDescriptor("Мёд гречишный 500 г", "Пчеловодство")),
      Map.entry("honey-linden", new CatalogDescriptor("Мёд липовый 500 г", "Пчеловодство")),
      Map.entry("linden-honey", new CatalogDescriptor("Мёд липовый 500 г", "Пчеловодство")),
      Map.entry("lingonberry", new CatalogDescriptor("Брусника 300 г", "Ягоды")),
      Map.entry("milk-2l", new CatalogDescriptor("Молоко фермерское 2 л", "Молочная продукция")),
      Map.entry("milk-whole-2l", new CatalogDescriptor("Молоко цельное 2 л", "Молочная продукция")),
      Map.entry("millet", new CatalogDescriptor("Пшено шлифованное 900 г", "Крупы и бобовые")),
      Map.entry("mixed-mince", new CatalogDescriptor("Фарш домашний 800 г", "Мясо и птица")),
      Map.entry("parsley", new CatalogDescriptor("Петрушка свежая 100 г", "Овощи и зелень")),
      Map.entry("parsley-root", new CatalogDescriptor("Корень петрушки 300 г", "Овощи и зелень")),
      Map.entry("pear", new CatalogDescriptor("Груши садовые 1 кг", "Фрукты")),
      Map.entry("pear-conference", new CatalogDescriptor("Груши Конференция 1 кг", "Фрукты")),
      Map.entry("plum", new CatalogDescriptor("Сливы садовые 1 кг", "Фрукты")),
      Map.entry("plum-tomato", new CatalogDescriptor("Томаты сливовидные 500 г", "Овощи и зелень")),
      Map.entry("pork", new CatalogDescriptor("Свинина охлаждённая 1 кг", "Мясо и птица")),
      Map.entry("pork-ribs", new CatalogDescriptor("Рёбра свиные 1 кг", "Мясо и птица")),
      Map.entry("potato-bake", new CatalogDescriptor("Картофель для запекания 2 кг", "Овощи и зелень")),
      Map.entry("potato-mash", new CatalogDescriptor("Картофель для пюре 2 кг", "Овощи и зелень")),
      Map.entry("prostokvasha", new CatalogDescriptor("Простокваша 900 мл", "Молочная продукция")),
      Map.entry("pumpkin", new CatalogDescriptor("Тыква мускатная 1 кг", "Овощи и зелень")),
      Map.entry("quail-eggs", new CatalogDescriptor("Яйца перепелиные 20 шт", "Птица и яйца")),
      Map.entry("quail-eggs-30", new CatalogDescriptor("Яйца перепелиные 30 шт", "Птица и яйца")),
      Map.entry("rabbit", new CatalogDescriptor("Кролик фермерский 1 кг", "Мясо и птица")),
      Map.entry("radish", new CatalogDescriptor("Редис свежий 300 г", "Овощи и зелень")),
      Map.entry("raspberry", new CatalogDescriptor("Малина 250 г", "Ягоды")),
      Map.entry("red-beans", new CatalogDescriptor("Фасоль красная 800 г", "Крупы и бобовые")),
      Map.entry("red-onion", new CatalogDescriptor("Лук красный 1 кг", "Овощи и зелень")),
      Map.entry("redcurrant", new CatalogDescriptor("Смородина красная 300 г", "Ягоды")),
      Map.entry("rice", new CatalogDescriptor("Рис длиннозёрный 900 г", "Крупы и бобовые")),
      Map.entry("romaine", new CatalogDescriptor("Салат ромэн 1 шт", "Овощи и зелень")),
      Map.entry("sea-buckthorn", new CatalogDescriptor("Облепиха 300 г", "Ягоды")),
      Map.entry("short-cucumber", new CatalogDescriptor("Огурцы короткоплодные 1 кг", "Овощи и зелень")),
      Map.entry("spinach", new CatalogDescriptor("Шпинат свежий 150 г", "Овощи и зелень")),
      Map.entry("strawberry", new CatalogDescriptor("Клубника 250 г", "Ягоды")),
      Map.entry("sunflower-oil", new CatalogDescriptor("Масло подсолнечное 1 л", "Масла")),
      Map.entry("sweet-cherry", new CatalogDescriptor("Черешня свежая 500 г", "Фрукты")),
      Map.entry("tomato-cherry", new CatalogDescriptor("Томаты черри 250 г", "Овощи и зелень")),
      Map.entry("turkey-breast", new CatalogDescriptor("Грудка индейки 700 г", "Мясо и птица")),
      Map.entry("turkey-fillet", new CatalogDescriptor("Филе индейки 700 г", "Мясо и птица")),
      Map.entry("washed-carrot", new CatalogDescriptor("Морковь мытая 1 кг", "Овощи и зелень")),
      Map.entry("water", new CatalogDescriptor("Вода питьевая 1 л", "Напитки")),
      Map.entry("wheat-rye-bread", new CatalogDescriptor("Хлеб пшенично-ржаной 600 г", "Хлеб и выпечка")),
      Map.entry("wholewheat-flour", new CatalogDescriptor("Мука цельнозерновая 1 кг", "Хлеб и выпечка")),
      Map.entry("yogurt-fruit", new CatalogDescriptor("Йогурт фруктовый 500 мл", "Молочная продукция")),
      Map.entry("young-beet", new CatalogDescriptor("Свёкла молодая 700 г", "Овощи и зелень")),
      Map.entry("zucchini", new CatalogDescriptor("Кабачки 1 кг", "Овощи и зелень"))
  );
  private static final String[] FALLBACK_DAIRY_BASES = {
      "Молоко деревенское",
      "Кефир био",
      "Ряженка томлёная",
      "Йогурт сливочный",
      "Творог зернёный",
      "Сыр молодой"
  };
  private static final String[] FALLBACK_DAIRY_PACKS = {"900 мл", "1 л", "500 мл", "500 г", "250 г"};
  private static final String[] FALLBACK_MEAT_BASES = {
      "Шницель куриный",
      "Гуляш говяжий",
      "Окорок свиной",
      "Фрикадельки домашние",
      "Стейк индейки",
      "Тушка утки"
  };
  private static final String[] FALLBACK_MEAT_PACKS = {"600 г", "700 г", "800 г", "1 кг", "500 г"};
  private static final String[] FALLBACK_VEGETABLE_BASES = {
      "Картофель белый",
      "Морковь хрустящая",
      "Лук шалот",
      "Капуста ранняя",
      "Огурцы тепличные",
      "Томаты мясистые"
  };
  private static final String[] FALLBACK_VEGETABLE_PACKS = {"1 кг", "2 кг", "500 г", "700 г", "1 шт"};
  private static final String[] FALLBACK_FRUIT_BASES = {
      "Яблоки медовые",
      "Груши летние",
      "Сливы янтарные",
      "Персики бархатные",
      "Абрикосы южные",
      "Нектарины сладкие"
  };
  private static final String[] FALLBACK_FRUIT_PACKS = {"1 кг", "500 г", "700 г", "250 г", "1 л"};
  private static final String[] FALLBACK_BERRY_BASES = {
      "Клубника луговая",
      "Малина отборная",
      "Голубика лесная",
      "Смородина рубиновая",
      "Клюква лесная",
      "Ежевика садовая"
  };
  private static final String[] FALLBACK_BERRY_PACKS = {"250 г", "300 г", "400 г", "500 г"};
  private static final String[] FALLBACK_BAKERY_BASES = {
      "Хлеб зерновой",
      "Булочки пшеничные",
      "Лаваш тонкий",
      "Крекеры солодовые",
      "Мука хлебопекарная",
      "Лепёшка деревенская"
  };
  private static final String[] FALLBACK_BAKERY_PACKS = {"400 г", "500 г", "600 г", "700 г", "1 кг"};
  private static final String[] FALLBACK_GRAIN_BASES = {
      "Киноа белая",
      "Перловка отборная",
      "Булгур золотистый",
      "Рис жасмин",
      "Фасоль белая",
      "Овсяные хлопья"
  };
  private static final String[] FALLBACK_GRAIN_PACKS = {"800 г", "900 г", "1 кг", "700 г"};
  private static final String[] FALLBACK_PANTRY_BASES = {
      "Мёд луговой",
      "Масло кукурузное",
      "Вода родниковая",
      "Сок облепиховый",
      "Сироп ягодный",
      "Масло горчичное"
  };
  private static final String[] FALLBACK_PANTRY_PACKS = {"500 г", "700 г", "1 л", "900 мл", "250 г"};

  private record CatalogDescriptor(String name, String category) {
  }

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
      normalizeCatalogProducts();

      demoSeeded = true;
    }
  }

  public void resetDemoSeedState() {
    synchronized (seedLock) {
      demoSeeded = false;
    }
  }

  private void seedProduct(String name, String cat, String price, int stock, String img) {
    String photoUrl = PRODUCT_IMAGE_BASE + img;
    Product existingByName = productRepository.findByNameIgnoreCase(name).orElse(null);
    Product existingByPhoto = productRepository.findByPhotoUrlIgnoreCase(photoUrl).orElse(null);
    Product existing = existingByName != null ? existingByName : existingByPhoto;
    double weight = parseWeight(name);
    double volume = parseVolume(name);
    boolean photoTakenByAnotherProduct =
        existingByPhoto != null
            && existing != null
            && existingByPhoto.getId() != null
            && !existingByPhoto.getId().equals(existing.getId());
    String normalizedPhotoUrl = photoTakenByAnotherProduct ? null : photoUrl;

    if (existing == null) {
      Product p = new Product(name, cat, name, normalizedPhotoUrl, new BigDecimal(price), stock, weight, volume);
      productRepository.save(p);
    } else {
      boolean changed = false;
      if (!name.equals(existing.getName())) {
        existing.setName(name);
        changed = true;
      }
      if (!cat.equals(existing.getCategory())) {
        existing.setCategory(cat);
        changed = true;
      }
      if (!name.equals(existing.getDescription())) {
        existing.setDescription(name);
        changed = true;
      }
      if (normalizedPhotoUrl != null && !normalizedPhotoUrl.equals(existing.getPhotoUrl())) {
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
      CatalogDescriptor descriptor = describeCatalogProduct(imageName, catalogIndex);
      seedProduct(
          descriptor.name(),
          descriptor.category(),
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

  private void normalizeCatalogProducts() {
    List<Product> products = productRepository.findAll().stream()
        .sorted(Comparator.comparing(Product::getId, Comparator.nullsLast(Long::compareTo)))
        .collect(Collectors.toList());
    if (products.isEmpty()) {
      return;
    }

    int fallbackIndex = DEMO_PRODUCT_TARGET_COUNT + 1;
    int normalizedCount = 0;
    for (Product product : products) {
      String imageName = extractImageName(product.getPhotoUrl());
      boolean supplementalByPhoto = imageName != null && !CORE_PRODUCT_IMAGES.contains(imageName);
      boolean needsRepair = supplementalByPhoto
          || String.valueOf(product.getName()).startsWith("Каталожный товар")
          || "Каталог".equalsIgnoreCase(product.getCategory());
      if (!needsRepair) {
        continue;
      }

      CatalogDescriptor descriptor = describeCatalogProduct(
          imageName != null ? imageName : "fallback-" + fallbackIndex,
          extractCatalogIndex(imageName, fallbackIndex)
      );
      fallbackIndex++;

      boolean changed = false;
      if (!descriptor.name().equals(product.getName())) {
        product.setName(descriptor.name());
        changed = true;
      }
      if (!descriptor.category().equals(product.getCategory())) {
        product.setCategory(descriptor.category());
        changed = true;
      }
      if (!descriptor.name().equals(product.getDescription())) {
        product.setDescription(descriptor.name());
        changed = true;
      }
      double weight = parseWeight(descriptor.name());
      if (product.getWeightKg() == null || Math.abs(product.getWeightKg() - weight) > 0.001) {
        product.setWeightKg(weight);
        changed = true;
      }
      double volume = parseVolume(descriptor.name());
      if (product.getVolumeM3() == null || Math.abs(product.getVolumeM3() - volume) > 0.001) {
        product.setVolumeM3(volume);
        changed = true;
      }
      if (changed) {
        productRepository.save(product);
        normalizedCount++;
      }
    }

    if (normalizedCount > 0) {
      log.info("Normalized {} demo catalog products with readable names.", normalizedCount);
    }
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

  private CatalogDescriptor describeCatalogProduct(String imageName, int catalogIndex) {
    String baseName = imageName == null
        ? ""
        : imageName.toLowerCase(Locale.ROOT).endsWith(".webp")
            ? imageName.substring(0, imageName.length() - ".webp".length())
            : imageName;
    CatalogDescriptor exactDescriptor = CATALOG_PRODUCT_BY_BASENAME.get(baseName);
    if (exactDescriptor != null) {
      return exactDescriptor;
    }

    int resolvedIndex = extractCatalogIndex(baseName, catalogIndex);
    if (baseName.isBlank() || baseName.startsWith("mogilev-product-")
        || Character.isDigit(baseName.charAt(0))) {
      return fallbackCatalogDescriptor(resolvedIndex);
    }

    CatalogDescriptor fallbackDescriptor = fallbackCatalogDescriptor(resolvedIndex);
    String category = resolveCatalogCategory(baseName, fallbackDescriptor.category());
    String readableName = applyDefaultCatalogPackage(
        Arrays.stream(baseName.split("-"))
            .filter(token -> !token.isBlank())
            .map(this::formatCatalogToken)
            .collect(Collectors.joining(" ")),
        category
    );
    if (readableName.isBlank()) {
      return fallbackDescriptor;
    }
    return new CatalogDescriptor(readableName, category);
  }

  private int extractCatalogIndex(String baseName, int fallbackIndex) {
    if (baseName != null && baseName.startsWith("mogilev-product-")) {
      try {
        return Integer.parseInt(baseName.substring("mogilev-product-".length()));
      } catch (NumberFormatException ignored) {
        return fallbackIndex;
      }
    }
    return fallbackIndex;
  }

  private CatalogDescriptor fallbackCatalogDescriptor(int catalogIndex) {
    int normalizedIndex = Math.max(0, catalogIndex - 1);
    int family = normalizedIndex % 8;
    int familyIndex = normalizedIndex / 8;
    return switch (family) {
      case 0 -> buildCatalogDescriptor("Молочная продукция", FALLBACK_DAIRY_BASES, FALLBACK_DAIRY_PACKS, familyIndex);
      case 1 -> buildCatalogDescriptor("Мясо и птица", FALLBACK_MEAT_BASES, FALLBACK_MEAT_PACKS, familyIndex);
      case 2 -> buildCatalogDescriptor("Овощи и зелень", FALLBACK_VEGETABLE_BASES, FALLBACK_VEGETABLE_PACKS, familyIndex);
      case 3 -> buildCatalogDescriptor("Фрукты", FALLBACK_FRUIT_BASES, FALLBACK_FRUIT_PACKS, familyIndex);
      case 4 -> buildCatalogDescriptor("Ягоды", FALLBACK_BERRY_BASES, FALLBACK_BERRY_PACKS, familyIndex);
      case 5 -> buildCatalogDescriptor("Хлеб и выпечка", FALLBACK_BAKERY_BASES, FALLBACK_BAKERY_PACKS, familyIndex);
      case 6 -> buildCatalogDescriptor("Крупы и бобовые", FALLBACK_GRAIN_BASES, FALLBACK_GRAIN_PACKS, familyIndex);
      default -> buildPantryCatalogDescriptor(familyIndex);
    };
  }

  private CatalogDescriptor buildCatalogDescriptor(String category, String[] bases, String[] packs, int index) {
    String base = bases[Math.floorMod(index, bases.length)];
    String pack = packs[Math.floorMod(index / bases.length, packs.length)];
    return new CatalogDescriptor(base + " " + pack, category);
  }

  private CatalogDescriptor buildPantryCatalogDescriptor(int index) {
    String base = FALLBACK_PANTRY_BASES[Math.floorMod(index, FALLBACK_PANTRY_BASES.length)];
    String pack = FALLBACK_PANTRY_PACKS[Math.floorMod(index / FALLBACK_PANTRY_BASES.length, FALLBACK_PANTRY_PACKS.length)];
    return new CatalogDescriptor(base + " " + pack, resolveCatalogCategory(base, "Напитки"));
  }

  private String formatCatalogToken(String token) {
    return switch (token.toLowerCase(Locale.ROOT)) {
      case "apple" -> "Яблоки";
      case "pear" -> "Груши";
      case "plum" -> "Сливы";
      case "beef" -> "Говядина";
      case "pork" -> "Свинина";
      case "duck" -> "Утка";
      case "rabbit" -> "Кролик";
      case "broccoli" -> "Брокколи";
      case "spinach" -> "Шпинат";
      case "water" -> "Вода";
      default -> Character.toUpperCase(token.charAt(0)) + token.substring(1);
    };
  }

  private String applyDefaultCatalogPackage(String name, String category) {
    String normalizedName = name == null ? "" : name.trim();
    if (normalizedName.isBlank() || normalizedName.matches(".*\\d.*")) {
      return normalizedName;
    }
    return switch (category) {
      case "Молочная продукция" -> normalizedName + " 500 г";
      case "Мясо и птица" -> normalizedName + " 1 кг";
      case "Овощи и зелень", "Фрукты" -> normalizedName + " 1 кг";
      case "Ягоды" -> normalizedName + " 300 г";
      case "Птица и яйца" -> normalizedName + " 10 шт";
      case "Напитки", "Масла", "Напитки и бакалея" -> normalizedName + " 1 л";
      case "Хлеб и выпечка", "Крупы и бобовые" -> normalizedName + " 1 кг";
      case "Пчеловодство" -> normalizedName + " 500 г";
      default -> normalizedName;
    };
  }

  private String resolveCatalogCategory(String imageName, String fallbackCategory) {
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
    if (containsAny(normalized, "apple", "pear", "plum", "sweet-cherry")) {
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
    return fallbackCategory;
  }

  private String extractImageName(String photoUrl) {
    if (photoUrl == null || photoUrl.isBlank()) {
      return null;
    }
    int separatorIndex = photoUrl.lastIndexOf('/');
    return separatorIndex >= 0 ? photoUrl.substring(separatorIndex + 1) : photoUrl;
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
    if (l.contains("1.5 л") || l.contains("1.5 кг")) return 1.5;
    if (l.contains("1 л") || l.contains("1 кг")) return 1.0;
    if (l.contains("2 л") || l.contains("2 кг")) return 2.0;
    if (l.contains("900 мл") || l.contains("900 г")) return 0.9;
    if (l.contains("800 г")) return 0.8;
    if (l.contains("700 г")) return 0.7;
    if (l.contains("600 г")) return 0.6;
    if (l.contains("500 мл") || l.contains("500 г")) return 0.5;
    if (l.contains("400 г")) return 0.4;
    if (l.contains("300 г")) return 0.3;
    if (l.contains("200 г") || l.contains("250 г")) return 0.25;
    if (l.contains("150 г")) return 0.15;
    if (l.contains("100 г")) return 0.1;
    if (l.contains("50 г")) return 0.05;
    if (l.contains("10 шт")) return 0.6;
    if (l.contains("20 шт")) return 0.4;
    if (l.contains("30 шт")) return 0.6;
    return 1.0;
  }

  private double parseVolume(String name) {
    String l = name.toLowerCase(Locale.ROOT);
    if (l.contains("1.5 л") || l.contains("1.5 кг")) return 0.0018;
    if (l.contains("1 л") || l.contains("1 кг")) return 0.0012;
    if (l.contains("2 л") || l.contains("2 кг")) return 0.0025;
    if (l.contains("900 мл") || l.contains("900 г")) return 0.0011;
    if (l.contains("800 г")) return 0.00095;
    if (l.contains("700 г")) return 0.00085;
    if (l.contains("600 г")) return 0.00075;
    if (l.contains("500 мл") || l.contains("500 г")) return 0.0006;
    if (l.contains("400 г")) return 0.0005;
    if (l.contains("300 г")) return 0.0004;
    if (l.contains("250 г")) return 0.0003;
    if (l.contains("200 г")) return 0.00025;
    if (l.contains("150 г")) return 0.0002;
    if (l.contains("100 г")) return 0.00015;
    if (l.contains("50 г")) return 0.0001;
    return 0.001;
  }

  private String seededPassword(String username) {
    return SEEDED_USER_PASSWORDS.get(username);
  }
}
