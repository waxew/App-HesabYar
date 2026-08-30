/*
 * Service Worker نسخه وب حسابیار.
 * Scope این فایل به‌صورت خودکار همان /App-HesabYar/ است و به پروژه‌های دیگر دسترسی ندارد.
 */
const CACHE_NAME = 'hesabyar-pwa-v2';

/* فایل‌های ضروری نسخه کامل‌تر Web/Android Parity. */
const APP_SHELL = [
  './',
  './index.html',
  './styles.css',
  './parity.css',
  './app.js',
  './parity.js',
  './manifest.json',
  './icon.svg'
];

/* هنگام نصب، تمام App Shell در Cache ذخیره می‌شود. */
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

/* Cache نسخه‌های قدیمی پس از فعال شدن Worker جدید پاک می‌شود. */
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

/*
 * Navigation شبکه‌اول است تا تغییرات جدید سریع دیده شوند؛
 * در حالت آفلاین index Cache شده باز می‌شود.
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

  /* فایل‌های استاتیک Cache First هستند و در پس‌زمینه به نسخه جدید تازه می‌شوند. */
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
