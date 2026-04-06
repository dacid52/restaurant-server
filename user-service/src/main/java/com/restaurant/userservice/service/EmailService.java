package com.restaurant.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.base-url:http://localhost:3000}")
    private String baseUrl;

    @Value("${app.frontend-url:http://localhost:3001}")
    private String frontendUrl;

    /**
     * Gửi email xác thực tài khoản.
     * Nếu mail chưa được cấu hình (username trống), in token ra console để test.
     */
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verifyUrl = baseUrl + "/api/users/verify-email?token=" + token;

        if (mailUsername == null || mailUsername.isBlank()) {
            log.info("=== [DEV MODE] Email verification token ===");
            log.info("To: {}", toEmail);
            log.info("Verify URL: {}", verifyUrl);
            log.info("===========================================");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("noreply@restaurant.local", "Nhà Hàng");
            helper.setTo(toEmail);
            helper.setSubject("Xác thực tài khoản của bạn");
            helper.setText(buildVerificationEmailHtml(fullName, verifyUrl), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Không thể gửi email xác thực đến {}: {}", toEmail, e.getMessage());
            log.info("=== [FALLBACK] Verify URL: {} ===", verifyUrl);
        }
    }

    private String buildVerificationEmailHtml(String fullName, String verifyUrl) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px;">
                  <div style="max-width: 600px; margin: auto; background: white; border-radius: 8px; padding: 32px;">
                    <h2 style="color: #c0392b;">Xác thực tài khoản</h2>
                    <p>Xin chào <strong>%s</strong>,</p>
                    <p>Cảm ơn bạn đã đăng ký tài khoản tại nhà hàng của chúng tôi.</p>
                    <p>Vui lòng bấm vào nút bên dưới để xác thực email của bạn:</p>
                    <div style="text-align: center; margin: 32px 0;">
                      <a href="%s"
                         style="background: #c0392b; color: white; padding: 14px 28px;
                                text-decoration: none; border-radius: 6px; font-size: 16px;">
                        Xác thực Email
                      </a>
                    </div>
                    <p style="color: #888; font-size: 13px;">
                      Link có hiệu lực trong 24 giờ. Nếu bạn không yêu cầu, hãy bỏ qua email này.
                    </p>
                    <p style="color: #888; font-size: 12px;">Hoặc copy link: %s</p>
                  </div>
                </body>
                </html>
                """.formatted(fullName, verifyUrl, verifyUrl);
    }
}
