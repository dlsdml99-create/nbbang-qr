#!/usr/bin/env bash
# 아이폰·PC용 웹앱 만들기: 앱 화면(index.html)과 QR 라이브러리를 site/app/ 에 넣는다.
# 사용: bash .github/webapp.sh <버전 번호 N>
set -euo pipefail
N="$1"
mkdir -p site/app
# 보안: QR 라이브러리는 npm 공식 저장소에서 받고, 처음 확인한 파일과 같은지(sha256) 확인
if [ ! -f app/src/main/assets/qrcode.js ]; then
  (cd "$RUNNER_TEMP" && npm pack qrcode-generator@1.4.4 --silent >/dev/null && tar -xzf qrcode-generator-1.4.4.tgz)
  cp "$RUNNER_TEMP/package/qrcode.js" app/src/main/assets/qrcode.js
fi
echo "18ae399f81182bc9de916e9c77b195df20cc58d6f2d55a62b085a299f1bf1780  app/src/main/assets/qrcode.js" | sha256sum -c -
cp app/src/main/assets/qrcode.js site/app/qrcode.js
# 다운로드 페이지의 QR도 같은 확인된 파일을 씀 (바깥 CDN 안 씀)
cp app/src/main/assets/qrcode.js site/qrcode.js
cp site/icon-180.png site/app/icon-180.png
# 홈 화면에 추가했을 때 아이콘·이름이 보이게
sed "s/미리보기에서는/웹에서는/g; s/__N__/$N/g; s#<meta charset=\"utf-8\">#<meta charset=\"utf-8\"><link rel=\"apple-touch-icon\" href=\"icon-180.png\"><meta name=\"apple-mobile-web-app-title\" content=\"N빵\"><meta name=\"apple-mobile-web-app-capable\" content=\"yes\">#" \
  app/src/main/assets/index.html > site/app/index.html
grep -q 'apple-touch-icon' site/app/index.html
# 인터넷 없이도 열리게(서비스워커). 캐시 이름은 'nbbang-' + 버전 번호
sed "s/__V__/$N/" .github/sw.js > site/app/sw.js
grep -q "nbbang-$N" site/app/sw.js
