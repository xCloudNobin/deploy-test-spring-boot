package dev.xcloud.taskboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t from Task t
            where (:q is null
                   or lower(t.title) like :titleLike escape '!'
                   or lower(t.description) like :descLike escape '!'
                   or lower(t.project.name) like :projLike escape '!')
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:projectId is null or t.project.id = :projectId)
            order by t.id desc
            """)
    List<Task> search(
            @Param("q") String q,
            @Param("titleLike") String titleLike,
            @Param("descLike") String descLike,
            @Param("projLike") String projLike,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("projectId") Long projectId);
}