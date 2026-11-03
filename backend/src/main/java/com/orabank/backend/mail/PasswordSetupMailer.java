package com.orabank.backend.mail;

import com.orabank.backend.config.AppProperties;
import com.orabank.backend.service.PasswordSetupLinkIssued;
import com.orabank.backend.service.PasswordSetupService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordSetupMailer {

    private static final String SETUP_PATH = "/definir-mot-de-passe";
    private static final String LOGO_ID = "orabank-logo";
    private static final ClassPathResource LOGO = new ClassPathResource("mail/orabank-logo.png");
    private static final String MAIL_ICON_ID = "mail-icon";
    private static final ClassPathResource MAIL_ICON = new ClassPathResource("mail/mail-icon.png");
    private static final String BANNER_ID = "security-banner";
    private static final ClassPathResource BANNER = new ClassPathResource("mail/security-banner.jpg");
    private static final ClassPathResource RESET_BANNER = new ClassPathResource("mail/security-banner-reset.jpg");
    private static final String TEMPLATE = load(new ClassPathResource("mail/password-setup.html"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final JavaMailSender mailSender;
    private final MailPreview mailPreview;
    private final AppProperties properties;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordSetupLinkIssued event) {
        String link = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path(SETUP_PATH)
                .queryParam("token", event.token())
                .build()
                .toUriString();
        if (properties.logPasswordSetupLinks()) {
            log.info("[dev] Lien de definition du mot de passe de {} : {}", event.matricule(), link);
        }

        String expiry = PasswordSetupService.EXPIRY_FORMAT.format(event.expiresAt().atZone(clock.getZone()));
        MimeMessage message;
        try {
            message = message(event, link, expiry);
        } catch (MessagingException exception) {
            log.error("Preparation de l'e-mail de definition du mot de passe de l'employe {} impossible : {}",
                    event.matricule(), exception.getMessage());
            return;
        }
        try {
            mailSender.send(message);
            log.info("Lien de definition du mot de passe envoye a l'employe {}", event.matricule());
        } catch (MailException exception) {
            log.error("Echec de l'envoi du lien de definition du mot de passe a l'employe {} : {}",
                    event.matricule(), exception.getMessage());
        }
        mailPreview.copy(message, event.matricule());
    }

    private MimeMessage message(PasswordSetupLinkIssued event, String link, String expiry) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.mailFrom());
        helper.setTo(event.email());
        helper.setSubject("OraVisit - " + title(event));
        helper.setText(plainText(event, link, expiry), html(event, link, expiry));
        helper.addInline(LOGO_ID, LOGO.getFilename(), LOGO, "image/png");
        helper.addInline(MAIL_ICON_ID, MAIL_ICON.getFilename(), MAIL_ICON, "image/png");
        ClassPathResource banner = event.passwordAlreadySet() ? RESET_BANNER : BANNER;
        helper.addInline(BANNER_ID, banner.getFilename(), banner, "image/jpeg");
        return message;
    }

    private String title(PasswordSetupLinkIssued event) {
        return event.passwordAlreadySet()
                ? "Réinitialisation de votre mot de passe"
                : "Définition de votre mot de passe";
    }

    private String titleKind(PasswordSetupLinkIssued event) {
        return event.passwordAlreadySet() ? "reset" : "setup";
    }

    private String buttonLabel(PasswordSetupLinkIssued event) {
        return event.passwordAlreadySet() ? "Réinitialiser mon mot de passe" : "Définir mon mot de passe";
    }

    private String introduction(PasswordSetupLinkIssued event) {
        return event.passwordAlreadySet()
                ? "Une réinitialisation de votre mot de passe OraVisit a été demandée par un administrateur."
                : "Un accès à OraVisit vous a été attribué (rôle : %s).".formatted(event.role().getLabel());
    }

    private String plainText(PasswordSetupLinkIssued event, String link, String expiry) {
        return """
                Bonjour %s,

                %s

                Pour %s votre mot de passe, ouvrez ce lien :
                %s

                Votre matricule de connexion : %s
                Ce lien est personnel, utilisable une seule fois et valable jusqu'au %s.

                Si vous n'attendiez pas ce message, ignorez-le et prévenez votre administrateur.
                Orabank ne vous demandera jamais votre mot de passe.

                Orabank Togo - OraVisit
                """.formatted(event.firstName(), introduction(event),
                event.passwordAlreadySet() ? "réinitialiser" : "définir", link, event.matricule(), expiry);
    }

    private String html(PasswordSetupLinkIssued event, String link, String expiry) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("title", HtmlUtils.htmlEscape(title(event))),
                Map.entry("titleKind", titleKind(event)),
                Map.entry("buttonLabel", HtmlUtils.htmlEscape(buttonLabel(event))),
                Map.entry("firstName", HtmlUtils.htmlEscape(event.firstName())),
                Map.entry("introduction", HtmlUtils.htmlEscape(introduction(event))),
                Map.entry("matricule", HtmlUtils.htmlEscape(event.matricule())),
                Map.entry("link", HtmlUtils.htmlEscape(link)),
                Map.entry("expiry", HtmlUtils.htmlEscape(expiry)),
                Map.entry("logo", "cid:" + LOGO_ID),
                Map.entry("mailIcon", "cid:" + MAIL_ICON_ID),
                Map.entry("banner", "cid:" + BANNER_ID),
                Map.entry("bannerAlt", event.passwordAlreadySet()
                        ? "Veuillez réinitialiser votre mot de passe"
                        : "Veuillez définir votre mot de passe"));
        return PLACEHOLDER.matcher(TEMPLATE)
                .replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }

    private static String load(ClassPathResource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Modele d'e-mail introuvable : " + resource.getPath(), exception);
        }
    }
}
