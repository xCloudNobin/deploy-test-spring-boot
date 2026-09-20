package dev.xcloud.taskboard.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectUpdateRequest(
        @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @Pattern(regexp = "active|archived", message = "status must be one of active, archived")
        String status) {
}