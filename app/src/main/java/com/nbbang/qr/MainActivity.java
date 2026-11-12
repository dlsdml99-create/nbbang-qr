package com.nbbang.qr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1, REQ_GALLERY = 2;
    private WebView web;
    private Uri photoUri;
    private boolean cardMode;
    private boolean pageReady;
    private final List<String> pendingJs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // 카메라를 여는 동안 앱이 정리됐다가 다시 켜져도 찍은 사진을 잃지 않게
            photoUri = savedInstanceState.getParcelable("photoUri");
            cardMode = savedInstanceState.getBoolean("cardMode");
        }
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // 보안: 앱 화면은 앱 안 파일만 열고, 폰의 다른 파일·콘텐츠는 못 읽게
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setGeolocationEnabled(false);
        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                for (String c : pendingJs) web.evaluateJavascript(c, null);
                pendingJs.clear();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return !String.valueOf(u.getPath()).startsWith("/android_asset/");
                openSafe(u); // 허용된 https 주소만 바깥 앱(브라우저)으로 열기
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        checkUpdate();
    }

    /** 바깥으로 여는 주소: https + 이 앱 GitHub 저장소·다운로드 페이지만 (그 밖은 무시) */
    static boolean isAllowed(Uri u) {
        if (u == null || !"https".equals(u.getScheme())) return false;
        String h = String.valueOf(u.getHost()), p = String.valueOf(u.getPath());
        if ("github.com".equals(h)) return p.startsWith("/dlsdml99-create/");
        return "dlsdml99-create.github.io".equals(h);
    }

    private void openSafe(Uri u) {
        if (!isAllowed(u)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)); } catch (Exception ignored) {}
    }

    // ── 새 버전 확인 (GitHub 최신 릴리스와 비교) ──
    private static final String RELEASE_API = "https://api.github.com/repos/dlsdml99-create/nbbang-qr/releases/latest";

    private void checkUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject o = new JSONObject(sb.toString());
                String digits = o.optString("tag_name", "").replaceAll("[^0-9]", "");
                if (digits.isEmpty()) return;
                long latest = Long.parseLong(digits);
                String url = null;
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String du = a.optString("browser_download_url"); if (a.optString("name").endsWith(".apk") && du.startsWith("https://github.com/dlsdml99-create/nbbang-qr/releases/download/")) { url = du; break; }
                    }
                }
                js("window.AppInfo&&window.AppInfo.native('1." + latest + "')");
                if (url == null || latest <= currentVersion()) return;
                final String u = url;
                runOnUiThread(() -> showUpdate(latest, u));
            } catch (Exception ignored) {}
        }).start();
    }

    @SuppressWarnings("deprecation")
    private long currentVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private void showUpdate(long latest, String url) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("새 버전이 나왔어요")
            .setMessage("v1." + latest + " 업데이트가 나왔어요.\n'업데이트'를 누르면 파일이 받아져요. 다 받으면 알림이나 다운로드에서 파일을 눌러 설치해 주세요.")
            .setPositiveButton("업데이트", (d, w) -> openSafe(Uri.parse(url)))
            .setNegativeButton("나중에", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        // 앱 정보 창 등이 열려 있으면 그것부터 닫는다
        web.evaluateJavascript("window.onBackKey?String(window.onBackKey()):'false'", v -> {
            if (v != null && v.contains("true")) return;
            if (web.canGoBack()) web.goBack(); else finish();
        });
    }

    class Bridge {
        /** 백업 파일 저장: 사용자가 '파일' 창에서 고른 곳에만 써요 (권한 필요 없음, 인터넷으로 보내지 않음) */
        @JavascriptInterface
        public void backupSave(String name, String json) {
            runOnUiThread(() -> {
                bkData = json;
                try {
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_BK_SAVE);
                } catch (Exception e) { bkData = null; js("window.AppInfo&&AppInfo.backupDone(false)"); }
            });
        }

        /** 백업 파일 불러오기: 사용자가 고른 파일 하나만 읽어요 */
        @JavascriptInterface
        public void backupOpen() {
            runOnUiThread(() -> {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"}), REQ_BK_OPEN);
                } catch (Exception e) { js("window.AppInfo&&AppInfo.backupOpened(null)"); }
            });
        }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("N빵", text));
        }

        @JavascriptInterface
        public void share(String text, String receiptsJson) {
            runOnUiThread(() -> doShare(text, receiptsJson));
        }

        @JavascriptInterface
        public void deleteReceipt(String name) {
            File f = receiptFile(name);
            if (f != null) f.delete();
        }

        @JavascriptInterface
        public void scanReceipt() {
            runOnUiThread(MainActivity.this::chooseSource);
        }
    }

    private void chooseSource() {
        new AlertDialog.Builder(this)
            .setTitle("사진으로 금액 넣기")
            .setItems(new String[]{"사진 촬영 (영수증)", "앨범에서 가져오기 (카드사 앱 캡처)"}, (d, which) -> {
                cardMode = which == 1;
                if (which == 0) openCamera(); else openGallery();
            })
            .setOnCancelListener(d -> status(""))
            .show();
    }

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "receipts");
            dir.mkdirs();
            File f = new File(dir, "receipt.jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            fail("카메라를 열 수 없어요. 앨범에서 골라 주세요.");
        }
    }

    private void openGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "카드사 앱 캡처 고르기"), REQ_GALLERY);
        } catch (Exception e) {
            fail("앨범을 열 수 없어요.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (backupResult(req, res, data)) return;
        if (req != REQ_CAMERA && req != REQ_GALLERY) return;
        if (res != RESULT_OK) { status(""); return; }
        Uri u = req == REQ_CAMERA ? photoUri : (data != null ? data.getData() : null);
        if (u == null) { fail("사진을 가져오지 못했어요."); return; }
        recognize(u);
    }

    private void recognize(Uri u) {
        status(cardMode ? "캡처 읽는 중…" : "영수증 읽는 중…");
        new Thread(() -> {
            Bitmap bmp;
            String name;
            try {
                bmp = loadUpright(u);
                name = saveReceipt(bmp);
            } catch (Exception e) {
                fail("사진을 열 수 없어요.", null);
                return;
            }
            final String saved = name;
            TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build())
                .process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(t -> onText(t, saved))
                .addOnFailureListener(e -> fail("글자를 읽지 못했어요. 금액을 직접 넣어 주세요.", saved));
        }).start();
    }

    // 큰 사진은 줄이고, 휴대폰 방향대로 똑바로 세운다
    private Bitmap loadUpright(Uri u) throws Exception {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, b); }
        int sample = 1;
        while (Math.max(b.outWidth, b.outHeight) / (sample * 2) >= 1600) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(u)) { bmp = BitmapFactory.decodeStream(in, null, o); }
        if (bmp == null) throw new Exception("decode");
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
        } catch (Exception ignored) {}
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    private File receiptDir() {
        File d = new File(getFilesDir(), "receipts");
        d.mkdirs();
        return d;
    }

    private File receiptFile(String name) {
        if (name == null || !name.matches("r_\\d+\\.jpg")) return null;
        return new File(receiptDir(), name);
    }

    private String saveReceipt(Bitmap bmp) throws Exception {
        String name = "r_" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream out = new FileOutputStream(new File(receiptDir(), name))) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
        }
        return name;
    }

    private void doShare(String text, String receiptsJson) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("N빵", text));
        } catch (Exception ignored) {}
        ArrayList<Uri> uris = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(receiptsJson == null ? "[]" : receiptsJson);
            for (int i = 0; i < a.length(); i++) {
                File f = receiptFile(a.optString(i));
                if (f != null && f.exists()) uris.add(FileProvider.getUriForFile(this, getPackageName() + ".files", f));
            }
        } catch (Exception ignored) {}
        Intent i;
        if (uris.isEmpty()) {
            i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
        } else if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.setType("image/jpeg");
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.putExtra(Intent.EXTRA_TEXT, text);
        if (!uris.isEmpty()) {
            ClipData cd = ClipData.newUri(getContentResolver(), "영수증", uris.get(0));
            for (int k = 1; k < uris.size(); k++) cd.addItem(new ClipData.Item(uris.get(k)));
            i.setClipData(cd);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        try {
            startActivity(Intent.createChooser(i, "정산 공유하기"));
        } catch (Exception e) {
            status("공유할 앱이 없어요. 내용은 복사돼 있어요.");
        }
    }

    private void onText(Text text, String receipt) {
        List<ReceiptParser.Line> lines = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks()) {
            for (Text.Line l : b.getLines()) {
                Rect r = l.getBoundingBox();
                if (r == null) r = new Rect(0, 0, 0, 0);
                lines.add(new ReceiptParser.Line(l.getText(), r.left, r.top, r.right, r.bottom));
            }
        }
        if (cardMode) { onCardLines(lines, receipt); return; }
        ReceiptParser.Result r = ReceiptParser.parse(lines);
        if (r.total <= 0) { fail("영수증에서 금액을 찾지 못했어요. 밝은 곳에서 가까이 찍어 주세요.", receipt); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("total", r.total);
            o.put("store", r.store);
            o.put("receipt", receipt);
            js("window.onReceiptResult&&window.onReceiptResult(" + o + ")");
        } catch (Exception e) {
            fail("결과를 처리하지 못했어요.", receipt);
        }
    }

    private void onCardLines(List<ReceiptParser.Line> lines, String receipt) {
        // 앨범에서 고른 게 종이 영수증 사진이면 영수증 방식으로 읽는다
        boolean paper = false;
        for (ReceiptParser.Line l : lines) {
            String n = ReceiptParser.norm(l.text);
            if (n.contains("사업자") || n.contains("부가세") || n.contains("공급가") || n.contains("받을금액") || n.contains("영수증")) { paper = true; break; }
        }
        List<CardParser.Entry> es = paper ? new ArrayList<>() : CardParser.parse(lines);
        if (es.isEmpty()) {
            // 앨범에 있는 영수증 사진일 수도 있으니 영수증 방식으로 한 번 더 읽는다
            ReceiptParser.Result r = ReceiptParser.parse(lines);
            if (r.total > 0) {
                List<CardParser.Entry> one = new ArrayList<>();
                one.add(new CardParser.Entry(r.store, r.total));
                sendEntries(one, receipt);
            } else {
                fail("사진에서 금액을 찾지 못했어요. 금액이 보이게 캡처해 주세요.", receipt);
            }
            return;
        }
        if (es.size() == 1) { sendEntries(es, receipt); return; }
        String[] labels = new String[es.size()];
        boolean[] checked = new boolean[es.size()];
        for (int k = 0; k < es.size(); k++) {
            labels[k] = es.get(k).name + "  " + String.format(java.util.Locale.KOREA, "%,d원", es.get(k).amount);
        }
        new AlertDialog.Builder(this)
            .setTitle("N빵할 결제를 골라 주세요")
            .setMultiChoiceItems(labels, checked, (d, w, on) -> checked[w] = on)
            .setPositiveButton("추가", (d, w) -> {
                List<CardParser.Entry> pick = new ArrayList<>();
                for (int k = 0; k < es.size(); k++) if (checked[k]) pick.add(es.get(k));
                if (pick.isEmpty()) { dropReceipt(receipt); status("고른 결제가 없어요."); }
                else sendEntries(pick, receipt);
            })
            .setNegativeButton("취소", (d, w) -> { dropReceipt(receipt); status(""); })
            .setOnCancelListener(d -> { dropReceipt(receipt); status(""); })
            .show();
    }

    private void dropReceipt(String name) {
        File f = receiptFile(name);
        if (f != null) f.delete();
    }

    private void sendEntries(List<CardParser.Entry> es, String receipt) {
        try {
            JSONArray arr = new JSONArray();
            for (CardParser.Entry e : es) {
                JSONObject x = new JSONObject();
                x.put("store", e.name);
                x.put("total", e.amount);
                arr.put(x);
            }
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("entries", arr);
            o.put("receipt", receipt);
            js("window.onReceiptResult&&window.onReceiptResult(" + o + ")");
        } catch (Exception e) {
            fail("결과를 처리하지 못했어요.", receipt);
        }
    }

    private void fail(String msg) { fail(msg, null); }

    private void fail(String msg, String receipt) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            if (receipt != null) o.put("receipt", receipt);
            js("window.onReceiptResult&&window.onReceiptResult(" + o + ")");
        } catch (Exception ignored) {}
    }

    private void status(String msg) {
        js("window.onReceiptStatus&&window.onReceiptStatus(" + JSONObject.quote(msg) + ")");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoUri != null) out.putParcelable("photoUri", photoUri);
        out.putBoolean("cardMode", cardMode);
    }

    // ── 내 데이터 백업 (파일 저장·불러오기) ──
    private static final int REQ_BK_SAVE = 7301, REQ_BK_OPEN = 7302;
    private String bkData;

    private boolean backupResult(int req, int res, Intent data) {
        if (req != REQ_BK_SAVE && req != REQ_BK_OPEN) return false;
        final android.net.Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
        if (req == REQ_BK_SAVE) {
            final String text = bkData; bkData = null;
            if (u == null || text == null) { js("window.AppInfo&&AppInfo.backupDone(false,'cancel')"); return true; }
            new Thread(() -> {
                boolean ok;
                try (java.io.OutputStream o = getContentResolver().openOutputStream(u, "wt")) {
                    o.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); ok = true;
                } catch (Exception e) { ok = false; }
                js("window.AppInfo&&AppInfo.backupDone(" + ok + ")");
            }).start();
            return true;
        }
        if (u == null) { js("window.AppInfo&&AppInfo.backupOpened(null)"); return true; }
        new Thread(() -> {
            String out;
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > 80 * 1024 * 1024) throw new java.io.IOException("too big"); }
                out = new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) { out = null; }
            js("window.AppInfo&&AppInfo.backupOpened(" + (out == null ? "null" : org.json.JSONObject.quote(out)) + ")");
        }).start();
        return true;
    }

    private void js(String code) {
        web.post(() -> {
            if (pageReady) web.evaluateJavascript(code, null);
            else pendingJs.add(code);
        });
    }
}
