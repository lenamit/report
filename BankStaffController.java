package vn.com.fwd.banca.lead.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.extern.slf4j.Slf4j;
import vn.com.fwd.banca.lead.constants.BancaLeadConstants;
import vn.com.fwd.banca.lead.model.Agent;
import vn.com.fwd.banca.lead.model.Bank;
import vn.com.fwd.banca.lead.model.BankBranch;
import vn.com.fwd.banca.lead.model.BankOffice;
import vn.com.fwd.banca.lead.model.Lead;
import vn.com.fwd.banca.lead.model.LeadQuery;
import vn.com.fwd.banca.lead.repository.AgentRepository;
import vn.com.fwd.banca.lead.repository.BankBranchRepository;
import vn.com.fwd.banca.lead.repository.BankOfficeRepository;
import vn.com.fwd.banca.lead.repository.LeadRepository;
import vn.com.fwd.banca.lead.security.CustomLdapUserDetails;
import vn.com.fwd.banca.lead.service.LeadService;

@Slf4j
@PreAuthorize("hasRole('ROLE_BANKSTAFF')")
@Controller
@RequestMapping("/bank-staff")
public class BankStaffController extends AbstractController {

    @Autowired
    private LeadService leadService;
    
    @Autowired
    private JavaMailSender sender;
    @Autowired
    private AgentRepository agentRepository;
    @Autowired
    private BankBranchRepository bankBranchRepository;
    @Autowired
    private BankOfficeRepository bankOfficeRepository;
    @Autowired
    private LeadRepository leadRepository;
    
    @GetMapping("/offices")
    @ResponseBody
    public List<BankOffice> getOffices(@RequestParam String branchCode) {
        return leadService.findAllOffices(branchCode);
    }

    @PreAuthorize("!hasRole('ROLE_POWERUSER') and !hasRole('ROLE_BANK_ADMIN')")
    @GetMapping("/register")
    public String getRegisterPage(Model model) {
        model.addAttribute("mode", "CREATE");
        model.addAttribute("FieldNameErrors", "");
        // get office code, branch code by username from lead by top create date
        // set to lead
        Lead lead = leadRepository.findTop1ByBankStaffCodeOrderByCreatedAtDesc(getLoginUser().getUsername());
        Lead leadModleAtrr = new Lead();
        if (lead != null) {
	        leadModleAtrr.setOfficeCode(lead.getOfficeCode());
	        leadModleAtrr.setBranchCode(lead.getBranchCode());
        }
        model.addAttribute("lead", leadModleAtrr);
        
        
        return "bank-staff/register";
    }


    @PreAuthorize("!hasRole('ROLE_POWERUSER') and !hasRole('ROLE_BANK_ADMIN')")
    @PostMapping("/register")
    public String register(@RequestParam String mode, @Valid @ModelAttribute Lead lead, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        leadService.validateFsc(lead, bindingResult);
        if (StringUtils.isEmpty(lead.getContactBy())) {
        	bindingResult.rejectValue("contactBy", "NotEmpty");
        }
        if (StringUtils.isEmpty(lead.getFscContact())) {
        	bindingResult.rejectValue("fscContact", "NotEmpty");
        }
        if (StringUtils.isEmpty(lead.getFscContactBy())) {
            bindingResult.rejectValue("fscContactBy", "NotEmpty");
        }
        if (StringUtils.isEmpty(lead.getIsPriority())) {
        	bindingResult.rejectValue("isPriority", "NotEmpty");
        } else if (lead.getIsPriority().equalsIgnoreCase("YES") && StringUtils.isEmpty(lead.getPriority())) {
        	bindingResult.rejectValue("priority", "NotEmpty");
        }

        if (bindingResult.hasErrors()) {
        	String strFieldErrors = "";
        	List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        	for (FieldError fieldError : fieldErrors) {
            	if(strFieldErrors.isEmpty()) {
            		strFieldErrors = fieldError.getField();
            	} else {
            		strFieldErrors += " " + fieldError.getField();
            	}
        	}
            model.addAllAttributes(bindingResult.getModel());
            model.addAttribute("mode", mode);
            model.addAttribute("FieldNameErrors", strFieldErrors);
            return "bank-staff/register";
        }

        // check duplicate
        boolean blnDuplicate = leadService.isExist(getBankLogo(), lead.getFullName(), lead.getMobileNumber());
        CustomLdapUserDetails userLogin = getLoginUser();
        lead.setBankStaffCode(userLogin.getUsername());
        lead.setBankStaffName(userLogin.getFullName());
        lead.setBank(userLogin.getBank());
        leadService.create(lead);
        
        if (!blnDuplicate) {
	        // send mail to fsc
	        try {
		        MimeMessage message = sender.createMimeMessage();
		        MimeMessageHelper helper = new MimeMessageHelper(message);
		        helper.setFrom("FWDBancaLead@fwd.com");
		        // get mail fsc
		        Agent fscAgent = agentRepository.findByCode(lead.getFscCode());
		        helper.setTo(fscAgent.getEmail());
		        // subject
		        String subject = "Banca E – Lead – Khách hàng được giới thiệu mới <<" + lead.getBank() + ">>";
		        helper.setSubject(subject);
		        //get branch
		        BankBranch branch = bankBranchRepository.findOne(lead.getBranchCode());
		        //get office
		        BankOffice office = bankOfficeRepository.findOne(lead.getOfficeCode());
		
		        String newLine = System.lineSeparator();
		        String mailMessage ="Thân chào Tư vấn tài chính " + fscAgent.getName() + ", " + fscAgent.getCode() + newLine + newLine +
		                "Bạn có số lượng (01) khách hàng được giới thiệu từ Nhân viên ngân hàng " + lead.getBankStaffName() + 
		                ", mã số " + lead.getBankStaffCode() + " / " + branch.getCode() + " - " + branch.getName() + " / " +
		                office.getCode() + " - " + office.getName() + newLine + newLine +
		                "Cảm ơn." + newLine + newLine +
		                "Bộ phận Hỗ trợ Kinh Doanh.";
		        helper.setText(mailMessage);
		        sender.send(message);
		        log.info("=========================== Send mail success ====================================");
	        } catch (Exception e) {
	        	e.printStackTrace();
	        	log.error(e.getMessage());
	        }
        }
        redirectAttributes.addFlashAttribute("registerSuccess", true);

        return "redirect:/bank-staff/register";
    }


    @GetMapping("/query")
    public String getReportPage(@ModelAttribute LeadQuery leadQuery,
                                @RequestParam(name = "leads", required = false) ArrayList<Lead> leads, Model model) {
    	try {
	        if (leads == null) {
	        	if (getInChargeBank().size() > 0 || getInChargeBranches().size() > 0 || getInChargeOffices().size() > 0) {
	    			model.addAttribute("leads", leadService.searchBankManagerLeads(leadQuery, getInChargeBank(), getInChargeBranches(), getInChargeOffices()));
	    		} else {
	    			model.addAttribute("leads", leadService.searchBankStaffLeads(leadQuery, getLoginUser().getUsername()));
	    		}
	        } else {
	        	model.addAttribute("leads", leads);
	        }
	        model.addAttribute(BancaLeadConstants.ROLE_POWERUSER, leadService.checkIsPowerUser(getLoginUser().getUsername()));
    	} catch (Exception e) {
			log.error(e.getMessage());
		}

        return "bank-staff/query";
    }

    @PostMapping("/query")
    public String showListLead(@ModelAttribute LeadQuery leadQuery, BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) return "bank-staff/query";

        redirectAttributes.addFlashAttribute("leadQuery", leadQuery);
        List<Bank> inChargeBanks = getInChargeBank();
        List<BankBranch> inChargeBranchs = getInChargeBranches();
        List<BankOffice> inChargeOffices = getInChargeOffices();

        if (inChargeBanks.size() > 0 || inChargeBranchs.size() > 0 || inChargeOffices.size() > 0) {
            redirectAttributes.addAttribute("leads", leadService.searchBankManagerLeads(leadQuery, getInChargeBank(), getInChargeBranches(), getInChargeOffices()));
        } else {
            redirectAttributes.addAttribute("leads", leadService.searchBankStaffLeads(leadQuery, getLoginUser().getUsername()));
        }

        return "redirect:/bank-staff/query";
    }

    @SuppressWarnings("unchecked")
	@GetMapping("/view/{id}")
    public String viewLead(@PathVariable Long id, Model model) {
        Lead lead = leadService.findOne(id);
    	
    	
//    	Lead lead = leadService.getHistoryData(id, 1);
        if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase(lead.getBank(), getLoginUser().getBank())) {
            model.addAttribute("lead", lead);
            List<GrantedAuthority> grantedAuthorities = (List<GrantedAuthority>) getLoginUser().getAuthorities();
            if ("P".equalsIgnoreCase(lead.getStatus()) && !grantedAuthorities.contains(new SimpleGrantedAuthority(BancaLeadConstants.ROLE_POWERUSER))) {
            	model.addAttribute("EditAble", true);
            } else {
            	model.addAttribute("EditAble", false);
            }
            model.addAttribute("mode", "BankStaffEdit");
            model.addAttribute("FieldNameErrors", "");
            return "bank-staff/view";
        } else {
            return "error";
        }
    }
    
    @SuppressWarnings("unchecked")
	@PostMapping("/view/{id}")
    public String updateLead(@PathVariable Long id, @Valid @ModelAttribute Lead lead,
            BindingResult bindingResult, RedirectAttributes redirectAttributes, Model model) {
    	
    	Lead leadModel = leadService.findOne(id);
    	List<GrantedAuthority> grantedAuthorities = (List<GrantedAuthority>) getLoginUser().getAuthorities();
    	boolean isPowerUser = false;
    	if (grantedAuthorities.contains(new SimpleGrantedAuthority(BancaLeadConstants.ROLE_POWERUSER))) {
    		isPowerUser = true;
    	}
    	if (!"P".equalsIgnoreCase(leadModel.getStatus()) || isPowerUser) {
    		// return
    		return "redirect:/bank-staff/query";
    	}
    	
    	if (StringUtils.isEmpty(lead.getContactBy())) {
    		bindingResult.rejectValue("contactBy", "NotEmpty");
    	}
//        if (StringUtils.isEmpty(lead.getFscContact())) bindingResult.rejectValue("fscContact", "NotEmpty");
        if (StringUtils.isEmpty(lead.getFscContactBy())) {
            bindingResult.rejectValue("fscContactBy", "NotEmpty");
        }
        if (StringUtils.isEmpty(lead.getIsPriority())) {
        	bindingResult.rejectValue("isPriority", "NotEmpty");
        } else if (lead.getIsPriority().equalsIgnoreCase("YES") && StringUtils.isEmpty(lead.getPriority())) {
        	bindingResult.rejectValue("priority", "NotEmpty");
        }

        if (bindingResult.hasErrors()) {
        	String strFieldErrors = "";
        	List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        	for (FieldError fieldError : fieldErrors) {
            	if(strFieldErrors.isEmpty()) {
            		strFieldErrors = fieldError.getField();
            	} else {
            		strFieldErrors += " " + fieldError.getField();
            	}
        	}
        	// set attribute to model
        	Map<String, Object> maps =bindingResult.getModel();
        	Lead objLead = (Lead) maps.get("lead");
        	objLead.setBranchCode(leadModel.getBranchCode());
        	objLead.setOfficeCode(leadModel.getOfficeCode());
        	objLead.setFscContact(leadModel.getFscContact());
        	maps.replace("lead", objLead);
            model.addAllAttributes(maps);
            if ("P".equalsIgnoreCase(leadModel.getStatus()) && !isPowerUser) {
            	model.addAttribute("EditAble", true);
            } else {
            	model.addAttribute("EditAble", false);
            }
            model.addAttribute("mode", "BankStaffEdit");
            model.addAttribute("FieldNameErrors", strFieldErrors);
            return "bank-staff/view";
        }
        
        if (leadModel.getStatus().equalsIgnoreCase("P")) {
	        try {
	        	// set attribute modified 
	        	leadModel.setTitle(lead.getTitle());
	        	leadModel.setFullName(lead.getFullName());
	        	leadModel.setDob(lead.getDob());
	        	leadModel.setMaritalStatus(lead.getMaritalStatus());
	        	leadModel.setChildren(lead.getChildren());
	        	leadModel.setMobileNumber(lead.getMobileNumber());
	        	leadModel.setEmail(lead.getEmail());
	        	leadModel.setContactBy(lead.getContactBy());
	        	leadModel.setFscContactBy(lead.getFscContactBy());
	        	leadModel.setSelectOptions(lead.getSelectOptions());
	        	leadModel.setCompanyName(lead.getCompanyName());
	        	leadModel.setPriority(lead.getPriority());
	        	leadModel.setOtherReason(lead.getOtherReason());
	        	leadModel.setIsPriority(lead.getIsPriority());
	        	// check fsc agent update
	        	leadService.update(leadModel);
			} catch (Exception e) {
				e.printStackTrace();
			}
        } else {
        	// report to bankStaff: lead is modified by fsc-agent
        }
        
        redirectAttributes.addFlashAttribute("registerSuccess", true);

        return "redirect:/bank-staff/query";
    }

    @PostMapping(value = "/exist")
    @ResponseBody
    public ResponseEntity<Boolean> isExist(@RequestBody HashMap<String, Object> params) {
        try {
            if (leadService.isExist(getBankLogo(), params.get("name").toString(), params.get("mobileNumber").toString()))
                return ResponseEntity.ok(Boolean.TRUE);
            return ResponseEntity.ok(Boolean.FALSE);
        } catch (Exception ex) {
            return ResponseEntity.ok(Boolean.TRUE);
        }
    }

    @GetMapping("/user-guide")
    public String getUserGuidePage() {
        return "bank-staff/user-guide";
    }
    
    @PostMapping(value="/checkModifiedByFSC")
    @ResponseBody
    public ResponseEntity<Boolean> isModifiedByFSC(@RequestBody HashMap<String, Object> params) {
    	try {
    		String[] pathNames = params.get("leadId").toString().split("/");
    		Long leadId = Long.parseLong(pathNames[pathNames.length -1]);
			Lead lead = leadService.findOne(leadId);
			if (lead.getStatus().equalsIgnoreCase("P")) {
				return ResponseEntity.ok(Boolean.FALSE);
			} else {
				return ResponseEntity.ok(Boolean.TRUE);
			}
		} catch (Exception e) {
			return ResponseEntity.ok(Boolean.TRUE);
		}
    }
}

