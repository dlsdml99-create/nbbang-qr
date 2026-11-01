package com.nbbang.qr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 영수증 글자 줄들에서 최종 결제 금액과 가게 이름을 찾는다. */
public class ReceiptParser {
    public static class Line {
        public final String text; public final int left, top, right, bottom;
        public Line(String text, int left, int top, int right, int bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int cy() { return (top + bottom) / 2; }
        int h() { return Math.max(1, bottom - top); }
    }

    public static class Result {
        public final long total; public final String store;
        Result(long total, String store) { this.total = total; this.store = store; }
    }

    // 앞에 있을수록 우선
    private static final String[] KEYS = {
        "받을금액", "결제금액", "결제할금액", "청구금액", "총결제금액", "합계금액", "총합계", "총금액",
        "판매합계", "판매금액", "승인금액", "카드승인", "합계", "총액", "total"
    };
    private static final String[] SKIP = { "부가세", "과세", "면세", "공급가", "할인", "봉사료", "거스름", "받은금액", "포인트", "적립" };
    private static final Pattern COMMA = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:[,.]\\d{3})+)(?![\\d,.]*\\d{2}[:/-])");
    private static final Pattern PLAIN_WON = Pattern.compile("(?<![\\d,])(\\d{3,8})\\s*원");

    static String norm(String s) { return s.replace(" ", "").toLowerCase(); }

    static List<Long> amounts(String text) {
        List<Long> out = new ArrayList<>();
        if (text.matches(".*\\d{2,4}[-/.]\\d{1,2}[-/.]\\d{1,2}.*") || text.matches(".*\\d{1,2}:\\d{2}.*")) return out; // 날짜·시간
        if (text.matches(".*\\d{2,4}-\\d{3,4}-\\d{4}.*")) return out; // 전화·카드번호
        Matcher m = COMMA.matcher(text);
        while (m.find()) add(out, m.group(1).replaceAll("[,.]", ""));
        Matcher p = PLAIN_WON.matcher(text);
        while (p.find()) add(out, p.group(1));
        return out;
    }

    private static void add(List<Long> out, String digits) {
        try {
            long v = Long.parseLong(digits);
            if (v >= 100 && v <= 50_000_000) out.add(v);
        } catch (NumberFormatException ignored) {}
    }

    private static boolean skip(String n) {
        for (String s : SKIP) if (n.contains(s)) return true;
        return false;
    }

    public static Result parse(List<Line> lines) {
        long total = 0;
        outer:
        for (String key : KEYS) {
            for (Line k : lines) {
                String n = norm(k.text);
                if (!n.contains(key) || skip(n)) continue;
                // 1) 같은 줄
                List<Long> same = amounts(k.text);
                if (!same.isEmpty()) { total = same.get(same.size() - 1); break outer; }
                // 2) 같은 높이의 오른쪽
                Line best = null;
                for (Line o : lines) {
                    if (o == k || o.left < k.left) continue;
                    if (Math.abs(o.cy() - k.cy()) <= k.h() && !amounts(o.text).isEmpty()) {
                        if (best == null || o.left > best.left) best = o;
                    }
                }
                if (best != null) { List<Long> a = amounts(best.text); total = a.get(a.size() - 1); break outer; }
                // 3) 바로 아래 줄
                Line below = null;
                for (Line o : lines) {
                    int dy = o.cy() - k.cy();
                    if (dy > 0 && dy <= k.h() * 2.5 && !amounts(o.text).isEmpty() && !skip(norm(o.text))) {
                        if (below == null || dy < below.cy() - k.cy()) below = o;
                    }
                }
                if (below != null) { List<Long> a = amounts(below.text); total = a.get(a.size() - 1); break outer; }
            }
        }
        if (total == 0) {
            for (Line l : lines) {
                if (skip(norm(l.text))) continue;
                for (long v : amounts(l.text)) if (v > total) total = v;
            }
        }
        return new Result(total, store(lines));
    }

    static String store(List<Line> lines) {
        Line top = null;
        for (Line l : lines) {
            String t = l.text.trim();
            if (t.length() < 2 || t.length() > 20) continue;
            if (!t.matches(".*[가-힣].*") || t.matches(".*\\d.*")) continue;
            if (t.contains("영수증") || t.contains("매출") || t.contains("고객") || t.contains("주문")) continue;
            if (top == null || l.top < top.top) top = l;
        }
        return top == null ? "영수증" : top.text.trim();
    }
}
