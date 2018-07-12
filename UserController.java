package vn.com.fwd.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.com.fwd.model.security.Role;
import vn.com.fwd.model.security.User;
import vn.com.fwd.service.GroupService;
import vn.com.fwd.service.RoleService;
import vn.com.fwd.service.UserService;

import java.util.Arrays;
import java.util.List;


@Controller
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupService groupService;


    @ModelAttribute(name = "roles")
    public List<Role> getRoles() {
        return roleService.findAll();
    }


    @GetMapping("/users")
    public String getAllUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "user/list";
    }


    @PostMapping("/users")
    public String updateUser(@ModelAttribute User user, @RequestParam(required = false) String agentCodes) {
        if (StringUtils.isNotEmpty(agentCodes)) {
            user.setAgents(Arrays.asList(org.springframework.util.StringUtils.trimAllWhitespace(agentCodes).split("[,;]")));
        }
        user.setEnabled(true);
        userService.save(user);
        return "redirect:/users";
    }

    @GetMapping("/users/new")
    public String createUser(@ModelAttribute User user, Model model) {
        model.addAttribute("agentCodes", StringUtils.join(user.getAgents(), ","));
        return "user/detail";
    }


    @GetMapping(value = "/users", params = "username")
    public String getUser(@RequestParam String username, Model model) {
        User user = userService.fetch(username);
        if (user == null) {
            user = new User();
        } else {
            model.addAttribute("groups", groupService.findAllGroupsHave(username));
        }
        model.addAttribute("user", user);
        model.addAttribute("agentCodes", StringUtils.join(user.getAgents(), ","));
        return "user/detail";
    }


    @DeleteMapping("/users")
    public ResponseEntity removeUser(@RequestParam(required = false) String username) {
        if (StringUtils.isNotEmpty(username)) {
            userService.delete(username);
        }
        return ResponseEntity.ok().build();
    }


}
