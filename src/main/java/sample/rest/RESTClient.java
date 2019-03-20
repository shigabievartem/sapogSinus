package sample.rest;

import com.google.gson.Gson;
import sample.objects.ButtonImpl;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

// Можно использовать при необходимости интеграции через rest сервисы
public class RESTClient {
    private static final String WS_CHECK_URI = "http://localhost:8080/greeting";
    private static final String WS_URI = "http://localhost:8080/sapogSinus";
    private final static Gson g = new Gson();

    public static boolean saveValue(Object value) throws RuntimeException {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget target = client.target(getBaseUri());
            Object response = target.path("save").request()
                    // TODO возможно подготовить свой json Entity.json
                    .post(Entity.entity(g.toJson(value), MediaType.APPLICATION_JSON), Object.class);
            System.out.println(response);
            return response == null; //---
        } finally {
            if (client != null) client.close();
        }
    }
    public static boolean checkWS() {
        boolean stateOfWS = false;
        try {
            URL siteURL = new URL(WS_CHECK_URI);
            HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            if (connection.getResponseCode() == 200) stateOfWS = true;
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return stateOfWS;
    }

    private static URI getBaseUri() {
        return UriBuilder.fromUri(WS_URI).build();
    }
}
