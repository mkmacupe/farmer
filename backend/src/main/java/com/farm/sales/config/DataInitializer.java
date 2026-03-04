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
    createOrUpdateProduct(
        "Молоко цельное 2 л",
        "Молочная продукция",
        "Цельное молоко от утренней дойки",
        image("milk-whole-2l.webp"),
        "5.90",
        90
    );
    createOrUpdateProduct(
        "Молоко топлёное 1 л",
        "Молочная продукция",
        "Томлёное молоко с карамельным вкусом",
        image("baked-milk.webp"),
        "4.10",
        76
    );
    createOrUpdateProduct(
        "Простокваша домашняя 1 л",
        "Молочная продукция",
        "Натуральная простокваша на фермерской закваске",
        image("prostokvasha.webp"),
        "3.20",
        82
    );
    createOrUpdateProduct(
        "Сливки 20% 500 мл",
        "Молочная продукция",
        "Питьевые сливки из цельного молока",
        image("cream-20.webp"),
        "5.60",
        54
    );
    createOrUpdateProduct(
        "Сыр адыгейский 400 г",
        "Молочная продукция",
        "Свежий мягкий сыр без длительной выдержки",
        image("adyghe-cheese.webp"),
        "8.30",
        48
    );
    createOrUpdateProduct(
        "Сыр рассольный 400 г",
        "Молочная продукция",
        "Умеренно солёный рассольный сыр",
        image("brined-cheese.webp"),
        "8.90",
        42
    );
    createOrUpdateProduct(
        "Творожный сыр 200 г",
        "Молочная продукция",
        "Нежный творожный сыр для бутербродов и соусов",
        image("curd-cheese.webp"),
        "4.90",
        64
    );
    createOrUpdateProduct(
        "Творожная масса с изюмом 250 г",
        "Молочная продукция",
        "Сладкая творожная масса с натуральным изюмом",
        image("curd-raisin.webp"),
        "4.40",
        58
    );
    createOrUpdateProduct(
        "Филе куриное охлаждённое 1 кг",
        "Мясо и птица",
        "Нежное куриное филе без кожи",
        image("chicken-fillet.webp"),
        "10.20",
        46
    );
    createOrUpdateProduct(
        "Бедро куриное 1 кг",
        "Мясо и птица",
        "Куриное бедро для запекания и тушения",
        image("chicken-thigh.webp"),
        "8.70",
        52
    );
    createOrUpdateProduct(
        "Свиные рёбра 1 кг",
        "Мясо и птица",
        "Мясные свиные рёбра фермерского откорма",
        image("pork-ribs.webp"),
        "11.90",
        34
    );
    createOrUpdateProduct(
        "Фарш домашний свино-говяжий 1 кг",
        "Мясо и птица",
        "Свежий фарш из свинины и говядины",
        image("mixed-mince.webp"),
        "13.30",
        40
    );
    createOrUpdateProduct(
        "Печень говяжья 1 кг",
        "Мясо и птица",
        "Охлаждённая говяжья печень",
        image("beef-liver.webp"),
        "9.40",
        30
    );
    createOrUpdateProduct(
        "Грудка индейки 1 кг",
        "Мясо и птица",
        "Диетическая грудка индейки без кожи",
        image("turkey-breast.webp"),
        "13.70",
        32
    );
    createOrUpdateProduct(
        "Яйца куриные С0 10 шт",
        "Птица и яйца",
        "Крупные яйца категории С0",
        image("egg-c0.webp"),
        "4.20",
        140
    );
    createOrUpdateProduct(
        "Яйца перепелиные 30 шт",
        "Птица и яйца",
        "Упаковка свежих перепелиных яиц",
        image("quail-eggs-30.webp"),
        "6.20",
        68
    );
    createOrUpdateProduct(
        "Картофель для запекания 2 кг",
        "Овощи",
        "Крупный картофель с плотной мякотью",
        image("potato-bake.webp"),
        "5.70",
        118
    );
    createOrUpdateProduct(
        "Картофель для пюре 2 кг",
        "Овощи",
        "Рассыпчатый картофель для домашнего пюре",
        image("potato-mash.webp"),
        "5.60",
        122
    );
    createOrUpdateProduct(
        "Лук красный 1 кг",
        "Овощи",
        "Сладковатый красный лук для салатов",
        image("red-onion.webp"),
        "2.80",
        86
    );
    createOrUpdateProduct(
        "Капуста пекинская 1 шт",
        "Овощи",
        "Сочная пекинская капуста тепличного выращивания",
        image("chinese-cabbage.webp"),
        "3.40",
        60
    );
    createOrUpdateProduct(
        "Огурцы короткоплодные 1 кг",
        "Овощи",
        "Плотные огурцы для салатов и засолки",
        image("short-cucumber.webp"),
        "5.20",
        84
    );
    createOrUpdateProduct(
        "Томаты сливка 1 кг",
        "Овощи",
        "Плотные томаты сливовидной формы",
        image("plum-tomato.webp"),
        "5.40",
        78
    );
    createOrUpdateProduct(
        "Свекла молодая пучковая 1 кг",
        "Овощи",
        "Нежная молодая свекла нового урожая",
        image("young-beet.webp"),
        "2.30",
        88
    );
    createOrUpdateProduct(
        "Морковь мытая 1 кг",
        "Овощи",
        "Отборная мытая морковь для готовки",
        image("washed-carrot.webp"),
        "2.60",
        92
    );
    createOrUpdateProduct(
        "Петрушка корневая 500 г",
        "Овощи",
        "Корневая петрушка для бульонов и запекания",
        image("parsley-root.webp"),
        "2.90",
        56
    );
    createOrUpdateProduct(
        "Укроп сушёный 50 г",
        "Зелень",
        "Сушёный укроп для супов и маринадов",
        image("dill-dry.webp"),
        "1.80",
        74
    );
    createOrUpdateProduct(
        "Яблоки антоновка 1 кг",
        "Фрукты",
        "Кисло-сладкая антоновка местных садов",
        image("apple-antonovka.webp"),
        "3.50",
        96
    );
    createOrUpdateProduct(
        "Яблоки белый налив 1 кг",
        "Фрукты",
        "Летний сорт белый налив",
        image("apple-white.webp"),
        "3.40",
        90
    );
    createOrUpdateProduct(
        "Груши конференция 1 кг",
        "Фрукты",
        "Сочные груши осеннего сбора",
        image("pear-conference.webp"),
        "4.50",
        74
    );
    createOrUpdateProduct(
        "Клюква 300 г",
        "Ягоды",
        "Свежая клюква для морсов и соусов",
        image("cranberry.webp"),
        "5.70",
        52
    );
    createOrUpdateProduct(
        "Брусника 300 г",
        "Ягоды",
        "Брусника с лёгкой терпкостью",
        image("lingonberry.webp"),
        "6.10",
        48
    );
    createOrUpdateProduct(
        "Смородина красная 300 г",
        "Ягоды",
        "Красная смородина с яркой кислинкой",
        image("redcurrant.webp"),
        "5.30",
        54
    );
    createOrUpdateProduct(
        "Мёд липовый 500 г",
        "Пчеловодство",
        "Липовый мёд со светлым ароматом",
        image("linden-honey.webp"),
        "12.40",
        36
    );
    createOrUpdateProduct(
        "Мука пшеничная цельнозерновая 1 кг",
        "Крупы",
        "Мука грубого помола из местной пшеницы",
        image("wholewheat-flour.webp"),
        "2.70",
        88
    );
    createOrUpdateProduct(
        "Хлеб пшенично-ржаной 700 г",
        "Хлеб и выпечка",
        "Фермерский подовый хлеб на закваске",
        image("wheat-rye-bread.webp"),
        "3.10",
        92
    );
    createOrUpdateProduct(
        "Молоко пастеризованное 3.2% 1 л",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-101.webp"),
        "3.73",
        76
    );
    createOrUpdateProduct(
        "Молоко обезжиренное 1 л",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-102.webp"),
        "4.10",
        83
    );
    createOrUpdateProduct(
        "Кефир 3.2% 1 л",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-103.webp"),
        "4.47",
        71
    );
    createOrUpdateProduct(
        "Кефир термостатный 500 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-104.webp"),
        "4.84",
        78
    );
    createOrUpdateProduct(
        "Йогурт питьевой черника 330 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-105.webp"),
        "3.36",
        85
    );
    createOrUpdateProduct(
        "Йогурт питьевой малина 330 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-106.webp"),
        "3.73",
        73
    );
    createOrUpdateProduct(
        "Творог 9% 300 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-107.webp"),
        "4.10",
        80
    );
    createOrUpdateProduct(
        "Творог 5% 300 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-108.webp"),
        "4.47",
        87
    );
    createOrUpdateProduct(
        "Сметана 15% 400 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-109.webp"),
        "4.84",
        75
    );
    createOrUpdateProduct(
        "Сливки 33% 300 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-110.webp"),
        "3.36",
        82
    );
    createOrUpdateProduct(
        "Масло сливочное 72.5% 200 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-111.webp"),
        "3.73",
        70
    );
    createOrUpdateProduct(
        "Сыр фермерский молодой 400 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-112.webp"),
        "4.10",
        77
    );
    createOrUpdateProduct(
        "Сыр козий мягкий 200 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-113.webp"),
        "4.47",
        84
    );
    createOrUpdateProduct(
        "Брынза коровья 350 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-114.webp"),
        "4.84",
        72
    );
    createOrUpdateProduct(
        "Рикотта фермерская 250 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-115.webp"),
        "3.36",
        79
    );
    createOrUpdateProduct(
        "Простокваша 500 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-116.webp"),
        "3.73",
        86
    );
    createOrUpdateProduct(
        "Пахта 500 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-117.webp"),
        "4.10",
        74
    );
    createOrUpdateProduct(
        "Сыворотка молочная 1 л",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-118.webp"),
        "4.47",
        81
    );
    createOrUpdateProduct(
        "Топлёное молоко 500 мл",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-119.webp"),
        "4.84",
        69
    );
    createOrUpdateProduct(
        "Десерт творожный ваниль 180 г",
        "Молочная продукция",
        "Свежий молочный продукт из сырья местных хозяйств.",
        image("mogilev-product-120.webp"),
        "3.36",
        76
    );
    createOrUpdateProduct(
        "Куриные крылья 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-121.webp"),
        "11.53",
        49
    );
    createOrUpdateProduct(
        "Куриная голень 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-122.webp"),
        "11.90",
        37
    );
    createOrUpdateProduct(
        "Куриные сердечки 500 г",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-123.webp"),
        "12.27",
        44
    );
    createOrUpdateProduct(
        "Куриная печень 500 г",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-124.webp"),
        "12.64",
        51
    );
    createOrUpdateProduct(
        "Фарш куриный 700 г",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-125.webp"),
        "11.16",
        39
    );
    createOrUpdateProduct(
        "Индейка бедро 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-126.webp"),
        "11.53",
        46
    );
    createOrUpdateProduct(
        "Индейка голень 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-127.webp"),
        "11.90",
        53
    );
    createOrUpdateProduct(
        "Фарш индейки 700 г",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-128.webp"),
        "12.27",
        41
    );
    createOrUpdateProduct(
        "Свиной карбонад 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-129.webp"),
        "12.64",
        48
    );
    createOrUpdateProduct(
        "Свиная шея 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-130.webp"),
        "11.16",
        36
    );
    createOrUpdateProduct(
        "Свиная лопатка 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-131.webp"),
        "11.53",
        43
    );
    createOrUpdateProduct(
        "Свиная грудинка 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-132.webp"),
        "11.90",
        50
    );
    createOrUpdateProduct(
        "Говядина тазобедренная часть 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-133.webp"),
        "12.27",
        38
    );
    createOrUpdateProduct(
        "Говядина ребро 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-134.webp"),
        "12.64",
        45
    );
    createOrUpdateProduct(
        "Говяжий фарш 700 г",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-135.webp"),
        "11.16",
        52
    );
    createOrUpdateProduct(
        "Телятина вырезка 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-136.webp"),
        "11.53",
        40
    );
    createOrUpdateProduct(
        "Баранина лопатка 1 кг",
        "Мясо и птица",
        "Охлаждённый продукт фермерского производства Могилёвской области.",
        image("mogilev-product-137.webp"),
        "11.90",
        47
    );
    createOrUpdateProduct(
        "Колбаски домашние свиные 600 г",
        "Полуфабрикаты",
        "Полуфабрикат из фермерского сырья для быстрого приготовления.",
        image("mogilev-product-138.webp"),
        "8.97",
        43
    );
    createOrUpdateProduct(
        "Купаты куриные 600 г",
        "Полуфабрикаты",
        "Полуфабрикат из фермерского сырья для быстрого приготовления.",
        image("mogilev-product-139.webp"),
        "9.34",
        50
    );
    createOrUpdateProduct(
        "Пельмени фермерские 800 г",
        "Полуфабрикаты",
        "Полуфабрикат из фермерского сырья для быстрого приготовления.",
        image("mogilev-product-140.webp"),
        "7.86",
        57
    );
    createOrUpdateProduct(
        "Яйца куриные С2 10 шт",
        "Птица и яйца",
        "Свежая продукция от птицеводческих хозяйств региона.",
        image("mogilev-product-141.webp"),
        "3.83",
        121
    );
    createOrUpdateProduct(
        "Яйца куриные отборные 15 шт",
        "Птица и яйца",
        "Свежая продукция от птицеводческих хозяйств региона.",
        image("mogilev-product-142.webp"),
        "4.20",
        128
    );
    createOrUpdateProduct(
        "Яйца домашние 20 шт",
        "Птица и яйца",
        "Свежая продукция от птицеводческих хозяйств региона.",
        image("mogilev-product-143.webp"),
        "4.57",
        135
    );
    createOrUpdateProduct(
        "Хлеб зерновой 600 г",
        "Хлеб и выпечка",
        "Свежая выпечка из муки местного помола.",
        image("mogilev-product-144.webp"),
        "3.14",
        93
    );
    createOrUpdateProduct(
        "Булка с отрубями 350 г",
        "Хлеб и выпечка",
        "Свежая выпечка из муки местного помола.",
        image("mogilev-product-145.webp"),
        "1.66",
        100
    );
    createOrUpdateProduct(
        "Багет цельнозерновой 300 г",
        "Хлеб и выпечка",
        "Свежая выпечка из муки местного помола.",
        image("mogilev-product-146.webp"),
        "2.03",
        107
    );
    createOrUpdateProduct(
        "Лепёшка ржаная 250 г",
        "Хлеб и выпечка",
        "Свежая выпечка из муки местного помола.",
        image("mogilev-product-147.webp"),
        "2.40",
        95
    );
    createOrUpdateProduct(
        "Сухари пшеничные 200 г",
        "Хлеб и выпечка",
        "Свежая выпечка из муки местного помола.",
        image("mogilev-product-148.webp"),
        "2.77",
        102
    );
    createOrUpdateProduct(
        "Мука ржаная 1 кг",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-149.webp"),
        "3.74",
        76
    );
    createOrUpdateProduct(
        "Мука пшеничная высший сорт 1 кг",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-150.webp"),
        "2.26",
        83
    );
    createOrUpdateProduct(
        "Мука овсяная 800 г",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-151.webp"),
        "2.63",
        90
    );
    createOrUpdateProduct(
        "Крупа перловая 1 кг",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-152.webp"),
        "3.00",
        78
    );
    createOrUpdateProduct(
        "Крупа ячневая 1 кг",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-153.webp"),
        "3.37",
        85
    );
    createOrUpdateProduct(
        "Овсяные хлопья 800 г",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-154.webp"),
        "3.74",
        92
    );
    createOrUpdateProduct(
        "Манная крупа 800 г",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-155.webp"),
        "2.26",
        80
    );
    createOrUpdateProduct(
        "Горох колотый 800 г",
        "Бобовые",
        "Отборные бобовые фермерского урожая.",
        image("mogilev-product-156.webp"),
        "3.53",
        79
    );
    createOrUpdateProduct(
        "Нут сушёный 800 г",
        "Бобовые",
        "Отборные бобовые фермерского урожая.",
        image("mogilev-product-157.webp"),
        "3.90",
        67
    );
    createOrUpdateProduct(
        "Фасоль белая 800 г",
        "Бобовые",
        "Отборные бобовые фермерского урожая.",
        image("mogilev-product-158.webp"),
        "4.27",
        74
    );
    createOrUpdateProduct(
        "Семена льна 300 г",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-159.webp"),
        "3.74",
        89
    );
    createOrUpdateProduct(
        "Семечки подсолнечника очищенные 400 г",
        "Крупы",
        "Сухой продукт из зерна урожая местных хозяйств.",
        image("mogilev-product-160.webp"),
        "2.26",
        77
    );
    createOrUpdateProduct(
        "Картофель красный 2 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-161.webp"),
        "2.43",
        94
    );
    createOrUpdateProduct(
        "Картофель белый 2 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-162.webp"),
        "2.80",
        101
    );
    createOrUpdateProduct(
        "Морковь молодая 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-163.webp"),
        "3.17",
        89
    );
    createOrUpdateProduct(
        "Лук шалот 500 г",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-164.webp"),
        "3.54",
        96
    );
    createOrUpdateProduct(
        "Лук зелёный 150 г",
        "Зелень",
        "Ароматная свежая зелень локального выращивания.",
        image("mogilev-product-165.webp"),
        "0.86",
        101
    );
    createOrUpdateProduct(
        "Капуста краснокочанная 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-166.webp"),
        "2.43",
        91
    );
    createOrUpdateProduct(
        "Капуста савойская 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-167.webp"),
        "2.80",
        98
    );
    createOrUpdateProduct(
        "Огурцы корнишоны 500 г",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-168.webp"),
        "3.17",
        86
    );
    createOrUpdateProduct(
        "Огурцы тепличные 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-169.webp"),
        "3.54",
        93
    );
    createOrUpdateProduct(
        "Томаты сливовидные 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-170.webp"),
        "2.06",
        100
    );
    createOrUpdateProduct(
        "Томаты жёлтые 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-171.webp"),
        "2.43",
        88
    );
    createOrUpdateProduct(
        "Перец острый 200 г",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-172.webp"),
        "2.80",
        95
    );
    createOrUpdateProduct(
        "Перец сладкий красный 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-173.webp"),
        "3.17",
        102
    );
    createOrUpdateProduct(
        "Кабачки цуккини 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-174.webp"),
        "3.54",
        90
    );
    createOrUpdateProduct(
        "Патиссоны 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-175.webp"),
        "2.06",
        97
    );
    createOrUpdateProduct(
        "Свекла запечная 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-176.webp"),
        "2.43",
        85
    );
    createOrUpdateProduct(
        "Сельдерей корневой 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-177.webp"),
        "2.80",
        92
    );
    createOrUpdateProduct(
        "Сельдерей стеблевой 300 г",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-178.webp"),
        "3.17",
        99
    );
    createOrUpdateProduct(
        "Пастернак 700 г",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-179.webp"),
        "3.54",
        87
    );
    createOrUpdateProduct(
        "Редька чёрная 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-180.webp"),
        "2.06",
        94
    );
    createOrUpdateProduct(
        "Репа столовая 1 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-181.webp"),
        "2.43",
        101
    );
    createOrUpdateProduct(
        "Тыква столовая 2 кг",
        "Овощи",
        "Свежие овощи сезонного сбора с фермерских полей.",
        image("mogilev-product-182.webp"),
        "2.80",
        89
    );
    createOrUpdateProduct(
        "Яблоки сладкие 1 кг",
        "Фрукты",
        "Фрукты из садов Могилёвского региона.",
        image("mogilev-product-183.webp"),
        "4.77",
        84
    );
    createOrUpdateProduct(
        "Яблоки кисло-сладкие 1 кг",
        "Фрукты",
        "Фрукты из садов Могилёвского региона.",
        image("mogilev-product-184.webp"),
        "5.14",
        91
    );
    createOrUpdateProduct(
        "Груши поздние 1 кг",
        "Фрукты",
        "Фрукты из садов Могилёвского региона.",
        image("mogilev-product-185.webp"),
        "3.66",
        79
    );
    createOrUpdateProduct(
        "Слива жёлтая 1 кг",
        "Фрукты",
        "Фрукты из садов Могилёвского региона.",
        image("mogilev-product-186.webp"),
        "4.03",
        86
    );
    createOrUpdateProduct(
        "Виноград тепличный 500 г",
        "Фрукты",
        "Фрукты из садов Могилёвского региона.",
        image("mogilev-product-187.webp"),
        "4.40",
        74
    );
    createOrUpdateProduct(
        "Крыжовник 300 г",
        "Ягоды",
        "Ягоды свежего сбора без искусственных добавок.",
        image("mogilev-product-188.webp"),
        "6.17",
        55
    );
    createOrUpdateProduct(
        "Смесь салатная 150 г",
        "Зелень",
        "Ароматная свежая зелень локального выращивания.",
        image("mogilev-product-189.webp"),
        "2.34",
        98
    );
    createOrUpdateProduct(
        "Базилик свежий 100 г",
        "Зелень",
        "Ароматная свежая зелень локального выращивания.",
        image("mogilev-product-190.webp"),
        "0.86",
        86
    );
    createOrUpdateProduct(
        "Мёд разнотравье 1 кг",
        "Пчеловодство",
        "Натуральный продукт с пасек Могилёвской области.",
        image("mogilev-product-191.webp"),
        "12.83",
        44
    );
    createOrUpdateProduct(
        "Мёд гречишный 1 кг",
        "Пчеловодство",
        "Натуральный продукт с пасек Могилёвской области.",
        image("mogilev-product-192.webp"),
        "13.20",
        51
    );
    createOrUpdateProduct(
        "Варенье клубничное 300 г",
        "Консервация",
        "Домашняя фермерская заготовка по традиционному рецепту.",
        image("mogilev-product-193.webp"),
        "5.27",
        68
    );
    createOrUpdateProduct(
        "Варенье малиновое 300 г",
        "Консервация",
        "Домашняя фермерская заготовка по традиционному рецепту.",
        image("mogilev-product-194.webp"),
        "5.64",
        75
    );
    createOrUpdateProduct(
        "Джем яблочный 300 г",
        "Консервация",
        "Домашняя фермерская заготовка по традиционному рецепту.",
        image("mogilev-product-195.webp"),
        "4.16",
        63
    );
    createOrUpdateProduct(
        "Компот яблочно-грушевый 1 л",
        "Напитки",
        "Натуральный напиток без искусственных ароматизаторов.",
        image("mogilev-product-196.webp"),
        "3.33",
        84
    );
    createOrUpdateProduct(
        "Морс клюквенный 1 л",
        "Напитки",
        "Натуральный напиток без искусственных ароматизаторов.",
        image("mogilev-product-197.webp"),
        "3.70",
        91
    );
    createOrUpdateProduct(
        "Масло льняное 500 мл",
        "Масла",
        "Нерафинированное масло из локального сырья.",
        image("mogilev-product-198.webp"),
        "7.57",
        51
    );
    createOrUpdateProduct(
        "Масло рапсовое 750 мл",
        "Масла",
        "Нерафинированное масло из локального сырья.",
        image("mogilev-product-199.webp"),
        "7.94",
        58
    );
    createOrUpdateProduct(
        "Квашеная капуста 900 г",
        "Консервация",
        "Домашняя фермерская заготовка по традиционному рецепту.",
        image("mogilev-product-200.webp"),
        "4.16",
        79
    );
    demoSeeded = true;
    }
  }

  public void seedDemoData() {
    run();
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

    String generated = buildUniqueDemoPhotoUrl(productName, photoUrl, currentProductId);
    if (generated != null) {
      log.warn(
          "Product photo URL '{}' is already used by another product; demo seed for '{}' switched to generated URL '{}'.",
          photoUrl,
          productName,
          generated
      );
      return generated;
    }

    log.warn(
        "Product photo URL '{}' is already used by another product; demo seed for '{}' will continue without photo.",
        photoUrl,
        productName
    );
    return null;
  }

  private String buildUniqueDemoPhotoUrl(String productName, String photoUrl, Long currentProductId) {
    String normalizedProduct = productName == null ? "" : productName;
    String slug = buildAsciiSlug(normalizedProduct);
    if (slug.isBlank()) {
      slug = "product";
    }
    String hashPart = Integer.toUnsignedString((normalizedProduct + "|" + photoUrl).hashCode(), 36);

    for (int index = 0; index < 100; index++) {
      String suffix = index == 0 ? "" : "-" + index;
      String candidate = PRODUCT_IMAGE_BASE + slug + "-" + hashPart + suffix + ".webp";
      boolean exists = currentProductId == null
          ? productRepository.existsByPhotoUrlIgnoreCase(candidate)
          : productRepository.existsByPhotoUrlIgnoreCaseAndIdNot(candidate, currentProductId);
      if (!exists) {
        return candidate;
      }
    }

    return null;
  }

  private String buildAsciiSlug(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    StringBuilder builder = new StringBuilder();
    boolean previousDash = false;
    for (int i = 0; i < lower.length(); i++) {
      char symbol = lower.charAt(i);
      boolean isAsciiLetterOrDigit = (symbol >= 'a' && symbol <= 'z') || (symbol >= '0' && symbol <= '9');
      if (isAsciiLetterOrDigit) {
        builder.append(symbol);
        previousDash = false;
        continue;
      }

      if (!previousDash && builder.length() > 0) {
        builder.append('-');
        previousDash = true;
      }
    }

    int length = builder.length();
    if (length > 0 && builder.charAt(length - 1) == '-') {
      builder.deleteCharAt(length - 1);
    }
    return builder.toString();
  }

  private void createAddressIfMissing(User user, String label, String addressLine, String latitude, String longitude) {
    if (storeAddressRepository.existsByUserIdAndLabelIgnoreCase(user.getId(), label)) {
      return;
    }

    StoreAddress created = new StoreAddress();
    Instant now = Instant.now();
    created.setUser(user);
    created.setLabel(label);
    created.setAddressLine(addressLine);
    created.setLatitude(new BigDecimal(latitude));
    created.setLongitude(new BigDecimal(longitude));
    created.setCreatedAt(now);
    created.setUpdatedAt(now);
    storeAddressRepository.save(created);
  }

  private String validateDemoPassword() {
    String normalized = demoPassword == null ? "" : demoPassword.trim();
    if (normalized.isEmpty() || normalized.toLowerCase().startsWith("replace-with-")) {
      throw new IllegalStateException("Необходимо задать app.demo.password");
    }
    return normalized;
  }

  private String seededPassword(String username) {
    String password = SEEDED_USER_PASSWORDS.get(username);
    if (password == null) {
      throw new IllegalStateException("Не задан пароль для пользователя: " + username);
    }
    return password;
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
