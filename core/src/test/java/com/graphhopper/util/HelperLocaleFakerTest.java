package com.graphhopper.util;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class HelperLocaleFakerTest {

    private static final Faker FAKER = new Faker();

    /**
     * Returns a random Locale string in one of the common formats that Helper.getLocale should accept:
     *   - "en"
     *   - "en_US"
     *   - "en_US_WIN" (language_country_variant)
     */
    private String randomLocaleString() {
        Locale[] all = Locale.getAvailableLocales();
        Locale base = all[ThreadLocalRandom.current().nextInt(all.length)];

        // Build a random style: language | language_country | language_country_variant
        String lang = base.getLanguage();
        String country = base.getCountry();
        String variant = base.getVariant();

        int style = ThreadLocalRandom.current().nextInt(3);
        switch (style) {
            case 0:
                return (lang == null || lang.isEmpty()) ? "en" : lang;
            case 1:
                if (lang == null || lang.isEmpty()) lang = "en";
                if (country == null || country.isEmpty()) country = "US";
                return lang + "_" + country;
            default:
                if (lang == null || lang.isEmpty()) lang = "en";
                if (country == null || country.isEmpty()) country = "US";
                if (variant == null || variant.isEmpty()) variant = "WIN";
                return lang + "_" + country + "_" + variant;
        }
    }

    @Test
    void parsesSimpleKnownLocales() {
        assertEquals("en", Helper.getLocale("en").getLanguage());
        assertEquals("de", Helper.getLocale("de_DE").getLanguage());
        assertEquals("fr", Helper.getLocale("fr_CA").getLanguage());
        assertNotNull(Helper.getLocale("en_US_WIN")); // variant tolerated
    }

    @RepeatedTest(100)
    void fuzzesRandomLocaleStrings() {
        String code = randomLocaleString();
        Locale parsed = Helper.getLocale(code);
        assertNotNull(parsed, "Parsed locale must not be null for code: " + code);

        // Basic sanity: language part (if present) should match (normalize parsed value)
        String expectedLang = code.split("_")[0].toLowerCase(Locale.ROOT);
        String actualLang = (parsed.getLanguage() == null ? "" : parsed.getLanguage())
                .trim().toLowerCase(Locale.ROOT);
        if (!expectedLang.isEmpty()) {
            assertEquals(expectedLang, actualLang, "Language mismatch for code: " + code);
        }
    }

    @Test
    void ignoresWhitespaceAndCase() {
        // Leading/trailing spaces
        Locale p1 = Helper.getLocale(" en_US ");
        String p1Lang = (p1.getLanguage() == null ? "" : p1.getLanguage()).trim().toLowerCase(Locale.ROOT);
        String p1Country = (p1.getCountry() == null ? "" : p1.getCountry()).trim().toUpperCase(Locale.ROOT);
        assertEquals("en", p1Lang);
        assertEquals("US", p1Country);

        // Mixed case with underscore
        Locale p2 = Helper.getLocale("EN_us");
        String p2Lang = (p2.getLanguage() == null ? "" : p2.getLanguage()).trim().toLowerCase(Locale.ROOT);
        String p2Country = (p2.getCountry() == null ? "" : p2.getCountry()).trim().toUpperCase(Locale.ROOT);
        assertEquals("en", p2Lang);
        assertEquals("US", p2Country);

        // Language-only with spaces and mixed case
        Locale p3 = Helper.getLocale("  Fr  ");
        String p3Lang = (p3.getLanguage() == null ? "" : p3.getLanguage()).trim().toLowerCase(Locale.ROOT);
        assertEquals("fr", p3Lang);
        // Country may be empty for language-only codes
    }

    @RepeatedTest(30)
    void randomCasingAndPadding() {
        // Keep inputs valid; vary only case + outer padding
        String base = ThreadLocalRandom.current().nextBoolean() ? "en_US" : "de_DE";

        StringBuilder sb = new StringBuilder();
        // add random outer padding (0–2 spaces on each side)
        int leftPad = ThreadLocalRandom.current().nextInt(3);
        int rightPad = ThreadLocalRandom.current().nextInt(3);
        for (int i = 0; i < leftPad; i++) sb.append(' ');

        for (char c : base.toCharArray()) {
            sb.append(ThreadLocalRandom.current().nextBoolean()
                    ? Character.toLowerCase(c)
                    : Character.toUpperCase(c));
        }
        for (int i = 0; i < rightPad; i++) sb.append(' ');

        String noisy = sb.toString();
        Locale parsed = Helper.getLocale(noisy);

        String[] parts = base.split("_");
        String expectedLang = parts[0].toLowerCase(Locale.ROOT);
        String expectedCountry = parts[1].toUpperCase(Locale.ROOT);

        String actualLang = (parsed.getLanguage() == null ? "" : parsed.getLanguage())
                .trim().toLowerCase(Locale.ROOT);
        String actualCountry = (parsed.getCountry() == null ? "" : parsed.getCountry())
                .trim().toUpperCase(Locale.ROOT);

        assertEquals(expectedLang, actualLang, "Language mismatch for code: " + noisy);
        assertEquals(expectedCountry, actualCountry, "Country mismatch for code: " + noisy);
    }
}

