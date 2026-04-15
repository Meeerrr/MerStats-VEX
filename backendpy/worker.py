import requests
import os
import time
from datetime import datetime
from dotenv import load_dotenv  # NEW: Imports the tool
from engine import calculate_truerank

# This tells Python to look for a .env file and load the keys into the environment!
load_dotenv()

# --- 1. CONFIGURATION ---
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
ROBOT_EVENTS_KEY = os.environ.get("ROBOT_EVENTS_KEY")

TARGET_SEASON_ID = 190

def run_worker():
    print(f"🤖 Starting Full Season TrueRank Pipeline for Season: {TARGET_SEASON_ID}")

    # NEW: A quick safety check so you know if your keys loaded correctly!
    if not ROBOT_EVENTS_KEY or not SUPABASE_URL:
        print("❌ ERROR: Keys are missing! Check your .env file.")
        return

re_headers = {
    "Authorization": f"Bearer {ROBOT_EVENTS_KEY}",
    "Accept": "application/json"
}

def fetch_all_pages(url):
    """Handles RobotEvents pagination to get ALL data, not just the first 250 rows."""
    results = []
    while url:
        res = requests.get(url, headers=re_headers)

        # If we hit the rate limit, sleep and try again
        if res.status_code == 429:
            print("⚠️ Rate limited by RobotEvents! Sleeping for 5 seconds...")
            time.sleep(5)
            continue

        data = res.json()
        if 'data' in data:
            results.extend(data['data'])

        url = data.get('meta', {}).get('next_page_url')
    return results

def upload_in_batches(endpoint, payload, headers, batch_size=1000):
    """Supabase prefers getting data in chunks rather than 10,000 rows at once."""
    for i in range(0, len(payload), batch_size):
        batch = payload[i:i + batch_size]
        requests.post(endpoint, json=batch, headers=headers)

def run_worker():
    print(f"🤖 Starting Full Season TrueRank Pipeline for Season: {TARGET_SEASON_ID}")

    # --- 2. FETCH ALL EVENTS FOR THE SEASON ---
    print("📡 Fetching event manifest for the season...")
    events_url = f"https://www.robotevents.com/api/v2/events?season[]={TARGET_SEASON_ID}&per_page=250"
    all_events = fetch_all_pages(events_url)

    # Filter for past/completed events only (Ignore future events with no matches)
    today = datetime.now().strftime('%Y-%m-%d')
    past_events = [e for e in all_events if e.get('start', '')[:10] < today]
    print(f"✅ Found {len(past_events)} completed events. Commencing data extraction...")

    all_teams = []
    all_matches = []

    # --- 3. EXTRACT MATCHES FROM EVERY EVENT ---
    for idx, event in enumerate(past_events):
        event_id = event['id']
        print(f"[{idx+1}/{len(past_events)}] Downloading data for {event.get('sku')}...")

        for div in event.get('divisions', []):
            div_id = div['id']

            # Fetch Rankings (for the roster)
            rank_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/rankings?per_page=250"
            all_teams.extend([r["team"] for r in fetch_all_pages(rank_url)])

            # Fetch Matches
            match_url = f"https://www.robotevents.com/api/v2/events/{event_id}/divisions/{div_id}/matches?per_page=250"
            all_matches.extend(fetch_all_pages(match_url))

        # Polite delay to respect VEX servers
        time.sleep(0.5)

    # --- 4. CRUNCH THE MATH ---
    print(f"🧠 Extraction complete. Running TrueRank on {len(all_matches)} matches...")
    final_elo_data = calculate_truerank(all_teams, all_matches)

    # INJECT THE SEASON ID INTO THE RESULTS
    for data in final_elo_data:
        data["season_id"] = TARGET_SEASON_ID

    # --- 5. UPLOAD TO DATABASE ---
    print("☁️ Connecting to Supabase...")
    supabase_headers = {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
    }

    # Upload Teams
    print("☁️ Step 1/2: Pushing global roster...")
    teams_payload = []
    seen_teams = set()
    for t in all_teams:
        t_id = t.get("name") or t.get("code") or t.get("number")
        if not t_id or t_id in seen_teams: continue
        seen_teams.add(t_id)
        teams_payload.append({
            "id": t_id,
            "team_name": t.get("team_name", t.get("name", "Unknown Name")),
            "grade_level": t.get("grade", "Unknown Grade")
        })
    upload_in_batches(f"{SUPABASE_URL}/rest/v1/teams", teams_payload, supabase_headers)

    # Upload Elo Data
    print(f"☁️ Step 2/2: Uploading {len(final_elo_data)} TrueRank records...")
    upload_in_batches(f"{SUPABASE_URL}/rest/v1/global_truerank", final_elo_data, supabase_headers)

    print("✅ Success! Global Season Pipeline complete.")

if __name__ == "__main__":
    run_worker()