package com.lushprojects.circuitjs1.client.util;

import com.lushprojects.circuitjs1.client.runner.RuntimeMode;

public final class NumFmt {
    public interface Formatter {
        String format(double value);
    }

    private NumFmt() {
    }

    public static Formatter forPattern(String pattern) {
        if (RuntimeMode.isGwt()) {
            try {
                com.google.gwt.i18n.client.NumberFormat gwtFmt = com.google.gwt.i18n.client.NumberFormat.getFormat(pattern);
                return gwtFmt::format;
            } catch (Throwable ignored) {
            }
        }
        return new NonInteractiveFormatter(pattern);
    }

    private static final class NonInteractiveFormatter implements Formatter {
        private final int decimalDigits;
        private final int minFractionDigits;
        private final boolean grouping;
        private final boolean scientific;
        private final int scientificExponentDigits;

        private NonInteractiveFormatter(String pattern) {
            int eIndex = pattern.indexOf('E');
            scientific = eIndex >= 0;
            scientificExponentDigits = scientific ? pattern.length() - eIndex - 1 : 0;

            String decimalPart = pattern;
            if (scientific) {
                decimalPart = pattern.substring(0, eIndex);
            }

            int dotIndex = decimalPart.indexOf('.');
            if (dotIndex >= 0) {
                String fraction = decimalPart.substring(dotIndex + 1);
                decimalDigits = fraction.length();
                int minDigits = 0;
                for (int i = 0; i < fraction.length(); i++) {
                    if (fraction.charAt(i) == '0') {
                        minDigits++;
                    }
                }
                minFractionDigits = minDigits;
            } else {
                decimalDigits = 0;
                minFractionDigits = 0;
            }
            grouping = decimalPart.indexOf(',') >= 0;
        }

        @Override
        public String format(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return Double.toString(value);
            }
            if (scientific) {
                return formatScientific(value);
            }
            return formatFixed(value, false);
        }

        private String formatScientific(double value) {
            if (value == 0.0) {
                String mantissaZero = formatFixed(0.0, true);
                return mantissaZero + "E" + leftPad("0", scientificExponentDigits, '0');
            }

            double abs = Math.abs(value);
            int exp = (int) Math.floor(Math.log10(abs));
            double mantissa = value / Math.pow(10, exp);

            if (Math.abs(mantissa) >= 10.0) {
                mantissa /= 10.0;
                exp++;
            }

            String mantissaText = formatFixed(mantissa, true);
            String expSign = exp < 0 ? "-" : "";
            String expValue = Integer.toString(Math.abs(exp));
            return mantissaText + "E" + expSign + leftPad(expValue, scientificExponentDigits, '0');
        }

        private String formatFixed(double value, boolean suppressGrouping) {
            boolean negative = value < 0;
            double abs = Math.abs(value);
            double rounded = round(abs, decimalDigits);

            long integerPart = (long) rounded;
            double fractional = rounded - integerPart;

            String integerText = Long.toString(integerPart);
            if (grouping && !suppressGrouping) {
                integerText = withGrouping(integerText);
            }

            String fractionText = "";
            if (decimalDigits > 0) {
                long scaled = Math.round(fractional * pow10(decimalDigits));
                if (scaled == pow10(decimalDigits)) {
                    integerPart++;
                    integerText = Long.toString(integerPart);
                    if (grouping && !suppressGrouping) {
                        integerText = withGrouping(integerText);
                    }
                    scaled = 0;
                }
                String rawFraction = leftPad(Long.toString(scaled), decimalDigits, '0');
                int lastRequired = minFractionDigits;
                int end = rawFraction.length();
                while (end > lastRequired && rawFraction.charAt(end - 1) == '0') {
                    end--;
                }
                if (end > 0) {
                    fractionText = "." + rawFraction.substring(0, end);
                }
            }

            return (negative ? "-" : "") + integerText + fractionText;
        }

        private static String withGrouping(String input) {
            StringBuilder out = new StringBuilder();
            int count = 0;
            for (int i = input.length() - 1; i >= 0; i--) {
                if (count > 0 && count % 3 == 0) {
                    out.append(',');
                }
                out.append(input.charAt(i));
                count++;
            }
            return out.reverse().toString();
        }

        private static String leftPad(String value, int width, char padChar) {
            if (value.length() >= width) {
                return value;
            }
            StringBuilder out = new StringBuilder(width);
            for (int i = value.length(); i < width; i++) {
                out.append(padChar);
            }
            out.append(value);
            return out.toString();
        }

        private static long pow10(int n) {
            long v = 1;
            for (int i = 0; i < n; i++) {
                v *= 10;
            }
            return v;
        }

        private static double round(double value, int decimals) {
            if (decimals <= 0) {
                return Math.round(value);
            }
            double factor = Math.pow(10, decimals);
            double maxSafeBeforeScale = Long.MAX_VALUE / factor;
            if (value > maxSafeBeforeScale) {
                return value;
            }
            return Math.round(value * factor) / factor;
        }
    }
}
