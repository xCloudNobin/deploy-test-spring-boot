package dev.xcloud.taskboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    protected Project() {
    }

    public Project(String name, String description, ProjectStatus status) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.status = status;
        String now = LocalDateTimeFormatter.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(String name, String description, ProjectStatus status) {
        this.name = name == null ? this.name : name;
        this.description = description == null ? this.description : description;
        this.status = status == null ? this.status : status;
        this.updatedAt = LocalDateTimeFormatter.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}