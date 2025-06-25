
// struc double
struct doubleStruct
{
  double* pdArray;
  long nSize;
};

// struc float
struct floatStruct
{
  float* pfArray;
  long nSize;
};

// struc long
struct longStruct
{
  long* plArray;
  long nSize;
};

// struc short
struct shortStruct
{
  short* pnArray;
  long nSize;
};

// struc int
struct intStruct
{
  int* pnArray;
  long nSize;
};

// double
void PrintDoubleArray(double* pdArray, long nSize);
double* GiveMeADoublePointer();
struct doubleStruct GiveMeADoubleStruct();
// float
float* GiveMeAFloatPointer();
struct floatStruct GiveMeAFloatStruct();
// long
void PrintLongArray(long* plArray, long nSize);
long* GiveMeALongPointer();
struct longStruct GiveMeALongStruct();
// short
short* GiveMeAShortPointer();
struct shortStruct GiveMeAShortStruct();
// int
extern int* GiveMeAIntPointer();
extern struct intStruct GiveMeAIntStruct();
