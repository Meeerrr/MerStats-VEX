package com.merstats.vex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.SkillsResponse;
import com.merstats.vex.model.TeamResponse;
import com.merstats.vex.model.VexTeam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class RobotEventsService {

    //API key
    private static final String API_KEY = "Bearer API_KEY";
    private static final String BASE_URL = "https://www.robotevents.com/api/v2";

    //Declare the client and mapper
    private final HttpClient client;
    private final ObjectMapper mapper;

    //construct
    public RobotEventsService() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    /**
     * Searches the API for a team by their number.
     * @param teamNumber The team number (e.g., "254A")
     * @return A VexTeam object, or null if the team is not found.
     * @throws Exception if the network fails or JSON parsing breaks.
     */
    public VexTeam getTeamByNumber(String teamNumber) throws Exception {

        // Construct the dynamic URL
        String url = BASE_URL + "/teams?number%5B%5D=" + teamNumber;

        // Build the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        // Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Check if the API request was successful
        if (response.statusCode() == 200) {

            // Translate the raw JSON string into our TeamResponse wrapper class
            TeamResponse teamResponse = mapper.readValue(response.body(), TeamResponse.class);
            List<VexTeam> teams = teamResponse.getData();

            // The API might return multiple teams with the same number
            // Although we only fetch the first one
            if (teams != null && !teams.isEmpty()) {
                return teams.get(0);
            }
        } else {
            // Print the error code if the server rejects us
            System.err.println("API Error: Received HTTP " + response.statusCode());
        }

        // Return null if the team was not found or if there was an error
        return null;
    }

    /**
     * Searches the API for a team's skills rankings using their internal ID.
     * @param teamId The internal database ID of the team (e.g., 181)
     * @return A List of SkillRanking objects.
     * @throws Exception if the network fails or JSON parsing breaks.
     */
    public List<SkillsRanking> getSkillsByTeamId(int teamId) throws Exception {


        // Correct path format
        String url = BASE_URL + "/teams/" + teamId + "/skills?per_page=250";

        // Build the exact same type of authorized request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", API_KEY)
                .header("Accept", "application/json")
                .GET()
                .build();

        // Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Translate the JSON into the Skills wrapper class
            SkillsResponse skillsResponse = mapper.readValue(response.body(), SkillsResponse.class);

            // Return the list of rankings
            return skillsResponse.getData();
        } else {
            System.err.println("Skills API Error: Received HTTP " + response.statusCode());
            return null;
        }
    }

    // Temporary main method
    public static void main(String[] args) {
        RobotEventsService service = new RobotEventsService();
        System.out.println("Starting API Chain test...");

        try {
            // CALL 1: Get the Team Object
            System.out.println("Fetching team details for 11017Y...");
            VexTeam fetchedTeam = service.getTeamByNumber("11017Y");

            if (fetchedTeam != null) {
                System.out.println("Success! Found " + fetchedTeam.getTeam_name() + " (ID: " + fetchedTeam.getId() + ")");

                // CALL 2: Use the newly discovered ID to get the Skills
                System.out.println("Fetching skills data for ID " + fetchedTeam.getId() + "...");
                List<SkillsRanking> rankings = service.getSkillsByTeamId(fetchedTeam.getId());

                if (rankings != null && !rankings.isEmpty()) {
                    System.out.println("\n--- SKILLS RANKINGS FOUND ---");
                    // Loop through the list and print out the attempts
                    for (SkillsRanking rank : rankings) {
                        System.out.println("Type: " + rank.getType() + " | Score: " + rank.getScore() + " | Global Rank: " + rank.getRank());
                    }
                } else {
                    System.out.println("No skills data found for this team.");
                }

            } else {
                System.out.println("Team not found.");
            }

        } catch (Exception e) {
            System.err.println("A network or parsing exception occurred!");
            e.printStackTrace();
        }
    }
}