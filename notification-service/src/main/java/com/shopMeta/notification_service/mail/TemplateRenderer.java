package com.shopMeta.notification_service.mail;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Renders a Thymeleaf template to an HTML string.
 *
 * <p>A thin wrapper rather than calling {@link TemplateEngine} directly from the planner, so the
 * templates' location and locale live in one place and the planner stays about event mapping.
 */
@Component
public class TemplateRenderer {

    /** Every template lives under {@code resources/templates/email}. */
    private static final String EMAIL_TEMPLATE_PREFIX = "email/";

    private final TemplateEngine templateEngine;

    public TemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param template template name without the folder or the {@code .html} suffix, e.g.
     *                 {@code order-confirmed}
     * @param model    the variables the template reads
     */
    public String render(String template, Map<String, Object> model) {
        return templateEngine.process(EMAIL_TEMPLATE_PREFIX + template, new Context(Locale.ENGLISH, model));
    }
}
