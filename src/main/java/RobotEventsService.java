import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RobotEventsService {

    //Robot events API key
    private static final String API_KEY="Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIzIiwianRpIjoiNTE0MjRkZDAyMDQ1MzA1MGVlZGYzZjM2OGY4ODQ3NDFhZGMwOTZjMjExMTA1OWRiYWE0MDFiMDJjY2VhZGE1ZGE0OTc4ZmY1ZDhlYjNkNzciLCJpYXQiOjE3NzUxNDUzMzAuNTI1OTI5LCJuYmYiOjE3NzUxNDUzMzAuNTI1OTMwOSwiZXhwIjoyNzIxOTE2NTMwLjUyMDU3NzksInN1YiI6IjEyNjM3MyIsInNjb3BlcyI6W119.hZuGrp7w4nO6m8NOgm3iKsFZ5l85PV8W5yhP9mgnpgXuJCL71Tg9IWR4xTIdD1uVVEKAZGPhuwRybTGHO2jp171zVZFU-U4K7w0m_wj1xzqu8-yIHW6uzhxypMAF6Nixc__vT0gKcEuLZE_WECxRjd3PQTtZ7uOT8bu81bXpCWSSd-GtItSaGpZ10CeQ42VV0aFzNm2uMePINW2N-gMJxjbrKEIqcloNw8R8K_xdrpL8O8VwQJoQFPMmq24fLSmcsWX8l4aoDqwHWsKx19JNj80h6lwge5WdGcWxmYU1b1crESb8Od69GV78QwnM0QqgIu7KRbSqiBeOVE4GyxxIVflnQPoiRSRKz1k5I1dLEzoBwavG-LX2QZjtZZTL5gFLOc_rqJLb6Y4rANZvJBpRfFUJ0MNRn0Ert7Up5ahDZqkU3_67_CQAomZITP8MVK7O__btFScefR4m9mffete2ad2MSbY3PiN9sFFp3dFhYGnC9MJKCvYn-6jzll-4oJpzj41QvKLe8Dwpkqz3DxRV8v9dgQcgifcEUi8yix1V8_YtX-VlPiH3TKdDm6gMMQewxK-25KiajV7wSm2ZLfj_KW2CLc5qyr09egDWdhAJhn_hrkKqlaCjP6CFM998HIIkwPzW81bh3SmqRgzvobg7fgpRrA2CuvU96ZIWDplS8Mg";

    // We store the base URL here so we don't have to type it out every time
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";
    public void testConnection() {
        try {
            // Create the Client
            HttpClient client = HttpClient.newHttpClient();

            //try on team 254A
            String url = BASE_URL + "/teams?number%5B%5D=254A";

            //Build the Request (The Letter)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", API_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            //Send the Request and get the Response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // check status
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Raw JSON Data:");
            System.out.println(response.body());
        }catch (Exception e) {
            // If the internet is down, or the URL is malformed, it drops down here safely
            System.out.println("Something went wrong with the connection!");
            e.printStackTrace();
        }
    }
    //temporary main method
    public static void main(String[] args) {
        RobotEventsService service = new RobotEventsService();
        System.out.println("Starting network test...");
        service.testConnection();
    }

}
