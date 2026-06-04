///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.github.pengrad:java-telegram-bot-api:7.1.1
//DEPS dev.langchain4j:langchain4j:0.35.0
//DEPS dev.langchain4j:langchain4j-google-ai-gemini:0.35.0
//DEPS org.slf4j:slf4j-simple:2.0.12

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import com.pengrad.telegrambot.request.SendMessage;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.Base64;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import dev.langchain4j.data.message.SystemMessage;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import com.pengrad.telegrambot.request.SendPhoto;

public class Chatfoto {

    private final ChatMemory chatMemory;
    private final TelegramBot bot;
    private final GoogleAiGeminiChatModel model;
    private final List<ToolSpecification> toolSpecs;
    private final Map<Long, byte[]> lastPhotos = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> currentChatId = new ThreadLocal<>();

    // Flag para habilitar/desabilitar os logs
    private boolean showLog = true;

    private void log(String message) {
        if (showLog) {
            System.out.println("[LOG] " + message);
        }
    }

    public Chatfoto(String botToken, String geminiKey) {
        this.bot = new TelegramBot(botToken);
        this.model = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiKey)
                .modelName("gemini-3.5-flash")
                .temperature(0.2) // Deixa o modelo menos "criativo" e mais factual (0.0 a 1.0)
                .timeout(java.time.Duration.ofSeconds(60)) // Evita o erro de SocketTimeoutException
                .build();

        this.chatMemory = MessageWindowChatMemory
                .builder()
                .maxMessages(10)
                .build();

        this.toolSpecs = ToolSpecifications.toolSpecificationsFrom(this);

        // Adiciona a instrução inicial na memória
        this.chatMemory.add(SystemMessage.from(
                "Você é um assistente prestativo e bem-humorado rodando dentro de um bot do Telegram.\n" +
                        "Sua principal função é descrever, criar e editar fotos.\n" +
                        "Suas respostas devem ser concisas e amigáveis.\n" +
                        "Sempre que possível, use emojis para tornar a conversa mais leve."));
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            updates.forEach(this::processUpdate);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        log("Bot iniciado com sucesso!");
    }

    private void processUpdate(Update update) {
        if (update.message() == null)
            return;

        var chatId = update.message().chat().id();

        if (update.message().photo() != null && update.message().photo().length > 0) {
            log("Recebendo nova foto do chat " + chatId);
            onPhotoReceived(bot, update);
            return;
        }

        var text = update.message().text();
        if (text != null && !text.isEmpty()) {
            log("Recebendo nova mensagem de texto do chat " + chatId + ": " + text);
            this.chatMemory.add(UserMessage.from(text));

            executeGenerateAndSend(chatId);
        }
    }

    private void executeGenerateAndSend(long chatId) {
        currentChatId.set(chatId);
        try {
            dev.langchain4j.model.output.Response<AiMessage> response = model.generate(chatMemory.messages(), toolSpecs);
            AiMessage aiMessage = response.content();
            this.chatMemory.add(aiMessage);

            String initialText = aiMessage.text();
            if (initialText != null && !initialText.isEmpty()) {
                bot.execute(new SendMessage(chatId, initialText));
                log("Enviando resposta de texto para o chat " + chatId);
            }

            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolReq : aiMessage.toolExecutionRequests()) {
                    if ("transformPhotoToBlackAndWhite".equals(toolReq.name())) {
                        log("Executando ferramenta: transformPhotoToBlackAndWhite");
                        String result = transformPhotoToBlackAndWhite();
                        this.chatMemory.add(ToolExecutionResultMessage.from(toolReq, result));
                    } else if ("generateImage".equals(toolReq.name())) {
                        log("Executando ferramenta: generateImage");
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> argsMap = dev.langchain4j.internal.Json.fromJson(toolReq.arguments(), java.util.Map.class);
                            String prompt = (String) argsMap.values().iterator().next();
                            String result = generateImage(prompt);
                            this.chatMemory.add(ToolExecutionResultMessage.from(toolReq, result));
                        } catch (Exception e) {
                            this.chatMemory.add(ToolExecutionResultMessage.from(toolReq, "Erro ao extrair prompt da ferramenta: " + e.getMessage()));
                        }
                    } else {
                        this.chatMemory.add(ToolExecutionResultMessage.from(toolReq, "Ferramenta não implementada."));
                    }
                }
                response = model.generate(chatMemory.messages(), toolSpecs);
                aiMessage = response.content();
                this.chatMemory.add(aiMessage);

                String finalText = aiMessage.text();
                if (finalText != null && !finalText.isEmpty()) {
                    bot.execute(new SendMessage(chatId, finalText));
                    log("Enviando resposta final para o chat " + chatId);
                }
            }
        } catch (Exception e) {
            log("Erro ao processar mensagem com modelo: " + e.getMessage());
            e.printStackTrace();
            bot.execute(new SendMessage(chatId, "Ops! 😵‍💫 Tive um erro: " + e.getMessage()));
        }
    }

    @Tool("Transforma a última foto recebida em preto e branco. Use esta ferramenta quando o usuário pedir para transformar a foto em preto e branco.")
    public String transformPhotoToBlackAndWhite() {
        Long chatId = currentChatId.get();
        if (chatId == null) return "Erro: Chat ID não encontrado.";
        
        byte[] photoBytes = lastPhotos.get(chatId);
        if (photoBytes == null) return "Nenhuma foto foi enviada ainda para transformar.";
        
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(photoBytes));
            if (img == null) return "Erro ao ler a imagem.";
            BufferedImage bwImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = bwImg.createGraphics();
            graphics.drawImage(img, 0, 0, null);
            graphics.dispose();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bwImg, "jpg", baos);
            
            bot.execute(new SendPhoto(chatId, baos.toByteArray()));
            simulatePayment(chatId);
            return "Foto transformada e enviada com sucesso!";
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro ao transformar a foto: " + e.getMessage();
        }
    }

    private void simulatePayment(long chatId) {
        String randomPixKey = java.util.UUID.randomUUID().toString();
        String paymentMessage = "Pagamento simulado! Para apoiar nosso projeto, faça um PIX para a seguinte chave aleatória:\n" + randomPixKey;
        bot.execute(new SendMessage(chatId, paymentMessage));
        log("Mensagem de cobrança PIX enviada para o chat " + chatId);
    }

    @Tool("Gera uma imagem do zero a partir de uma descrição de texto. Use esta ferramenta quando o usuário pedir para gerar, criar ou desenhar uma imagem.")
    public String generateImage(String prompt) {
        Long chatId = currentChatId.get();
        if (chatId == null) return "Erro: Chat ID não encontrado.";
        
        try {
            // Usando Pollinations.ai como alternativa gratuita e direta para geração de imagens
            String encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8");
            String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt + "?width=1024&height=1024&nologo=true";
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                bot.execute(new SendPhoto(chatId, response.body()));
                simulatePayment(chatId);
                return "Imagem gerada e enviada com sucesso para o prompt: " + prompt;
            } else {
                return "Erro da API de geração de imagem. Status: " + response.statusCode();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro interno ao gerar a imagem: " + e.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String geminiKey = System.getenv("GEMINI_API_KEY");

        if (args.length >= 2) {
            botToken = args[0];
            geminiKey = args[1];
        }

        if (botToken == null || geminiKey == null) {
            System.err.println("Erro: TELEGRAM_BOT_TOKEN e GEMINI_API_KEY devem ser configurados.");
            System.err.println("Uso: jbang Chatfoto.java <BOT_TOKEN> <GEMINI_KEY>");
            System.exit(1);
        }

        new Chatfoto(botToken, geminiKey).start();

        // Mantém a aplicação rodando
        Thread.currentThread().join();
    }

    public void onPhotoReceived(TelegramBot bot, Update update) {
        var chatId = update.message().chat().id();
        try {
            // 1. Obtém o ID da maior foto disponível
            PhotoSize[] photos = update.message().photo();
            String fileId = photos[photos.length - 1].fileId();

            // 2. Baixa os bytes da foto do Telegram
            byte[] photoBytes = downloadPhotoFromTelegram(bot, fileId);

            if (photoBytes == null) {
                bot.execute(new SendMessage(chatId, "Desculpe, não consegui baixar a foto. 😢"));
                log("Falha ao baixar foto. Enviando mensagem de erro para o chat " + chatId);
                return;
            }
            
            lastPhotos.put(chatId, photoBytes);

            // 3. Converte para Base64
            String base64Image = Base64.getEncoder().encodeToString(photoBytes);

            // 4. Cria a mensagem para o modelo (texto + imagem)
            String caption = update.message().caption();
            String promptText = (caption != null && !caption.isEmpty()) ? caption
                    : "Descreva detalhadamente o que você vê nesta imagem.";

            UserMessage userMessage = UserMessage.from(
                    TextContent.from(promptText),
                    ImageContent.from(base64Image, "image/jpeg"));

            // Adiciona a imagem na memória primeiro
            this.chatMemory.add(userMessage);

            // 5. Envia para o modelo processar todo o contexto (incluindo a imagem)
            log("Enviando imagem para análise do modelo Gemini...");
            executeGenerateAndSend(chatId);
        } catch (Exception e) {
            log("Erro ao processar foto: " + e.getMessage());
            e.printStackTrace();
            bot.execute(new SendMessage(chatId, "Ops! 😵‍💫 Tive um erro: " + e.getMessage()));
            log("Enviando mensagem de erro para o chat " + chatId);
        }
    }

    // Método auxiliar para baixar a foto
    private byte[] downloadPhotoFromTelegram(TelegramBot bot, String fileId) {
        try {
            GetFileResponse getFileResponse = bot.execute(new GetFile(fileId));
            if (!getFileResponse.isOk()) {
                log("Erro ao obter arquivo: " + getFileResponse.description());
                return null;
            }
            com.pengrad.telegrambot.model.File file = getFileResponse.file();
            String fullPath = bot.getFullFilePath(file);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullPath))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log("Erro ao baixar a foto. Status code: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log("Erro ao baixar arquivo do telegram: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

}