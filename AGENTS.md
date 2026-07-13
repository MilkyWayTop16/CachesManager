# AGENTS.md — CachesManager

Инструкции для **Grok Build** и **Claude Code**. Следуй этому файлу при любой работе с репозиторием.

---

## 0. Рабочие принципы (кратко)

### 0.1 Не исследовать проект без причины

- Не запускай обход графа, полный `search_graph`/`grep`/чтение деревьев «на всякий случай».
- Исследуй **только** то, что нужно для текущей задачи.
- AGENTS.md + уже известный контекст сессии — достаточно, пока не упёрся в пробел.
- Широкий `index_repository` — только если графа нет, он явно устарел, или пользователь попросил.

### 0.2 Самостоятельность

Агент должен самостоятельно принимать технические решения.

Если существует объективно лучший вариант
(производительность, читаемость, безопасность, поддерживаемость,
совместимость версий, соответствие архитектуре проекта),
выбирай его автоматически.

Не перечисляй мне 3–5 вариантов без необходимости.
Предлагай один лучший и реализуй его.

Спрашивай пользователя только если решение влияет на:

- функциональность;
- UX;
- формат конфигурации;
- совместимость;
- права;
- удаление существующего поведения.

Во всех остальных случаях принимай решение самостоятельно.

### 0.3 Использование MCP (codebase-memory)

- Структурные вопросы: **`search_graph`**, **`trace_path`** (и при необходимости `get_code_snippet` / `get_architecture`).
- Сначала граф, потом файлы — не наоборот.
- Не читай целые модули, если `trace_path` + snippet закрывают вопрос.
- Если tools MCP недоступны в сессии — CLI `codebase-memory-mcp cli …` (см. §2).

### 0.4 Ponytail (lazy senior)

Думай как **ленивый senior**: лучший код — тот, который **не написали**.

Перед новым кодом пройди лестницу (после понимания задачи, не вместо него):

1. Нужно ли это вообще? (YAGNI)
2. Уже есть в проекте? → **reuse**
3. Stdlib / Paper API / уже подключённая зависимость?
4. Можно одной строкой / минимальным diff?
5. Только потом — минимальный рабочий код

Правила: без лишних абстракций и зависимостей; deletion > addition; boring > clever; **наименьший diff в правильном месте** (root cause, не симптом).  
Не ленись только в: понимании задачи, security/data-loss, validation на границах, том, что явно попросили.

### 0.4.1 Минимальное исследование

Не исследуй проект ради исследования.

Перед использованием graph ответь себе:

"Мне действительно нужна эта информация для текущей задачи?"

Если нет —
не запускай search_graph,
не открывай десятки файлов,
не читай полрепозитория.

Изучай только тот участок,
который непосредственно относится к задаче.

### 0.5 Несколько файлов — открывай постепенно

Если изменение затрагивает **несколько** файлов:

- **не** открывай их все сразу;
- иди по цепочке: graph/callers → один файл → правка → следующий **только когда стало нужно**;
- не держи в контексте «весь feature-slice» заранее.

### 0.5.1 Контекст важнее повторного исследования

Используй уже накопленный контекст текущей сессии.

Если нужная информация уже известна,
не перечитывай те же файлы повторно.

Не выполняй одинаковые graph-запросы несколько раз,
если архитектура уже понятна.

### 0.6 Стиль и простота

- Держи существующий стиль проекта.
- Не усложняй архитектуру без необходимости.

---

## 1. О проекте

**CachesManager** — Paper/Spigot-плагин (Java 17), `api-version: 1.16` (совместимость **1.16+**, modern features через feature-detect).

Назначение: **тайники (caches)** в мире — блок + ключ + лут со шансами + анимация открытия + голограмма + админ-GUI (DeluxeMenus-style) + статистика/история в SQLite/MySQL.

| | |
|--|--|
| **GroupId / artifact** | `org.gw` / `CachesManager` |
| **Version** | `1.4.2` (`pom.xml` / `plugin.yml`) |
| **Main class** | `org.gw.cachesmanager.CachesManager` |
| **Сборка** | Maven (`pom.xml`), Java **17** |
| **API** | Paper API `1.20.4-R0.1-SNAPSHOT` (`provided`) |
| **Команда** | `/cachesmanager` (`/cm`) |
| **Soft depends** | ProtocolLib, PacketEvents, DecentHolograms, FancyHolograms |
| **Опционально** | PlaceholderAPI (hook) |
| **Shade** | `bstats` (relocated → `org.gw.cachesmanager.bstats`) + `HikariCP` |
| **Не shade** | Paper API, adventure, ProtocolLib/PacketEvents, hologram plugins, item-nbt-api runtime as configured |

**Не трогать без явной просьбы:**

- `config-version: 1.4` во всех YAML (config.yml, animations.yml, menus/*).
- Каталоги `references/` (эталон DeluxeMenus и др.), `target/`, `.idea/`, `.claude/`.
- Комментарии в **Java** — **запрещены** (в YAML комментарии допустимы и ожидаемы).
- PDC-ключи `CacheKeys` и UUID ключей (`key-uuid` в конфиге тайника) — ломают совместимость ключей.

---

## 2. Codebase-memory-mcp

Проект индексируется через **codebase-memory-mcp**. Граф — основной источник структурных знаний.

### 2.1 Когда индексировать

- Первый заход в репозиторий / после крупного рефакторинга / «граф устарел» → `index_repository`.
- Режим по умолчанию: **`moderate`** (type-aware CALLS + similarity/semantic; без full dump всего дерева).
- Исключения индекса (`.cbmignore` + gitignore): `.idea`, `target`, `references/*`, `reference/*`, `.claude`.

Пример CLI:

```bash
codebase-memory-mcp cli index_repository \
  --repo-path "C:/Users/Stas/IdeaProjects/Мои плагины/CachesManager" \
  --mode moderate
```

Имя проекта в графе (после index):

`C-Users-Stas-IdeaProjects-d09cd0bed0b8-d0bfd0bbd0b0d0b3d0b8d0bdd18b-CachesManager`

Проверка: `list_projects`, `index_status --project <name>`.

### 2.2 Правила использования графа

| Задача | Инструмент |
|--------|------------|
| Найти класс/метод по имени | `search_graph` (`name_pattern`, `label`, `file_pattern`) |
| Кто вызывает / кого вызывает | `trace_path` (`direction=inbound\|outbound\|both`) |
| Обзор архитектуры | `get_architecture`, `get_graph_schema` |
| Прочитать тело символа | `get_code_snippet` (после search) |
| Текст по содержимому | `search_code` / встроенный grep |
| Влияние локальных правок | `detect_changes` |

**Приоритет исследования (обязательно):**

1. Только если нужно для задачи — `search_graph` / `trace_path` / `get_architecture`
2. `get_code_snippet` или точечное чтение **одного** нужного файла
3. Следующий файл — только когда без него нельзя продолжить (не открывай пачку сразу)
4. Не вычитывать «полпроекта» ради одного метода
5. При неоднозначном имени (`start`, `open`, `reload`) — сначала `search_graph`, затем `trace_path` с точным `function_name`

### 2.3 Если MCP tools недоступны в сессии

Допустимо CLI:

```text
codebase-memory-mcp cli search_graph --project <name> --name-pattern ".*CacheManager.*"
codebase-memory-mcp cli trace_path --project <name> --function-name openCache --direction both --depth 2
```

Не подменяй граф слепым `find`/`grep` по всему дереву, пока не исчерпан graph-first путь.  
`references/` в индекс **не** входит — не ищи там runtime-символы через graph.

---

## 3. Архитектура

### 3.1 Слои и пакеты

Корень: `org.gw.cachesmanager`

| Пакет | Роль |
|-------|------|
| *(root)* | `CachesManager` — lifecycle, wiring менеджеров, reload, log/console |
| `caches` | Domain: `Cache`, `CacheRegistry`, `CacheInteractionHandler`, `CachePersistenceHandler` |
| `opening` | `CacheOpening` + `StandardCacheOpening` — старт открытия, roll loot, stats, запуск анимации |
| `animations` | `Animation` model, `AnimationRegistry`, `AnimationExecutor`, `AnimationListener` |
| `animations.platform` | `HologramPlatform` + DH / Fancy / Modern / Legacy / ProtocolLib / PacketEvents |
| `animations.view` | `AnimationView` + Legacy/Modern view для item-display |
| `managers` | Orchestration: Cache/Config/Menu/Hologram/Item/Animations/Stats/LootHistory |
| `configs` | `MainConfig`, `CacheConfigHandler`, `MenuConfigHandler`, `ActionManager`, `ConfigUpdater` |
| `commands` | `/cm` subcommands (`SubCommand` + `AbstractSubCommand` + `CommandsHandler`) |
| `listeners` | Block interact, mode sessions, key integrity, confirm delete |
| `listeners.modes` | `PlayerMode` + `ChatModeHandler` per edit mode |
| `menus` | GUI: holder, click commands, special actions, loot pages |
| `storage` | `DatabaseManager` (Hikari, stats + loot history) |
| `utils` | `HexColors`, `CacheKeys`, PAPI hook, UpdateChecker, BStats |

### 3.2 Ключевые компоненты (runtime)

```
CachesManager
 ├─ ConfigManager
 │   ├─ MainConfig          (config.yml + actions)
 │   ├─ CacheConfigHandler  (plugins/.../caches/<name>.yml, batch save)
 │   ├─ MenuConfigHandler   (menus/*.yml)
 │   └─ ActionManager       ([tag] pipeline)
 ├─ DatabaseManager         (SQLite/MySQL via Hikari, async executor)
 ├─ ItemManager             (ключи: PDC + appearance + stamp)
 ├─ HologramManager         (selectPlatform → HologramPlatform)
 ├─ AnimationsManager       → AnimationRegistry + AnimationExecutor
 ├─ StatsManager / LootHistoryManager
 ├─ CacheManager
 │   ├─ CacheRegistry       (name + location index)
 │   ├─ CachePersistenceHandler
 │   └─ CacheInteractionHandler  (open cooldown → StandardCacheOpening)
 ├─ MenuManager             (open/update/close, special actions, loot pages)
 └─ Listeners / Commands / modes
```

**Потоки данных (типичные):**

- **Открытие тайника:**  
  `CacheBlockListener.onPlayerInteract` → key check (`ItemManager`) → `CacheManager.openCache` → `CacheInteractionHandler.open` → `StandardCacheOpening.start`  
  → `inUse` CAS → roll loot → stats/history async → hide hologram → `AnimationExecutor` (or instant give).

- **Админ-меню:**  
  shift-right (по умолчанию) / `/cm menu` → `MenuManager` → YAML menus → `MenuClickActionHandler` (`[open-menu]`, `[selection-mode]`, chance/loot tags).

- **Режимы настройки:**  
  menu special tag → `CacheModeListener.enableMode(PlayerMode.*)` → chat/click handlers в `listeners.modes` → write via `CacheManager` + `ConfigManager` → timeout `settings.mode-timeout`.

- **Голограммы:**  
  `HologramManager` → DecentHolograms → FancyHolograms (reflective bridge) → Modern (`TextDisplay`) → Legacy.

- **Reload:**  
  close menus → reload YAML → DB reinit (stats flush first) → animations → caches/holograms → refresh open menus.

### 3.3 Повторяющиеся паттерны проекта

1. **Manager + Registry + Handler**
   - `CacheManager` — facade lifecycle/mutations.
   - `CacheRegistry` — in-memory name/location maps.
   - `CacheInteractionHandler` — open path + short cooldown.
   - `CachePersistenceHandler` — load/save orchestration with config handler.
   - Не плоди второй registry/manager рядом — расширяй существующий.

2. **Opening strategy**
   - Интерфейс `CacheOpening` (`start` / `finishVisual` / `cancel` / `isRunning`).
   - Реализация: `StandardCacheOpening`.
   - Новый тип открытия — новый implementor, не раздувай listener.

3. **Platform interface + soft-depend**
   - `HologramPlatform` в `animations.platform`.
   - Порядок: **DecentHolograms → FancyHolograms (если bridge OK) → ModernMinecraftPlatform → LegacyMinecraftPlatform**.
   - Modern грузится через `Class.forName` + constructor reflection (classloader-safe).
   - Packet item names: ProtocolLib **или** PacketEvents (soft); без них — degraded item names pre-1.19.4.
   - Hot-swap platform при enable/disable soft-plugin (см. `selectPlatform(disablingPlugin)`).

4. **Config object + per-cache YAML**
   - Global: `config.yml` (`MainConfig`), `animations.yml`, `menus/*.yml`.
   - Per-cache: `CacheConfigHandler` — файл на тайник, batch saver, loot pages.
   - `ConfigUpdater` — merge missing keys, **не** затирать user values.
   - `config-version: 1.4` — не bump без миграции/просьбы.

5. **Actions pipeline (UX)**
   - Пути `actions.*` в `config.yml`.
   - Теги `[message]`, `[sound]`, `[title]`, `[console-command]`, … в `ActionManager`.
   - Для `[message]`: снять **один** ведущий пробел после `]`, **не** `trim()` всего content (сохранить пустые строки/отступы).
   - Плейсхолдеры `{key}` / `<key>` + PlaceholderAPI если доступен.
   - Confirm-delete: `{confirm-button}` / `{cancel-button}` → clickable components.

6. **Menu click-commands (DeluxeMenus-style)**
   - Tags в menus: `[open-menu]`, `[close-menu]`, `[selection-mode]`, `[increase-chance]`, `[sound]`, …
   - Per-click lists: `click-commands` / `left-click-commands` / `shift-right-click-commands` …
   - `SpecialActionRegistry` + `MenuManager.registerSpecialAction` для plugin-internal handlers.
   - `CacheMenuHolder` идентифицирует inventory + cache + page.

7. **Player modes (edit sessions)**
   - Enum `PlayerMode`: SELECTION, REPLACE_BLOCK, RENAME, HOLOGRAM_*, KEY_*.
   - `ChatModeHandler` + registry; chat input / block click.
   - Timeout из `settings.mode-timeout.time`; cancel: `/cm cancel`.

8. **Keys (PDC + appearance)**
   - `CacheKeys`: `CACHE_NAME`, `KEY_UUID`, `GHOST`, `HOLOGRAM_ID`.
   - `ItemManager.isKey` / `isAnyKey`: PDC match → UUID match → appearance fallback + stamp.
   - Key integrity listener чинит/валидирует stacks.
   - Не меняй namespaced key format без миграции всех ключей.

9. **Concurrency & open state**
   - `Cache.inUse` = `AtomicBoolean` (CAS в `StandardCacheOpening`).
   - 500ms open cooldown per player (`CacheInteractionHandler` Guava cache).
   - Loot lists: `CopyOnWriteArrayList`; stats maps: `ConcurrentHashMap`.
   - DB writes async (`submitDatabaseTask`); sync только на reload/shutdown flush stats.

10. **Animations**
    - Model phases: **delay → item → final** + optional **ambient**.
    - `AnimationExecutor` — heavy path (particles, sounds, item entity, firework).
    - `continue-if-players-nearby` / `orphaned-radius` из config.
    - Hologram temporarily removed during open; restore after finish/cancel.

11. **Version-safe utilities**
    - `HexColors`: MiniMessage + legacy + hex → Adventure `Component` / string.
    - Feature-detect `TextDisplay` for modern holograms.
    - Soft-depends checked once / on plugin events, not every tick.

---

## 4. Соглашения по именованию и структуре

### Java

- Пакеты: lowercase `org.gw.cachesmanager.<layer>`.
- Классы: `PascalCase`; managers `*Manager`, handlers `*Handler`, listeners `*Listener`.
- Методы/поля: `camelCase`.
- Команды: `*Command` extends `AbstractSubCommand`, register in `CommandsHandler`.
- Режимы: `PlayerMode` + `*ModeHandler` implements `ChatModeHandler`.
- Без public API-шума: `package-private` / `final` где достаточно.
- Lombok `@Getter` / `@Setter` — как в соседних файлах (`Cache`, managers).

### YAML / resources

| Файл / путь | Назначение |
|-------------|------------|
| `config.yml` | settings, database, bstats, update-checker, **actions.*** |
| `animations.yml` | named animations (delay/item/final/ambient) |
| `menus/*.yml` | GUI: global, loot, chance, key, hologram, history, stats, … |
| `plugin.yml` | command, permissions, softdepend |
| runtime `caches/<name>.yml` | per-cache definition (не в jar defaults — создаётся на диске) |

- Ключи: **kebab-case** (`mode-timeout`, `name-cache` placeholders).
- Плейсхолдер тайника в UI: **`{name-cache}`**.
- `config-version: 1.4` — не повышать в рамках текущей линии без запроса.
- Материалы/звуки: Bukkit enum names (`TRIPWIRE_HOOK`, `BLOCK_LEVER_CLICK`).

### Сообщения и цвета

- Игрок/консоль: `HexColors` + `ActionManager` / menu click tags.
- Не хардкодь длинные UX-строки в commands — actions.yml path или menu YAML.
- Логи плагина: `CachesManager.log` / `error` / `console` (hex-friendly).

### Permissions (plugin.yml)

- Admin ops: `cachesmanager.createcache`, `.menu`, `.givekey`, `.deletecache`, `.listcaches`, `.reload`, `.cancel`, `.admin`.
- Player open: `cachesmanager.opencache` default true.
- Per-menu: e.g. `cachesmanager.openmenu.global-menu`.

---

## 5. Производительность

1. **Не** блокируй main thread JDBC — async executor + history flush task.
2. Open path: короткий cooldown; не запускай вторую анимацию на `inUse` cache.
3. Holograms: update lines если line count тот же; recreate при смене line count / location / platform.
4. Menu update-interval — не спамь full rebuild без нужды; `update: false` на static items.
5. Soft-depends: class/plugin checks at platform construct / plugin events.
6. Batch save cache configs (`CacheConfigHandler` batch saver) — не `save` на каждый keystroke без debounce path.
7. History prune: max-entries / max-days; не копи limit без нужды.
8. Shade только bstats + HikariCP — не тащи новые fat-deps в shade.

---

## 6. Качество кода

### Обязательно

- **Без комментариев в Java** (ни `//`, ни `/* */`, ни Javadoc в новом коде). YAML — ок.
- Код компилируется под **Java 17** и Paper API из `pom.xml`.
- Совместимость 1.16+: feature-detect, soft-depend fallbacks.
- Null-safety для world/location/player/item; early return.
- `inUse` всегда сбрасывается на cancel / empty loot / error paths.
- Сообщения игроку — actions / HexColors / menu commands.
- PDC keys через `CacheKeys`, не ad-hoc strings.

### Запрещено без причины

- Новые тяжёлые зависимости в shade.
- Синхронный SQL на main thread в hot path (open, click, animation tick).
- Ломающие изменения схемы БД / PDC / `key-uuid` без обсуждения.
- Bump `config-version` без миграции.
- Копипаста из `references/DeluxeMenus` в runtime «как есть» — только как reference UX/YAML ideas.
- Второй параллельный menu/action engine.

### Тесты / проверка

- Maven compile как минимум после нетривиальных правок: `mvn -q -DskipTests package`.
- Ручные сценарии: create cache, place block, give key, open (animation), menu loot/chance, `/cm reload`, key integrity, hologram platform switch.

---

## 7. Правила рефакторинга

1. **Сначала reuse** — расширь Manager/Handler/Platform/Opening, не плоди параллельные системы.
2. Сохраняй точки входа: `CacheInteractionHandler.open`, `StandardCacheOpening.start`, `ConfigManager.executeActions`, `HologramManager.create/update/remove`, menu click tags, `CacheKeys`.
3. Рефакторинг «ради красоты» без perf/clarity — не делать.
4. При изменении `HologramPlatform` — обновить **все** реализации + `selectPlatform`.
5. При изменении open/animation — проверить `inUse`, orphaned animation, hologram restore, stats write.
6. `detect_changes` / `trace_path inbound` перед удалением метода.

Перед созданием нового класса,
менеджера,
утилиты,
enum,
интерфейса
или сервиса

обязательно проверить,
нельзя ли расширить уже существующую реализацию.

Предпочитать изменение существующего кода
созданию нового.

---

## 8. Принятие решений и вопросы

| Ситуация | Поведение |
|----------|-----------|
| Есть **объективно лучший** вариант (perf main-thread, CAS inUse, soft-depend order, существующий tag/action) | **Делай сам**, кратко объясни в ответе |
| Выбор **меняет геймплей / UX / конфиг-контракт** (шансы, actions, menu defaults, права, key format) | **Спроси** |
| Неясно требование | 1–2 уточнения; не блокируй работу на мелочах |
| Стиль vs новый framework | **Стиль проекта** побеждает |

### Не усложнять

- Не вводи DI-framework, event-bus, multi-module без запроса.
- Не добавляй abstraction layer «на будущее», если 1–2 call sites.
- Новая сущность — только если не вписывается в Manager/Handler/Platform/Opening/Mode/Action tag.

### Стиль

- Следуй соседним файлам: lombok, early-return, switch на action tags, Concurrent* для shared state.
- Имена domain-oriented: `open`, `start`, `enableMode`, `selectPlatform`, `executeActions`.
- Русский в user-facing strings / console logs; код и AGENTS — технический ясный язык.

---

## 9. Типовой workflow агента

1. Уточни задачу; **не** исследуй репозиторий, если ответ уже ясен.
2. При необходимости (граф/символ): `search_graph` / `trace_path` — точечно.
3. Открывай файлы **по одному/по мере нужды**, не пачкой «все связанные».
4. Минимальный diff (Ponytail + reuse-first) в существующих слоях.
5. YAML-compat и `config-version: 1.4`.
6. Compile / критичный сценарий при нетривиальных правках.
7. Краткий отчёт: что/зачем, без воды.

---

## 10. Быстрые «не ломай»

| Область | Правило |
|---------|---------|
| Open concurrency | `inUse.compareAndSet(false, true)`; всегда release на fail paths |
| Message actions | не `trim()` полный message content; один leading space after `]` |
| Keys | PDC `CACHE_NAME` + `KEY_UUID` + appearance stamp; UUID в cache config |
| Hologram platform order | DH → Fancy (bridge) → Modern TextDisplay → Legacy |
| Hologram during open | remove at start; restore after finish/cancel |
| DB | stats/history only; cache defs stay YAML; async write |
| Shade | only bstats + HikariCP |
| config-version | `1.4` — no silent bump |
| references/ | not runtime; not in graph index |
| Menu special tags | extend existing click-command switch / special registry |
| Mode timeout | respect `settings.mode-timeout`; cancel via `/cm cancel` |

---

## 11. Project graph snapshot (ориентир)

После индекса (moderate): ~**1587** nodes, ~**5898** edges (Java + YAML).

Ключевые хабы по fan-in (ориентир, не догма):

- `Cache` (domain entity)
- `CachesManager` (plugin hub: log/error/console/getters)
- `ConfigManager.executeActions` / `ActionManager`
- `CacheConfigHandler.loadCacheConfig` / `saveCacheConfig`
- `HexColors.translate`
- `CacheOpening.getCache` / `getPlayer`
- Menu path: `openMenu`, `updateMenu`, `executeClickCommands`
- Animation path: `startAnimation`, `trackPhaseTask`, `finishAnimation`
- Hologram path: `selectPlatform`, `createHologram`, `updateHologram`
- Modes: `enableMode`, `ChatModeHandler` implementors
- Keys: `matchesKeyAppearance`, `isAnyKey`, `repairKeyStack`

Интерфейсы расширения:

- `HologramPlatform`
- `CacheOpening`
- `ChatModeHandler`
- `SubCommand`
- `AnimationView`
- `MenuClickDelegate`
- `AnimationEngineConfigurator.PacketItemMetadataSender`

---

*Конец AGENTS.md. При конфликте: этот файл + фактический код > догадки. При сомнении — graph-first, reuse-first, style-first.*
