// 다운로드 페이지: PC에서 볼 때 폰으로 찍는 다운로드 QR (QR 라이브러리는 같은 사이트의 확인된 파일)
(function () {
  var el = document.getElementById('qr-nb'), a = document.querySelector('a.btn.nb');
  if (!el) return;
  if (typeof qrcode !== 'function' || !a) { el.parentNode.hidden = true; return; }
  try {
    var q = qrcode(0, 'M'); q.addData(a.href); q.make();
    var n = q.getModuleCount(), d = '';
    for (var r = 0; r < n; r++) for (var c = 0; c < n; c++) if (q.isDark(r, c)) d += 'M' + c + ' ' + r + 'h1v1h-1z';
    var ns = 'http://www.w3.org/2000/svg', svg = document.createElementNS(ns, 'svg'), path = document.createElementNS(ns, 'path');
    svg.setAttribute('viewBox', '0 0 ' + n + ' ' + n); svg.setAttribute('shape-rendering', 'crispEdges'); svg.setAttribute('role', 'img'); svg.setAttribute('aria-label', '다운로드 QR');
    path.setAttribute('fill', '#111'); path.setAttribute('d', d); svg.appendChild(path); el.appendChild(svg);
  } catch (e) { el.parentNode.hidden = true; }
})();
