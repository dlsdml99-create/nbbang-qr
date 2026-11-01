package com.nbbang.qr;

import java.util.ArrayList;
import java.util.List;

/** 카드사 앱 결제내역 캡처에서 (가맹점, 금액) 목록을 뽑는다. */
public class CardParser {
    public static class Entry {
        public final String name; public final long amount;
        public Entry(String name, long amount) { this.name = name; this.amount = amount; }
    }

    private static final String[] DETAIL_KEYS = { "승인금액", "결제금액", "이용금액", "거래금액" };
    private static final String[] MERCHANT_KEYS = { "가맹점명", "가맹점", "사용처", "이용처", "상호" };
    // 금액 줄이 이런 말과 같이 있으면 결제 건이 아니다
    private static final String[] SKIP = {
        "합계", "총", "누적", "한도", "포인트", "적립", "할인", "청구", "결제예정", "잔액", "캐시백",
        "혜택", "취소", "실적", "예정", "남은", "가능"
    };
    // 가맹점 이름이 될 수 없는 말
    private static final String[] NOT_NAME = {
        "일시불", "할부", "승인", "체크", "신용", "카드", "이용내역", "결제내역", "사용내역", "전체", "국내", "해외",
        "완료", "상세", "내역", "조회", "오늘", "어제", "월", "개월"
    };

    static boolean hasKo(String s) { return s.matches(".*[가-힣].*"); }

    static boolean any(String s, String[] words) {
        String n = ReceiptParser.norm(s);
        for (String w : words) if (n.contains(w)) return true;
        return false;
    }

    static boolean nameLike(String t) {
        String s = t.trim();
        if (s.length() < 2 || s.length() > 30 || !hasKo(s)) return false;
        if (!ReceiptParser.amounts(s).isEmpty()) return false;
        if (s.matches(".*\\d{1,2}[./:]\\d{1,2}.*")) return false; // 날짜·시간
        if (any(s, SKIP)) return false;
        String n = ReceiptParser.norm(s);
        for (String w : NOT_NAME) if (n.equals(w)) return false;
        if (n.matches("^(일시불|할부|\\d*개월|승인|체크|신용|국내|해외|완료|[|·/])+$")) return false;
        return true;
    }

    static String clean(String s) {
        String t = s.replaceAll("-?\\d{1,3}(?:[,.]\\d{3})+\\s*원?", "").replaceAll("\\d{3,8}\\s*원", "").trim();
        for (String k : MERCHANT_KEYS) if (t.startsWith(k)) t = t.substring(k.length()).trim();
        t = t.replaceAll("^[:·|\\-\\s]+|[:·|\\-\\s]+$", "").trim();
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    public static List<Entry> parse(List<ReceiptParser.Line> lines) {
        List<Entry> out = new ArrayList<>();
        // 1) 결제 상세 화면: '승인금액' 같은 이름표가 있으면 한 건
        for (ReceiptParser.Line l : lines) {
            if (!any(l.text, DETAIL_KEYS) || any(l.text, new String[]{"합계", "누적", "한도", "총"})) continue;
            ReceiptParser.Result r = ReceiptParser.parse(lines);
            if (r.total > 0) {
                out.add(new Entry(merchant(lines), r.total));
                return out;
            }
        }
        // 2) 내역 목록 화면: 금액마다 옆이나 위의 가맹점 이름을 찾는다
        for (ReceiptParser.Line a : lines) {
            List<Long> am = ReceiptParser.amounts(a.text);
            if (am.isEmpty()) continue;
            if (a.text.trim().startsWith("-") || any(a.text, SKIP)) continue;
            boolean rowSkip = false;
            String name = null;
            String own = clean(a.text);
            if (nameLike(own)) name = own;
            ReceiptParser.Line best = null;
            for (ReceiptParser.Line o : lines) {
                if (o == a || Math.abs(o.cy() - a.cy()) > a.h()) continue;
                if (any(o.text, SKIP)) { rowSkip = true; break; }
                if (name == null && o.right <= a.left + a.h() && nameLike(o.text)) {
                    if (best == null || o.right > best.right) best = o;
                }
            }
            if (rowSkip) continue;
            if (name == null && best != null) name = clean(best.text);
            if (name == null) {
                ReceiptParser.Line above = null;
                for (ReceiptParser.Line o : lines) {
                    int dy = a.cy() - o.cy();
                    if (dy <= 0 || dy > a.h() * 3 || !nameLike(o.text)) continue;
                    if (above == null || dy < a.cy() - above.cy()) above = o;
                }
                if (above != null) name = clean(above.text);
            }
            if (name == null || name.isEmpty()) name = "카드 결제";
            long v = am.get(am.size() - 1);
            boolean dup = false;
            for (Entry e : out) if (e.amount == v && e.name.equals(name)) dup = true;
            if (!dup) out.add(new Entry(name, v));
        }
        return out;
    }

    static String merchant(List<ReceiptParser.Line> lines) {
        for (ReceiptParser.Line k : lines) {
            if (!any(k.text, MERCHANT_KEYS)) continue;
            String own = clean(k.text);
            if (nameLike(own)) return own;
            ReceiptParser.Line best = null;
            for (ReceiptParser.Line o : lines) {
                if (o == k || Math.abs(o.cy() - k.cy()) > k.h() || o.left < k.left) continue;
                if (nameLike(o.text) && (best == null || o.left < best.left)) best = o;
            }
            if (best != null) return clean(best.text);
        }
        // 이름표가 없으면 맨 위쪽의 가맹점 같은 줄
        ReceiptParser.Line top = null;
        for (ReceiptParser.Line l : lines) if (nameLike(l.text) && (top == null || l.top < top.top)) top = l;
        return top == null ? "카드 결제" : clean(top.text);
    }
}
