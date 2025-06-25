gcc -c -std=c11 -fPIC -Wall  -Wno-implicit  macadam.c
gcc -c -std=c11 -fPIC -Wall -I /opt/jdk-19.0.1/include -I /opt/jdk-19.0.1/include/linux DoMetro.c

gfortran -Wall -Wsurprising -W -c -fPIC -fdefault-integer-8 lib_gen.f grille.f array2matrix.f initial.f coupla.f lib_therm.f flxsurfz.f balanc.f constPhys.f
gfortran -lstdc++ -lc -lpthread -shared lib_gen.o grille.o array2matrix.o initial.o coupla.o lib_therm.o flxsurfz.o balanc.o constPhys.o macadam.o DoMetro.o -o libDoMetroWrapper.so
rm *.o
