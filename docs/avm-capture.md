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
