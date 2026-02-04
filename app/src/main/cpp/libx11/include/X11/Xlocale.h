
#ifndef _X11_XLOCALE_H_
#define _X11_XLOCALE_H_

/* Do not include locale.h here to avoid shadowing system headers.
   Just provide the type if we are on Android. */
#ifdef __ANDROID__
#include <sys/cdefs.h>
/* locale_t is a typedef to struct __locale_t* in Bionic */
typedef struct __locale_t* locale_t;
#endif

#endif /* _X11_XLOCALE_H_ */
