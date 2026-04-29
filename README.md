<p align="center">
  <img src="assets/icon.svg" width="88" height="88" alt="Max-X"/>
</p>

<h1 align="center">Max-X</h1>

<p align="center">
  Приватный Android-клиент для сервиса <b>MAX (oneme.ru)</b><br/>
  Kotlin · Jetpack Compose · Нулевая телеметрия
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-5.0%2B-brightgreen?style=flat-square&logo=android"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat-square&logo=kotlin"/>
  <img src="https://img.shields.io/badge/Compose-BOM%202025.05-4285F4?style=flat-square&logo=jetpackcompose"/>
  <img src="https://img.shields.io/badge/Telemetry-ZERO-8CBF26?style=flat-square"/>
  <img src="https://img.shields.io/badge/minSdk-21-orange?style=flat-square"/>
</p>

---

## О проекте

Max-X — форк протокола MAX/oneme.ru, написанный с нуля на Kotlin + Jetpack Compose.

**Что отличает от официального клиента:**

- Все сетевые запросы идут **только** на `api.oneme.ru` — ни один сторонний хост не получает данные
- Полностью удалены: ИИ-функции (D2VerbAI / Думейт), VPN-детектор (`HOST_REACHABILITY`), аналитика, пуш через Firebase
- E2E шифрование через Android Keystore (RSA-2048 + AES-256-GCM) — опциональное, ключи не покидают устройство
- Спуфинг сессии — маскировка под официальный клиент

---

## Функционал

| Раздел | Возможности |
|--------|-------------|
| **Чаты** | Список чатов, папки-фильтры (Личные / Группы / Каналы / Непрочитанные), поиск, статус «печатает» |
| **Сообщения** | Отправка/получение, ответ, редактирование, удаление у всех, реакции, пересылка |
| **Медиа** | Фото, видео, аудио, голосовые, файлы до 50 МБ — отправка и отображение в чате |
| **Шифрование** | E2E опционально для личных чатов — Android Keystore, сервер видит только зашифрованный блоб `__e2e__` |
| **Уведомления** | MessagingStyle, быстрые ответы (RemoteInput) прямо из шторки, кнопка «Прочитано» |
| **Папки** | Создание пользовательских папок, фильтрация по типу/статусу, синхронизация с сервером |
| **Избранное** | Сохранение сообщений локально, поиск по сохранённым |
| **Экспорт** | Переписка в `.txt`, `.html` (dark theme), `.json` — share через FileProvider |
| **Контакты** | Алфавитный список, онлайн-статус, поиск, подписка на присутствие |
| **Каналы** | Список каналов, подписчики, переход в чат |
| **Настройки** | Приватность, безопасность (PIN / биометрия), SOCKS5 прокси, уведомления, спуфинг |
| **Профиль** | E2E toggle, избранное, активные сессии |

---

## Безопасность

### NetworkGuard

Единственный разрешённый трафик:

```
api.oneme.ru:443
ws-api.oneme.ru:443 / 8443
i.oneme.ru:443
```

NetworkGuard работает на уровне `SocketFactory` — **до** установки TLS-соединения. Любой хост вне whitelist выбрасывает `SecurityException`. Дополнительно продублировано в `network_security_config.xml`.

**Заблокировано (выборка):**

| Категория | Хосты |
|-----------|-------|
| IP-детекция (деанон VPN) | `api.ipify.org`, `checkip.amazonaws.com`, `ifconfig.me`, … |
| VPN-детект (ТСПУ) | `main.telegram.org`, `gstatic.com`, `gosuslugi.ru`, … |
| Аналитика | `firebase.googleapis.com`, `sentry.io`, `amplitude.com`, … |
| ИИ-сервисы MAX | `d2verb.ai`, `dumate.ru`, `ai.max.ru` |
| VK/OK экосистема | `vk.com`, `ok.ru`, `mail.ru`, `mycdn.me`, … |

Опкод `HOST_REACHABILITY` (5) — шпионский модуль MAX — **намеренно отсутствует** в протоколе.

### E2E шифрование

```
Sender:  AES-256-GCM(message) → ciphertext
         RSA-2048-OAEP(aes_key, recipient_pub) → enc_key
         send: {"c": ciphertext, "i": iv, "k": enc_key}  ← префикс __e2e__

Recipient: RSA-2048-OAEP.decrypt(enc_key, my_priv) → aes_key
           AES-256-GCM.decrypt(ciphertext, aes_key) → plaintext
```

Приватный ключ хранится в **Android Keystore**, никогда не экспортируется.

---

## Стек

| Слой | Технологии |
|------|-----------|
| UI | Jetpack Compose, Material3, Navigation Compose |
| Архитектура | MVVM, StateFlow, унифицированный `AppContainer` (DI без Hilt) |
| Сеть | TLS/SSL сокет напрямую, msgpack + LZ4, reconnect backoff |
| Хранилище | SharedPreferences, DataStore Preferences |
| Шифрование | Android Keystore, RSA-2048 OAEP, AES-256-GCM |
| Изображения | Coil |
| Сборка | Gradle 8.11, AGP 8.10, KSP, Version Catalog |

---

## Сборка

**Требования:** JDK 17+, Android Studio Hedgehog или новее.

```bash
git clone https://github.com/SelfC0de/Max-X.git
cd Max-X
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

**Release:**

```bash
# Создайте keystore.properties в корне:
# storeFile=path/to/keystore.jks
# storePassword=...
# keyAlias=...
# keyPassword=...

./gradlew assembleRelease
```

---

## Структура проекта

```
app/src/main/java/ru/maxx/app/
├── core/
│   ├── crypto/         E2ECrypto — Android Keystore + AES-GCM
│   ├── export/         ExportService — TXT / HTML / JSON
│   ├── network/        MaxSocket, SessionManager
│   ├── notification/   NotificationService, QuickReplyReceiver
│   ├── protocol/       MaxProtocol — msgpack + LZ4 кодек
│   ├── security/       NetworkGuard — whitelist сокетов
│   └── spoofing/       SpoofingManager — маскировка девайса
├── data/
│   ├── model/          Chat, Message, Contact, Attachment, …
│   ├── prefs/          AuthPrefs, AppPrefs
│   └── repository/     Chat, Message, Contact, Media, E2E, Folders, Favorites
├── di/
│   └── AppContainer.kt ленивый DI
└── ui/
    ├── screens/        auth / chats / chat / contacts / channels
    │                   profile / settings / favorites
    ├── components/     MaxXTopBar, AvatarView, UnreadBadge, …
    ├── navigation/     AppNavGraph
    └── theme/          Цвета (#0D0D14 / #8CBF26), Typography
```

---

## Известные ограничения

- Звонки не реализованы (требуют WebRTC + CDN `calls.okcdn.ru`, который намеренно заблокирован)
- Регистрация нового аккаунта — только через официальный клиент или web
- Push-уведомления при закрытом приложении требуют собственного FCM/фонового сервиса

---

## Лицензия

```
MIT License — свободное использование, модификация и распространение.
Данный форк не аффилирован с ООО «ВК» / MAX / oneme.ru.
```

---

## Первая публикация на GitHub

```bash
# 1. Распакуй архив
unzip Max-X-github.zip
cd Max-X

# 2. Инициализируй репозиторий
git init
git add .
git commit -m "feat: initial release v1.0.0"

# 3. Подключи remote и запушь
git remote add origin https://github.com/SelfC0de/Max-X.git
git branch -M main
git push -u origin main
```

## Пуш обновлений

```bash
# Обновить один файл
git add app/src/main/java/ru/maxx/app/ui/screens/chat/ChatScreen.kt
git commit -m "fix: описание изменения"
git push

# Обновить всё изменённое
git add .
git commit -m "feat: описание обновления"
git push

# Обновить и сразу запустить билд (указываешь тег и описание в Actions UI)
git add .
git commit -m "release: v1.0.1"
git push
# → GitHub → Actions → Build Max-X → Run workflow → v1.0.1
```

## Запуск сборки APK вручную

```
GitHub → Actions → Build Max-X → Run workflow
  version_tag:    v1.0.1
  release_notes:  что изменилось
→ Releases → Pre-release → Max-X.apk
```
