#include <lzma.h>


#ifndef _XzBuffer
#define _XzBuffer


typedef struct XzBuffer_t
{
	lzma_stream dummy;
	int chunksize;
	int capacity;
	int size;
	int count;
	void **elems;
}
XzBuffer;


XzBuffer* XzBuffer_init(int, int, int);
XzBuffer* XzBuffer_new(int);
XzBuffer* XzBuffer_new_stream(int);
void XzBuffer_del(void*);
int XzBuffer_proc(XzBuffer*, int, void*);
void XzBuffer_copy(XzBuffer*, int, void*);


#endif
