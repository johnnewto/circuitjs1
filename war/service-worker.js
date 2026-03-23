const CACHE_NAME = 'circuitjs1-app-cache-v4';
const urlsToCache = [
    'about.html',
    'canvas2svg.js',
    'circuitjs.html',
    'crystal.html',
    'customfunction.html',
    'customlogic.html',
    'customtransformer.html',
    'diodecalc.html',
    'icon512.png',
    'icon128.png',
    'iframe.html',
    'lz-string.min.js',
    'manifest.json',
    'mexle.html',
    'mosfet-beta.html',
    'opampreal.html',
    'subcircuits.html',
  // put everything else here
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
            .then(cache => Promise.all(
                urlsToCache.map(url => cache.add(url).catch(() => null))
            ))
  );
});

function shouldBypassCache(request) {
    if (request.method !== 'GET')
        return true;
    const url = new URL(request.url);
    const p = url.searchParams;
    if (p.has('headless'))
        return true;
    if (p.has('runner'))
        return true;
    if (url.pathname.indexOf('/circuitjs1/circuits/') >= 0)
        return true;
    if (url.pathname.endsWith('.md') || url.pathname.endsWith('.txt'))
        return true;
    if (url.pathname.endsWith('/world2.html') || url.pathname.endsWith('/headless.html'))
        return true;
    if (url.pathname.endsWith('/run') || url.pathname.endsWith('/run.csv') || url.pathname.endsWith('/scenarios'))
        return true;
    // Avoid stale/mixed GWT bundles after deploy; always fetch loader/chunks fresh.
    if (url.pathname.endsWith('/split.js'))
        return true;
    if (url.pathname.endsWith('/circuitjs1/circuitjs1.nocache.js'))
        return true;
    if (url.pathname.includes('/circuitjs1/') && url.pathname.endsWith('.cache.js'))
        return true;
    return false;
}

self.addEventListener('fetch', (event) => {
    if (shouldBypassCache(event.request)) {
        event.respondWith(fetch(event.request, { cache: 'no-store' }));
        return;
    }

    event.respondWith(
        caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) {
                // If the resource is already cached, return it
                return cachedResponse;
            }

            // Otherwise, fetch it from the network and add it to the cache
            return fetch(event.request).then((networkResponse) => {
                // Only cache non-GET requests and responses that aren't errors
                if (
                    event.request.method === 'GET' &&
                    networkResponse.status === 200
                ) {
		    const responseClone = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseClone);
                    });
                }

                return networkResponse;
            });
        })
    );
});


// Activate event: cleans up old caches
self.addEventListener('activate', (event) => {
    const cacheWhitelist = [CACHE_NAME];  // List of cache versions you want to keep

    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((cacheName) => {
                    if (!cacheWhitelist.includes(cacheName)) {
                        return caches.delete(cacheName);  // Delete old caches that aren't in whitelist
                    }
                })
            );
        })
    );
});
