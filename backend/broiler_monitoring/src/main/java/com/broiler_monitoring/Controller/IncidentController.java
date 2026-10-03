package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.IncidentAttachmentResponse;
import com.broiler_monitoring.dto.AssignIncidentRequest;
import com.broiler_monitoring.entity.Incident;
import com.broiler_monitoring.enumerated.IncidentStatus;
import com.broiler_monitoring.service.IncidentAttachmentService;
import com.broiler_monitoring.service.IncidentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService service;
    private final IncidentAttachmentService attachmentService;

    public IncidentController(IncidentService service, IncidentAttachmentService attachmentService){
        this.service = service;
        this.attachmentService = attachmentService;
    }
    @GetMapping
    public List<Incident> getIncident(){
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Incident getById(@PathVariable UUID id){
        return service.getById(id);
    }

    @GetMapping("/code/{code}")
    public Incident getByCode(@PathVariable String code){
        return service.getByCode(code);
    }

    @PostMapping
    public Incident create(@RequestBody Incident incident){
        return service.create(incident);
    }

    @PostMapping("/from-notification/{notificationId}")
    public Incident createFromNotification(
            @PathVariable UUID notificationId,
            @RequestBody(required = false) Incident incident
    ){
        return service.createFromNotification(notificationId, incident);
    }

    @GetMapping("/{id}/attachments")
    public List<IncidentAttachmentResponse> getAttachments(@PathVariable UUID id){
        return attachmentService.findByIncidentId(id);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<IncidentAttachmentResponse> uploadAttachments(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files
    ){
        return attachmentService.upload(id, files);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/content")
    public ResponseEntity<StreamingResponseBody> openAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ){
        IncidentAttachmentService.AttachmentDownload attachment = attachmentService.openAttachment(id, attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .contentLength(attachment.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(attachment.body());
    }

    @PatchMapping("/{id}/status")
    public Incident changeStatus(
            @PathVariable UUID id,
            @RequestBody IncidentStatus status){
        return service.changeStatus(id, status);
    }

    @PatchMapping("/{id}/assign")
    public Incident assign(
            @PathVariable UUID id,
            @RequestBody(required = false) AssignIncidentRequest request){
        return service.assign(id, request);
    }


    @PutMapping("/{id}")
    public Incident update(
            @PathVariable UUID id,
            @RequestBody Incident incident
    ){
        return service.update(id, incident);
    }


}
