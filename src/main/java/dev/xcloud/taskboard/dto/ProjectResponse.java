package dev.xcloud.taskboard.dto;

import dev.xcloud.taskboard.domain.Project;


public record ProjectResponse(
        Long id,
        String name,
        String description,
        String status,
        String createdAt,
        String updatedAt) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}