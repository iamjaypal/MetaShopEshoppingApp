package com.ShopMeta.product_service.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Generates URL-friendly slugs from arbitrary text (spec section 8.4: slug auto-generated from name).
 */
public final class SlugUtils {

    private SlugUtils() {
    }

    public static String toSlug(String input) {

        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
