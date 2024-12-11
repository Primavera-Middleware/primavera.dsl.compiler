package primavera.app.client;

import java.io.IOException;
import java.net.URISyntaxException;
import br.ufrn.imd.primavera.remoting.handlers.client.ClientRequestHandler;
import br.ufrn.imd.primavera.remoting.handlers.client.Request;
import br.ufrn.imd.primavera.remoting.handlers.client.Response;

public class ApiServiceClient {

    private final String baseUrl;
    private final ClientRequestHandler clientRequestHandler;

    public ApiServiceClient() {
        this.baseUrl = "http://localhost:8080";
        this.clientRequestHandler = new ClientRequestHandler();
    }

    public static class User {
        public String name;
        public String address;
    }

    public Response createUser(String name, String address) throws IOException, InterruptedException, URISyntaxException {
        String path = "/user/create";
        Request request = new Request("POST", baseUrl, path);
        request.addHeader("Content-Type", "application/json");
        request.setBody("{\"name\": \"" + name + "\", \"address\": \"" + address + "\"}");
        Response response = clientRequestHandler.sendRequest(request);
        if (response.getStatusCode() == 200 || response.getStatusCode() == 201) {
            return response;
        } else {
            throw new RuntimeException("Falha ao chamar createUser: Codigo HTTP " + response.getStatusCode());
        }
    }

    public Response getUser(String id) throws IOException, InterruptedException, URISyntaxException {
        String path = "/user/" + id + "";
        Request request = new Request("GET", baseUrl, path);
        Response response = clientRequestHandler.sendRequest(request);
        if (response.getStatusCode() == 200 || response.getStatusCode() == 201) {
            return response;
        } else {
            throw new RuntimeException("Falha ao chamar getUser: Codigo HTTP " + response.getStatusCode());
        }
    }

}
