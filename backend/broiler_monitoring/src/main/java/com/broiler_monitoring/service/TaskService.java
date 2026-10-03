package com.broiler_monitoring.service;

import com.broiler_monitoring.dto.TaskPatchRequest;
import com.broiler_monitoring.entity.Task;
import com.broiler_monitoring.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class TaskService {
    private TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository){
        this.taskRepository = taskRepository;
    }

    public List<Task> findAll(){
        return taskRepository.findAll();
    }

    public Task create(Task task){
        return taskRepository.save(task);
    }

    public Task updateTask(Task task) {

        if (!taskRepository.existsById(task.getId())) {
            throw taskNotFound(task.getId());
        }

        return taskRepository.save(task);
    }

    @Transactional
    public Task patchTask(UUID id, TaskPatchRequest request) {
        Task task = taskRepository.findById(id).orElseThrow(() -> taskNotFound(id));

        setIfPresent(request.nameTask(), task::setNameTask);
        setIfPresent(request.descriptionTask(), task::setDescriptionTask);
        setIfPresent(request.nameIndicator(), task::setNameIndicator);
        setIfPresent(request.valueIndicator(), task::setValueIndicator);
        setIfPresent(request.measure(), task::setMeasure);
        setIfPresent(request.priority(), task::setPriority);
        setIfPresent(request.responsible(), task::setResponsible);
        setIfPresent(request.status(), task::setStatus);
        if (request.termTask() != null) {
            task.setTermTask(request.termTask());
        }

        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw taskNotFound(id);
        }
        taskRepository.deleteById(id);
    }

    private static void setIfPresent(String value, Consumer<String> setter) {
        if (value != null) {
            if (value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task fields must not be blank");
            }
            setter.accept(value);
        }
    }

    private static ResponseStatusException taskNotFound(UUID id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with id: " + id);
    }

    public List<Task> findByStatus(String status){
        return taskRepository.findByStatus(status);
    }

    public List<Task> findByPriority(String priority){
        return taskRepository.findByPriority(priority);
    }

    public List<Task> findByResponsible(String responsible){
        return taskRepository.findByResponsible(responsible);
    }

    public List<Task> findByNameIndicator(String nameIndicator){
        return taskRepository.findByNameIndicator(nameIndicator);
    }

    public List<Task> findByCreateTaskBefore(LocalDateTime date){
        return taskRepository.findByCreateTaskBefore(date);
    }

    public List<Task> findByCreateTaskAfter(LocalDateTime date){
        return taskRepository.findByCreateTaskAfter(date);
    }

    public List<Task> findByFilterTask(String status,String priority,String responsible,String nameIndicator,LocalDateTime dateFrom,LocalDateTime dateTo){
        return taskRepository.filterTasks(status,priority,responsible,nameIndicator,dateFrom,dateTo);
    }
}
