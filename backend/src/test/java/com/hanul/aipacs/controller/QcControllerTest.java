package com.hanul.aipacs.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hanul.aipacs.client.OrthancClient;
import com.hanul.aipacs.domain.QcStatus;
import com.hanul.aipacs.dto.QcDtos.QcReportDto;
import com.hanul.aipacs.service.AuditService;
import com.hanul.aipacs.service.QcService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QcController.class)
class QcControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrthancClient orthancClient;

    @MockBean
    QcService qcService;

    @MockBean
    AuditService auditService;

    @Test
    @WithMockUser(username = "demo", roles = "RADIOLOGIST_DEMO")
    void validateUsesOrthancBytesAndReturnsReport() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3};
        when(orthancClient.getInstanceDicomBytes("study", "series", "sop")).thenReturn(bytes);
        when(qcService.validateAndSave(eq(bytes), eq("study"), eq("series"), eq("sop")))
            .thenReturn(new QcReportDto(null, QcStatus.PASS, List.of(), null));

        mockMvc.perform(post("/api/qc/validate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"studyInstanceUid":"study","seriesInstanceUid":"series","sopInstanceUid":"sop"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PASS"));
    }
}
