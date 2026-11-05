/* OSify JNI lifecycle adapter for the unmodified Limbo QEMU libraries.
 * Based on the call contract of Limbo's vm-executor-jni.c, Max Kastanas 2012.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {return JNI_VERSION_1_6;}

JNIEXPORT jstring JNICALL Java_com_max2idea_android_limbo_jni_VMExecutor_start(
        JNIEnv *env, jobject self, jstring storage, jstring base,
        jstring library, jstring library_path, jint scale, jobjectArray arguments) {
    int argc=(*env)->GetArrayLength(env,arguments);
    if(argc<1 || argc>128) return (*env)->NewStringUTF(env,"Error: invalid QEMU arguments");
    char **argv=calloc((size_t)argc+1,sizeof(char *));
    if(!argv) return (*env)->NewStringUTF(env,"Error: no argument memory");
    for(int i=0;i<argc;i++) {
        jstring item=(jstring)(*env)->GetObjectArrayElement(env,arguments,i);
        const char *text=(*env)->GetStringUTFChars(env,item,0);
        argv[i]=strdup(text);
        (*env)->ReleaseStringUTFChars(env,item,text);
        (*env)->DeleteLocalRef(env,item);
        if(!argv[i]) return (*env)->NewStringUTF(env,"Error: no argument memory");
    }
    const char *path=(*env)->GetStringUTFChars(env,library_path,0);
    void *qemu=dlopen(path,RTLD_NOW|RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env,library_path,path);
    if(!qemu) return (*env)->NewStringUTF(env,dlerror());
    void *compat=dlopen("libcompat-limbo.so",RTLD_NOW|RTLD_LOCAL);
    void (*setup)(JNIEnv *,jobject,jclass,const char *,const char *)=compat?dlsym(compat,"set_jni"):NULL;
    void (*init)(int,char **,char **)=dlsym(qemu,"qemu_init");
    void (*loop)(void)=dlsym(qemu,"qemu_main_loop");
    void (*cleanup)(void)=dlsym(qemu,"qemu_cleanup");
    if(!setup || !init || !loop || !cleanup) return (*env)->NewStringUTF(env,"Error: missing QEMU lifecycle symbol");
    const char *storage_text=(*env)->GetStringUTFChars(env,storage,0);
    const char *base_text=(*env)->GetStringUTFChars(env,base,0);
    /* Limbo stores these pointers. Keep copies alive until the service process exits. */
    setup(env,self,(*env)->GetObjectClass(env,self),strdup(storage_text),strdup(base_text));
    (*env)->ReleaseStringUTFChars(env,storage,storage_text);
    (*env)->ReleaseStringUTFChars(env,base,base_text);
    init(argc,argv,NULL);
    loop();
    cleanup();
    /* QEMU worker callbacks can still refer to DSO code after cleanup. Do not dlclose:
     * Android disposes this private process after Room has committed the stopped state.
     * Keeping the DSO mapped eliminates the upstream unload/use-after-free race.
     */
    return (*env)->NewStringUTF(env,"VM shutdown");
}
