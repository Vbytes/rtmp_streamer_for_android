set -e
cd ~
rm -rf rtmpdump
mkdir rtmpdump
chmod 777 -R rtmpdump

/home/songmm/ndk/android-ndk-r10e/build/tools/make-standalone-toolchain.sh --platform=android-14 --install-dir=/home/songmm/rtmpdump/android-toolchain --toolchain=arm-linux-androideabi-4.8
##编译工作
cd rtmpdump && ls

cd /home/songmm/mygitlab/android-streamer-project/moonstoneandroid
cd polarssl-1.2.0
make clean

make CC=arm-linux-androideabi-gcc APPS=
make install DESTDIR=/home/songmm/rtmpdump/android-toolchain/sysroot

cd ../rtmpdump/
make clean
 make SYS=android CROSS_COMPILE=arm-linux-androideabi- INC="-I/home/songmm/rtmpdump/android-toolchain/sysroot/include" CRYPTO=POLARSSL 

