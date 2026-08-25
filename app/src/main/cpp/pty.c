#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

static void throw_io(JNIEnv *env, const char *operation) {
    int saved_errno = errno;
    char message[256];
    const char *detail = strerror(saved_errno);
    int written = snprintf(message, sizeof(message), "%s: %s", operation, detail);
    if (written < 0) {
        strcpy(message, "PTY operation failed");
    }
    jclass exception = (*env)->FindClass(env, "java/io/IOException");
    if (exception != NULL) {
        (*env)->ThrowNew(env, exception, message);
    }
}

JNIEXPORT jintArray JNICALL
Java_ai_alaser_app_terminal_NativePtyBridge_nativeOpen(
    JNIEnv *env,
    jobject receiver,
    jstring working_directory,
    jint rows,
    jint columns
) {
    (void) receiver;
    const char *directory = (*env)->GetStringUTFChars(env, working_directory, NULL);
    if (directory == NULL) {
        return NULL;
    }

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) < 0 || unlockpt(master) < 0) {
        if (master >= 0) close(master);
        (*env)->ReleaseStringUTFChars(env, working_directory, directory);
        throw_io(env, "Could not initialize pseudo-terminal");
        return NULL;
    }

    char slave_path[128];
    if (ptsname_r(master, slave_path, sizeof(slave_path)) != 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, working_directory, directory);
        throw_io(env, "Could not resolve pseudo-terminal slave");
        return NULL;
    }

    struct winsize size = {
        .ws_row = rows > 0 ? (unsigned short) rows : 30,
        .ws_col = columns > 0 ? (unsigned short) columns : 100,
        .ws_xpixel = 0,
        .ws_ypixel = 0,
    };
    if (ioctl(master, TIOCSWINSZ, &size) < 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, working_directory, directory);
        throw_io(env, "Could not set pseudo-terminal size");
        return NULL;
    }

    pid_t child = fork();
    if (child < 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, working_directory, directory);
        throw_io(env, "Could not start pseudo-terminal shell");
        return NULL;
    }

    if (child == 0) {
        if (setsid() < 0) _exit(126);
        int slave = open(slave_path, O_RDWR);
        if (slave < 0) _exit(126);
        if (ioctl(slave, TIOCSCTTY, 0) < 0) _exit(126);
        if (dup2(slave, STDIN_FILENO) < 0) _exit(126);
        if (dup2(slave, STDOUT_FILENO) < 0) _exit(126);
        if (dup2(slave, STDERR_FILENO) < 0) _exit(126);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        if (chdir(directory) < 0) _exit(126);
        setenv("TERM", "xterm-256color", 1);
        execl("/system/bin/sh", "sh", "-i", (char *) NULL);
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, working_directory, directory);
    jint values[2] = {master, (jint) child};
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        kill(child, SIGKILL);
        close(master);
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_ai_alaser_app_terminal_NativePtyBridge_nativeRead(
    JNIEnv *env,
    jobject receiver,
    jint descriptor
) {
    (void) receiver;
    uint8_t buffer[8192];
    ssize_t count;
    do {
        count = read(descriptor, buffer, sizeof(buffer));
    } while (count < 0 && errno == EINTR);

    if (count <= 0) {
        if (count < 0 && errno != EIO && errno != EBADF) {
            throw_io(env, "Pseudo-terminal read failed");
        }
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, (jsize) count);
    if (result != NULL) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize) count, (jbyte *) buffer);
    }
    return result;
}

JNIEXPORT void JNICALL
Java_ai_alaser_app_terminal_NativePtyBridge_nativeWrite(
    JNIEnv *env,
    jobject receiver,
    jint descriptor,
    jbyteArray input
) {
    (void) receiver;
    jsize length = (*env)->GetArrayLength(env, input);
    jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (bytes == NULL) return;

    jsize offset = 0;
    while (offset < length) {
        ssize_t written = write(descriptor, bytes + offset, (size_t) (length - offset));
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) {
            (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
            throw_io(env, "Pseudo-terminal write failed");
            return;
        }
        offset += (jsize) written;
    }
    (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
}

JNIEXPORT void JNICALL
Java_ai_alaser_app_terminal_NativePtyBridge_nativeResize(
    JNIEnv *env,
    jobject receiver,
    jint descriptor,
    jint rows,
    jint columns
) {
    (void) receiver;
    struct winsize size = {
        .ws_row = (unsigned short) rows,
        .ws_col = (unsigned short) columns,
        .ws_xpixel = 0,
        .ws_ypixel = 0,
    };
    if (ioctl(descriptor, TIOCSWINSZ, &size) < 0) {
        throw_io(env, "Pseudo-terminal resize failed");
    }
}

JNIEXPORT void JNICALL
Java_ai_alaser_app_terminal_NativePtyBridge_nativeClose(
    JNIEnv *env,
    jobject receiver,
    jint descriptor,
    jint process_id
) {
    (void) env;
    (void) receiver;
    if (process_id > 0) {
        kill((pid_t) process_id, SIGHUP);
    }
    if (descriptor >= 0) {
        close(descriptor);
    }
    if (process_id > 0) {
        int status;
        waitpid((pid_t) process_id, &status, WNOHANG);
    }
}
