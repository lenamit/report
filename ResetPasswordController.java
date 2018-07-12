package vn.com.fwd.banca.lead.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.OperationNotSupportedException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import vn.com.fwd.banca.lead.model.Agent;
import vn.com.fwd.banca.lead.model.ForgetPasswordForm;
import vn.com.fwd.banca.lead.repository.AgentRepository;
import vn.com.fwd.banca.lead.security.CustomLdapUserDetails;
import vn.com.fwd.banca.lead.service.LdapService;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.UUID;


@Slf4j
@Controller
public class ResetPasswordController {

    private final LdapService service;
    private final JavaMailSender sender;
    private final AgentRepository repository;


    @Autowired
    public ResetPasswordController(LdapService service, JavaMailSender sender, AgentRepository repository) {
        this.service = service;
        this.sender = sender;
        this.repository = repository;
    }

    @ModelAttribute("loginUser")
    public CustomLdapUserDetails getLoginUser() {
        CustomLdapUserDetails result;
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomLdapUserDetails) {
            result = (CustomLdapUserDetails) principal;
        } else {
            result = new CustomLdapUserDetails();
            if (principal instanceof UserDetails) result.setDetails((UserDetails) principal);
        }
        return result;
    }


    @GetMapping("/reset-password")
    public String getForgetPasswordPage(@ModelAttribute("forgetPassForm") ForgetPasswordForm form) {
        return "public/reset-password";
    }


    @PostMapping("/reset-password")
    public String postForgetPassword(@ModelAttribute("forgetPassForm") ForgetPasswordForm form, BindingResult bindingResult) {
        String username = form.getUsername();
        if (StringUtils.isEmpty(username)) {
            bindingResult.rejectValue("username", "IsEmpty");
        } else if (!service.isUserExist(username)) {
            bindingResult.rejectValue("username", "Invalid");
        } else if (StringUtils.isEmpty(form.getEmail())) {
            bindingResult.rejectValue("email", "IsEmpty");
        } else if (!StringUtils.equalsIgnoreCase(getEmail(username), form.getEmail())) {
            bindingResult.rejectValue("email", "Invalid");
        }

        if (bindingResult.hasErrors()) return "public/reset-password";
        try {
            String newPassword = generatePassword();
            log.info(newPassword);
            sendNewPasswordMail(username, getEmail(username), newPassword);
            service.resetPassword(username, newPassword);
        } catch (OperationNotSupportedException | UnsupportedEncodingException | CommunicationException ex) {
            log.error("Cannot change password, username is " + username, ex);
            return "public/fail";
        } catch (MessagingException | MailSendException ex) {
            log.error("Cannot send mail, username is " + username, ex);
            return "public/fail";
        }
        return "public/reset-password-success";
    }

    private String getEmail(String username) {
        Agent agent = repository.findByAgentid(username);
        if (agent == null) return "";
        else return agent.getEmail();
    }


    private void sendNewPasswordMail(String username, String email, String newPassword) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);
        helper.setFrom("FWDBancaLead@fwd.com");
        helper.setTo(email);
        helper.setSubject("FWD_Banca_Thay đổi mật khẩu");

        String newLine = System.lineSeparator();
        helper.setText("Xin chào " + username + newLine + newLine +
                "Bạn đã gửi yêu cầu thay đổi mật khẩu tới hệ thống cổng thông tin Lead Tracking System." + newLine +
                "Mật khẩu mới của bạn là \"" + newPassword + "\"" + newLine +
                "Vui lòng đăng nhập vào hệ thống để thay đổi mật khẩu." + newLine + newLine +
                "Trân trọng cảm ơn," + newLine +
                "Administrator"
        );

        sender.send(message);
        log.info("Send reset password email to " + email);
    }


    private String generatePassword() {
        return "Fwd" + StringUtils.left(RandomUtils.nextInt(0, 9) + UUID.randomUUID().toString().replaceAll("-", ""), 7).toUpperCase();
    }


}
