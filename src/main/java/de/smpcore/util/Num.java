package de.smpcore.util;

import java.util.Locale;

public final class Num {
    private static boolean comma = true; // deutsches Format: 48,64M

    private Num() {}

    public static void setComma(boolean b) { comma = b; }

    /** Parst 1500, 1.5k, 2m, 3b, 1t (auch mit Komma). */
    public static double parse(String in) {
        String s = in.trim().toLowerCase(Locale.ROOT).replace(",", ".").replace("$", "");
        double mult = 1;
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            switch (last) {
                case 'k' -> mult = 1e3;
                case 'm' -> mult = 1e6;
                case 'b' -> mult = 1e9;
                case 't' -> mult = 1e12;
                default -> { }
            }
            if (mult != 1) s = s.substring(0, s.length() - 1);
        }
        double v = Double.parseDouble(s) * mult;
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) throw new NumberFormatException();
        return Math.round(v * 100) / 100.0;
    }

    public static String format(double v) {
        if (v < 1000) return loc(strip(String.format(Locale.US, "%.2f", v)));
        String[] suf = {"", "K", "M", "B", "T"};
        int i = 0;
        double d = v;
        while (d >= 1000 && i < suf.length - 1) { d /= 1000; i++; }
        return loc(strip(String.format(Locale.US, "%.2f", d))) + suf[i];
    }

    public static String full(double v) {
        String s = strip(String.format(Locale.US, "%,.2f", v));
        return comma ? s.replace(',', '\u0001').replace('.', ',').replace('\u0001', '.') : s;
    }

    private static String loc(String s) { return comma ? s.replace('.', ',') : s; }

    private static String strip(String s) {
        if (s.indexOf('.') < 0) return s;
        s = s.replaceAll("0+$", "");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return "<1m";
    }

    /** 25s -> "0m 25s" */
    public static String mmss(long seconds) {
        long s = Math.max(0, seconds);
        return (s / 60) + "m " + (s % 60) + "s";
    }
}
