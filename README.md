<p align="center">
  <img src="assets/icon.svg" width="88" height="88" alt="Max-X"/>
</p>

<h1 align="center">Max-X</h1>

<p align="center">
  Приватный Android-клиент для сервиса <b>MAX</b><br/>
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

Max-X — форк протокола MAX/, написанный с нуля на Kotlin + Jetpack Compose.

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


