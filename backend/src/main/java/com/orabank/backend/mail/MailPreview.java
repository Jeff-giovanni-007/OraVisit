package com.orabank.backend.mail;

import com.orabank.backend.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MailPreview {

    private static final String TIMEOUT_MS = "3000";

    private final JavaMailSenderImpl sender;

    public MailPreview(AppProperties properties, MailProperties mail) {
        String host = Objects.requireNonNullElse(properties.mailPreviewHost(), "").trim();
        int port = properties.mailPreviewPort();
        boolean enabled = !host.isEmpty()
                && !(host.equalsIgnoreCase(mail.getHost()) && Objects.equals(port, mail.getPort()));
        this.sender = enabled ? previewSender(host, port) : null;
        if (enabled) {
            log.info("Copie des e-mails deposee dans le SMTP de previsualisation {}:{}", host, port);
        }
    }

    public void copy(MimeMessage message, String matricule) {
        if (sender == null) {
            return;
        }
        try {
            sender.send(message);
        } catch (MailException exception) {
            log.warn("Copie de l'e-mail de l'employe {} non deposee dans {}:{} : {}",
                    matricule, sender.getHost(), sender.getPort(), exception.getMessage());
        }
    }

    private static JavaMailSenderImpl previewSender(String host, int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        Properties smtp = sender.getJavaMailProperties();
        smtp.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        smtp.put("mail.smtp.timeout", TIMEOUT_MS);
        smtp.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }
}
