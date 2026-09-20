package dev.xcloud.taskboard;

import dev.xcloud.taskboard.service.SchemaInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskboardIntegrationTest {

    private static final Path TEST_DB;

    static {
        try {
            TEST_DB = Files.createTempDirectory("taskboard-test").resolve("test.db");
            System.out.println("TEST_DB=" + TEST_DB);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchemaInitializer schemaInitializer;

    @BeforeEach
    void reset() throws Exception {
        schemaInitializer.resetAndSeed();
    }

    @Test
    void seedDataIsPresent() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", hasItems("Website Redesign", "Operations Backlog")));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void projectCrudLifecycle() throws Exception {
        String create = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API Project\",\"description\":\"from test\",\"status\":\"active\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/projects/")))
                .andExpect(jsonPath("$.name").value("API Project"))
                .andExpect(jsonPath("$.description").value("from test"))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn().getResponse().getContentAsString();

        long id = extractId(create);

        mockMvc.perform(get("/api/projects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API Project"));

        mockMvc.perform(patch("/api/projects/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"));

        mockMvc.perform(delete("/api/projects/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void taskCrudLifecycle() throws Exception {
        long projectId = createProject("Task Host");
        String taskJson = "{\"projectId\":" + projectId
                + ",\"title\":\"Write tests\",\"description\":\"covers API\",\"status\":\"todo\",\"priority\":\"high\"}";

        long taskId = extractId(mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectName").value("Task Host"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(patch("/api/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"done\",\"priority\":\"low\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.priority").value("low"));

        mockMvc.perform(delete("/api/tasks/" + taskId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectDeleteCascadesTasks() throws Exception {
        long projectId = createProject("Cascade Me");
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectId + ",\"title\":\"child task\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/projects/" + projectId))
                .andExpect(status().isNoContent());

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);
             ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM task WHERE project_id NOT IN (SELECT id FROM project)")) {
            rs.next();
            org.junit.jupiter.api.Assertions.assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void searchAndStatusFilters() throws Exception {
        long pA = createProject("Alpha Team");
        long pB = createProject("Beta Crew");
        createTask(pA, "Fix login redirect", "done", "high");
        createTask(pA, "Fix pagination bug", "todo", "medium");
        createTask(pB, "Draft handbook", "todo", "low");

        mockMvc.perform(get("/api/tasks").param("q", "fix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", hasItem("Fix login redirect")))
                .andExpect(jsonPath("$[*].title", hasItem("Fix pagination bug")));

        mockMvc.perform(get("/api/tasks").param("status", "done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Fix login redirect")))
                .andExpect(jsonPath("$[*].title", not(hasItem("Fix pagination bug"))));

        mockMvc.perform(get("/api/tasks").param("priority", "low")
                        .param("q", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Draft handbook"));

        mockMvc.perform(get("/api/tasks").param("projectId", String.valueOf(pB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Draft handbook"));

        mockMvc.perform(get("/api/projects").param("q", "beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Beta Crew"));
    }

    @Test
    void searchEscapesLikeWildcards() throws Exception {
        long p = createProject("Wildcards");
        createTask(p, "100% automated", "todo", "medium");

        mockMvc.perform(get("/api/tasks").param("q", "100% aut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/tasks").param("q", "100_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/tasks").param("q", "%a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validationRejectsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"status\":\"banana\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value(not(emptyOrNullString())));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No project\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.projectId").exists());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":999991,\"title\":\"ghost\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":1,\"title\":\"ok\",\"status\":\"banana\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        long p = createProject("Patch Target");
        mockMvc.perform(patch("/api/projects/" + p)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthProbes() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metaReportsReleaseMarker() throws Exception {
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("deploy-test-spring-boot"))
                .andExpect(jsonPath("$.release").isNotEmpty());
    }

    @Test
    void rowsPersistOnDiskThroughIndependentConnection() throws Exception {
        long p = createProject("Disk Persist");
        createTask(p, "survivor row", "todo", "high");

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);
             ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM project WHERE name='Disk Persist'")) {
            rs.next();
            org.junit.jupiter.api.Assertions.assertEquals(1, rs.getInt(1));
        }
    }

    private long createProject(String name) throws Exception {
        String body = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private long createTask(long projectId, String title, String status, String priority) throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":" + projectId + ",\"title\":\"" + title
                                + "\",\"status\":\"" + status + "\",\"priority\":\"" + priority + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractId(body);
    }

    private static long extractId(String json) {
        String token = "\"id\":";
        int start = json.indexOf(token) + token.length();
        int end = json.indexOf(',', start);
        return Long.parseLong(json.substring(start, end));
    }
}