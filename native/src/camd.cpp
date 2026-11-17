// camd — снимок с четырёх камер кругового обзора на магнитоле Geely Okavango.
//
//   camd --probe            что видно из вендорной библиотеки камер
//   camd --shot DIR         снимок в DIR: front.jpg/back.jpg/left.jpg/right.jpg
//                           или сырой кадр кадрами: frame.yuv (5120×800, YUYV)
//
// Работает под root: камеры отдаёт /vendor/lib64/liblitecamdev.so, обычному
// приложению она недоступна. Пока снимаем — держим камеры, после кадра сразу
// отпускаем, чтобы штатный обзор 360 открывался как обычно.
//
// Что уже известно про библиотеку (docs/avm-capture.md):
//   createCamerasMgr()                  -> Mtk::LiteCameraMgr*
//   Mtk::LiteCameraMgr::openCamera(int)  виртуальный слот 1, результат через x8
//   Mtk::LiteCameraDevice::start()       запускает поток кадров
//   Mtk::LiteCameraFrame::getImgBuffer() доступ к картинке в колбэке
//
// Сам колбэк (регистрация слушателя) на этой прошивке не импортируется по
// имени — это виртуальный метод. Его номер слота печатает --probe.

#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <string>

namespace {

const char *kLib = "liblitecamdev.so";

// Символы, которые нужны обязательно.
struct Api {
    void *lib = nullptr;
    void *(*createCamerasMgr)() = nullptr;
    void (*getCameraList)() = nullptr;
    void (*openCamera)() = nullptr;
    void (*start)() = nullptr;
    void (*requestExit)() = nullptr;
};

std::string dirPath(const char *arg) {
    std::string dir = arg ? arg : "";
    return dir.empty() ? "/sdcard/GpsTrips/cam" : dir;
}

void writeError(const std::string &dir, const std::string &text) {
    std::string path = dir + "/error.txt";
    FILE *f = fopen(path.c_str(), "w");
    if (!f) return;
    fprintf(f, "%s\n", text.c_str());
    fclose(f);
}

// Смещение адреса от начала библиотеки: печатать абсолютные адреса бесполезно
// (ASLR), а смещения можно сверить с таблицей символов на компьютере.
long offsetIn(void *addr, void *base) {
    if (!addr || !base) return -1;
    return static_cast<long>(reinterpret_cast<char *>(addr) - reinterpret_cast<char *>(base));
}

int probe() {
    Api api;
    api.lib = dlopen(kLib, RTLD_NOW | RTLD_LOCAL);
    if (!api.lib) {
        printf("dlopen(%s): %s\n", kLib, dlerror());
        return 1;
    }

    Dl_info info{};
    if (!dladdr(api.lib, &info)) {
        printf("dladdr: база библиотеки неизвестна\n");
        return 1;
    }
    void *base = info.dli_fbase;
    printf("библиотека %s загружена, база %p\n", kLib, base);

    api.createCamerasMgr = reinterpret_cast<void *(*)()>(dlsym(api.lib, "createCamerasMgr"));
    api.getCameraList = reinterpret_cast<void (*)()>(dlsym(api.lib, "_ZN3Mtk13LiteCameraMgr13getCameraListEv"));
    api.openCamera = reinterpret_cast<void (*)()>(dlsym(api.lib, "_ZN3Mtk13LiteCameraMgr10openCameraEi"));
    api.start = reinterpret_cast<void (*)()>(dlsym(api.lib, "_ZN3Mtk16LiteCameraDevice5startEv"));
    api.requestExit = reinterpret_cast<void (*)()>(dlsym(api.lib, "_ZN3Mtk16LiteCameraDevice11requestExitEv"));

    printf("createCamerasMgr          %+ld\n", offsetIn(reinterpret_cast<void *>(api.createCamerasMgr), base));
    printf("LiteCameraMgr::getCameraList %+ld\n", offsetIn(reinterpret_cast<void *>(api.getCameraList), base));
    printf("LiteCameraMgr::openCamera    %+ld\n", offsetIn(reinterpret_cast<void *>(api.openCamera), base));
    printf("LiteCameraDevice::start      %+ld\n", offsetIn(reinterpret_cast<void *>(api.start), base));
    printf("LiteCameraDevice::requestExit %+ld\n", offsetIn(reinterpret_cast<void *>(api.requestExit), base));

    if (!api.createCamerasMgr) {
        printf("нет createCamerasMgr\n");
        return 2;
    }

    void *mgr = api.createCamerasMgr();
    printf("createCamerasMgr() = %p\n", mgr);
    if (!mgr) {
        printf("менеджер камер не создан\n");
        return 3;
    }

    // Первые шесть слотов таблицы виртуальных функций: по ним видно, какой
    // слот занят списком камер, какой — открытием, какой — деструктором.
    void **vtable = *reinterpret_cast<void ***>(mgr);
    printf("vtable %p (смещение %+ld)\n", vtable, offsetIn(vtable, base));
    for (int i = 0; i < 6; ++i) {
        void *entry = vtable ? vtable[i] : nullptr;
        printf("  слот %d: %+ld\n", i, offsetIn(entry, base));
    }

    fflush(stdout);
    return 0;
}

int shot(const std::string &dir) {
    // Снимать пока нечем: не подтверждено, как библиотека отдаёт кадры.
    // Сообщаем это текстом — приложение отправит текст на сайт.
    writeError(dir, "камеры: порядок выдачи кадров ещё не подтверждён (см. camd --probe)");
    printf("снимок не сделан: сначала нужен вывод camd --probe\n");
    return 1;
}

}  // namespace

int main(int argc, char **argv) {
    std::string mode = argc > 1 ? argv[1] : "--help";

    if (mode == "--probe") return probe();

    if (mode == "--shot") {
        std::string dir = dirPath(argc > 2 ? argv[2] : nullptr);
        return shot(dir);
    }

    printf(
        "camd — снимок с камер 360\n"
        "  camd --probe          что видно из вендорной библиотеки камер\n"
        "  camd --shot [DIR]     снимок в DIR (по умолчанию /sdcard/GpsTrips/cam)\n");
    return 2;
}
