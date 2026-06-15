package com.hanul.aipacs.controller;

import com.hanul.aipacs.dto.StudyDtos.InstanceMetadata;
import com.hanul.aipacs.dto.StudyDtos.InstanceSummary;
import com.hanul.aipacs.dto.StudyDtos.SeriesSummary;
import com.hanul.aipacs.dto.StudyDtos.StudySummary;
import com.hanul.aipacs.service.StudyService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StudyController {
    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @GetMapping("/studies")
    public List<StudySummary> studies(Authentication authentication) {
        return studyService.listStudies(actor(authentication));
    }

    @GetMapping("/studies/{studyInstanceUid}")
    public StudySummary study(@PathVariable String studyInstanceUid, Authentication authentication) {
        return studyService.getStudy(studyInstanceUid, actor(authentication));
    }

    @GetMapping("/studies/{studyInstanceUid}/series")
    public List<SeriesSummary> series(@PathVariable String studyInstanceUid, Authentication authentication) {
        return studyService.listSeries(studyInstanceUid, actor(authentication));
    }

    @GetMapping("/studies/{studyInstanceUid}/instances")
    public List<InstanceSummary> studyInstances(@PathVariable String studyInstanceUid, Authentication authentication) {
        return studyService.listStudyInstances(studyInstanceUid, actor(authentication));
    }

    @GetMapping("/instances/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}/metadata")
    public InstanceMetadata metadata(
        @PathVariable String studyInstanceUid,
        @PathVariable String seriesInstanceUid,
        @PathVariable String sopInstanceUid,
        Authentication authentication
    ) {
        return studyService.metadata(studyInstanceUid, seriesInstanceUid, sopInstanceUid, actor(authentication));
    }

    @GetMapping("/instances/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}/preview")
    public ResponseEntity<byte[]> preview(
        @PathVariable String studyInstanceUid,
        @PathVariable String seriesInstanceUid,
        @PathVariable String sopInstanceUid,
        @RequestParam(defaultValue = "chest") String window,
        Authentication authentication
    ) {
        byte[] png = studyService.preview(studyInstanceUid, seriesInstanceUid, sopInstanceUid, window, actor(authentication));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/instances/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}/dicom")
    public ResponseEntity<byte[]> dicom(
        @PathVariable String studyInstanceUid,
        @PathVariable String seriesInstanceUid,
        @PathVariable String sopInstanceUid,
        Authentication authentication
    ) {
        byte[] dicom = studyService.dicom(studyInstanceUid, seriesInstanceUid, sopInstanceUid, actor(authentication));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/dicom"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + sopInstanceUid + ".dcm")
            .body(dicom);
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
