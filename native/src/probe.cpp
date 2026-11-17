// probe — первая проверка на магнитоле: грузится ли вендорная библиотека камер
// и создаётся ли менеджер камер. Ничего не записывает и не открывает камеры.
//
//   su -c /data/local/tmp/gpstrips-probe

#include <dlfcn.h>
#include <unistd.h>

#include <cstdio>
#include <cstring>

static const char *kLib = "liblitecamdev.so";

int main() {
    printf("gpstrips probe: uid=%d\n", getuid());

    void *lib = dlopen(kLib, RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        printf("dlopen(%s) failed: %s\n", kLib, dlerror());
        return 1;
    }
    printf("dlopen(%s) ok, handle=%p\n", kLib, lib);

    const char *names[] = {
        "createCamerasMgr",
        "_ZN3Mtk13LiteCameraMgr13getCameraListEv",
        "_ZN3Mtk13LiteCameraMgr10openCameraEi",
        "_ZN3Mtk16LiteCameraDevice5startEv",
        "_ZN3Mtk16LiteCameraDevice11requestExitEv",
    };
    for (const char *n : names) {
        void *p = dlsym(lib, n);
        printf("  %-45s %p%s\n", n, p, p ? "" : "  (нет)");
    }

    auto create = (void *(*)())dlsym(lib, "createCamerasMgr");
    if (!create) {
        printf("createCamerasMgr not found\n");
        return 2;
    }
    void *mgr = create();
    printf("createCamerasMgr() = %p\n", mgr);
    if (!mgr) {
        printf("менеджер камер не создан\n");
        return 3;
    }

    printf("готово: библиотека грузится, менеджер создаётся\n");
    return 0;
}
