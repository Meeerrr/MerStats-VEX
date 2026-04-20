import requests
import os
import time
from dotenv import load_dotenv
from engine import calculate_truerank # Your existing math engine

load_dotenv()

# ==========================================
# API KEYS & HEADERS
# ==========================================
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

supabase_headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "resolution=merge-duplicates" # Safely overwrites existing ranks
}

TARGET_SEASONS = [240, 181, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]

def inflate_match(micro_match):
    """
    Inflates our ultra-light Micro-JSON back into standard RobotEvents JSON
    so your existing engine.py math doesn't break.
    """
    return {
        "alliances": [
            {
                "color": "red",
                "score": micro_match.get("rs", 0),
                "teams": [{"team": {"name": t}} for t in micro_match.get("rt", [])]
            },
            {
                "color": "blue",
                "score": micro_match.get("bs", 0),
                "teams": [{"team": {"name": t}} for t in micro_match.get("bt", [])]
            }
        ]
    }

def fetch_db_matches(season_id):
    """Pulls thousands of raw matches from Supabase in ~2 seconds"""
    matches = []
    offset = 0
    limit = 1000 # Supabase max rows per request

    print(f"📡 Pulling Season {season_id} matches from Data Lake...", end=" ")

    while True:
        # Use Range headers to paginate through the database instantly
        headers = {**supabase_headers, "Range": f"{offset}-{offset+limit-1}"}
        res = requests.get(f"{SUPABASE_URL}/rest/v1/raw_matches?season_id=eq.{season_id}&select=match_data", headers=headers)

        if res.status_code != 200:
            print(f"❌ Error: {res.text}")
            break

        data = res.json()
        if not data:
            break

        # Extract the micro-match payload from the database row
        matches.extend([row["match_data"] for row in data])

        offset += limit
        if len(data) < limit:
            break # We reached the end of the data

    print(f"✅ Downloaded {len(matches)} matches.")
    return matches

def run_fast_compute():
    print("🚀 Starting Lightning Compute Engine...")
    start_time = time.time()

    for season_id in TARGET_SEASONS:
        print(f"\n==================================================")
        print(f"⚡ RECALCULATING SEASON: {season_id}")
        print(f"==================================================")

        # 1. Fetch from your own DB instead of RobotEvents
        micro_matches = fetch_db_matches(season_id)
        if not micro_matches:
            print("⚠️ No matches found for this season. Skipping.")
            continue

        # 2. Extract unique teams and Inflate matches for the Engine
        unique_teams = set()
        inflated_matches = []

        for m in micro_matches:
            # Add teams to our master roster
            unique_teams.update(m.get("rt", []))
            unique_teams.update(m.get("bt", []))
            # Inflate the match
            inflated_matches.append(inflate_match(m))

        # Format the team roster to match what engine.py expects (usually a list of dicts)
        all_teams = [{"name": t} for t in unique_teams]
        print(f"🤖 Found {len(all_teams)} unique active teams.")

        # 3. Run the math engine
        print("🧠 Crunching TrueRank and OPR matrices...")
        engine_start = time.time()

        # Calls your engine.py function
        final_elo_data = calculate_truerank(all_teams, inflated_matches)

        print(f"⏱️ Math finished in {round(time.time() - engine_start, 2)} seconds.")

        # 4. Inject the season ID and push updated ranks back to Supabase
        for data in final_elo_data:
            data["season_id"] = season_id

        print("☁️ Pushing updated leaderboards to Supabase...")

        # Upload in batches of 1000
        for i in range(0, len(final_elo_data), 1000):
            batch = final_elo_data[i:i+1000]
            res = requests.post(f"{SUPABASE_URL}/rest/v1/global_truerank", json=batch, headers=supabase_headers)

            if res.status_code in [200, 201]:
                print(f"   ✅ Upserted batch {i} to {i + len(batch)}")
            else:
                print(f"   ❌ Upload Error: {res.text}")

    total_time = round(time.time() - start_time, 2)
    print(f"\n🏁 ALL SEASONS COMPLETED in {total_time} seconds!")

if __name__ == "__main__":
    run_fast_compute()