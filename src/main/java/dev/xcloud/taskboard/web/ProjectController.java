package dev.xcloud.taskboard.web;

import dev.xcloud.taskboard.dto.ProjectCreateRequest;
import dev.xcloud.taskboard.dto.ProjectResponse;
import dev.xcloud.taskboard.dto.ProjectUpdateRequest;
import dev.xcloud.taskboard.service.TaskboardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final TaskboardService service;

    public ProjectController(TaskboardService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        return service.listProjects(q, status);
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody ProjectCreateRequest req,
            UriComponentsBuilder uriBuilder) {
        ProjectResponse created = service.createProject(req);
        URI location = uriBuilder.path("/api/projects/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return service.getProject(id);
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest req) {
        return service.updateProject(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteProject(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}