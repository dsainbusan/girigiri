package net.dsa.girigiri.util;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 이메일 가입자용 이메일 인증(OTP) 발송 유틸. Gmail SMTP를 발신 계정으로 쓴다 — 수신자는 어떤
 * 이메일 서비스(네이버/다음/구글 등)든 상관없다(발신·수신 제공자는 SMTP상 서로 독립적이라
 * application.properties의 spring.mail.* 설정 주석 참고).
 * 담당: 문창호 (WBS 2.1 인증)
 *
 * PortOneClient/GeminiClient와 같은 패턴 — isConfigured()가 false인 동안(.env에 MAIL_USERNAME/
 * MAIL_PASSWORD 미설정)엔 sendOtpEmail()이 예외 없이 false만 돌려주고, 호출부(AuthController)가
 * 그 경우 콘솔 로그로 대체 노출한다(휴대폰 인증 목업과 동일한 안전장치 — 자격증명 없는 팀원도
 * 코드 수정 없이 흐름을 그대로 테스트할 수 있다).
 */
@Slf4j
@Component
public class EmailOtpClient {

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public EmailOtpClient(JavaMailSender mailSender, @Value("${spring.mail.username:}") String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	/** .env에 MAIL_USERNAME이 채워져 있는지 (MAIL_PASSWORD가 비어있으면 발송 시점에 인증 실패로 걸러진다). */
	public boolean isConfigured() {
		return fromAddress != null && !fromAddress.isBlank();
	}

	/** 인증번호가 담긴 메일을 실제로 발송한다. 미설정이거나 발송 중 오류가 나면 false. */
	public boolean sendOtpEmail(String toEmail, String code) {
		if (!isConfigured()) {
			return false;
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(fromAddress, "기리기리");
			helper.setTo(toEmail);
			helper.setSubject("[기리기리] 이메일 인증번호");
			helper.setText("인증번호: " + code + "\n\n3분 이내에 입력해 주세요. 본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.", false);
			mailSender.send(message);
			return true;
		} catch (Exception e) {
			log.warn("이메일 인증 발송 실패 ({}): {}", toEmail, e.getMessage());
			return false;
		}
	}
}
