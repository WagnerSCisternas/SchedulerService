package com.wasc.schedulerService.api;

import com.wasc.schedulerService.model.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate; // Pode ser necessário importar explicitamente

@Service
@Slf4j
public class TaskApiClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String authUrl;
    private final String listTasksPath; // <<--- NOVA PROPRIEDADE
    private final String apiUsername;
    private final String apiPassword;
    private final ObjectMapper objectMapper;

    private String jwtToken;

    public TaskApiClient(WebClient webClient,
                         @Value("${api.tasks.base-url}") String baseUrl,
                         @Value("${api.tasks.auth-url}") String authUrl,
                         @Value("${api.tasks.list-path}") String listTasksPath, // <<--- INJETAR AQUI
                         @Value("${api.tasks.username}") String apiUsername,
                         @Value("${api.tasks.password}") String apiPassword,
                         ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.authUrl = authUrl;
        this.listTasksPath = listTasksPath; // <<--- ATRIBUIR AQUI
        this.apiUsername = apiUsername;
        this.apiPassword = apiPassword;
        this.objectMapper = objectMapper;
    }

    // ... (o método authenticate() permanece igual, não precisa de listTasksPath)

    public String authenticate() {
        String fullAuthUrl = baseUrl + authUrl;
        log.info("Tentando autenticar na URL: {}", fullAuthUrl);

        String loginPayload;
        try {
            loginPayload = objectMapper.writeValueAsString(
                new LoginRequest(apiUsername, apiPassword)
            );
            log.debug("Payload de autenticação (DEBUG): {}", loginPayload);
        } catch (Exception e) {
            log.error("Erro ao criar payload de login: {}", e.getMessage(), e);
            return null;
        }

        try {
            String tokenResponse = webClient.post()
                    .uri(fullAuthUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginPayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do cliente (4xx) na autenticação. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro de autenticação do cliente: " + body));
                                })
                    )
                    .onStatus(status -> status.is5xxServerError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do servidor (5xx) na autenticação. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro de autenticação do servidor: " + body));
                                })
                    )
                    .bodyToMono(String.class)
                    .block();

            log.info("Resposta bruta da autenticação: {}", tokenResponse);

            if (tokenResponse != null && !tokenResponse.trim().isEmpty()) {
                try {
                    JsonNode rootNode = objectMapper.readTree(tokenResponse);
                    if (rootNode.has("token")) {
                        this.jwtToken = rootNode.get("token").asText();
                    } else if (rootNode.has("accessToken")) {
                        this.jwtToken = rootNode.get("accessToken").asText();
                    } else if (rootNode.has("jwt")) { // Adicionado esta condição para a chave "jwt"
                        this.jwtToken = rootNode.get("jwt").asText();
                        log.info("Token JWT encontrado na chave 'jwt'.");
                    }
                    else {
                        this.jwtToken = tokenResponse.trim();
                        log.warn("Resposta de autenticação não contém 'token', 'accessToken' ou 'jwt'. Tentando usar resposta bruta como token.");
                    }
                } catch (Exception jsonEx) {
                    this.jwtToken = tokenResponse.trim();
                    log.warn("Erro ao parsear resposta JSON. Tratando resposta bruta como token: {}", jsonEx.getMessage());
                }
            } else {
                log.warn("Resposta de autenticação vazia ou nula.");
                this.jwtToken = null;
            }

            if (this.jwtToken != null && !this.jwtToken.isEmpty()) {
            	log.info("JWT Token obtido. Início: 'hidden'");
            	//log.info("JWT Token obtido. Início: {}...", this.jwtToken.substring(0, Math.min(this.jwtToken.length(), 20)));
                return this.jwtToken;
            } else {
                log.error("Token JWT não foi obtido ou está vazio após parsing.");
                return null;
            }

        } catch (WebClientResponseException e) {
            log.error("Erro de resposta do WebClient na autenticação (Status: {}): {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado durante a autenticação na API de tarefas: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * Obtém tarefas para um intervalo de datas.
     * @param jwtToken O token JWT para autenticação.
     * @param startDate Data de início do filtro.
     * @param endDate Data de fim do filtro.
     * @return Uma lista de tarefas.
     */
    public List<Task> getTasksByDueDateRange(String jwtToken, LocalDate startDate, LocalDate endDate) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            log.error("Token JWT ausente para buscar tarefas.");
            return List.of();
        }

        // Mude a URL para usar a nova propriedade 'listTasksPath'
        String url = String.format("%s%s?dueDateFrom=%s&dueDateTo=%s",
                                   baseUrl, listTasksPath, startDate.toString(), endDate.toString());

        log.info("Buscando tarefas na URL: {}", url); // Adicionado log da URL de busca

        try {
            return webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do cliente (4xx) ao buscar tarefas. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro ao buscar tarefas: " + body));
                                })
                    )
                    .onStatus(status -> status.is5xxServerError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do servidor (5xx) ao buscar tarefas. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro ao buscar tarefas do servidor: " + body));
                                })
                    )
                    .bodyToFlux(Task.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Erro de resposta do WebClient ao buscar tarefas (Status: {}): {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            return List.of();
        } catch (Exception e) {
            log.error("Erro inesperado ao buscar tarefas da API: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ... (o método updateTaskAlertStatus() e a classe LoginRequest permanecem iguais)

    public void updateTaskAlertStatus(Long taskId, boolean alertSentOneDayBefore, boolean alertSentOnDay, String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            log.error("Token JWT ausente para atualizar status da tarefa.");
            return;
        }

        String updateUrl = String.format("%s/tasks/%d/updateAlertStatus", baseUrl, taskId);
        String updatePayload = String.format("{\"alertSentOneDayBefore\": %b, \"alertSentOnDay\": %b}",
                                             alertSentOneDayBefore, alertSentOnDay);

        try {
            webClient.put()
                    .uri(updateUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updatePayload)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do cliente (4xx) ao atualizar status da tarefa. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro ao atualizar status da tarefa: " + body));
                                })
                    )
                    .onStatus(status -> status.is5xxServerError(), (ClientResponse response) ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("Erro do servidor (5xx) ao atualizar status da tarefa. Status: {}. Corpo: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException("Erro ao atualizar status da tarefa do servidor: " + body));
                                })
                    )
                    .toBodilessEntity()
                    .block();
            log.info("Status de alerta da tarefa {} atualizado na API.", taskId);
        } catch (WebClientResponseException e) {
            log.error("Erro de resposta do WebClient ao atualizar status da tarefa {} (Status: {}): {}", taskId, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar status de alerta da tarefa {}: {}", taskId, e.getMessage(), e);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class LoginRequest {
        private String username;
        private String password;
    }
}