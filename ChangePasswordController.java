package vn.com.fwd.banca.lead.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.InvalidAttributeValueException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import vn.com.fwd.banca.lead.model.ChangePasswordForm;
import vn.com.fwd.banca.lead.service.LdapService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;

@PreAuthorize("isAuthenticated()")
@Slf4j
@Controller
public class ChangePasswordController extends AbstractController {

    @Autowired
    private LdapService service;

    @GetMapping("/change-password")
    public String getChangePassword(@ModelAttribute("changePassForm") ChangePasswordForm form) {
        form.setUsername(SecurityContextHolder.getContext().getAuthentication().getName());
        return "public/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("changePassForm") ChangePasswordForm form, BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) return "public/change-password";
        if (!StringUtils.equals(form.getPassword(), form.getVerifyPassword())) {
            bindingResult.rejectValue("verifyPassword", "Invalid");
        }
        if (bindingResult.hasErrors()) return "public/change-password";
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {

            service.changePassword(username, form.getPassword());
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            request.logout();

            return "public/change-password-success";

        } catch (UnsupportedEncodingException | InvalidAttributeValueException e) {
            log.error("Change pass not success, username is" + username, e);
            bindingResult.rejectValue("password", "Invalid");
            return "public/change-password";
        } catch (ServletException | CommunicationException e) {
            e.printStackTrace();
            return "public/fail";
        }
    }

}

