#ifndef _DataBuffer
#define _DataBuffer


typedef struct DateBuffer_t
{
	int m_nCapacity;
	int m_nSize;
	void* m_pBuf;
}
DataBuffer;


DataBuffer* DataBuffer_new();
void DataBuffer_del(void*);
void DataBuffer_write_short(DataBuffer*, int);


#endif
