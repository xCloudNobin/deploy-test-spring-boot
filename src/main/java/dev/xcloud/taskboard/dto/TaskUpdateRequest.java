package dev.xcloud.taskboard.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "title must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @Pattern(regexp = "todo|in_progress|done", message = "status must be one of todo, in_progress, done")
        String status,

        @Pattern(regexp = "low|medium|high", message = "priority must be one of low, medium, high")
        String priority) {
}