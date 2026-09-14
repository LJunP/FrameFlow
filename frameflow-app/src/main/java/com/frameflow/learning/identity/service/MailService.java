package com.frameflow.learning.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 事务邮件。未配置 host 时只打日志（测试与未接 SMTP 的环境）；
 * 配了 host 就走 SMTP（本地默认 Mailpit :1025）。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String publicBaseUrl;

    public MailService(@Value("${frameflow.mail.host:}") String host,
                       @Value("${frameflow.mail.port:1025}") int port,
                       @Value("${frameflow.mail.username:}") String username,
                       @Value("${frameflow.mail.password:}") String password,
                       @Value("${frameflow.mail.from:FrameFlow <noreply@localhost>}") String from,
                       @Value("${frameflow.mail.public-base-url:http://127.0.0.1:3100}") String publicBaseUrl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public boolean enabled() {
        return StringUtils.hasText(host);
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    public void send(String to, String subject, String body) {
        if (!enabled()) {
            log.info("mail skipped (no FRAMEFLOW_MAIL_HOST) to={} subject={} body={}", to, subject, body);
            return;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (StringUtils.hasText(username)) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
            log.info("mail sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.warn("mail send failed to={} subject={}: {}", to, subject, e.toString());
        }
    }
}
