import numpy as np
from scipy.sparse import lil_matrix
from scipy.sparse.linalg import lsqr

def calculate_truerank(all_teams, all_matches):
    """Calculates both the 3-Pass Elo and the Sparse Matrix Global OPR."""

    # 1. Map all unique teams to a specific index for our Matrix
    team_list = list(set([t.get("name") for t in all_teams if t.get("name")]))
    team_to_idx = {team: idx for idx, team in enumerate(team_list)}
    num_teams = len(team_list)

    # 2. Filter valid matches
    valid_matches = []
    for m in all_matches:
        if not m.get("alliances") or len(m["alliances"]) < 2: continue
        red = m["alliances"][0] if m["alliances"][0]["color"] == "red" else m["alliances"][1]
        blue = m["alliances"][0] if m["alliances"][0]["color"] == "blue" else m["alliances"][1]
        if red["score"] == 0 and blue["score"] == 0: continue
        valid_matches.append((red, blue))

    # ==========================================
    # --- PART 1: OFFENSIVE POWER RATING (OPR) ---
    # ==========================================
    num_equations = len(valid_matches) * 2 # Red score is one equation, Blue score is another
    A = lil_matrix((num_equations, num_teams))
    B = np.zeros(num_equations)

    row = 0
    for red, blue in valid_matches:
        for t in red["teams"]:
            t_name = t["team"]["name"]
            if t_name in team_to_idx: A[row, team_to_idx[t_name]] = 1
        B[row] = red["score"]
        row += 1

        for t in blue["teams"]:
            t_name = t["team"]["name"]
            if t_name in team_to_idx: A[row, team_to_idx[t_name]] = 1
        B[row] = blue["score"]
        row += 1

    # Solve the sparse system Ax = B using Least Squares
    opr_vector = lsqr(A, B)[0]

    # ==========================================
    # --- PART 2: 3-PASS ELO TRUERANK ---
    # ==========================================
    elo_scores = {team: 1500.0 for team in team_list}
    records = {team: {"wins": 0, "losses": 0, "ties": 0} for team in team_list}
    k_factor = 32

    for pass_num in range(3):
        for red, blue in valid_matches:
            red_teams = [t["team"]["name"] for t in red["teams"] if t["team"]["name"] in elo_scores]
            blue_teams = [t["team"]["name"] for t in blue["teams"] if t["team"]["name"] in elo_scores]
            if not red_teams or not blue_teams: continue

            red_avg = sum([elo_scores[t] for t in red_teams]) / len(red_teams)
            blue_avg = sum([elo_scores[t] for t in blue_teams]) / len(blue_teams)

            expected_red = 1 / (1 + 10 ** ((blue_avg - red_avg) / 400))
            expected_blue = 1 - expected_red

            rs, bs = red["score"], blue["score"]
            actual_red = 1 if rs > bs else (0.5 if rs == bs else 0)
            actual_blue = 1 - actual_red
            mov = 1 + abs(rs - bs) / max(rs + bs, 1) # Margin of Victory Multiplier

            shift_red = k_factor * (actual_red - expected_red) * mov
            shift_blue = k_factor * (actual_blue - expected_blue) * mov

            for t in red_teams: elo_scores[t] += shift_red
            for t in blue_teams: elo_scores[t] += shift_blue

            # Only log wins/losses on the first pass
            if pass_num == 0:
                for t in red_teams:
                    if actual_red == 1: records[t]["wins"] += 1
                    elif actual_red == 0.5: records[t]["ties"] += 1
                    else: records[t]["losses"] += 1
                for t in blue_teams:
                    if actual_blue == 1: records[t]["wins"] += 1
                    elif actual_blue == 0.5: records[t]["ties"] += 1
                    else: records[t]["losses"] += 1

    # ==========================================
    # --- PART 3: PACKAGE DATA FOR CLOUD ---
    # ==========================================
    final_data = []
    for team in team_list:
        idx = team_to_idx[team]
        final_data.append({
            "team_id": team,
            "elo_score": round(elo_scores[team], 2),
            "opr": round(opr_vector[idx], 2),
            "wins": records[team]["wins"],
            "losses": records[team]["losses"],
            "ties": records[team]["ties"]
        })

    return final_data