import math

BASE_ELO = 1500.0
K_FACTOR = 32.0
ITERATION_PASSES = 3

def get_dynamic_k(matches_played):
    """Calculates K-Factor decay based on team experience."""
    if matches_played <= 3: return 64.0
    if matches_played <= 7: return 32.0
    return 16.0

def calculate_truerank(teams_list, match_list):
    """
    Runs the 3-Pass TrueRank algorithm.
    Returns a dictionary of team stats ready to be pushed to Supabase.
    """
    global_memory = {}
    for team in teams_list:
        team_id = team.get("name") or team.get("code") or team.get("number")
        if not team_id: continue

        global_memory[team_id] = {
            "team_id": team_id,
            "elo_score": BASE_ELO,
            "matches_played": 0,
            "wins": 0,     # NEW: Initialized to 0
            "losses": 0,   # NEW: Initialized to 0
            "ties": 0      # NEW: Initialized to 0
        }

    # The Time Machine Loop (3 Passes)
    for _ in range(ITERATION_PASSES):

        # Reset trackers at the start of every pass
        match_counts = {t: 0 for t in global_memory}
        wins = {t: 0 for t in global_memory}
        losses = {t: 0 for t in global_memory}
        ties = {t: 0 for t in global_memory}

        for match in match_list:
            alliances = match.get("alliances", [])
            if len(alliances) < 2: continue

            red_alliance = next((a for a in alliances if a["color"] == "red"), None)
            blue_alliance = next((a for a in alliances if a["color"] == "blue"), None)

            if not red_alliance or not blue_alliance: continue

            red_score = red_alliance.get("score", 0)
            blue_score = blue_alliance.get("score", 0)

            if red_score == 0 and blue_score == 0: continue

            red_teams = [t["team"].get("name") or t["team"].get("code") or t["team"].get("number") for t in red_alliance.get("teams", [])]
            blue_teams = [t["team"].get("name") or t["team"].get("code") or t["team"].get("number") for t in blue_alliance.get("teams", [])]

            red_teams = [t for t in red_teams if t in global_memory]
            blue_teams = [t for t in blue_teams if t in global_memory]

            if not red_teams or not blue_teams: continue

            # --- NEW: Track Wins, Losses, and Ties ---
            for t in red_teams:
                if red_score > blue_score: wins[t] += 1
                elif red_score < blue_score: losses[t] += 1
                else: ties[t] += 1

            for t in blue_teams:
                if blue_score > red_score: wins[t] += 1
                elif blue_score < red_score: losses[t] += 1
                else: ties[t] += 1

            # --- 1. ALLIANCE AVERAGING ---
            red_avg_elo = sum(global_memory[t]["elo_score"] for t in red_teams) / len(red_teams)
            blue_avg_elo = sum(global_memory[t]["elo_score"] for t in blue_teams) / len(blue_teams)

            expected_red = 1.0 / (1.0 + 10.0 ** ((blue_avg_elo - red_avg_elo) / 400.0))
            expected_blue = 1.0 - expected_red

            actual_red = 1.0 if red_score > blue_score else (0.5 if red_score == blue_score else 0.0)
            actual_blue = 1.0 - actual_red

            # --- 2. STACKED MULTIPLIERS ---
            total_score = red_score + blue_score
            mov_multiplier = 1.0
            if total_score > 0:
                mov_multiplier = 1.0 + (abs(red_score - blue_score) / total_score)

            round_num = match.get("round", 2)
            elim_multiplier = 1.5 if round_num > 2 else 1.0

            auto_red_mult = 1.0
            auto_blue_mult = 1.0
            score_breakdown = match.get("score_breakdown")
            if score_breakdown:
                auto_winner = score_breakdown.get("autonomous", "")
                if auto_winner == "red": auto_red_mult = 1.15
                if auto_winner == "blue": auto_blue_mult = 1.15

            base_red_shift = (actual_red - expected_red) * mov_multiplier * elim_multiplier * auto_red_mult
            base_blue_shift = (actual_blue - expected_blue) * mov_multiplier * elim_multiplier * auto_blue_mult

            # --- 3. K-FACTOR DECAY & APPLICATION ---
            for t_id in red_teams:
                matches_played = match_counts[t_id]
                match_counts[t_id] += 1
                global_memory[t_id]["elo_score"] += (get_dynamic_k(matches_played) * base_red_shift)

            for t_id in blue_teams:
                matches_played = match_counts[t_id]
                match_counts[t_id] += 1
                global_memory[t_id]["elo_score"] += (get_dynamic_k(matches_played) * base_blue_shift)

    # Compile the final statistics for the database payload
    for t_id in global_memory:
        global_memory[t_id]["matches_played"] = match_counts[t_id]
        global_memory[t_id]["wins"] = wins[t_id]         # NEW
        global_memory[t_id]["losses"] = losses[t_id]     # NEW
        global_memory[t_id]["ties"] = ties[t_id]         # NEW
        global_memory[t_id]["elo_score"] = round(global_memory[t_id]["elo_score"], 1)

    return list(global_memory.values())