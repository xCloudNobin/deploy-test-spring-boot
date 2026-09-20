package dev.xcloud.taskboard.web;

import dev.xcloud.taskboard.dto.TaskCreateRequest;
import dev.xcloud.taskboard.dto.TaskResponse;
import dev.xcloud.taskboard.dto.TaskUpdateRequest;
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
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskboardService service;

    public TaskController(TaskboardService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long projectId) {
        return service.listTasks(q, status, priority, projectId);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody TaskCreateRequest req,
            UriComponentsBuilder uriBuilder) {
        TaskResponse created = service.createTask(req);
        URI location = uriBuilder.path("/api/tasks/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return service.getTask(id);
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest req) {
        return service.updateTask(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteTask(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}