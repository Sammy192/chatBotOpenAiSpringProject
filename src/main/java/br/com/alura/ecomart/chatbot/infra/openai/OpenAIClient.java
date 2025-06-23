package br.com.alura.ecomart.chatbot.infra.openai;

import br.com.alura.ecomart.chatbot.domain.DadosCalculoFrete;
import br.com.alura.ecomart.chatbot.domain.service.CalculadorDeFrete;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.runs.Run;
import com.theokanning.openai.runs.RunCreateRequest;
import com.theokanning.openai.runs.SubmitToolOutputRequestItem;
import com.theokanning.openai.runs.SubmitToolOutputsRequest;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.Thread;
import com.theokanning.openai.threads.ThreadRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class OpenAIClient {

    private final String apiKey;
    private final String assistantId;
    private String threadId;
    private final OpenAiService service;
    private final CalculadorDeFrete calculadorDeFrete;

    public OpenAIClient(@Value("${app.openai.api.key}") String apiKey, @Value("${app.openai.assistant.id}") String assistantId, CalculadorDeFrete calculadorDeFrete) {
        this.apiKey = apiKey;
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.assistantId = assistantId;
        this.calculadorDeFrete = calculadorDeFrete;
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

        var concluido = false;
        var precisaChamarFuncao = false;
        try {
            while (!concluido && !precisaChamarFuncao) {
                java.lang.Thread.sleep(1000 * 5L);
                run = service.retrieveRun(threadId, run.getId());
                concluido = run.getStatus().equalsIgnoreCase("completed");
                precisaChamarFuncao = run.getRequiredAction() != null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (precisaChamarFuncao) {
            String precoDoFrete = chamarFuncao(run);
            var submitRequest = SubmitToolOutputsRequest
                    .builder()
                    .toolOutputs(Arrays.asList(
                            new SubmitToolOutputRequestItem(
                                    run
                                            .getRequiredAction()
                                            .getSubmitToolOutputs()
                                            .getToolCalls()
                                            .get(0)
                                            .getId(),
                                    precoDoFrete)
                    ))
                    .build();
            service.submitToolOutputs(threadId, run.getId(), submitRequest);

            try {
                while (!concluido) {
                    java.lang.Thread.sleep(1000 * 5L);
                    run = service.retrieveRun(threadId, run.getId());
                    concluido = run.getStatus().equalsIgnoreCase("completed");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        OpenAiResponse<Message> mensagens = service.listMessages(threadId);

        String respostaAssistente = mensagens.getData().stream()
                .sorted(Comparator.comparingInt(Message::getCreatedAt).reversed())
                .findFirst().get().getContent().get(0).getText()
                .getValue()
                .replaceAll("\\\u3010.*?\\\u3011", "");

        return respostaAssistente;
    }

    private String chamarFuncao(Run run) {
        try {
            var funcao = run.getRequiredAction().getSubmitToolOutputs().getToolCalls().get(0).getFunction();
            var funcaoCalcularFrete = ChatFunction.builder()
                    .name("calcularFrete")
                    .executor(DadosCalculoFrete.class, calculadorDeFrete::calcular)
                    .build();

            var executorDeFuncoes = new FunctionExecutor(Arrays.asList(funcaoCalcularFrete));
            var functionCall = new ChatFunctionCall(funcao.getName(), new ObjectMapper().readTree(funcao.getArguments()));
            return executorDeFuncoes.execute(functionCall).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> carregarHistoricoDeMensagens() {
        ArrayList<String> mensagens = new ArrayList<>();

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

    public void limparHistorico() {
        if (Objects.nonNull(threadId)) {
            service.deleteThread(threadId);
            this.threadId = null;
        }
    }
}
