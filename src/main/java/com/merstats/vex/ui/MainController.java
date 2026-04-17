package com.merstats.vex.ui;

import com.merstats.vex.model.SeasonRanking;
import com.merstats.vex.service.RobotEventsService;
import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.VexTeam;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainController {

    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar, contentCard, leaderboardCard, eventCard, aboutCard, settingsCard, placeholderCard, eloOverlay;
    @FXML private Label btnDashboard, btnSkills, btnH2H, btnAwards, btnEvents, btnLeaderboards, btnAbout, btnSettings;
    @FXML private Label placeholderTitle, dashboardTitle, dashboardSubtitle, leaderboardTitle;
    @FXML private Button btnCloseOverlay, btnGithub, searchButton, btnLoadLeaderboard, btnHowItWorks, eventSearchButton, btnThemeToggle;
    @FXML private ImageView mainLogo;
    @FXML private TextField teamInput, lbSearchInput, eventInput;
    @FXML private ProgressBar loadingBar, lbLoadingBar, eventLoadingBar;
    @FXML private ComboBox<String> seasonDropdown;

    @FXML private TableView<SkillsRanking> skillsTable;
    @FXML private TableColumn<SkillsRanking, String> seasonCol, eventCol, typeCol;
    @FXML private TableColumn<SkillsRanking, Integer> attemptCol, scoreCol, dashRankCol;

    @FXML private TableView<SeasonRanking> leaderboardTable;
    @FXML private TableColumn<SeasonRanking, Integer> lbRankCol;
    @FXML private TableColumn<SeasonRanking, String> lbTeamCol, lbRecordCol;
    @FXML private TableColumn<SeasonRanking, Double> lbTrueRankCol, lbOprCol;

    @FXML private TableView<SeasonRanking> eventTable;
    @FXML private TableColumn<SeasonRanking, Integer> evRankCol;
    @FXML private TableColumn<SeasonRanking, String> evTeamCol, evRecordCol;
    @FXML private TableColumn<SeasonRanking, Double> evTrueRankCol, evOprCol;

    private Label dashboardPlaceholder, leaderboardPlaceholder;
    private final RobotEventsService apiService = new RobotEventsService();
    private final List<String> recentSearches = new ArrayList<>();
    private RotateTransition logoRotation;
    private final ObservableList<SeasonRanking> masterLeaderboardData = FXCollections.observableArrayList();
    private final java.util.Map<String, Integer> seasonMap = new java.util.LinkedHashMap<>();
    private boolean isDarkMode = false;

    @FXML
    public void initialize() {
        setupDashboardTable();
        setupLeaderboardTable();
        setupEventTable();

        seasonMap.put("Push Back (25-26)", 204);
        seasonMap.put("High Stakes (24-25)", 190);
        seasonMap.put("Over Under (23-24)", 181);
        seasonMap.put("Spin Up (22-23)", 173);
        seasonMap.put("Tipping Point (21-22)", 154);
        seasonMap.put("Change Up (20-21)", 139);
        seasonMap.put("Tower Takeover (19-20)", 130);
        seasonMap.put("Turning Point (18-19)", 125);
        seasonMap.put("In the Zone (17-18)", 119);
        seasonMap.put("Starstruck (16-17)", 115);
        seasonMap.put("Nothing But Net (15-16)", 110);
        seasonMap.put("Skyrise (14-15)", 102);
        seasonMap.put("Toss Up (13-14)", 92);
        seasonMap.put("Sack Attack (12-13)", 85);
        seasonMap.put("Gateway (11-12)", 73);

        seasonDropdown.getItems().addAll(seasonMap.keySet());
        seasonDropdown.getSelectionModel().selectFirst();
        seasonDropdown.setOnAction(e -> loadLeaderboardData());

        setupSearchHistoryPopup();
        searchButton.setOnAction(event -> handleSearch());
        btnLoadLeaderboard.setOnAction(event -> loadLeaderboardData());
        eventSearchButton.setOnAction(event -> handleEventSearch());

        btnHowItWorks.setOnAction(event -> showEloExplanation());
        btnCloseOverlay.setOnAction(event -> hideEloExplanation());
        btnThemeToggle.setOnAction(e -> toggleTheme());

        setupNavigationRouter();
        setupSocialLinks();
        playEntranceAnimation();
    }

    private void setupDashboardTable() {
        seasonCol.setCellValueFactory(new PropertyValueFactory<>("seasonName"));
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("formattedType"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        dashRankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        dashRankCol.setCellFactory(column -> new TableCell<SkillsRanking, Integer>() {
            @Override
            protected void updateItem(Integer rank, boolean empty) {
                super.updateItem(rank, empty);
                if (empty || rank == null || rank == 0) {
                    setText(null); setStyle("");
                } else {
                    if (rank == 1) {
                        setText("🏆 " + rank);
                        setStyle("-fx-text-fill: #FFD700; -fx-font-weight: bold; -fx-alignment: center;");
                    } else if (rank <= 50) {
                        setText("🔥 " + rank);
                        setStyle("-fx-text-fill: #00E676; -fx-font-weight: bold; -fx-alignment: center;");
                    } else {
                        setText(String.valueOf(rank));
                        setStyle("-fx-alignment: center;");
                    }
                }
            }
        });

        dashboardPlaceholder = new Label("Ready to query. Enter a Team ID above.");
        dashboardPlaceholder.getStyleClass().add("placeholder-label");
        skillsTable.setPlaceholder(dashboardPlaceholder);
    }

    private void setupLeaderboardTable() {
        lbRankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        lbTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamDisplay"));
        lbRecordCol.setCellValueFactory(new PropertyValueFactory<>("record"));
        lbTrueRankCol.setCellValueFactory(new PropertyValueFactory<>("eloScore"));
        lbOprCol.setCellValueFactory(new PropertyValueFactory<>("opr"));

        lbTeamCol.setPrefWidth(220.0);
        leaderboardTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        leaderboardPlaceholder = new Label("Select a season and sync with MerStats Cloud.");
        leaderboardPlaceholder.getStyleClass().add("placeholder-label");
        leaderboardTable.setPlaceholder(leaderboardPlaceholder);

        lbOprCol.setCellFactory(column -> new TableCell<SeasonRanking, Double>() {
            @Override
            protected void updateItem(Double score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) setText(null);
                else setText(String.format("%.1f", score));
            }
        });

        lbTrueRankCol.setCellFactory(column -> new TableCell<SeasonRanking, Double>() {
            @Override
            protected void updateItem(Double mmr, boolean empty) {
                super.updateItem(mmr, empty);
                SeasonRanking teamData = getTableRow() != null ? getTableRow().getItem() : null;

                if (empty || mmr == null || teamData == null) {
                    setText(null);
                    getStyleClass().removeAll("tier-dome", "tier-titanium", "tier-carbon", "tier-aluminum", "tier-steel");
                } else {
                    getStyleClass().removeAll("tier-dome", "tier-titanium", "tier-carbon", "tier-aluminum", "tier-steel");

                    int globalRank = teamData.getRank();
                    int totalTeams = getTableView().getItems().size();
                    int domeThreshold = (int) Math.ceil(totalTeams * 0.005);

                    if (totalTeams > 0 && globalRank <= Math.max(1, domeThreshold)) {
                        getStyleClass().add("tier-dome");
                        setText("⭐ The Dome (" + mmr + ")");
                    } else if (mmr >= 1900.0) {
                        getStyleClass().add("tier-titanium");
                        setText("💠 Titanium (" + mmr + ")");
                    } else if (mmr >= 1700.0) {
                        getStyleClass().add("tier-carbon");
                        setText("⚡ Carbon Fiber (" + mmr + ")");
                    } else if (mmr >= 1500.0) {
                        getStyleClass().add("tier-aluminum");
                        setText("🔧 Aluminum (" + mmr + ")");
                    } else {
                        getStyleClass().add("tier-steel");
                        setText("🔩 Steel (" + mmr + ")");
                    }
                }
            }
        });

        FilteredList<SeasonRanking> filteredData = new FilteredList<>(masterLeaderboardData, b -> true);
        lbSearchInput.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(ranking -> {
                if (newValue == null || newValue.isEmpty()) return true;
                return ranking.getTeamDisplay().toLowerCase().contains(newValue.toLowerCase());
            });
        });

        SortedList<SeasonRanking> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(leaderboardTable.comparatorProperty());
        leaderboardTable.setItems(sortedData);
    }

    private void setupEventTable() {
        evRankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        evTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamDisplay"));
        evRecordCol.setCellValueFactory(new PropertyValueFactory<>("record"));
        evTrueRankCol.setCellValueFactory(new PropertyValueFactory<>("eloScore"));
        evOprCol.setCellValueFactory(new PropertyValueFactory<>("opr"));
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        evOprCol.setCellFactory(column -> new TableCell<SeasonRanking, Double>() {
            @Override
            protected void updateItem(Double score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) setText(null);
                else setText(String.format("%.1f", score));
            }
        });

        evTrueRankCol.setCellFactory(column -> new TableCell<SeasonRanking, Double>() {
            @Override
            protected void updateItem(Double score, boolean empty) {
                super.updateItem(score, empty);
                SeasonRanking teamData = getTableRow() != null ? getTableRow().getItem() : null;

                if (empty || score == null || teamData == null) {
                    setText(null);
                    getStyleClass().removeAll("tier-dome", "tier-titanium", "tier-carbon", "tier-aluminum", "tier-steel");
                } else {
                    getStyleClass().removeAll("tier-dome", "tier-titanium", "tier-carbon", "tier-aluminum", "tier-steel");
                    int eventRank = teamData.getRank();

                    if (eventRank == 1) {
                        getStyleClass().add("tier-dome");
                        setText(String.format("⭐ Event Champion (%.1f)", score));
                    } else if (score >= 1600.0) {
                        getStyleClass().add("tier-titanium");
                        setText(String.format("💠 Titanium (%.1f)", score));
                    } else if (score >= 1550.0) {
                        getStyleClass().add("tier-carbon");
                        setText(String.format("⚡ Carbon Fiber (%.1f)", score));
                    } else if (score >= 1500.0) {
                        getStyleClass().add("tier-aluminum");
                        setText(String.format("🔧 Aluminum (%.1f)", score));
                    } else {
                        getStyleClass().add("tier-steel");
                        setText(String.format("🔩 Steel (%.1f)", score));
                    }
                }
            }
        });
    }

    private void setupSocialLinks() {
        btnGithub.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("https://github.com/Meeerrr"));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    private void setupNavigationRouter() {
        btnDashboard.setOnMouseClicked(e -> switchView(contentCard, btnDashboard, ""));
        btnSkills.setOnMouseClicked(e -> switchView(placeholderCard, btnSkills, "Skills Analytics"));
        btnH2H.setOnMouseClicked(e -> switchView(placeholderCard, btnH2H, "Head-to-Head Simulator"));
        btnAwards.setOnMouseClicked(e -> switchView(placeholderCard, btnAwards, "Awards Archive"));
        btnEvents.setOnMouseClicked(e -> switchView(eventCard, btnEvents, "Event Statistics"));
        btnLeaderboards.setOnMouseClicked(e -> switchView(leaderboardCard, btnLeaderboards, ""));
        btnAbout.setOnMouseClicked(e -> switchView(aboutCard, btnAbout, ""));
        btnSettings.setOnMouseClicked(e -> switchView(settingsCard, btnSettings, ""));
    }

    private void switchView(VBox targetCard, Label activeNavLabel, String pageTitle) {
        if (activeNavLabel.getStyleClass().contains("nav-link-active")) return;
        VBox[] allCards = {contentCard, leaderboardCard, eventCard, aboutCard, settingsCard, placeholderCard};
        for (VBox card : allCards) { card.setVisible(false); card.setManaged(false); }
        if (targetCard == placeholderCard) { placeholderTitle.setText(pageTitle); }
        targetCard.setVisible(true);
        targetCard.setManaged(true);
        playCardAnimation(targetCard);
        Label[] allNavs = {btnDashboard, btnSkills, btnH2H, btnAwards, btnEvents, btnLeaderboards, btnAbout, btnSettings};
        for (Label nav : allNavs) {
            nav.getStyleClass().remove("nav-link-active");
            if (!nav.getStyleClass().contains("nav-link")) nav.getStyleClass().add("nav-link");
        }
        activeNavLabel.getStyleClass().remove("nav-link");
        activeNavLabel.getStyleClass().add("nav-link-active");
    }

    private void playCardAnimation(VBox card) {
        card.setOpacity(0.0);
        card.setTranslateY(20.0);
        FadeTransition fade = new FadeTransition(Duration.millis(500), card);
        fade.setToValue(1.0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(500), card);
        slide.setToY(0.0);
        fade.play(); slide.play();
    }

    private void playEntranceAnimation() {
        sidebar.setOpacity(0.0);
        FadeTransition sidebarFade = new FadeTransition(Duration.millis(800), sidebar);
        sidebarFade.setFromValue(0.0); sidebarFade.setToValue(1.0); sidebarFade.play();
        playCardAnimation(contentCard);
    }

    private void loadLeaderboardData() {
        btnLoadLeaderboard.setDisable(true);
        btnLoadLeaderboard.setText("Connecting to Cloud...");
        lbLoadingBar.setVisible(true);

        masterLeaderboardData.clear();
        lbSearchInput.clear();
        leaderboardTitle.setText("MerStats Global TrueRank");

        CompletableFuture.supplyAsync(() -> {
            try {
                String selectedSeason = seasonDropdown.getValue();
                int seasonId = seasonMap.getOrDefault(selectedSeason, 204);
                return apiService.getGlobalLeaderboard(seasonId);
            } catch (Exception e) { e.printStackTrace(); return null; }
        }).thenAccept((List<SeasonRanking> rankingsData) -> {
            Platform.runLater(() -> {
                btnLoadLeaderboard.setDisable(false);
                btnLoadLeaderboard.setText("Refresh Leaderboard");
                lbLoadingBar.setVisible(false);

                if (rankingsData != null && !rankingsData.isEmpty()) {
                    for (int i = 0; i < rankingsData.size(); i++) rankingsData.get(i).setRank(i + 1);
                    masterLeaderboardData.setAll(rankingsData);
                    leaderboardTable.setOpacity(0);
                    FadeTransition fade = new FadeTransition(Duration.millis(400), leaderboardTable);
                    fade.setToValue(1); fade.play();
                } else {
                    leaderboardPlaceholder.setText("Error: Failed to connect to MerStats Cloud.");
                }
            });
        });
    }

    private void handleEventSearch() {
        String input = eventInput.getText().trim();
        if (input.isEmpty()) return;

        String sku = input;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("RE-[A-Za-z0-9]+-\\d{2}-\\d{4}").matcher(input);
        if (m.find()) { sku = m.group(); eventInput.setText(sku); }

        final String targetSku = sku;
        eventSearchButton.setDisable(true);
        eventSearchButton.setText("Analyzing...");
        eventLoadingBar.setVisible(true);
        eventTable.getItems().clear();

        CompletableFuture.supplyAsync(() -> {
            try { return apiService.getEventTrueRank(targetSku); }
            catch (Exception ex) { ex.printStackTrace(); return null; }
        }).thenAccept(eventRankings -> {
            Platform.runLater(() -> {
                eventSearchButton.setDisable(false);
                eventSearchButton.setText("Analyze Event");
                eventLoadingBar.setVisible(false);

                if (eventRankings != null && !eventRankings.isEmpty()) {
                    for (int i = 0; i < eventRankings.size(); i++) eventRankings.get(i).setRank(i + 1);
                    eventTable.getItems().setAll(eventRankings);
                    eventTable.setOpacity(0);
                    FadeTransition fade = new FadeTransition(Duration.millis(400), eventTable);
                    fade.setToValue(1); fade.play();
                } else {
                    eventInput.setText("Error: Event not found or no matches played.");
                }
            });
        });
    }

    private void handleSearch() {
        String teamNumber = teamInput.getText().trim();
        if (teamNumber.isEmpty()) return;

        if (!recentSearches.contains(teamNumber)) {
            recentSearches.add(0, teamNumber);
            if (recentSearches.size() > 5) recentSearches.remove(5);
        }

        searchButton.setDisable(true); searchButton.setText("Searching...");
        skillsTable.getItems().clear();
        dashboardPlaceholder.setText("Fetching telemetry for " + teamNumber + "...");
        loadingBar.setVisible(true);

        if (logoRotation == null) logoRotation = createLogoAnimation();
        logoRotation.play();
        contentCard.getStyleClass().add("logo-active-pulse");

        CompletableFuture.supplyAsync(() -> {
            try {
                VexTeam team = apiService.getTeamByNumber(teamNumber);
                if (team != null) {
                    Double globalElo = apiService.getTeamGlobalElo(team.getResolvedNumber());
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team " + team.getResolvedNumber() + " - " + team.getResolvedName());
                        dashboardSubtitle.getStyleClass().removeAll("tier-dome", "tier-titanium", "tier-carbon", "tier-aluminum", "tier-steel");
                        if (globalElo != null) {
                            String tierText = "🔩 Steel"; String styleClass = "tier-steel";
                            if (globalElo >= 2000.0) { tierText = "⭐ The Dome"; styleClass = "tier-dome"; }
                            else if (globalElo >= 1900.0) { tierText = "💠 Titanium"; styleClass = "tier-titanium"; }
                            else if (globalElo >= 1700.0) { tierText = "⚡ Carbon Fiber"; styleClass = "tier-carbon"; }
                            else if (globalElo >= 1500.0) { tierText = "🔧 Aluminum"; styleClass = "tier-aluminum"; }
                            dashboardSubtitle.setText("Grade: " + team.getGrade() + "   •   " + tierText + " (" + globalElo + ")");
                            dashboardSubtitle.getStyleClass().add(styleClass);
                        } else {
                            dashboardSubtitle.setText("Grade: " + team.getGrade() + "   •   No TrueRank Data");
                        }
                    });
                    return apiService.getSkillsByTeamId(team.getId());
                } else {
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team Not Found"); dashboardSubtitle.setText("Please verify the ID and try again.");
                        dashboardPlaceholder.setText("Error: Team '" + teamNumber + "' does not exist.");
                        resetSearchUI();
                    });
                    return null;
                }
            } catch (Exception e) {
                Platform.runLater(() -> { dashboardPlaceholder.setText("Connection Error."); resetSearchUI(); });
                return null;
            }
        }).thenAccept((List<SkillsRanking> skillsData) -> {
            Platform.runLater(() -> {
                resetSearchUI();
                if (skillsData != null && !skillsData.isEmpty()) {
                    skillsData.sort((rank1, rank2) -> {
                        int seasonCompare = Integer.compare(rank2.getSeasonId(), rank1.getSeasonId());
                        if (seasonCompare != 0) return seasonCompare;
                        int eventCompare = rank1.getEventName().compareTo(rank2.getEventName());
                        if (eventCompare != 0) return eventCompare;
                        return Integer.compare(rank2.getScore(), rank1.getScore());
                    });
                    skillsTable.getItems().setAll(skillsData);
                    skillsTable.setOpacity(0.0); skillsTable.setTranslateY(20.0);
                    FadeTransition tableFade = new FadeTransition(Duration.millis(600), skillsTable); tableFade.setToValue(1.0);
                    TranslateTransition tableSlide = new TranslateTransition(Duration.millis(600), skillsTable); tableSlide.setToY(0.0);
                    tableFade.play(); tableSlide.play();
                } else if (skillsData != null && skillsData.isEmpty()) {
                    dashboardPlaceholder.setText("No skills runs recorded for " + teamNumber + ".");
                }
            });
        });
    }

    private void setupSearchHistoryPopup() {
        ContextMenu historyMenu = new ContextMenu();
        teamInput.setOnMouseClicked(event -> {
            if (!recentSearches.isEmpty()) {
                historyMenu.getItems().clear();
                for (String search : recentSearches) {
                    MenuItem item = new MenuItem(search);
                    item.setOnAction(e -> { teamInput.setText(search); handleSearch(); });
                    historyMenu.getItems().add(item);
                }
                historyMenu.show(teamInput, javafx.geometry.Side.BOTTOM, 0, 0);
            }
        });
    }

    private void resetSearchUI() {
        loadingBar.setVisible(false);
        if (logoRotation != null) { logoRotation.stop(); mainLogo.setRotate(0); }
        contentCard.getStyleClass().remove("logo-active-pulse");
        searchButton.setDisable(false); searchButton.setText("Search");
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        if (isDarkMode) {
            rootPane.getStyleClass().add("dark-theme");
            btnThemeToggle.setText("☀️ Switch to Light Mode");
            btnThemeToggle.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-cursor: hand;");
        } else {
            rootPane.getStyleClass().remove("dark-theme");
            btnThemeToggle.setText("🌙 Switch to Dark Mode");
            btnThemeToggle.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        }
    }

    private void showEloExplanation() {
        eloOverlay.setOpacity(0.0); eloOverlay.setVisible(true); eloOverlay.setManaged(true);
        FadeTransition fade = new FadeTransition(Duration.millis(300), eloOverlay); fade.setToValue(1.0); fade.play();
    }

    private void hideEloExplanation() {
        FadeTransition fade = new FadeTransition(Duration.millis(300), eloOverlay); fade.setToValue(0.0);
        fade.setOnFinished(e -> { eloOverlay.setVisible(false); eloOverlay.setManaged(false); }); fade.play();
    }

    private RotateTransition createLogoAnimation() {
        RotateTransition rt = new RotateTransition(Duration.millis(800), mainLogo);
        rt.setByAngle(360); rt.setCycleCount(Animation.INDEFINITE); rt.setInterpolator(Interpolator.LINEAR);
        return rt;
    }
}