package dev.xcloud.taskboard.service;

import dev.xcloud.taskboard.domain.Project;
import dev.xcloud.taskboard.domain.ProjectRepository;
import dev.xcloud.taskboard.domain.ProjectStatus;
import dev.xcloud.taskboard.domain.Task;
import dev.xcloud.taskboard.domain.TaskPriority;
import dev.xcloud.taskboard.domain.TaskRepository;
import dev.xcloud.taskboard.domain.TaskStatus;
import dev.xcloud.taskboard.dto.ProjectCreateRequest;
import dev.xcloud.taskboard.dto.ProjectResponse;
import dev.xcloud.taskboard.dto.ProjectUpdateRequest;
import dev.xcloud.taskboard.dto.TaskCreateRequest;
import dev.xcloud.taskboard.dto.TaskResponse;
import dev.xcloud.taskboard.dto.TaskUpdateRequest;
import dev.xcloud.taskboard.web.ApiNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class TaskboardService {

    private static final String ESCAPE = "!";

    private final ProjectRepository projects;
    private final TaskRepository tasks;

    public TaskboardService(ProjectRepository projects, TaskRepository tasks) {
        this.projects = projects;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(String q, String status) {
        String like = likeTerm(q);
        return projects.search(like, like, like, parseProjectStatus(status))
                .stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findProject(id));
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest req) {
        ProjectStatus status = parseProjectStatus(req.status());
        if (status == null) {
            status = ProjectStatus.active;
        }
        Project saved = projects.save(new Project(
                req.name().trim(),
                req.description() == null ? "" : req.description().trim(),
                status));
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse updateProject(Long id, ProjectUpdateRequest req) {
        Project project = findProject(id);
        String name = req.name() == null ? null : req.name().trim();
        String description = req.description() == null ? null : req.description().trim();
        project.touch(name, description, parseProjectStatus(req.status()));
        return ProjectResponse.from(projects.save(project));
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = findProject(id);
        projects.delete(project);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(String q, String status, String priority, Long projectId) {
        String like = likeTerm(q);
        return tasks.search(like, like, like, like,
                        parseTaskStatus(status), parseTaskPriority(priority), projectId)
                .stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id) {
        return TaskResponse.from(findTask(id));
    }

    @Transactional
    public TaskResponse createTask(TaskCreateRequest req) {
        Project project = projects.findById(req.projectId())
                .orElseThrow(() -> new ApiNotFoundException("project " + req.projectId() + " not found"));
        TaskStatus status = parseTaskStatus(req.status());
        if (status == null) {
            status = TaskStatus.todo;
        }
        TaskPriority priority = parseTaskPriority(req.priority());
        if (priority == null) {
            priority = TaskPriority.medium;
        }
        Task saved = tasks.save(new Task(
                project,
                req.title().trim(),
                req.description() == null ? "" : req.description().trim(),
                status,
                priority));
        return TaskResponse.from(saved);
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest req) {
        Task task = findTask(id);
        String title = req.title() == null ? null : req.title().trim();
        String description = req.description() == null ? null : req.description().trim();
        task.touch(title, description, parseTaskStatus(req.status()), parseTaskPriority(req.priority()));
        return TaskResponse.from(tasks.save(task));
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = findTask(id);
        tasks.delete(task);
    }

    private Project findProject(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new ApiNotFoundException("project " + id + " not found"));
    }

    private Task findTask(Long id) {
        return tasks.findById(id)
                .orElseThrow(() -> new ApiNotFoundException("task " + id + " not found"));
    }

    private ProjectStatus parseProjectStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ProjectStatus.valueOf(raw.toLowerCase(Locale.ROOT));
    }

    private TaskStatus parseTaskStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return TaskStatus.valueOf(raw.toLowerCase(Locale.ROOT));
    }

    private TaskPriority parseTaskPriority(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return TaskPriority.valueOf(raw.toLowerCase(Locale.ROOT));
    }

    static String likeTerm(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.toLowerCase(Locale.ROOT)
                .replace(ESCAPE, ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + escaped + "%";
    }
}