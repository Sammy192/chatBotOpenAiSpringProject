package br.com.alura.ecomart.chatbot.infra.openai;

import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.runs.Run;
import com.theokanning.openai.runs.RunCreateRequest;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.Thread;
import com.theokanning.openai.threads.ThreadRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class OpenAIClient {

    private final String apiKey;
    private final String assistantId;
    private String threadId;
    private final OpenAiService service;

    public OpenAIClient(@Value("${app.openai.api.key}") String apiKey, @Value("${app.openai.assistant.id}") String assistantId) {
        this.apiKey = apiKey;
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.assistantId = assistantId;
    }

    public String enviarRequisicaoChatCompletion(DadosRequisicaoChatCompletion dados) {
        MessageRequest messageRequest = MessageRequest.builder()
                .role(ChatMessageRole.USER.value())
                .content(dados.promptUsuario())
                .build();

        if (Objects.isNull(threadId)) {
            ThreadRequest threadRequest = ThreadRequest.builder()
                    .messages(Arrays.asList(messageRequest))
                    .build();

            Thread thread = service.createThread(threadRequest);
            this.threadId = thread.getId();
        } else {
            service.createMessage(this.threadId, messageRequest);
        }

        RunCreateRequest runCreateRequest = RunCreateRequest.builder()
                .assistantId(assistantId)
                .build();

        Run run = service.createRun(threadId, runCreateRequest);

        try {
            while (!run.getStatus().equalsIgnoreCase("completed")) {
                java.lang.Thread.sleep(1000 * 3);
                run = service.retrieveRun(threadId, run.getId());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        OpenAiResponse<Message> mensagens = service.listMessages(threadId);

        String respostaAssistente = mensagens.getData().stream()
                .sorted(Comparator.comparingInt(Message::getCreatedAt).reversed())
                .findFirst().get().getContent().get(0).getText().getValue();

        return respostaAssistente;
    }

    public List<String> carregarHistoricoDeMensagens() {
        ArrayList<String> mensagens = new ArrayList<String>();

        if (Objects.nonNull(threadId)) {
            List<String> listaMensagens = service.listMessages(threadId).getData()
                    .stream()
                    .sorted(Comparator.comparingInt(Message::getCreatedAt))
                    .map(m -> m.getContent().get(0).getText().getValue())
                    .toList();

            mensagens.addAll(listaMensagens);
        }

        return mensagens;
    }
}
