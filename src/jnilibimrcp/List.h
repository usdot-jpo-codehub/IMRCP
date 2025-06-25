#ifndef _List
#define _List


typedef struct List_t
{
	int m_nCapacity;
	int m_nSize;
	void** m_pElems;
}
List;


List* List_new();
List* List_new_capacity(int);
void List_del(List*, void (*)(void*));
void List_reserve(List*, int);
void List_append(List*, void*);
void List_insert(List*, void*, int);
int List_search(List*, void*, int (*)(void*, void*));


#endif
