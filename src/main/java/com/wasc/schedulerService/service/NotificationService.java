package com.wasc.schedulerService.service;

import com.wasc.schedulerService.model.Task;
import lombok.extern.slf4j.Slf4j; // Adicionado para usar o logger
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException; // Importado para capturar exceções de e-mail
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j // Adiciona um logger chamado 'log' na classe
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmailAlert(Task task, String subject, String messageContent) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("devwasc@gmail.com"); // Configure o remetente, pode ser o mesmo do username no properties
            message.setTo(task.getUserEmail());
            message.setSubject(subject);
            message.setText(messageContent);
            mailSender.send(message);
            log.info("E-mail de alerta enviado para: {} - Tarefa: {}", task.getUserEmail(), task.getTitle());
        } catch (MailException e) { // Captura exceções específicas de e-mail
            log.error("Erro ao enviar e-mail para {} (Tarefa: {}): {}", task.getUserEmail(), task.getTitle(), e.getMessage(), e);
        } catch (Exception e) { // Captura outras exceções inesperadas
            log.error("Erro inesperado ao enviar e-mail para {} (Tarefa: {}): {}", task.getUserEmail(), task.getTitle(), e.getMessage(), e);
        }
    }

}