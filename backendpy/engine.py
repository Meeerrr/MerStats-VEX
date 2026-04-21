import math

def calculate_truerank(teams, matches):
    """
    The Core Math Engine (Single-Pass Chronological).
    Takes in a list of unique teams and a list of matches, runs a strict
    forward-moving Elo simulation, and returns the final global leaderboards.
    """
    print("      -> Initializing TrueRank Math Engine...")

    # 1. Initialize the global roster (Everyone starts at 1500)
    team_data = {}
    for t in teams:
        team_name = t["name"]
        team_data[team_name] = {
            "elo": 1500.0,
            "wins": 0,
            "losses": 0,
            "ties": 0
        }

    # 2. The Single-Pass Chronological Simulation
    # We use a static K-Factor of 32.0 so the system is highly responsive to upsets
    base_k = 32.0

    for match in matches:
        # Safely extract alliances
        red_alliance = match["alliances"][0] if match["alliances"][0]["color"] == "red" else match["alliances"][1]
        blue_alliance = match["alliances"][0] if match["alliances"][0]["color"] == "blue" else match["alliances"][1]

        red_score = red_alliance["score"]
        blue_score = blue_alliance["score"]

        # Extract team names
        red_teams = [t["team"]["name"] for t in red_alliance["teams"]]
        blue_teams = [t["team"]["name"] for t in blue_alliance["teams"]]

        # Skip matches with invalid team data
        if not red_teams or not blue_teams:
            continue

        # Calculate Alliance Averages
        red_avg_elo = sum([team_data[t]["elo"] for t in red_teams]) / len(red_teams)
        blue_avg_elo = sum([team_data[t]["elo"] for t in blue_teams]) / len(blue_teams)

        # Calculate Expected Win Probability (Standard Elo Math)
        red_expected = 1.0 / (1.0 + math.pow(10, (blue_avg_elo - red_avg_elo) / 400.0))
        blue_expected = 1.0 / (1.0 + math.pow(10, (red_avg_elo - blue_avg_elo) / 400.0))

        # Determine actual outcome & Log W-L-T
        if red_score > blue_score:
            red_actual = 1.0
            blue_actual = 0.0
            for t in red_teams: team_data[t]["wins"] += 1
            for t in blue_teams: team_data[t]["losses"] += 1
        elif blue_score > red_score:
            red_actual = 0.0
            blue_actual = 1.0
            for t in red_teams: team_data[t]["losses"] += 1
            for t in blue_teams: team_data[t]["wins"] += 1
        else:
            red_actual = 0.5
            blue_actual = 0.5
            for t in red_teams: team_data[t]["ties"] += 1
            for t in blue_teams: team_data[t]["ties"] += 1

        # ---------------------------------------------------------
        # 📉 1. THE "SMART" MOV MULTIPLIER (Rewards OPR, Nerfs Farming)
        # ---------------------------------------------------------
        point_diff = abs(red_score - blue_score)

        # We use a softer logarithmic curve (0.10 instead of 0.15).
        # Every extra point scored matters, but the value diminishes as the blowout gets bigger.
        mov_multiplier = 1.0 + (math.log10(point_diff + 1) * 0.10)

        # We raise the cap from 1.05x to 1.20x.
        # High OPR teams like 229V can now earn up to a 20% Elo bonus for a masterclass blowout,
        # restoring the mathematical value of having a massive offense!
        mov_multiplier = min(mov_multiplier, 1.20)

        # ---------------------------------------------------------
        # 🔥 2. ASYMMETRIC EVENT MULTIPLIER (The Worlds Shield)
        # ---------------------------------------------------------
        event_level = match.get("level", "Local")

        if "World" in event_level or "Championship" in event_level:
            reward_mult = 2.0   # Massive reward for winning at Worlds
            penalty_mult = 0.5  # 50% Shield: Losing at Worlds barely hurts
        elif "Signature" in event_level or "National" in event_level:
            reward_mult = 1.5
            penalty_mult = 0.75 # 25% Shield for Nationals
        elif "State" in event_level or "Region" in event_level:
            reward_mult = 1.2
            penalty_mult = 0.9
        else:
            reward_mult = 1.0
            penalty_mult = 1.0  # Local events are standard 1-to-1

        # Calculate the Base Elo Shift (K-factor * MOV * Expected Outcome)
        base_red_shift = base_k * mov_multiplier * (red_actual - red_expected)
        base_blue_shift = base_k * mov_multiplier * (blue_actual - blue_expected)

        # Apply the Asymmetric Multipliers (Rewards for wins, Shields for losses)
        red_shift = base_red_shift * (reward_mult if base_red_shift > 0 else penalty_mult)
        blue_shift = base_blue_shift * (reward_mult if base_blue_shift > 0 else penalty_mult)

        # Apply final shifts to the teams
        for t in red_teams:
            team_data[t]["elo"] += red_shift
        for t in blue_teams:
            team_data[t]["elo"] += blue_shift

    # 3. Package the final data for Supabase
    print("      -> Math complete. Packaging leaderboards...")
    final_leaderboard = []

    for team_name, stats in team_data.items():
        total_matches = stats["wins"] + stats["losses"] + stats["ties"]
        if total_matches > 0:
            final_leaderboard.append({
                "team_id": team_name,
                "elo_score": round(stats["elo"], 2),
                "wins": stats["wins"],
                "losses": stats["losses"],
                "ties": stats["ties"]
            })

    return final_leaderboard