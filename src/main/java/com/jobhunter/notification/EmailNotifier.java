package com.jobhunter.notification;

import com.jobhunter.model.Job;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class EmailNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String fromEmail;
    private final String toEmail;

    public EmailNotifier(String smtpHost, int smtpPort, String username,
                         String password, String fromEmail, String toEmail) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.fromEmail = fromEmail;
        this.toEmail = toEmail;
    }

    @Override
    public boolean notify(List<Job> jobs) {
        if (jobs.isEmpty()) return true;

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(String.format("🔔 Job Hunter: %d new posting(s) found!", jobs.size()));
            message.setContent(buildHtmlBody(jobs), "text/html; charset=utf-8");

            Transport.send(message);
            log.info("Email notification sent to {} ({} jobs)", toEmail, jobs.size());
            return true;

        } catch (MessagingException e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            return false;
        }
    }

    private String buildHtmlBody(List<Job> jobs) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Arial, sans-serif;'>");
        html.append("<h2>🔔 ").append(jobs.size()).append(" New Job(s) Found</h2>");
        html.append("<hr>");

        for (Job job : jobs) {
            html.append("<div style='margin-bottom: 20px; padding: 10px; border: 1px solid #ddd; border-radius: 5px;'>");
            html.append("<h3 style='margin: 0;'>");
            if (job.getUrl() != null) {
                html.append("<a href='").append(job.getUrl()).append("'>").append(escapeHtml(job.getTitle())).append("</a>");
            } else {
                html.append(escapeHtml(job.getTitle()));
            }
            html.append("</h3>");
            if (job.getCompany() != null) html.append("<p><strong>Company:</strong> ").append(escapeHtml(job.getCompany())).append("</p>");
            if (job.getLocation() != null) html.append("<p><strong>Location:</strong> ").append(escapeHtml(job.getLocation())).append("</p>");
            if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
                html.append("<p><strong>Salary:</strong> ");
                if (job.getSalaryMin() != null) html.append(String.format("$%.0f", job.getSalaryMin()));
                html.append(" - ");
                if (job.getSalaryMax() != null) html.append(String.format("$%.0f", job.getSalaryMax()));
                html.append("</p>");
            }
            html.append("<p style='color: #888; font-size: 12px;'>Source: ").append(job.getSource()).append("</p>");
            html.append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
