package me.trae.foundation.utilities;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class IndefiniteArticle {

    private static final String A = "a ";

    private static final String AN = "an ";

    private static final Set<String> A_WORD_SET = Set.of("ledden", "soss", "ula", "un", "us");

    private static final Set<String> AN_WORD_SET = Set.of("eu", "hors", "mit", "nes", "urena", "yquem");

    private static final Set<String> A_PREFIX_SET = Set.of(
            "aaa",
            "eu", "ew",
            "herbar", "herbe", "herbi", "hive", "hmon",
            "lapdo", "leda", "lede", "ledg",
            "msgr",
            "once", "one", "oua", "oui",
            "u-", "uae", "uaw", "ubi", "uc", "uefa", "ufo", "uga", "ugr", "uhf", "ui", "uk", "ulys", "unani", "unesco", "uni", "unidir", "ura", "ure", "uri", "url", "uro", "uru", "usa", "usb", "usd", "use", "usi", "usm", "usps", "uss", "usu", "uta", "ute", "uti", "uto", "uv", "ux"
    );

    private static final Set<String> AN_PREFIX_SET = Set.of(
            "a", "e", "i", "o", "u",
            "oner", "onei", "unid", "unim", "unin", "uniss",
            "heir", "herb", "homag", "honest", "honor", "honour", "hour",
            "8",
            "fb", "fc", "fd", "ff", "fg", "fh", "fk", "fm", "fn", "fp", "fq", "ft", "fv", "fw", "fx", "fyi", "fz", "f-", "f0", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9",
            "hb", "hc", "hd", "hf", "hg", "hh", "hiv", "hk", "hl", "hmo", "hn", "hp", "hq", "hr", "hsbc", "ht", "hv", "hx", "h-", "h0", "h1", "h2", "h3", "h4", "h5", "h6", "h7", "h8", "h9",
            "lapd", "lc", "ld", "led", "lf", "lg", "lk", "llc", "llm", "llp", "lm", "lp", "lq", "lr", "ls", "lte", "lti", "lts", "ltv", "lv", "lz", "l-", "l0", "l1", "l2", "l3", "l4", "l5", "l6", "l7", "l8", "l9",
            "mba", "mcu", "md", "mf", "mg", "mh", "mj", "mk", "mlb", "mlk", "mlm", "mls", "mma", "mmo", "mmr", "mp", "mq", "mri", "mrna", "mrsa", "msc", "msg", "msn", "msrp", "mta", "mtv", "mv", "mx", "mz", "m-", "m0", "m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9",
            "naacp", "nb", "nc", "nd", "nf", "ngo", "nh", "nj", "nk", "nl", "nm", "nn", "np", "nq", "nr", "ns", "nt", "nv", "nw", "nx", "nyc", "nypd", "nyse", "nyt", "nyu", "nz", "n-", "n0", "n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8", "n9",
            "rb", "rc", "rd", "rf", "rg", "rj", "rk", "rl", "rm", "rn", "rp", "rq", "rr", "rs", "rt", "rv", "rx", "rz", "r-", "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9",
            "sba", "sd", "sftp", "sfx", "sj", "smb", "sms", "smtp", "snl", "sos", "spf", "sql", "ss", "std", "suv", "svg", "svu", "sx", "s-", "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9",
            "xb", "xc", "xd", "xf", "xg", "xhtml", "xj", "xk", "xl", "xm", "xn", "xp", "xq", "xr", "xs", "xt", "xv", "xw", "xx", "xz", "x-", "x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9",
            "ytt", "yv"
    );

    private static final Map<String, String> WORD_MAP = createMap(A_WORD_SET, AN_WORD_SET);

    private static final Map<String, String> PREFIX_MAP = createMap(A_PREFIX_SET, AN_PREFIX_SET);

    private static final int MAX_PREFIX_LENGTH = PREFIX_MAP.keySet().stream().mapToInt(String::length).max().orElse(0);

    public static String get(final String input) {
        if (UtilString.isEmpty(input)) {
            return "";
        }

        final int separator = input.indexOf(' ');

        return resolve(input.substring(0, separator == -1 ? input.length() : separator).toLowerCase(Locale.ROOT));
    }

    public static String format(final String input) {
        return UtilString.isEmpty(input) ? input : get(input) + input;
    }

    private static String resolve(final String word) {
        final String article = WORD_MAP.get(word);

        if (article != null) {
            return article;
        }

        if (word.startsWith("11") || word.startsWith("18")) {
            int digits = 0;

            while (digits < word.length() && Character.isDigit(word.charAt(digits))) {
                digits++;
            }

            return digits % 3 == 2 ? AN : A;
        }

        for (int length = Math.min(MAX_PREFIX_LENGTH, word.length()); length > 0; length--) {
            final String prefixArticle = PREFIX_MAP.get(word.substring(0, length));

            if (prefixArticle != null) {
                return prefixArticle;
            }
        }

        return A;
    }

    private static Map<String, String> createMap(final Set<String> aSet, final Set<String> anSet) {
        return Stream.concat(
                aSet.stream().map(value -> Map.entry(value, A)),
                anSet.stream().map(value -> Map.entry(value, AN))
        ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}