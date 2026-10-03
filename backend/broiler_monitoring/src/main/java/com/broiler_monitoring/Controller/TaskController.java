package com.broiler_monitoring.Controller;

import com.broiler_monitoring.dto.TaskPatchRequest;
import com.broiler_monitoring.entity.Task;
import com.broiler_monitoring.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/task")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService){
        this.taskService = taskService;
    }

    @GetMapping
    public List<Task> getAll(){
        return taskService.findAll();
    }

    @GetMapping("/filter")
    public List<Task> getTaskFilter(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String responsible,
            @RequestParam(required = false) String nameIndicator,
            @RequestParam(required = false) LocalDateTime dateFrom,
            @RequestParam(required = false) LocalDateTime dateTo) {
        return taskService.findByFilterTask(status,priority,responsible,nameIndicator,dateFrom,dateTo);

    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(@RequestBody Task request) {
        return taskService.create(request);
    }


    @PutMapping("/{id}")
    public Task updateTask(@PathVariable UUID id, @Valid @RequestBody Task request) {
        request.setId(id);
        return taskService.updateTask(request);
    }

    @PatchMapping("/{id}")
    public Task patchTask(@PathVariable UUID id, @RequestBody TaskPatchRequest request) {
        return taskService.patchTask(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
    }
}
