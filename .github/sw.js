// N빵 웹앱: 한 번 열면 인터넷 없이도(해외·지하) 열리게 저장하고, 인터넷이 되면 항상 새 버전을 먼저 받아요.
// 이 사이트(github.io/nbbang-qr/app/) 파일만 다루고, 다른 곳으로는 아무것도 보내지 않아요.
// 같은 주소(github.io)를 쓰는 다른 앱의 저장분은 건드리지 않게, 'nbbang-'으로 시작하는 것만 정리해요.
const CACHE = 'nbbang-__V__';
const FILES = ['./', './index.html', './qrcode.js', './icon-180.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.startsWith('nbbang-') && k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin || !u.pathname.startsWith(new URL('./', location).pathname)) return;
  e.respondWith(fetch(e.request).then(r => {
    if (r.ok) { const cp = r.clone(); caches.open(CACHE).then(c => c.put(e.request, cp)); }
    return r;
  }).catch(() => caches.match(e.request, { ignoreSearch: true }).then(r => r || caches.match('./index.html'))));
});
