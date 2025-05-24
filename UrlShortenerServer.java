import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UrlShortenerServer {
    private static final int PORT = 80;
    private static final String BASE_URL = "http://localhost:80/";
    private static final Map<String, String> urlMap = new ConcurrentHashMap<>();
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_URL_LENGTH = 8;
    private static final Random random = new Random();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.execute(new ClientHandler(clientSocket));
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                if (requestLine == null) {
                    return;
                }

                String[] requestParts = requestLine.split(" ");
                if (requestParts.length < 2) {
                    sendResponse(out, 400, "Bad Request", "Invalid request line", "*");
                    return;
                }

                String method = requestParts[0];
                String path = requestParts[1];

                // Read headers
                Map<String, String> headers = new HashMap<>();
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    int separator = line.indexOf(':');
                    if (separator > 0) {
                        String headerName = line.substring(0, separator).trim();
                        String headerValue = line.substring(separator + 1).trim();
                        headers.put(headerName, headerValue);
                    }
                }

                // Handle OPTIONS request for CORS preflight
                if (method.equals("OPTIONS")) {
                    handleCorsPreflight(out, headers);
                    return;
                }

                // Add CORS headers to all responses
                String origin = headers.getOrDefault("Origin", "*");
                
                // Handle POST request for URL shortening
                if (method.equals("POST") && path.equals("/url-shortener/shorten")) {
                    int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
                    
                    if (contentLength <= 0) {
                        sendResponse(out, 400, "Bad Request", "Content-Length required", origin);
                        return;
                    }

                    // Read the JSON body
                    StringBuilder body = new StringBuilder();
                    int charsRead;
                    int totalCharsRead = 0;
                    char[] buffer = new char[1024];
                    
                    while (totalCharsRead < contentLength) {
                        int remaining = contentLength - totalCharsRead;
                        int toRead = Math.min(buffer.length, remaining);
                        charsRead = in.read(buffer, 0, toRead);
                        if (charsRead == -1) {
                            break;
                        }
                        body.append(buffer, 0, charsRead);
                        totalCharsRead += charsRead;
                    }

                    String jsonBody = body.toString();
                    String originalUrl = extractUrlFromJson(jsonBody);

                    if (originalUrl == null || originalUrl.isEmpty()) {
                        sendResponse(out, 400, "Bad Request", "Invalid or empty URL", origin);
                        return;
                    }

                    // Add http:// if not present
                    if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
                        originalUrl = "http://" + originalUrl;
                    }

                    // Check if URL already exists
                    String existingShortCode = null;
                    for (Map.Entry<String, String> entry : urlMap.entrySet()) {
                        if (entry.getValue().equals(originalUrl)) {
                            existingShortCode = entry.getKey();
                            break;
                        }
                    }

                    String shortCode;
                    if (existingShortCode != null) {
                        shortCode = existingShortCode;
                    } else {
                        // Generate a unique short code
                        do {
                            shortCode = generateShortCode();
                        } while (urlMap.containsKey(shortCode));

                        // Store the mapping
                        urlMap.put(shortCode, originalUrl);
                    }

                    sendResponse(out, 200, "OK", BASE_URL + shortCode, origin);
                }
                // Handle GET request for redirection
                else if (method.equals("GET") && path.startsWith("/")) {
                    String shortCode = path.substring(1);
                    String originalUrl = urlMap.get(shortCode);

                    if (originalUrl != null) {
                        sendRedirectResponse(out, originalUrl, origin);
                    } else {
                        sendResponse(out, 404, "Not Found", "Short URL not found", origin);
                    }
                } else {
                    sendResponse(out, 404, "Not Found", "Endpoint not found", origin);
                }
            } catch (Exception e) {
                System.err.println("Error handling client request: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private String extractUrlFromJson(String json) {
            try {
                // Simple JSON parsing - in production use a proper JSON library
                int urlStart = json.indexOf("\"url\":\"");
                if (urlStart == -1) return null;
                urlStart += 7;
                int urlEnd = json.indexOf("\"", urlStart);
                if (urlEnd == -1) return null;
                return json.substring(urlStart, urlEnd);
            } catch (Exception e) {
                return null;
            }
        }

        private void handleCorsPreflight(PrintWriter out, Map<String, String> headers) {
            String origin = headers.getOrDefault("Origin", "*");
            out.println("HTTP/1.1 204 No Content");
            out.println("Access-Control-Allow-Origin: " + origin);
            out.println("Access-Control-Allow-Methods: POST, GET, OPTIONS");
            out.println("Access-Control-Allow-Headers: Content-Type");
            out.println("Access-Control-Max-Age: 86400");
            out.println();
        }

        private void sendResponse(PrintWriter out, int statusCode, String statusText, String body, String origin) {
            out.println("HTTP/1.1 " + statusCode + " " + statusText);
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + body.length());
            out.println("Access-Control-Allow-Origin: " + origin);
            out.println();
            out.println(body);
        }

        private void sendRedirectResponse(PrintWriter out, String location, String origin) {
            out.println("HTTP/1.1 302 Found");
            out.println("Location: " + location);
            out.println("Access-Control-Allow-Origin: " + origin);
            out.println();
        }
    }

    private static String generateShortCode() {
        StringBuilder shortCode = new StringBuilder();
        for (int i = 0; i < SHORT_URL_LENGTH; i++) {
            shortCode.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return shortCode.toString();
    }
}