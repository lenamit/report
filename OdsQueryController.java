package vn.com.fwd.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.com.fwd.service.OdsQueryService;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@Slf4j
@RestController
@PreAuthorize("isFullyAuthenticated()")
public class OdsQueryController {
    @Autowired
    private OdsQueryService queryService;


    @PostMapping("ods/query")
    public ResponseEntity query(@RequestBody Map<String, Object> params) {
        return ResponseEntity.ok(queryService.query(params));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity handleException(Exception ex) {
        log.info(ex.getMessage());
        return ResponseEntity.ok("Invalid params");
    }

    @GetMapping("/ods/downloadReport/{reportType},{dateReport},{agentCode}")
    public void downloadFileTemplate(@PathVariable String reportType, @PathVariable String dateReport, @PathVariable String agentCode, HttpServletResponse response) throws Exception {
        OutputStream out = response.getOutputStream();
        String filePath = queryService.getResourceDetailReport(agentCode, reportType, dateReport);
        File fileTmp = new File(filePath);
        response.setContentType("application/x-download");
        response.setHeader("Content-disposition", "attachment; filename=" + fileTmp.getName());
        if (fileTmp.exists()) {
            InputStream in = new FileInputStream(fileTmp);
            int c = 0;
            while ((c = in.read()) != -1) {
                out.write(c);
            }
            out.flush();
            out.close();
            in.close();
            fileTmp.delete();
        }
    }
}
