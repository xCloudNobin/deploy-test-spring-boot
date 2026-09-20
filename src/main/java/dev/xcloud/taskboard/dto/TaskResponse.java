package dev.xcloud.taskboard.dto;

import dev.xcloud.taskboard.domain.Task;


public record TaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title,
        String description,
        String status,
        String priority,
        String createdAt,
        String updatedAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getProject().getId(),
                t.getProject().getName(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus().name(),
                t.getPriority().name(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}