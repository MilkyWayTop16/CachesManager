package org.gw.cachesmanager.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CacheCommandArgs {

    private CacheCommandArgs() {}

    public static String join(String[] args, int from, int toExclusive) {
        if (args == null || from >= toExclusive || from < 0 || toExclusive > args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, from, toExclusive)).trim();
    }

    public static boolean equalsName(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    public static boolean containsName(Collection<String> names, String name) {
        if (names == null || name == null || name.isEmpty()) return false;
        for (String n : names) {
            if (equalsName(n, name)) return true;
        }
        return false;
    }

    public static String resolveExistingName(Collection<String> names, String raw) {
        if (raw == null) return null;
        for (String n : names) {
            if (equalsName(n, raw)) return n;
        }
        return raw;
    }

    public static int findFullNameEnd(Collection<String> names, String[] args, int from, int toExclusive) {
        if (names == null || args == null || from < 0 || toExclusive > args.length || from >= toExclusive) {
            return -1;
        }
        for (int end = toExclusive; end > from; end--) {
            String candidate = join(args, from, end);
            if (!candidate.isEmpty() && containsName(names, candidate)) {
                return end;
            }
        }
        return -1;
    }

    public static boolean hasLongerNameWithPrefix(Collection<String> names, String prefix) {
        if (names == null || prefix == null || prefix.isEmpty()) return false;
        String p = prefix.toLowerCase(Locale.ROOT) + " ";
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> completeNameToken(Collection<String> names, String[] args, int fromIndex) {
        Set<String> out = new LinkedHashSet<>();
        if (names == null || names.isEmpty() || args == null || fromIndex >= args.length) {
            return new ArrayList<>(out);
        }

        boolean trailingEmpty = args[args.length - 1].isEmpty();
        int typedEnd = trailingEmpty ? args.length - 1 : args.length;
        if (typedEnd <= fromIndex) {
            for (String name : names) {
                String first = firstToken(name);
                if (!first.isEmpty()) out.add(first);
            }
            return new ArrayList<>(out);
        }

        String[] typed = Arrays.copyOfRange(args, fromIndex, typedEnd);
        String last = typed[typed.length - 1].toLowerCase(Locale.ROOT);

        for (String name : names) {
            String[] parts = name.split(" ");
            if (typed.length > parts.length) continue;

            boolean prefixOk = true;
            for (int i = 0; i < typed.length - 1; i++) {
                if (!parts[i].equalsIgnoreCase(typed[i])) {
                    prefixOk = false;
                    break;
                }
            }
            if (!prefixOk) continue;

            String part = parts[typed.length - 1];
            if (part.toLowerCase(Locale.ROOT).startsWith(last)) {
                out.add(part);
            }
        }

        if (trailingEmpty) {
            String full = join(args, fromIndex, typedEnd);
            for (String name : names) {
                String[] parts = name.split(" ");
                String[] fullParts = full.isEmpty() ? new String[0] : full.split(" ");
                if (fullParts.length >= parts.length) continue;
                boolean ok = true;
                for (int i = 0; i < fullParts.length; i++) {
                    if (!parts[i].equalsIgnoreCase(fullParts[i])) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    out.add(parts[fullParts.length]);
                }
            }
        }

        return new ArrayList<>(out);
    }

    public static boolean isPastCacheName(Collection<String> names, String[] args, int fromIndex) {
        if (names == null || args == null || fromIndex >= args.length) return false;

        boolean trailingEmpty = args[args.length - 1].isEmpty();
        int scanEnd = trailingEmpty ? args.length - 1 : args.length;
        if (scanEnd <= fromIndex) return false;

        if (trailingEmpty) {
            String full = join(args, fromIndex, scanEnd);
            if (!containsName(names, full)) return false;
            return !hasLongerNameWithPrefix(names, full);
        }

        int nameEnd = findFullNameEnd(names, args, fromIndex, scanEnd);
        return nameEnd != -1 && nameEnd < scanEnd;
    }

    public static int nameEndIndex(Collection<String> names, String[] args, int fromIndex) {
        if (names == null || args == null) return -1;
        boolean trailingEmpty = args.length > fromIndex && args[args.length - 1].isEmpty();
        int scanEnd = trailingEmpty ? args.length - 1 : args.length;
        return findFullNameEnd(names, args, fromIndex, scanEnd);
    }

    private static String firstToken(String name) {
        if (name == null || name.isEmpty()) return "";
        int sp = name.indexOf(' ');
        return sp < 0 ? name : name.substring(0, sp);
    }
}
