import math

def calculate_truerank(teams, matches):
    """
    The Core Math Engine.
    Takes in a list of unique teams and a list of matches, runs a 3-pass
    Elo simulation, and returns the final global leaderboards.
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

    # 2. The 3-Pass System (Simulated Annealing)
    # Pass 1: Aggressive sorting (K=32)
    # Pass 2: Stabilization (K=24)
    # Pass 3: Micro-adjustments and Record Keeping (K=16)
    k_factors = [32.0, 24.0, 16.0]

    for pass_num, base_k in enumerate(k_factors):
        is_final_pass = (pass_num == 2) # We only track Wins/Losses on the last pass

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

            # Determine actual outcome & Log W-L-T on final pass
            if red_score > blue_score:
                red_actual = 1.0
                blue_actual = 0.0
                if is_final_pass:
                    for t in red_teams: team_data[t]["wins"] += 1
                    for t in blue_teams: team_data[t]["losses"] += 1
            elif blue_score > red_score:
                red_actual = 0.0
                blue_actual = 1.0
                if is_final_pass:
                    for t in red_teams: team_data[t]["losses"] += 1
                    for t in blue_teams: team_data[t]["wins"] += 1
            else:
                red_actual = 0.5
                blue_actual = 0.5
                if is_final_pass:
                    for t in red_teams: team_data[t]["ties"] += 1
                    for t in blue_teams: team_data[t]["ties"] += 1

            # ---------------------------------------------------------
            # 📉 1. LOGARITHMIC MOV MULTIPLIER (Nerfing Blowouts)
            # ---------------------------------------------------------
            point_diff = abs(red_score - blue_score)
            # We add +1 so a 0 point diff (tie) equals log10(1) = 0 multiplier increase
            # We use a base modifier of + 1.0 so the minimum multiplier is 1x.
            mov_multiplier = 1.0 + (math.log10(point_diff + 1) * 0.15)
            # Cap the maximum blowout reward at 1.3x to stop OPR farming
            mov_multiplier = min(mov_multiplier, 1.05)

            # ---------------------------------------------------------
            # 🔥 2. THE EVENT TIER MULTIPLIER (Strength of Schedule)
            # ---------------------------------------------------------
            event_level = match.get("level", "Local")

            if "World" in event_level:
                tier_multiplier = 2.0  # Worlds is a bloodbath. High risk, high reward.
            elif "Signature" in event_level or "National" in event_level:
                tier_multiplier = 1.5  # Heavy respect for Nationals & Signatures
            elif "State" in event_level or "Region" in event_level:
                tier_multiplier = 1.2  # State Championships
            else:
                tier_multiplier = 1.0  # Local weekend events

            # Combine the multipliers with the current pass's K-Factor
            final_k_factor = base_k * mov_multiplier * tier_multiplier

            # Calculate Elo Shift (How many points are stolen)
            red_shift = final_k_factor * (red_actual - red_expected)
            blue_shift = final_k_factor * (blue_actual - blue_expected)

            # Apply shifts to the teams
            for t in red_teams:
                team_data[t]["elo"] += red_shift
            for t in blue_teams:
                team_data[t]["elo"] += blue_shift

    # 3. Package the final data for Supabase
    print("      -> Math complete. Packaging leaderboards...")
    final_leaderboard = []

    for team_name, stats in team_data.items():
        # Only upload teams that actually played matches (prevents blank 0-0-0 profiles)
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