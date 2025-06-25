gcc -c -fPIC -Wall -g0 -O3 -fno-math-errno -funsafe-math-optimizations -ffinite-math-only -I <path-to-jni-headers> -I <path-to-jni-linux-headers> -I <path-to-liblzma-headers> *.c
g++ -c -fPIC -Wall -g0 -O3 -fno-math-errno -funsafe-math-optimizations -ffinite-math-only -I <path-to-jni-headers> -I <path-to-jni-linux-headers> -I <path-to-boost-headers> *.cpp
gcc *.o -o libimrcp.so -shared -lstdc++ -llzma
