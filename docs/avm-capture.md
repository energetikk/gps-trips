# Камеры 360: как это устроено и как повторяем

Черновик рабочих заметок. Всё, что ниже, проверено по образу прошивки
`D:\OkaAQH59-Lunaris` (vendor.bin) и по APK `avmdvr-1.1.0.apk`.
Всё, что не проверено, помечено **«уточнить»**.

## Железо и прошивка

- магнитола `IHU720P`, платформа `ecarxp`, `ro.product.vendor.model=G836J`;
- `ro.build.version.sdk = 28` (Android 9), `arm64-v8a`;
- вендорная библиотека камер `/vendor/lib64/liblitecamdev.so`,
  sha256 `f34ab7c8657945912dfbe6f96df0189d9d788fd18d2ceb78153ab0b7fb74ed0a`
  — та самая, что проверяет автор «Регистратора 360»;
- штатный обзор 360 — `/vendor/bin/ecarx_avm` (сервис `ecarx_avm`, группа `camera`);
- штатный RVC — `/vendor/bin/litecamrvcdev` (сервис `litecamrvcdev`).

## API вендорной библиотеки (символы экспортированы, не скрыты)

| Символ | Назначение |
|---|---|
| `createCamerasMgr` | создать `Mtk::LiteCameraMgr*` |
| `Mtk::LiteCameraMgr::getCameraList()` | список доступных камер (тип возврата **уточнить**) |
| `Mtk::LiteCameraMgr::openCamera(int camType)` | открыть камеру; возвращает `sp<Mtk::LiteCameraDevice>` (возврат через указатель, sret) |
| `Mtk::LiteCameraDevice::start()` / `requestExit()` / `stopCamio()` | запуск и остановка потока кадров |
| `Mtk::LiteCameraFrame::getImgBuffer()` / `getGraphicBuffer()` | доступ к кадру |
| `Mtk::ICameraFrameListener::onNewCameraFrame(sp<Mtk::ICameraFrame>)` | колбэк с кадром |
| `surfaceView::*` | отрисовка поверх (нам не нужна) |

Из `openCamera` видно: менеджер держит `std::map<int camType, ...>`; для найденного
типа берётся `sensorIdx`, под объект устройства выделяется 0x4f0 байт.
Если типа нет — в лог уходит `can not find camType:%d` и возвращается пустой указатель.

## Готовый образец: `/vendor/bin/litecamdev`

В прошивке лежит вендорная тестовая программа (`LiteCamTest`), в ней сохранились
имена функций (`main`, `myCameraFrameListener::onNewCameraFrame`). Её сценарий:

```
dlopen("liblitecamdev.so")
  dlsym("createCamerasMgr")      -> mgr
  mgr->getCameraList()           // "find avm camera %d.."
  mgr->openCamera(idx)           // "open avm camera %d.."
  подписка на кадры (myCameraFrameListener)
  device->start()                // "starting..."
  onNewCameraFrame -> кадр в очередь -> запись в /data/src_avm.yuv
  device->requestExit(); dlclose()
```

Это и есть наш каркас: своя программа повторяет эту последовательность, но вместо
файла YUV отдаёт JPEG.

## Формат кадра

YUYV **5120×800** — четыре плитки по 1280×800 (перед, зад, лево, право).
Подтверждено строками в бинарнике автора: `is not a multiple of a YUYV 5120x800 frame`,
`kTileWidth`, `tile = min(x / kTileWidth, 3)`.

## Не блокировать штатный обзор 360

Правило, которое использует автор (и которое закреплено в его супервизоре):

- камеры открыты, только пока `getprop ecarx.camera.openstate` = `0`
  и `service.instantcam.running` = `0`;
- когда штатный обзор открывают (`openstate` = 1), поток надо закрыть;
  иначе обзор остаётся чёрным, а камеры заняты нами;
- после закрытия обзора — открыть снова;
- при аварийном выходе освобождать ISP-флаг (`--clear-cams` у автора), иначе
  камеры остаются «занятыми» до перезагрузки.

**Уточнить:** точные ioctl на `/dev/camera-isp` (`CAM_%c taken`) и реакцию
`Mtk::LiteCameraDevice` на закрытие обзора (у автора это код выхода 5).

## Контракт camd

Приложение и `camd` договариваются файлами, без сокетов:

```
camd --shot /sdcard/GpsTrips/cam
```

- открыть камеры, дождаться кадра, разрезать его на четыре плитки;
- записать `front.jpg`, `back.jpg`, `left.jpg`, `right.jpg`
  **или** сырой кадр целиком в `frame.yuv` (5120×800, YUYV, 8 192 000 байт) —
  тогда его режет и сжимает приложение;
- при ошибке — `error.txt` с текстом и код возврата не 0;
- в любом случае освободить камеры перед выходом.

Сырой кадр удобнее на первом этапе: в C++ не нужен ни JPEG-кодер, ни работа с
цветом, вся обработка остаётся в приложении, где она проверяется юнит-тестами.

Приложение ищет бинарник в `/data/adb/gpstrips-cam/bin/camd`, затем в
`/data/local/tmp/camd`, запускает через `su -c` и ждёт до 25 с.

Режим демона (`--serve`) добавим, когда понадобится поток: он будет держать
камеры и отдавать кадр по `want`, следя за `ecarx.camera.openstate`.

## Как сайт просит снимок

1. Страница: `POST /api/camera/request` — сервер запоминает время запроса.
2. Приложение отправляет точку `POST /api/position` (раз в 15 с, пока машина
   стоит) и получает в ответе `{"ok":true,"camera":<время>}`.
3. Если время новое — приложение делает снимок и шлёт `POST /api/camera`
   с четырьмя кадрами в base64.
4. Страница опрашивает `GET /api/camera` и показывает кадры.

Если снимок не удался, приложение всё равно отвечает серверу — с текстом
ошибки и без кадров, чтобы страница не ждала вечно.

## Открытый вопрос: как приходят кадры

В `liblitecamdev.so` нет ни `lcSetFrameListener`, ни вообще слова «listener»,
хотя вендорный тест `litecamdev` свой `myCameraFrameListener` куда-то передаёт.
Значит регистрация идёт через виртуальный метод `Mtk::ICameraClient` (от него
наследуется `Mtk::LiteCameraDevice`) или через `Mtk::LiteCameraDevice` напрямую.

Что уже известно из дизассемблера `main` вендорного теста:

- `createCamerasMgr()` возвращает указатель в `x0` — это `Mtk::LiteCameraMgr*`;
- `LiteCameraMgr::openCamera(int)` вызывается **виртуально, слот 1** таблицы
  (`ldr x8,[x0]; ldr x9,[x8,#8]; blr x9`) и возвращает результат через `x8`
  (sret) — то есть `sp<Mtk::LiteCameraDevice>`;
- слушатель — объект на 0x18 байт с двумя таблицами виртуальных функций
  (основная по смещению 0, вторая по 0x70) — наследование от двух интерфейсов;
- кадр в колбэке приходит как `android::sp<Mtk::ICameraFrame>`, доступ к
  картинке — `Mtk::LiteCameraFrame::getImgBuffer()`.

Ещё две находки:

- в штатном `ecarx_avm` из всего `Mtk` импортируется **только конструктор**
  `Mtk::LiteCameraMgr::LiteCameraMgr()` — значит остальные методы класса
  вызываются через таблицу виртуальных функций, а не по именам;
- таблица `LiteCameraMgr` — 4 слота; слот 1 занят `openCamera(int)`, остальные
  деструктор и, видимо, список камер.

Отсюда рабочий план на живом устройстве: поднять `Mtk::LiteCameraMgr`
конструктором (он импортирован!), дальше идти по слотам таблицы, а не искать
имена. Регистрация слушателя — тоже виртуальный метод (`LiteCameraDevice`
наследует `ICameraClient`, у него 33 слота).

Самый быстрый способ проверить всё это — запустить вендорный тест на магнитоле:

```sh
su -c /vendor/bin/litecamdev
# в другом окне: adb shell su -c logcat -s LiteCamTest LiteCameraMgr ecarx_avm
# результат: /data/src_avm.yuv — сырой кадр 5120×800
```

Он ничего не устанавливает и ничего не ломает: это штатная программа из
прошивки.

## Наша архитектура

Как у автора — демон с автозапуском, а не разовый запуск:

```
/data/adb/gpstrips-cam/          рабочая папка
  bin/camd                       наш бинарник
  supervise.sh                   следит за camd, стартует после загрузки
/data/adb/service.d/gpstrips-cam.sh   хук Magisk
/sdcard/GpsTrips/cam/            обмен с приложением
  cam.want                       запрос снимка (приложение трогает файл)
  front.jpg back.jpg left.jpg right.jpg
  cam.json                       время, статус, ошибка
```

`camd` держит камеры открытыми, следит за `ecarx.camera.openstate` и отпускает
камеры, как только открывают штатный обзор. По запросу пишет четыре JPEG
(склейку делает клиент).

## Этапы

1. `native/probe` — dlopen, `createCamerasMgr`, список камер. Проверка: печатает
   список без падений.
2. `native/camd` — открыть AVM-камеру, получить кадр, разрезать на 4 JPEG.
3. Супервизор + хук Magisk + реакция на `openstate`.
4. Сервер: `POST /api/camera`, флаг запроса в ответе `/api/position`.
5. Страница: кнопка и режимы «отдельно / 2×2 / одна камера».
