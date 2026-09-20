package dev.xcloud.taskboard.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
            select p from Project p
            where (:q is null
                   or lower(p.name) like :nameLike escape '!'
                   or lower(p.description) like :descLike escape '!')
              and (:status is null or p.status = :status)
            order by p.id desc
            """)
    List<Project> search(
            @Param("q") String q,
            @Param("nameLike") String nameLike,
            @Param("descLike") String descLike,
            @Param("status") ProjectStatus status);
}