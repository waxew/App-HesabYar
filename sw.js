/*
 * Service Worker نسخه وب حسابیار.
 * Scope این فایل به‌صورت خودکار همان /App-HesabYar/ است و به پروژه‌های دیگر دسترسی ندارد.
 */
const CACHE_NAME = 'hesabyar-pwa-v1';

/* فایل‌های ضروری برای اجرای کامل آفلاین برنامه. */
const APP_SHELL = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './icon.svg'
];

/* هنگام نصب، App Shell در Cache ذخیره می‌شود. */
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

/* Cache نسخه‌های قدیمی بعد از فعال شدن Worker پاک می‌شود. */
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

/*
 * برای Navigation ابتدا شبکه امتحان می‌شود تا آخرین نسخه سریع دیده شود؛
 * در قطع اینترنت، همان نسخه Cache شده برگردانده می‌شود.
 */
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;

  const requestUrl = new URL(event.request.url);
  if (requestUrl.origin !== self.location.origin) return;

  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put('./index.html', clone));
          return response;
        })
        .catch(() => caches.match('./index.html'))
    );
    return;
  }

  /* فایل‌های استاتیک Cache First هستند و در پس‌زمینه از شبکه تازه می‌شوند. */
  event.respondWith(
    caches.match(event.request).then(cached => {
      const network = fetch(event.request)
        .then(response => {
          if (response && response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => cached);

      return cached || network;
    })
  );
});
