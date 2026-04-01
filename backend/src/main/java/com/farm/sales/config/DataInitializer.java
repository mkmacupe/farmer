package com.farm.sales.config;

import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class DataInitializer implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
  private static final String PRODUCT_IMAGE_BASE = "/images/products/";
  private static final String CATALOG_PRODUCT_IMAGES_RESOURCE = "/catalog-product-images.txt";
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
      Map.entry("zucchini", new CatalogDescriptor("Кабачки 1 кг", "Овощи и зелень")),
      Map.entry("1-10-30qe2l", new CatalogDescriptor("Яйца куриные отборные 10 шт", "Птица и яйца")),
      Map.entry("1-13bsrlt", new CatalogDescriptor("Томаты красные 1 кг", "Овощи")),
      Map.entry("1-15anhlx", new CatalogDescriptor("Лук белый 1 кг", "Овощи")),
      Map.entry("1-16w6dop", new CatalogDescriptor("Курица домашняя 1 кг", "Мясо и птица")),
      Map.entry("1-1ism8yi", new CatalogDescriptor("Говядина фермерская 1 кг", "Мясо и птица")),
      Map.entry("1-dfapvz", new CatalogDescriptor("Морковь свежая 1 кг", "Овощи")),
      Map.entry("1-j0s2dk", new CatalogDescriptor("Свинина фермерская 1 кг", "Мясо и птица")),
      Map.entry("1-n7ve3", new CatalogDescriptor("Молоко пастеризованное 1 л", "Молочная продукция")),
      Map.entry("1-wlcdb1", new CatalogDescriptor("Кефир 2.5% 1 л", "Молочная продукция")),
      Map.entry("2-ew7mzr", new CatalogDescriptor("Картофель отборный 2 кг", "Овощи")),
      Map.entry("20-400-jzg17w", new CatalogDescriptor("Сметана деревенская 20% 400 г", "Молочная продукция")),
      Map.entry("500-1386zma", new CatalogDescriptor("Сыр фермерский 500 г", "Молочная продукция")),
      Map.entry("500-16nazls", new CatalogDescriptor("Творог зернёный 500 г", "Молочная продукция")),
      Map.entry("500-bs4v1c", new CatalogDescriptor("Йогурт классический 500 мл", "Молочная продукция")),
      Map.entry("82-5-200-1jt74k3", new CatalogDescriptor("Масло крестьянское 82.5% 200 г", "Молочная продукция")),
      Map.entry("mogilev-product-101", new CatalogDescriptor("Молоко пастеризованное 3.2% 1 л", "Молочная продукция")),
      Map.entry("mogilev-product-102", new CatalogDescriptor("Молоко обезжиренное 1 л", "Молочная продукция")),
      Map.entry("mogilev-product-103", new CatalogDescriptor("Кефир 3.2% 1 л", "Молочная продукция")),
      Map.entry("mogilev-product-104", new CatalogDescriptor("Кефир термостатный 500 мл", "Молочная продукция")),
      Map.entry("mogilev-product-105", new CatalogDescriptor("Йогурт питьевой черника 330 мл", "Молочная продукция")),
      Map.entry("mogilev-product-106", new CatalogDescriptor("Йогурт питьевой малина 330 мл", "Молочная продукция")),
      Map.entry("mogilev-product-107", new CatalogDescriptor("Творог 9% 300 г", "Молочная продукция")),
      Map.entry("mogilev-product-108", new CatalogDescriptor("Творог 5% 300 г", "Молочная продукция")),
      Map.entry("mogilev-product-109", new CatalogDescriptor("Сметана 15% 400 г", "Молочная продукция")),
      Map.entry("mogilev-product-110", new CatalogDescriptor("Сливки 33% 300 мл", "Молочная продукция")),
      Map.entry("mogilev-product-111", new CatalogDescriptor("Масло сливочное 72.5% 200 г", "Молочная продукция")),
      Map.entry("mogilev-product-112", new CatalogDescriptor("Сыр фермерский молодой 400 г", "Молочная продукция")),
      Map.entry("mogilev-product-113", new CatalogDescriptor("Сыр козий мягкий 200 г", "Молочная продукция")),
      Map.entry("mogilev-product-114", new CatalogDescriptor("Брынза коровья 350 г", "Молочная продукция")),
      Map.entry("mogilev-product-115", new CatalogDescriptor("Рикотта фермерская 250 г", "Молочная продукция")),
      Map.entry("mogilev-product-116", new CatalogDescriptor("Простокваша 500 мл", "Молочная продукция")),
      Map.entry("mogilev-product-117", new CatalogDescriptor("Пахта 500 мл", "Молочная продукция")),
      Map.entry("mogilev-product-118", new CatalogDescriptor("Сыворотка молочная 1 л", "Молочная продукция")),
      Map.entry("mogilev-product-119", new CatalogDescriptor("Топлёное молоко 500 мл", "Молочная продукция")),
      Map.entry("mogilev-product-120", new CatalogDescriptor("Десерт творожный ваниль 180 г", "Молочная продукция")),
      Map.entry("mogilev-product-121", new CatalogDescriptor("Куриные крылья 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-122", new CatalogDescriptor("Куриная голень 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-123", new CatalogDescriptor("Куриные сердечки 500 г", "Мясо и птица")),
      Map.entry("mogilev-product-124", new CatalogDescriptor("Куриная печень 500 г", "Мясо и птица")),
      Map.entry("mogilev-product-125", new CatalogDescriptor("Фарш куриный 700 г", "Мясо и птица")),
      Map.entry("mogilev-product-126", new CatalogDescriptor("Индейка бедро 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-127", new CatalogDescriptor("Индейка голень 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-128", new CatalogDescriptor("Фарш индейки 700 г", "Мясо и птица")),
      Map.entry("mogilev-product-129", new CatalogDescriptor("Свиной карбонад 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-130", new CatalogDescriptor("Свиная шея 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-131", new CatalogDescriptor("Свиная лопатка 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-132", new CatalogDescriptor("Свиная грудинка 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-133", new CatalogDescriptor("Говядина тазобедренная часть 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-134", new CatalogDescriptor("Говядина ребро 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-135", new CatalogDescriptor("Говяжий фарш 700 г", "Мясо и птица")),
      Map.entry("mogilev-product-136", new CatalogDescriptor("Телятина вырезка 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-137", new CatalogDescriptor("Баранина лопатка 1 кг", "Мясо и птица")),
      Map.entry("mogilev-product-138", new CatalogDescriptor("Колбаски домашние свиные 600 г", "Полуфабрикаты")),
      Map.entry("mogilev-product-139", new CatalogDescriptor("Купаты куриные 600 г", "Полуфабрикаты")),
      Map.entry("mogilev-product-140", new CatalogDescriptor("Пельмени фермерские 800 г", "Полуфабрикаты")),
      Map.entry("mogilev-product-141", new CatalogDescriptor("Яйца куриные С2 10 шт", "Птица и яйца")),
      Map.entry("mogilev-product-142", new CatalogDescriptor("Яйца куриные отборные 15 шт", "Птица и яйца")),
      Map.entry("mogilev-product-143", new CatalogDescriptor("Яйца домашние 20 шт", "Птица и яйца")),
      Map.entry("mogilev-product-144", new CatalogDescriptor("Хлеб зерновой 600 г", "Хлеб и выпечка")),
      Map.entry("mogilev-product-145", new CatalogDescriptor("Булка с отрубями 350 г", "Хлеб и выпечка")),
      Map.entry("mogilev-product-146", new CatalogDescriptor("Багет цельнозерновой 300 г", "Хлеб и выпечка")),
      Map.entry("mogilev-product-147", new CatalogDescriptor("Лепёшка ржаная 250 г", "Хлеб и выпечка")),
      Map.entry("mogilev-product-148", new CatalogDescriptor("Сухари пшеничные 200 г", "Хлеб и выпечка")),
      Map.entry("mogilev-product-149", new CatalogDescriptor("Мука ржаная 1 кг", "Крупы")),
      Map.entry("mogilev-product-150", new CatalogDescriptor("Мука пшеничная высший сорт 1 кг", "Крупы")),
      Map.entry("mogilev-product-151", new CatalogDescriptor("Мука овсяная 800 г", "Крупы")),
      Map.entry("mogilev-product-152", new CatalogDescriptor("Крупа перловая 1 кг", "Крупы")),
      Map.entry("mogilev-product-153", new CatalogDescriptor("Крупа ячневая 1 кг", "Крупы")),
      Map.entry("mogilev-product-154", new CatalogDescriptor("Овсяные хлопья 800 г", "Крупы")),
      Map.entry("mogilev-product-155", new CatalogDescriptor("Манная крупа 800 г", "Крупы")),
      Map.entry("mogilev-product-156", new CatalogDescriptor("Горох колотый 800 г", "Бобовые")),
      Map.entry("mogilev-product-157", new CatalogDescriptor("Нут сушёный 800 г", "Бобовые")),
      Map.entry("mogilev-product-158", new CatalogDescriptor("Фасоль белая 800 г", "Бобовые")),
      Map.entry("mogilev-product-159", new CatalogDescriptor("Семена льна 300 г", "Крупы")),
      Map.entry("mogilev-product-160", new CatalogDescriptor("Семечки подсолнечника очищенные 400 г", "Крупы")),
      Map.entry("mogilev-product-161", new CatalogDescriptor("Картофель красный 2 кг", "Овощи")),
      Map.entry("mogilev-product-162", new CatalogDescriptor("Картофель белый 2 кг", "Овощи")),
      Map.entry("mogilev-product-163", new CatalogDescriptor("Морковь молодая 1 кг", "Овощи")),
      Map.entry("mogilev-product-164", new CatalogDescriptor("Лук шалот 500 г", "Овощи")),
      Map.entry("mogilev-product-165", new CatalogDescriptor("Лук зелёный 150 г", "Зелень")),
      Map.entry("mogilev-product-166", new CatalogDescriptor("Капуста краснокочанная 1 кг", "Овощи")),
      Map.entry("mogilev-product-167", new CatalogDescriptor("Капуста савойская 1 кг", "Овощи")),
      Map.entry("mogilev-product-168", new CatalogDescriptor("Огурцы корнишоны 500 г", "Овощи")),
      Map.entry("mogilev-product-169", new CatalogDescriptor("Огурцы тепличные 1 кг", "Овощи")),
      Map.entry("mogilev-product-170", new CatalogDescriptor("Томаты сливовидные 1 кг", "Овощи")),
      Map.entry("mogilev-product-171", new CatalogDescriptor("Томаты жёлтые 1 кг", "Овощи")),
      Map.entry("mogilev-product-172", new CatalogDescriptor("Перец острый 200 г", "Овощи")),
      Map.entry("mogilev-product-173", new CatalogDescriptor("Перец сладкий красный 1 кг", "Овощи")),
      Map.entry("mogilev-product-174", new CatalogDescriptor("Кабачки цуккини 1 кг", "Овощи")),
      Map.entry("mogilev-product-175", new CatalogDescriptor("Патиссоны 1 кг", "Овощи")),
      Map.entry("mogilev-product-176", new CatalogDescriptor("Свекла запечная 1 кг", "Овощи")),
      Map.entry("mogilev-product-177", new CatalogDescriptor("Сельдерей корневой 1 кг", "Овощи")),
      Map.entry("mogilev-product-178", new CatalogDescriptor("Сельдерей стеблевой 300 г", "Овощи")),
      Map.entry("mogilev-product-179", new CatalogDescriptor("Пастернак 700 г", "Овощи")),
      Map.entry("mogilev-product-180", new CatalogDescriptor("Редька чёрная 1 кг", "Овощи")),
      Map.entry("mogilev-product-181", new CatalogDescriptor("Репа столовая 1 кг", "Овощи")),
      Map.entry("mogilev-product-182", new CatalogDescriptor("Тыква столовая 2 кг", "Овощи")),
      Map.entry("mogilev-product-183", new CatalogDescriptor("Яблоки сладкие 1 кг", "Фрукты")),
      Map.entry("mogilev-product-184", new CatalogDescriptor("Яблоки кисло-сладкие 1 кг", "Фрукты")),
      Map.entry("mogilev-product-185", new CatalogDescriptor("Груши поздние 1 кг", "Фрукты")),
      Map.entry("mogilev-product-186", new CatalogDescriptor("Слива жёлтая 1 кг", "Фрукты")),
      Map.entry("mogilev-product-187", new CatalogDescriptor("Виноград тепличный 500 г", "Фрукты")),
      Map.entry("mogilev-product-188", new CatalogDescriptor("Крыжовник 300 г", "Ягоды")),
      Map.entry("mogilev-product-189", new CatalogDescriptor("Смесь салатная 150 г", "Зелень")),
      Map.entry("mogilev-product-190", new CatalogDescriptor("Базилик свежий 100 г", "Зелень")),
      Map.entry("mogilev-product-191", new CatalogDescriptor("Мёд разнотравье 1 кг", "Пчеловодство")),
      Map.entry("mogilev-product-192", new CatalogDescriptor("Мёд гречишный 1 кг", "Пчеловодство")),
      Map.entry("mogilev-product-193", new CatalogDescriptor("Варенье клубничное 300 г", "Консервация")),
      Map.entry("mogilev-product-194", new CatalogDescriptor("Варенье малиновое 300 г", "Консервация")),
      Map.entry("mogilev-product-195", new CatalogDescriptor("Джем яблочный 300 г", "Консервация")),
      Map.entry("mogilev-product-196", new CatalogDescriptor("Компот яблочно-грушевый 1 л", "Напитки")),
      Map.entry("mogilev-product-197", new CatalogDescriptor("Морс клюквенный 1 л", "Напитки")),
      Map.entry("mogilev-product-198", new CatalogDescriptor("Масло льняное 500 мл", "Масла")),
      Map.entry("mogilev-product-199", new CatalogDescriptor("Масло рапсовое 750 мл", "Масла")),
      Map.entry("mogilev-product-200", new CatalogDescriptor("Квашеная капуста 900 г", "Консервация"))
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

  private record DirectorSeedProfile(
      String username,
      String password,
      String fullName,
      String phone,
      String legalEntityName
  ) {
  }

  private record SeededAddressProfile(
      String label,
      String addressLine,
      String latitude,
      String longitude,
      List<String> legacyLabels
  ) {
  }

  private static final List<DirectorSeedProfile> SEEDED_DIRECTOR_PROFILES = List.of(
      new DirectorSeedProfile("diralekseev", "AlekseevFarm26", "Андрей Алексеев", "+375291000001", "ООО \"Лавка Полесья\""),
      new DirectorSeedProfile("dirbaranova", "BaranovaFarm26", "Виктория Баранова", "+375291000002", "ООО \"Сезонный Двор\""),
      new DirectorSeedProfile("dirvasilevsky", "VasilevskyFarm26", "Сергей Василевский", "+375291000003", "ООО \"Усадьба Урожая\""),
      new DirectorSeedProfile("dirgromova", "GromovaFarm26", "Елена Громова", "+375291000004", "ООО \"Зелёная Полка\""),
      new DirectorSeedProfile("dirdrozdov", "DrozdovFarm26", "Игорь Дроздов", "+375291000005", "ООО \"Фермерский Погребок\""),
      new DirectorSeedProfile("dirermakova", "ErmakovaFarm26", "Наталья Ермакова", "+375291000006", "ООО \"Солнечный Огород\""),
      new DirectorSeedProfile("dirzhuravlev", "ZhuravlevFarm26", "Павел Журавлёв", "+375291000007", "ООО \"Хуторская Лавка\""),
      new DirectorSeedProfile("dirzimina", "ZiminaFarm26", "Оксана Зимина", "+375291000008", "ООО \"Добрый Улей\""),
      new DirectorSeedProfile("dirivashkevich", "IvashkevichFarm26", "Максим Ивашкевич", "+375291000009", "ООО \"Рынок Сезона\""),
      new DirectorSeedProfile("dirkovaleva", "KovalevaFarm26", "Татьяна Ковалёва", "+375291000010", "ООО \"Кладовая Фермера\""),
      new DirectorSeedProfile("dirlavrinenko", "LavrinenkoFarm26", "Артём Лавриненко", "+375291000011", "ООО \"Берёзовый Двор\""),
      new DirectorSeedProfile("dirmelnik", "MelnikFarm26", "Марина Мельник", "+375291000012", "ООО \"Дары Слободы\""),
      new DirectorSeedProfile("dirnovik", "NovikFarm26", "Денис Новик", "+375291000013", "ООО \"Поле и Печь\""),
      new DirectorSeedProfile("dirosipova", "OsipovaFarm26", "Светлана Осипова", "+375291000014", "ООО \"Корзина Хозяина\""),
      new DirectorSeedProfile("dirparkhomenko", "ParkhomenkoFarm26", "Роман Пархоменко", "+375291000015", "ООО \"Грядка у Дома\""),
      new DirectorSeedProfile("dirrudenko", "RudenkoFarm26", "Ирина Руденко", "+375291000016", "ООО \"Золотой Амбар\""),
      new DirectorSeedProfile("dirsavchuk", "SavchukFarm26", "Кирилл Савчук", "+375291000017", "ООО \"Свежий Стан\""),
      new DirectorSeedProfile("dirtarasova", "TarasovaFarm26", "Ольга Тарасова", "+375291000018", "ООО \"Сельский Прилавок\""),
      new DirectorSeedProfile("dirulyanov", "UlyanovFarm26", "Константин Ульянов", "+375291000019", "ООО \"Луговой Ряд\""),
      new DirectorSeedProfile("dirfedorova", "FedorovaFarm26", "Вероника Федорова", "+375291000020", "ООО \"Пчелиный Берег\""),
      new DirectorSeedProfile("dirharitonov", "HaritonovFarm26", "Евгений Харитонов", "+375291000021", "ООО \"Натуральный Двор\""),
      new DirectorSeedProfile("dirsokolova", "SokolovaFarm26", "Анна Соколова", "+375291000022", "ООО \"Садовая Кладовая\""),
      new DirectorSeedProfile("dirchernov", "ChernovFarm26", "Дмитрий Чернов", "+375291000023", "ООО \"Белый Колос\""),
      new DirectorSeedProfile("dirshevtsova", "ShevtsovaFarm26", "Юлия Шевцова", "+375291000024", "ООО \"Ярмарка Урожая\""),
      new DirectorSeedProfile("diryashin", "YashinFarm26", "Николай Яшин", "+375291000025", "ООО \"Овощная Артель\""),
      new DirectorSeedProfile("dirabramova", "AbramovaFarm26", "Лариса Абрамова", "+375291000026", "ООО \"Тёплый Сеновал\""),
      new DirectorSeedProfile("dirbelyaev", "BelyaevFarm26", "Борис Беляев", "+375291000027", "ООО \"Родной Луг\""),
      new DirectorSeedProfile("dirvoronova", "VoronovaFarm26", "Галина Воронова", "+375291000028", "ООО \"Зерно и Травы\""),
      new DirectorSeedProfile("dirgrishin", "GrishinFarm26", "Степан Гришин", "+375291000029", "ООО \"Чистый Сбор\""),
      new DirectorSeedProfile("dirdanilova", "DanilovaFarm26", "Дарья Данилова", "+375291000030", "ООО \"Яблоневый Двор\"")
  );
  private static final List<SeededAddressProfile> DEFAULT_DIRECTOR_ADDRESS_PROFILES = List.of(
      new SeededAddressProfile(
          "Лавка Полесья • Центральный",
          "Могилёв, ул. Челюскинцев 105",
          "53.8654",
          "30.2905",
          List.of("Демо 01 • Центральный", "Берёзка • Центральный", "МХВ Точка 01")
      ),
      new SeededAddressProfile(
          "Сезонный Двор • Проспект Мира",
          "Могилёв, пр-т Мира 42",
          "53.8948",
          "30.3312",
          List.of("Демо 02 • Проспект Мира", "Квартал • Проспект Мира", "МЛМ Точка 01")
      ),
      new SeededAddressProfile(
          "Усадьба Урожая • Павлова",
          "Могилёв, ул. Академика Павлова 3",
          "53.9342",
          "30.2941",
          List.of("Демо 03 • Павлова", "Янтарь • Павлова", "БК Точка 01")
      )
  );

  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final PasswordEncoder passwordEncoder;
  private final Object seedLock = new Object();
  @Value("${app.demo.enabled:true}")
  private boolean demoEnabled = true;
  @Value("${app.demo.seed-on-startup:true}")
  private boolean seedOnStartup = true;
  @Value("${app.catalog.seed-on-startup:true}")
  private boolean catalogSeedOnStartup = true;
  @Value("${app.demo.product-images-dir:}")
  private String productImagesDir = "";
  private volatile boolean demoSeeded;
  private volatile boolean catalogSeeded;

  public DataInitializer(UserRepository userRepository,
                         OrderRepository orderRepository,
                         ProductRepository productRepository,
                         StoreAddressRepository storeAddressRepository,
                         PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.orderRepository = orderRepository;
    this.productRepository = productRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (demoEnabled) {
      if (!seedOnStartup) {
        log.info("Startup demo seed skipped: app.demo.seed-on-startup=false");
        return;
      }
      ensureDemoDataSeeded(true);
      return;
    }

    if (!catalogSeedOnStartup) {
      log.info("Startup catalog seed skipped: app.catalog.seed-on-startup=false");
      return;
    }
    ensureCatalogSeeded();
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

      List<User> directors = SEEDED_DIRECTOR_PROFILES.stream()
          .map(this::createDemoDirector)
          .toList();
      User firstDirector = directors.get(0);
      User secondDirector = directors.get(1);
      User thirdDirector = directors.get(2);
      
      createUserIfMissing("manager", "Менеджер", "+375290000002", null, Role.MANAGER, seededPassword("manager"));
      createUserIfMissing("logistician", "Логист", "+375290000003", null, Role.LOGISTICIAN, seededPassword("logistician"));
      createUserIfMissing("driver1", "Водитель 1", "+375290000005", null, Role.DRIVER, seededPassword("driver1"));
      createUserIfMissing("driver2", "Водитель 2", "+375290000006", null, Role.DRIVER, seededPassword("driver2"));
      createUserIfMissing("driver3", "Водитель 3", "+375290000007", null, Role.DRIVER, seededPassword("driver3"));

      if (includeDefaultAddresses) {
        createAddressIfMissing(firstDirector, DEFAULT_DIRECTOR_ADDRESS_PROFILES.get(0));
        createAddressIfMissing(secondDirector, DEFAULT_DIRECTOR_ADDRESS_PROFILES.get(1));
        createAddressIfMissing(thirdDirector, DEFAULT_DIRECTOR_ADDRESS_PROFILES.get(2));
      }

      ensureCatalogSeeded();

      demoSeeded = true;
    }
  }

  private void ensureCatalogSeeded() {
    if (catalogSeeded) return;
    synchronized (seedLock) {
      if (catalogSeeded) return;
      seedCoreProducts();
      seedCatalogProducts();
      normalizeCatalogProducts();
      catalogSeeded = true;
    }
  }

  public void resetDemoSeedState() {
    synchronized (seedLock) {
      demoSeeded = false;
      catalogSeeded = false;
    }
  }

  private void seedCoreProducts() {
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
  }

  private void seedProduct(String name, String cat, String price, int stock, String img) {
    String photoUrl = PRODUCT_IMAGE_BASE + img;
    Product existingByName = productRepository.findByNameIgnoreCase(name).orElse(null);
    Product existingByPhoto = productRepository.findByPhotoUrlIgnoreCase(photoUrl).orElse(null);
    Product existing = existingByPhoto != null
        ? existingByPhoto
        : shouldReuseSeedProductByName(existingByName, photoUrl)
            ? existingByName
            : null;
    double weight = parseWeight(name);
    double volume = parseVolume(name);
    String normalizedPhotoUrl = photoUrl;

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
      BigDecimal normalizedPrice = new BigDecimal(price);
      if (existing.getPrice() == null || existing.getPrice().compareTo(normalizedPrice) != 0) {
        existing.setPrice(normalizedPrice);
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

  private boolean shouldReuseSeedProductByName(Product existingByName, String photoUrl) {
    if (existingByName == null) {
      return false;
    }
    String existingPhotoUrl = existingByName.getPhotoUrl();
    if (existingPhotoUrl == null || existingPhotoUrl.isBlank()) {
      return true;
    }
    return existingPhotoUrl.equalsIgnoreCase(photoUrl);
  }

  private void seedCatalogProducts() {
    long existingCount = productRepository.count();
    if (existingCount >= DEMO_PRODUCT_TARGET_COUNT) {
      return;
    }

    Path productImagesDirectory = resolveProductImagesDirectory();
    List<String> availableImages = resolveSupplementalProductImages(productImagesDirectory);
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
          resolveCatalogPrice(imageName, descriptor),
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

  private List<String> resolveSupplementalProductImages(Path productImagesDirectory) {
    LinkedHashSet<String> imageNames = new LinkedHashSet<>();
    if (productImagesDirectory != null) {
      imageNames.addAll(loadSupplementalProductImages(productImagesDirectory));
    }
    imageNames.addAll(loadBundledSupplementalProductImages());
    return List.copyOf(imageNames);
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
      BigDecimal normalizedPrice = new BigDecimal(resolveCatalogPrice(
          imageName != null ? imageName : "fallback-" + fallbackIndex,
          descriptor
      ));
      if (product.getPrice() == null || product.getPrice().compareTo(normalizedPrice) != 0) {
        product.setPrice(normalizedPrice);
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
    Path configuredDirectory = resolveConfiguredProductImagesDirectory();
    if (configuredDirectory != null) {
      return configuredDirectory;
    }

    for (Path candidate : PRODUCT_IMAGE_DIR_CANDIDATES) {
      Path normalizedCandidate = candidate.toAbsolutePath().normalize();
      if (Files.isDirectory(normalizedCandidate)) {
        return normalizedCandidate;
      }
    }
    return null;
  }

  private Path resolveConfiguredProductImagesDirectory() {
    if (productImagesDir == null || productImagesDir.isBlank()) {
      return null;
    }

    try {
      Path configuredDirectory = Path.of(productImagesDir.trim()).toAbsolutePath().normalize();
      if (Files.isDirectory(configuredDirectory)) {
        return configuredDirectory;
      }

      log.warn("Configured demo product image directory {} does not exist or is not a directory.", configuredDirectory);
    } catch (InvalidPathException ex) {
      log.warn("Configured demo product image directory is invalid: {}", productImagesDir, ex);
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

  private List<String> loadBundledSupplementalProductImages() {
    try (InputStream inputStream = DataInitializer.class.getResourceAsStream(CATALOG_PRODUCT_IMAGES_RESOURCE)) {
      if (inputStream == null) {
        log.warn("Bundled catalog image list {} was not found on the classpath.", CATALOG_PRODUCT_IMAGES_RESOURCE);
        return List.of();
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
        return reader.lines()
            .map(String::trim)
            .filter(imageName -> !imageName.isBlank())
            .filter(this::isWebpImage)
            .filter(imageName -> !CORE_PRODUCT_IMAGES.contains(imageName))
            .distinct()
            .collect(Collectors.toList());
      }
    } catch (IOException ex) {
      log.warn("Failed to load bundled catalog image list from {}", CATALOG_PRODUCT_IMAGES_RESOURCE, ex);
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

  private String resolveCatalogPrice(String imageName, CatalogDescriptor descriptor) {
    String normalizedName = descriptor == null ? "" : descriptor.name().toLowerCase(Locale.ROOT);
    String normalizedCategory = descriptor == null ? "" : descriptor.category().toLowerCase(Locale.ROOT);
    String priceSeed = String.valueOf(imageName) + "|" + normalizedName + "|" + normalizedCategory;

    BigDecimal price = switch (normalizedCategory) {
      case "молочная продукция" -> {
        if (containsAny(normalizedName, "масло")) {
          yield rangedPrice(priceSeed, 6.20, 7.40, 0.20);
        }
        if (containsAny(normalizedName, "сыр", "брынза", "рикотта")) {
          yield rangedPrice(priceSeed, 7.50, 13.90, 0.40);
        }
        if (containsAny(normalizedName, "творог", "десерт")) {
          yield rangedPrice(priceSeed, 4.20, 6.20, 0.20);
        }
        if (containsAny(normalizedName, "сметан")) {
          yield rangedPrice(priceSeed, 4.10, 5.10, 0.20);
        }
        if (containsAny(normalizedName, "сливк")) {
          yield rangedPrice(priceSeed, 4.80, 6.00, 0.20);
        }
        if (containsAny(normalizedName, "йогурт")) {
          yield rangedPrice(priceSeed, 3.60, 5.60, 0.20);
        }
        yield rangedPrice(priceSeed, 3.20, 4.90, 0.10);
      }
      case "мясо и птица" -> {
        if (containsAny(normalizedName, "кури")) {
          yield rangedPrice(priceSeed, 8.10, 10.40, 0.30);
        }
        if (containsAny(normalizedName, "индей")) {
          yield rangedPrice(priceSeed, 12.50, 14.50, 0.20);
        }
        if (containsAny(normalizedName, "говяж", "говядин", "телят")) {
          yield rangedPrice(priceSeed, 9.40, 17.90, 0.50);
        }
        if (containsAny(normalizedName, "свин", "карбонад", "шея", "лопатка", "грудин")) {
          yield rangedPrice(priceSeed, 11.00, 13.90, 0.30);
        }
        if (containsAny(normalizedName, "утк", "кролик", "баран")) {
          yield rangedPrice(priceSeed, 13.00, 16.20, 0.40);
        }
        if (containsAny(normalizedName, "сердеч", "печен")) {
          yield rangedPrice(priceSeed, 7.40, 9.80, 0.20);
        }
        if (containsAny(normalizedName, "фарш")) {
          yield rangedPrice(priceSeed, 10.80, 13.40, 0.20);
        }
        yield rangedPrice(priceSeed, 9.50, 14.00, 0.30);
      }
      case "полуфабрикаты" -> rangedPrice(priceSeed, 7.80, 10.20, 0.20);
      case "птица и яйца" -> rangedPrice(priceSeed, 3.70, 6.20, 0.10);
      case "овощи", "овощи и зелень" -> {
        if (containsAny(normalizedName, "укроп", "петруш", "базилик", "салат", "шпинат", "лук зел")) {
          yield rangedPrice(priceSeed, 0.90, 2.60, 0.10);
        }
        if (containsAny(normalizedName, "картоф", "лук", "капуст", "свекл", "репа", "редьк", "пастернак")) {
          yield rangedPrice(priceSeed, 1.80, 3.60, 0.10);
        }
        yield rangedPrice(priceSeed, 2.20, 5.80, 0.20);
      }
      case "зелень" -> rangedPrice(priceSeed, 0.90, 2.60, 0.10);
      case "фрукты" -> rangedPrice(priceSeed, 3.20, 5.80, 0.10);
      case "ягоды" -> rangedPrice(priceSeed, 5.20, 7.20, 0.10);
      case "пчеловодство" -> rangedPrice(priceSeed, 11.50, 13.50, 0.10);
      case "хлеб и выпечка" -> rangedPrice(priceSeed, 1.60, 3.40, 0.10);
      case "крупы", "крупы и бобовые", "бобовые" -> rangedPrice(priceSeed, 2.20, 4.40, 0.10);
      case "напитки" -> rangedPrice(priceSeed, 2.40, 4.60, 0.10);
      case "масла" -> rangedPrice(priceSeed, 6.20, 8.20, 0.10);
      case "консервация" -> rangedPrice(priceSeed, 3.80, 6.20, 0.10);
      default -> rangedPrice(priceSeed, 2.50, 7.50, 0.10);
    };
    return price.toPlainString();
  }

  private BigDecimal rangedPrice(String seed, double min, double max, double step) {
    int slots = Math.max(1, (int) Math.round((max - min) / step));
    int slotIndex = Math.floorMod(seed.hashCode(), slots + 1);
    return BigDecimal.valueOf(min + slotIndex * step).setScale(2, RoundingMode.HALF_UP);
  }

  private int resolveCatalogStock(String imageName) {
    return 30 + Math.floorMod(imageName.hashCode(), 121);
  }

  private User createUserIfMissing(String username,
                                   String fullName,
                                   String phone,
                                   String legal,
                                   Role role,
                                   String password,
                                   String... legacyAliases) {
    User existing = findUserByUsernameOrAliases(username, legacyAliases);
    if (existing == null) {
      return userRepository.save(new User(username, passwordEncoder.encode(password), fullName, phone, legal, role));
    }
    boolean dirty = false;
    if (!Objects.equals(existing.getUsername(), username)) {
      existing.setUsername(username);
      dirty = true;
    }
    if (!Objects.equals(existing.getFullName(), fullName)) {
      existing.setFullName(fullName);
      dirty = true;
    }
    if (!Objects.equals(existing.getPhone(), phone)) {
      existing.setPhone(phone);
      dirty = true;
    }
    if (!Objects.equals(existing.getLegalEntityName(), legal)) {
      existing.setLegalEntityName(legal);
      dirty = true;
    }
    if (existing.getRole() != role) {
      existing.setRole(role);
      dirty = true;
    }
    if (password != null && (existing.getPasswordHash() == null || !passwordEncoder.matches(password, existing.getPasswordHash()))) {
      existing.setPasswordHash(passwordEncoder.encode(password));
      dirty = true;
    }
    if (dirty) {
      return userRepository.save(existing);
    }
    return existing;
  }

  private User findUserByUsernameOrAliases(String username, String... legacyAliases) {
    User currentUser = userRepository.findByUsername(username).orElse(null);
    List<User> legacyUsers = new ArrayList<>();
    for (String legacyAlias : legacyAliases) {
      if (legacyAlias == null || legacyAlias.isBlank() || legacyAlias.equalsIgnoreCase(username)) {
        continue;
      }
      User legacyUser = userRepository.findByUsername(legacyAlias).orElse(null);
      if (legacyUser != null && legacyUsers.stream().noneMatch(existing -> Objects.equals(existing.getId(), legacyUser.getId()))) {
        legacyUsers.add(legacyUser);
      }
    }
    if (currentUser != null) {
      for (User legacyUser : legacyUsers) {
        if (!Objects.equals(currentUser.getId(), legacyUser.getId())) {
          mergeLegacyDirectorIntoCurrentUser(currentUser, legacyUser);
        }
      }
      return currentUser;
    }
    if (legacyUsers.isEmpty()) {
      return null;
    }

    User canonicalLegacyUser = legacyUsers.get(0);
    for (int index = 1; index < legacyUsers.size(); index++) {
      mergeLegacyDirectorIntoCurrentUser(canonicalLegacyUser, legacyUsers.get(index));
    }
    return canonicalLegacyUser;
  }

  private void createAddressIfMissing(User user, SeededAddressProfile profile) {
    StoreAddress existing = storeAddressRepository.findByUserIdAndLabelIgnoreCase(user.getId(), profile.label()).orElse(null);
    if (existing == null) {
      existing = findLegacyAddressByAliases(user.getId(), profile.legacyLabels());
    }
    if (existing == null) {
      StoreAddress address = new StoreAddress();
      address.setUser(user);
      address.setLabel(profile.label());
      address.setAddressLine(profile.addressLine());
      address.setLatitude(new BigDecimal(profile.latitude()));
      address.setLongitude(new BigDecimal(profile.longitude()));
      address.setCreatedAt(Instant.now());
      address.setUpdatedAt(Instant.now());
      storeAddressRepository.save(address);
      return;
    }

    boolean dirty = synchronizeSeededAddress(existing, user, profile);
    if (dirty) {
      existing.setUpdatedAt(Instant.now());
      storeAddressRepository.save(existing);
    }
    removeLegacyAddressAliases(user.getId(), existing, profile.legacyLabels());
  }

  private boolean synchronizeSeededAddress(StoreAddress address, User user, SeededAddressProfile profile) {
    boolean dirty = false;
    if (!Objects.equals(address.getUser(), user)) {
      address.setUser(user);
      dirty = true;
    }
    if (!Objects.equals(address.getLabel(), profile.label())) {
      address.setLabel(profile.label());
      dirty = true;
    }
    if (!Objects.equals(address.getAddressLine(), profile.addressLine())) {
      address.setAddressLine(profile.addressLine());
      dirty = true;
    }
    BigDecimal latitude = new BigDecimal(profile.latitude());
    if (!Objects.equals(address.getLatitude(), latitude)) {
      address.setLatitude(latitude);
      dirty = true;
    }
    BigDecimal longitude = new BigDecimal(profile.longitude());
    if (!Objects.equals(address.getLongitude(), longitude)) {
      address.setLongitude(longitude);
      dirty = true;
    }
    return dirty;
  }

  private StoreAddress findLegacyAddressByAliases(Long userId, List<String> legacyLabels) {
    for (String legacyLabel : legacyLabels) {
      StoreAddress existing = storeAddressRepository.findByUserIdAndLabelIgnoreCase(userId, legacyLabel).orElse(null);
      if (existing != null) {
        return existing;
      }
    }
    return null;
  }

  private void removeLegacyAddressAliases(Long userId, StoreAddress canonicalAddress, List<String> legacyLabels) {
    for (String legacyLabel : legacyLabels) {
      StoreAddress legacyAddress = storeAddressRepository.findByUserIdAndLabelIgnoreCase(userId, legacyLabel).orElse(null);
      if (legacyAddress == null || Objects.equals(legacyAddress.getId(), canonicalAddress.getId())) {
        continue;
      }
      reassignDeliveryAddressReferences(canonicalAddress, legacyAddress);
      storeAddressRepository.delete(legacyAddress);
    }
  }

  private void reassignDeliveryAddressReferences(StoreAddress canonicalAddress, StoreAddress legacyAddress) {
    List<com.farm.sales.model.Order> orders = orderRepository.findByDeliveryAddressId(legacyAddress.getId());
    if (orders.isEmpty()) {
      return;
    }
    for (com.farm.sales.model.Order order : orders) {
      order.setDeliveryAddress(canonicalAddress);
      order.setDeliveryAddressText(canonicalAddress.getAddressLine());
      order.setDeliveryLatitude(canonicalAddress.getLatitude());
      order.setDeliveryLongitude(canonicalAddress.getLongitude());
    }
    orderRepository.saveAll(orders);
  }

  private void mergeLegacyDirectorIntoCurrentUser(User currentUser, User legacyUser) {
    List<com.farm.sales.model.Order> legacyOrders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(
        legacyUser.getId(),
        Pageable.unpaged()
    );
    if (!legacyOrders.isEmpty()) {
      for (com.farm.sales.model.Order order : legacyOrders) {
        order.setCustomer(currentUser);
      }
      orderRepository.saveAll(legacyOrders);
    }

    List<StoreAddress> legacyAddresses = storeAddressRepository.findByUserIdOrderByCreatedAtDesc(legacyUser.getId());
    for (StoreAddress legacyAddress : legacyAddresses) {
      legacyAddress.setUser(currentUser);
      legacyAddress.setUpdatedAt(Instant.now());
      storeAddressRepository.save(legacyAddress);
    }

    userRepository.delete(legacyUser);
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
    if (username == null || username.isBlank()) {
      return null;
    }

    String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
    String staticPassword = SEEDED_USER_PASSWORDS.get(normalizedUsername);
    if (staticPassword != null) {
      return staticPassword;
    }

    DirectorSeedProfile directorProfile = findDirectorSeedProfile(normalizedUsername);
    if (directorProfile != null) {
      return directorProfile.password();
    }

    return null;
  }

  private User createDemoDirector(DirectorSeedProfile profile) {
    return createUserIfMissing(
        profile.username(),
        profile.fullName(),
        profile.phone(),
        profile.legalEntityName(),
        Role.DIRECTOR,
        profile.password(),
        legacyDirectorAliases(profile).toArray(String[]::new)
    );
  }

  private User createDemoDirector(int index) {
    return createDemoDirector(directorProfile(index));
  }

  public static List<String> demoDirectorUsernames() {
    return SEEDED_DIRECTOR_PROFILES.stream()
        .map(DirectorSeedProfile::username)
        .toList();
  }

  public static String formatDemoDirectorUsername(int index) {
    return directorProfile(index).username();
  }

  public static int parseDemoDirectorIndex(String username) {
    int profileIndex = directorProfileIndex(username);
    return profileIndex < 0 ? -1 : profileIndex + 1;
  }

  private static DirectorSeedProfile directorProfile(int index) {
    if (index < 1 || index > SEEDED_DIRECTOR_PROFILES.size()) {
      throw new IllegalArgumentException("Director index is out of range: " + index);
    }
    return SEEDED_DIRECTOR_PROFILES.get(index - 1);
  }

  private static DirectorSeedProfile findDirectorSeedProfile(String username) {
    int profileIndex = directorProfileIndex(username);
    if (profileIndex < 0) {
      return null;
    }
    return SEEDED_DIRECTOR_PROFILES.get(profileIndex);
  }

  private static int directorProfileIndex(String username) {
    if (username == null) {
      return -1;
    }
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    for (int index = 0; index < SEEDED_DIRECTOR_PROFILES.size(); index++) {
      if (SEEDED_DIRECTOR_PROFILES.get(index).username().equalsIgnoreCase(normalized)
          || legacyDirectorAliases(index + 1).stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized))) {
        return index;
      }
    }
    return -1;
  }

  private static List<String> legacyDirectorAliases(DirectorSeedProfile profile) {
    return legacyDirectorAliases(SEEDED_DIRECTOR_PROFILES.indexOf(profile) + 1);
  }

  private static List<String> legacyDirectorAliases(int index) {
    List<String> aliases = new ArrayList<>();
    aliases.add(String.format(Locale.ROOT, "director%02d", index));
    switch (index) {
      case 1 -> aliases.add("berezka");
      case 2 -> aliases.add("kvartal");
      case 3 -> aliases.add("yantar");
      default -> {
      }
    }
    return aliases;
  }
}
