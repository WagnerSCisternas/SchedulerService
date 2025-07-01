package com.wasc.schedulerService.service;

import com.wasc.schedulerService.api.TaskApiClient;
import com.wasc.schedulerService.model.Task;
import lombok.extern.slf4j.Slf4j; // Adicionado para usar o logger
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j // Adiciona um logger chamado 'log' na classe
public class TaskAlertScheduler {

    @Autowired
    private TaskApiClient taskApiClient;
    @Autowired
    private NotificationService notificationService;

    // Este método será executado a cada 15 minutos (900000 milissegundos)
    @Scheduled(fixedRate = 900000)
    public void checkTasksAndSendAlerts() {
        log.info("Iniciando verificação de tarefas para alertas...");

        String jwtToken = null;
        try {
            jwtToken = taskApiClient.authenticate();
            if (jwtToken == null) {
                log.error("Falha ao obter token JWT. Não será possível buscar tarefas.");
                return;
            }
        } catch (Exception e) {
            log.error("Exceção durante a autenticação JWT: {}", e.getMessage(), e);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Task> tasks = taskApiClient.getTasksByDueDateRange(jwtToken, today, tomorrow);

        if (tasks.isEmpty()) {
            log.info("Nenhuma tarefa encontrada para hoje ou amanhã.");
            return;
        }

        log.info("Encontradas {} tarefas para verificar.", tasks.size());

        for (Task task : tasks) {
            LocalDate taskDueDate = task.getDueDate();

            // Alerta "Um dia antes"
            if (taskDueDate.isEqual(tomorrow) && !task.isAlertSentOneDayBefore()) {
                String subject = "Lembrete: Sua tarefa '" + task.getTitle() + "' vence amanhã!";
                String message = "Olá " + task.getUserName() + ",\n\n" +
                                 "Sua tarefa '" + task.getTitle() + "' está agendada para amanhã, " + task.getDueDate() + ".\n" +
                                 "Não se esqueça de concluí-la!\n\n" +
                                 "Descrição: " + task.getDescription();
                notificationService.sendEmailAlert(task, subject, message);

                // Atualizar o status na API para evitar reenvio
                taskApiClient.updateTaskAlertStatus(task.getId(), true, task.isAlertSentOnDay(), jwtToken);
            }

            // Alerta "No próprio dia"
            if (taskDueDate.isEqual(today) && !task.isAlertSentOnDay()) {
                String subject = "Alerta Final: Sua tarefa '" + task.getTitle() + "' vence HOJE!";
                String message = "Olá " + task.getUserName() + ",\n\n" +
                                 "Sua tarefa '" + task.getTitle() + "' vence HOJE, " + task.getDueDate() + ".\n" +
                                 "Por favor, certifique-se de finalizá-la.\n\n" +
                                 "Descrição: " + task.getDescription();
                notificationService.sendEmailAlert(task, subject, message);

                // Atualizar o status na API para evitar reenvio
                taskApiClient.updateTaskAlertStatus(task.getId(), task.isAlertSentOneDayBefore(), true, jwtToken);
            }
        }
        log.info("Verificação de tarefas concluída.");
    }
}