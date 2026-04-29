# Changelog

## [1.0.0] — 2025

### Added
- Полная реализация протокола MAX/oneme v11 (msgpack + LZ4)
- NetworkGuard whitelist: только `api.oneme.ru`, `ws-api.oneme.ru`, `i.oneme.ru`
- E2E шифрование: Android Keystore + RSA-2048 OAEP + AES-256-GCM
- Папки-фильтры чатов с синхронизацией на сервер
- Быстрые ответы из уведомлений (RemoteInput / MessagingStyle)
- Избранные сообщения (локально, с поиском)
- Экспорт переписки: TXT / HTML / JSON
- Отправка и отображение медиа: фото, видео, аудио, файлы, голосовые
- Спуфинг сессии (6 пресетов устройств)
- Поддержка Android 5.0+ (minSdk 21)
- SplashScreen API (compat + native API 31+)
- Адаптивные иконки для всех densities

### Security
- Удалён `HOST_REACHABILITY` opcode (VPN-детектор)
- Заблокированы все телеметрия-хосты, ИИ-сервисы, VK/OK CDN
- `network_security_config.xml` — cleartext запрещён, whitelist на уровне OS
